"""The pieces Level 143's buildings are made of: the three styles, walls with their frames and
windows, roofs, doors, furniture, trees and lamps. Everything draws into a schem.Build."""

from dataclasses import dataclass, field

from schem import GROUND, OPPOSITE, LEFT, RIGHT, STEP, WOOL

WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "warped",
         "crimson"}
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
    "tuff": "tuff", "polished_tuff": "polished_tuff", "smooth_quartz": "smooth_quartz",
    "polished_diorite": "polished_diorite", "diorite": "diorite", "quartz": "quartz_block",
}

# A full block -> the stairs/slab base that goes with it (for sills, cornices, caps and steps).
TRIM = {
    "stone_bricks": "stone_brick", "mossy_stone_bricks": "mossy_stone_brick", "cracked_stone_bricks": "stone_brick",
    "chiseled_stone_bricks": "stone_brick", "cobblestone": "cobblestone", "mossy_cobblestone": "mossy_cobblestone",
    "andesite": "andesite", "polished_andesite": "polished_andesite", "stone": "stone",
    "deepslate_bricks": "deepslate_brick", "deepslate_tiles": "deepslate_tile", "cobbled_deepslate": "cobbled_deepslate",
    "polished_deepslate": "polished_deepslate", "polished_blackstone_bricks": "polished_blackstone_brick",
    "blackstone": "blackstone", "polished_blackstone": "polished_blackstone", "tuff_bricks": "tuff_brick",
    "tuff": "tuff", "polished_tuff": "polished_tuff", "smooth_sandstone": "smooth_sandstone",
    "sandstone": "sandstone", "cut_sandstone": "smooth_sandstone", "chiseled_sandstone": "sandstone",
    "red_sandstone": "red_sandstone", "smooth_red_sandstone": "smooth_red_sandstone", "mud_bricks": "mud_brick",
    "packed_mud": "mud_brick", "bricks": "brick", "granite": "granite", "polished_granite": "polished_granite",
    "calcite": "polished_diorite", "white_terracotta": "smooth_quartz", "diorite": "diorite",
    "polished_diorite": "polished_diorite", "resin_bricks": "resin_brick", "quartz_block": "quartz",
    "smooth_quartz": "smooth_quartz",
}
WALLED = {"cobblestone", "mossy_cobblestone", "stone_brick", "mossy_stone_brick", "andesite", "granite", "diorite",
          "brick", "mud_brick", "sandstone", "red_sandstone", "deepslate_brick", "deepslate_tile", "cobbled_deepslate",
          "polished_deepslate", "polished_blackstone_brick", "polished_blackstone", "blackstone", "tuff",
          "tuff_brick", "polished_tuff", "resin_brick", "nether_brick", "prismarine"}

WL = {"waterlogged": "false"}
LEAF = {"persistent": "true", "distance": "1", "waterlogged": "false"}


def trim_of(block, default="stone_brick"):
    """The stairs/slab base that goes with a full block: planks give their wood."""
    if block.endswith("_planks"):
        return block[:-len("_planks")]
    return TRIM.get(block, default)


def slab_of(block, default="smooth_stone_slab"):
    """The slab that goes with a full block (fountain rims, caps)."""
    base = trim_of(block, None)
    return f"{base}_slab" if base else default


def stairs_of(block, default="stone_brick_stairs"):
    base = trim_of(block, None)
    return f"{base}_stairs" if base and base != "smooth_stone" else default


def wall_of(block, default="cobblestone_wall"):
    base = trim_of(block, None)
    return f"{base}_wall" if base in WALLED else default


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
    shutter_woods: tuple = ()  # trapdoor woods for window shutters
    shutter: float = 0.4       # how many houses have shutters
    sills: float = 0.5         # how many houses have sills or flower boxes under the windows
    jetty: float = 0.0         # how many timbered houses overhang upstairs
    half_hip: float = 0.0      # how many gables are clipped at the ends
    cross: float = 0.0         # how many wide houses have a gable on the front
    hip: float = 0.2           # how many pitched roofs are hipped
    lancet: float = 0.0        # how many houses have tall pointed windows with hoods (gothic)
    buttress: float = 0.0      # how many stone houses have buttresses
    grille: float = 0.0        # how many houses have iron grilles on the ground floor windows
    pots: tuple = ("potted_red_tulip", "potted_azure_bluet", "potted_fern", "potted_oxeye_daisy")


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
    shutter_woods=("spruce", "dark_oak", "oak"), shutter=0.45, sills=0.65,
    jetty=0.6, half_hip=0.25, cross=0.45, hip=0.12,
    pots=("potted_red_tulip", "potted_poppy", "potted_dandelion", "potted_cornflower", "potted_oxeye_daisy",
          "potted_azure_bluet", "potted_pink_tulip"),
)

