"""Still more of Level 143's buildings: a third set of kinds, registered in the same CATALOG.

Same conventions as buildings.py: every generator takes (rng, style) and returns a schem.Build
facing north (its front at z = 0, on the street), a path from the door to z = 0, a block of
ground round the walls for the eaves. Roofs come from parts.py (gable, hip) or from the closed
shapes here (cone over a round wall, pyramid caps); .temp/level143/more/check.py checks that
every cell inside the walls is covered and no gable is left open.
"""

import math

from buildings import (HOUSE_GROUND, HOUSE_ONE, HOUSE_UPPER, SECTION_LEVELS, SKY, farmland, flowers, house_body,
                       new, section, gate_base, yard_cells)
from buildings_extra import (candle, circle, clock_face, cone, disc, dome, gen, hanging, light, mat_of, potted, slab,
                             stair, toward, wall_of, wall_torch)
from parts import (FULL, LEAF, Mat, Room, Shell, bench, buttresses, chimney, cornice, door_dress, draw_roof, edge_points,
                   front_door, furnish, gable, gable_fill, hang, hang_chain, hip, ladder, lamp_post, look, mat, pick,
                   roof, roof_levels, shell, solid, table, tree, trim_of, windows)
from schem import GROUND, OPPOSITE, PATH, STEP, TREASURE, WALL, WOOL, Build

WL = dict(waterlogged="false")
TD = dict(powered="false", waterlogged="false")


def fence(b, x0, y0, z0, x1, y1, z1, wood):
    b.fill(x0, y0, z0, x1, y1, z1, f"{wood}_fence", **WL)


def gate(b, x, y, z, wood, facing="north"):
    b.set(x, y, z, f"{wood}_fence_gate", facing=facing, in_wall="false", open="false", powered="false")


def trapdoor(b, x, y, z, wood, facing="north", half="top", open_=False):
    b.set(x, y, z, f"{wood}_trapdoor", facing=facing, half=half, open="true" if open_ else "false", **TD)


def lit_candle(b, x, y, z, n=3, color=None):
    b.set(x, y, z, f"{color}_candle" if color else "candle", candles=str(n), lit="true", **WL)


def shelf_of(block):
    """An item callable for furnish(): `block` against the wall."""
    return lambda b, x, y, z, f: b.set(x, y, z, block)


def topped(lower, upper, **up):
    """An item callable: `lower` with `upper` standing on it."""
    def put(b, x, y, z, f):
        b.set(x, y, z, lower)
        b.set(x, y + 1, z, upper, **up)
    return put


def cauldron(b, x, y, z, f=None):
    b.set(x, y, z, "water_cauldron", level="3")


def desk(wood):
    """A writing desk: a top slab with a candle, a chair before it."""
    def put(b, x, y, z, f):
        b.set(x, y, z, f"{wood}_slab", type="top", **WL)
        b.set(x, y + 1, z, "candle", candles="1", lit="false", **WL)
        dx, dz = STEP[f]
        if b.empty(x + dx, y, z + dz):
            stair(b, x + dx, y, z + dz, f"{wood}_stairs", f)
    return put


def counter_row(b, x0, x1, z, y, wood, gap=None):
    for x in range(x0, x1 + 1):
        if x == gap:
            gate(b, x, y, z, wood, "south")
        else:
            slab(b, x, y, z, f"{wood}_slab", "top")


def town_house(rng, st, name, sign, ground, upper=None, floors=(1, 2), W=(7, 9), D=(6, 8), yard=(2, 3), kind=None,
               timbered=None, cellar=False, chimney_=None):
    """A house_body shop or workshop: the trade's things on the ground floor, rooms above.
    Returns (b, shell, look, door x)."""
    w, d, y = rng.randint(*W), rng.randint(*D), rng.randint(*yard)
    b = new(name, "city", w + 2, y + d + 1)
    lk = look(rng, st, timbered=timbered)
    s = Shell(1, y, w, y + d - 1, rng.choice(floors) if isinstance(floors, tuple) else floors)
    items = [ground] + ([upper or pick(rng, HOUSE_UPPER)] if s.floors > 1 else [])
    dx = house_body(b, rng, st, s, lk, items, sign=sign, cellar=cellar, kind=kind)
    return b, s, lk, dx


def highest(b, x, z):
    ys = [y for (bx, y, bz), (n, _) in b.blocks.items() if bx == x and bz == z and n != "air"]
    return max(ys) if ys else 0


# ═════════════════════════════════════════════════════════════════════════
#  City: trades
# ═════════════════════════════════════════════════════════════════════════

@gen("bookbinder", freq="average", n=3)
def bookbinder(rng, st):
    """A bindery: presses, a loom for the headbands, stacks of finished books in the window."""
    wood = pick(rng, st.woods)
    press = topped(f"{wood}_planks", "piston", facing="down", extended="false")
    ground = ["lectern", "shelf", "loom", press, "shelf", "chest", "cartography_table"]
    b, s, lk, dx = town_house(rng, st, "Bookbinder", ["2[surnames]~Bookbinder", "", "", ""], ground, floors=(1, 2, 2))
    # books drying on the sills outside: a bench of slabs with lecterns
    for x in range(s.x0 + 1, s.x1):
        if abs(x - dx) > 1 and b.get(x, 0, s.z0 - 1) == GROUND and b.empty(x, 1, s.z0 - 1) and x % 2 == 0:
            b.set(x, 1, s.z0 - 1, "chiseled_bookshelf", facing="north", **{f"slot_{i}_occupied": "false" for i in range(6)})
    return b


@gen("hat_maker", freq="average", n=3)
def hat_maker(rng, st):
    """A milliner's: bolts of felt, a loom, hats on stands in the window."""
    ground = ["loom", WOOL, "white_wool", "barrel", "chest", topped("oak_fence", "skeleton_skull", rotation="8", powered="false")]
    b, s, lk, dx = town_house(rng, st, "Hat Maker", ["2[surnames]'s~Hats", "", "", ""], ground, floors=(1, 2))
    # hat stands in the front garden: fence posts with carpet "hats"
    cols = ("black_carpet", "brown_carpet", "red_carpet", "gray_carpet")
    for i, x in enumerate(range(s.x0, s.x1 + 1, 3)):
        if abs(x - dx) > 1 and b.get(x, 0, s.z0 - 1) == GROUND and b.empty(x, 1, s.z0 - 1):
            fence(b, x, 1, s.z0 - 1, x, 1, s.z0 - 1, lk.wood)
            b.set(x, 2, s.z0 - 1, cols[i % len(cols)])
    return b


@gen("toy_maker", freq="average", n=3)
def toy_maker(rng, st):
    """A toy shop: painted blocks, a jukebox, a target for the archery set, tops on the counter."""
    toys = ("red_wool", "yellow_wool", "light_blue_wool", "lime_wool")
    ground = ["crafting_table", topped(pick(rng, toys), "red_carpet"), "jukebox", "target", topped(pick(rng, toys),
              "decorated_pot", facing="north", cracked="false", waterlogged="false"), "chest", "barrel"]
    b, s, lk, dx = town_house(rng, st, "Toy Maker", ["2[surnames]'s~Toys", "", "", ""],
                              ground, floors=(1, 2))
    for (x, y, z), (n, p) in list(b.blocks.items()):
        if n == "jukebox":
            b.set(x, y, z, "jukebox", has_record="false")
    # a rocking horse in the yard: a stair body on a slab
    for x in (s.x0, s.x1):
        if b.get(x, 0, 0) == GROUND and s.z0 >= 2:
            slab(b, x, 1, 0, f"{lk.wood}_slab")
            break
    return b


@gen("glassblower", freq="average", n=3)
def glassblower(rng, st):
    """A glasshouse workshop: the furnace and its tall chimney, coloured glass in the window."""
    ground = [topped("blast_furnace", "air"), "furnace", cauldron, "crafting_table", "chest",
              topped("glass", "white_stained_glass"), "barrel"]
    b, s, lk, dx = town_house(rng, st, "Glassblower", ["2[surnames]~Glassworks", "", "", ""], ground, floors=1,
                              W=(8, 10), D=(7, 8))
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if n in ("blast_furnace", "furnace"):
            b.set(x, y, z, n, facing=STEP_FROM_WALL(b, x, y, z), lit="true")
    # coloured glass in the front windows
    tints = ("light_blue", "yellow", "lime", "magenta", "orange", "cyan")
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if z == s.z0 and n.endswith("pane") and y < s.h:
            b.set(x, y, z, f"{pick(rng, tints)}_stained_glass_pane", **WL)
    cx = s.x0 + 1 if dx > (s.x0 + s.x1) // 2 else s.x1 - 1
    chimney(b, cx, s.z1, 1, highest(b, cx, s.z1) + 3, "bricks")
    return b


def STEP_FROM_WALL(b, x, y, z):
    """The way out of the wall a block stands against (for furnaces placed by furnish)."""
    for f, (dx, dz) in STEP.items():
        if solid(b, x - dx, y, z - dz) and b.empty(x + dx, y, z + dz):
            return f
    return "north"


