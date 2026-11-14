"""The pieces Level 143's buildings are made of: the three styles, walls with their frames and
windows, roofs, doors, furniture, trees and lamps. Everything draws into a schem.Build."""

from dataclasses import dataclass, field

from schem import GROUND, OPPOSITE, LEFT, RIGHT, STEP

WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"}
FULL = {  # stairs/slab base -> its full block
    "brick": "bricks", "stone_brick": "stone_bricks", "mossy_stone_brick": "mossy_stone_bricks",
    "mud_brick": "mud_bricks", "deepslate_tile": "deepslate_tiles", "deepslate_brick": "deepslate_bricks",
    "polished_blackstone_brick": "polished_blackstone_bricks", "tuff_brick": "tuff_bricks",
    "cobblestone": "cobblestone", "mossy_cobblestone": "mossy_cobblestone", "cobbled_deepslate": "cobbled_deepslate",
    "blackstone": "blackstone", "polished_blackstone": "polished_blackstone", "granite": "granite",
    "polished_granite": "polished_granite", "red_sandstone": "red_sandstone", "sandstone": "sandstone",
    "smooth_sandstone": "smooth_sandstone", "smooth_red_sandstone": "smooth_red_sandstone",
    "stone": "stone", "andesite": "andesite", "polished_andesite": "polished_andesite",
    "polished_deepslate": "polished_deepslate", "prismarine_brick": "prismarine_bricks",
    "resin_brick": "resin_bricks", "nether_brick": "nether_bricks",
}


@dataclass(frozen=True)
class Mat:
    """A roofing or stair material: its stairs, slab and full block."""
    stairs: str
    slab: str
    block: str


def mat(base):
    return Mat(f"{base}_stairs", f"{base}_slab", f"{base}_planks" if base in WOODS else FULL[base])


@dataclass
class Style:
    key: str
    file: str                 # the theme file named in buildings' `files`
    found: tuple              # plinths and foundations
    infill: tuple             # house walls
    woods: tuple              # frames, floors, doors, furniture
    roofs: tuple              # roof Mats
    stone: tuple              # dressed stone for the big buildings
    panes: tuple
    light: str                # lantern
    wall_torch: str
    torch: str
    timbered: float           # how many houses show their frame
    flat: float               # how many roofs are flat
    pitch2: float             # how many pitched roofs are steep
    trees: tuple              # tree kinds (see tree())
    flowers: tuple
    beds: tuple
    crops: tuple
    extra: dict = field(default_factory=dict)


TIMBER = Style(
    key="timber", file="Timber Town",
    found=("cobblestone", "stone_bricks", "mossy_cobblestone"),
    infill=("white_terracotta", "calcite", "birch_planks", "light_gray_terracotta"),
    woods=("spruce", "dark_oak", "oak"),
    roofs=(mat("dark_oak"), mat("spruce"), mat("cobbled_deepslate"), mat("deepslate_tile")),
    stone=("stone_bricks", "cobblestone", "andesite"),
    panes=("glass_pane",),
    light="lantern", wall_torch="wall_torch", torch="torch",
    timbered=0.8, flat=0.05, pitch2=0.55,
    trees=("oak", "spruce", "birch"),
    flowers=("poppy", "dandelion", "oxeye_daisy", "cornflower", "azure_bluet", "short_grass", "fern"),
    beds=("red", "blue", "green", "brown", "white"),
    crops=("wheat", "carrots", "potatoes", "beetroots"),
)

OCHRE = Style(
    key="ochre", file="Ochre Town",
    found=("smooth_sandstone", "mud_bricks", "packed_mud"),
    infill=("white_terracotta", "smooth_sandstone", "calcite", "terracotta", "orange_terracotta"),
    woods=("acacia", "jungle", "oak"),
    roofs=(mat("brick"), mat("granite"), mat("mud_brick"), mat("red_sandstone")),
    stone=("smooth_sandstone", "cut_sandstone", "mud_bricks"),
    panes=("glass_pane", "air"),
    light="lantern", wall_torch="wall_torch", torch="torch",
    timbered=0.15, flat=0.55, pitch2=0.0,
    trees=("acacia", "olive", "cypress"),
    flowers=("dead_bush", "short_dry_grass", "short_grass", "allium", "orange_tulip"),
    beds=("orange", "yellow", "red", "white", "cyan"),
    crops=("wheat", "carrots", "potatoes", "beetroots"),
)