OCHRE = Style(
    key="ochre", file="Ochre Town",
    found=("smooth_sandstone", "mud_bricks", "packed_mud"),
    infill=("white_terracotta", "smooth_sandstone", "calcite", "terracotta", "orange_terracotta"),
    woods=("acacia", "jungle", "oak"),
    roofs=(mat("brick"), mat("granite"), mat("mud_brick"), mat("red_sandstone")),
    stone=("smooth_sandstone", "cut_sandstone", "mud_bricks"),
    panes=("glass_pane",),
    light="lantern", wall_torch="wall_torch", torch="torch",
    timbered=0.15, flat=0.55, pitch2=0.0,
    trees=("acacia", "olive", "cypress"),
    flowers=("dead_bush", "short_dry_grass", "short_grass", "allium", "orange_tulip"),
    beds=("orange", "yellow", "red", "white", "cyan"),
    crops=("wheat", "carrots", "potatoes", "beetroots"),
    shutter_woods=("warped", "jungle", "acacia", "warped"), shutter=0.75, sills=0.6,
    half_hip=0.0, cross=0.2, hip=0.45, grille=0.45,
    pots=("potted_flowering_azalea_bush", "potted_azalea_bush", "potted_cactus", "potted_orange_tulip",
          "potted_allium", "potted_red_tulip", "potted_dead_bush"),
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
    shutter_woods=("dark_oak", "spruce"), shutter=0.15, sills=0.35,
    jetty=0.35, half_hip=0.15, cross=0.5, hip=0.1, lancet=0.8, buttress=0.6,
    pots=("potted_wither_rose", "potted_fern", "potted_dead_bush", "potted_brown_mushroom", "potted_closed_eyeblossom"),
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
    shutter: str = ""         # the shutters' wood (default: the frame's)
    sill: str = ""            # "", "slab" (a ledge) or "box" (a ledge with a pot, a bush below the ground floor)
    lancet: bool = False      # tall windows under a little stone hood
    grille: bool = False      # iron bars in the ground floor windows
    jetty: bool = False       # upper storeys overhang the street by a block (timbered houses)
    buttress: bool = False

    @property
    def log(self):
        return f"stripped_{self.wood}_log" if self.wood == "pale_oak" else f"{self.wood}_log"

    @property
    def trim(self):
        """The stairs/slab base for sills, hoods and cornices."""
        return trim_of(self.found, "stone_brick")


def look(rng, st, timbered=None):
    wood = pick(rng, st.woods)
    t = rng.random() < st.timbered if timbered is None else timbered
    infill = pick(rng, st.infill)
    if not t and infill.endswith("_planks"):
        infill = pick(rng, st.found)
    lk = Look(found=pick(rng, st.found), infill=infill, wood=wood,
              floor=f"{pick(rng, st.woods)}_planks", pane=pick(rng, st.panes), timbered=t,
              post_every=rng.choice((3, 4)), window_every=rng.choice((2, 3, 3, 4)),
              window_tall=rng.random() < 0.35, shutters=rng.random() < 0.4)
    # the details, drawn after the rest so the old choices above keep their seeds
    lk.shutters = rng.random() < st.shutter
    lk.shutter = pick(rng, st.shutter_woods) if st.shutter_woods else wood
    r = rng.random()
    lk.sill = "" if r > st.sills else "box" if r < st.sills * 0.55 else "slab"
    lk.lancet = rng.random() < st.lancet
    if lk.lancet:
        lk.window_tall = True
    lk.grille = rng.random() < st.grille
    lk.jetty = t and rng.random() < st.jetty
    lk.buttress = not t and rng.random() < st.buttress
    if lk.pane == "air":
        lk.pane = "glass_pane"
    return lk


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


def is_pane(n):
    return n.endswith("_pane") or n == "iron_bars"


def solid(b, x, y, z):
    """Roughly: a full block a torch, sign or ladder can hang on."""
    n = b.get(x, y, z)
    if n in ("air", "cave_air", "water", "lava", "ladder") or is_pane(n):
        return False
    return not n.endswith(("_door", "_trapdoor", "_slab", "_stairs", "_fence", "_wall", "_sign", "torch", "lantern",
                           "_carpet", "_leaves", "_bed", "chain", "_gate", "potted", "_button", "candle", "rail"))


def windows(b, s, lk, sides=("north", "south", "east", "west"), skip=(), floors=None, every=None, details=True):
    """Panes along the walls, between the frame's posts, on every storey; with the look's
    shutters, sills or flower boxes, hoods and grilles. A window never goes behind a ladder."""
    every = every or lk.window_every
    frame = posts(s, lk.post_every) if lk.timbered else set()
    for side in sides:
        cells, out = wall_cells(s, side)
        dx, dz = STEP[out]
        n = len(cells)
        for i, (x, z) in enumerate(cells):
            if (x, z) in frame or (x, z) in skip:
                continue
            if every > 1 and (i - n // 2) % every != 0:
                continue
            for k in (floors if floors is not None else range(s.floors)):
                y0 = k * s.h + 2
                tall = (lk.window_tall or lk.lancet) and s.h >= 4
                ys = tuple(range(y0, min(y0 + (3 if lk.lancet and s.h >= 5 else 2 if tall else 1), (k + 1) * s.h)))
                if any(b.get(x - dx, y, z - dz) == "ladder" for y in ys) or not ys:
                    continue
                pane = "iron_bars" if lk.grille and k == 0 and lk.pane != "air" else lk.pane
                for y in ys:
                    b.set(x, y, z, pane, waterlogged="false") if pane != "air" else b.set(x, y, z, "air")
                if details:
                    window_dress(b, lk, x, z, ys, out, k, frame, skip)


def window_dress(b, lk, x, z, ys, out, k, frame=(), skip=()):
    """Shutters, a sill or flower box, a hood over one window (x, ys, z) in a wall facing `out`."""
    dx, dz = STEP[out]
    ox, oz = x + dx, z + dz
    if not b.inside(ox, oz):
        return
    if lk.shutters:
        for side_dir in (LEFT[out], RIGHT[out]):
            sx, sz = STEP[side_dir]
            wall = [b.get(x + sx, y, z + sz) for y in ys]
            if (x + sx, z + sz) in skip or any(is_pane(w) or w.endswith("_door") or w == "air" for w in wall):
                continue
            if all(b.empty(ox + sx, y, oz + sz) for y in ys):
                for y in ys:
                    b.set(ox + sx, y, oz + sz, f"{lk.shutter or lk.wood}_trapdoor", facing=out, half="bottom",
                          open="true", powered="false", waterlogged="false")
    if lk.sill and k > 0 and b.empty(ox, ys[0] - 1, oz):
        b.set(ox, ys[0] - 1, oz, f"{lk.shutter or lk.wood}_trapdoor", facing=OPPOSITE[out], half="top",
              open="false", powered="false", waterlogged="false")
        if lk.sill == "box" and b.empty(ox, ys[0], oz):
            b.set(ox, ys[0], oz, POTS[(x * 7 + z * 3 + ys[0]) % len(POTS)])
    elif lk.sill == "box" and k == 0 and b.empty(ox, 1, oz) and b.get(ox, 0, oz) == GROUND:
        b.set(ox, 1, oz, ("flowering_azalea_leaves", "azalea_leaves")[(x + z) % 2], **LEAF)
    if lk.lancet:
        hy = ys[-1] + 1
        if b.empty(ox, hy, oz) and solid(b, x, hy, z):
            b.set(ox, hy, oz, f"{lk.trim}_stairs", facing=OPPOSITE[out], half="bottom", waterlogged="false")


POTS = ("potted_red_tulip", "potted_azure_bluet", "potted_oxeye_daisy", "potted_cornflower", "potted_allium",
        "potted_dandelion", "potted_poppy", "potted_fern")


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


def door_dress(b, rng, st, lk, x, y, z, out="north", wide=False, torches=True):
    """Dresses a door at (x, y, z) in a wall facing `out`: a hood (timber), an arch with pots
    (ochre) or a pointed hood with soul torches (ashen)."""
    dx, dz = STEP[out]
    l, r = STEP[LEFT[out]], STEP[RIGHT[out]]
    w = 2 if wide else 1
    xl, zl = x + l[0], z + l[1]                     # the wall cell left of the door
    xr, zr = x + r[0] * w, z + r[1] * w             # ... and right of it
    top = y + 2
    if st.key == "timber":
        m = f"{lk.wood}_stairs"
        for i in range(-1, w + 1):
            cx, cz = x + r[0] * i + dx, z + r[1] * i + dz
            if b.empty(cx, top, cz):
                b.set(cx, top, cz, m, facing=OPPOSITE[out], half="bottom", waterlogged="false")
        for cx, cz in ((xl, zl), (xr, zr)):
            if b.empty(cx + dx, top - 1, cz + dz) and solid(b, cx, top - 1, cz):
                b.set(cx + dx, top - 1, cz + dz, f"{lk.wood}_stairs", facing=OPPOSITE[out], half="top",
                      waterlogged="false")
    elif st.key == "ochre":
        t = lk.trim
        for (cx, cz), f in (((xl, zl), RIGHT[out]), ((xr, zr), LEFT[out])):
            if b.empty(cx + dx, top, cz + dz) and solid(b, cx, top, cz):
                b.set(cx + dx, top, cz + dz, f"{t}_stairs", facing=OPPOSITE[f], half="top", waterlogged="false")
            if b.empty(cx + dx, y, cz + dz) and b.inside(cx + dx, cz + dz) and \
                    b.get(cx + dx, y - 1, cz + dz) not in ("air",):
                b.set(cx + dx, y, cz + dz, pick(rng, st.pots))
        for i in range(w):
            cx, cz = x + r[0] * i + dx, z + r[1] * i + dz
            if b.empty(cx, top, cz):
                b.set(cx, top, cz, f"{t}_slab", type="top", waterlogged="false")
    else:
        t = lk.trim
        for (cx, cz), f in (((xl, zl), RIGHT[out]), ((xr, zr), LEFT[out])):
            if b.empty(cx + dx, top, cz + dz) and solid(b, cx, top, cz):
                b.set(cx + dx, top, cz + dz, f"{t}_stairs", facing=f, half="bottom", waterlogged="false")
            if torches and b.empty(cx + dx, y + 1, cz + dz) and solid(b, cx, y + 1, cz):
                b.set(cx + dx, y + 1, cz + dz, st.wall_torch, facing=out)
        for i in range(w):   # a pointed hood
            cx, cz = x + r[0] * i + dx, z + r[1] * i + dz
            if b.empty(cx, top, cz):
                b.set(cx, top, cz, FULL.get(t, lk.found))
                if b.empty(cx, top + 1, cz):
                    b.set(cx, top + 1, cz, f"{t}_slab", type="bottom", waterlogged="false")


def door_in(b, x, y, z, facing, wood):
    b.door(x, y, z, facing, wood)


def ladder(b, x, z, y0, y1, facing, support=None):
    """A ladder from y0 to y1, against the block behind it (opposite facing). With `support`,
    whatever isn't a solid block behind it (a window, air) is walled up with that."""
    dx, dz = STEP[facing]
    for y in range(y0, y1 + 1):
        if support and not solid(b, x - dx, y, z - dz):
            b.set(x - dx, y, z - dz, support)
        b.set(x, y, z, "ladder", facing=facing, waterlogged="false")


def buttresses(b, s, lk, sides=("east", "west"), block=None, height=None, corners=True):
    """Stone buttresses against the walls, between the windows, each capped by a sloping stair."""
    block = block or lk.found
    cap = stairs_of(block)
    h = height or max(3, min(s.top - 2, round(s.top * 0.6)))
    spots = []
    for side in sides:
        cells, out = wall_cells(s, side)
        dx, dz = STEP[out]
        for i in range(1, len(cells) - 1):
            trio = cells[i - 1:i + 2]
            if i % 3 != 1 and i != len(cells) - 2:
                continue
            if any(not solid(b, cx, y, cz) for cx, cz in trio for y in range(1, h + 1)):
                continue
            spots.append((cells[i][0] + dx, cells[i][1] + dz, out))
        if corners:
            a, c = (cells[0], cells[-1])
            for (cx, cz), along in ((a, -1), (c, 1)):
                # the corner itself, one more step along the wall
                ax, az = (cx, cz + along) if side in ("east", "west") else (cx + along, cz)
                spots.append((ax + dx, az + dz, out))
    for x, z, out in spots:
        if not b.inside(x, z) or any(not b.empty(x, y, z) for y in range(1, h + 2)):
            continue
        if b.get(x, 0, z) not in (GROUND,) and not b.get(x, 0, z).endswith(("_ore",)):
            continue
        b.fill(x, 1, z, x, h - 1, z, block)
        b.set(x, h, z, cap, facing=OPPOSITE[out], half="bottom", waterlogged="false")


# ── Roofs ────────────────────────────────────────────────────────────────

def _axes(along, x0, z0, x1, z1):
    if along == "x":
        return x0, x1, z0, z1, (lambda a, c: (a, c)), ("south", "north"), ("west", "east")
    return z0, z1, x0, x1, (lambda a, c: (c, a)), ("east", "west"), ("north", "south")


def roof_levels(x0, z0, x1, z1, kind="gable", along="x", over=1, end_over=1, clip=0, ends=(True, True)):
    """The course of a roof over each column: 0 at the eaves, rising one per step inwards.
    kind: gable (ridge along `along`), hip (slopes on all sides), or a gable with `clip`
    courses at the top hipped back at the ends (a half-hip). `ends` says which gable ends get
    their overhang."""
    A0, A1, C0, C1, at, _, _ = _axes(along, x0, z0, x1, z1)
    if kind == "hip":
        end_over = over
    ca, cb = C0 - over, C1 + over
    aa, ab = A0 - (end_over if ends[0] else 0), A1 + (end_over if ends[1] else 0)
    lmax = (cb - ca) // 2
    out = {}
    for a in range(aa, ab + 1):
        for c in range(ca, cb + 1):
            lv = min(c - ca, cb - c)
            la = min(a - aa, ab - a)
            if kind == "hip":
                lv = min(lv, la)
            elif clip:
                lv = min(lv, la + max(1, lmax - clip))
            out[at(a, c)] = lv
    return out


def draw_roof(b, levels, yb, m, pitch=1, fill=None, walls=(), keep_fill=()):
    """Draws a roof from its courses: stairs facing up the slope, slabs on the ridge and peaks,
    with `pitch` blocks of rise per course (the steps under steep ones filled with m.block).
    Under it, the outline of each wall rectangle is filled with `fill` up to the roof.
    Returns the highest y drawn."""
    def lv(x, z):
        return levels.get((x, z), -1)

    top = yb
    order = ("north", "south", "west", "east")
    for (x, z), L in levels.items():
        y = yb + pitch * L
        nb = {d: lv(x + STEP[d][0], z + STEP[d][1]) for d in order}
        higher = [d for d in order if nb[d] > L]
        lower = [d for d in order if nb[d] < L]
        if higher:
            f = max(higher, key=lambda d: nb[d])
            b.set(x, y, z, m.stairs, facing=f, half="bottom", waterlogged="false")
        elif len(lower) == 1 or (len(lower) == 2 and lower[0] != OPPOSITE[lower[1]]):
            b.set(x, y, z, m.stairs, facing=OPPOSITE[lower[0]], half="bottom", waterlogged="false")
        else:
            b.set(x, y, z, m.slab, type="bottom", waterlogged="false")
        if L > 0:
            for yy in range(y - pitch + 1, y):
                b.set(x, yy, z, m.block)
        top = max(top, y)
    if fill:
        for (x0, z0, x1, z1) in walls:
            outline = {(x, z) for x in range(x0, x1 + 1) for z in (z0, z1)} | \
                      {(x, z) for z in range(z0, z1 + 1) for x in (x0, x1)}
            for x, z in outline:
                L = lv(x, z)
                if L <= 0:
                    continue
                for yy in range(yb + 1, yb + pitch * L - pitch + 1):
                    if (x, yy, z) not in keep_fill:
                        b.set(x, yy, z, fill)
    return top


def gable(b, x0, z0, x1, z1, yb, m, along="x", fill=None, pitch=1, over=1, end_over=1, clip=0, extra=()):
    """A ridged roof over the walls x0..x1, z0..z1 whose top course is at yb: eaves at yb hanging
    `over` past the long walls, gables filled with `fill`. `clip` hips back the top courses at
    the ends (a half-hip); `extra` are more roof_levels to join (cross gables, dormers).
    Returns the ridge's y."""
    levels = roof_levels(x0, z0, x1, z1, "gable", along, over, end_over, clip)
    for e in extra:
        for k, v in e.items():
            levels[k] = max(levels.get(k, -1), v)
    return draw_roof(b, levels, yb, m, pitch, fill, walls=[(x0, z0, x1, z1)])


def _xyz(at, a, y, c):
    x, z = at(a, c)
    return x, y, z


def hip(b, x0, z0, x1, z1, yb, m, over=1, pitch=1, fill=None):
    """A roof sloping on all four sides."""
    levels = roof_levels(x0, z0, x1, z1, "hip", "x" if x1 - x0 >= z1 - z0 else "z", over)
    return draw_roof(b, levels, yb, m, pitch, fill, walls=[(x0, z0, x1, z1)])


def spire(b, x0, z0, x1, z1, yb, m, pitch=3, finial="lightning_rod"):
    """A steep pyramid over a square tower, a finial on its point."""
    top = hip(b, x0, z0, x1, z1, yb, m, over=0, pitch=pitch)
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    if finial == "lightning_rod":
        b.set(cx, top + 1, cz, "lightning_rod", facing="up", powered="false", waterlogged="false")
    elif finial:
        b.set(cx, top + 1, cz, finial)
    return top + 1


def flat(b, x0, z0, x1, z1, yb, edge, cap=None):
    """A flat roof at yb with a parapet of `edge` around it, capped with `cap` slabs."""
    b.fill(x0, yb, z0, x1, yb, z1, edge)
    b.ring(x0, z0, x1, z1, yb + 1, yb + 1, edge)
    if cap:
        b.ring(x0, z0, x1, z1, yb + 2, yb + 2, cap, type="bottom", waterlogged="false")
    return yb + 1


def terrace(b, rng, st, s, lk, hatch=None):
    """A flat roof to sit out on: a capped parapet over a cornice, the ladder's hatch, and in
    Ochre pots and an awning; Ashen gets battlements. Returns the parapet's top y."""
    x0, z0, x1, z1, yb = s.x0, s.z0, s.x1, s.z1, s.top
    edge = lk.found
    t = trim_of(edge)
    b.fill(x0, yb, z0, x1, yb, z1, edge)
    b.ring(x0, z0, x1, z1, yb + 1, yb + 1, edge)
    if st.key == "ashen":   # battlements
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x in (x0, x1) or z in (z0, z1)) and (x + z) % 2 == 0:
                    b.set(x, yb + 2, z, edge)
    else:
        b.ring(x0, z0, x1, z1, yb + 2, yb + 2, f"{t}_slab", type="bottom", waterlogged="false")
    cornice(b, s, f"{t}_stairs", yb)
    if hatch:
        hx, hz = hatch
        b.set(hx, yb, hz, f"{lk.wood}_trapdoor", facing="north", half="top", open="false", powered="false",
              waterlogged="false")
    inner = [(x, z) for x in range(x0 + 1, x1) for z in range(z0 + 1, z1)
             if (x in (x0 + 1, x1 - 1) or z in (z0 + 1, z1 - 1))]
    near = set()
    if hatch:
        near = {(hatch[0] + a, hatch[1] + c) for a in (-1, 0, 1) for c in (-1, 0, 1)}
    if st.key == "ochre":
        for x, z in inner:
            if (x, z) not in near and rng.random() < 0.3:
                b.set(x, yb + 1, z, pick(rng, st.pots))
        if x1 - x0 >= 6 and z1 - z0 >= 5:   # an awning over a bench
            ax0, az0 = x0 + 2, z0 + 2
            ax1, az1 = ax0 + 2, az0 + 1
            if not any((x, z) in near for x in range(ax0 - 1, ax1 + 2) for z in range(az0 - 1, az1 + 2)):
                for x, z in ((ax0, az0), (ax1, az0), (ax0, az1), (ax1, az1)):
                    b.fill(x, yb + 1, z, x, yb + 2, z, f"{lk.wood}_fence", waterlogged="false")
                for x in range(ax0, ax1 + 1):
                    b.fill(x, yb + 3, az0, x, yb + 3, az1, WOOL if x % 2 else "white_wool")
                b.set(ax0 + 1, yb + 1, az1, f"{lk.wood}_stairs", facing="south", half="bottom", waterlogged="false")
                b.set(ax0 + 1, yb + 1, az0, "potted_flowering_azalea_bush")
    elif st.key == "timber":
        for x, z in inner:
            if (x, z) not in near and rng.random() < 0.12:
                b.set(x, yb + 1, z, pick(rng, st.pots))
    return yb + 2


def cornice(b, s, stairs, y, every=1):
    """Upside-down stairs along the outside of the walls at y, where the wall is solid."""
    for side in ("north", "south", "east", "west"):
        cells, out = wall_cells(s, side)
        dx, dz = STEP[out]
        corner = [(s.x0, s.z0), (s.x1, s.z0)] if side == "north" else [(s.x0, s.z1), (s.x1, s.z1)] \
            if side == "south" else []
        for i, (x, z) in enumerate(list(cells) + corner):
            if every > 1 and i % every:
                continue
            if solid(b, x, y, z) and b.empty(x + dx, y, z + dz):
                b.set(x + dx, y, z + dz, stairs, facing=OPPOSITE[out], half="top", waterlogged="false")


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


def roof_kind(rng, st, s):
    """Picks a roof for a shell in this style: flat, hip, half_hip, cross or gable."""
    w, d = s.x1 - s.x0 + 1, s.z1 - s.z0 + 1
    r = rng.random()
    if r < st.flat:
        return "flat"
    r = rng.random()
    if r < st.hip and abs(w - d) <= 4:
        return "hip"
    r = rng.random()
    if r < st.cross and w >= 7 and d >= 6 and w >= d - 1:
        return "cross"
    if rng.random() < st.half_hip and max(w, d) >= 6:
        return "half_hip"
    return "gable"


def roof(b, rng, st, s, lk, m=None, kind=None, along=None, hatch=None, pitch=None):
    """The style's roof over a shell: gabled, half-hipped, cross-gabled, hipped or flat (a
    terrace, with the hatch at `hatch`). Returns the top y."""
    m = m or pick(rng, st.roofs)
    w, d = s.x1 - s.x0 + 1, s.z1 - s.z0 + 1
    if kind is None:
        kind = roof_kind(rng, st, s)
    if kind == "flat":
        return terrace(b, rng, st, s, lk, hatch)
    if pitch is None:
        pitch = 2 if rng.random() < st.pitch2 else 1
    if kind == "hip":
        top = hip(b, s.x0, s.z0, s.x1, s.z1, s.top, m, pitch=min(pitch, 1 if st.key == "ochre" else pitch),
                  fill=lk.infill)
        eaves(b, s, lk, stone=st.key == "ochre")
        return top
    along = along or ("x" if w >= d else "z")
    extra = ()
    if kind == "cross" and along == "x":
        span = d + 1
        lmax = span // 2
        cw = min(2 * lmax - 1, w - 2)
        cw -= 1 - cw % 2
        if cw >= 3:
            cx0 = (s.x0 + s.x1) // 2 - cw // 2
            zc = s.z0 + (d - 1) // 2
            lv = roof_levels(cx0, s.z0, cx0 + cw - 1, zc, "gable", "z", 1, 1, ends=(True, False))
            extra = ({k: v for k, v in lv.items() if k[1] <= zc},)
    clip = 0
    if kind == "half_hip":
        clip = 2 if pitch == 1 and min(w, d) >= 7 else 1
    top = gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along=along, fill=lk.infill, pitch=pitch, clip=clip,
                extra=extra)
    if extra:   # a window in the front gable
        cx = (s.x0 + s.x1) // 2
        ys = [y for y in range(s.top + 1, top) if b.get(cx, y, s.z0) == lk.infill]
        if len(ys) >= 2:
            b.set(cx, ys[0] + (1 if len(ys) >= 3 else 0), s.z0, lk.pane if lk.pane != "air" else "glass_pane",
                  waterlogged="false")
            if lk.timbered:
                for y in ys:
                    if b.get(cx, y, s.z0) == lk.infill:
                        b.set(cx - 2, y, s.z0, lk.log, axis="y") if b.get(cx - 2, y, s.z0) == lk.infill else None
                        b.set(cx + 2, y, s.z0, lk.log, axis="y") if b.get(cx + 2, y, s.z0) == lk.infill else None
    eaves(b, s, lk, stone=st.key == "ochre")
    if st.key == "ashen" and pitch == 2:   # finials at the gable ends
        A0, A1, C0, C1, at, _, _ = _axes(along, s.x0, s.z0, s.x1, s.z1)
        cm = (C0 + C1) // 2
        for a in (A0, A1):
            x, z = at(a, cm)
            if b.get(x, top, z) in (m.slab, m.stairs) and b.empty(x, top + 1, z) and clip == 0:
                b.set(x, top + 1, z, wall_of(m.block, "polished_blackstone_wall"), waterlogged="false")
    return top