@gen("cooperage", freq="average", n=3)
def cooperage(rng, st):
    """A cooper's shop with a yard of finished casks and hoops hung on the fence."""
    w, d = rng.randint(7, 8), rng.randint(6, 7)
    yw = 5
    b = new("Cooperage", "city", w + yw + 2, d + 4)
    lk = look(rng, st)
    s = Shell(1, 2, w, d + 1, 1)
    ground = ["crafting_table", "smithing_table", "barrel", "barrel", "chest", "grindstone"]
    dx = house_body(b, rng, st, s, lk, [ground], sign=["2[surnames]~Cooper", "", "", ""], kind="gable")
    # the yard: fenced, casks stacked on their sides
    x0, x1 = s.x1 + 2, b.w - 1
    for x in range(x0, x1 + 1):
        for z in (s.z0, s.z1):
            fence(b, x, 1, z, x, 1, z, lk.wood)
    for z in range(s.z0, s.z1 + 1):
        fence(b, x1, 1, z, x1, 1, z, lk.wood)
    gate(b, (x0 + x1) // 2, 1, s.z0, lk.wood)
    b.path((x0 + x1) // 2, 0, (x0 + x1) // 2, s.z0 - 1)
    for x in range(x0, x1):
        for z in range(s.z0 + 2, s.z1):
            if x != (x0 + x1) // 2 or z > s.z0 + 2:
                h = 2 if (x + z) % 3 == 0 else 1
                for y in range(1, h + 1):
                    b.barrel(x, y, z, facing=pick(rng, ("east", "north", "up")))
    for x in range(x0, x1 + 1, 2):   # hoops: chains looped on the yard fence
        if b.get(x, 1, s.z1) == f"{lk.wood}_fence":
            b.set(x, 2, s.z1, "iron_chain", axis="x", **WL)
    lamp_post(b, x0, s.z0 - 1, st, lk.wood)
    return b


@gen("tannery", freq="average", n=3)
def tannery(rng, st):
    """Lime pits and dye vats in a yard, hides drying on a rack, the tanner's shed at the back."""
    W, D = 13, 13
    b = new("Tannery", "city", W, D)
    lk = look(rng, st)
    wood = lk.wood
    b.fill(1, 0, 1, W - 2, 0, D - 2, "coarse_dirt")
    b.path(W // 2, 0, W // 2, 7)
    rim = pick(rng, st.found)
    dyes = ("red", "blue", "yellow", "brown", "green", "white")
    # vats: 2x2 pits, rimmed, each a different colour of water (stained glass under it)
    for i, (vx, vz) in enumerate(((1, 1), (4, 1), (8, 1), (1, 4), (8, 4))):
        dye = dyes[(i + rng.randrange(6)) % len(dyes)]
        b.fill(vx, -2, vz, vx + 2, 0, vz + 2, rim)
        b.fill(vx + 1, -1, vz + 1, vx + 1, -1, vz + 1, f"{dye}_stained_glass")
        b.set(vx + 1, 0, vz + 1, "water", level="0")
        for x in range(vx, vx + 3):
            for z in range(vz, vz + 3):
                if (x, z) != (vx + 1, vz + 1):
                    slab(b, x, 1, z, slab_of_block(rim))
    # the drying rack: posts, a beam, hides hanging under it
    rz = 8
    for x in (1, 5):
        fence(b, x, 1, rz, x, 3, rz, wood)
    for x in range(1, 6):
        b.set(x, 4, rz, f"{lk.log}", axis="x")
    for x in range(2, 5):
        b.set(x, 3, rz, pick(rng, ("brown_wool", "white_wool", "orange_wool", "brown_wool")))
    # the shed: open-fronted, a work bench, barrels of bark
    s = Shell(7, 7, 11, 11, 1, h=4)
    b.ring(s.x0, s.z0, s.x1, s.z1, 1, 3, lk.infill)
    b.fill(s.x0 + 1, 0, s.z0 + 1, s.x1 - 1, 0, s.z1 - 1, lk.floor)
    for x, z in ((s.x0, s.z0), (s.x1, s.z0), (s.x0, s.z1), (s.x1, s.z1)):
        b.fill(x, 1, z, x, 3, z, lk.log, axis="y")
    b.clear(s.x0 + 1, 1, s.z0, s.x1 - 1, 2, s.z0)
    b.fill(s.x0, 4, s.z0, s.x1, 4, s.z1, lk.log, axis="x")
    b.fill(s.x0 + 1, 4, s.z0 + 1, s.x1 - 1, 4, s.z1 - 1, lk.floor)
    gable(b, s.x0, s.z0, s.x1, s.z1, 5, pick(rng, st.roofs), along="x", fill=lk.infill)
    b.barrel(s.x0 + 1, 1, s.z1 - 1, [("leather", rng.randint(2, 9))])
    b.barrel(s.x1 - 1, 1, s.z1 - 1, [("oak_log", 8)])
    b.set(s.x0 + 2, 1, s.z1 - 1, "smithing_table")
    b.set(s.x0 + 3, 1, s.z1 - 1, "crafting_table")
    b.chest(s.x1 - 1, 1, s.z0 + 1, "west")
    hanging(b, s.x0 + 2, 3, s.z0 + 2, st)
    lamp_post(b, W // 2 - 1, 7, st, wood)
    b.sign(W // 2 + 1, 1, 0, "north", ["2[surnames]~Tanner", "", "", ""], wood, wall=False)
    return b


def slab_of_block(block):
    return mat_of(block).slab


@gen("rope_walk", freq="rare", n=3)
def rope_walk(rng, st):
    """A long low shed where rope is laid: posts down its length with the strands stretched
    between them, a spinning wheel at the head and coils at the far end."""
    L = rng.randint(17, 21)
    b = new("Rope Walk", "city", 7, L + 2)
    lk = look(rng, st)
    s = Shell(1, 1, 5, L, 1, h=3)
    shell(b, s, lk)
    front_door(b, s, lk, 3)
    windows(b, s, lk, sides=("east", "west"), every=3)
    for z in range(s.z0 + 3, s.z1 - 1, 3):   # the posts and the strands
        b.set(2, 1, z, f"{lk.wood}_fence", **WL)
        b.set(4, 1, z, f"{lk.wood}_fence", **WL)
    for z in range(s.z0 + 2, s.z1):
        b.set(2, 2, z, "iron_chain", axis="z", **WL)
        b.set(4, 2, z, "iron_chain", axis="z", **WL)
    b.set(2, 1, s.z0 + 1, "grindstone", face="floor", facing="north")
    b.set(4, 1, s.z0 + 1, "loom", facing="south")
    b.barrel(2, 1, s.z1 - 1, [("string", rng.randint(8, 32))])
    b.barrel(4, 1, s.z1 - 1, [("lead", rng.randint(1, 3))])
    b.set(3, 1, s.z1 - 1, "hay_block", axis="y")
    m = pick(rng, st.roofs)
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along="z", fill=lk.infill)
    for z in range(s.z0 + 2, s.z1, 4):
        hang(b, 3, z, 1, st, height=3)
    b.sign(4, 2, 0, "north", ["Rope Walk~[surnames]~& Sons", "", "", ""], lk.wood)
    return b


# ═════════════════════════════════════════════════════════════════════════
#  City: towers
# ═════════════════════════════════════════════════════════════════════════

@gen("observatory", freq="very rare", n=2, unique=True)
def observatory(rng, st):
    """A round tower with a study at its foot and a domed room at the top, the telescope
    poking out of the dome's slot."""
    r = 4
    S = 2 * r + 3
    c = S // 2
    b = new("Observatory", "city", S, S + 1)
    stone_ = pick(rng, st.stone)
    wood = pick(rng, st.woods)
    cz = c + 1
    h = rng.randint(10, 12)
    for dx, dz in disc(r):
        b.set(c + dx, 0, cz + dz, f"{wood}_planks")
    for dx, dz in circle(r):
        b.fill(c + dx, 1, cz + dz, c + dx, h, cz + dz, stone_)
    for dx, dz in disc(r - 1):
        b.set(c + dx, 5, cz + dz, f"{wood}_planks")
        b.set(c + dx, h, cz + dz, f"{wood}_planks")
    # windows round the tower, a door on the north
    for dx, dz in ((-r, 0), (0, r)):   # (the ladder runs up the east wall)
        for y in (2, 3, 7, 8):
            b.set(c + dx, y, cz + dz, "glass_pane", **WL)
    b.door(c, 1, cz - r, "south", wood)
    b.path(c, 0, c, cz - r - 1)
    b.set(c, 0, cz - r, f"{wood}_planks")
    tm = trim_of(stone_)
    stair(b, c, 3, cz - r - 1, f"{tm}_stairs", "south", "top")
    # the study: books, a desk, a chart table
    ladder(b, c + r - 1, cz, 1, h - 1, "west")
    b.set(c + r - 1, 5, cz, "ladder", facing="west", **WL)
    for dx, dz in circle(r - 1):
        x, z = c + dx, cz + dz
        if dz > 0 and abs(dx) <= 2:
            b.fill(x, 1, z, x, 2, z, "bookshelf")
    b.set(c - 2, 1, cz - 1, "cartography_table")
    b.set(c - 2, 1, cz, "lectern", facing="east", has_book="false", powered="false")
    table(b, c, 1, cz, wood, chairs=("west",))
    hang(b, c - 1, cz - 1, 1, st, height=4)
    # the upper room: the instrument stand
    b.chest(c - 2, 6, cz + 1, "east")
    b.set(c, 6, cz, "copper_block")
    b.set(c - 2, 6, cz - 1, "crafting_table")
    hang(b, c + 1, cz + 1, 6, st, height=4)
    # the dome: closed, a glass slot over the telescope
    dy = h + 1
    for ddx, ddz in disc(r):
        b.set(c + ddx, h, cz + ddz, stone_ if (ddx, ddz) in circle(r) else f"{wood}_planks")
    cu = pick(rng, ("oxidized_copper", "weathered_copper", "exposed_copper", "copper_block"))
    dome(b, c, cz, dy - 1, r, cu)
    for y in range(dy, dy + r):
        for ddz in (-1,):
            pass
    for k in range(0, r):   # the slot: glass from the rim up over the top
        b.set(c, dy + k, cz - r + k if k < r else cz, "glass")
    b.set(c, dy + r - 1, cz - 1, "glass")
    b.set(c, dy + r, cz, "glass")
    # the telescope: copper rods from the stand up through the slot
    b.set(c, h + 1, cz, "copper_block")
    for k, (yy, zz) in enumerate(((h + 2, cz - 1), (h + 3, cz - 2), (h + 4, cz - 3))):
        b.set(c, yy, zz, "lightning_rod", facing="north", powered="false", **WL)
    b.set(c, dy + 1, cz - 3, "glass") if b.get(c, dy + 1, cz - 3) not in ("lightning_rod",) else None
    b.set(c - 1, h + 1, cz + 1, "lantern" if st.key != "ashen" else "soul_lantern", hanging="false", **WL)
    b.set(c + r - 1, h, cz, "air")   # the hatch the ladder comes up through
    b.sign(c + 1, 2, cz - r - 1, "north", ["Observatory~Stars seen~nightly", "", "", ""], wood)
    return b


@gen("signal_tower", freq="rare", n=3)
def signal_tower(rng, st):
    """A square tower with a beacon fire in a glazed lantern room at the top, a gallery round it."""
    b = new("Signal Tower", "city", 11, 11)
    stone_ = pick(rng, st.stone)
    tm = trim_of(stone_)
    wood = pick(rng, st.woods)
    log = f"{wood}_log" if wood != "pale_oak" else "stripped_pale_oak_log"
    h = rng.randint(13, 16)
    x0, x1, c = 2, 8, 5
    b.fill(x0, 0, x0, x1, 0, x1, stone_)
    b.ring(x0, x0, x1, x1, 1, h, stone_)
    lx, lz = c, x1 - 1        # the ladder up the south wall, clear of the lantern room
    for y in range(4, h, 4):
        b.fill(x0 + 1, y, x0 + 1, x1 - 1, y, x1 - 1, f"{wood}_planks")
        for x, z in ((c, x0), (x0, c), (x1, c)):
            b.set(x, y + 2, z, "glass_pane", **WL)
    ladder(b, lx, lz, 1, h, "north")
    b.door(c, 1, x0, "south", wood)
    b.path(c, 0, c, x0 - 1)
    stair(b, c, 3, x0 - 1, f"{tm}_stairs", "south", "top")
    wall_torch(b, x0 + 1, 2, c, st, "east")
    hanging(b, c, 3, c, st)
    for y in range(4, h, 4):   # a bed and a chest on the floors
        b.chest(x0 + 1, y + 1, x0 + 1, "south") if y == 4 else b.barrel(x0 + 1, y + 1, x0 + 1, [("coal", rng.randint(4, 20))])
        hanging(b, c, y + 3, c, st) if y + 4 < h else None
    # the gallery: corbelled out a block all round, a rail
    g = h + 1
    b.fill(x0 - 1, g, x0 - 1, x1 + 1, g, x1 + 1, stone_)
    for x in range(x0 - 1, x1 + 2):
        stair(b, x, h, x0 - 1, f"{tm}_stairs", "south", "top")
        stair(b, x, h, x1 + 1, f"{tm}_stairs", "north", "top")
    for z in range(x0, x1 + 1):
        stair(b, x0 - 1, h, z, f"{tm}_stairs", "east", "top")
        stair(b, x1 + 1, h, z, f"{tm}_stairs", "west", "top")
    b.ring(x0 - 1, x0 - 1, x1 + 1, x1 + 1, g + 1, g + 1, wall_of(stone_), **WL)
    b.set(lx, g, lz, "air")
    b.set(lx, h, lz, "ladder", facing="north", **WL)
    b.set(lx, g, lz + 1, stone_)
    # the lantern room: glass round the fire, on a stone base, a pyramid cap
    b.fill(c - 1, g + 1, c - 1, c + 1, g + 1, c + 1, stone_)
    for x in range(c - 1, c + 2):
        for z in range(c - 1, c + 2):
            if (x, z) != (c, c):
                b.fill(x, g + 2, z, x, g + 3, z, "glass" if (x - c) * (z - c) == 0 else log, **({} if (x - c) * (z - c) == 0 else {"axis": "y"}))
    fire = "soul_campfire" if st.key == "ashen" else "campfire"
    b.set(c, g + 2, c, fire, facing="north", lit="true", signal_fire="false", **WL)
    b.set(c, g + 3, c, "air")
    b.fill(c - 1, g + 4, c - 1, c + 1, g + 4, c + 1, stone_)
    hip(b, c - 1, c - 1, c + 1, c + 1, g + 5, pick(rng, st.roofs), over=1)
    b.set(c, highest(b, c, c) + 1, c, "lightning_rod", facing="up", powered="false", **WL)
    for x, z in ((x0 - 1, x0 - 1), (x1 + 1, x0 - 1), (x0 - 1, x1 + 1), (x1 + 1, x1 + 1)):
        light(b, x, g + 2, z, st)
    b.sign(c + 1, 2, x0 - 1, "north", ["Signal~Tower~Keep the~fire lit", "", "", ""], wood)
    return b


# ═════════════════════════════════════════════════════════════════════════
#  City: civic
# ═════════════════════════════════════════════════════════════════════════

@gen("post_office", freq="rare", n=3)
def post_office(rng, st):
    """A counter, pigeonholes of barrels, sorting tables; pillar boxes outside."""
    wood = pick(rng, st.woods)
    letters = lambda b, x, y, z, f: (b.barrel(x, y, z, [("paper", rng.randint(3, 20))], facing=f),
                                     b.barrel(x, y + 1, z, [("paper", rng.randint(1, 9))], facing=f))
    ground = [letters, letters, "lectern", "cartography_table", letters, "chest"]
    b, s, lk, dx = town_house(rng, st, "Post Office", ["Post Office~Open~every day", "", "", ""], ground,
                              floors=(1, 2), W=(8, 10), yard=(3, 3), timbered=False)
    box = {"timber": "red_terracotta", "ochre": "yellow_terracotta", "ashen": "black_terracotta"}[st.key]
    for x in (s.x0, s.x1):
        if b.get(x, 0, 1) == GROUND:
            b.fill(x, 1, 1, x, 2, 1, box)
            slab(b, x, 3, 1, "smooth_stone_slab" if st.key != "ashen" else "polished_blackstone_slab")
            b.sign(x, 2, 0, "north", ["2[post]", "", "", ""], wood)
    return b


@gen("fire_station", freq="rare", n=2)
def fire_station(rng, st):
    """The engine house: a wide door, water cauldrons and buckets, the alarm bell in a tower."""
    W, D = rng.randint(9, 11), rng.randint(8, 9)
    yard = 3
    b = new("Fire Station", "city", W + 5, yard + D + 1)
    lk = look(rng, st, timbered=False)
    lk.jetty = lk.buttress = False
    s = Shell(1, yard, W, yard + D - 1, 2)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    dx = front_door(b, s, lk, mx - 1, wide=True)
    b.clear(mx - 1, 1, s.z0, mx, 3, s.z0)   # the engine door: open, three high
    b.fill(mx - 1, 0, s.z0, mx, 0, s.z0, PATH)
    lx, lz = s.x0 + 1, s.z1 - 1
    ladder(b, lx, lz, 1, s.h, "north", support=lk.infill)
    windows(b, s, lk, skip={(mx - 2, s.z0), (mx - 1, s.z0), (mx, s.z0), (mx + 1, s.z0)}, every=2)
    floor = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, 1, {(mx - 1, s.z0 + 1), (mx, s.z0 + 1), (lx, lz), (lx, lz - 1)})
    furnish(floor, rng, st, lk, [cauldron, cauldron, cauldron, "barrel", "hay_block", "chest", "barrel"])
    # the engine: a cart of barrels on the floor
    for z in (s.z0 + 3, s.z0 + 4):
        b.barrel(mx - 1, 1, z, [("water_bucket", 1)], facing="north")
        b.barrel(mx, 1, z, [("bucket", 3)], facing="north")
    up = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, s.h + 1, {(lx, lz), (lx, lz - 1)})
    furnish(up, rng, st, lk, ["bed", "bed", "bed", "chest", "barrel"])
    m = pick(rng, st.roofs)
    top = gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along="x", fill=lk.infill)
    # the bell tower against the east wall
    tx0 = s.x1 + 1
    t = Shell(tx0, s.z0, tx0 + 2, s.z0 + 2, 1)
    th = top + 3
    b.fill(t.x0, 0, t.z0, t.x1, 0, t.z1, lk.found)
    b.ring(t.x0, t.z0, t.x1, t.z1, 1, th, lk.found)
    b.clear(t.x0 + 1, 1, t.z0 + 1, t.x0 + 1, th, t.z0 + 1)
    for y in range(th - 2, th + 1):   # the belfry's openings
        for x, z in ((t.x0 + 1, t.z0), (t.x0 + 1, t.z1), (t.x1, t.z0 + 1)):
            b.set(x, y, z, "air")
    b.fill(t.x0, th + 1, t.z0, t.x1, th + 1, t.z1, lk.found)
    b.set(t.x0 + 1, th, t.z0 + 1, "bell", attachment="ceiling", facing="north", powered="false")
    hip(b, t.x0, t.z0, t.x1, t.z1, th + 2, m, over=0)
    b.set(t.x0 + 1, 1, t.z0 + 1, "air")
    b.clear(s.x1, 1, t.z0 + 1, s.x1, 2, t.z0 + 1)   # into the tower from the hall
    b.set(t.x0 + 1, 0, t.z0 + 1, lk.floor)
    b.set(t.x0 + 1, th - 3, t.z0 + 1, lk.floor)
    b.set(t.x0 + 1, 2, t.z0 + 1, "air")
    hang(b, mx - 1, s.z0 + 1, 1, st, height=s.h)
    for x in (mx - 2, mx + 1):
        if solid(b, x, 3, s.z0):
            wall_torch(b, x, 3, s.z0 - 1, st, "north")
    b.sign(mx + 1, 2, s.z0 - 1, "north", ["Fire Station~In case of~fire, ring", "", "", ""], lk.wood)
    b.fill(mx - 1, 4, s.z0, mx, 4, s.z0, "red_terracotta" if st.key != "ashen" else "red_nether_bricks")
    return b


@gen("lockup", freq="rare", n=3)
def lockup(rng, st):
    """The town lock-up: a keeper's desk and two barred cells, straw on the floor."""
    b = new("Lock-up", "city", 10, 10)
    stone_ = pick(rng, st.stone)
    lk = look(rng, st, timbered=False)
    lk.infill, lk.jetty, lk.buttress = stone_, False, False
    lk.pane = "iron_bars"
    s = Shell(1, 2, 8, 8, 1)
    shell(b, s, lk)
    dx = front_door(b, s, lk, 3)
    b.door(dx, 1, s.z0, "south", material="iron_door")
    for z in (s.z0 + 2,):
        b.set(s.x0, 2, z, "iron_bars", **WL)
        b.set(s.x1, 2, z, "iron_bars", **WL)
    # two cells along the back, barred fronts
    cz = s.z1 - 3
    b.fill(s.x0 + 1, 1, cz, s.x1 - 1, s.h - 1, cz, "iron_bars", **WL)
    b.fill(4, 1, cz + 1, 4, s.h - 1, s.z1 - 1, stone_)
    b.set(4, 1, cz, stone_)
    b.set(4, 2, cz, stone_)
    b.set(4, 3, cz, stone_)
    for x0, x1 in ((s.x0 + 1, 3), (5, s.x1 - 1)):
        dxx = (x0 + x1) // 2 + (1 if x1 - x0 >= 2 else 0)
        b.door(dxx, 1, cz, "south", material="iron_door")
        slab(b, x0, 1, s.z1 - 1, "smooth_stone_slab")
        b.set(x1, 1, s.z1 - 1, "cauldron")
        for x in range(x0, x1 + 1):
            for z in range(cz + 1, s.z1):
                if b.empty(x, 1, z) and rng.random() < 0.5:
                    b.set(x, 1, z, "yellow_carpet")
    # the keeper's corner
    desk(lk.wood)(b, s.x1 - 2, 1, s.z0 + 1, "south")
    b.chest(s.x1 - 1, 1, s.z0 + 1, "west", [("iron_ingot", 2), ("tripwire_hook", 3)])
    b.set(s.x0 + 1, 1, s.z0 + 1, "lectern", facing="east", has_book="false", powered="false")
    hang(b, 5, s.z0 + 2, 1, st)
    roof(b, rng, st, s, lk, kind="hip" if st.key != "ochre" else "flat")
    # cells need a lamp the prisoners can't reach: one hangs in each from the ceiling
    for x in (2, 6):
        hang(b, x, cz + 2, 1, st)
    b.sign(dx + 1, 2, s.z0 - 1, "north", ["2[lockup]", "", "", ""], lk.wood)
    return b


@gen("courthouse", freq="very rare", n=2, unique=True)
def courthouse(rng, st):
    """A columned court: the judge's bench raised at the back, the dock, the jury's benches, the
    public on pews; records upstairs."""
    W, D = rng.choice((13, 15)), rng.randint(12, 14)
    yard = 4
    b = new("Courthouse", "city", W + 2, yard + D + 1)
    stone_ = pick(rng, st.stone)
    tm = trim_of(stone_)
    lk = look(rng, st, timbered=False)
    lk.infill, lk.found, lk.window_tall = stone_, pick(rng, st.found), True
    lk.jetty = lk.buttress = lk.grille = False
    s = Shell(1, yard, W, yard + D - 1, 1, h=7)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    front_door(b, s, lk, mx, wide=False)
    b.door(mx - 1, 1, s.z0, "south", lk.wood, hinge="left")
    b.door(mx + 1, 1, s.z0, "south", lk.wood, hinge="right")
    for x in (mx - 1, mx + 1):
        b.set(x, 0, s.z0, lk.floor)
        b.set(x, 3, s.z0, stone_)
    b.path(mx - 1, 0, mx + 1, s.z0 - 1)
    windows(b, s, lk, skip={(x, s.z0) for x in range(mx - 2, mx + 3)}, every=2)
    # the portico: steps, columns, a pediment
    px0, px1 = mx - 4, mx + 4
    b.fill(px0, 0, s.z0 - 3, px1, 0, s.z0 - 1, stone_)
    for x in range(px0, px1 + 1, 2):
        b.fill(x, 1, s.z0 - 3, x, s.h - 1, s.z0 - 3, wall_of(stone_), **WL)
        b.set(x, 1, s.z0 - 3, stone_)
    b.fill(px0, s.h, s.z0 - 3, px1, s.h, s.z0 - 1, stone_)
    pm = Mat(f"{tm}_stairs", f"{tm}_slab", FULL.get(tm, stone_))
    draw_roof(b, roof_levels(px0, s.z0 - 3, px1, s.z0, "gable", "z", 0, 1, ends=(True, False)), s.h + 1, pm, 1, stone_,
              walls=[(px0, s.z0 - 3, px1, s.z0 - 1)])
    b.path(mx - 1, 0, mx + 1, s.z0 - 4) if s.z0 - 4 >= 0 else None
    b.sign(mx, s.h, s.z0 - 4, "north", ["2[court]", "", "", ""], lk.wood)
    for x in (px0 + 1, px1 - 1):
        hang(b, x, s.z0 - 2, 1, st, height=s.h - 1)
    # inside: the bench, the dock, the jury, pews
    bz = s.z1 - 2
    b.fill(s.x0 + 1, 1, bz, s.x1 - 1, 1, s.z1 - 1, f"{lk.wood}_planks")
    for x in range(mx - 2, mx + 3):
        b.set(x, 2, bz, f"{lk.wood}_slab", type="top", **WL) if x != mx else b.set(x, 2, bz, f"{lk.wood}_planks")
    stair(b, mx, 2, s.z1 - 1, f"{lk.wood}_stairs", "south")
    b.set(mx - 1, 3, bz, "candle", candles="2", lit="false", **WL)
    for x in range(s.x0 + 2, s.x0 + 5):   # the dock
        fence(b, x, 1, bz - 3, x, 1, bz - 3, lk.wood)
    fence(b, s.x0 + 4, 1, bz - 2, s.x0 + 4, 1, bz - 2, lk.wood)
    for z in range(bz - 5, bz - 1):   # the jury box
        stair(b, s.x1 - 1, 1, z, f"{lk.wood}_stairs", "east")
        stair(b, s.x1 - 2, 1, z, f"{lk.wood}_stairs", "east") if z % 2 else None
    for z in range(s.z0 + 2, bz - 5, 2):
        for x in range(s.x0 + 1, s.x1):
            if abs(x - mx) > 1:
                stair(b, x, 1, z, f"{lk.wood}_stairs", "south")
    b.chest(s.x0 + 1, 2, s.z1 - 1, "east", TREASURE)
    for x in range(s.x0 + 2, s.x1 - 1, 3):
        hang_chain(b, x, (s.z0 + s.z1) // 2, s.h - 2, st.light)
    b.fill(s.x0 + 1, 1, s.z0 + 1, s.x0 + 1, 3, s.z0 + 1, "bookshelf")
    b.fill(s.x1 - 1, 1, s.z0 + 1, s.x1 - 1, 3, s.z0 + 1, "bookshelf")
    for x in (mx - 3, mx + 3):
        b.set(x, 2, s.z1 - 1, st.light, hanging="false", **WL)
    hip(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), fill=stone_)
    cornice(b, s, f"{tm}_stairs", s.top - 1)
    return b


@gen("bank", freq="rare", n=3, unique=True)
def bank(rng, st):
    """A counting house: tellers behind a grille, ledgers on the shelves, and under the floor the
    vault, down a ladder behind the counter."""
    W, D = rng.randint(9, 11), rng.randint(9, 10)
    yard = 2
    b = new("Counting House", "city", W + 2, yard + D + 1)
    lk = look(rng, st, timbered=False)
    lk.infill = pick(rng, st.stone)
    lk.jetty = lk.buttress = False
    lk.grille = True
    s = Shell(1, yard, W, yard + D - 1, 2)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    dx = front_door(b, s, lk, mx)
    lx, lz = s.x1 - 1, s.z1 - 1
    ladder(b, lx, lz, 1, s.h, "north", support=lk.infill)
    windows(b, s, lk, skip={(mx - 1, s.z0), (mx, s.z0), (mx + 1, s.z0)}, every=2)
    door_dress(b, rng, st, lk, dx, 1, s.z0, torches=False)
    # the counter and its grille
    cz = s.z0 + 3
    for x in range(s.x0 + 1, s.x1):
        slab(b, x, 1, cz, f"{lk.wood}_slab", "top") if x != s.x0 + 1 else gate(b, x, 1, cz, lk.wood, "south")
        if x != s.x0 + 1:
            b.set(x, 2, cz, "iron_bars", **WL)
    b.set(mx, 2, cz, "air")
    for x in range(s.x0 + 2, s.x1, 2):
        stair(b, x, 1, cz + 1, f"{lk.wood}_stairs", "south") if b.empty(x, 1, cz + 1) and x != s.x0 + 2 else None
    b.set(s.x0 + 2, 1, s.z1 - 1, "lectern", facing="north", has_book="false", powered="false")
    for x in range(s.x0 + 3, s.x1 - 1):
        b.fill(x, 1, s.z1 - 1, x, 2, s.z1 - 1, "bookshelf")
    for x in (s.x0 + 1, s.x1 - 1):
        b.set(x, 1, s.z0 + 1, potted(rng))
    hang(b, mx, s.z0 + 1, 1, st)
    hang(b, mx, cz + 2, 1, st)
    up = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, s.h + 1, {(lx, lz), (lx, lz - 1)})
    furnish(up, rng, st, lk, [desk(lk.wood), desk(lk.wood), "shelf", "shelf", "chest", "bed"], carpet="green")
    roof(b, rng, st, s, lk, kind="hip" if st.key == "ochre" else pick(rng, ("hip", "gable")))
    # the vault: walls of iron, gold on the shelves, the treasure chests
    vx0, vz0, vx1, vz1 = s.x0, cz, s.x1, s.z1
    b.fill(vx0, -5, vz0, vx1, -1, vz1, lk.found)
    b.clear(vx0 + 1, -4, vz0 + 1, vx1 - 1, -2, vz1 - 1)
    hx, hz = s.x0 + 2, cz + 1
    ladder(b, hx, hz, -4, -1, "south")
    b.set(hx, 0, hz, "iron_trapdoor", facing="north", half="top", open="false", **TD)
    b.fill(vx0 + 1, -4, vz0 + 2, vx1 - 1, -2, vz0 + 2, "iron_block")
    b.door(hx + 1, -4, vz0 + 2, "south", material="iron_door")
    b.set(hx + 2, -3, vz0 + 1, "stone_button", face="wall", facing="north", powered="false")
    b.set(hx + 2, -3, vz0 + 3, "stone_button", face="wall", facing="south", powered="false")
    for x in range(vx0 + 1, vx1):
        if x != hx + 1:
            b.set(x, -4, vz1 - 1, pick(rng, ("gold_block", "raw_gold_block", "gold_block")))
    b.chest(vx1 - 1, -4, vz0 + 3, "west", TREASURE)
    b.chest(vx0 + 1, -4, vz0 + 3, "east", TREASURE)
    hanging(b, (vx0 + vx1) // 2, -2, vz1 - 2, st)
    hanging(b, (vx0 + vx1) // 2, -2, vz0 + 1, st)
    b.sign(dx + 1, 2, s.z0 - 1, "north", ["[surnames]~& [surnames]~Counting House", "", "", ""], lk.wood)
    return b


@gen("chapel_of_rest", freq="rare", n=3)
def chapel_of_rest(rng, st):
    """A small candle-lit chapel: biers under palls, candles burning on every ledge."""
    W, D = 7, rng.randint(10, 12)
    b = new("Chapel of Rest", "city", W + 2, D + 3)
    stone_ = pick(rng, st.stone)
    tm = trim_of(stone_)
    lk = look(rng, st, timbered=False)
    lk.infill, lk.found, lk.jetty, lk.buttress = stone_, stone_, False, False
    s = Shell(1, 2, W, D + 1, 1, h=6)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    front_door(b, s, lk, mx)
    pane = pick(rng, ("purple_stained_glass_pane", "white_stained_glass_pane", "black_stained_glass_pane"))
    for z in range(s.z0 + 2, s.z1 - 1, 2):
        for x in (s.x0, s.x1):
            b.fill(x, 2, z, x, 4, z, pane, **WL)
    b.fill(mx, 3, s.z1, mx, 4, s.z1, pane, **WL)
    b.fill(mx, 3, s.z0, mx, 4, s.z0, pane, **WL)
    # the biers: stone slabs with a wool pall
    for z in range(s.z0 + 3, s.z1 - 2, 3):
        b.set(mx, 1, z, stone_)
        b.set(mx, 1, z + 1, stone_)
        b.set(mx, 2, z, "white_carpet" if st.key != "ashen" else "black_carpet")
        b.set(mx, 2, z + 1, "white_carpet" if st.key != "ashen" else "black_carpet")
        for x in (mx - 2, mx + 2):
            b.set(x, 1, z, "polished_andesite" if st.key != "ashen" else "polished_blackstone")
            lit_candle(b, x, 2, z, rng.randint(2, 4))
    # the altar shelf of candles at the back
    b.fill(s.x0 + 1, 1, s.z1 - 1, s.x1 - 1, 1, s.z1 - 1, stone_)
    for x in range(s.x0 + 1, s.x1):
        lit_candle(b, x, 2, s.z1 - 1, rng.randint(1, 4), pick(rng, (None, "white", "black" if st.key == "ashen" else "yellow")))
    stair(b, s.x0 + 1, 1, s.z0 + 2, f"{pick(rng, st.woods)}_stairs", "west")
    stair(b, s.x1 - 1, 1, s.z0 + 2, f"{pick(rng, st.woods)}_stairs", "east")
    hang_chain(b, mx, s.z0 + 2, s.h - 2, st.light)
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), along="z", fill=stone_, pitch=2 if st.key != "ochre" else 1)
    stair(b, mx, 3, s.z0 - 1, f"{tm}_stairs", "south", "top")
    for x in (mx - 1, mx + 1):
        wall_torch(b, x, 2, s.z0 - 1, st, "north")
    b.sign(mx + 2, 2, s.z0 - 1, "north", ["Chapel~of Rest~Quiet please", "", "", ""], pick(rng, st.woods))
    return b


