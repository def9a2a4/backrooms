#!/usr/bin/env python3
"""Extract Mace's hand-built buildings into Sponge Schematic v3 (.schem) files.

Mace (https://github.com/corbin-auriti/mace-minecraft, GPLv3) keeps its buildings in a template
world (Resources/Mace/region/*.mca, MC 1.2-1.4 era: numeric block ids + data nibbles). XML files
index them: each <building> gives a name, a footprint (<size>, or <size_x>/<size_z>) starting at
<source_x>/<source_z>, an optional <source_start_y> (default 63), plus frequency/type/tags. Height
is not stored: like Mace's SourceWorld.InsertBuilding, a building runs from its start y up to the
highest non-air block in its footprint (scanning down from y=128); mineshaft sections are a fixed
5 blocks tall.

Blocks are converted to 1.21.11 block states through PrismarineJS minecraft-data's legacy.json
(id:meta -> 1.13 state) plus the renames since 1.13. States that 1.13's flattening derived from
neighbours (stair shapes, door halves, fence/pane/wall connections, double chests) are recomputed.
Signs, containers, spawners and paintings are converted to the modern format.

Mace's placeholders are left in place, for a loader to substitute the way Mace does:
    lapis ore                   -> wool, one random colour per building
    redstone ore at source y63  -> the city's ground block
    gold ore at source y63      -> the city's path block
    iron ore, glowstone, torch  -> (city entrances) wall material / removed, per theme
    chest with 3 / 4 paper      -> random house / treasure loot
    signs: "cityname", "entity <type> <min max> <npc>", "spawner"/"gspawner <mob> <dx dy dz>
           <gx gy gz|no>", "commentsign" (author note, removed); a leading 0/1/2 sets uniqueness
The building's XML entry is kept in the schematic's Metadata.Mace compound.

Sources and the mapping tables are downloaded into .temp/mace/ (needs `gh` and network) and
cached. Output: .temp/mace/schem/<type>/<name>.schem and .temp/mace/schem/report.txt.

Usage: scripts/mace/extract_schem.py [--out DIR] [--only NAME_SUBSTRING]
"""

import argparse
import gzip
import json
import re
import struct
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zlib
from collections import Counter
from pathlib import Path

REPO = "corbin-auriti/mace-minecraft"
SOURCES = [
    "Resources/Mace/level.dat",
    "Resources/Mace/region/r.-1.-1.mca",
    "Resources/Mace/region/r.-1.0.mca",
    "Resources/Mace/region/r.0.-1.mca",
    "Resources/Mace/region/r.0.0.mca",
    "Resources/Themes/Desert Buildings.xml",
    "Resources/Themes/Medieval Buildings.xml",
    "Resources/Themes/Novv's Medieval Buildings.xml",
    "Resources/Buildings/City.xml",
    "Resources/Buildings/CityEntrance.xml",
    "Resources/Buildings/Farm.xml",
    "Resources/Buildings/MineshaftEntrance.xml",
    "Resources/Buildings/MineshaftSection.xml",
    "Resources/Buildings/SkyFeature.xml",
]
LEGACY_URL = "https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/common/legacy.json"
DATA_VERSION = 4671  # 1.21.11

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / ".temp" / "mace"


# ---------------------------------------------------------------------------
# Fetching
# ---------------------------------------------------------------------------

def fetch_sources():
    for rel in SOURCES:
        dest = CACHE / "src" / rel
        if dest.exists() and dest.stat().st_size > 0:
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        api = f"repos/{REPO}/contents/{rel.replace(' ', '%20')}"
        data = subprocess.run(["gh", "api", api, "-H", "Accept: application/vnd.github.raw"],
                              check=True, capture_output=True).stdout
        dest.write_bytes(data)
    legacy = CACHE / "legacy.json"
    if not legacy.exists():
        with urllib.request.urlopen(LEGACY_URL) as r:
            legacy.write_bytes(r.read())
    return json.loads(legacy.read_text())