def eaves(b, s, lk, timber=True, stone=True):
    """Brackets under the eaves: at the posts of a timber frame, along the whole wall of stone."""
    y = s.top - 1
    if lk.timbered:
        if not timber:
            return
        for x, z in posts(s, lk.post_every):
            for side, (dx, dz) in STEP.items():
                ox, oz = x + dx, z + dz
                if s.x0 <= ox <= s.x1 and s.z0 <= oz <= s.z1:
                    continue
                if b.empty(ox, y, oz) and b.get(ox, y + 1, oz).endswith("_stairs") and b.inside(ox, oz):
                    b.set(ox, y, oz, f"{lk.wood}_stairs", facing=OPPOSITE[side], half="top", waterlogged="false")
        return
    if not stone:
        return
    for side in ("north", "south", "east", "west"):
        cells, out = wall_cells(s, side)
        dx, dz = STEP[out]
        for x, z in cells:
            ox, oz = x + dx, z + dz
            if not b.inside(ox, oz) or not b.get(ox, y + 1, oz).endswith(("_stairs", "_slab")):
                continue
            # a bracket where the wall below is solid and nothing (a shutter, a hood) is in the way
            if solid(b, x, y, z) and solid(b, x, y - 1, z) and b.empty(ox, y, oz) and b.empty(ox, y - 1, oz):
                b.set(ox, y, oz, f"{lk.trim}_stairs", facing=OPPOSITE[out], half="top", waterlogged="false")


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
            and self.b.empty(x, self.y, z) and self.b.empty(x, self.y + 1, z) \
            and not self.b.get(x, self.y - 1, z) == "ladder"

    def wall_spots(self):
        """Cells along the walls, each with the way into the room."""
        out = []
        for x in range(self.x0, self.x1 + 1):
            out.append((x, self.z0, "south"))
            out.append((x, self.z1, "north"))
        for z in range(self.z0 + 1, self.z1):
            out.append((self.x0, z, "east"))
            out.append((self.x1, z, "west"))
        return [(x, z, f) for x, z, f in out if self.free(x, z) and not self.by_door(x, z)]

    def by_door(self, x, z):
        """Whether (x, z) is the cell just inside a door in the walls around the room."""
        for dx, dz in STEP.values():
            if self.b.get(x + dx, self.y, z + dz).endswith("_door") and \
                    not (self.x0 <= x + dx <= self.x1 and self.z0 <= z + dz <= self.z1):
                return True
        return False

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
                if not room.free(x + dx, z + dz) or not inside_room(room, x + dx, z + dz) or \
                        room.by_door(x + dx, z + dz):
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
        cx, cz = (room.x0 + room.x1) // 2, (room.z0 + room.z1) // 2
        if not hang(b, cx, cz, y, st):
            for x, z in ((cx + 1, cz), (cx, cz + 1), (cx - 1, cz), (cx, cz - 1)):
                if hang(b, x, z, y, st):
                    break