@gen("public_toilets", freq="average", n=2)
def public_toilets(rng, st):
    """Two doors, two signs, a row of stalls, a basin. Nobody has ever needed them."""
    b = new("Public Conveniences", "city", 11, 9)
    lk = look(rng, st, timbered=False)
    lk.jetty = lk.buttress = False
    s = Shell(1, 2, 9, 7, 1)
    shell(b, s, lk)
    for x, label in ((3, "Ladies"), (7, "Gentlemen")):
        b.set(x, 0, s.z0, lk.floor)
        b.door(x, 1, s.z0, "south", lk.wood)
        b.path(x, 0, x, s.z0 - 1)
        b.sign(x + (1 if x == 7 else -1), 2, s.z0 - 1, "north", [label, "", "", ""], lk.wood)
    b.fill(5, 1, s.z0 + 1, 5, 3, s.z1 - 1, lk.infill)   # the dividing wall
    for x0, x1 in ((2, 4), (6, 8)):
        for x in range(x0, x1 + 1, 2):
            b.set(x, 1, s.z1 - 1, "cauldron")
        for x in range(x0 + 1, x1, 2):
            b.fill(x, 1, s.z1 - 1, x, 2, s.z1 - 1, f"{lk.wood}_planks")
        b.set(x1, 1, s.z0 + 1, "water_cauldron", level="3") if x1 != 8 else b.set(x0, 1, s.z0 + 1, "water_cauldron", level="3")
        hang(b, x0 + 1, s.z0 + 2, 1, st)
    windows(b, s, lk, sides=("east", "west", "south"), every=2)
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if y == 2 and n.endswith("pane"):
            b.set(x, y, z, "white_stained_glass_pane" if st.key != "ashen" else "gray_stained_glass_pane", **WL)
    roof(b, rng, st, s, lk, kind="hip" if st.key != "ochre" else "flat")
    b.sign(5, 2, s.z0 - 1, "north", ["Out of~order~since~opening", "", "", ""], lk.wood)
    return b


@gen("laundry", freq="average", n=3)
def laundry(rng, st):
    """A wash house of tubs and coppers, washing lines in the yard beside it."""
    b = new("Laundry", "city", 16, 11)
    lk = look(rng, st)
    s = Shell(1, 2, 8, 8, 1)
    ground = [cauldron, cauldron, cauldron, "furnace", "barrel", "chest", topped("smooth_stone", "white_carpet")]
    dx = house_body(b, rng, st, s, lk, [ground], sign=["2[surnames]'s~Laundry", "", "", ""], kind=None)
    # the drying yard: posts with lines, sheets pegged on them
    wood = lk.wood
    x0, x1 = 10, 14
    for z in (2, 5, 8):
        fence(b, x0, 1, z, x0, 3, z, wood)
        fence(b, x1, 1, z, x1, 3, z, wood)
        for x in range(x0 + 1, x1):
            b.set(x, 3, z, f"{wood}_fence", **WL)
            if rng.random() < 0.75:
                b.set(x, 2, z, pick(rng, ("white_wool", "white_wool", "light_blue_wool", "pink_wool", WOOL)))
    b.barrel(x0 + 2, 1, 9, [("white_wool", rng.randint(2, 8))])
    flowers(b, rng, st, [(x, z) for x in range(x0, x1 + 1) for z in range(0, 10)], 0.12)
    return b


# ═════════════════════════════════════════════════════════════════════════
#  City: open places
# ═════════════════════════════════════════════════════════════════════════

@gen("bandstand", freq="average", n=3)
def bandstand(rng, st):
    """A raised octagonal stand under a round roof, chairs for the band, benches round it."""
    S = 15
    c = S // 2
    b = new("Bandstand", "city", S, S)
    b.fill(0, 0, 0, S - 1, 0, S - 1, PATH)
    stone_ = pick(rng, st.stone)
    wood = pick(rng, st.woods)
    r = 4
    for dx, dz in disc(r):
        b.set(c + dx, 1, c + dz, stone_)
    for dx, dz in disc(r - 1):
        b.set(c + dx, 1, c + dz, f"{wood}_planks")
    for dx, dz in circle(r):
        f = toward(dx, dz)
        if abs(dx) <= 1 and dz < 0:
            continue
    stair(b, c, 1, c - r - 1, f"{trim_of(stone_)}_stairs", "south")   # steps up
    b.set(c, 1, c - r, f"{wood}_planks")
    posts = [(c + round(r * math.cos(a)), c + round(r * math.sin(a))) for a in [k * math.tau / 8 for k in range(8)]]
    for x, z in posts:
        if (x, z) == (c, c - r):
            continue
        fence(b, x, 2, z, x, 4, z, wood)
    for dx, dz in circle(r):   # a rail between the posts
        if (c + dx, c + dz) not in posts and not (dz < 0 and dx == 0):
            b.set(c + dx, 2, c + dz, f"{wood}_fence", **WL)
    m = pick(rng, st.roofs)
    for dx, dz in disc(r):   # the ceiling, then a cone over it
        b.set(c + dx, 5, c + dz, f"{wood}_planks")
    top = cone(b, c, c, 6, r + 1, m)
    b.set(c, top + 1, c, "lightning_rod", facing="up", powered="false", **WL)
    for dx, dz in circle(r + 1):
        if b.empty(c + dx, 5, c + dz):
            pass
    # the band's chairs, music stands, the lamps
    for dx, dz in ((-2, 1), (0, 2), (2, 1), (-1, -1), (1, -1)):
        stair(b, c + dx, 2, c + dz, f"{wood}_stairs", toward(dx, dz) if (dx, dz) != (0, 2) else "south")
    b.set(c, 2, c, "note_block", instrument="harp", note="0", powered="false")
    b.set(c, 2, c + 1, "jukebox", has_record="false")
    hang_chain(b, c, c, 4, st.light)
    for x, z in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        lamp_post(b, x, z, st, wood)
    for x, z, f in ((2, c, "east"), (S - 3, c, "west"), (c, S - 2, "north")):
        bench(b, x if f != "north" else x - 1, z - 1 if f != "north" else z, f, wood, n=3 if f == "north" else 3)
    flowers(b, rng, st, [(x, z) for x in range(S) for z in range(S) if x in (0, S - 1) or z == S - 1], 0.3)
    return b


@gen("maze_garden", freq="rare", n=3)
def maze_garden(rng, st):
    """A hedge maze, two blocks high, its way in on the street and a fountain at the heart."""
    n = rng.choice((6, 7))
    S = 2 * n + 1
    b = new("Hedge Maze", "city", S, S)
    hedge = {"timber": "oak_leaves", "ochre": "azalea_leaves", "ashen": "dark_oak_leaves"}[st.key]
    b.fill(0, 0, 0, S - 1, 0, S - 1, PATH)
    walls = {(x, z) for x in range(S) for z in range(S)}
    seen = {(0, 0)}
    stack = [(0, 0)]
    walls.discard((1, 1))
    while stack:
        cx, cz = stack[-1]
        nbs = [(cx + a, cz + c) for a, c in ((1, 0), (-1, 0), (0, 1), (0, -1))
               if 0 <= cx + a < n and 0 <= cz + c < n and (cx + a, cz + c) not in seen]
        if not nbs:
            stack.pop()
            continue
        nx, nz = pick(rng, nbs)
        walls.discard((2 * cx + 1 + (nx - cx), 2 * cz + 1 + (nz - cz)))
        walls.discard((2 * nx + 1, 2 * nz + 1))
        seen.add((nx, nz))
        stack.append((nx, nz))
    mid = n // 2
    walls.discard((2 * mid + 1, 0))   # the way in, from the street
    for a in range(-1, 2):   # the heart: a clearing
        for c_ in range(-1, 2):
            walls.discard((2 * mid + 1 + a, 2 * mid + 1 + c_))
    for x, z in walls:
        b.set(x, 0, z, GROUND)
        b.fill(x, 1, z, x, 2, z, hedge, **LEAF)
    cx = cz = 2 * mid + 1
    stone_ = pick(rng, st.stone)
    b.set(cx, 0, cz, "water", level="0")
    for d in STEP.values():
        slab(b, cx + d[0], 1, cz + d[1], slab_of_block(stone_))
    b.set(cx, 1, cz, stone_)
    b.set(cx, 2, cz, wall_of(stone_), **WL)
    light(b, cx, 3, cz, st)
    b.set(cx, 0, cz, stone_)
    for x, z in ((cx - 1, cz - 1), (cx + 1, cz + 1)):
        stair(b, x, 1, z, f"{pick(rng, st.woods)}_stairs", "south" if z < cz else "north")
    for x in (2 * mid, 2 * mid + 2):   # gateposts with lamps at the way in
        b.fill(x, 1, 0, x, 2, 0, stone_)
        b.set(x, 3, 0, st.light, hanging="false", **WL)
    b.sign(2 * mid + 1, 2, 0, "east", ["The Maze~Exit at~the centre", "", "", ""], pick(rng, st.woods))
    return b


@gen("topiary_garden", freq="average", n=3)
def topiary_garden(rng, st):
    """Clipped bushes in balls, cones and tiers along gravel walks, a bench in the middle."""
    S = rng.choice((13, 15))
    c = S // 2
    b = new("Topiary Garden", "city", S, S)
    wood = pick(rng, st.woods)
    hedge = {"timber": "oak_leaves", "ochre": "azalea_leaves", "ashen": "dark_oak_leaves"}[st.key]
    b.path(c - 1, 0, c + 1, S - 1)
    b.path(0, c - 1, S - 1, c + 1)
    for x in range(S):   # low box hedges round the edge
        for z in (0, S - 1):
            if abs(x - c) > 1:
                b.set(x, 1, z, hedge, **LEAF)
    for z in range(S):
        for x in (0, S - 1):
            if abs(z - c) > 1:
                b.set(x, 1, z, hedge, **LEAF)
    shapes = ("ball", "cone", "tiers", "ball", "spiral")
    for qx, qz in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
        shape = pick(rng, shapes)
        b.set(qx, 0, qz, GROUND)
        b.set(qx, 1, qz, f"{wood}_log" if wood != "pale_oak" else "stripped_pale_oak_log", axis="y")
        if shape == "ball":
            b.set(qx, 2, qz, f"{wood}_log" if wood != "pale_oak" else "stripped_pale_oak_log", axis="y")
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        if abs(dx) + abs(dy) + abs(dz) <= 2:
                            if b.empty(qx + dx, 4 + dy, qz + dz):
                                b.set(qx + dx, 4 + dy, qz + dz, hedge, **LEAF)
            b.set(qx, 4, qz, hedge, **LEAF)
        elif shape == "cone":
            for y, r in ((2, 1), (3, 1), (4, 0), (5, 0)):
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        if abs(dx) + abs(dz) <= r:
                            b.set(qx + dx, y, qz + dz, hedge, **LEAF)
        elif shape == "tiers":
            for y, r in ((2, 1), (4, 1), (6, 0)):
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        b.set(qx + dx, y, qz + dz, hedge, **LEAF)
            for y in (3, 5):
                b.set(qx, y, qz, f"{wood}_log" if wood != "pale_oak" else "stripped_pale_oak_log", axis="y")
        else:
            for y in range(2, 7):
                a = y * math.pi / 2
                b.set(qx + round(math.cos(a)), y, qz + round(math.sin(a)), hedge, **LEAF)
                b.set(qx, y, qz, hedge, **LEAF)
    for qx, qz in ((c - 3, c - 3), (c + 3, c - 3), (c - 3, c + 3), (c + 3, c + 3)):
        b.set(qx, 1, qz, hedge, **LEAF)   # little cubes at the crossing
    b.set(c, 1, c, pick(rng, st.stone))
    b.set(c, 2, c, potted(rng))
    for x, z in ((c - 2, c - 2), (c + 2, c + 2)):
        lamp_post(b, x, z, st, wood)
    stair(b, c - 2, 1, c + 2, f"{wood}_stairs", "north") if b.empty(c - 2, 1, c + 2) else None
    stair(b, c + 2, 1, c - 2, f"{wood}_stairs", "south") if b.empty(c + 2, 1, c - 2) else None
    flowers(b, rng, st, [(x, z) for x in range(1, S - 1) for z in range(1, S - 1)], 0.2)
    return b


@gen("sundial_plaza", freq="average", n=3)
def sundial_plaza(rng, st):
    """A paved round with a sundial at its heart, the hours set into the paving, benches and lamps
    round its rim."""
    S = rng.choice((11, 13))
    c = S // 2
    b = new("Sundial Plaza", "city", S, S)
    stone_ = pick(rng, st.stone)
    pave = {"timber": "polished_andesite", "ochre": "smooth_sandstone", "ashen": "polished_deepslate"}[st.key]
    mark = {"timber": "chiseled_stone_bricks", "ochre": "chiseled_sandstone", "ashen": "chiseled_polished_blackstone"}[st.key]
    wood = pick(rng, st.woods)
    r = c - 1
    for dx, dz in disc(r):
        b.set(c + dx, 0, c + dz, pave)
    for k in range(12):   # the hours
        a = k * math.tau / 12
        b.set(c + round((r - 1) * math.cos(a)), 0, c + round((r - 1) * math.sin(a)), mark)
    b.path(c, 0, c, c - r)
    b.set(c, 1, c, stone_)
    slab(b, c, 2, c, slab_of_block(stone_))
    # the gnomon: a stair and a rod pointing north
    b.set(c, 2, c, slab_of_block(stone_), type="bottom", **WL)
    b.set(c, 2, c, "smooth_stone_slab", type="bottom", **WL)
    stair(b, c, 1, c - 1, f"{trim_of(stone_)}_stairs", "south")
    stair(b, c, 1, c + 1, f"{trim_of(stone_)}_stairs", "north")
    stair(b, c - 1, 1, c, f"{trim_of(stone_)}_stairs", "east")
    stair(b, c + 1, 1, c, f"{trim_of(stone_)}_stairs", "west")
    b.set(c, 2, c, "lightning_rod", facing="up", powered="false", **WL)
    b.sign(c, 1, c - 2, "north", ["It is later~than you~think", "", "", ""], wood, wall=False)
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        x, z = c + round(r * math.cos(a)), c + round(r * math.sin(a))
        lamp_post(b, x, z, st, wood)
    bench(b, c - 1, c + r, "north", wood, n=3)
    flowers(b, rng, st, [(x, z) for x in range(S) for z in range(S) if b.get(x, 0, z) == GROUND], 0.4)
    return b


@gen("market_cross", freq="average", n=3)
def market_cross(rng, st):
    """A buttercross: a roofed pavilion on stepped plinth where the market sets out, a cross shaft
    rising from its roof."""
    S = 11
    c = S // 2
    b = new("Market Cross", "city", S, S)
    b.fill(0, 0, 0, S - 1, 0, S - 1, PATH)
    stone_ = pick(rng, st.stone)
    tm = trim_of(stone_)
    wood = pick(rng, st.woods)
    b.fill(2, 1, 2, 8, 1, 8, stone_)
    for x in range(2, 9):
        stair(b, x, 1, 1, f"{tm}_stairs", "south")
        stair(b, x, 1, 9, f"{tm}_stairs", "north")
    for z in range(2, 9):
        stair(b, 1, 1, z, f"{tm}_stairs", "east")
        stair(b, 9, 1, z, f"{tm}_stairs", "west")
    for x, z in ((2, 2), (8, 2), (2, 8), (8, 8)):
        b.fill(x, 2, z, x, 4, z, stone_)
    for x, z in ((5, 2), (2, 5), (8, 5), (5, 8)):
        b.fill(x, 2, z, x, 4, z, wall_of(stone_), **WL)
    b.fill(2, 5, 2, 8, 5, 8, stone_)
    hip(b, 2, 2, 8, 8, 6, pick(rng, st.roofs), over=1)
    # the shaft and its head through the roof's point
    top = highest(b, c, c)
    b.fill(c, top + 1, c, c, top + 3, c, wall_of(stone_), **WL)
    b.set(c, top + 4, c, stone_)
    for d, (dx, dz) in STEP.items():
        stair(b, c + dx, top + 4, c + dz, f"{tm}_stairs", OPPOSITE[d], "top")
    b.set(c, top + 5, c, st.light, hanging="false", **WL)
    # stalls under it
    for x in (3, 7):
        b.barrel(x, 2, 7, [(pick(rng, ("apple", "bread", "egg", "carrot")), rng.randint(3, 12))])
    counter_row(b, 4, 6, 7, 2, wood)
    b.set(5, 3, 7, "cake", bites="0")
    hanging(b, c, 4, c, st)
    b.sign(c, 2, 3, "south", ["Market~every day~but today", "", "", ""], wood, wall=False)
    return b


