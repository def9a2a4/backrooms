"""A building being drawn block by block, written out as a Sponge v3 schematic in the shape
Level 142's Mace buildings have (generator/MaceBuilding.java reads both): Metadata.Mace holds
the building's type, frequency, theme files and the rest, and stand-in blocks mark what each
city fills in.

Coordinates: x across the front, z from the front (z = 0, north, facing the street) to the back,
y = 0 the ground layer (world y63). Below ground is y < 0; the schematic starts at the lowest
layer drawn and records it as source_y.
"""

import gzip
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "mace"))
import extract_schem as nbt  # noqa: E402  (NBT writer, neighbour fix-ups)

DATA_VERSION = nbt.DATA_VERSION

# Stand-ins MaceBuilding.fill replaces per city (see its class comment).
GROUND = "redstone_ore"   # on the ground layer: the city's ground block
PATH = "gold_ore"         # on the ground layer: the city's path block
WOOL = "lapis_ore"        # wool, one colour per building
WALL = "iron_ore"         # gatehouses: the city's wall block
HOUSE_LOOT = 3            # a chest whose first slot is this much paper gets house loot
TREASURE = 4              # ... or treasure

FACINGS = ("north", "east", "south", "west")
STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
LEFT = {"north": "west", "west": "south", "south": "east", "east": "north"}   # turning left
RIGHT = {v: k for k, v in LEFT.items()}


def parse(state):
    m = re.fullmatch(r"(?:minecraft:)?([a-z0-9_]+)(?:\[(.*)\])?", state)
    if not m:
        raise ValueError(f"bad block state {state!r}")
    props = dict(p.split("=", 1) for p in m.group(2).split(",")) if m.group(2) else {}
    return m.group(1), props