def inside_room(room, x, z):
    return room.x0 <= x <= room.x1 and room.z0 <= z <= room.z1


def hang(b, x, z, y_floor, st, height=3):
    """A lantern hanging under the ceiling over (x, z), if there's a ceiling. Returns whether."""
    top = y_floor + height
    if b.empty(x, top, z) or b.get(x, top, z) in ("ladder",) or b.get(x, top, z).endswith(("_trapdoor", "_pane")):
        return False
    if not b.empty(x, top - 1, z):
        return False
    b.set(x, top - 1, z, st.light, hanging="true", waterlogged="false")
    return True


def hang_chain(b, x, z, y, light, longest=16):
    """A lantern at y hanging on a chain from the first block above it. Returns whether."""
    for yy in range(y + 1, y + longest + 1):
        if not b.empty(x, yy, z):
            b.fill(x, y + 1, z, x, yy - 1, z, "iron_chain", axis="y", waterlogged="false")
            b.set(x, y, z, light, hanging="true", waterlogged="false")
            return True
    return False


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
    cypress, pale_oak. Leaves stay on the footprint, a block in from its edge where they can."""
    lv = leaves(kind)
    log = log_of(kind)
    L = dict(LEAF)

    def put(px, py, pz):
        if b.empty(px, py, pz) and b.inside(px, pz):
            b.set(px, py, pz, lv, **L)

    def blob(cx, cy, cz, r, ry=None, keep=0.85):
        ry = ry or r
        for dy in range(-ry, ry + 1):
            for dz in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    d = (dx / (r + 0.5)) ** 2 + (dz / (r + 0.5)) ** 2 + (dy / (ry + 0.5)) ** 2
                    if d <= 1 and (d < 0.6 or rng.random() < keep):
                        put(cx + dx, cy + dy, cz + dz)

    if kind in ("oak", "birch", "pale_oak"):
        h = size or rng.randint(4, 6) + (1 if kind == "birch" else 0)
        b.fill(x, y, z, x, y + h - 1, z, log, axis="y")
        blob(x, y + h - 1, z, 2, 2)
        put(x, y + h, z)
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
                    if abs(dx) + abs(dz) <= max(r, 2 * r - 1):
                        put(x + dx, y + i, z + dz)
        put(x, y + h, z)
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
                    if abs(ox) + abs(oz) <= r + 1:
                        put(tx + ox, yy, tz + oz)
    elif kind == "olive":
        h = size or rng.randint(2, 3)
        b.fill(x, y, z, x, y + h - 1, z, log, axis="y")
        blob(x, y + h, z, 2, 1, keep=0.7)
        for _ in range(3):   # blossom, only where it touches the crown
            px, py, pz = x + rng.randint(-1, 1), y + h + rng.randint(0, 1), z + rng.randint(-1, 1)
            if b.get(px, py, pz) == lv:
                b.set(px, py, pz, "flowering_azalea_leaves", **L)