# ═════════════════════════════════════════════════════════════════════════
#  City: water
# ═════════════════════════════════════════════════════════════════════════

@gen("well_house", freq="average", n=3)
def well_house(rng, st):
    """A small house built over a deep well: the winding drum and bucket inside, a trough and a
    bench by the door."""
    b = new("Well House", "city", 9, 9)
    lk = look(rng, st)
    lk.jetty = lk.buttress = False
    s = Shell(1, 2, 7, 7, 1)
    shell(b, s, lk)
    dx = front_door(b, s, lk, 4)
    windows(b, s, lk, sides=("east", "west", "south"), every=2)
    stone_ = pick(rng, st.found)
    cx, cz = 4, 5
    b.fill(cx - 1, -9, cz - 1, cx + 1, 1, cz + 1, stone_)
    b.fill(cx, -8, cz, cx, -1, cz, "water", level="0")
    b.set(cx, 0, cz, "air")
    b.set(cx, 1, cz, "air")
    # the winding drum on posts over it
    for x in (cx - 1, cx + 1):
        fence(b, x, 2, cz, x, 2, cz, lk.wood)
    b.set(cx, 3, cz, lk.log, axis="x")
    b.set(cx - 1, 3, cz, lk.log, axis="x")
    b.set(cx + 1, 3, cz, lk.log, axis="x")
    b.set(cx, 2, cz, "iron_chain", axis="y", **WL)
    b.set(s.x0 + 1, 1, s.z0 + 1, "water_cauldron", level="3")
    b.set(s.x1 - 1, 1, s.z0 + 1, "cauldron")
    roof(b, rng, st, s, lk, kind=pick(rng, ("hip", "gable")) if st.key != "ochre" else "hip")
    hang(b, s.x0 + 1, s.z1 - 1, 1, st)
    # a trough and bench outside
    for x in range(1, 3):
        slab(b, x, 1, 1, slab_of_block(stone_)) if b.get(x, 0, 1) == GROUND else None
    b.set(6, 1, 1, "water_cauldron", level="3") if b.get(6, 0, 1) == GROUND else None
    b.sign(dx + 1, 2, s.z0 - 1, "north", ["Well House~Water is~free~Count the~rungs"[:40], "", "", ""], lk.wood)
    return b


@gen("cistern", freq="rare", n=3)
def cistern(rng, st):
    """A pump house on the street over a great vaulted tank under it, down a stair of ladders:
    columns standing in still water, a walkway round the edge."""
    b = new("Cistern", "city", 13, 13)
    lk = look(rng, st, timbered=False)
    lk.jetty = lk.buttress = False
    stone_ = pick(rng, st.stone)
    lk.infill = stone_
    s = Shell(3, 2, 9, 7, 1)
    shell(b, s, lk)
    dx = front_door(b, s, lk, 6)
    windows(b, s, lk, sides=("east", "west"), every=2)
    # the tank: the whole lot underground
    x0, z0, x1, z1 = 1, 1, 11, 11
    b.fill(x0 - 1, -9, z0 - 1, x1 + 1, -1, z1 + 1, stone_)
    b.clear(x0, -7, z0, x1, -2, z1)
    b.fill(x0 + 1, -8, z0 + 1, x1 - 1, -7, z1 - 1, "water", level="0")
    b.fill(x0, -7, z0, x1, -7, z1, stone_)   # the walkway round the edge
    b.fill(x0 + 1, -7, z0 + 1, x1 - 1, -7, z1 - 1, "water", level="0")
    for x in range(x0 + 3, x1 - 1, 3):
        for z in range(z0 + 3, z1 - 1, 3):
            b.fill(x, -8, z, x, -2, z, stone_)
            b.set(x, -2, z, stone_)
            for d, (ddx, ddz) in STEP.items():
                stair(b, x + ddx, -2, z + ddz, f"{trim_of(stone_)}_stairs", OPPOSITE[d], "top")
    for x in range(x0, x1 + 1, 4):
        for z in (z0, z1):
            b.set(x, -6, z, st.light, hanging="false", **WL)
    for z in range(z0 + 4, z1, 4):
        for x in (x0, x1):
            b.set(x, -6, z, st.light, hanging="false", **WL)
    for x in range(x0 + 2, x1, 3):
        for z in range(z0 + 2, z1, 6):
            hanging(b, x, -3, z, st) if b.empty(x, -3, z) and not b.empty(x, -2, z) else None
    # down from the pump house: a hatch and a ladder
    hx, hz = s.x0 + 1, s.z1 - 1
    ladder(b, hx, hz, -6, -1, "north")
    b.set(hx, 0, hz, f"{lk.wood}_trapdoor", facing="north", half="top", open="false", **TD)
    b.set(hx, -1, hz + 1, stone_)
    # the pump: a cauldron under a chain off a beam, troughs
    b.set(s.x1 - 1, 1, s.z1 - 1, "water_cauldron", level="3")
    b.set(s.x1 - 2, 1, s.z1 - 1, "cauldron")
    b.barrel(s.x0 + 1, 1, s.z0 + 1, [("bucket", rng.randint(1, 4))])
    hang(b, 6, 4, 1, st)
    roof(b, rng, st, s, lk, kind=pick(rng, ("hip", "gable")) if st.key != "ochre" else "flat")
    b.sign(dx + 1, 2, s.z0 - 1, "north", ["Cistern~Drinking water~Do not swim", "", "", ""], lk.wood)
    return b