# ---------------------------------------------------------------------------
# NBT
# ---------------------------------------------------------------------------

class Tag:
    """A typed scalar for writing: Tag(1, v) is a byte, Tag(2, v) a short, and so on."""
    __slots__ = ("type", "value")

    def __init__(self, tag_type, value):
        self.type, self.value = tag_type, value


def B(v): return Tag(1, v)
def S(v): return Tag(2, v)
def L(v): return Tag(4, v)
def F(v): return Tag(5, v)
def D(v): return Tag(6, v)
def IA(v): return Tag(11, list(v))


def _read(buf, pos, t):
    if t == 1: return struct.unpack_from(">b", buf, pos)[0], pos + 1
    if t == 2: return struct.unpack_from(">h", buf, pos)[0], pos + 2
    if t == 3: return struct.unpack_from(">i", buf, pos)[0], pos + 4
    if t == 4: return struct.unpack_from(">q", buf, pos)[0], pos + 8
    if t == 5: return struct.unpack_from(">f", buf, pos)[0], pos + 4
    if t == 6: return struct.unpack_from(">d", buf, pos)[0], pos + 8
    if t == 7:
        n = struct.unpack_from(">i", buf, pos)[0]
        return bytes(buf[pos + 4:pos + 4 + n]), pos + 4 + n
    if t == 8:
        n = struct.unpack_from(">H", buf, pos)[0]
        return buf[pos + 2:pos + 2 + n].decode("utf-8", "replace"), pos + 2 + n
    if t == 9:
        inner, n = buf[pos], struct.unpack_from(">i", buf, pos + 1)[0]
        pos += 5
        out = []
        for _ in range(n):
            v, pos = _read(buf, pos, inner)
            out.append(v)
        return out, pos
    if t == 10:
        d = {}
        while True:
            tt = buf[pos]
            pos += 1
            if tt == 0:
                return d, pos
            n = struct.unpack_from(">H", buf, pos)[0]
            name = buf[pos + 2:pos + 2 + n].decode("utf-8", "replace")
            d[name], pos = _read(buf, pos + 2 + n, tt)
    if t == 11:
        n = struct.unpack_from(">i", buf, pos)[0]
        return list(struct.unpack_from(f">{n}i", buf, pos + 4)), pos + 4 + 4 * n
    if t == 12:
        n = struct.unpack_from(">i", buf, pos)[0]
        return list(struct.unpack_from(f">{n}q", buf, pos + 4)), pos + 4 + 8 * n
    raise ValueError(f"unknown NBT tag {t}")


def read_nbt(data):
    if data[0] != 10:
        raise ValueError("root is not a compound")
    n = struct.unpack_from(">H", data, 1)[0]
    return _read(data, 3 + n, 10)[0]


def _type_of(v):
    if isinstance(v, Tag): return v.type
    if isinstance(v, bool): return 1
    if isinstance(v, int): return 3
    if isinstance(v, float): return 6
    if isinstance(v, (bytes, bytearray)): return 7
    if isinstance(v, str): return 8
    if isinstance(v, list): return 9
    if isinstance(v, dict): return 10
    raise TypeError(f"no NBT type for {type(v)}")


def _write(out, v, t):
    if isinstance(v, Tag): v = v.value
    if t == 1: out += struct.pack(">b", int(v))
    elif t == 2: out += struct.pack(">h", v)
    elif t == 3: out += struct.pack(">i", v)
    elif t == 4: out += struct.pack(">q", v)
    elif t == 5: out += struct.pack(">f", v)
    elif t == 6: out += struct.pack(">d", v)
    elif t == 7: out += struct.pack(">i", len(v)) + bytes(v)
    elif t == 8:
        b = v.encode("utf-8")
        out += struct.pack(">H", len(b)) + b
    elif t == 9:
        inner = _type_of(v[0]) if v else 0
        out += struct.pack(">bi", inner, len(v))
        for item in v:
            _write(out, item, inner)
    elif t == 10:
        for k, item in v.items():
            tt = _type_of(item)
            kb = k.encode("utf-8")
            out += struct.pack(">bH", tt, len(kb)) + kb
            _write(out, item, tt)
        out += b"\0"
    elif t == 11: out += struct.pack(f">i{len(v)}i", len(v), *v)
    else: raise ValueError(t)