ASHEN = Style(
    key="ashen", file="Ashen Town",
    found=("polished_blackstone_bricks", "deepslate_bricks", "cobbled_deepslate"),
    infill=("deepslate_bricks", "tuff_bricks", "polished_blackstone_bricks", "gray_terracotta", "cobbled_deepslate"),
    woods=("dark_oak", "spruce", "pale_oak"),
    roofs=(mat("deepslate_tile"), mat("blackstone"), mat("dark_oak"), mat("polished_blackstone_brick")),
    stone=("deepslate_bricks", "polished_blackstone_bricks", "tuff_bricks"),
    panes=("gray_stained_glass_pane", "glass_pane", "black_stained_glass_pane"),
    light="soul_lantern", wall_torch="soul_wall_torch", torch="soul_torch",
    timbered=0.35, flat=0.1, pitch2=0.8,
    trees=("dark_oak", "spruce", "pale_oak"),
    flowers=("fern", "dead_bush", "brown_mushroom", "wither_rose", "short_grass"),
    beds=("black", "gray", "purple", "red"),
    crops=("wheat", "potatoes", "beetroots"),
)

STYLES = (TIMBER, OCHRE, ASHEN)


def pick(rng, seq):
    return seq[rng.randrange(len(seq))]


# ── Walls ────────────────────────────────────────────────────────────────

@dataclass
class Shell:
    """A box of storeys: outer walls x0..x1, z0..z1; storey k's floor at y = k * h."""
    x0: int
    z0: int
    x1: int
    z1: int
    floors: int
    h: int = 4

    @property
    def top(self):
        return self.floors * self.h

    def inner(self):
        return self.x0 + 1, self.z0 + 1, self.x1 - 1, self.z1 - 1


@dataclass
class Look:
    """How a house's walls look."""
    found: str
    infill: str
    wood: str
    floor: str
    pane: str
    timbered: bool
    post_every: int = 4
    window_every: int = 3
    window_tall: bool = False
    shutters: bool = False

    @property
    def log(self):
        return f"stripped_{self.wood}_log" if self.wood == "pale_oak" else f"{self.wood}_log"


def look(rng, st, timbered=None):
    wood = pick(rng, st.woods)
    t = rng.random() < st.timbered if timbered is None else timbered
    infill = pick(rng, st.infill)
    if not t and infill.endswith("_planks"):
        infill = pick(rng, st.found)
    return Look(found=pick(rng, st.found), infill=infill, wood=wood,
                floor=f"{pick(rng, st.woods)}_planks", pane=pick(rng, st.panes), timbered=t,
                post_every=rng.choice((3, 4)), window_every=rng.choice((2, 3, 3, 4)),
                window_tall=rng.random() < 0.35, shutters=rng.random() < 0.4)


def shell(b, s, lk, ground_floor=True):
    """Floors, walls, frame. Windows and doors are cut afterwards."""
    x0, z0, x1, z1 = s.x0, s.z0, s.x1, s.z1
    if ground_floor:
        b.fill(x0, 0, z0, x1, 0, z1, lk.floor)
        b.ring(x0, z0, x1, z1, 0, 0, lk.found)
    for k in range(1, s.floors + 1):
        b.fill(x0 + 1, k * s.h, z0 + 1, x1 - 1, k * s.h, z1 - 1, lk.floor)
    b.ring(x0, z0, x1, z1, 1, s.top, lk.infill)
    if not lk.timbered:
        b.ring(x0, z0, x1, z1, 1, 1, lk.found)
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            if lk.infill != lk.found:
                b.fill(x, 1, z, x, s.top, z, lk.found)
            else:
                b.fill(x, 1, z, x, s.top, z, lk.log, axis="y")
        return
    # exposed frame: corner and wall posts, a beam at every floor
    b.ring(x0, z0, x1, z1, 1, 1, lk.found)
    for k in range(1, s.floors + 1):
        y = k * s.h
        for x in range(x0, x1 + 1):
            b.set(x, y, z0, lk.log, axis="x")
            b.set(x, y, z1, lk.log, axis="x")
        for z in range(z0, z1 + 1):
            b.set(x0, y, z, lk.log, axis="z")
            b.set(x1, y, z, lk.log, axis="z")
    for x, z in posts(s, lk.post_every):
        b.fill(x, 2, z, x, s.top - 1, z, lk.log, axis="y")
        for k in range(1, s.floors + 1):
            b.set(x, k * s.h, z, lk.log, axis="y")