@gen("houseboats", freq="rare", n=3)
def houseboats(rng, st):
    """A stretch of canal walled in at both ends, a towpath along the front, two or three
    houseboats moored on it."""
    W, D = 19, 12
    b = new("Canal Moorings", "city", W, D)
    stone_ = pick(rng, st.found)
    wood = pick(rng, st.woods)
    b.path(0, 0, W - 1, 2)
    # the channel: z 4..10, x 1..W-2, closed all round by the quay
    cz0, cz1 = 4, 10
    b.fill(0, -3, cz0 - 1, W - 1, 0, cz1 + 1, stone_)
    b.fill(1, -2, cz0, W - 2, 0, cz1, "water", level="0")
    for x in range(0, W):
        if x % 6 == 3:
            b.set(x, 1, cz0 - 1, wall_of(stone_), **WL)   # bollards
        else:
            slab(b, x, 1, cz0 - 1, slab_of_block(stone_))
    x = 2
    colours = ("red", "green", "blue", "yellow", "cyan", "white")
    while x + 5 < W - 1:
        L = min(rng.randint(6, 7), W - 2 - x)
        hx0, hx1 = x, x + L - 1
        hz0, hz1 = cz0 + 1, cz0 + 4
        paint = f"{pick(rng, colours)}_terracotta"
        b.fill(hx0, -1, hz0, hx1, -1, hz1, f"{wood}_planks")
        b.fill(hx0, 0, hz0, hx1, 0, hz1, f"{wood}_planks")
        for xx in range(hx0, hx1 + 1):
            for zz in range(hz0, hz1 + 1):
                if xx in (hx0, hx1) or zz in (hz0, hz1):
                    b.set(xx, 0, zz, paint)
        # the cabin, a deck at the bow (west) end
        c0, c1 = hx0 + 2, hx1
        b.ring(c0, hz0, c1, hz1, 1, 2, f"{wood}_planks")
        b.fill(c0 + 1, 0, hz0 + 1, c1 - 1, 0, hz1 - 1, f"{wood}_planks")
        b.fill(c0, 3, hz0, c1, 3, hz1, f"{wood}_planks")
        dz = hz0 + 1
        b.door(c0, 1, dz, "east", wood)
        for zz in (hz0 + 1, hz0 + 2):
            b.set(c1, 2, zz, "glass_pane", **WL)
        for xx in range(c0 + 1, c1):
            if (xx - c0) % 2:
                b.set(xx, 2, hz0, "glass_pane", **WL)
                b.set(xx, 2, hz1, "glass_pane", **WL)
        gable(b, c0, hz0, c1, hz1, 3, mat(wood), along="x", fill=f"{wood}_planks", over=0, end_over=0)
        hanging(b, c0 + 2, 2, hz0 + 2, st)
        b.bed(c1 - 1, 1, hz1 - 1, "west", pick(rng, st.beds))
        b.chest(c1 - 1, 1, hz0 + 1, "west")
        b.barrel(c0 + 1, 1, hz1 - 1, [("cod", rng.randint(1, 6))])
        b.set(hx0, 1, hz0 + 1, f"{wood}_fence", **WL)
        b.set(hx0, 2, hz0 + 1, st.light, hanging="false", **WL)
        b.set(hx0 + 1, 1, hz1 - 1, potted(rng))
        b.set(hx0 + 1, 0, cz0, f"{wood}_planks")   # a plank gangway to the quay
        b.set(hx0 + 1, 1, cz0 - 1, "air")
        b.set(hx0 + 1, 0, cz0 - 1, f"{wood}_planks")
        x = hx1 + 2
    lamp_post(b, 0, 2, st, wood)
    lamp_post(b, W - 1, 2, st, wood)
    b.sign(W // 2, 1, 1, "north", ["Moorings~No wake", "", "", ""], wood, wall=False)
    return b


@gen("terraced_gardens", freq="average", n=3)
def terraced_gardens(rng, st):
    """Three garden terraces stepping up from the street behind retaining walls, a stair up the
    middle, a pergola on the top terrace."""
    S = 13
    c = S // 2
    b = new("Terraced Gardens", "city", S, S)
    stone_ = pick(rng, st.found)
    wood = pick(rng, st.woods)
    tm = trim_of(stone_)
    rows = ((1, 4, 0), (5, 8, 1), (9, 11, 2))
    for z0, z1, k in rows:
        if k:
            b.fill(0, 1, z0, S - 1, k, z1, "dirt")
        for z in range(z0, z1 + 1):
            for x in range(S):
                b.set(x, k, z, GROUND)
        b.fill(0, 0 if k == 0 else 1, z0, S - 1, k, z0, stone_) if k else None
        if k:
            for x in range(S):
                slab(b, x, k + 1, z0, slab_of_block(stone_)) if abs(x - c) > 1 else None
    for z0, z1, k in rows:   # beds on each terrace
        crop = pick(rng, st.crops)
        for z in range(z0 + 1, z1 + 1):
            for x in list(range(1, c - 1)) + list(range(c + 2, S - 1)):
                if (z - z0) % 3 == 0:
                    b.set(x, k, z, "water", level="0") if x in (1, S - 2) and False else None
                f = pick(rng, st.flowers)
                if f != "brown_mushroom" and rng.random() < 0.6:
                    b.set(x, k + 1, z, f)
    for k, z in ((1, 5), (2, 9)):   # the stairs up the middle
        for x in range(c - 1, c + 2):
            stair(b, x, k, z, f"{tm}_stairs", "south")
            b.set(x, k + 1, z, "air")
            b.set(x, k - 1, z, stone_)
    for z in range(0, 12):
        for x in range(c - 1, c + 2):
            k = 0 if z < 5 else 1 if z < 9 else 2
            if not b.get(x, k, z).endswith("stairs"):
                b.set(x, k, z, PATH if k == 0 else "gravel")
                b.set(x, k + 1, z, "air")
    # the pergola on top
    for x in (c - 3, c + 3):
        for z in (9, 11):
            fence(b, x, 3, z, x, 4, z, wood)
    for x in range(c - 3, c + 4):
        for z in (9, 11):
            slab(b, x, 5, z, f"{wood}_slab")
        b.set(x, 5, 10, f"{wood}_slab", type="bottom", **WL)
    for x in range(c - 3, c + 4):
        for z in range(9, 12):
            b.set(x, 5, z, f"{wood}_slab", type="bottom", **WL) if (x + z) % 2 == 0 else None
            b.set(x, 3, z, "air") if b.get(x, 3, z) not in (f"{wood}_fence",) else None
    bench(b, c - 1, 11, "north", wood, n=3)
    lamp_post(b, c - 2, 0, st, wood)
    lamp_post(b, c + 2, 0, st, wood)
    hanging(b, c, 4, 10, st)
    return b


@gen("treehouse", freq="rare", n=3)
def treehouse(rng, st):
    """A great tree with a house in its crown: a deck round the trunk, a ladder up, a roofed hut."""
    S = 13
    c = S // 2
    b = new("Treehouse", "city", S, S)
    kind = {"timber": "oak", "ochre": "jungle", "ashen": "dark_oak"}[st.key]
    log = {"timber": "oak_log", "ochre": "jungle_log", "ashen": "dark_oak_log"}[st.key]
    leaves = {"timber": "oak_leaves", "ochre": "jungle_leaves", "ashen": "dark_oak_leaves"}[st.key]
    wood = {"timber": "spruce", "ochre": "acacia", "ashen": "spruce"}[st.key]
    h = 6
    for dx in (0, 1):
        for dz in (0, 1):
            b.fill(c - 1 + dx, 1, c + dz, c - 1 + dx, h + 8, c + dz, log, axis="y")
    # roots
    for x, z in ((c - 2, c), (c + 1, c + 1), (c - 1, c + 2), (c, c - 1)):
        b.set(x, 1, z, log, axis="x" if x in (c - 2, c + 1) else "z")
    # the deck
    d0, d1 = c - 4, c + 3
    b.fill(d0, h, d0 + 1, d1, h, d1 + 1, f"{wood}_planks")
    for x in range(d0, d1 + 1):
        for z in range(d0 + 1, d1 + 2):
            if x in (d0, d1) or z in (d0 + 1, d1 + 1):
                b.set(x, h + 1, z, f"{wood}_fence", **WL)
    for x, z in ((d0, d0 + 1), (d1, d0 + 1), (d0, d1 + 1), (d1, d1 + 1)):   # props under the deck
        for y in range(1, h):
            b.set(x, y, z, f"{wood}_fence", **WL)
    # the ladder up the trunk's north face, through a gap in the rail
    lx, lz = c, c - 1
    ladder(b, lx, lz, 1, h + 1, "north")
    b.set(lx, h, lz, "ladder", facing="north", **WL)
    b.set(c, 1, c - 1, "ladder", facing="north", **WL)
    b.path(lx, 0, lx, 0)
    # the hut on the deck, on the south side of the trunk
    hx0, hz0, hx1, hz1 = d0 + 1, c + 2, d1 - 1, d1
    hl = look(rng, st)
    hl.jetty = hl.buttress = False
    hs = Shell(hx0, hz0, hx1, hz1, 1, h=4)
    b.ring(hx0, hz0, hx1, hz1, h + 1, h + 3, f"{wood}_planks")
    for x, z in ((hx0, hz0), (hx1, hz0), (hx0, hz1), (hx1, hz1)):
        b.fill(x, h + 1, z, x, h + 3, z, log, axis="y")
    b.clear(hx0 + 1, h + 1, hz0 + 1, hx1 - 1, h + 3, hz1 - 1)
    b.door(hx1 - 1, h + 1, hz0, "south", wood)
    for x in (hx0, hx1):
        b.set(x, h + 2, (hz0 + hz1) // 2, "glass_pane", **WL)
    b.set(hx0 + 1, h + 2, hz1, "glass_pane", **WL)
    b.bed(hx0 + 1, h + 1, hz0 + 1, "south", pick(rng, st.beds)) if hz1 - hz0 >= 3 else None
    b.chest(hx1 - 1, h + 1, hz1 - 1, "north", TREASURE if rng.random() < 0.3 else 3)
    b.fill(hx0, h + 4, hz0, hx1, h + 4, hz1, f"{wood}_planks")
    gable(b, hx0, hz0, hx1, hz1, h + 4, mat(wood), along="x", fill=f"{wood}_planks")
    hanging(b, (hx0 + hx1) // 2, h + 3, (hz0 + hz1) // 2, st)
    for x, z in ((d0, d0 + 1), (d1, d0 + 1)):
        light(b, x, h + 2, z, st)
    # the crown over everything
    top = max(highest(b, x, z) for x in range(hx0, hx1 + 1) for z in range(hz0, hz1 + 1))
    cy = top + 2
    for dy in range(-2, 3):
        r = 4 - abs(dy)
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if dx * dx + dz * dz <= r * r + 1 and rng.random() < 0.9:
                    x, z = c + dx, c + dz
                    if b.empty(x, cy + dy, z) and cy + dy > top:
                        b.set(x, cy + dy, z, leaves, **LEAF)
    for y in range(h + 9, cy + 1):
        b.set(c - 1, y, c, log, axis="y")
    return b


# ═════════════════════════════════════════════════════════════════════════
#  City: halls and the streetscape
# ═════════════════════════════════════════════════════════════════════════

@gen("arcade", freq="average", n=3)
def arcade(rng, st):
    """A row of small shops behind a covered arcade of arches, all under one roof."""
    n = rng.choice((3, 4))
    uw = 4
    W = n * uw + 1
    D = 7
    b = new("Arcade", "city", W + 2, D + 3)
    stone_ = pick(rng, st.stone)
    tm = trim_of(stone_)
    lk = look(rng, st, timbered=False)
    lk.infill, lk.jetty, lk.buttress = pick(rng, st.infill) if st.key == "ochre" else stone_, False, False
    z0 = 1                # the arcade's front (the arches)
    zw = 3                # the shop fronts
    s = Shell(1, z0, W, D + 1, 1, h=4)
    b.fill(s.x0, 0, z0, s.x1, 0, zw, pick(rng, st.found))
    b.fill(s.x0, 0, zw, s.x1, 0, s.z1, lk.floor)
    b.ring(1, zw, W, s.z1, 1, 4, lk.infill)
    for i in range(n):
        x0 = 1 + i * uw
        if i:
            b.fill(x0, 1, zw, x0, 4, s.z1, lk.infill)
        dx = x0 + 2
        b.door(dx, 1, zw, "south", lk.wood)
        b.set(dx - 1, 2, zw, "glass_pane", **WL)
        b.set(dx + 1, 2, zw, "glass_pane", **WL)
        room = Room(b, x0 + 1, zw + 1, x0 + uw - 1, s.z1 - 1, 1, {(dx, zw + 1)})
        furnish(room, rng, st, lk, pick(rng, (["barrel", "chest", "plant"], ["loom", WOOL, "chest"], ["shelf", "lectern"],
                                              ["smoker", "barrel", "cake"], ["brewing_stand", "barrel", "plant"])))
        b.sign(dx, 3, zw - 1, "north", ["2[surnames]'s", "", "", ""], lk.wood)
        b.path(dx, 0, dx, 0)
    # the arches: piers every two blocks along the front, upside-down stairs springing between
    for x in range(1, W + 1):
        if (x - 1) % 2 == 0:
            b.fill(x, 1, z0, x, 3, z0, stone_)
        else:
            stair(b, x, 3, z0, f"{tm}_stairs", "south", "top")
    for z in (z0, z0 + 1):
        b.fill(1, 1, z, 1, 3, z, stone_)
        b.fill(W, 1, z, W, 3, z, stone_)
    b.fill(1, 4, z0, W, 4, zw, stone_)
    b.fill(2, 4, z0 + 1, W - 1, 4, zw - 1, lk.floor)
    for x in range(3, W, 4):
        hanging(b, x, 3, z0 + 1, st)
    m = pick(rng, st.roofs)
    gable(b, 1, z0, W, s.z1, 4, m, along="x", fill=lk.infill if st.key != "ochre" else stone_)
    # chimneys on the party walls
    for i in range(1, n):
        if rng.random() < 0.5:
            x = 1 + i * uw
            chimney(b, x, s.z1, 4, highest(b, x, s.z1) + 2, "bricks")
    return b


@gen("district_arch", freq="rare", n=3)
def district_arch(rng, st):
    """A gate arch across a district's street: two towers, a room over the arch with a window
    each way, the district's name on a sign."""
    b = new("District Arch", "city", 13, 9)
    stone_ = pick(rng, st.stone)
    tm = trim_of(stone_)
    wood = pick(rng, st.woods)
    b.path(4, 0, 8, 8)
    for x0 in (1, 9):
        b.fill(x0, 0, 2, x0 + 2, 9, 6, stone_)
        b.clear(x0 + 1, 1, 3, x0 + 1, 8, 5)
    # the passage roof and the room above it
    b.fill(4, 5, 2, 8, 9, 6, stone_)
    b.clear(4, 1, 2, 8, 4, 6)
    for z in (2, 6):   # the arch's soffit
        stair(b, 4, 4, z, f"{tm}_stairs", "west", "top")
        stair(b, 8, 4, z, f"{tm}_stairs", "east", "top")
    b.clear(2, 6, 3, 10, 8, 5)
    b.fill(2, 5, 3, 10, 5, 5, f"{wood}_planks")
    for z in (2, 6):
        b.set(6, 7, z, "glass_pane", **WL)
        b.set(5, 7, z, "glass_pane", **WL)
        b.set(7, 7, z, "glass_pane", **WL)
    # a door into each tower from the street, a ladder up the east one
    for x0 in (1, 9):
        b.door(x0 + 1, 1, 2, "south", wood)
        b.path(x0 + 1, 0, x0 + 1, 1)
        b.set(x0 + 1, 0, 2, stone_)
        hanging(b, x0 + 1, 4, 4, st)
    ladder(b, 10, 5, 1, 5, "north")
    b.set(10, 4, 4, "air")
    b.set(10, 5, 5, "ladder", facing="north", **WL)
    b.chest(3, 6, 5, "east")
    b.set(9, 6, 3, "crafting_table")
    hanging(b, 6, 8, 4, st)
    # roofs: the towers' caps and the gable over the middle
    m = pick(rng, st.roofs)
    gable(b, 1, 2, 11, 6, 9, m, along="x", fill=stone_)
    b.sign(6, 5, 1, "north", ["[adjectives]~Quarter", "", "", ""], wood)
    b.sign(6, 5, 7, "south", ["[adjectives]~Quarter", "", "", ""], wood)
    for x in (3, 9):
        wall_torch(b, x, 3, 1, st, "north")
        wall_torch(b, x, 3, 7, st, "south")
    return b


# ── Style-specific ─────────────────────────────────────────────────────────

@gen("scriptorium", freq="rare", n=3, styles=("ashen",))
def scriptorium(rng, st):
    """A tall gothic hall of desks under lancet windows, a steep roof, buttresses between bays."""
    W, D = rng.choice((9, 11)), rng.randint(11, 13)
    b = new("Scriptorium", "city", W + 4, D + 3)
    stone_ = pick(rng, st.stone)
    lk = look(rng, st, timbered=False)
    lk.infill, lk.lancet, lk.window_tall, lk.buttress, lk.jetty = stone_, True, True, True, False
    s = Shell(2, 2, W + 1, D + 1, 1, h=7)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    front_door(b, s, lk, mx)
    windows(b, s, lk, skip={(mx, s.z0), (mx - 1, s.z0), (mx + 1, s.z0)}, every=2)
    door_dress(b, rng, st, lk, mx, 1, s.z0, torches=True)
    buttresses(b, s, lk, sides=("east", "west"))
    for z in range(s.z0 + 2, s.z1 - 1, 2):
        for x in (s.x0 + 1, s.x1 - 1):
            desk(lk.wood)(b, x, 1, z, "east" if x == s.x0 + 1 else "west")
            b.set(x, 2, z, "candle", candles="2", lit="true", **WL)
    for z in range(s.z0 + 3, s.z1 - 2, 3):
        b.set(mx, 1, z, "lectern", facing="north", has_book="false", powered="false")
    b.fill(s.x0 + 1, 1, s.z1 - 1, s.x1 - 1, 4, s.z1 - 1, "bookshelf")
    b.chest(mx, 1, s.z1 - 2, "north", TREASURE)
    for z in range(s.z0 + 2, s.z1, 3):
        hang_chain(b, mx, z, s.h - 2, st.light)
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), along="z", fill=stone_, pitch=2)
    b.sign(mx + 1, 2, s.z0 - 1, "north", ["Scriptorium~Silence", "", "", ""], lk.wood)
    return b


@gen("loggia_house", freq="common", n=4, styles=("ochre",))
def loggia_house(rng, st):
    """A Mediterranean house: plastered walls, a low tiled hip roof, and in front an arcaded
    loggia whose top is a balcony off the upper floor, pots along its balustrade."""
    W, D = rng.randint(9, 11), rng.randint(7, 8)
    yard = 4
    b = new("Loggia House", "city", W + 2, yard + D + 1)
    lk = look(rng, st, timbered=False)
    lk.jetty = lk.buttress = False
    s = Shell(1, yard, W, yard + D - 1, 2)
    dx = house_body(b, rng, st, s, lk, [pick(rng, HOUSE_GROUND), pick(rng, HOUSE_UPPER)], kind="hip")
    z0, z1 = s.z0 - 2, s.z0 - 1
    b.clear(s.x0, 1, z0, s.x1, s.h + 2, z1)
    b.fill(s.x0, 0, z0, s.x1, 0, z1, pick(rng, st.found))
    tm = trim_of(lk.found)
    for x in range(s.x0, s.x1 + 1):   # piers and arches
        if (x - s.x0) % 2 == 0:
            b.fill(x, 1, z0, x, s.h - 1, z0, lk.found)
        else:
            stair(b, x, s.h - 1, z0, f"{tm}_stairs", "south", "top")
    for z in (z0, z1):
        b.fill(s.x0, 1, z, s.x0, s.h - 1, z, lk.found)
        b.fill(s.x1, 1, z, s.x1, s.h - 1, z, lk.found)
    b.clear(s.x0, 1, z1, s.x0, 2, z1)
    b.clear(s.x1, 1, z1, s.x1, 2, z1)
    b.fill(s.x0, s.h, z0, s.x1, s.h, z1, lk.found)   # the balcony floor over it
    b.ring(s.x0, z0, s.x1, z1, s.h + 1, s.h + 1, wall_of(lk.found), **WL)
    b.fill(s.x0 + 1, s.h + 1, z1, s.x1 - 1, s.h + 1, z1, "air")
    for x in range(s.x0, s.x1 + 1, 2):
        b.set(x, s.h + 2, z0, potted(rng))
    # a door from the upper floor onto the balcony
    for x in sorted(range(s.x0 + 1, s.x1), key=lambda x: abs(x - dx)):
        if all(b.empty(x, y, s.z0 + 1) for y in (s.h + 1, s.h + 2)) and b.get(x, s.h + 1, s.z0) != "ladder":
            b.set(x, s.h, s.z0, lk.floor)
            b.door(x, s.h + 1, s.z0, "south", lk.wood)
            break
    b.path(dx, 0, dx, z0 - 1)
    b.set(dx, 0, z0, PATH)
    b.set(dx, 0, z1, PATH)
    for x in (s.x0 + 2, s.x1 - 2):
        hanging(b, x, s.h - 1, z1, st)
    b.sign(dx + 1, 2, z1, "north", "[house]", lk.wood) if solid(b, dx + 1, 2, s.z0) else None
    flowers(b, rng, st, yard_cells(b, z0), 0.4)
    return b


@gen("longhouse", freq="average", n=3, styles=("timber",))
def longhouse(rng, st):
    """A northern longhouse: low log walls, a steep roof almost to the ground, the hearth down
    the middle, benches along the walls, crossed boards on the gables."""
    W, D = 9, rng.randint(13, 16)
    b = new("Longhouse", "city", W + 2, D + 3)
    wood = pick(rng, ("spruce", "dark_oak"))
    log = f"{wood}_log"
    lk = look(rng, st, timbered=False)
    lk.infill = lk.found = log
    s = Shell(1, 2, W, D + 1, 1, h=3)
    b.fill(s.x0, 0, s.z0, s.x1, 0, s.z1, f"{wood}_planks")
    b.ring(s.x0, s.z0, s.x1, s.z1, 1, 3, f"{wood}_planks")
    for x in range(s.x0, s.x1 + 1):
        for z in (s.z0, s.z1):
            b.fill(x, 1, z, x, 3, z, log, axis="y")
    for z in range(s.z0, s.z1 + 1):
        for x in (s.x0, s.x1):
            b.fill(x, 1, z, x, 3, z, log, axis="z")
    mx = (s.x0 + s.x1) // 2
    b.door(mx, 1, s.z0, "south", wood)
    b.path(mx, 0, mx, s.z0 - 1)
    for z in range(s.z0 + 3, s.z1 - 1, 4):
        for x in (s.x0, s.x1):
            b.set(x, 2, z, f"{wood}_trapdoor", facing="west" if x == s.x0 else "east", half="bottom", open="true", **TD)
    # the hearth down the middle, benches, beds at the back
    for z in range(s.z0 + 4, s.z1 - 3, 4):
        b.set(mx, 0, z, "cobblestone")
        b.set(mx, 1, z, "campfire", facing="north", lit="true", signal_fire="false", **WL)
    for z in range(s.z0 + 2, s.z1 - 2):
        for x in (s.x0 + 1, s.x1 - 1):
            if z % 3:
                stair(b, x, 1, z, f"{wood}_stairs", "west" if x == s.x0 + 1 else "east")
    b.bed(s.x0 + 1, 1, s.z1 - 2, "south", pick(rng, st.beds))
    b.bed(s.x1 - 1, 1, s.z1 - 2, "south", pick(rng, st.beds))
    b.chest(mx, 1, s.z1 - 1, "north")
    b.barrel(mx - 1, 1, s.z1 - 1, [("bread", rng.randint(2, 8))])
    b.barrel(mx + 1, 1, s.z1 - 1, [])
    m = pick(rng, (mat("spruce"), mat("dark_oak"), mat("cobbled_deepslate")))
    ridge = gable(b, s.x0, s.z0, s.x1, s.z1, 3, m, along="z", fill=f"{wood}_planks", pitch=2)
    for z in range(s.z0 + 2, s.z1 - 1, 4):   # roof beams across, lanterns hung off them
        hang_chain(b, mx - 2, z, 2, st.light) if z % 8 == (s.z0 + 2) % 8 else None
    # crossed boards over each gable's peak
    for z in (s.z0 - 1, s.z1 + 1):   # carved posts over each gable's peak
        if b.get(mx, ridge, z).endswith(("slab", "stairs")):
            b.set(mx, ridge + 1, z, f"{wood}_fence", **WL)
            b.set(mx, ridge + 2, z, f"{wood}_fence", **WL)
    b.set(mx - 1, 2, s.z0 - 1, st.wall_torch, facing="north")
    b.set(mx + 1, 2, s.z0 - 1, st.wall_torch, facing="north")
    return b


# ═════════════════════════════════════════════════════════════════════════
#  Farms (outside the north wall)
# ═════════════════════════════════════════════════════════════════════════

def paddock(b, x0, z0, x1, z1, wood, gate_x=None, post=None):
    """A fence round x0..x1, z0..z1, a gate in the north side."""
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            b.set(x, 1, z, post or f"{wood}_fence", **WL)
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            b.set(x, 1, z, post or f"{wood}_fence", **WL)
    gx = (x0 + x1) // 2 if gate_x is None else gate_x
    gate(b, gx, 1, z0, wood)
    return gx


@gen("cattle_byre", kind="farm", freq="common", n=3)
def cattle_byre(rng, st):
    """A long byre of stalls with a hay loft over them, a muddy yard in front behind a fence."""
    W, D = rng.randint(11, 13), 7
    yard = 6
    b = new("Cattle Byre", "farm", W + 2, yard + D + 1)
    lk = look(rng, st, timbered=st.key != "ochre")
    lk.jetty = lk.buttress = False
    if st.key == "timber":
        lk.infill = pick(rng, ("white_terracotta", "spruce_planks", "calcite"))
    s = Shell(1, yard, W, yard + D - 1, 1, h=5)
    shell(b, s, lk)
    b.fill(s.x0 + 1, 0, s.z0 + 1, s.x1 - 1, 0, s.z1 - 1, "coarse_dirt")
    for x in range(s.x0 + 1, s.x1, 3):   # wide openings onto the yard, a stall behind each
        b.clear(x, 1, s.z0, x + 1, 3, s.z0) if x + 1 < s.x1 else None
        b.fill(x, 0, s.z0, x + 1, 0, s.z0, "coarse_dirt") if x + 1 < s.x1 else None
    for x in range(s.x0 + 3, s.x1, 3):
        fence(b, x, 1, s.z0 + 1, x, 1, s.z1 - 2, lk.wood)
    for x in range(s.x0 + 1, s.x1):   # the manger along the back, the loft over it
        if (x - s.x0) % 3 == 0:
            continue
        b.set(x, 1, s.z1 - 1, "hay_block", axis="x") if x % 2 else b.set(x, 1, s.z1 - 1, "water_cauldron", level="3")
    b.fill(s.x0 + 1, 3, s.z1 - 3, s.x1 - 1, 3, s.z1 - 1, f"{lk.wood}_planks")
    for x in range(s.x0 + 1, s.x1):
        for z in range(s.z1 - 3, s.z1):
            if rng.random() < 0.6:
                b.set(x, 4, z, "hay_block", axis="z")
    windows(b, s, lk, sides=("east", "west"), every=2)
    m = pick(rng, st.roofs)
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along="x", fill=lk.infill)
    for x in range(s.x0 + 2, s.x1, 3):
        hang(b, x, s.z0 + 2, 1, st, height=4)
    # the yard
    gx = paddock(b, 0, 1, W + 1, s.z0, lk.wood)
    b.path(gx, 0, gx, 0)
    for x in range(1, W + 1):
        for z in range(2, s.z0):
            r = rng.random()
            b.set(x, 0, z, "mud" if r < 0.25 else "coarse_dirt" if r < 0.5 else GROUND)
    for x in (2, W - 1):
        b.set(x, 1, 2, "water_cauldron", level="3")
    b.barrel(3, 1, 2, [("wheat", rng.randint(4, 20))])
    return b


@gen("goat_rocks", kind="farm", freq="average", n=3)
def goat_rocks(rng, st):
    """A pile of rocks fenced in for goats, ledges climbing to a little shelter at the top."""
    S = 13
    c = S // 2
    b = new("Goat Rocks", "farm", S, S)
    wood = pick(rng, st.woods)
    rock = {"timber": ("stone", "andesite", "cobblestone", "mossy_cobblestone"),
            "ochre": ("sandstone", "smooth_sandstone", "terracotta", "granite"),
            "ashen": ("tuff", "cobbled_deepslate", "blackstone", "andesite")}[st.key]
    hmap = {}
    for x in range(1, S - 1):
        for z in range(1, S - 1):
            d = math.hypot(x - c, z - c - 1)
            h = max(0, round(5 - d * 1.0 + rng.uniform(-0.8, 0.8)))
            hmap[(x, z)] = h
            for y in range(1, h + 1):
                b.set(x, y, z, pick(rng, rock))
            if h and rng.random() < 0.3:
                b.set(x, h + 1, z, "short_grass" if st.key != "ochre" else "short_dry_grass")
    top = max(hmap.values())
    tx, tz = max(hmap, key=lambda k: (hmap[k], -abs(k[0] - c)))
    # a shelter of slabs on posts over the summit
    for x, z in ((tx - 1, tz - 1), (tx + 1, tz - 1), (tx - 1, tz + 1), (tx + 1, tz + 1)):
        y0 = hmap.get((x, z), 0)
        fence(b, x, y0 + 1, z, x, top + 2, z, wood)
    for x in range(tx - 1, tx + 2):
        for z in range(tz - 1, tz + 2):
            slab(b, x, top + 3, z, f"{wood}_slab")
            if b.empty(x, top + 1, z) and hmap.get((x, z), 0) == top and (x, z) != (tx, tz):
                b.set(x, top + 1, z, "hay_block", axis="y") if rng.random() < 0.3 else None
    hanging(b, tx, top + 2, tz, st)
    paddock(b, 0, 0, S - 1, S - 1, wood)
    b.set(c, 1, 0, f"{wood}_fence_gate", facing="north", in_wall="false", open="false", powered="false")
    b.set(2, 1, 2, "water_cauldron", level="3") if b.empty(2, 1, 2) else None
    return b