def write_nbt(root):
    out = bytearray(b"\x0a\x00\x00")
    _write(out, root, 10)
    return bytes(out)


# ---------------------------------------------------------------------------
# Source world (old Anvil: Level.Sections[].Blocks/Add/Data)
# ---------------------------------------------------------------------------

class SourceWorld:
    def __init__(self, region_dir):
        self.sections = {}       # (cx, cy, cz) -> (blocks, add, data)
        self.tile_entities = {}  # (x, y, z) -> compound
        self.paintings = []
        for mca in sorted(region_dir.glob("*.mca")):
            raw = mca.read_bytes()
            for i in range(1024):
                off = int.from_bytes(raw[i * 4:i * 4 + 3], "big") * 4096
                if off == 0:
                    continue
                length, comp = struct.unpack_from(">ib", raw, off)
                blob = raw[off + 5:off + 4 + length]
                level = read_nbt(zlib.decompress(blob) if comp == 2 else gzip.decompress(blob))["Level"]
                cx, cz = level["xPos"], level["zPos"]
                for sec in level.get("Sections", []):
                    self.sections[(cx, sec["Y"], cz)] = (sec["Blocks"], sec.get("Add"), sec["Data"])
                for te in level.get("TileEntities", []):
                    self.tile_entities[(te["x"], te["y"], te["z"])] = te
                for ent in level.get("Entities", []):
                    if ent.get("id") == "Painting":
                        self.paintings.append(ent)

    def block(self, x, y, z):
        sec = self.sections.get((x >> 4, y >> 4, z >> 4))
        if sec is None:
            return 0, 0
        blocks, add, data = sec
        i = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
        bid = blocks[i]
        if add:
            bid |= ((add[i >> 1] >> (4 * (i & 1))) & 15) << 8
        return bid, (data[i >> 1] >> (4 * (i & 1))) & 15


# ---------------------------------------------------------------------------
# Building index
# ---------------------------------------------------------------------------

def read_index(src):
    found = {}
    files = sorted((src / "Resources" / "Themes").glob("*.xml")) + sorted((src / "Resources" / "Buildings").glob("*.xml"))
    for path in files:
        root = ET.fromstring(path.read_text(encoding="utf-8-sig"))
        for el in root.iter("building"):
            f = {child.tag: (child.text or "").strip() for child in el}
            b = {
                "name": f["name"],
                "source_x": int(f["source_x"]),
                "source_z": int(f["source_z"]),
                "source_y": int(f.get("source_start_y") or 63),
                "size_x": int(f.get("size") or f["size_x"]),
                "size_z": int(f.get("size") or f["size_z"]),
                "type": f.get("type") or "city",
            }
            for key in ("frequency", "tags", "unique", "pattern", "pattern_position_x", "pattern_position_z",
                        *(f"frequency{d}" for d in range(1, 8))):
                if f.get(key):
                    b[key] = f[key]
            # The same build is often listed under different names; themes come first, so their
            # name wins and the rest become aliases.
            key = (b["source_x"], b["source_z"], b["source_y"], b["size_x"], b["size_z"])
            entry = found.setdefault(key, b)
            entry.setdefault("files", []).append(path.stem)
            if b["name"].lower() != entry["name"].lower() and b["name"] not in entry.setdefault("aliases", []):
                entry["aliases"].append(b["name"])
    return list(found.values())


# ---------------------------------------------------------------------------
# Block conversion
# ---------------------------------------------------------------------------