def posts(s, every):
    out = set()
    for x in edge_points(s.x0, s.x1, every):
        out.add((x, s.z0))
        out.add((x, s.z1))
    for z in edge_points(s.z0, s.z1, every):
        out.add((s.x0, z))
        out.add((s.x1, z))
    return out


def edge_points(a, b, every):
    """Evenly spread points from a to b, ends included, gaps about `every`."""
    n = max(1, round((b - a) / every))
    return sorted({a + round(i * (b - a) / n) for i in range(n + 1)})


def wall_cells(s, side):
    """The cells of one wall, corners excluded, and the way out of it."""
    if side == "north":
        return [(x, s.z0) for x in range(s.x0 + 1, s.x1)], "north"
    if side == "south":
        return [(x, s.z1) for x in range(s.x0 + 1, s.x1)], "south"
    if side == "west":
        return [(s.x0, z) for z in range(s.z0 + 1, s.z1)], "west"
    return [(s.x1, z) for z in range(s.z0 + 1, s.z1)], "east"


def windows(b, s, lk, sides=("north", "south", "east", "west"), skip=(), floors=None, every=None):
    """Panes along the walls, between the frame's posts, on every storey."""
    every = every or lk.window_every
    frame = posts(s, lk.post_every) if lk.timbered else set()
    for side in sides:
        cells, out = wall_cells(s, side)
        n = len(cells)
        for i, (x, z) in enumerate(cells):
            if (x, z) in frame or (x, z) in skip:
                continue
            if every > 1 and (i - n // 2) % every != 0:
                continue
            for k in (floors if floors is not None else range(s.floors)):
                ys = (k * s.h + 2, k * s.h + 3) if lk.window_tall and s.h >= 4 else (k * s.h + 2,)
                for y in ys:
                    if lk.pane == "air":
                        b.set(x, y, z, "air")
                    else:
                        b.set(x, y, z, lk.pane)
                if lk.shutters:
                    dx, dz = STEP[out]
                    l, r = LEFT[out], RIGHT[out]
                    for side_dir in (l, r):
                        sx, sz = STEP[side_dir]
                        if (x + sx, z + sz) in frame:
                            continue
                        b.set(x + dx + sx, ys[0], z + dz + sz, f"{lk.wood}_trapdoor", facing=side_dir,
                              half="bottom", open="true", powered="false", waterlogged="false")


def front_door(b, s, lk, x=None, wide=False, path=True):
    """A door in the front (north) wall, its step and a path to the street. Returns its x."""
    if x is None:
        x = (s.x0 + s.x1) // 2
    xs = (x, x + 1) if wide else (x,)
    for i, dx in enumerate(xs):
        b.set(dx, 0, s.z0, lk.floor)
        b.door(dx, 1, s.z0, "south", lk.wood, hinge="left" if i == 0 else "right")
        if lk.timbered:
            b.set(dx, 3, s.z0, lk.log, axis="x")
        else:
            b.set(dx, 3, s.z0, lk.infill)
        if path:
            b.path(dx, 0, dx, s.z0 - 1)
    return x


def door_in(b, x, y, z, facing, wood):
    b.door(x, y, z, facing, wood)


def ladder(b, x, z, y0, y1, facing):
    """A ladder from y0 to y1, against the block behind it (opposite facing)."""
    for y in range(y0, y1 + 1):
        b.set(x, y, z, "ladder", facing=facing, waterlogged="false")


# ── Roofs ────────────────────────────────────────────────────────────────

def _axes(along, x0, z0, x1, z1):
    if along == "x":
        return x0, x1, z0, z1, (lambda a, c: (a, c)), ("south", "north"), ("west", "east")
    return z0, z1, x0, x1, (lambda a, c: (c, a)), ("east", "west"), ("north", "south")


def gable(b, x0, z0, x1, z1, yb, m, along="x", fill=None, pitch=1, over=1, end_over=1):
    """A ridged roof over the walls x0..x1, z0..z1 whose top course is at yb: eaves at yb hanging
    `over` past the long walls, gables filled with `fill`. Returns the ridge's y."""
    A0, A1, C0, C1, at, (lo_face, hi_face), _ = _axes(along, x0, z0, x1, z1)
    ca, cb = C0 - over, C1 + over
    i, y = 0, yb
    while True:
        cl, cr = ca + i, cb - i
        if cl > cr:
            return y - 1
        for a in range(A0 - end_over, A1 + end_over + 1):
            if cl == cr:
                b.set(*_xyz(at, a, y, cl), m.slab, type="bottom", waterlogged="false")
                if pitch == 2:
                    b.set(*_xyz(at, a, y - 1, cl), m.block)
            else:
                b.set(*_xyz(at, a, y, cl), m.stairs, facing=lo_face, half="bottom", waterlogged="false")
                b.set(*_xyz(at, a, y, cr), m.stairs, facing=hi_face, half="bottom", waterlogged="false")
                if pitch == 2 and i > 0:
                    b.set(*_xyz(at, a, y - 1, cl), m.block)
                    b.set(*_xyz(at, a, y - 1, cr), m.block)
        if fill:
            for c in range(max(cl + 1, C0), min(cr - 1, C1) + 1):
                for yy in range(y - pitch + 1, y + 1):
                    if yy > yb:
                        b.set(*_xyz(at, A0, yy, c), fill)
                        b.set(*_xyz(at, A1, yy, c), fill)
        if cl == cr:
            return y
        i += 1
        y += pitch


def _xyz(at, a, y, c):
    x, z = at(a, c)
    return x, y, z


def hip(b, x0, z0, x1, z1, yb, m, over=1):
    """A roof sloping on all four sides."""
    xa, xb, za, zb, y = x0 - over, x1 + over, z0 - over, z1 + over, yb
    while xa <= xb and za <= zb:
        if xa == xb or za == zb:
            b.fill(xa, y, za, xb, y, zb, m.slab, type="bottom", waterlogged="false")
            return y
        for x in range(xa, xb + 1):
            b.set(x, y, za, m.stairs, facing="south", half="bottom", waterlogged="false")
            b.set(x, y, zb, m.stairs, facing="north", half="bottom", waterlogged="false")
        for z in range(za + 1, zb):
            b.set(xa, y, z, m.stairs, facing="east", half="bottom", waterlogged="false")
            b.set(xb, y, z, m.stairs, facing="west", half="bottom", waterlogged="false")
        xa, xb, za, zb, y = xa + 1, xb - 1, za + 1, zb - 1, y + 1
    return y - 1


def flat(b, x0, z0, x1, z1, yb, edge, cap=None):
    """A flat roof at yb with a parapet of `edge` around it, capped with `cap` slabs."""
    b.fill(x0, yb, z0, x1, yb, z1, edge)
    b.ring(x0, z0, x1, z1, yb + 1, yb + 1, edge)
    if cap:
        b.ring(x0, z0, x1, z1, yb + 2, yb + 2, cap, type="bottom", waterlogged="false")
    return yb + 1


def shed(b, x0, z0, x1, z1, yb, m, high="south", over=1):
    """A lean-to: one slope rising towards `high`, a stair then a top slab per half block."""
    vertical = high in ("north", "south")
    cs = list(range(z0 - over, z1 + over + 1)) if vertical else list(range(x0 - over, x1 + over + 1))
    if high in ("north", "west"):
        cs.reverse()
    span = range(x0 - over, x1 + over + 1) if vertical else range(z0 - over, z1 + over + 1)
    for i, c in enumerate(cs):
        for a in span:
            x, z = (a, c) if vertical else (c, a)
            if i % 2 == 0:
                b.set(x, yb + i // 2, z, m.stairs, facing=high, half="bottom", waterlogged="false")
            else:
                b.set(x, yb + i // 2, z, m.slab, type="top", waterlogged="false")
    return yb + len(cs) // 2


def roof(b, rng, st, s, lk, m=None, kind=None, along=None):
    """The style's roof over a shell: gabled, hipped or flat. Returns the top y."""
    m = m or pick(rng, st.roofs)
    w, d = s.x1 - s.x0 + 1, s.z1 - s.z0 + 1
    if kind is None:
        r = rng.random()
        kind = "flat" if r < st.flat else "hip" if r < st.flat + 0.2 and abs(w - d) <= 4 else "gable"
    if kind == "flat":
        return flat(b, s.x0, s.z0, s.x1, s.z1, s.top, lk.found if lk.found != lk.infill else lk.infill,
                    cap=None)
    if kind == "hip":
        return hip(b, s.x0, s.z0, s.x1, s.z1, s.top, m)
    along = along or ("x" if w >= d else "z")
    pitch = 2 if rng.random() < st.pitch2 else 1
    return gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along=along, fill=lk.infill, pitch=pitch)


def chimney(b, x, z, y0, y1, block, smoke=True):
    b.fill(x, y0, z, x, y1, z, block)
    if smoke:
        b.set(x, y1 + 1, z, "campfire", facing="north", lit="true", signal_fire="false", waterlogged="false")


# ── Furniture ────────────────────────────────────────────────────────────

class Room:
    """The floor of a room (inside the walls) at y, and what's been put in it."""

    def __init__(self, b, x0, z0, x1, z1, y, keep=()):
        self.b, self.x0, self.z0, self.x1, self.z1, self.y = b, x0, z0, x1, z1, y
        self.keep = set(keep)

    def free(self, x, z):
        return self.x0 <= x <= self.x1 and self.z0 <= z <= self.z1 and (x, z) not in self.keep \
            and self.b.empty(x, self.y, z)

    def wall_spots(self):
        """Cells along the walls, each with the way into the room."""
        out = []
        for x in range(self.x0, self.x1 + 1):
            out.append((x, self.z0, "south"))
            out.append((x, self.z1, "north"))
        for z in range(self.z0 + 1, self.z1):
            out.append((self.x0, z, "east"))
            out.append((self.x1, z, "west"))
        return [(x, z, f) for x, z, f in out if self.free(x, z)]

    def keep_out(self, x, z):
        self.keep.add((x, z))


def furnish(room, rng, st, lk, items, light=True, carpet=None):
    """Puts each item against a wall: a block name, "bed", "chest", "table" or a callable."""
    b, y = room.b, room.y
    spots = room.wall_spots()
    rng.shuffle(spots)
    for item in items:
        for i, (x, z, f) in enumerate(spots):
            if not room.free(x, z):
                continue
            if item == "bed":
                dx, dz = STEP[f]
                if not room.free(x + dx, z + dz) or not inside_room(room, x + dx, z + dz):
                    continue
                b.bed(x + dx, y, z + dz, OPPOSITE[f], pick(rng, st.beds))
                room.keep_out(x + 2 * dx, z + 2 * dz)
            elif item == "chest":
                b.chest(x, y, z, f)
            elif item == "barrel":
                b.barrel(x, y, z, facing="up")
            elif item in ("furnace", "smoker", "blast_furnace"):
                b.set(x, y, z, item, facing=f, lit="false")
            elif item in ("loom", "stonecutter", "grindstone", "lectern", "anvil"):
                extra = {"face": "floor"} if item == "grindstone" else {}
                if item == "lectern":
                    extra = {"has_book": "false", "powered": "false"}
                b.set(x, y, z, item, facing=f if item != "anvil" else LEFT[f], **extra)
            elif item == "shelf":
                b.set(x, y, z, "bookshelf")
                b.set(x, y + 1, z, "bookshelf")
            elif item == "plant":
                b.set(x, y, z, pick(rng, ("potted_fern", "potted_red_tulip", "potted_azure_bluet", "potted_oak_sapling",
                                          "potted_spruce_sapling", "potted_dead_bush", "potted_cactus")))
            elif callable(item):
                item(b, x, y, z, f)
            else:
                b.set(x, y, z, item)
            room.keep_out(x, z)
            del spots[i]
            break
    if carpet and room.x1 - room.x0 >= 3 and room.z1 - room.z0 >= 3:
        for x in range(room.x0 + 1, room.x1):
            for z in range(room.z0 + 1, room.z1):
                if room.free(x, z):
                    b.set(x, y, z, f"{carpet}_carpet")
    if light:
        hang(b, (room.x0 + room.x1) // 2, (room.z0 + room.z1) // 2, y, st)


def inside_room(room, x, z):
    return room.x0 <= x <= room.x1 and room.z0 <= z <= room.z1


def hang(b, x, z, y_floor, st, height=3):
    """A lantern hanging under the ceiling over (x, z), if there's a ceiling."""
    top = y_floor + height
    if b.empty(x, top, z) or b.get(x, top, z) in ("ladder",):
        return
    b.set(x, top - 1, z, st.light, hanging="true", waterlogged="false")


def table(b, x, y, z, wood, chairs=(), plate=True):
    b.set(x, y, z, f"{wood}_fence", waterlogged="false")
    if plate:
        b.set(x, y + 1, z, f"{wood}_pressure_plate", powered="false")
    for f in chairs:
        dx, dz = STEP[f]
        if b.empty(x + dx, y, z + dz):
            b.set(x + dx, y, z + dz, f"{wood}_stairs", facing=f, half="bottom", waterlogged="false")


# ── Outdoors ─────────────────────────────────────────────────────────────

def lamp_post(b, x, z, st, wood="oak", h=3):
    b.fill(x, 1, z, x, h, z, f"{wood}_fence", waterlogged="false")
    b.set(x, h + 1, z, st.light, hanging="false", waterlogged="false")


def bench(b, x, z, facing, wood, n=2):
    dx, dz = STEP[RIGHT[facing]]
    for i in range(n):
        b.set(x + i * dx, 1, z + i * dz, f"{wood}_stairs", facing=OPPOSITE[facing], half="bottom", waterlogged="false")


def leaves(kind):
    return {"olive": "azalea_leaves", "cypress": "spruce_leaves", "pale_oak": "pale_oak_leaves"}.get(kind, f"{kind}_leaves")


def log_of(kind):
    return {"olive": "oak_log", "cypress": "spruce_log"}.get(kind, f"{kind}_log")


def tree(b, rng, x, z, kind, y=1, size=None):
    """A tree standing on (x, y - 1, z). Kinds: oak, birch, spruce, dark_oak, acacia, olive,
    cypress, pale_oak."""
    lv = leaves(kind)
    log = log_of(kind)
    L = dict(persistent="true", distance="1", waterlogged="false")

    def blob(cx, cy, cz, r, ry=None, keep=0.85):
        ry = ry or r
        for dy in range(-ry, ry + 1):
            for dz in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    d = (dx / (r + 0.5)) ** 2 + (dz / (r + 0.5)) ** 2 + (dy / (ry + 0.5)) ** 2
                    if d <= 1 and (d < 0.6 or rng.random() < keep) and b.empty(cx + dx, cy + dy, cz + dz):
                        b.set(cx + dx, cy + dy, cz + dz, lv, **L)

    if kind in ("oak", "birch", "pale_oak"):
        h = size or rng.randint(4, 6) + (1 if kind == "birch" else 0)
        b.fill(x, y, z, x, y + h - 1, z, log, axis="y")
        blob(x, y + h - 1, z, 2, 2)
        b.set(x, y + h, z, lv, **L)
    elif kind == "dark_oak":
        h = size or rng.randint(4, 6)
        for dx in (0, 1):
            for dz in (0, 1):
                b.fill(x + dx, y, z + dz, x + dx, y + h - 1, z + dz, log, axis="y")
        for cx, cz in ((x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)):
            blob(cx, y + h, cz, 2, 1)
    elif kind in ("spruce", "cypress"):
        h = size or rng.randint(6, 9)
        b.fill(x, y, z, x, y + h - 1, z, log, axis="y")
        for i in range(2 if kind == "spruce" else 1, h + 1):
            if kind == "cypress":
                r = 0 if i == h else 1
            else:
                r = 0 if i == h else 1 if (h - i) % 2 == 1 else min(3, 1 + (h - i) // 3)
            for dz in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    if abs(dx) + abs(dz) <= max(r, 2 * r - 1) and b.empty(x + dx, y + i, z + dz):
                        b.set(x + dx, y + i, z + dz, lv, **L)
        b.set(x, y + h, z, lv, **L)
    elif kind == "acacia":
        h = size or rng.randint(4, 5)
        b.fill(x, y, z, x, y + h - 2, z, log, axis="y")
        d = pick(rng, ("north", "south", "east", "west"))
        dx, dz = STEP[d]
        b.set(x + dx, y + h - 1, z + dz, log, axis="y")
        tx, tz = x + dx, z + dz
        for r, yy in ((2, y + h), (1, y + h + 1)):
            for ox in range(-r, r + 1):
                for oz in range(-r, r + 1):
                    if abs(ox) + abs(oz) <= r + 1 and b.empty(tx + ox, yy, tz + oz):
                        b.set(tx + ox, yy, tz + oz, lv, **L)
    elif kind == "olive":
        h = size or rng.randint(2, 3)
        b.fill(x, y, z, x, y + h - 1, z, log, axis="y")
        blob(x, y + h, z, 2, 1, keep=0.7)
        for _ in range(3):
            b.set(x + rng.randint(-2, 2), y + h + rng.randint(-1, 1), z + rng.randint(-2, 2), "flowering_azalea_leaves", **L)