class Build:
    """One schematic. {@code meta} goes into Metadata.Mace as strings."""

    def __init__(self, name, kind, w, l, *, freq="common", files=(), **meta):
        self.name, self.kind, self.w, self.l = name, kind, w, l
        self.blocks = {}      # (x, y, z) -> (name, props)
        self.entities = {}    # (x, y, z) -> (block entity id, data)
        self.meta = {"type": kind, "frequency": freq, "files": ",".join(files), "size_x": w, "size_z": l}
        for k, v in meta.items():
            self.meta[k] = v

    # ── Drawing ──────────────────────────────────────────────────────────

    def inside(self, x, z):
        return 0 <= x < self.w and 0 <= z < self.l

    def set(self, x, y, z, state, **props):
        """Sets a block; positions off the footprint are dropped."""
        if not self.inside(x, z):
            return
        name, p = parse(state)
        p.update({k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()})
        self.blocks[(x, y, z)] = (name, p)
        self.entities.pop((x, y, z), None)

    def get(self, x, y, z):
        return self.blocks.get((x, y, z), ("air", {}))[0]

    def props(self, x, y, z):
        return self.blocks.get((x, y, z), ("air", {}))[1]

    def empty(self, x, y, z):
        return self.get(x, y, z) in ("air", "cave_air")

    def fill(self, x0, y0, z0, x1, y1, z1, state, **props):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    self.set(x, y, z, state, **props)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, "air")

    def ring(self, x0, z0, x1, z1, y0, y1, state, **props):
        """The outline of the rectangle, y0 to y1."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, z0, state, **props)
                self.set(x, y, z1, state, **props)
            for z in range(z0, z1 + 1):
                self.set(x0, y, z, state, **props)
                self.set(x1, y, z, state, **props)

    def ground(self, state=GROUND):
        """The whole ground layer: the city's ground unless drawn over."""
        self.fill(0, 0, 0, self.w - 1, 0, self.l - 1, state)

    def path(self, x0, z0, x1, z1):
        self.fill(x0, 0, z0, x1, 0, z1, PATH)

    def replace(self, old, new, **props):
        for k, (n, _) in list(self.blocks.items()):
            if n == old:
                self.set(*k, new, **props)

    # ── Blocks with parts or data ────────────────────────────────────────

    def door(self, x, y, z, facing, wood="oak", hinge="left", material=None):
        m = material or f"{wood}_door"
        for half, dy in (("lower", 0), ("upper", 1)):
            self.set(x, y + dy, z, m, facing=facing, half=half, hinge=hinge, open="false", powered="false")

    def bed(self, x, y, z, facing, color="red"):
        """Foot at (x, z), head one step towards {@code facing}."""
        dx, dz = STEP[facing]
        self.set(x, y, z, f"{color}_bed", facing=facing, part="foot", occupied="false")
        self.set(x + dx, y, z + dz, f"{color}_bed", facing=facing, part="head", occupied="false")

    def sign(self, x, y, z, facing, text, wood="oak", wall=True):
        """A sign showing {@code text}: one string (Mace's tokens and "~" breaks, wrapped when
        filled) or a list of up to four lines. A wall sign hangs on the block behind it."""
        if not self.inside(x, z):
            return
        if wall:
            self.set(x, y, z, f"{wood}_wall_sign", facing=facing, waterlogged="false")
        else:
            rot = {"south": 0, "west": 4, "north": 8, "east": 12}[facing]
            self.set(x, y, z, f"{wood}_sign", rotation=rot, waterlogged="false")
        lines = [text, "", "", ""] if isinstance(text, str) else (list(text) + ["", "", "", ""])[:4]
        blank = {"messages": ["", "", "", ""], "color": "black", "has_glowing_text": nbt.B(0)}
        self.entities[(x, y, z)] = ("minecraft:sign", {"front_text": {**blank, "messages": lines},
                                                       "back_text": blank, "is_waxed": nbt.B(0)})

    def container(self, x, y, z, state, items=HOUSE_LOOT, **props):
        """A chest (or barrel, ...) with house loot, treasure, or the given [(item, count)]."""
        if not self.inside(x, z):
            return
        self.set(x, y, z, state, **props)
        if isinstance(items, int):
            items = [("paper", items)]
        stacks = [{"Slot": nbt.B(i), "id": "minecraft:" + it, "count": n} for i, (it, n) in enumerate(items)]
        be = parse(state)[0]
        be = "chest" if be.endswith("chest") and be != "trapped_chest" else be
        self.entities[(x, y, z)] = ("minecraft:" + be, {"Items": stacks})

    def chest(self, x, y, z, facing, items=HOUSE_LOOT):
        self.container(x, y, z, "chest", items, facing=facing, type="single", waterlogged="false")

    def barrel(self, x, y, z, items=(), facing="up"):
        self.container(x, y, z, "barrel", list(items), facing=facing, open="false")

    # ── Output ───────────────────────────────────────────────────────────

    def bounds(self):
        ys = [y for (_, y, _), (n, _) in self.blocks.items() if n != "air"] or [0]
        lo = min(ys) if self.kind == "sky_feature" else min(min(ys), 0)   # sky features float
        return lo, max(max(ys), 0)

    def schematic(self):
        lo, hi = self.bounds()
        W, L, H = self.w, self.l, hi - lo + 1
        grid = []
        for y in range(lo, hi + 1):
            for z in range(L):
                for x in range(W):
                    n, p = self.blocks.get((x, y, z), ("air", {}))
                    grid.append(("minecraft:" + n, dict(p)))
        nbt.fix_neighbours(grid, W, H, L)
        palette, data = {}, bytearray()
        for n, p in grid:
            key = n + ("[" + ",".join(f"{k}={v}" for k, v in sorted(p.items())) + "]" if p else "")
            nbt.varint(data, palette.setdefault(key, len(palette)))
        bes = [{"Pos": nbt.IA([x, y - lo, z]), "Id": i, "Data": d}
               for (x, y, z), (i, d) in sorted(self.entities.items()) if y <= hi]
        meta = {k: str(v) for k, v in self.meta.items()}
        meta["source_y"] = 63 + lo
        root = {"Schematic": {
            "Version": 3, "DataVersion": DATA_VERSION,
            "Metadata": {"Name": self.name, "Author": "scripts/level143", "Mace": meta},
            "Width": nbt.S(W), "Height": nbt.S(H), "Length": nbt.S(L), "Offset": nbt.IA([0, 0, 0]),
            "Blocks": {"Palette": palette, "Data": bytes(data), "BlockEntities": bes},
            "Entities": [],
        }}
        return gzip.compress(nbt.write_nbt(root), mtime=0), palette


class Checker:
    """Checks block states against the vanilla report (java -DbundlerMainClass=net.minecraft.data.Main
    -jar server.jar --reports), if there is one: a misspelt block would load as air."""

    def __init__(self, report):
        self.blocks = None
        if report and Path(report).exists():
            self.blocks = {k.split(":", 1)[1]: v.get("properties", {}) for k, v in json.loads(Path(report).read_text()).items()}
        self.bad = {}

    def check(self, build, palette):
        if self.blocks is None:
            return
        for key in palette:
            name, props = parse(key)
            known = self.blocks.get(name)
            if known is None:
                self.bad.setdefault(f"unknown block {name}", set()).add(build.name)
                continue
            for k, v in props.items():
                if k not in known or v not in known[k]:
                    self.bad.setdefault(f"{name}: {k}={v}", set()).add(build.name)