def parse_state(s):
    m = re.fullmatch(r"([^\[]+)(?:\[(.*)\])?", s)
    props = dict(p.split("=", 1) for p in m.group(2).split(",")) if m.group(2) else {}
    return m.group(1), props


RENAMES = {  # 1.13 -> 1.21.11
    "minecraft:sign": "minecraft:oak_sign",
    "minecraft:wall_sign": "minecraft:oak_wall_sign",
    "minecraft:grass": "minecraft:short_grass",
    "minecraft:stone_slab": "minecraft:smooth_stone_slab",
    "minecraft:grass_path": "minecraft:dirt_path",
}
EXTRA_BLOCKS = {90: "minecraft:nether_portal[axis=x]"}  # legacy.json only has 90:1/90:2
ITEM_RENAMES = {
    **RENAMES,
    "minecraft:rose_red": "minecraft:red_dye",
    "minecraft:dandelion_yellow": "minecraft:yellow_dye",
    "minecraft:cactus_green": "minecraft:green_dye",
}


class Converter:
    def __init__(self, legacy):
        self.blocks = legacy["blocks"]
        self.items = legacy["items"]
        self.unmapped = Counter()

    def block(self, bid, meta):
        s = self.blocks.get(f"{bid}:{meta}") or self.blocks.get(f"{bid}:0") or EXTRA_BLOCKS.get(bid)
        if s is None:
            self.unmapped[f"{bid}:{meta}"] += 1
            return "minecraft:air", {}
        name, props = parse_state(s)
        name = RENAMES.get(name, name)
        if name == "minecraft:cauldron" and props.get("level", "0") != "0":
            name = "minecraft:water_cauldron"
        elif name == "minecraft:cauldron":
            props = {}
        elif name.endswith("_leaves"):
            props["persistent"] = "true"  # pasted builds have no log network to keep them alive
        return name, props

    def item(self, iid, damage):
        if isinstance(iid, str):
            return iid if ":" in iid else "minecraft:" + iid
        s = self.items.get(f"{iid}:{damage}") or self.items.get(f"{iid}:0") or self.blocks.get(f"{iid}:0")
        if s is None:
            self.unmapped[f"item {iid}:{damage}"] += 1
            return None
        name = parse_state(s)[0]
        return ITEM_RENAMES.get(name, name)


HORIZONTAL = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}

NOT_SOLID = re.compile(r"air|torch|sign|door|fence|pane|bars|slab|stairs|carpet|flower|poppy|dandelion|"
                       r"short_grass|fern|bush|sapling|ladder|rail|button|lever|plate|wire|repeater|"
                       r"comparator|bed|chest|cake|cobweb|vine|lily|mushroom$|wheat|carrots|potatoes|"
                       r"reeds|sugar_cane|snow$|water|lava|fire|portal|skull|head|pot|wall$|cauldron|"
                       r"brewing|enchanting|anvil|piston_head|hopper|daylight|tripwire|cocoa|stem|"
                       r"leaves|glass$|glowstone|ice$|farmland|soul_sand|nether_wart|dragon_egg|end_portal")


def solid(name):
    return not NOT_SOLID.search(name.split(":", 1)[1])