@gen("scarecrow_field", kind="farm", freq="common", n=3)
def scarecrow_field(rng, st):
    """A field of tall wheat with scarecrows in it, a path down the middle, crows' perches."""
    S, T = rng.randint(11, 15), rng.randint(11, 14)
    b = new("Scarecrow Field", "farm", S, T)
    crop = pick(rng, st.crops)
    wood = pick(rng, st.woods)
    mx = S // 2
    for x in range(S):
        for z in range(T):
            if x in (0, S - 1) or z in (0, T - 1):
                continue
            if x == mx:
                b.set(x, 0, z, PATH)
            elif (x - 1) % 4 == 3:
                b.set(x, 0, z, "water", level="0")
            else:
                farmland(b, x, z, crop, rng)
    paddock(b, 0, 0, S - 1, T - 1, wood, gate_x=mx)
    heads = ("carved_pumpkin", "carved_pumpkin", "jack_o_lantern")
    for k in range(rng.randint(2, 3)):
        x = pick(rng, [x for x in range(2, S - 2) if x != mx and (x - 1) % 4 != 3])
        z = 3 + k * ((T - 6) // 2)
        b.set(x, 0, z, "farmland", moisture="7")
        b.fill(x, 1, z, x, 2, z, f"{wood}_fence", **WL)
        b.set(x, 3, z, pick(rng, ("hay_block", WOOL, "brown_wool")))
        if b.get(x, 3, z) == "hay_block":
            b.set(x, 3, z, "hay_block", axis="y")
        b.set(x, 4, z, pick(rng, heads), facing="north")
        for dx in (-1, 1):   # arms
            if b.inside(x + dx, z):
                b.set(x + dx, 3, z, f"{wood}_fence", **WL)
                b.set(x + dx, 2, z, "air")
        b.set(x, 5, z, "brown_carpet") if rng.random() < 0.5 else None
    b.set(mx, 1, T - 1, f"{wood}_fence", **WL)
    b.set(mx, 2, T - 1, st.light, hanging="false", **WL)
    return b


@gen("pig_sty", kind="farm", freq="common", n=3)
def pig_sty(rng, st):
    """A low stone sty with a slab roof, a wallow of mud in its pen, troughs."""
    S, T = rng.randint(10, 12), rng.randint(9, 11)
    b = new("Pig Sty", "farm", S, T)
    wood = pick(rng, st.woods)
    stone_ = pick(rng, st.found)
    paddock(b, 0, 0, S - 1, T - 1, wood)
    b.path(S // 2, 0, S // 2, 0)
    for x in range(1, S - 1):
        for z in range(1, T - 1):
            b.set(x, 0, z, "mud" if rng.random() < 0.55 else "coarse_dirt")
    for x in range(2, 5):   # a wallow of water in the mud
        for z in range(2, 4):
            b.set(x, 0, z, "water", level="0") if rng.random() < 0.6 else None
    # the sty at the back: low walls, a doorway, roofed with slabs on a beam
    x0, x1, z0, z1 = S - 6, S - 2, T - 5, T - 2
    b.ring(x0, z0, x1, z1, 1, 2, stone_)
    b.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, "coarse_dirt")
    b.clear(x0 + 2, 1, z0, x0 + 2, 2, z0)
    b.fill(x0, 3, z0, x1, 3, z1, stone_)
    b.fill(x0 - 1, 4, z0 - 1, x1 + 1, 4, z1 + 1, mat_of(stone_).slab, type="bottom", **WL)
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if rng.random() < 0.5:
                b.set(x, 1, z, "yellow_carpet")
    hanging(b, x0 + 2, 2, z1 - 1, st)
    # troughs along the fence
    for x in range(2, 6):
        b.set(x, 1, T - 2, "composter", level=str(rng.randint(1, 7))) if x % 2 else slab(b, x, 1, T - 2, f"{wood}_slab")
    b.set(1, 1, T - 2, "water_cauldron", level="3")
    b.set(S - 1, 2, 0, st.light, hanging="false", **WL)
    return b


@gen("fish_ponds", kind="farm", freq="average", n=3)
def fish_ponds(rng, st):
    """Stew ponds: two or three stone-edged ponds with reeds and lily pads, a sluice and a hut
    for the nets."""
    S, T = 15, rng.randint(12, 14)
    b = new("Fish Ponds", "farm", S, T)
    wood = pick(rng, st.woods)
    stone_ = pick(rng, st.found)
    ponds = [(1, 1, 6, 5), (8, 1, 13, 5), (1, 7, 6, T - 2)]
    for x0, z0, x1, z1 in ponds:
        b.fill(x0, -3, z0, x1, 0, z1, stone_)
        b.fill(x0 + 1, -2, z0 + 1, x1 - 1, 0, z1 - 1, "water", level="0")
        b.fill(x0 + 1, -3, z0 + 1, x1 - 1, -3, z1 - 1, "clay")
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                r = rng.random()
                if r < 0.15:
                    b.set(x, 1, z, "lily_pad")
                elif r < 0.25:
                    b.set(x, -2, z, "seagrass")
        for x, z in ((x0 + 1, z0 + 1), (x1 - 1, z1 - 1)):
            b.set(x, 0, z, GROUND)
            b.fill(x, 1, z, x, 2, z, "sugar_cane", age="0")
    # the sluice between the top ponds
    b.set(7, 0, 3, "water", level="0")
    b.set(7, -1, 3, stone_)
    # the net hut
    s = Shell(8, 7, 13, T - 2, 1)
    lk = look(rng, st)
    lk.jetty = lk.buttress = False
    shell(b, s, lk)
    b.door(10, 1, s.z0, "south", lk.wood)
    b.set(10, 0, s.z0, lk.floor)
    b.path(10, 0, 10, 6)
    windows(b, s, lk, sides=("east", "south"), every=2)
    b.barrel(s.x0 + 1, 1, s.z1 - 1, [("cod", rng.randint(2, 9))])
    b.barrel(s.x1 - 1, 1, s.z1 - 1, [("salmon", rng.randint(1, 5))])
    b.set(s.x0 + 1, 1, s.z0 + 1, "cauldron")
    b.set(s.x1 - 1, 1, s.z0 + 1, "crafting_table")
    hang(b, (s.x0 + s.x1) // 2, (s.z0 + s.z1) // 2, 1, st)
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), along="z", fill=lk.infill)
    b.path(7, 0, 7, 0)
    b.path(7, 6, 13, 6)
    for z in range(0, 7):
        if b.get(7, 0, z) == GROUND:
            b.set(7, 0, z, PATH)
    return b


@gen("hop_garden", kind="farm", freq="average", n=3, styles=("timber", "ashen"))
def hop_garden(rng, st):
    """Rows of tall poles strung with wires, the hop bines climbing them, an oast at the end."""
    S, T = 15, rng.randint(12, 14)
    b = new("Hop Garden", "farm", S, T)
    wood = pick(rng, st.woods)
    vine = "vine"
    for x in range(1, 9):
        for z in range(1, T - 1):
            b.set(x, 0, z, "coarse_dirt" if (x % 2) else GROUND)
    for x in range(1, 9, 2):   # poles every other row, wires along the tops
        for z in range(1, T - 1, 3):
            fence(b, x, 1, z, x, 5, z, wood)
            for y in range(1, 5):
                b.set(x, y, z + 1, "oak_leaves" if st.key == "timber" else "azalea_leaves", **LEAF) if y < 4 and rng.random() < 0.8 else None
        for z in range(1, T - 1):
            if b.empty(x, 5, z):
                b.set(x, 5, z, f"{wood}_fence", **WL)
    # the oast: a round kiln with a cone cowl, the drying barn beside it
    c, cz, r = 11, T - 5, 2
    stone_ = "bricks" if st.key == "timber" else "deepslate_bricks"
    for dx, dz in disc(r):
        b.set(c + dx, 0, cz + dz, stone_)
    for dx, dz in circle(r):
        b.fill(c + dx, 1, cz + dz, c + dx, 5, cz + dz, stone_)
    b.set(c, 1, cz, "campfire", facing="north", lit="true", signal_fire="false", **WL)
    for dx, dz in disc(r - 1):
        b.set(c + dx, 4, cz + dz, f"{wood}_slab", type="top", **WL) if (dx, dz) != (0, 0) else None
    b.set(c, 4, cz, f"{wood}_trapdoor", facing="north", half="top", open="false", **TD)
    top = cone(b, c, cz, 6, r + 1, mat("dark_oak") if st.key != "ashen" else mat("deepslate_tile"))
    b.set(c, top + 1, cz, "white_wool" if st.key == "timber" else "white_terracotta")
    b.set(c, top + 2, cz, f"{wood}_fence", **WL)
    b.door(c, 1, cz - r, "south", wood)
    b.path(c, 0, c, 0)
    b.set(c, 0, cz - r, stone_)
    hang(b, c + 1, cz, 1, st, height=2)
    b.barrel(c - 1, 1, cz + 1, [("wheat", rng.randint(4, 16))])
    for x in range(9, 15):
        if x != c:
            b.set(x, 1, 1, f"{wood}_fence", **WL)
    b.set(14, 2, 1, st.light, hanging="false", **WL)
    return b


@gen("lavender_field", kind="farm", freq="common", n=3, styles=("ochre", "timber"))
def lavender_field(rng, st):
    """Rounded rows of lavender (allium and lilac) between paths of pale earth, a still for the
    oil under a pergola."""
    S, T = rng.randint(11, 15), rng.randint(11, 14)
    b = new("Lavender Field", "farm", S, T)
    wood = pick(rng, st.woods)
    for x in range(S):
        for z in range(T):
            b.set(x, 0, z, "coarse_dirt" if x % 3 == 0 else GROUND)
            if x % 3 and 0 < z < T - 3:
                b.set(x, 1, z, pick(rng, ("allium", "allium", "allium", "cornflower")))
    for x in range(1, S, 6):
        if b.inside(x + 1, 1):
            b.set(x, 1, 1, "lilac", half="lower")
            b.set(x, 2, 1, "lilac", half="upper")
    # the still under a pergola at the back
    z0 = T - 3
    for x in range(1, S - 1):
        b.set(x, 1, z0, "air")
        b.set(x, 1, z0 + 1, "air")
        b.set(x, 0, z0, PATH)
        b.set(x, 0, z0 + 1, PATH)
    for x in (1, 5):
        for z in (z0, z0 + 2):
            fence(b, x, 1, z, x, 2, z, wood)
    for x in range(1, 6):
        for z in range(z0, z0 + 3):
            slab(b, x, 3, z, f"{wood}_slab")
    b.set(3, 1, z0 + 2, "brewing_stand", has_bottle_0="true", has_bottle_1="false", has_bottle_2="true")
    b.set(2, 1, z0 + 2, "cauldron")
    b.barrel(4, 1, z0 + 2, [("glass_bottle", rng.randint(1, 6))])
    hanging(b, 3, 2, z0 + 1, st)
    b.set(3, 1, z0 + 1, "air")
    return b


@gen("flax_field", kind="farm", freq="average", n=3)
def flax_field(rng, st):
    """Blue-flowering flax in beds, a retting pond to soak it, sheaves drying on a rack."""
    S, T = rng.randint(12, 14), rng.randint(11, 13)
    b = new("Flax Field", "farm", S, T)
    wood = pick(rng, st.woods)
    for x in range(1, S - 1):
        for z in range(1, T - 5):
            if (x - 1) % 4 == 3:
                b.set(x, 0, z, "water", level="0")
            else:
                b.set(x, 0, z, "farmland", moisture="7")
                b.set(x, 1, z, "cornflower" if rng.random() < 0.6 else "cornflower")
                if rng.random() < 0.25:
                    b.set(x, 1, z, "short_grass")
    paddock(b, 0, 0, S - 1, T - 5, wood)
    # the retting pond, sheaves in it
    b.fill(1, -1, T - 4, 6, 0, T - 2, "mud")
    b.fill(2, 0, T - 3, 5, 0, T - 3, "water", level="0")
    b.set(3, 0, T - 3, "water", level="0")
    # the drying rack
    for x in (8, S - 2):
        fence(b, x, 1, T - 3, x, 2, T - 3, wood)
    for x in range(8, S - 1):
        b.set(x, 3, T - 3, f"{wood}_fence", **WL)
        b.set(x, 1, T - 3, "hay_block", axis="y") if 8 < x < S - 2 else None
    b.set(8, 4, T - 3, st.light, hanging="false", **WL)
    return b


@gen("cider_press", kind="farm", freq="average", n=3, styles=("timber",))
def cider_press(rng, st):
    """A press house beside a little orchard: the press and its screw, vats, casks and apples."""
    W, D = 13, 13
    b = new("Cider Press", "farm", W, D)
    lk = look(rng, st, timbered=True)
    lk.jetty = lk.buttress = False
    s = Shell(1, 2, 7, 8, 1, h=5)
    shell(b, s, lk)
    dx = front_door(b, s, lk, 4)
    windows(b, s, lk, sides=("east", "west", "south"), every=2)
    # the press: a block on posts with a screw (a chain) through a beam
    b.set(3, 1, 6, "smooth_stone")
    b.set(4, 1, 6, "smooth_stone")
    b.set(3, 2, 6, "piston", facing="down", extended="false")
    b.set(4, 2, 6, "piston", facing="down", extended="false")
    fence(b, 2, 1, 6, 2, 3, 6, lk.wood)
    fence(b, 5, 1, 6, 5, 3, 6, lk.wood)
    for x in range(2, 6):
        b.set(x, 4, 6, lk.log, axis="x")
    b.set(3, 3, 6, "iron_chain", axis="y", **WL)
    b.set(4, 3, 6, "iron_chain", axis="y", **WL)
    b.set(2, 1, 7, "cauldron")
    b.barrel(5, 1, 7, [("apple", rng.randint(6, 24))])
    b.barrel(6, 1, 7, [("honey_bottle", rng.randint(1, 3))])
    b.barrel(6, 2, 7, [])
    b.barrel(6, 1, 3, [("apple", rng.randint(3, 12))])
    b.set(2, 1, 3, "crafting_table")
    hang(b, 4, 4, 1, st, height=4)
    roof(b, rng, st, s, lk, kind="gable", along="z")
    # the orchard beside it
    for x, z in ((10, 3), (10, 9)):
        tree(b, rng, x, z, "oak", size=3)
    for x in range(9, 12):
        for z in (6,):
            b.barrel(x, 1, z, [("apple", rng.randint(2, 8))]) if x != 10 else b.set(x, 1, z, "hay_block", axis="y")
    return b


@gen("smokehouse", kind="farm", freq="average", n=3)
def smokehouse(rng, st):
    """A tall narrow smokehouse: the fire pit under racks of hams, smoke out of louvres in the roof,
    a salting shed beside it."""
    b = new("Smokehouse", "farm", 11, 10)
    lk = look(rng, st, timbered=False)
    lk.jetty = lk.buttress = False
    stone_ = pick(rng, st.found)
    s = Shell(1, 2, 5, 6, 1, h=6)
    b.fill(s.x0, 0, s.z0, s.x1, 0, s.z1, stone_)
    b.ring(s.x0, s.z0, s.x1, s.z1, 1, s.top, stone_)
    b.ring(s.x0, s.z0, s.x1, s.z1, 3, s.top, f"{lk.wood}_planks")
    b.door(3, 1, s.z0, "south", lk.wood)
    b.path(3, 0, 3, s.z0 - 1)
    b.set(3, 1, 4, "campfire", facing="north", lit="true", signal_fire="false", **WL)
    b.set(3, 0, 4, "cobblestone")
    for z in (3, 5):   # racks of hams: chains with brown/pink wool
        for x in (2, 4):
            b.set(x, 5, z, "iron_chain", axis="y", **WL)
            b.set(x, 4, z, "brown_terracotta" if (x + z) % 2 else "pink_terracotta")
    for x in range(2, 5):
        b.set(x, 6, 3, lk.log, axis="x")
        b.set(x, 6, 5, lk.log, axis="x")
    b.fill(s.x0, s.top, s.z0, s.x1, s.top, s.z1, f"{lk.wood}_planks")
    b.fill(s.x0 + 1, s.top, s.z0 + 1, s.x1 - 1, s.top, s.z1 - 1, "air")
    for x in range(2, 5):
        for z in (3, 5):
            b.set(x, s.top, z, lk.log, axis="x")
    m = pick(rng, st.roofs)
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along="z", fill=f"{lk.wood}_planks")
    top = highest(b, 3, 4)
    # a smoke louvre on the ridge: a little roof on posts
    b.set(3, top, 4, "air")
    b.set(3, top + 1, 4, "air")
    for z in (3, 5):
        b.set(3, top + 1, z, f"{lk.wood}_fence", **WL)
    for z in range(2, 7):
        slab(b, 3, top + 2, z, m.slab)
    for x, f in ((2, "west"), (4, "east")):   # slatted sides
        if b.empty(x, top, 4):
            trapdoor(b, x, top, 4, lk.wood, f, "bottom", open_=True)
        if b.empty(x, top + 1, 4):
            trapdoor(b, x, top + 1, 4, lk.wood, f, "bottom", open_=True)
    # the salting shed
    t = Shell(6, 3, 9, 6, 1)
    b.fill(t.x0, 0, t.z0, t.x1, 0, t.z1, lk.floor)
    for x, z in ((t.x0, t.z0), (t.x1, t.z0), (t.x0, t.z1), (t.x1, t.z1)):
        b.fill(x, 1, z, x, 3, z, lk.log, axis="y")
    b.fill(t.x0 + 1, 1, t.z1, t.x1 - 1, 3, t.z1, f"{lk.wood}_planks")
    b.fill(t.x1, 1, t.z0 + 1, t.x1, 3, t.z1 - 1, f"{lk.wood}_planks")
    b.fill(t.x0, 4, t.z0, t.x1, 4, t.z1, f"{lk.wood}_planks")
    gable(b, t.x0, t.z0, t.x1, t.z1, 4, m, along="x", fill=f"{lk.wood}_planks")
    b.barrel(t.x1 - 1, 1, t.z1 - 1, [("porkchop", rng.randint(2, 10))])
    b.barrel(t.x1 - 1, 1, t.z1 - 2, [("beef", rng.randint(2, 10))])
    b.set(t.x0 + 1, 1, t.z1 - 1, "smoker", facing="north", lit="false")
    b.set(t.x0 + 2, 1, t.z1 - 1, "white_concrete_powder")
    hanging(b, t.x0 + 1, 3, t.z0 + 1, st)
    return b