def fix_neighbours(grid, W, H, Lz):
    """Recompute the states 1.13's flattening derived from neighbouring blocks."""
    def at(x, y, z):
        if 0 <= x < W and 0 <= y < H and 0 <= z < Lz:
            return grid[(y * Lz + z) * W + x]
        return ("minecraft:air", {})

    for y in range(H):
        for z in range(Lz):
            for x in range(W):
                name, props = grid[(y * Lz + z) * W + x]
                n = name.split(":", 1)[1]
                if n.endswith("_door") and props.get("half") == "lower":
                    up_name, up = at(x, y + 1, z)
                    if up_name == name:
                        props["hinge"], props["powered"] = up["hinge"], up["powered"]
                        up["facing"], up["open"] = props["facing"], props["open"]
                elif n.endswith("_stairs"):
                    props["shape"] = stair_shape(at, x, y, z, props)
                elif n.endswith("_fence") or n in ("iron_bars", "glass_pane") or n.endswith("_pane"):
                    is_fence = n.endswith("_fence")
                    for d, (dx, dz) in HORIZONTAL.items():
                        on, op = at(x + dx, y, z + dz)
                        o = on.split(":", 1)[1]
                        if is_fence:
                            ok = o.endswith("_fence") and ("nether" in o) == ("nether" in n) or \
                                (o.endswith("fence_gate") and op.get("facing") in (CW[d], CCW[d]))
                        else:
                            ok = o in ("iron_bars", "glass") or o.endswith("pane") or o.endswith("_wall")
                        props[d] = "true" if ok or solid(on) else "false"
                elif n.endswith("_wall") and "sign" not in n and "torch" not in n:
                    conn = {}
                    for d, (dx, dz) in HORIZONTAL.items():
                        on, op = at(x + dx, y, z + dz)
                        o = on.split(":", 1)[1]
                        ok = (o.endswith("_wall") and "sign" not in o and "torch" not in o) or "bars" in o \
                            or o.endswith("pane") or solid(on) or \
                            (o.endswith("fence_gate") and op.get("facing") in (CW[d], CCW[d]))
                        conn[d] = ok
                    tall = solid(at(x, y + 1, z)[0])
                    for d, ok in conn.items():
                        props[d] = ("tall" if tall else "low") if ok else "none"
                    straight = (conn["north"] and conn["south"] and not conn["east"] and not conn["west"]) or \
                        (conn["east"] and conn["west"] and not conn["north"] and not conn["south"])
                    props["up"] = "false" if straight and not tall else "true"
                elif n in ("chest", "trapped_chest"):
                    f = props.get("facing", "north")
                    props["type"] = "single"
                    for side, kind in ((CW[f], "left"), (CCW[f], "right")):
                        dx, dz = HORIZONTAL[side]
                        on, op = at(x + dx, y, z + dz)
                        if on == name and op.get("facing", "north") == f:
                            props["type"] = kind
                            break


def stair_shape(at, x, y, z, props):
    """Vanilla StairBlock.getStairsShape."""
    f, half = props["facing"], props["half"]

    def stair(d):
        dx, dz = HORIZONTAL[d]
        on, op = at(x + dx, y, z + dz)
        return op if on.endswith("_stairs") and op.get("half") == half else None

    front = stair(f)
    if front and front["facing"] in (CW[f], CCW[f]):
        side = stair(OPPOSITE[front["facing"]])
        if not side or side["facing"] != f:
            return "outer_left" if front["facing"] == CCW[f] else "outer_right"
    back = stair(OPPOSITE[f])
    if back and back["facing"] in (CW[f], CCW[f]):
        side = stair(back["facing"])
        if not side or side["facing"] != f:
            return "inner_left" if back["facing"] == CCW[f] else "inner_right"
    return "straight"


# ---------------------------------------------------------------------------
# Block entities and paintings
# ---------------------------------------------------------------------------

OLD_ENTITY_IDS = {
    "PigZombie": "zombified_piglin", "LavaSlime": "magma_cube", "CaveSpider": "cave_spider",
    "Ozelot": "ocelot", "VillagerGolem": "iron_golem", "MushroomCow": "mooshroom",
    "SnowMan": "snow_golem", "EnderDragon": "ender_dragon", "WitherBoss": "wither",
}


def entity_id(old):
    if ":" in old:
        return old
    return "minecraft:" + OLD_ENTITY_IDS.get(old, re.sub(r"(?<!^)(?=[A-Z])", "_", old).lower())


def convert_items(conv, items):
    out = []
    for it in items or []:
        iid = conv.item(it["id"], it.get("Damage", 0))
        if iid and iid != "minecraft:air":
            out.append({"Slot": B(it.get("Slot", 0)), "id": iid, "count": max(1, it.get("Count", 1))})
    return out


def convert_tile_entity(conv, te, name, props):
    """Return (block entity id, data) or None; may adjust the block's props."""
    tid = te.get("id", "")
    if tid == "Sign":
        lines = [te.get(f"Text{i}", "") for i in range(1, 5)]
        blank = {"messages": ["", "", "", ""], "color": "black", "has_glowing_text": B(0)}
        return "minecraft:sign", {"front_text": {**blank, "messages": lines}, "back_text": blank,
                                  "is_waxed": B(0)}
    if tid in ("Chest", "Trap", "Furnace", "Cauldron"):
        be = {"Chest": "minecraft:chest", "Trap": "minecraft:dispenser", "Furnace": "minecraft:furnace",
              "Cauldron": "minecraft:brewing_stand"}[tid]
        if name.endswith("trapped_chest"):
            be = "minecraft:trapped_chest"
        return be, {"Items": convert_items(conv, te.get("Items"))}
    if tid == "MobSpawner":
        data = {"SpawnData": {"entity": {"id": entity_id(te.get("EntityId", "Pig"))}}}
        for k in ("Delay", "MinSpawnDelay", "MaxSpawnDelay", "SpawnCount", "MaxNearbyEntities",
                  "RequiredPlayerRange", "SpawnRange"):
            if k in te:
                data[k] = S(te[k])
        return "minecraft:mob_spawner", data
    if tid == "Music":
        props["note"] = str(te.get("note", 0))
        return None
    if tid == "Control":
        return "minecraft:command_block", {"Command": te.get("Command", "")}
    if tid == "RecordPlayer":
        return "minecraft:jukebox", {}
    conv.unmapped[f"tile entity {tid}"] += 1
    return None


MOTIVES = {"SkullAndRoses": "skull_and_roses", "BurningSkull": "burning_skull", "DonkeyKong": "donkey_kong"}
FACINGS = ["south", "west", "north", "east"]  # pre-1.8 Direction values


def convert_painting(p, x0, y0, z0):
    """Pre-1.8 painting -> 1.21.11 (block_pos is the block the painting occupies; Pos is centred on it)."""
    if "Direction" in p:
        d = p["Direction"]
    else:  # older "Dir": 0 and 2 swapped relative to Direction
        d = {0: 2, 1: 1, 2: 0, 3: 3}[p["Dir"]]
    facing = FACINGS[d]
    # pre-1.8 Tile* is the block the painting hangs on; 1.8+ is the block it sits in.
    dx, dz = HORIZONTAL[facing]
    tx, ty, tz = p["TileX"] + dx, p["TileY"], p["TileZ"] + dz
    motive = p.get("Motive", "Kebab")
    variant = "minecraft:" + MOTIVES.get(motive, motive.lower())
    rel = (tx - x0, ty - y0, tz - z0)
    pos = [D(rel[0] + 0.5 - dx * 0.46875), D(rel[1] + 0.5), D(rel[2] + 0.5 - dz * 0.46875)]
    return rel, {"Pos": pos, "Id": "minecraft:painting",
                 "Data": {"id": "minecraft:painting", "facing": B(d), "variant": variant,
                          "block_pos": IA(rel), "Pos": pos}}


# ---------------------------------------------------------------------------
# Extraction
# ---------------------------------------------------------------------------

def varint(out, v):
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            out.append(b | 0x80)
        else:
            out.append(b)
            return