@gen("tree_nursery", kind="farm", freq="average", n=3)
def tree_nursery(rng, st):
    """Rows of saplings and young trees in pots and beds, a cold frame of glass, a potting bench."""
    S, T = rng.randint(12, 14), rng.randint(11, 13)
    b = new("Tree Nursery", "farm", S, T)
    wood = pick(rng, st.woods)
    saps = {"timber": ("oak_sapling", "birch_sapling", "spruce_sapling"),
            "ochre": ("acacia_sapling", "jungle_sapling", "oak_sapling"),
            "ashen": ("dark_oak_sapling", "spruce_sapling", "pale_oak_sapling")}[st.key]
    for x in range(1, S - 1):
        for z in range(1, T - 4):
            if x % 2 == 0:
                b.set(x, 0, z, PATH)
            else:
                b.set(x, 0, z, "dirt")
                if z % 2:
                    b.set(x, 1, z, pick(rng, saps), stage="0")
    for x, z in ((1, T - 3), (S - 2, T - 3)):   # two young trees
        tree(b, rng, x + (1 if x == 1 else -1), z, pick(rng, st.trees), size=3)
    # the cold frame: a glass lid on a plank box
    for x in range(4, 9):
        b.set(x, 1, T - 3, f"{wood}_planks") if x in (4, 8) else b.set(x, 0, T - 3, "dirt")
        if x not in (4, 8):
            b.set(x, 1, T - 3, pick(rng, saps), stage="1")
            b.set(x, 2, T - 3, "glass")
            b.set(x, 2, T - 2, "glass")
            b.set(x, 1, T - 2, f"{wood}_planks")
    b.set(4, 2, T - 3, "glass")
    b.set(8, 2, T - 3, "glass")
    # the potting bench, pots on it
    for x in range(4, 9):
        slab(b, x, 1, T - 1, f"{wood}_slab", "top")
        b.set(x, 2, T - 1, potted(rng) if x % 2 else "flower_pot")
    for x in range(S):
        if x not in (S // 2,):
            b.set(x, 1, 0, f"{wood}_fence", **WL)
    b.path(S // 2, 0, S // 2, 0)
    b.set(S - 1, 2, 0, st.light, hanging="false", **WL)
    b.set(0, 2, 0, st.light, hanging="false", **WL)
    return b


# ═════════════════════════════════════════════════════════════════════════
#  Mineshaft sections (7 x 5 x 7, centred on a corridor cell)
# ═════════════════════════════════════════════════════════════════════════
#
# Patterns: three rows north to south of the cells round the section, 1 a corridor, 0 rock,
# ? either (each row's string reads east to west; the ones here are symmetric east-west).

SECTION_LEVELS.update({  # frequency1..7
    "underground_lake": ("rare", "average", "common", "common", "average", "average", "rare"),
    "mushroom_grotto": ("very rare", "rare", "average", "common", "common", "average", "average"),
    "lost_office": ("exclude", "exclude", "exclude", "exclude", "exclude", "very rare", "rare"),
    "collapsed_elevator": ("rare", "average", "average", "average", "rare", "rare", "rare"),
    "geode": ("exclude", "very rare", "rare", "average", "average", "common", "common"),
    "lava_forge": ("exclude", "exclude", "very rare", "rare", "average", "average", "common"),
    "flooded_junction": ("rare", "average", "common", "common", "average", "average", "rare"),
    "foreman_office": ("common", "common", "average", "average", "rare", "very rare", "exclude"),
    "crate_store": ("common", "common", "common", "average", "average", "rare", "rare"),
    "timbered_tunnel": ("common", "common", "common", "common", "common", "average", "average"),
    "dead_end_cart": ("average", "average", "average", "common", "common", "common", "average"),
})


def close_side(b, side, block="stone"):
    """Walls up a section's opening on one side."""
    if side == "north":
        b.fill(2, 1, 0, 4, 3, 0, block)
    elif side == "south":
        b.fill(2, 1, 6, 4, 3, 6, block)
    elif side == "west":
        b.fill(0, 1, 2, 0, 3, 4, block)
    else:
        b.fill(6, 1, 2, 6, 3, 4, block)


def props(b, wood, corners=((1, 1), (5, 1), (1, 5), (5, 5))):
    for x, z in corners:
        b.fill(x, 1, z, x, 3, z, f"{wood}_log", axis="y")


@gen("underground_lake", kind="mineshaft_section", n=1)
def underground_lake(rng, st):
    """A still pool under a low vault, a dock of planks with a moored punt."""
    b = section("Underground Lake", rng, st)
    wood = pick(rng, st.woods)
    b.fill(1, -2, 1, 5, 0, 5, "stone")
    b.fill(1, -1, 2, 5, 0, 5, "water", level="0")
    b.fill(1, -2, 2, 5, -2, 5, "clay")
    for x in range(1, 6):   # the dock along the north side
        b.set(x, 0, 1, f"{wood}_planks")
    b.set(3, 0, 2, f"{wood}_planks")
    fence(b, 1, 1, 1, 1, 1, 1, wood)
    fence(b, 5, 1, 1, 5, 1, 1, wood)
    light(b, 1, 2, 1, st)
    light(b, 5, 2, 1, st)
    # a punt: slabs floating on the water, tied to the dock
    for z in (3, 4):
        b.set(2, 0, z, f"{wood}_slab", type="top", waterlogged="true")
        b.set(4, 0, z, f"{wood}_slab", type="top", waterlogged="true")
    b.set(3, 0, 4, f"{wood}_slab", type="top", waterlogged="true")
    b.barrel(3, 1, 4, [("cod", rng.randint(1, 4))])
    for x, z in ((1, 5), (5, 5), (5, 3)):
        b.set(x, 3, z, "pointed_dripstone", vertical_direction="down", thickness="tip", **WL)
        b.set(x, 4, z, "dripstone_block")
    b.set(3, 4, 4, "glow_lichen", down="false", up="true", north="false", south="false", east="false", west="false", **WL)
    b.set(3, 4, 4, "stone")
    b.set(3, 3, 4, "glow_lichen", up="true", down="false", north="false", south="false", east="false", west="false", **WL)
    return b


@gen("mushroom_grotto", kind="mineshaft_section", n=1)
def mushroom_grotto(rng, st):
    """A damp cave grown over with moss and mushrooms, a huge cap glowing in the middle."""
    b = section("Mushroom Grotto", rng, st)
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if n == "stone" and rng.random() < 0.4:
            b.set(x, y, z, pick(rng, ("mossy_cobblestone", "moss_block", "tuff")))
    b.fill(1, 0, 1, 5, 0, 5, "mycelium")
    for x in range(1, 6):
        for z in range(1, 6):
            if (x, z) == (3, 3):
                continue
            r = rng.random()
            if r < 0.25:
                b.set(x, 1, z, pick(rng, ("brown_mushroom", "red_mushroom")))
            elif r < 0.35:
                b.set(x, 1, z, "moss_carpet")
    b.fill(3, 1, 3, 3, 2, 3, "mushroom_stem", up="false", down="false", north="true", south="true", east="true", west="true")
    for x in range(2, 5):
        for z in range(2, 5):
            b.set(x, 3, z, "red_mushroom_block" if (x + z) % 2 else "brown_mushroom_block",
                  up="true", down="false", north="true", south="true", east="true", west="true")
    b.set(3, 3, 3, "shroomlight")
    b.set(1, 3, 1, "shroomlight")
    b.set(5, 3, 5, "shroomlight")
    return b


@gen("lost_office", kind="mineshaft_section", n=1)
def lost_office(rng, st):
    """Deep under the mine, a door into an office that has been waiting: yellowed wallpaper, damp
    carpet, a buzzing light, a desk with the chair pushed back. Only one way in."""
    b = section("Lost Office", rng, st, pattern="?0? 010 ?1?", unique=True)
    b.fill(0, 0, 0, 6, 4, 6, "stone")
    b.fill(1, 0, 1, 5, 0, 5, "yellow_wool")
    b.ring(1, 1, 5, 5, 1, 3, "smooth_sandstone")
    b.fill(2, 1, 2, 4, 3, 4, "air")
    b.fill(2, 4, 2, 4, 4, 4, "smooth_sandstone")
    b.set(3, 4, 3, "ochre_froglight", axis="y")
    b.fill(2, 0, 2, 4, 0, 4, "yellow_wool")
    b.fill(2, 1, 2, 4, 1, 4, "yellow_carpet")
    # in from the south corridor through a door in the panelled wall
    b.clear(3, 1, 6, 3, 2, 6)
    b.clear(2, 1, 6, 2, 3, 6)
    b.clear(4, 1, 6, 4, 3, 6)
    b.set(3, 0, 5, "smooth_sandstone")
    b.door(3, 1, 5, "north", material="birch_door")
    b.set(3, 1, 5, "birch_door", facing="north", half="lower", hinge="left", open="true", powered="false")
    b.set(3, 2, 5, "birch_door", facing="north", half="upper", hinge="left", open="true", powered="false")
    b.set(3, 1, 4, "air")
    # the desk, the pushed-back chair, a lamp, the files
    slab(b, 2, 1, 2, "birch_slab", "top")
    slab(b, 3, 1, 2, "birch_slab", "top")
    b.container(4, 1, 2, "chest", [("paper", 4)], facing="south", type="single", waterlogged="false")
    stair(b, 2, 1, 3, "birch_stairs", "east")
    b.set(3, 2, 2, "candle", candles="1", lit="false", **WL)
    b.set(2, 2, 2, "flower_pot")
    b.container(4, 1, 4, "barrel", [("paper", 12)], facing="up", open="false")
    b.sign(2, 2, 4, "north", ["Please wait~to be~called", "", "", ""], "birch")
    b.set(2, 2, 5, "smooth_sandstone")
    return b


@gen("collapsed_elevator", kind="mineshaft_section", n=1)
def collapsed_elevator(rng, st):
    """A shaft from the level above, its cage fallen down it and wrecked, the cable in coils."""
    b = section("Collapsed Elevator", rng, st, pattern="?1? 010 ?1?")
    wood = pick(rng, st.woods)
    close_side(b, "east")
    close_side(b, "west")
    b.fill(2, 4, 2, 4, 4, 4, "air")   # the shaft going up (into the rock above)
    for x, z in ((2, 2), (4, 2), (2, 4), (4, 4)):
        b.fill(x, 1, z, x, 4, z, f"{wood}_log", axis="y")
    b.set(3, 4, 3, "air")
    # the wrecked cage: a tilted floor, bars, a broken lamp
    for x in range(2, 5):
        b.set(x, 1, 3, f"{wood}_planks")
        stair(b, x, 1, 2, f"{wood}_stairs", "north")
    b.set(2, 2, 3, "iron_bars", **WL)
    b.set(4, 2, 4, "iron_bars", **WL)
    for x, z in ((3, 4), (4, 3)):
        b.set(x, 1, z, "iron_chain", axis="x", **WL)
    b.set(3, 2, 3, "iron_chain", axis="y", **WL)
    b.set(3, 3, 3, "iron_chain", axis="y", **WL)
    b.set(3, 4, 3, "iron_chain", axis="y", **WL)
    b.set(1, 1, 5, "gravel")
    b.set(5, 1, 1, "gravel")
    b.set(1, 1, 1, "cobweb")
    wall_torch(b, 1, 2, 3, st, "east")
    wall_torch(b, 5, 2, 3, st, "west")
    b.sign(5, 2, 2, "west", ["Lift out~of order", "", "", ""], wood)
    return b


@gen("geode", kind="mineshaft_section", n=1)
def geode(rng, st):
    """The miners broke into a geode: calcite and amethyst, crystals growing off every face."""
    b = section("Geode", rng, st)
    for x in range(0, 7):
        for y in range(0, 5):
            for z in range(0, 7):
                if b.get(x, y, z) in ("stone", "gravel"):
                    d = abs(x - 3) + abs(z - 3) + abs(y - 2)
                    b.set(x, y, z, "smooth_basalt" if d >= 6 else "calcite" if d >= 5 else "amethyst_block")
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if n != "amethyst_block" or rng.random() > 0.4:
            continue
        for f, (dx, dy, dz) in (("up", (0, 1, 0)), ("down", (0, -1, 0)), ("north", (0, 0, -1)), ("south", (0, 0, 1)),
                                ("east", (1, 0, 0)), ("west", (-1, 0, 0))):
            c = (x + dx, y + dy, z + dz)
            if 1 <= c[0] <= 5 and 1 <= c[2] <= 5 and b.empty(*c) and c[1] >= 1 and abs(c[0] - 3) + abs(c[2] - 3) > 1:
                b.set(*c, pick(rng, ("amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud")), facing=f, **WL)
                break
    b.set(3, 4, 3, "amethyst_block")
    b.set(3, 3, 3, "amethyst_cluster", facing="down", **WL)
    return b


@gen("lava_forge", kind="mineshaft_section", n=1)
def lava_forge(rng, st):
    """A deep forge on a lava pool: the anvil, quenching water, bellows and a stack of ingots."""
    b = section("Lava Forge", rng, st)
    stone_ = "polished_blackstone_bricks"
    b.fill(1, 0, 1, 5, 0, 5, "blackstone")
    b.fill(1, -1, 4, 3, 0, 5, stone_)
    b.fill(1, 0, 4, 2, 0, 5, "lava", level="0")
    b.fill(1, 1, 5, 3, 1, 5, stone_)
    b.set(1, 1, 4, "iron_bars", **WL)
    b.set(2, 1, 4, "iron_bars", **WL)
    b.set(3, 1, 4, stone_)
    b.set(3, 2, 5, "blast_furnace", facing="north", lit="true")
    b.set(2, 2, 5, stone_)
    b.set(1, 2, 5, stone_)
    b.set(3, 1, 2, "anvil", facing="east")
    b.set(5, 1, 3, "water_cauldron", level="3")
    b.set(5, 1, 4, "smithing_table")
    b.container(5, 1, 5, "barrel", [("iron_ingot", rng.randint(3, 12))], facing="up", open="false")
    b.set(5, 1, 1, "grindstone", face="floor", facing="north")
    b.set(1, 1, 1, "iron_block")
    b.set(1, 2, 1, "iron_chain", axis="y", **WL)
    b.set(1, 3, 1, "iron_chain", axis="y", **WL)
    b.set(1, 1, 1, "iron_block")
    b.set(4, 4, 3, "magma_block")
    return b


@gen("flooded_junction", kind="mineshaft_section", n=1)
def flooded_junction(rng, st):
    """A crossing of four tunnels gone under shallow water, the rails running on through it."""
    b = section("Flooded Junction", rng, st, pattern="?1? 111 ?1?")
    wood = pick(rng, st.woods)
    props(b, wood)
    b.fill(1, 4, 1, 5, 4, 5, f"{wood}_planks")
    b.fill(1, -1, 1, 5, -1, 5, "clay")
    b.fill(1, 0, 1, 5, 0, 5, "water", level="0")
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
        b.set(x, 0, z, "stone")
    for x in range(0, 7):
        b.set(x, 0, 3, "gravel")
        b.set(x, 1, 3, "rail", shape="east_west", **WL)
    for z in range(0, 7):
        if z != 3:
            b.set(3, 0, z, "gravel")
            b.set(3, 1, z, "rail", shape="north_south", **WL)
    hanging(b, 3, 3, 3, st)
    b.set(2, 1, 2, "lily_pad")
    b.set(4, 1, 4, "lily_pad")
    return b


@gen("foreman_office", kind="mineshaft_section", n=1)
def foreman_office(rng, st):
    """A boarded office off a tunnel: a desk with the shift book, the pay chest, a map of the mine."""
    b = section("Foreman Office", rng, st, pattern="?1? 010 ?1?")
    wood = pick(rng, st.woods)
    close_side(b, "east")
    close_side(b, "west")
    b.fill(1, 0, 1, 5, 0, 5, f"{wood}_planks")
    b.fill(1, 1, 1, 1, 3, 5, f"{wood}_planks")
    b.fill(5, 1, 1, 5, 3, 5, f"{wood}_planks")
    b.fill(1, 4, 1, 5, 4, 5, f"{wood}_planks")
    props(b, wood)
    b.set(2, 1, 2, "cartography_table")
    b.set(2, 1, 3, f"{wood}_slab", type="top", **WL)
    b.set(2, 2, 3, "candle", candles="2", lit="true", **WL)
    b.set(2, 1, 4, "lectern", facing="east", has_book="false", powered="false")
    b.chest(4, 1, 2, "west", TREASURE)
    b.container(4, 1, 4, "barrel", [("torch", 16)], facing="up", open="false")
    b.sign(4, 2, 3, "west", ["Shift~reports~due~yesterday", "", "", ""], wood)
    b.set(5, 2, 3, f"{wood}_planks")
    hanging(b, 3, 3, 3, st)
    return b


@gen("crate_store", kind="mineshaft_section", n=1)
def crate_store(rng, st):
    """A store cut beside the tunnel, stacked high with crates and sacks."""
    b = section("Crate Store", rng, st)
    wood = pick(rng, st.woods)
    props(b, wood)
    b.fill(1, 4, 1, 5, 4, 5, f"{wood}_planks")
    for x in range(1, 6):
        for z in range(1, 6):
            if abs(x - 3) <= 0 or abs(z - 3) <= 0 or (x, z) in ((1, 1), (5, 1), (1, 5), (5, 5)):
                continue
            for y in range(1, rng.randint(1, 3) + 1):
                if rng.random() < 0.6:
                    b.barrel(x, y, z, [(pick(rng, ("coal", "torch", "rail", "bread", "iron_ingot")), rng.randint(2, 16))]
                             if y == 1 and rng.random() < 0.4 else [], facing=pick(rng, ("up", "east", "north")))
                else:
                    b.set(x, y, z, pick(rng, ("hay_block", "dried_kelp_block", f"{wood}_planks")))
                    if b.get(x, y, z) == "hay_block":
                        b.set(x, y, z, "hay_block", axis="y")
                if b.empty(x, y - 1, z) and y > 1:
                    b.set(x, y, z, "air")
    hanging(b, 3, 3, 3, st)
    return b


@gen("timbered_tunnel", kind="mineshaft_section", n=1)
def timbered_tunnel(rng, st):
    """A straight run of tunnel shored up with timber sets, a lamp and a tool rack."""
    b = section("Timbered Tunnel", rng, st, pattern="?1? 010 ?1?")
    wood = pick(rng, st.woods)
    close_side(b, "east")
    close_side(b, "west")
    b.fill(1, 1, 1, 1, 3, 5, "stone")
    b.fill(5, 1, 1, 5, 3, 5, "stone")
    for z in (1, 3, 5):
        b.fill(2, 1, z, 2, 3, z, f"{wood}_log", axis="y")
        b.fill(4, 1, z, 4, 3, z, f"{wood}_log", axis="y")
        b.fill(2, 4, z, 4, 4, z, f"{wood}_planks")
    for z in range(0, 7):
        b.set(3, 1, z, "rail", shape="north_south", **WL)
    hanging(b, 3, 3, 3, st)
    b.set(4, 1, 2, "air")
    b.set(4, 1, 4, "air")
    return b


@gen("dead_end_cart", kind="mineshaft_section", n=1)
def dead_end_cart(rng, st):
    """The end of a line: buffers of logs and hay, a tipped cart of ore, the face where they stopped."""
    b = section("Dead End Cart", rng, st, pattern="?0? 010 ?1?")
    wood = pick(rng, st.woods)
    for side in ("north", "east", "west"):
        close_side(b, side)
    for z in range(2, 7):
        b.set(3, 1, z, "rail", shape="north_south", **WL)
    b.set(3, 1, 1, "hay_block", axis="y")
    b.set(2, 1, 1, f"{wood}_log", axis="x")
    b.set(4, 1, 1, f"{wood}_log", axis="x")
    ores = ("coal_ore", "iron_ore", "copper_ore", "gold_ore")
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if n == "stone" and z <= 1 and rng.random() < 0.3:
            b.set(x, y, z, "deepslate_" + pick(rng, ores) if y < 2 else pick(rng, ores))
    for x, z in ((1, 2), (5, 3), (2, 4)):
        b.set(x, 1, z, pick(rng, ("raw_iron_block", "coal_block", "gravel", "cobblestone")))
    b.container(1, 1, 5, "barrel", [("raw_iron", rng.randint(2, 9))], facing="up", open="false")
    b.set(5, 1, 5, "grindstone", face="floor", facing="north")
    wall_torch(b, 1, 2, 3, st, "east")
    b.sign(5, 2, 2, "west", ["End of~the line", "", "", ""], wood)
    return b


# ═════════════════════════════════════════════════════════════════════════
#  Mineshaft entrances (7 x 7; the city's ladder at (3, 3), its stone column at (3, 4))
# ═════════════════════════════════════════════════════════════════════════

def keep_shaft(b, top=3):
    """The ladder and its column come up through (3, 3) and (3, 4): clear above the ground."""
    for y in range(1, top + 1):
        for z in (3, 4):
            b.set(3, y, z, "air")


@gen("lift_house", kind="mineshaft_entrance", freq="common", n=3)
def lift_house(rng, st):
    """A winding house: a drum on a frame over the shaft, its cable going down beside the ladder,
    the winch lever, under a steep gabled roof."""
    b = new("Lift House", "mineshaft_entrance", 7, 7)
    lk = look(rng, st)
    lk.jetty = lk.buttress = False
    s = Shell(1, 1, 5, 5, 1, h=5)
    shell(b, s, lk)
    b.fill(2, 0, 3, 4, 0, 4, "gravel")
    front_door(b, s, lk, 3, path=False)
    b.path(3, 0, 3, 0)
    windows(b, s, lk, sides=("east", "west"), every=2)
    for x in (2, 4):   # the drum's bearings on the back wall, the cable down beside the ladder
        b.set(x, 4, 4, lk.log, axis="x")
    b.set(3, 4, 4, f"stripped_{lk.wood}_log" if lk.wood != "pale_oak" else "pale_oak_log", axis="x")
    b.fill(2, 1, 4, 2, 3, 4, "iron_chain", axis="y", **WL)
    b.set(4, 1, 4, "lever", face="floor", facing="north", powered="false")
    b.container(2, 1, 2, "barrel", [("rail", rng.randint(4, 16))], facing="up", open="false")
    stair(b, 4, 1, 2, f"{lk.wood}_stairs", "east")
    hang(b, 4, 3, 1, st, height=4)
    roof(b, rng, st, s, lk, kind="gable", along="z", pitch=2 if st.key != "ochre" else 1)
    b.sign(4, 2, 0, "north", ["2[lift]", "", "", ""], lk.wood)
    keep_shaft(b)
    b.set(3, 4, 4, f"stripped_{lk.wood}_log" if lk.wood != "pale_oak" else "pale_oak_log", axis="x")
    return b


@gen("stone_portal", kind="mineshaft_entrance", freq="average", n=3)
def stone_portal(rng, st):
    """A heavy stone portal over the shaft, a lintel carved with the mine's name, lamps either side."""
    b = new("Mine Portal", "mineshaft_entrance", 7, 7)
    stone_ = pick(rng, st.stone)
    tm = trim_of(stone_)
    b.fill(0, 0, 0, 6, 0, 6, PATH)
    b.fill(1, 1, 1, 5, 5, 6, stone_)
    b.clear(2, 1, 1, 4, 4, 6)
    b.set(2, 4, 1, stone_)
    b.set(4, 4, 1, stone_)
    stair(b, 2, 3, 1, f"{tm}_stairs", "east", "top")
    stair(b, 4, 3, 1, f"{tm}_stairs", "west", "top")
    b.fill(2, 0, 2, 4, 0, 5, "gravel")
    b.fill(2, 5, 2, 4, 5, 5, stone_)
    for x in (1, 5):   # buttresses with lamps
        b.set(x, 1, 0, stone_)
        b.set(x, 2, 0, stair_name := f"{tm}_stairs", facing="south", half="bottom", **WL)
        wall_torch(b, x, 3, 0, st, "north")
    hip(b, 1, 1, 5, 6, 6, pick(rng, st.roofs), over=0)
    b.fill(2, 1, 6, 4, 4, 6, stone_)
    b.set(3, 4, 5, st.light, hanging="true", **WL)
    b.sign(3, 5, 0, "north", ["Mine~Level 1~to Level 7", "", "", ""], pick(rng, st.woods))
    for x in (2, 4):
        b.set(x, 1, 5, "rail", shape="north_south", **WL) if x == 2 else b.barrel(x, 1, 5, [("torch", 8)])
    keep_shaft(b)
    return b


@gen("tool_shed", kind="mineshaft_entrance", freq="common", n=3)
def tool_shed(rng, st):
    """A lean-to shed over the shaft hung with tools, a cart and spare rails outside."""
    b = new("Tool Shed", "mineshaft_entrance", 7, 7)
    lk = look(rng, st)
    lk.jetty = lk.buttress = False
    s = Shell(1, 1, 5, 5, 1, h=3)
    shell(b, s, lk)
    b.fill(2, 0, 2, 4, 0, 4, "gravel")
    front_door(b, s, lk, 3, path=False)
    b.path(3, 0, 3, 0)
    windows(b, s, lk, sides=("east",), every=2)
    b.set(2, 1, 2, "grindstone", face="floor", facing="east")
    b.set(4, 1, 2, "crafting_table")
    b.container(2, 1, 4, "barrel", [("stone_pickaxe", 1)], facing="up", open="false")
    b.container(4, 1, 4, "chest", [("paper", 3)], facing="west", type="single", waterlogged="false")
    b.set(4, 2, 3, "air")
    m = pick(rng, st.roofs)
    b.fill(s.x0, s.top, s.z0, s.x1, s.top, s.z1, f"{lk.wood}_planks")
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along="z", fill=lk.infill)
    hang(b, 4, 3, 1, st, height=3)
    # outside: spare rails, a pick in a block
    b.set(6, 1, 0, f"{lk.wood}_fence", **WL)
    b.set(6, 2, 0, st.light, hanging="false", **WL)
    b.sign(2, 2, 0, "north", ["Tools~Sign out~Sign back", "", "", ""], lk.wood)
    keep_shaft(b)
    return b


# ═════════════════════════════════════════════════════════════════════════
#  Gatehouses (13 x 13 in the wall; gate_base is the wall they must match)
# ═════════════════════════════════════════════════════════════════════════

@gen("gate_barbican", kind="city_entrance", freq="common", n=2)
def gate_barbican(rng, st):
    """Two drum towers flanking the arch, machicolations over it, the city's colour hanging
    between, a raised portcullis."""
    b = new("Barbican Gate", "city_entrance", 13, 13)
    gate_base(b, rng, st)
    m = pick(rng, st.roofs)
    for cx in (1, 11):   # half-round towers, standing out of the outer face
        for dx in range(-1, 2):
            for z in range(0, 4):
                if abs(dx) + max(0, 2 - z) <= 2 or z >= 1:
                    b.fill(cx + dx, 0, z, cx + dx, 13, z, WALL)
        b.clear(cx, 10, 1, cx, 12, 2)
        b.set(cx, 12, 0, "iron_bars", **WL)
        b.fill(cx - 1, 14, 0, cx + 1, 14, 3, WALL)
        for x, z in ((cx - 1, 0), (cx + 1, 0), (cx - 1, 3), (cx + 1, 3)):
            b.set(x, 15, z, WALL)
        b.set(cx, 13, 2, st.light, hanging="true", **WL)
        b.clear(cx, 10, 3, cx, 11, 3)
    # keep the walkway through: openings in the towers at y10-11 along z=3
    for x in (0, 2, 10, 12):
        b.clear(x, 10, 3, x, 11, 3)
    # the arch: machicolations over it, a portcullis inside
    for x in range(3, 10):
        stair(b, x, 8, 0, "cobblestone_stairs", "south", "top")
    b.set(4, 5, 1, WALL)
    b.set(8, 5, 1, WALL)
    b.fill(4, 5, 2, 8, 5, 2, "iron_bars", **WL)
    b.fill(5, 6, 0, 7, 7, 0, WOOL)
    for x in (3, 9):
        b.set(x, 3, 0, st.wall_torch, facing="north")
    return b


@gen("gate_clocktower", kind="city_entrance", freq="average", n=2, unique=True)
def gate_clocktower(rng, st):
    """A tall clock tower straddling the gate, its faces looking out and in, a bell under the cap."""
    b = new("Clock Gate", "city_entrance", 13, 13)
    gate_base(b, rng, st)
    stone_ = pick(rng, st.stone)
    m = pick(rng, st.roofs)
    x0, x1, z0, z1 = 3, 9, 0, 6
    top = 22
    b.fill(x0, 1, z0, x1, top, z1, WALL)
    b.clear(4, 1, z0, 8, 5, z1)
    b.set(4, 5, 0, WALL)
    b.set(8, 5, 0, WALL)
    b.set(4, 5, 6, WALL)
    b.set(8, 5, 6, WALL)
    b.clear(4, 10, 1, 8, top - 1, 5)
    b.clear(x0, 10, 3, x0, 11, 3)   # the walkway passes through
    b.clear(x1, 10, 3, x1, 11, 3)
    for y in range(13, top - 4, 4):
        b.fill(4, y, 1, 8, y, 5, f"{pick(rng, st.woods)}_planks")
        b.set(5, y, 3, "air")
    for y in range(10, top - 4):
        b.set(5, y, 2, "ladder", facing="south", **WL)
        b.set(5, y, 1, WALL) if b.empty(5, y, 1) else None
    for y in range(13, top - 4, 4):
        b.set(5, y, 2, "air")
        b.set(5, y, 2, "ladder", facing="south", **WL)
    # clock faces out and in
    for z, f in ((z0, "north"), (z1, "south")):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                b.set(6 + dx, top - 6 + dy, z, "quartz_block" if (dx, dy) != (0, 0) else "black_concrete")
        b.set(6, top - 5, z, "black_concrete")
        b.set(7, top - 6, z, "black_concrete")
    # the belfry: openings, the bell
    for y in range(top - 3, top):
        for x in (5, 7):
            b.set(x, y, z0, "air")
            b.set(x, y, z1, "air")
        for z in (2, 4):
            b.set(x0, y, z, "air")
            b.set(x1, y, z, "air")
    b.fill(4, top - 4, 1, 8, top - 4, 5, f"{pick(rng, st.woods)}_planks")
    b.set(5, top - 4, 2, "air")
    b.set(6, top - 1, 3, "bell", attachment="ceiling", facing="north", powered="false")
    b.set(4, top - 3, 4, st.light, hanging="false", **WL)
    hip(b, x0, z0, x1, z1, top + 1, m, over=0, pitch=2 if st.key == "ashen" else 1)
    for x in (2, 10):
        b.set(x, 3, 0, st.wall_torch, facing="north")
    b.fill(5, 7, 0, 7, 8, 0, WOOL)
    return b


@gen("gate_horseshoe", kind="city_entrance", freq="common", n=2, styles=("ochre",))
def gate_horseshoe(rng, st):
    """A Moorish gate: a tall horseshoe arch in a raised frame of glazed tiles, merlons along the
    top, flags on poles."""
    b = new("Horseshoe Gate", "city_entrance", 13, 13)
    gate_base(b, rng, st)
    tile = pick(rng, ("cyan_glazed_terracotta", "blue_glazed_terracotta", "light_blue_glazed_terracotta"))
    b.fill(2, 0, 0, 10, 13, 1, WALL)
    b.clear(4, 1, 0, 8, 7, 1)
    b.clear(4, 1, 2, 8, 5, 5)
    # the horseshoe: the arch's top rounded with stairs, narrowing at the springing
    for x, y, f in ((4, 7, "east"), (8, 7, "west"), (5, 7, "east"), (7, 7, "west")):
        stair(b, x, y, 0, "smooth_sandstone_stairs", f, "top")
        stair(b, x, y, 1, "smooth_sandstone_stairs", f, "top")
    b.fill(6, 7, 0, 6, 7, 1, "air")
    b.set(6, 8, 0, "chiseled_sandstone")
    for x in range(3, 10):   # the tiled frame
        b.set(x, 9, 0, tile, facing="north")
        b.set(x, 10, 0, tile, facing="south")
    for y in range(1, 9):
        b.set(3, y, 0, tile, facing="east" if y % 2 else "west")
        b.set(9, y, 0, tile, facing="east" if y % 2 else "west")
    for x in range(2, 11, 2):   # merlons
        b.fill(x, 14, 0, x, 15, 0, WALL)
        b.set(x, 16, 0, "smooth_sandstone_slab", type="bottom", **WL)
    for x in (2, 10):   # flagpoles
        b.fill(x, 17, 0, x, 20, 0, f"{pick(rng, st.woods)}_fence", **WL)
        b.fill(x + (1 if x == 2 else -1), 19, 0, x + (1 if x == 2 else -1), 20, 0, WOOL)
    # a room in the thick frame over the passage, the walkway through it
    b.set(6, 5, 3, st.light, hanging="true", **WL)
    b.fill(2, 10, 1, 10, 11, 1, WALL)
    b.fill(2, 12, 1, 10, 13, 1, WALL)
    return b


# ═════════════════════════════════════════════════════════════════════════
#  Trees for the countryside (7 x 7, the trunk at (3, 3))
# ═════════════════════════════════════════════════════════════════════════

def leaf(b, x, y, z, state, **extra):
    if b.inside(x, z) and b.empty(x, y, z):
        b.set(x, y, z, state, **LEAF, **extra)


def crown(b, rng, cx, cy, cz, r, ry, state, keep=0.85):
    for dy in range(-ry, ry + 1):
        for dz in range(-r, r + 1):
            for dx in range(-r, r + 1):
                d = (dx / (r + 0.5)) ** 2 + (dz / (r + 0.5)) ** 2 + (dy / (ry + 0.5)) ** 2
                if d <= 1 and (d < 0.55 or rng.random() < keep):
                    leaf(b, cx + dx, cy + dy, cz + dz, state)


def branch(b, x, y, z, dx, dz, n, log, rise=1):
    """A branch out from (x, y, z): n steps along (dx, dz), climbing every `rise`. Returns its tip."""
    axis = "x" if dx else "z"
    for i in range(1, n + 1):
        x, z = x + dx, z + dz
        if i % rise == 0:
            y += 1
        if b.inside(x, z):
            b.set(x, y, z, log, axis=axis)
    return x, y, z


def tree_lot(name):
    return new(name, "tree", 7, 7)


def big_oak(rng, st):
    b = tree_lot("Great Oak")
    h = rng.randint(5, 6)
    b.fill(3, 1, 3, 3, h, 3, "oak_log", axis="y")
    for x, z in ((2, 3), (4, 3), (3, 2), (3, 4)):   # buttress roots
        if rng.random() < 0.7:
            b.set(x, 1, z, "oak_log", axis="x" if x != 3 else "z")
    tips = [(3, h, 3)]
    for dx, dz in rng.sample(((1, 0), (-1, 0), (0, 1), (0, -1)), 3):
        tips.append(branch(b, 3, h - rng.randint(1, 2), 3, dx, dz, 2, "oak_log"))
    for x, y, z in tips:
        crown(b, rng, x, y + 1, z, 2, 2, "oak_leaves")
    return b


def weeping_willow(rng, st):
    b = tree_lot("Weeping Willow")
    h = rng.randint(5, 6)
    b.fill(3, 1, 3, 3, h, 3, "oak_log", axis="y")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        branch(b, 3, h - 1, 3, dx, dz, 2, "oak_log", rise=2)
    crown(b, rng, 3, h + 1, 3, 3, 1, "oak_leaves", keep=0.9)
    # the curtain: strands hanging from the crown's rim
    for x in range(7):
        for z in range(7):
            if 2 <= abs(x - 3) + abs(z - 3) and max(abs(x - 3), abs(z - 3)) >= 2 and rng.random() < 0.6:
                ys = [y for y in range(h - 1, h + 3) if b.get(x, y, z) == "oak_leaves"]
                if ys:
                    for y in range(min(ys) - 1, min(ys) - rng.randint(2, 4), -1):
                        if y >= 2:
                            leaf(b, x, y, z, "oak_leaves")
    return b


def blossom(rng, st):
    b = tree_lot("Blossom Tree")
    h = rng.randint(3, 4)
    b.fill(3, 1, 3, 3, h, 3, "cherry_log", axis="y")
    d = pick(rng, ((1, 0), (-1, 0), (0, 1), (0, -1)))
    x, y, z = branch(b, 3, h, 3, d[0], d[1], 1, "cherry_log")
    crown(b, rng, 3, h + 2, 3, 2, 1, "cherry_leaves", keep=0.8)
    crown(b, rng, x, y + 1, z, 1, 1, "cherry_leaves")
    for _ in range(4):
        px, pz = rng.randint(1, 5), rng.randint(1, 5)
        if b.get(px, 0, pz) == GROUND and b.empty(px, 1, pz):
            b.set(px, 1, pz, "pink_petals", facing=pick(rng, ("north", "east", "south", "west")), flower_amount=str(rng.randint(1, 4)))
    return b


def palm(rng, st):
    b = tree_lot("Palm")
    h = rng.randint(6, 8)
    dx, dz = pick(rng, ((1, 0), (-1, 0), (0, 1), (0, -1)))
    x, z = 3 - dx, 3 - dz
    for y in range(1, h + 1):   # a trunk that leans one block over its height
        if y == h // 2:
            x, z = x + dx, z + dz
        b.set(x, y, z, "jungle_log", axis="y")
    top = h + 1
    leaf(b, x, top, z, "jungle_leaves")
    for fx, fz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        for i in range(1, 4 if fx * fz == 0 else 3):
            drop = 0 if fx * fz else (1 if i >= 2 else 0) + (1 if i >= 3 else 0)
            leaf(b, x + fx * i, top - drop, z + fz * i, "jungle_leaves")
    for fx, fz in ((1, 0), (-1, 0)):
        if b.inside(x + fx, z) and b.empty(x + fx, h, z) and rng.random() < 0.5:
            b.set(x + fx, h, z, "cocoa", facing="west" if fx > 0 else "east", age="2")
    return b


def stone_pine(rng, st):
    b = tree_lot("Stone Pine")
    h = rng.randint(6, 7)
    b.fill(3, 1, 3, 3, h, 3, "spruce_log", axis="y")
    for dx, dz in ((1, 0), (-1, 0)):
        branch(b, 3, h - 1, 3, dx, dz, 1, "spruce_log")
    for dz in range(-3, 4):   # a wide flat umbrella
        for dx in range(-3, 4):
            if dx * dx + dz * dz <= 10 and rng.random() < 0.9:
                leaf(b, 3 + dx, h + 1, 3 + dz, "acacia_leaves")
            if dx * dx + dz * dz <= 4:
                leaf(b, 3 + dx, h + 2, 3 + dz, "acacia_leaves")
    return b


def lemon_tree(rng, st):
    b = tree_lot("Lemon Tree")
    b.fill(3, 1, 3, 3, 2, 3, "oak_log", axis="y")
    crown(b, rng, 3, 4, 3, 2, 1, "azalea_leaves", keep=0.9)
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if n == "azalea_leaves" and rng.random() < 0.3:
            b.set(x, y, z, "flowering_azalea_leaves", **LEAF)
    for x, z in ((1, 3), (5, 3), (3, 1)):
        if b.empty(x, 1, z) and rng.random() < 0.5:
            b.set(x, 1, z, "decorated_pot", facing="north", cracked="false", **WL)
    return b


def dead_tree(rng, st):
    b = tree_lot("Dead Tree")
    h = rng.randint(4, 6)
    log = pick(rng, ("dark_oak_log", "stripped_dark_oak_log", "spruce_log", "stripped_spruce_log"))
    b.fill(3, 1, 3, 3, h, 3, log, axis="y")
    for dx, dz in rng.sample(((1, 0), (-1, 0), (0, 1), (0, -1)), rng.randint(2, 4)):
        x, y, z = branch(b, 3, rng.randint(2, h - 1), 3, dx, dz, rng.randint(1, 3), log, rise=rng.choice((1, 2)))
        if rng.random() < 0.6 and b.inside(x, z):
            b.set(x, y + 1, z, f"{'dark_oak' if 'dark' in log else 'spruce'}_fence", **WL)
    for x in range(7):
        for z in range(7):
            if b.get(x, 0, z) == GROUND and rng.random() < 0.15:
                b.set(x, 0, z, "podzol")
    return b


def mossy_pale_oak(rng, st):
    b = tree_lot("Mossy Pale Oak")
    h = rng.randint(4, 5)
    for dx in (0, 1):
        for dz in (0, 1):
            b.fill(2 + dx, 1, 2 + dz, 2 + dx, h, 2 + dz, "pale_oak_log", axis="y")
    for cx, cz in ((2, 2), (3, 2), (2, 3), (3, 3)):
        crown(b, rng, cx, h + 1, cz, 2, 1, "pale_oak_leaves", keep=0.8)
    for x in range(7):   # pale moss hanging from the underside of the crown
        for z in range(7):
            ys = [y for y in range(2, h + 3) if b.get(x, y, z) == "pale_oak_leaves"]
            if ys and rng.random() < 0.45:
                y0 = min(ys) - 1
                n = rng.randint(1, 3)
                for i in range(n):
                    if y0 - i >= 1 and b.empty(x, y0 - i, z):
                        b.set(x, y0 - i, z, "pale_hanging_moss", tip="true" if i == n - 1 else "false")
                    else:
                        break
                if not b.get(x, y0 - n + 1, z) == "pale_hanging_moss":
                    for yy in range(y0, y0 - n, -1):
                        if b.get(x, yy, z) == "pale_hanging_moss":
                            b.set(x, yy, z, "pale_hanging_moss", tip="true" if b.empty(x, yy - 1, z) else "false")
    for x in range(7):
        for z in range(7):
            if b.get(x, 0, z) == GROUND and b.empty(x, 1, z) and rng.random() < 0.2:
                b.set(x, 1, z, "pale_moss_carpet", bottom="true", north="none", south="none", east="none", west="none")
    return b


def yew(rng, st):
    b = tree_lot("Churchyard Yew")
    h = rng.randint(6, 8)
    b.fill(3, 1, 3, 3, h - 1, 3, "spruce_log", axis="y")
    for y in range(2, h + 1):
        r = 2 if 3 <= y <= h - 2 else 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) <= r + (1 if r == 2 else 0) and rng.random() < 0.92:
                    leaf(b, 3 + dx, y, 3 + dz, "dark_oak_leaves")
    leaf(b, 3, h + 1, 3, "dark_oak_leaves")
    return b


TREES = {
    "timber": (("big_oak", big_oak), ("weeping_willow", weeping_willow), ("blossom", blossom)),
    "ochre": (("palm", palm), ("stone_pine", stone_pine), ("lemon_tree", lemon_tree)),
    "ashen": (("dead_tree", dead_tree), ("mossy_pale_oak", mossy_pale_oak), ("yew", yew)),
}
for _style, _kinds in TREES.items():
    for _name, _f in _kinds:
        gen(f"tree_{_name}", kind="tree", freq="common", n=3, styles=(_style,))(_f)