def extract(world, conv, b):
    x0, z0, y0 = b["source_x"], b["source_z"], b["source_y"]
    W, Lz = b["size_x"], b["size_z"]
    if b["type"] == "mineshaft_section":
        top = y0 + 4
    else:
        top = 64
        for x in range(x0, x0 + W):
            for z in range(z0, z0 + Lz):
                for y in range(128, 64, -1):
                    if world.block(x, y, z)[0] != 0:
                        top = max(top, y)
                        break
    H = top - y0 + 1

    grid, raw = [], []
    for y in range(y0, top + 1):
        for z in range(z0, z0 + Lz):
            for x in range(x0, x0 + W):
                bid, meta = world.block(x, y, z)
                raw.append(bid)
                grid.append(conv.block(bid, meta))
    fix_neighbours(grid, W, H, Lz)

    block_entities = []
    for (tx, ty, tz), te in world.tile_entities.items():
        if x0 <= tx < x0 + W and z0 <= tz < z0 + Lz and y0 <= ty <= top:
            i = ((ty - y0) * Lz + (tz - z0)) * W + (tx - x0)
            res = convert_tile_entity(conv, te, *grid[i])
            if res:
                be_id, data = res
                block_entities.append({"Pos": IA([tx - x0, ty - y0, tz - z0]), "Id": be_id, "Data": data})

    entities = []
    for p in world.paintings:
        rel, ent = convert_painting(p, x0, y0, z0)
        if 0 <= rel[0] < W and 0 <= rel[1] < H and 0 <= rel[2] < Lz:
            entities.append(ent)

    palette, data = {}, bytearray()
    for name, props in grid:
        key = name + ("[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]" if props else "")
        varint(data, palette.setdefault(key, len(palette)))

    mace = {k: (",".join(v) if isinstance(v, list) else v) for k, v in b.items() if k != "name"}
    schem = {"Schematic": {
        "Version": 3,
        "DataVersion": DATA_VERSION,
        "Metadata": {"Name": b["name"], "Author": "Mace (GPLv3)", "Mace": mace},
        "Width": S(W), "Height": S(H), "Length": S(Lz),
        "Offset": IA([0, 0, 0]),
        "Blocks": {"Palette": {k: v for k, v in palette.items()}, "Data": bytes(data),
                   "BlockEntities": block_entities},
        "Entities": entities,
    }}
    stats = {"size": (W, H, Lz), "palette": len(palette), "block_entities": len(block_entities),
             "paintings": len(entities), "solid": sum(1 for r in raw if r != 0)}
    return schem, stats


def slug(name):
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=CACHE / "schem")
    ap.add_argument("--only", help="only buildings whose name contains this (case-insensitive)")
    args = ap.parse_args()

    legacy = fetch_sources()
    world = SourceWorld(CACHE / "src" / "Resources" / "Mace" / "region")
    conv = Converter(legacy)
    buildings = read_index(CACHE / "src")
    if args.only:
        buildings = [b for b in buildings if args.only.lower() in b["name"].lower()]

    report, used = [], set()
    for b in sorted(buildings, key=lambda b: (b["type"], b["name"].lower())):
        schem, st = extract(world, conv, b)
        name = slug(b["name"])
        if (b["type"], name) in used:
            name += f"_{b['source_x']}_{b['source_z']}"
        used.add((b["type"], name))
        path = args.out / b["type"] / f"{name}.schem"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(gzip.compress(write_nbt(schem)))
        W, H, Lz = st["size"]
        report.append(f"{b['type']:<20} {name:<40} {W:>3}x{H:>3}x{Lz:<3} solid={st['solid']:<6} "
                      f"palette={st['palette']:<4} be={st['block_entities']:<3} paintings={st['paintings']:<2} "
                      f"src=({b['source_x']},{b['source_y']},{b['source_z']}) [{','.join(b['files'])}]"
                      + (f" aka {', '.join(b['aliases'])}" if b.get("aliases") else ""))
        if st["solid"] == 0:
            report[-1] += "  EMPTY"
    report.append("")
    report.append(f"{len(buildings)} buildings; unmapped: " +
                  (", ".join(f"{k} x{v}" for k, v in conv.unmapped.most_common()) or "none"))
    (args.out / "report.txt").write_text("\n".join(report) + "\n")
    print("\n".join(report))


if __name__ == "__main__":
    sys.exit(main())
