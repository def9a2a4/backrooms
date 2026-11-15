"""Level 143's buildings, drawn from rules and a seed. Every generator takes (rng, style) and
returns a schem.Build facing north (its front at z = 0, on the street); the city turns it.
CATALOG lists them with their Mace type, frequency and how many of each to draw per style.
"""

from dataclasses import dataclass

from parts import (FULL, LEAF, Mat, Room, Shell, bench, buttresses, chimney, cornice, door_dress, edge_points, flat,
                   furnish, front_door, gable, hang, hang_chain, hip, ladder, lamp_post, look, mat, pick, roof,
                   roof_kind, roof_levels, draw_roof, gable_fill, shell, shed, slab_of, solid, spire, stairs_of, table, terrace,
                   tree, trim_of, wall_of, window_dress, windows)
from schem import GROUND, LEFT, OPPOSITE, PATH, RIGHT, STEP, TREASURE, WALL, WOOL, Build


@dataclass
class Entry:
    name: str
    kind: str
    make: object
    freq: str = "common"
    n: int = 3
    unique: bool = False
    styles: tuple = ()        # empty: every style
    extra: dict = None        # more Metadata.Mace fields


CATALOG = []


def gen(name, kind="city", freq="common", n=3, unique=False, styles=(), **extra):
    def deco(f):
        CATALOG.append(Entry(name, kind, f, freq, n, unique, styles, extra))
        return f
    return deco


def new(name, kind, w, l):
    b = Build(name, kind, w, l)
    b.ground()
    return b


def flowers(b, rng, st, cells, chance=0.5):
    for x, z in cells:
        if b.get(x, 0, z) == GROUND and b.empty(x, 1, z) and rng.random() < chance:
            f = pick(rng, st.flowers)
            if f in ("brown_mushroom",):
                continue
            b.set(x, 1, z, f)


def yard_cells(b, z_max):
    return [(x, z) for x in range(b.w) for z in range(z_max)]


# ── Houses ────────────────────────────────────────────────────────────────

def house_body(b, rng, st, s, lk, items_by_floor, sign=None, cellar=False, kind=None, door_x=None):
    """A whole house in the shell: walls, door, windows, ladder, rooms, roof, chimney. A
    timbered house with a yard in front may overhang the street upstairs (a jetty)."""
    jetty = lk.jetty and lk.timbered and s.floors >= 2 and s.z0 >= 2
    if s.x1 - s.x0 <= 7:   # narrow fronts: windows close enough that the street side gets some
        lk.window_every = min(lk.window_every, 2)
    up = Shell(s.x0, s.z0 - 1, s.x1, s.z1, s.floors, s.h) if jetty else s
    shell(b, up, lk)
    if jetty:
        jetty_front(b, s, up, lk)
    dx = front_door(b, s, lk, door_x if door_x is not None else rng.randint(s.x0 + 1, s.x1 - 1))
    skip = {(dx, s.z0), (dx - 1, s.z0), (dx + 1, s.z0), (dx, up.z0), (dx - 1, up.z0), (dx + 1, up.z0)}
    lx, lz = (s.x0 + 1, s.z1 - 1) if rng.random() < 0.5 else (s.x1 - 1, s.z1 - 1)
    if kind is None:
        kind = roof_kind(rng, st, up)
    flat_roof = kind == "flat"
    if s.floors > 1 or flat_roof:
        ladder(b, lx, lz, 1, s.top - 1 if flat_roof else (s.floors - 1) * s.h, "north", support=lk.infill)
    if jetty:
        windows(b, s, lk, sides=("north",), skip=skip, floors=[0])
        windows(b, up, lk, sides=("north",), skip=skip, floors=range(1, s.floors))
        windows(b, up, lk, sides=("south", "east", "west"), skip=skip | {(s.x0, s.z0), (s.x1, s.z0)})
    else:
        windows(b, s, lk, skip=skip)
    door_dress(b, rng, st, lk, dx, 1, s.z0, torches=not sign)
    if lk.buttress:
        buttresses(b, s, lk, sides=("east", "west"))
    for k in range(s.floors):
        y = k * s.h + 1
        r = s if k == 0 else up
        keep = {(dx, s.z0 + 1), (lx, lz), (lx, lz - 1)}
        room = Room(b, r.x0 + 1, r.z0 + 1, r.x1 - 1, r.z1 - 1, y, keep)
        items = items_by_floor[min(k, len(items_by_floor) - 1)]
        if k == 0 and s.x1 - s.x0 >= 6 and s.z1 - s.z0 >= 5:
            tx, tz = (s.x0 + s.x1) // 2, (s.z0 + s.z1) // 2 + 1
            if room.free(tx, tz) and (tx, tz) not in keep and abs(tx - lx) + abs(tz - lz) > 2:
                table(b, tx, y, tz, lk.wood, chairs=("east", "west"))
                for c in ((tx, tz), (tx - 1, tz), (tx + 1, tz)):
                    room.keep_out(*c)
        furnish(room, rng, st, lk, items, carpet=pick(rng, st.beds) if rng.random() < 0.4 else None)
    top = roof(b, rng, st, up, lk, kind=kind, hatch=(lx, lz) if flat_roof else None)
    if rng.random() < 0.6:
        cx = s.x1 - 1 if lx == s.x0 + 1 else s.x0 + 1
        chimney(b, cx, s.z1, 1, top + 1, pick(rng, ("bricks", lk.found)))
    if cellar:
        under(b, rng, s, lk)
    if sign:
        sx = dx + 1 if solid(b, dx + 1, 2, s.z0) else dx - 1
        b.sign(sx, 2, s.z0 - 1, "north", sign, lk.wood)
    return dx


def jetty_front(b, s, up, lk):
    """Moves the ground floor's front wall a block back under the upper storeys, which rest on
    a beam and brackets over the street."""
    z = up.z0
    b.clear(s.x0, 1, z, s.x1, s.h - 1, z)
    b.fill(s.x0, 0, z, s.x1, 0, z, GROUND)
    b.fill(s.x0, 0, s.z0, s.x1, 0, s.z0, lk.found)
    b.fill(s.x0, 1, s.z0, s.x1, 1, s.z0, lk.found)
    b.fill(s.x0, 2, s.z0, s.x1, s.h - 1, s.z0, lk.infill)
    for x in edge_points(s.x0, s.x1, lk.post_every):
        b.fill(x, 2, s.z0, x, s.h - 1, s.z0, lk.log, axis="y")
        if b.empty(x, s.h - 1, z):
            b.set(x, s.h - 1, z, f"{lk.wood}_stairs", facing="south", half="top", waterlogged="false")
    for x in (s.x0, s.x1):   # the side walls' corner posts come down to the ground floor's front
        b.set(x, s.h, z, lk.log, axis="x")


def under(b, rng, s, lk):
    """A cellar under the house, down a ladder by the door."""
    x0, z0, x1, z1 = s.x0, s.z0, s.x1, s.z1
    hx, hz = x0 + 1, z0 + 1
    if not b.empty(hx, 1, hz) or not b.empty(hx, 2, hz) or b.get(hx + 1, 1, hz).endswith("_door"):
        hx = x1 - 1
        if not b.empty(hx, 1, hz) or not b.empty(hx, 2, hz) or b.get(hx - 1, 1, hz).endswith("_door"):
            return
    b.fill(x0, -4, z0, x1, -1, z1, lk.found)
    b.clear(x0 + 1, -3, z0 + 1, x1 - 1, -1, z1 - 1)
    ladder(b, hx, hz, -3, -1, "south")
    b.set(hx, 0, hz, f"{lk.wood}_trapdoor", facing="north", half="top", open="false", powered="false",
          waterlogged="false")
    for x in range(x0 + 2, x1):
        if (x + z1) % 2 == 0:
            b.barrel(x, -3, z1 - 1, [(pick(rng, ("potato", "carrot", "apple", "beetroot", "wheat")), rng.randint(4, 16))])
    b.chest(x1 - 1 if hx == x0 + 1 else x0 + 1, -3, z0 + 1, "west" if hx == x0 + 1 else "east")
    b.set((x0 + x1) // 2, -1, (z0 + z1) // 2, "lantern", hanging="true", waterlogged="false")


HOUSE_GROUND = [["crafting_table", "furnace", "barrel", "chest", "plant"],
                ["furnace", "barrel", "crafting_table", "chest", "shelf"],
                ["smoker", "barrel", "chest", "plant", "crafting_table"]]
HOUSE_UPPER = [["bed", "chest", "shelf", "plant"], ["bed", "bed", "chest", "plant"], ["bed", "chest", "shelf", "barrel"]]
HOUSE_ONE = [["bed", "chest", "crafting_table", "furnace", "plant"], ["bed", "furnace", "chest", "barrel", "shelf"]]


@gen("cottage", freq="very common", n=5)
def cottage(rng, st):
    W, D = rng.randint(5, 7), rng.randint(5, 7)
    yard = rng.randint(1, 3)
    b = new("Cottage", "city", W + 2, yard + D + 1)
    s = Shell(1, yard, W, yard + D - 1, 1, h=rng.choice((4, 4, 5)))
    house_body(b, rng, st, s, look(rng, st), [pick(rng, HOUSE_ONE)], sign="[house]" if rng.random() < 0.5 else None,
               cellar=rng.random() < 0.2)
    flowers(b, rng, st, yard_cells(b, yard), 0.3)
    return b


@gen("house", freq="very common", n=6)
def house(rng, st):
    W, D = rng.randint(6, 9), rng.randint(6, 8)
    yard = rng.randint(1, 3)
    b = new("House", "city", W + 2, yard + D + 1)
    s = Shell(1, yard, W, yard + D - 1, 2)
    house_body(b, rng, st, s, look(rng, st), [pick(rng, HOUSE_GROUND), pick(rng, HOUSE_UPPER)],
               sign="[house]" if rng.random() < 0.6 else None, cellar=rng.random() < 0.25)
    flowers(b, rng, st, yard_cells(b, yard), 0.3)
    if yard >= 2 and rng.random() < 0.5:   # a fence along the front garden
        for x in range(b.w):
            if b.get(x, 0, 0) == GROUND:
                b.set(x, 1, 0, f"{pick(rng, st.woods)}_fence", waterlogged="false")
    return b


@gen("large_house", freq="common", n=4)
def large_house(rng, st):
    W, D = rng.randint(9, 12), rng.randint(7, 10)
    yard = rng.randint(2, 4)
    b = new("Large House", "city", W + 2, yard + D + 1)
    s = Shell(1, yard, W, yard + D - 1, rng.choice((2, 3)))
    house_body(b, rng, st, s, look(rng, st), [pick(rng, HOUSE_GROUND), pick(rng, HOUSE_UPPER), pick(rng, HOUSE_UPPER)],
               sign="[house]", cellar=rng.random() < 0.4)
    flowers(b, rng, st, yard_cells(b, yard), 0.4)
    return b


@gen("tall_house", freq="common", n=3)
def tall_house(rng, st):
    W, D = rng.randint(5, 6), rng.randint(5, 7)
    yard = rng.randint(1, 2)
    b = new("Tall House", "city", W + 2, yard + D + 1)
    s = Shell(1, yard, W, yard + D - 1, rng.choice((3, 4)))
    house_body(b, rng, st, s, look(rng, st), [pick(rng, HOUSE_GROUND), pick(rng, HOUSE_UPPER)],
               sign="[house]" if rng.random() < 0.5 else None, kind=pick(rng, ("gable", "hip")))
    return b


@gen("l_house", freq="common", n=4)
def l_house(rng, st):
    """A house with a wing behind it, one storey lower."""
    W, D = rng.randint(7, 10), rng.randint(5, 7)
    ww, wd = rng.randint(5, 6), rng.randint(4, 6)
    yard = rng.randint(1, 3)
    b = new("Winged House", "city", W + 2, yard + D + wd - 1 + 2)
    lk = look(rng, st)
    floors = rng.choice((1, 2, 2))
    left = rng.random() < 0.5
    wx0 = 1 if left else W - ww + 1
    wing = Shell(wx0, yard + D - 1, wx0 + ww - 1, yard + D - 1 + wd, max(1, floors - 1) if floors > 1 else 1)
    shell(b, wing, lk)
    windows(b, wing, lk, sides=("south", "east" if not left else "west"))
    # the wing's roof dies into the house's back wall: no overhang into it, never steeper
    draw_roof(b, roof_levels(wing.x0, wing.z0, wing.x1, wing.z1, "gable", "z", 1, 1, ends=(False, True)),
              wing.top, pick(rng, st.roofs), 1, lk.infill, walls=[(wing.x0, wing.z0, wing.x1, wing.z1)])
    furnish(Room(b, wing.x0 + 1, wing.z0 + 1, wing.x1 - 1, wing.z1 - 1, 1), rng, st, lk,
            ["bed", "chest", "plant"] if floors == 1 else ["furnace", "barrel", "crafting_table"])
    lk.jetty = False   # the wing's roof meets the back wall
    main = Shell(1, yard, W, yard + D - 1, floors)
    house_body(b, rng, st, main, lk, [pick(rng, HOUSE_GROUND), pick(rng, HOUSE_UPPER)],
               sign="[house]" if rng.random() < 0.5 else None)
    # a doorway from the house into the wing, where both sides are clear
    for mx in sorted(range(wing.x0 + 1, wing.x1), key=lambda x: abs(x - (wing.x0 + wing.x1) // 2)):
        if all(b.empty(mx, y, z) for y in (1, 2) for z in (main.z1 - 1, main.z1 + 1)) and \
                b.get(mx, 0, main.z1 - 1) != "ladder":
            b.clear(mx, 1, main.z1, mx, 2, main.z1)
            b.set(mx, 0, main.z1, lk.floor)
            break
    flowers(b, rng, st, yard_cells(b, yard), 0.3)
    return b


@gen("row_houses", freq="common", n=4)
def row_houses(rng, st):
    """Three or four narrow houses sharing their side walls, each its own colour: under one long
    roof, or each with its own gable to the street."""
    n, uw = rng.randint(3, 4), rng.randint(4, 5)
    D = rng.randint(6, 8)
    yard = rng.randint(1, 2)
    W = n * (uw - 1) + 1
    b = new("Row Houses", "city", W + 2, yard + D + 1)
    floors = rng.choice((2, 2, 3))
    base = look(rng, st)
    m = pick(rng, st.roofs)
    gabled = rng.random() < 0.6
    units = []
    for i in range(n):
        lk = look(rng, st, timbered=base.timbered)
        lk.wood, lk.found, lk.jetty, lk.buttress = base.wood, base.found, False, False
        s = Shell(1 + i * (uw - 1), yard, 1 + (i + 1) * (uw - 1), yard + D - 1, floors)
        shell(b, s, lk)
        units.append((s, lk))
    for i, (s, lk) in enumerate(units):
        dx = front_door(b, s, lk, s.x0 + 1 + (i % 2) * (uw - 3))
        sx = dx + 1 if dx == s.x0 + 1 else dx - 1
        lx = s.x1 - 1 if dx == s.x0 + 1 else s.x0 + 1
        ladder(b, lx, s.z1 - 1, 1, (floors - 1) * s.h, "north", support=lk.infill)
        windows(b, s, lk, sides=("north",), skip={(dx, s.z0), (sx, s.z0)}, every=2, floors=[0])
        windows(b, s, lk, sides=("north",), every=2 if uw == 5 else 1, floors=range(1, floors))
        windows(b, s, lk, sides=("south",), every=2)
        door_dress(b, rng, st, lk, dx, 1, s.z0, torches=False)
        for k in range(floors):
            y = k * s.h + 1
            room = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, y, {(dx, s.z0 + 1), (lx, s.z1 - 1), (lx, s.z1 - 2)})
            furnish(room, rng, st, lk, pick(rng, HOUSE_UPPER) if k else pick(rng, HOUSE_GROUND)[:3])
        b.sign(sx, 2, s.z0 - 1, "north", "[house]", lk.wood)
    top = units[0][0].top
    if gabled:
        pitch = 2 if rng.random() < st.pitch2 else 1
        levels = {}
        for s, _ in units:
            for k, v in roof_levels(s.x0, s.z0, s.x1, s.z1, "gable", "z", 1, 1).items():
                levels[k] = max(levels.get(k, -1), v)
        draw_roof(b, levels, top, m, pitch)
        for s, lk in units:
            gable_fill(b, levels, top, pitch, (s.x0, s.z0, s.x1, s.z1), lk.infill)
            cx = (s.x0 + s.x1) // 2
            for z in (s.z0, s.z1):
                if b.get(cx, top + 2, z) == lk.infill:
                    b.set(cx, top + 2, z, lk.pane, waterlogged="false")
                if (s.x1 - s.x0) % 2 == 0 and b.get(cx, top + 1, z) == lk.infill and lk.timbered:
                    b.set(cx, top + 1, z, lk.log, axis="x")
    else:
        s = Shell(1, yard, W, yard + D - 1, floors)
        gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along="x", fill=base.found, pitch=1)
    for i in range(1, n):   # chimneys on the party walls
        if rng.random() < 0.6:
            x = 1 + i * (uw - 1)
            zc = yard + D // 2 if not gabled else yard + D - 2
            ytop = max(y for (bx, y, bz) in b.blocks if bx == x and bz == zc)
            chimney(b, x, zc, top, ytop + 1, "bricks")
    return b


@gen("courtyard_house", freq="common", n=4, styles=("ochre",))
def courtyard_house(rng, st):
    """Rooms around an open court with a well or a tree, under a flat roof."""
    W, D = rng.randint(11, 13), rng.randint(11, 13)
    b = new("Courtyard House", "city", W + 2, D + 2)
    lk = look(rng, st, timbered=False)
    s = Shell(1, 1, W, D, 1, h=5)
    shell(b, s, lk)
    cx0, cz0, cx1, cz1 = 4, 4, W - 3, D - 3
    b.fill(cx0, 1, cz0, cx1, 5, cz1, "air")
    b.ring(cx0 - 1, cz0 - 1, cx1 + 1, cz1 + 1, 1, 5, lk.infill)
    b.fill(cx0, 0, cz0, cx1, 0, cz1, PATH)
    for x in range(cx0, cx1 + 1, 2):   # arcades onto the court
        b.clear(x, 1, cz0 - 1, x, 3, cz0 - 1)
        b.clear(x, 1, cz1 + 1, x, 3, cz1 + 1)
    front_door(b, s, lk, (s.x0 + s.x1) // 2)
    b.clear((s.x0 + s.x1) // 2, 1, cz0 - 1, (s.x0 + s.x1) // 2, 2, cz0 - 1)
    windows(b, s, lk, skip={((s.x0 + s.x1) // 2, s.z0)}, every=3)
    if rng.random() < 0.5:
        mx, mz = (cx0 + cx1) // 2, (cz0 + cz1) // 2
        b.set(mx, 0, mz, "water", level="0")
        b.ring(mx - 1, mz - 1, mx + 1, mz + 1, 1, 1, lk.found)
        b.set(mx, 1, mz, "air")
    else:
        tree(b, rng, (cx0 + cx1) // 2, (cz0 + cz1) // 2, "olive")
        b.set((cx0 + cx1) // 2, 0, (cz0 + cz1) // 2, GROUND)
    for z0, z1 in ((s.z0 + 1, cz0 - 2), (cz1 + 2, s.z1 - 1)):
        arcade = {(x, z) for x in range(cx0, cx1 + 1, 2) for z in (cz0 - 2, cz1 + 2)}
        room = Room(b, s.x0 + 1, z0, s.x1 - 1, z1, 1, {((s.x0 + s.x1) // 2, z) for z in range(s.z0 + 1, cz0 - 1)} | arcade)
        furnish(room, rng, st, lk, ["bed", "chest", "barrel", "plant", "furnace", "crafting_table"])
    b.fill(s.x0, s.top, s.z0, s.x1, s.top, s.z1, lk.found)
    b.fill(cx0, s.top, cz0, cx1, s.top, cz1, "air")
    t = trim_of(lk.found)
    b.ring(s.x0, s.z0, s.x1, s.z1, s.top + 1, s.top + 1, lk.found)
    b.ring(s.x0, s.z0, s.x1, s.z1, s.top + 2, s.top + 2, f"{t}_slab", type="bottom", waterlogged="false")
    b.ring(cx0 - 1, cz0 - 1, cx1 + 1, cz1 + 1, s.top + 1, s.top + 1, f"{t}_slab", type="bottom", waterlogged="false")
    cornice(b, s, f"{t}_stairs", s.top)
    for x in range(cx0, cx1 + 1, 2):   # the arcades' arches
        for z in (cz0 - 1, cz1 + 1):
            b.set(x, 3, z, f"{t}_slab", type="top", waterlogged="false")
    for x in range(s.x0 + 1, s.x1):   # pots along the roof walk
        for z in (s.z0 + 1, s.z1 - 1):
            if rng.random() < 0.25:
                b.set(x, s.top + 1, z, pick(rng, st.pots))
    door_dress(b, rng, st, lk, (s.x0 + s.x1) // 2, 1, s.z0)
    for x, z in ((cx0 - 1, cz0 - 1), (cx1 + 1, cz1 + 1)):
        hang(b, x, z, 1, st, height=s.h)
    return b


# ── Shops, inns and halls ─────────────────────────────────────────────────

SHOPS = {
    "bakery": ("Bakery", ["smoker", "smoker", "hay_block", "barrel", "chest"], "cake"),
    "smithy": ("Smithy", ["anvil", "blast_furnace", "grindstone", "smithing_table", "chest"], None),
    "apothecary": ("Apothecary", ["brewing_stand", "plant", "plant", "barrel", "chest"], "potted_red_mushroom"),
    "tailor": ("Tailor", ["loom", WOOL, WOOL, "barrel", "chest"], None),
    "books": ("Books", ["shelf", "shelf", "lectern", "shelf", "chest"], None),
    "grocer": ("Grocer", ["barrel", "barrel", "composter", "pumpkin", "melon", "hay_block"], "potted_cactus"),
    "maps": ("Maps", ["cartography_table", "shelf", "lectern", "chest"], None),
    "fletcher": ("Fletcher", ["fletching_table", "target", "barrel", "chest"], None),
    "mason": ("Mason", ["stonecutter", "chiseled_stone_bricks", "stone_bricks", "chest"], None),
    "chandler": ("Candles", ["barrel", "chest", "shelf"], "candle"),
}


def shop(rng, st, kind):
    title, items, counter_item = SHOPS[kind]
    W, D = rng.randint(7, 10), rng.randint(7, 9)
    yard = rng.randint(1, 3)
    b = new(f"{title} Shop", "city", W + 2, yard + D + 1)
    lk = look(rng, st)
    lk.window_every, lk.jetty = 2, False
    s = Shell(1, yard, W, yard + D - 1, rng.choice((1, 2, 2)))
    shell(b, s, lk)
    dx = front_door(b, s, lk, s.x0 + 2)
    lx, lz = s.x1 - 1, s.z1 - 1
    if s.floors > 1:
        ladder(b, lx, lz, 1, s.h, "north", support=lk.infill)
    windows(b, s, lk, sides=("south", "east", "west"))
    windows(b, s, lk, sides=("north",), floors=range(1, s.floors))
    # the shop window, right of the sign, and the goods on show behind it
    glass = lk.pane if lk.pane != "air" else "glass_pane"
    show = [counter_item or "barrel", "potted_fern", "candle", "lantern", "cake", "flower_pot", "potted_red_tulip"]
    for x in range(dx + 2, s.x1):
        b.fill(x, 2, s.z0, x, s.h - 1, s.z0, glass, waterlogged="false")
        b.set(x, 1, s.z0 + 1, f"{lk.wood}_slab", type="top", waterlogged="false")
        g = show[(x * 3 + len(title)) % len(show)] if (x - dx) % 2 == 0 else None
        if g == "candle":
            b.set(x, 2, s.z0 + 1, "candle", candles=str(rng.randint(1, 4)), lit="false", waterlogged="false")
        elif g == "lantern":
            b.set(x, 2, s.z0 + 1, st.light, hanging="false", waterlogged="false")
        elif g == "cake":
            b.set(x, 2, s.z0 + 1, "cake", bites="0")
        elif g == "barrel":
            b.barrel(x, 2, s.z0 + 1, facing="up")
        elif g:
            b.set(x, 2, s.z0 + 1, g, **({"candles": "3", "lit": "false", "waterlogged": "false"} if g == "candle" else {}))
    # a striped awning over the door and the window
    ay = s.h - 1
    for x in range(s.x0, s.x1 + 1):
        b.set(x, ay, s.z0 - 1, WOOL if x % 2 else "white_wool")
    for x in (s.x0, s.x1):
        if b.get(x, 0, s.z0 - 1) == GROUND:
            b.fill(x, 1, s.z0 - 1, x, ay - 1, s.z0 - 1, f"{lk.wood}_fence", waterlogged="false")
    # the counter across the shop, a stool behind it, a gap at the end
    cz = s.z0 + 3
    for x in range(s.x0 + 1, s.x1 - 1):
        if x == dx:
            continue
        b.set(x, 1, cz, f"stripped_{lk.wood}_log" if lk.wood != "pale_oak" else "stripped_pale_oak_log", axis="x")
    for x in range(s.x0 + 1, s.x1 - 1):
        if x == dx:
            b.set(x, 1, cz, f"{lk.wood}_fence_gate", facing="south", in_wall="false", open="false", powered="false")
    if counter_item:
        props = {"candles": "3", "lit": "false", "waterlogged": "false"} if counter_item == "candle" else {}
        if counter_item == "cake":
            props = {"bites": "0"}
        b.set(s.x0 + 3, 2, cz, counter_item, **props)
    b.set(s.x1 - 2, 2, cz, st.light, hanging="false", waterlogged="false")
    b.set(s.x0 + 3, 1, cz + 1, f"{lk.wood}_stairs", facing="south", half="bottom", waterlogged="false")
    room = Room(b, s.x0 + 1, cz + 1, s.x1 - 1, s.z1 - 1, 1,
                {(s.x0 + 3, cz + 1), (dx, cz + 1), (s.x1 - 1, cz + 1), (lx, lz), (lx, lz - 1)})
    if s.floors > 1:
        up = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, s.h + 1, {(lx, lz), (lx, lz - 1)})
        furnish(up, rng, st, lk, pick(rng, HOUSE_UPPER))
    furnish(room, rng, st, lk, list(items), light=False)
    hang(b, s.x0 + 2, s.z0 + 1, 1, st)
    hang(b, (s.x0 + s.x1) // 2, cz + 2, 1, st)
    top = roof(b, rng, st, s, lk, kind=pick(rng, ("gable", "gable", "half_hip", "hip")) if st.key != "ochre" else
               pick(rng, ("hip", "gable")))
    if kind in ("smithy", "bakery"):
        chimney(b, s.x1 - 1, s.z1, 1, top + 1, "bricks")
    b.sign(dx + 1, 2, s.z0 - 1, "north", ["2[surnames]'s~" + title, "", "", ""], lk.wood)
    return b


for _k in SHOPS:
    gen(f"shop_{_k}", freq="average", n=1)(lambda rng, st, _k=_k: shop(rng, st, _k))


@gen("tavern", freq="average", n=3)
def tavern(rng, st):
    """A taproom with a fireplace and a bar, rooms to let upstairs."""
    W, D = rng.randint(11, 13), rng.randint(9, 11)
    yard = rng.randint(2, 3)
    b = new("Tavern", "city", W + 2, yard + D + 1)
    lk = look(rng, st)
    lk.jetty = False
    s = Shell(1, yard, W, yard + D - 1, rng.choice((2, 3)))
    shell(b, s, lk)
    dx = front_door(b, s, lk, (s.x0 + s.x1) // 2, wide=True)
    lx, lz = s.x1 - 1, s.z0 + 1
    ladder(b, lx, lz, 1, (s.floors - 1) * s.h, "south", support=lk.infill)
    zm = (s.z0 + s.z1) // 2
    windows(b, s, lk, skip={(dx - 1, s.z0), (dx, s.z0), (dx + 1, s.z0), (dx + 2, s.z0), (s.x0, zm),
                            (s.x0, zm - 1), (s.x0, zm + 1)})
    door_dress(b, rng, st, lk, dx, 1, s.z0, wide=True, torches=False)
    # the fireplace in the west wall, its stack outside
    b.fill(s.x0 - 1, 1, zm - 1, s.x0 - 1, 2, zm + 1, "bricks")
    b.fill(s.x0, 1, zm - 1, s.x0, s.h - 1, zm + 1, "bricks")
    b.fill(s.x0 + 1, 1, zm - 1, s.x0 + 1, 3, zm + 1, "bricks")
    b.set(s.x0 + 1, 2, zm, "air")
    b.set(s.x0 + 1, 1, zm, "campfire", facing="east", lit="true", signal_fire="false", waterlogged="false")
    for z in (zm - 1, zm + 1):
        b.set(s.x0 + 2, 1, z, f"{lk.wood}_stairs", facing="east", half="bottom", waterlogged="false")
    b.set(s.x0 + 1, 3, zm, "brick_slab", type="top", waterlogged="false")
    b.set(s.x0 + 1, 3, zm - 1, "brick_stairs", facing="west", half="top", waterlogged="false")
    b.set(s.x0 + 1, 3, zm + 1, "brick_stairs", facing="west", half="top", waterlogged="false")
    # the bar along the back: a counter with the taps on it, casks behind
    bz = s.z1 - 2
    for x in range(s.x0 + 3, s.x1 - 1):
        b.set(x, 1, bz, f"{lk.wood}_planks")
        b.set(x, 2, bz, f"{lk.wood}_slab", type="bottom", waterlogged="false")
        b.barrel(x, 1, s.z1 - 1, [("honey_bottle", rng.randint(1, 4))] if x % 3 == 0 else [], facing="north")
        if x % 2:
            b.barrel(x, 2, s.z1 - 1, [], facing="north")
    b.set(s.x0 + 4, 2, bz, "brewing_stand", has_bottle_0="false", has_bottle_1="false", has_bottle_2="false")
    b.set(s.x1 - 3, 2, bz, "candle", candles="3", lit="false", waterlogged="false")
    for x in range(s.x0 + 3, s.x1 - 1, 2):   # stools
        if b.empty(x, 1, bz - 1):
            b.set(x, 1, bz - 1, f"{lk.wood}_stairs", facing="north", half="bottom", waterlogged="false")
    door_cols = {dx - 1, dx, dx + 1, dx + 2}
    for tx in range(s.x0 + 4, s.x1 - 1, 4):   # tables, clear of the door and the fire
        for tz in (s.z0 + 2, bz - 3):
            if tz <= s.z0 + 1 or tz >= bz - 1 or {tx - 1, tx, tx + 1} & door_cols and tz < s.z0 + 3:
                continue
            if all(b.empty(x, 1, tz) for x in (tx - 1, tx, tx + 1)) and (tx, tz) != (lx, lz):
                table(b, tx, 1, tz, lk.wood, chairs=("east", "west"))
    for x in range(s.x0 + 2, s.x1 - 1, 3):
        hang(b, x, s.z0 + 3, 1, st)
        hang(b, x, bz - 1, 1, st)
    for k in range(1, s.floors):
        room = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, k * s.h + 1, {(lx, lz), (lx, lz + 1), (s.x0 + 1, zm)})
        furnish(room, rng, st, lk, ["bed", "bed", "bed", "chest", "bed", "barrel", "plant"],
                carpet=pick(rng, st.beds))
    top = roof(b, rng, st, s, lk, kind=pick(rng, ("gable", "half_hip", "cross")) if st.key != "ochre" else "hip")
    chimney(b, s.x0 - 1, zm, 1, top + 1, "bricks")
    b.sign(dx + 2, 2, s.z0 - 1, "north", ["2The [tavernadj]~[tavernnoun]", "", "", ""], lk.wood)
    b.set(dx - 1, 3, s.z0 - 1, st.wall_torch, facing="north") if solid(b, dx - 1, 3, s.z0) and \
        b.empty(dx - 1, 3, s.z0 - 1) else None
    flowers(b, rng, st, yard_cells(b, yard), 0.2)
    return b


@gen("chapel", freq="rare", n=3)
def chapel(rng, st):
    """A nave with pews facing the altar at the back, a bell tower over the door: a spire in
    Timber and Ashen, a campanile's low roof in Ochre."""
    W, D = rng.choice((7, 9)), rng.randint(13, 17)
    tw = 5
    b = new("Chapel", "city", W + 2, 1 + tw + D + 1)
    stone = pick(rng, st.stone)
    trim = trim_of(stone)
    m = pick(rng, st.roofs)
    pane = pick(rng, ("white_stained_glass_pane", "yellow_stained_glass_pane", "light_blue_stained_glass_pane",
                      "red_stained_glass_pane", "purple_stained_glass_pane"))
    z0 = tw
    nave = Shell(1, z0, W, z0 + D - 1, 1, h=rng.randint(6, 8))
    b.fill(nave.x0, 0, nave.z0, nave.x1, 0, nave.z1, stone)
    b.ring(nave.x0, nave.z0, nave.x1, nave.z1, 1, nave.top, stone)
    b.ring(nave.x0, nave.z0, nave.x1, nave.z1, 1, 1, pick(rng, st.found))
    pitch = 1 if st.key == "ochre" else 2
    mx = (nave.x0 + nave.x1) // 2
    # tall windows down the sides, buttresses between them
    for z in range(nave.z0 + 2, nave.z1 - 1, 2):
        for x, out in ((nave.x0, "west"), (nave.x1, "east")):
            b.fill(x, 2, z, x, nave.top - 2, z, pane, waterlogged="false")
            ox = x + STEP[out][0]
            b.set(ox, nave.top - 1, z, f"{trim}_stairs", facing=OPPOSITE[out], half="bottom", waterlogged="false")
            if st.key != "ochre" and z + 1 < nave.z1 - 1:
                bh = nave.top - 2
                b.fill(ox, 1, z + 1, ox, bh - 1, z + 1, stone)
                b.set(ox, bh, z + 1, f"{trim}_stairs", facing=OPPOSITE[out], half="bottom", waterlogged="false")
    # a rose window over the altar
    ry = nave.top - 2
    for x in range(mx - 1, mx + 2):
        for y in range(ry - 1, ry + 2):
            if abs(x - mx) + abs(y - ry) <= 1 or nave.x1 - nave.x0 >= 8:
                b.set(x, y, nave.z1, pane, waterlogged="false")
    ridge = gable(b, nave.x0, nave.z0, nave.x1, nave.z1, nave.top, m, along="z", fill=stone, pitch=pitch,
                  extra=(), end_over=1)
    # pews, the aisle, the altar up a step
    wood = pick(rng, st.woods)
    for z in range(nave.z0 + 2, nave.z1 - 4, 2):
        for x in range(nave.x0 + 1, nave.x1):
            if x != mx:
                b.set(x, 1, z, f"{wood}_stairs", facing="south", half="bottom", waterlogged="false")
    az = nave.z1 - 2
    b.fill(nave.x0 + 1, 0, az - 1, nave.x1 - 1, 0, nave.z1 - 1, f"polished_{'deepslate' if st.key == 'ashen' else 'andesite'}")
    for x in range(nave.x0 + 1, nave.x1):
        b.set(x, 1, az - 1, f"{trim}_slab", type="bottom", waterlogged="false")
    b.fill(nave.x0 + 1, 1, az, nave.x1 - 1, 1, nave.z1 - 1, f"{trim}_slab", type="top", waterlogged="false")
    b.set(mx - 1, 1, az, stone)
    b.set(mx, 1, az, stone)
    b.set(mx + 1, 1, az, stone)
    b.set(mx, 2, az, "candle", candles="4", lit="false", waterlogged="false")
    b.set(mx - 1, 2, az, "candle", candles="2", lit="false", waterlogged="false")
    b.set(mx + 1, 2, az, "candle", candles="3", lit="false", waterlogged="false")
    b.set(mx, 2, az - 1, "lectern", facing="north", has_book="false", powered="false")
    for x in (nave.x0 + 1, nave.x1 - 1):
        b.set(x, 2, nave.z1 - 1, st.light, hanging="false", waterlogged="false")
    for z in range(nave.z0 + 3, nave.z1 - 2, 4):
        hang_chain(b, mx, z, nave.top - 1, st.light)
    # the bell tower in front, the door in it
    t = Shell(mx - 2, 1, mx + 2, z0, 1)
    ttop = max(ridge + 3, nave.top + 6)
    bf = ttop - 5   # the belfry floor
    b.fill(t.x0, 0, t.z0, t.x1, 0, t.z1, stone)
    b.ring(t.x0, t.z0, t.x1, t.z1, 1, ttop, stone)
    b.ring(t.x0, t.z0, t.x1, t.z1, 1, 1, pick(rng, st.found))
    b.clear(t.x0 + 1, 1, t.z0 + 1, t.x1 - 1, ttop - 1, t.z1 - 1)
    b.fill(t.x0 + 1, bf, t.z0 + 1, t.x1 - 1, bf, t.z1 - 1, f"{wood}_planks")
    b.fill(t.x0 + 1, ttop, t.z0 + 1, t.x1 - 1, ttop, t.z1 - 1, stone)
    tc = (t.z0 + t.z1) // 2
    for y in range(bf + 1, bf + 4):   # the belfry's openings, two on each face
        for x in (mx - 1, mx + 1):
            for z in (t.z0, t.z1):
                if not (z == t.z1 and y <= ridge):
                    b.set(x, y, z, "air")
        for z in (tc - 1, tc + 1):
            for x in (t.x0, t.x1):
                b.set(x, y, z, "air")
    for x in (mx - 1, mx + 1):   # louvres
        for z in (t.z0, t.z1):
            if b.empty(x, bf + 1, z):
                b.set(x, bf + 1, z, f"{trim}_slab", type="bottom", waterlogged="false")
    for z in (tc - 1, tc + 1):
        for x in (t.x0, t.x1):
            b.set(x, bf + 1, z, f"{trim}_slab", type="bottom", waterlogged="false")
    b.set(mx, ttop - 1, tc, "bell", attachment="ceiling", facing="north", powered="false")
    cornice(b, t, f"{trim}_stairs", ttop)
    if st.key == "ochre":
        hip(b, t.x0, t.z0, t.x1, t.z1, ttop + 1, m, over=0)
    else:
        spire(b, t.x0, t.z0, t.x1, t.z1, ttop + 1, m, pitch=3 if st.key == "ashen" else 2)
        for x, z in ((t.x0, t.z0), (t.x1, t.z0), (t.x0, t.z1), (t.x1, t.z1)):   # pinnacles
            b.set(x, ttop + 1, z, wall_of(stone), waterlogged="false")
    lz = t.z0 + 2
    ladder(b, t.x0 + 1, lz, 1, bf, "east")
    b.set(t.x1 - 1, bf + 1, t.z1 - 1, st.light, hanging="false", waterlogged="false")
    hang_chain(b, mx, tc, 4, st.light)
    b.clear(mx, 1, t.z1, mx, 2, t.z1)
    b.set(mx, 0, t.z1, stone)
    b.door(mx, 1, t.z0, "south", wood)
    b.set(mx, 0, t.z0, stone)
    b.path(mx, 0, mx, t.z0 - 1)
    for y in (4, 5):   # a window over the door
        b.set(mx, y, t.z0, pane, waterlogged="false")
    b.set(mx, 3, t.z0 - 1, f"{trim}_slab", type="top", waterlogged="false")
    return b


@gen("town_hall", freq="very rare", n=2, unique=True)
def town_hall(rng, st):
    """A hall of dressed stone behind a pedimented porch, a clock tower on its roof."""
    W, D = rng.randint(13, 15), rng.randint(11, 13)
    yard = 3
    b = new("Town Hall", "city", W + 2, yard + D + 1)
    stone = pick(rng, st.stone)
    trim = trim_of(stone)
    lk = look(rng, st, timbered=False)
    lk.infill, lk.found = stone, pick(rng, st.found)
    lk.window_tall, lk.jetty, lk.buttress, lk.grille = True, False, False, False
    s = Shell(1, yard, W, yard + D - 1, 2, h=5)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    front_door(b, s, lk, mx - 1, wide=True)
    lx, lz = s.x1 - 1, s.z1 - 1
    ladder(b, lx, lz, 1, s.h, "north", support=stone)
    windows(b, s, lk, skip={(mx - 2, s.z0), (mx - 1, s.z0), (mx, s.z0), (mx + 1, s.z0)}, every=2)
    # the porch: columns, an entablature carrying the city's name, a pediment over it
    px0, px1, pz0, pz1 = mx - 3, mx + 2, s.z0 - 2, s.z0 - 1
    b.fill(px0, 0, pz0, px1, 0, pz1, pick(rng, st.found))
    b.path(mx - 1, 0, mx, pz0 - 1)
    col = wall_of(stone, "stone_brick_wall")
    for x in (px0, px1):
        b.set(x, 1, pz0, stone)
        b.fill(x, 2, pz0, x, s.h - 1, pz0, col, waterlogged="false")
        b.set(x, s.h, pz0, f"{trim}_stairs", facing="south", half="top", waterlogged="false")
    b.fill(px0, s.h + 1, pz0, px1, s.h + 1, pz1, stone)
    pm = Mat(f"{trim}_stairs", f"{trim}_slab", FULL.get(trim, stone))
    draw_roof(b, roof_levels(px0, pz0, px1, pz1, "gable", "z", 0, 1, ends=(True, False)), s.h + 2, pm, 1, stone,
              walls=[(px0, pz0, px1, pz1)])
    b.sign(mx - 1, s.h + 1, pz0 - 1, "north", ["cityname", "", "", ""], lk.wood)
    b.sign(mx, s.h + 1, pz0 - 1, "north", ["Town Hall", "", "", ""], lk.wood)
    hang(b, mx - 1, pz1, 1, st, height=s.h)
    hang(b, mx, pz1, 1, st, height=s.h)
    # the council chamber below, the archive above
    hall = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, 1, {(mx - 1, s.z0 + 1), (mx, s.z0 + 1), (lx, lz), (lx, lz - 1)})
    tz = (s.z0 + s.z1) // 2 + 1
    for x in range(mx - 3, mx + 3):   # the council table and its chairs
        b.set(x, 1, tz, f"{lk.wood}_slab", type="top", waterlogged="false")
        b.set(x, 1, tz - 1, f"{lk.wood}_stairs", facing="north", half="bottom", waterlogged="false")
        b.set(x, 1, tz + 1, f"{lk.wood}_stairs", facing="south", half="bottom", waterlogged="false")
        hall.keep_out(x, tz)
    b.set(mx - 4, 1, tz, f"{lk.wood}_stairs", facing="west", half="bottom", waterlogged="false")
    furnish(hall, rng, st, lk, ["lectern", "shelf", "shelf", "chest", "plant", "plant"], carpet="red")
    up = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, s.h + 1, {(lx, lz), (lx, lz - 1)})
    b.chest(s.x0 + 1, s.h + 1, s.z1 - 1, "north", TREASURE)
    furnish(up, rng, st, lk, ["shelf", "shelf", "shelf", "lectern", "chest", "shelf", "shelf"])
    top = hip(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), fill=stone)
    cornice(b, s, f"{trim}_stairs", s.top - 1)
    # a clock tower on the ridge
    zc = (s.z0 + s.z1) // 2
    b.fill(mx - 1, s.top + 1, zc - 1, mx + 1, top + 4, zc + 1, stone)
    for x, z, f in ((mx, zc - 1, "north"), (mx, zc + 1, "south"), (mx - 1, zc, "west"), (mx + 1, zc, "east")):
        b.set(x, top + 3, z, "white_glazed_terracotta", facing=f)
    for x in (mx - 1, mx + 1):
        for z in (zc - 1, zc + 1):
            b.set(x, top + 5, z, wall_of(stone, "stone_brick_wall"), waterlogged="false")
    tm = pick(rng, st.roofs)
    if st.key == "ochre":
        hip(b, mx - 1, zc - 1, mx + 1, zc + 1, top + 5, tm, over=0)
    else:
        spire(b, mx - 1, zc - 1, mx + 1, zc + 1, top + 5, tm, pitch=3, finial=None)
    return b


@gen("library", freq="very rare", n=1, unique=True)
def library(rng, st):
    W, D = 13, rng.randint(11, 13)
    b = new("Library", "city", W + 2, D + 3)
    stone = pick(rng, st.stone)
    lk = look(rng, st, timbered=False)
    lk.infill = stone
    s = Shell(1, 2, W, D + 1, 1, h=7)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    lk.jetty, lk.buttress = False, False
    front_door(b, s, lk, mx)
    windows(b, s, lk, sides=("north", "east", "west"), skip={(mx, s.z0), (mx - 1, s.z0), (mx + 1, s.z0)}, every=2)
    door_dress(b, rng, st, lk, mx, 1, s.z0, torches=False)
    for x in range(s.x0 + 2, s.x1 - 1, 3):   # stacks
        if abs(x - mx) <= 1:
            continue
        b.fill(x, 1, s.z0 + 3, x, 4, s.z1 - 2, "bookshelf")
    for x in range(s.x0 + 1, s.x1):
        b.fill(x, 1, s.z1 - 1, x, 5, s.z1 - 1, "bookshelf")
    b.set(mx, 1, s.z0 + 3, "lectern", facing="north", has_book="false", powered="false")
    b.set(mx, 1, s.z1 - 3, "enchanting_table")
    for z in range(s.z0 + 2, s.z1, 3):
        hang(b, mx, z, 1, st, height=s.h - 1)
    roof(b, rng, st, s, lk, kind="gable", along="x")
    b.sign(mx + 1, 2, s.z0 - 1, "north", ["Library~of~cityname".replace("cityname", "the City"), "", "", ""], lk.wood)
    return b


@gen("warehouse", freq="average", n=3)
def warehouse(rng, st):
    W, D = rng.randint(9, 12), rng.randint(8, 11)
    b = new("Warehouse", "city", W + 2, D + 3)
    lk = look(rng, st)
    s = Shell(1, 2, W, D + 1, 1, h=6)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    b.clear(mx - 1, 1, s.z0, mx + 1, 3, s.z0)   # the loading door
    b.path(mx - 1, 0, mx + 1, s.z0)
    windows(b, s, lk, sides=("east", "west"), every=3)
    for x in range(s.x0 + 1, s.x1):
        for z in range(s.z0 + 2, s.z1):
            if abs(x - mx) <= 1:
                continue
            r = rng.random()
            stack = rng.randint(1, 3)
            for y in range(1, stack + 1):
                if r < 0.45:
                    b.barrel(x, y, z, [(pick(rng, ("wheat", "coal", "oak_log", "cobblestone", "string", "leather")),
                                        rng.randint(8, 32))] if y == 1 and rng.random() < 0.3 else [])
                elif r < 0.7:
                    b.set(x, y, z, "hay_block", axis=pick(rng, ("x", "z")))
    hang(b, mx, (s.z0 + s.z1) // 2, 1, st, height=s.h)
    roof(b, rng, st, s, lk, kind="gable")
    return b


@gen("forge", freq="average", n=3)
def forge(rng, st):
    """An open-sided smithy: posts, a roof, the forge and anvil."""
    W, D = rng.randint(7, 9), rng.randint(6, 7)
    b = new("Forge", "city", W + 2, D + 3)
    lk = look(rng, st)
    s = Shell(1, 2, W, D + 1, 1, h=4)
    b.fill(s.x0, 0, s.z0, s.x1, 0, s.z1, PATH)
    for x, z in ((s.x0, s.z0), (s.x1, s.z0), (s.x0, s.z1), (s.x1, s.z1)):
        b.fill(x, 1, z, x, s.top - 1, z, lk.log, axis="y")
    b.fill(s.x0, 1, s.z1, s.x1, s.top - 1, s.z1, lk.found)
    b.fill(s.x0 + 1, 1, s.z1 - 1, s.x0 + 3, 2, s.z1 - 1, lk.found)
    b.set(s.x0 + 2, 1, s.z1 - 1, "lava_cauldron")
    b.set(s.x0 + 2, 2, s.z1 - 1, "air")
    b.set(s.x0 + 1, 1, s.z1 - 1, "blast_furnace", facing="north", lit="true")
    b.set(s.x0 + 3, 1, s.z1 - 1, "smoker", facing="north", lit="false")
    b.set(s.x0 + 2, 1, s.z1 - 3, "anvil", facing="east")
    b.set(s.x1 - 1, 1, s.z1 - 1, "grindstone", face="floor", facing="north")
    b.set(s.x1 - 2, 1, s.z1 - 1, "smithing_table")
    b.chest(s.x1 - 1, 1, s.z0 + 1, "west")
    b.set(s.x1 - 1, 1, s.z0 + 2, "water_cauldron", level="3")
    chimney(b, s.x0 + 2, s.z1, s.top, s.top + 4, "bricks")
    b.fill(s.x0, s.top, s.z0, s.x1, s.top, s.z1, f"{lk.wood}_planks")
    shed(b, s.x0, s.z0, s.x1, s.z1, s.top + 1, pick(rng, st.roofs), high="south")
    hang(b, (s.x0 + s.x1) // 2, (s.z0 + s.z1) // 2, 1, st, height=s.h)
    return b


@gen("watchtower", freq="rare", n=2)
def watchtower(rng, st):
    b = new("Watchtower", "city", 7, 7)
    stone = pick(rng, st.stone)
    h = rng.randint(12, 16)
    b.fill(1, 0, 1, 5, 0, 5, stone)
    b.ring(1, 1, 5, 5, 1, h, stone)
    b.fill(0, h, 0, 6, h, 6, stone)
    for x in range(0, 7):
        for z in range(0, 7):
            if (x in (0, 6) or z in (0, 6)) and (x + z) % 2 == 0:
                b.set(x, h + 1, z, stone)
    b.clear(2, h, 2, 4, h, 4)
    b.fill(2, h, 2, 4, h, 4, f"{pick(rng, st.woods)}_planks")
    ladder(b, 3, 4, 1, h, "north")
    b.door(3, 1, 1, "south", pick(rng, st.woods))
    b.set(3, 0, 1, stone)
    b.path(3, 0, 3, 0)
    for y in range(4, h - 1, 4):
        for x, z in ((3, 1), (1, 3), (5, 3)):
            b.set(x, y, z, "iron_bars", waterlogged="false")
    b.set(3, h + 1, 3, st.light, hanging="false", waterlogged="false")
    b.set(2, 2, 3, st.wall_torch, facing="east")
    return b


# ── Squares, gardens and smaller things ───────────────────────────────────

@gen("market", freq="common", n=3)
def market(rng, st):
    S = rng.randint(11, 15)
    b = new("Market", "city", S, S)
    b.fill(0, 0, 0, S - 1, 0, S - 1, PATH)
    wood = pick(rng, st.woods)
    goods = ["melon", "pumpkin", "hay_block", "barrel", "composter", "carved_pumpkin", "barrel", "cake"]
    for sx in range(1, S - 3, 4):
        for sz in (1, S - 3):
            if rng.random() < 0.15:
                continue
            for x, z in ((sx, sz), (sx + 2, sz), (sx, sz + 1), (sx + 2, sz + 1)):
                b.fill(x, 1, z, x, 2, z, f"{wood}_fence", waterlogged="false")
            for x in range(sx, sx + 3):   # a striped awning, sloping to the customers
                front, back = (sz, sz + 1) if sz == 1 else (sz + 1, sz)
                b.set(x, 3, back, WOOL if x % 2 else "white_wool")
                b.set(x, 3, front, WOOL if x % 2 else "white_wool")
            for x in range(sx, sx + 3):
                g = pick(rng, goods)
                z = sz + (1 if sz == 1 else 0)
                if g == "barrel":
                    b.barrel(x, 1, z, [(pick(rng, ("apple", "bread", "carrot", "potato", "beetroot", "cookie")), rng.randint(2, 12))])
                elif g == "hay_block":
                    b.set(x, 1, z, g, axis="y")
                elif g == "composter":
                    b.set(x, 1, z, g, level=str(rng.randint(0, 7)))
                elif g == "cake":
                    b.set(x, 1, z, f"{wood}_slab", type="top", waterlogged="false")
                    b.set(x, 2, z, "cake", bites="0")
                elif g == "carved_pumpkin":
                    b.set(x, 1, z, g, facing="north" if sz != 1 else "south")
                else:
                    b.set(x, 1, z, g)
    c = S // 2
    lamp_post(b, c, c, st, wood)
    bench(b, c - 2, c + 2, "north", wood)
    bench(b, c + 1, c - 2, "south", wood)
    return b


@gen("fountain", freq="average", n=3)
def fountain(rng, st):
    """A basin with its corners cut, a spout on a pillar spilling into a bowl."""
    S = rng.choice((7, 9, 11))
    b = new("Fountain", "city", S, S)
    b.fill(0, 0, 0, S - 1, 0, S - 1, PATH)
    stone = pick(rng, st.stone)
    rim = slab_of(stone)
    r = S // 2 - 1
    c = S // 2
    cut = r // 2 + (1 if r >= 3 else 0)

    def basin(x, z):
        dx, dz = abs(x - c), abs(z - c)
        return max(dx, dz) <= r and dx + dz <= 2 * r - cut

    for x in range(c - r, c + r + 1):
        for z in range(c - r, c + r + 1):
            if not basin(x, z):
                continue
            b.set(x, 0, z, stone)
            edge = any(not basin(x + a, z + e) for a, e in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                b.set(x, 1, z, rim, type="bottom", waterlogged="false")
            else:
                b.set(x, 1, z, "water", level="0")
    b.fill(c, 1, c, c, 3, c, stone)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        b.set(c + dx, 3, c + dz, rim, type="bottom", waterlogged="true")
    b.set(c, 4, c, wall_of(stone, "stone_brick_wall"), waterlogged="false")
    b.set(c, 5, c, "water", level="0")
    if S >= 9:
        for x, z in ((0, 0), (S - 1, 0), (0, S - 1), (S - 1, S - 1)):
            lamp_post(b, x, z, st, pick(rng, st.woods))
        wood = pick(rng, st.woods)
        bench(b, c - 1, S - 1, "north", wood, n=3)
        bench(b, c + 1, 0, "south", wood, n=3)
    return b


@gen("well", freq="rare", n=2)
def well(rng, st):
    b = new("Well", "city", 5, 5)
    stone = pick(rng, st.found)
    wood = pick(rng, st.woods)
    b.fill(0, 0, 0, 4, 0, 4, PATH)
    b.fill(1, -8, 1, 3, 1, 3, stone)
    b.fill(2, -7, 2, 2, -1, 2, "water", level="0")
    b.set(2, 0, 2, "air")
    b.set(2, 1, 2, "air")
    for x, z in ((1, 1), (3, 1), (1, 3), (3, 3)):
        b.fill(x, 2, z, x, 3, z, f"{wood}_fence", waterlogged="false")
    hip(b, 1, 1, 3, 3, 4, pick(rng, st.roofs), over=1)
    b.set(2, 3, 2, "iron_chain", axis="y", waterlogged="false")
    return b


@gen("garden", freq="common", n=4)
def garden(rng, st):
    S, T = rng.randint(9, 15), rng.randint(9, 13)
    b = new("Garden", "city", S, T)
    hedge = {"timber": "oak_leaves", "ochre": "azalea_leaves", "ashen": "dark_oak_leaves"}[st.key]
    wood = pick(rng, st.woods)
    for x in range(S):
        for z in range(T):
            if x in (0, S - 1) or z in (0, T - 1):
                if not (z == 0 and abs(x - S // 2) <= 1):
                    b.set(x, 1, z, hedge, persistent="true", distance="1", waterlogged="false")
    b.path(S // 2, 0, S // 2, T - 2)
    b.path(1, T // 2, S - 2, T // 2)
    for _ in range(rng.randint(1, 3)):
        tx, tz = rng.randint(2, S - 3), rng.randint(2, T - 3)
        if abs(tx - S // 2) > 1 and abs(tz - T // 2) > 1:
            tree(b, rng, tx, tz, pick(rng, st.trees), size=rng.randint(3, 5))
    flowers(b, rng, st, [(x, z) for x in range(1, S - 1) for z in range(1, T - 1)], 0.35)
    bench(b, S // 2 + 1, T // 2 + 1, "north", wood)
    lamp_post(b, S // 2 - 1, T // 2 - 1, st, wood)
    return b


@gen("park", freq="average", n=3)
def park(rng, st):
    S = rng.randint(14, 20)
    b = new("Park", "city", S, S)
    wood = pick(rng, st.woods)
    c = S // 2
    for i in range(S):   # a ring path and a cross
        b.set(i, 0, c, PATH)
        b.set(c, 0, i, PATH)
    b.path(2, 2, S - 3, 2)
    b.path(2, S - 3, S - 3, S - 3)
    b.path(2, 2, 2, S - 3)
    b.path(S - 3, 2, S - 3, S - 3)
    for qx, qz in ((5, 5), (S - 6, 5), (5, S - 6), (S - 6, S - 6)):
        if rng.random() < 0.8:
            tree(b, rng, qx, qz, pick(rng, st.trees))
    flowers(b, rng, st, [(x, z) for x in range(S) for z in range(S)], 0.15)
    for x, z in ((c - 2, c - 2), (c + 2, c + 2)):
        lamp_post(b, x, z, st, wood)
    bench(b, c + 1, c - 2, "south", wood)
    bench(b, c - 2, c + 2, "north", wood)
    if rng.random() < 0.5:   # a statue in the middle
        stone = pick(rng, st.stone)
        b.set(c, 1, c, stone)
        b.set(c, 2, c, stone)
        b.set(c, 3, c, pick(rng, ("carved_pumpkin", "chiseled_stone_bricks", "lodestone", "jack_o_lantern")))
        if b.get(c, 3, c).endswith("lantern") or b.get(c, 3, c) == "carved_pumpkin":
            b.set(c, 3, c, b.get(c, 3, c), facing="north")
    return b


@gen("graveyard", freq="rare", n=3)
def graveyard(rng, st):
    S, T = rng.randint(9, 13), rng.randint(9, 13)
    b = new("Graveyard", "city", S, T)
    wall = pick(rng, ("cobblestone_wall", "mossy_cobblestone_wall", "cobbled_deepslate_wall", "stone_brick_wall"))
    for x in range(S):
        for z in range(T):
            if (x in (0, S - 1) or z in (0, T - 1)) and not (z == 0 and abs(x - S // 2) <= 1):
                b.set(x, 1, z, wall, waterlogged="false")
    b.set(S // 2 - 2, 2, 0, st.light, hanging="false", waterlogged="false")
    b.set(S // 2 + 2, 2, 0, st.light, hanging="false", waterlogged="false")
    b.path(S // 2, 0, S // 2, T - 2)
    stone = pick(rng, ("mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks", "chiseled_tuff", "polished_andesite"))
    for z in range(3, T - 1, 3):
        for x in range(2, S - 2, 2):
            if abs(x - S // 2) <= 1 or rng.random() < 0.15:
                continue
            b.set(x, 1, z, stone)
            if rng.random() < 0.5:
                b.set(x, 2, z, f"{pick(rng, ('stone_brick', 'mossy_stone_brick'))}_slab", type="bottom", waterlogged="false")
            b.sign(x, 1, z - 1, "north", ["1[epitaph]", "", "", ""], pick(rng, st.woods))
            b.set(x, 0, z + 1, "coarse_dirt" if st.key != "ashen" else "podzol")
    if rng.random() < 0.6:
        tree(b, rng, S - 3, T - 3, pick(rng, st.trees), size=4)
    return b


@gen("noticeboard", freq="average", n=2)
def noticeboard(rng, st):
    """A board of notices between two posts under a little roof."""
    b = new("Notice Board", "city", 5, 3)
    wood = pick(rng, st.woods)
    log = f"{wood}_log" if wood != "pale_oak" else "stripped_pale_oak_log"
    b.path(0, 0, 4, 1)
    for x in (0, 4):
        b.fill(x, 1, 2, x, 3, 2, log, axis="y")
    b.fill(1, 1, 2, 3, 1, 2, f"{wood}_fence", waterlogged="false")
    b.fill(1, 2, 2, 3, 3, 2, f"{wood}_planks")
    b.fill(0, 4, 2, 4, 4, 2, log, axis="x")
    b.fill(0, 4, 1, 4, 4, 1, f"{wood}_stairs", facing="south", half="bottom", waterlogged="false")
    b.fill(0, 5, 2, 4, 5, 2, f"{wood}_slab", type="bottom", waterlogged="false")
    for x, y in ((1, 3), (2, 2), (3, 3)):
        b.sign(x, y, 1, "north", ["2[notice]", "", "", ""], wood)
    return b


@gen("monument", freq="very rare", n=2)
def monument(rng, st):
    """On a stepped plinth with its inscription: an obelisk, a statue holding up a lamp, or a
    column with a bell. Lamps on posts at the corners."""
    b = new("Monument", "city", 7, 7)
    stone = pick(rng, st.stone)
    trim = trim_of(stone)
    b.fill(0, 0, 0, 6, 0, 6, PATH)
    b.fill(1, 1, 1, 5, 1, 5, stone)
    for x in range(1, 6):   # steps all round
        b.set(x, 1, 1, f"{trim}_stairs", facing="south", half="bottom", waterlogged="false")
        b.set(x, 1, 5, f"{trim}_stairs", facing="north", half="bottom", waterlogged="false")
    for z in range(2, 5):
        b.set(1, 1, z, f"{trim}_stairs", facing="east", half="bottom", waterlogged="false")
        b.set(5, 1, z, f"{trim}_stairs", facing="west", half="bottom", waterlogged="false")
    b.fill(2, 2, 2, 4, 2, 4, stone)
    b.sign(3, 2, 1, "north", ["1[monument]", "", "", ""], pick(rng, st.woods))
    kind = pick(rng, ("obelisk", "statue", "column"))
    chis = {"stone_bricks": "chiseled_stone_bricks", "deepslate_bricks": "chiseled_deepslate",
            "polished_blackstone_bricks": "chiseled_polished_blackstone", "tuff_bricks": "chiseled_tuff_bricks",
            "smooth_sandstone": "chiseled_sandstone", "cut_sandstone": "chiseled_sandstone",
            "mud_bricks": "chiseled_sandstone", "cobblestone": "chiseled_stone_bricks",
            "andesite": "polished_andesite"}.get(stone, "chiseled_stone_bricks")
    b.set(3, 3, 3, chis)
    if kind == "obelisk":
        h = rng.randint(6, 9)
        b.fill(3, 4, 3, 3, 3 + h, 3, stone)
        for d, (dx, dz) in STEP.items():   # a flared foot
            b.set(3 + dx, 3, 3 + dz, f"{trim}_stairs", facing=OPPOSITE[d], half="bottom", waterlogged="false")
        top = pick(rng, ("gold_block", "lantern", "chiseled_quartz_block", "lightning_rod"))
        extra = {"lantern": {"hanging": "false", "waterlogged": "false"},
                 "lightning_rod": {"facing": "up", "powered": "false", "waterlogged": "false"}}.get(top, {})
        b.set(3, 4 + h, 3, top if top != "lantern" else st.light, **extra)
    elif kind == "statue":
        leg = wall_of(stone, "stone_brick_wall")
        b.set(3, 4, 3, leg, waterlogged="false")
        b.set(3, 5, 3, stone)
        b.set(2, 5, 3, f"{trim}_stairs", facing="east", half="top", waterlogged="false")
        b.set(4, 5, 3, leg, waterlogged="false")
        b.set(4, 6, 3, leg, waterlogged="false")
        b.set(4, 7, 3, st.light, hanging="false", waterlogged="false")
        head = pick(rng, ("carved_pumpkin", chis, "skeleton_skull" if st.key == "ashen" else chis))
        if head == "carved_pumpkin":
            b.set(3, 6, 3, head, facing="north")
        elif head == "skeleton_skull":
            b.set(3, 6, 3, head, rotation="8", powered="false")
        else:
            b.set(3, 6, 3, head)
    else:
        h = rng.randint(4, 6)
        b.fill(3, 4, 3, 3, 3 + h, 3, wall_of(stone, "stone_brick_wall"), waterlogged="false")
        b.fill(2, 4 + h, 2, 4, 4 + h, 4, f"{trim}_slab", type="bottom", waterlogged="false")
        b.set(3, 4 + h, 3, stone)
        b.set(3, 3 + h, 3, wall_of(stone, "stone_brick_wall"), waterlogged="false")
        b.fill(3, 5 + h, 3, 3, 5 + h, 3, stone)
        b.set(3, 6 + h, 3, "bell", attachment="floor", facing="north", powered="false")
    wood = pick(rng, st.woods)
    for x, z in ((0, 0), (6, 0), (0, 6), (6, 6)):
        lamp_post(b, x, z, st, wood, h=2)
    return b


@gen("facade", freq="very rare", n=2)
def facade(rng, st):
    """The front of a house and nothing behind it, propped up from behind like a stage flat:
    a crow-stepped gable, a false front with a cornice, or a plain gable."""
    W = rng.randint(7, 9)
    b = new("House", "city", W + 2, 5)
    lk = look(rng, st)
    lk.jetty, lk.buttress = False, False
    s = Shell(1, 1, W, 1, 2)
    shell(b, s, lk)
    for z in (s.z0 - 1, s.z0 + 1):   # a one-block wall: its "floors" spill either side
        b.clear(s.x0, 1, z, s.x1, s.top + 1, z)
    b.fill(s.x0, 0, s.z0 - 1, s.x1, 0, s.z0 - 1, GROUND)
    b.fill(s.x0, 0, s.z0 + 1, s.x1, 0, s.z1 + 3, GROUND)
    dx = front_door(b, s, lk)
    b.set(dx, 0, s.z0 + 1, PATH)
    windows(b, s, lk, sides=("north",), skip={(dx, s.z0), (dx + 1, s.z0), (dx - 1, s.z0)}, floors=[0], every=2)
    windows(b, s, lk, sides=("north",), floors=[1], every=2)
    door_dress(b, rng, st, lk, dx, 1, s.z0, torches=False)
    top = s.top
    mx = (s.x0 + s.x1) / 2
    kind = pick(rng, ("stepped", "false_front", "gable"))
    edge = lk.found if not lk.timbered else lk.infill
    t = trim_of(edge if not edge.endswith("terracotta") else lk.found)
    if kind == "stepped":
        half = (s.x1 - s.x0) / 2
        for x in range(s.x0, s.x1 + 1):
            d = abs(x - mx)
            h = top + 1 + int((half - d) // 1.5) * 1
            b.fill(x, top + 1, s.z0, x, h, s.z0, edge)
            b.set(x, h + 1, s.z0, f"{t}_slab", type="bottom", waterlogged="false")
        cx = int(mx)
        if b.get(cx, top + 2, s.z0) == edge:
            b.set(cx, top + 2, s.z0, lk.pane, waterlogged="false")
    elif kind == "false_front":
        b.fill(s.x0, top + 1, s.z0, s.x1, top + 3, s.z0, edge)
        for x in range(s.x0, s.x1 + 1):
            b.set(x, top + 1, s.z0 - 1, f"{t}_stairs", facing="south", half="top", waterlogged="false")
            b.set(x, top + 4, s.z0, f"{t}_slab", type="bottom", waterlogged="false")
        b.sign(int(mx), top + 2, s.z0 - 1, "north", "[house]", lk.wood)
    else:
        m = pick(rng, st.roofs)
        lv = roof_levels(s.x0, s.z0, s.x1, s.z0 + 1, "gable", "z", 1, 1, ends=(True, False))
        lv = {k: v for k, v in lv.items() if k[1] <= s.z0}
        draw_roof(b, lv, top, m, 2 if rng.random() < st.pitch2 else 1, lk.infill, walls=[(s.x0, s.z0, s.x1, s.z0)])
    # the props behind: posts, and rails tying them to the wall
    for x in edge_points(s.x0 + 1, s.x1 - 1, 3):
        if x == dx:
            x += 1
        b.fill(x, 1, s.z0 + 2, x, top - 1, s.z0 + 2, f"{lk.wood}_fence", waterlogged="false")
        for y in (2, top - 2):
            b.set(x, y, s.z0 + 1, f"{lk.wood}_fence", waterlogged="false")
        b.set(x, 1, s.z0 + 3, f"{lk.wood}_stairs", facing="north", half="bottom", waterlogged="false")
    if kind != "false_front":
        b.sign(dx + 1, 2, s.z0 - 1, "north", "[house]", lk.wood)
    return b


@gen("ruin", freq="rare", n=3)
def ruin(rng, st):
    """A house fallen in: the roof gone, the walls broken down to a ragged line, rubble inside."""
    b = house(rng, st)
    b.name = "Ruin"
    cut, h = {}, rng.randint(3, 6)
    for x in range(b.w):   # a ragged height per column, wandering along the walls
        h = max(1, min(8, h + rng.randint(-2, 2)))
        for z in range(b.l):
            cut[(x, z)] = max(1, h + rng.randint(-1, 1) - (z * 3) // b.l)
    for (x, y, z), (n, p) in list(b.blocks.items()):
        if y <= 0:
            continue
        if y > cut[(x, z)] or n.endswith(("_stairs", "_door", "_bed", "pane", "_slab", "lantern", "campfire", "sign")) \
                or n in ("chest", "ladder"):
            b.set(x, y, z, "air")
        elif n in ("cobblestone", "stone_bricks", "deepslate_bricks", "polished_blackstone_bricks", "mud_bricks") \
                and rng.random() < 0.5:
            b.set(x, y, z, {"cobblestone": "mossy_cobblestone", "stone_bricks": "cracked_stone_bricks",
                            "deepslate_bricks": "cracked_deepslate_bricks",
                            "polished_blackstone_bricks": "cracked_polished_blackstone_bricks",
                            "mud_bricks": "packed_mud"}[n])
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if y == 0 and n.endswith("_planks") and b.empty(x, 1, z) and rng.random() < 0.25:
            b.set(x, 1, z, pick(rng, ("gravel", "cobblestone", "cobweb", "moss_carpet")))
    for (x, y, z) in [k for k, v in b.blocks.items() if v[0] == "air" and 1 <= k[1] < cut[(k[0], k[2])]]:
        if rng.random() < 0.02:
            b.set(x, y, z, "cobweb")
    b.entities = {k: v for k, v in b.entities.items() if b.get(*k) in ("barrel",)}
    settle(b)
    return b


HANGING = ("_wall_torch", "_wall_sign", "wall_torch", "ladder", "_trapdoor", "lantern", "_bed", "_door", "potted_",
           "_carpet", "_pressure_plate", "candle", "flower_pot")


def settle(b):
    """Drops what a ruin leaves hanging: torches, signs and ladders with nothing behind them,
    doors and beds missing a half, and whatever no longer touches the ground (18-neighbour)."""
    changed = True
    while changed:
        changed = False
        for (x, y, z), (n, p) in list(b.blocks.items()):
            if y < 1 or n == "air":
                continue
            f = p.get("facing")
            gone = False
            if n.endswith(("wall_torch", "_wall_sign")) or n == "ladder":
                dx, dz = STEP[f]
                gone = not solid(b, x - dx, y, z - dz) and b.get(x - dx, y, z - dz) != "ladder"
            elif n.endswith("_door"):
                gone = b.get(x, y + (1 if p.get("half") == "lower" else -1), z) != n
            elif n.endswith("_bed"):
                dx, dz = STEP[f]
                s_ = 1 if p.get("part") == "foot" else -1
                gone = b.get(x + s_ * dx, y, z + s_ * dz) != n
            elif n.endswith("lantern") and p.get("hanging") == "true":
                gone = b.empty(x, y + 1, z)
            elif n.startswith("potted_") or n.endswith(("_carpet", "_pressure_plate", "candle")) or \
                    (n.endswith("lantern") and p.get("hanging") == "false"):
                gone = b.empty(x, y - 1, z)
            if gone:
                b.set(x, y, z, "air")
                changed = True
    nodes = {k for k, v in b.blocks.items() if v[0] != "air"}
    seen = {k for k in nodes if k[1] <= 0}
    todo = list(seen)
    steps = [(a, c, e) for a in (-1, 0, 1) for c in (-1, 0, 1) for e in (-1, 0, 1) if 0 < abs(a) + abs(c) + abs(e) <= 2]
    while todo:
        x, y, z = todo.pop()
        for a, c, e in steps:
            k = (x + a, y + c, z + e)
            if k in nodes and k not in seen:
                seen.add(k)
                todo.append(k)
    for k in nodes - seen:
        b.set(*k, "air")


# ── Farms (outside the north wall) ────────────────────────────────────────

def farmland(b, x, z, crop, rng):
    b.set(x, 0, z, "farmland", moisture="7")
    ages = {"beetroots": 3}
    b.set(x, 1, z, crop, age=str(rng.randint(ages.get(crop, 7) - 3, ages.get(crop, 7))))


@gen("field", kind="farm", freq="very common", n=6)
def field(rng, st):
    S, T = rng.randint(9, 15), rng.randint(9, 15)
    b = new("Field", "farm", S, T)
    crop = pick(rng, st.crops)
    wood = pick(rng, st.woods)
    for x in range(S):
        for z in range(T):
            if x in (0, S - 1) or z in (0, T - 1):
                b.set(x, 0, z, f"{wood}_log", axis="x" if z in (0, T - 1) else "z")
            elif (x - 1) % 5 == 2:
                b.set(x, 0, z, "water", level="0")
            else:
                farmland(b, x, z, crop, rng)
    if rng.random() < 0.4:   # a scarecrow
        x, z = rng.randint(2, S - 3), rng.randint(2, T - 3)
        if b.get(x, 0, z) == "farmland":
            b.set(x, 1, z, f"{wood}_fence", waterlogged="false")
            b.set(x, 2, z, "hay_block", axis="y")
            b.set(x, 3, z, "carved_pumpkin", facing="north")
    return b


@gen("pumpkin_patch", kind="farm", freq="common", n=3)
def pumpkin_patch(rng, st):
    S, T = rng.randint(9, 13), rng.randint(9, 13)
    b = new("Pumpkin Patch", "farm", S, T)
    fruit = rng.choice(("pumpkin", "melon"))
    for z in range(1, T - 1):
        b.set(S // 2, 0, z, "water", level="0")
        for x in range(1, S - 1):
            if x == S // 2:
                continue
            if abs(x - S // 2) % 3 == 1:
                b.set(x, 0, z, "farmland", moisture="7")
                if rng.random() < 0.6:
                    side = "east" if x > S // 2 else "west"
                    dx = 1 if x > S // 2 else -1
                    b.set(x, 1, z, f"attached_{fruit}_stem", facing=side)
                    b.set(x + dx, 1, z, fruit)
                else:
                    b.set(x, 1, z, f"{fruit}_stem", age=str(rng.randint(2, 7)))
    return b


@gen("orchard", kind="farm", freq="common", n=4)
def orchard(rng, st):
    S = rng.randint(13, 16)
    b = new("Orchard", "farm", S, S)
    wood = pick(rng, st.woods)
    kind = pick(rng, st.trees)
    for i in range(S):
        for x, z in ((i, 0), (i, S - 1), (0, i), (S - 1, i)):
            if not (z == 0 and x == S // 2):
                b.set(x, 1, z, f"{wood}_fence", waterlogged="false")
    b.set(S // 2, 1, 0, f"{wood}_fence_gate", facing="north", in_wall="false", open="false", powered="false")
    for x in range(3, S - 2, 5):
        for z in range(3, S - 2, 5):
            tree(b, rng, x, z, kind, size=rng.randint(3, 4))
    flowers(b, rng, st, [(x, z) for x in range(1, S - 1) for z in range(1, S - 1)], 0.1)
    return b


@gen("pen", kind="farm", freq="very common", n=4)
def pen(rng, st):
    S, T = rng.randint(10, 15), rng.randint(10, 14)
    b = new("Animal Pen", "farm", S, T)
    wood = pick(rng, st.woods)
    for x in range(S):
        for z in range(T):
            if x in (0, S - 1) or z in (0, T - 1):
                b.set(x, 1, z, f"{wood}_fence", waterlogged="false")
    b.set(S // 2, 1, 0, f"{wood}_fence_gate", facing="north", in_wall="false", open="false", powered="false")
    for x in range(2, 5):   # a trough
        b.set(x, 1, T - 2, "water_cauldron", level="3") if x == 3 else b.set(x, 1, T - 2, f"{wood}_slab", type="bottom", waterlogged="false")
    for _ in range(rng.randint(2, 5)):
        x, z = rng.randint(1, S - 2), rng.randint(1, T - 3)
        if b.empty(x, 1, z):
            b.set(x, 1, z, "hay_block", axis=pick(rng, ("x", "z", "y")))
    # a lean-to shelter in the far corner
    sx0, sz0 = S - 6, T - 5
    for x, z in ((sx0, sz0), (S - 2, sz0)):
        b.fill(x, 1, z, x, 2, z, f"{wood}_fence", waterlogged="false")
    b.fill(sx0, 1, T - 2, S - 2, 2, T - 2, f"{wood}_planks")
    shed(b, sx0, sz0, S - 2, T - 2, 3, mat(wood), high="south", over=0)
    for x in range(1, S - 1):
        for z in range(1, T - 1):
            if b.get(x, 0, z) == GROUND and rng.random() < 0.3:
                b.set(x, 0, z, "coarse_dirt")
    return b


@gen("barn", kind="farm", freq="common", n=3)
def barn(rng, st):
    W, D = rng.randint(9, 11), rng.randint(11, 14)
    b = new("Barn", "farm", W + 2, D + 3)
    lk = look(rng, st, timbered=True)
    lk.infill = pick(rng, ("red_terracotta", f"{lk.wood}_planks", "spruce_planks", "brown_terracotta"))
    s = Shell(1, 2, W, D + 1, 1, h=6)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    b.clear(mx - 1, 1, s.z0, mx + 1, 4, s.z0)
    b.path(mx - 1, 0, mx + 1, s.z0)
    b.fill(s.x0 + 1, 0, s.z0 + 1, s.x1 - 1, 0, s.z1 - 1, "coarse_dirt")
    for z in range(s.z0 + 2, s.z1 - 1, 3):   # stalls
        for x in (s.x0 + 3, s.x1 - 3):
            b.fill(x, 1, z, x, 1, z + 1, f"{lk.wood}_fence", waterlogged="false")
    b.fill(s.x0 + 1, 4, s.z1 - 4, s.x1 - 1, 4, s.z1 - 1, f"{lk.wood}_planks")   # the loft
    for x in range(s.x0 + 1, s.x1):
        for z in range(s.z1 - 3, s.z1):
            if rng.random() < 0.7:
                b.set(x, 5, z, "hay_block", axis="x")
    ladder(b, s.x0 + 1, s.z1 - 4, 1, 4, "east", support=lk.infill)
    b.set(s.x0 + 1, 5, s.z1 - 4, "air")
    for _ in range(4):
        x, z = rng.randint(s.x0 + 1, s.x1 - 1), rng.randint(s.z0 + 1, s.z1 - 5)
        if b.empty(x, 1, z) and abs(x - mx) > 1:
            b.set(x, 1, z, "hay_block", axis="y")
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), along="z", fill=lk.infill, pitch=1)
    for z in range(s.z0 + 2, s.z1 - 4, 4):   # lanterns on chains from the ridge, one over the loft
        for x in (s.x0 + 2, s.x1 - 2):
            hang_chain(b, x, z, 4, st.light)
    hang_chain(b, mx, s.z1 - 2, 7, st.light)
    return b


@gen("windmill", kind="farm", freq="rare", n=2, unique=True)
def windmill(rng, st):
    """A tower mill: three floors, the sails on the front turning about a hub, each a stock with
    canvas on its trailing side and a lattice edge."""
    b = new("Windmill", "farm", 13, 9)
    lk = look(rng, st, timbered=rng.random() < 0.5)
    lk.jetty, lk.buttress, lk.sill = False, False, ""
    s = Shell(4, 2, 8, 6, 3, h=4)
    shell(b, s, lk)
    front_door(b, s, lk, 6)
    ladder(b, 7, 5, 1, 2 * s.h, "north", support=lk.infill)
    windows(b, s, lk, skip={(6, s.z0)}, every=2)
    keep = {(6, 3), (7, 5), (7, 4)}
    furnish(Room(b, 5, 3, 7, 5, 1, keep), rng, st, lk, ["barrel", "hay_block", "chest"])
    furnish(Room(b, 5, 3, 7, 5, s.h + 1, keep), rng, st, lk, ["barrel", "barrel", "hay_block"])
    furnish(Room(b, 5, 3, 7, 5, 2 * s.h + 1, keep), rng, st, lk, ["grindstone", "composter"])
    hip(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), pitch=2 if st.key != "ochre" else 1, fill=lk.infill)
    hy, hx = s.top - 2, 6
    b.set(hx, hy, s.z0 - 1, f"{lk.wood}_log" if lk.wood != "pale_oak" else "stripped_pale_oak_log", axis="z")
    b.set(hx, hy, 0, f"{lk.wood}_planks")
    stock = f"stripped_{lk.wood}_log"
    for ux, uy in ((1, 0), (0, 1), (-1, 0), (0, -1)):
        px, py = uy, -ux   # the trailing side: the sails turn clockwise seen from the street
        for i in range(1, 6):
            x, y = hx + ux * i, hy + uy * i
            b.set(x, y, 0, stock, axis="x" if ux else "y")
            if i >= 2:
                b.set(x + px, y + py, 0, "white_wool")
                b.set(x + 2 * px, y + 2 * py, 0, f"{lk.wood}_fence", waterlogged="false")
    return b


@gen("beehives", kind="farm", freq="average", n=2)
def beehives(rng, st):
    S, T = rng.randint(9, 12), rng.randint(9, 12)
    b = new("Apiary", "farm", S, T)
    wood = pick(rng, st.woods)
    for x in range(1, S - 1):
        for z in range(1, T - 1):
            if rng.random() < 0.55:
                b.set(x, 1, z, pick(rng, ("dandelion", "poppy", "cornflower", "oxeye_daisy", "allium", "azure_bluet", "lily_of_the_valley")))
    for x in range(2, S - 2, 3):
        for z in range(2, T - 2, 3):
            b.set(x, 1, z, f"{wood}_fence", waterlogged="false")
            b.set(x, 2, z, "beehive", facing=pick(rng, ("north", "south", "east", "west")), honey_level=str(rng.randint(0, 5)))
    return b


@gen("haystacks", kind="farm", freq="common", n=2)
def haystacks(rng, st):
    S, T = rng.randint(8, 11), rng.randint(8, 11)
    b = new("Hay Field", "farm", S, T)
    for x in range(S):
        for z in range(T):
            if rng.random() < 0.3:
                b.set(x, 1, z, "short_grass" if st.key != "ochre" else "short_dry_grass")
    for _ in range(rng.randint(2, 4)):
        cx, cz = rng.randint(2, S - 3), rng.randint(2, T - 3)
        for x in range(cx - 1, cx + 2):
            for z in range(cz - 1, cz + 2):
                b.set(x, 1, z, "hay_block", axis="y")
        b.set(cx, 2, cz, "hay_block", axis="y")
    return b


@gen("vineyard", kind="farm", freq="very common", n=3, styles=("ochre",))
def vineyard(rng, st):
    S, T = rng.randint(10, 15), rng.randint(10, 14)
    b = new("Vineyard", "farm", S, T)
    wood = pick(rng, st.woods)
    for x in range(1, S - 1, 2):
        for z in range(1, T - 1):
            b.set(x, 0, z, "coarse_dirt")
            if z in (1, T - 2):
                b.fill(x, 1, z, x, 2, z, f"{wood}_fence", waterlogged="false")
            else:
                b.set(x, 1, z, "sweet_berry_bush", age=str(rng.randint(1, 3)))
                if rng.random() < 0.5:
                    b.set(x, 2, z, "azalea_leaves", persistent="true", distance="1", waterlogged="false")
    return b


@gen("mushroom_cellar", kind="farm", freq="rare", n=2, styles=("ashen",))
def mushroom_cellar(rng, st):
    """A dark room under a little hut, down a ladder."""
    b = new("Mushroom Cellar", "farm", 9, 9)
    lk = look(rng, st, timbered=False)
    b.fill(1, -5, 1, 7, 0, 7, lk.found)
    b.clear(2, -4, 2, 6, -1, 6)
    b.fill(2, -5, 2, 6, -5, 6, "mycelium")
    for x in range(2, 7):
        for z in range(2, 7):
            if rng.random() < 0.5 and (x, z) != (4, 2):
                b.set(x, -4, z, pick(rng, ("brown_mushroom", "red_mushroom")))
    b.set(4, -1, 4, st.light, hanging="true", waterlogged="false")
    # the hut over the hatch
    b.fill(3, 1, 1, 5, 2, 3, lk.found)
    b.clear(4, 1, 2, 4, 2, 2)
    ladder(b, 4, 2, -4, 0, "south")
    b.door(4, 1, 1, "south", lk.wood)
    b.path(4, 0, 4, 0)
    b.fill(2, 3, 0, 6, 3, 4, f"{lk.wood}_slab", type="bottom", waterlogged="false")
    b.fill(3, 3, 1, 5, 3, 3, lk.found)
    b.set(3, 2, 0, st.wall_torch, facing="north")
    return b


def gate_base(b, rng, st):
    """The wall itself, the passage through it and the walkway ladders: the parts every
    gatehouse shares with the city wall (MaceCity.makeWalls: wall in rows 1-5 up to y72,
    parapets in rows 0 and 6 at y72-73)."""
    b.fill(0, 0, 0, 12, 0, 0, "air")
    b.fill(0, 0, 1, 12, 9, 5, WALL)
    b.fill(0, 10, 0, 12, 10, 0, WALL)
    b.fill(0, 10, 6, 12, 10, 6, WALL)
    b.fill(0, 9, 0, 12, 9, 0, WALL)
    b.fill(0, 9, 6, 12, 9, 6, WALL)
    for x in range(0, 13, 2):
        b.set(x, 11, 0, WALL)
        b.set(x, 11, 6, WALL)
    b.fill(4, 0, 1, 8, 0, 7, PATH)
    b.fill(4, 0, 6, 8, 0, 12, PATH)
    b.clear(4, 1, 1, 8, 5, 5)
    for x in (1, 11):   # up through a gap in the inner parapet onto the walkway
        ladder(b, x, 6, 1, 9, "south")
        b.clear(x, 10, 6, x, 11, 6)


@gen("gate_portcullis", kind="city_entrance", freq="very common", n=2)
def gate_portcullis(rng, st):
    b = new("Portcullis Gate", "city_entrance", 13, 13)
    gate_base(b, rng, st)
    b.set(4, 5, 1, WALL)
    b.set(8, 5, 1, WALL)
    b.fill(4, 4, 2, 8, 5, 2, "iron_bars", waterlogged="false")   # the portcullis, raised
    for x in (3, 9):
        b.set(x, 3, 0, st.wall_torch, facing="north")
    # a guard room over the passage, through which the walkway runs
    b.fill(2, 10, 1, 10, 14, 5, WALL)
    b.clear(3, 10, 2, 9, 13, 4)
    b.clear(2, 10, 3, 2, 11, 3)
    b.clear(10, 10, 3, 10, 11, 3)
    for x in range(2, 11, 2):
        b.set(x, 15, 1, WALL)
        b.set(x, 15, 5, WALL)
    for x in (4, 6, 8):
        b.set(x, 12, 1, "iron_bars", waterlogged="false")
    b.set(6, 13, 3, st.light, hanging="true", waterlogged="false")
    b.chest(3, 10, 4, "north", [("arrow", 32), ("bow", 1)])
    return b


@gen("gate_towers", kind="city_entrance", freq="very common", n=2)
def gate_towers(rng, st):
    b = new("Twin Tower Gate", "city_entrance", 13, 13)
    gate_base(b, rng, st)
    m = pick(rng, st.roofs)
    wood = pick(rng, st.woods)
    for tx in (0, 9):
        b.fill(tx, 0, 0, tx + 3, 14, 6, WALL)
        b.clear(tx + 1, 10, 1, tx + 2, 13, 5)
        x_in = tx + 3 if tx == 0 else tx
        b.clear(x_in, 10, 3, x_in, 11, 3)
        for z in (2, 4):
            b.set(tx if tx == 0 else tx + 3, 12, z, "iron_bars", waterlogged="false")
        hip(b, tx, 0, tx + 3, 6, 15, m, over=0, pitch=2 if st.key == "ashen" else 1)
        b.set(tx + 1, 13, 3, st.light, hanging="true", waterlogged="false")
    for x in (4, 8):
        b.set(x, 5, 1, WALL)
    for i, x in enumerate((4, 5, 7, 8)):   # doors, standing open
        b.set(x, 1, 3, f"{wood}_door", facing="east" if x < 6 else "west", half="lower",
              hinge="left" if x < 6 else "right", open="true", powered="false")
        b.set(x, 2, 3, f"{wood}_door", facing="east" if x < 6 else "west", half="upper",
              hinge="left" if x < 6 else "right", open="true", powered="false")
    b.clear(4, 1, 3, 8, 2, 3)
    b.set(6, 5, 1, WALL)
    b.set(6, 4, 1, st.light, hanging="true", waterlogged="false")
    return b


@gen("gate_arch", kind="city_entrance", freq="common", n=2)
def gate_arch(rng, st):
    b = new("Arched Gate", "city_entrance", 13, 13)
    gate_base(b, rng, st)
    stone = pick(rng, st.stone)
    for x, y in ((4, 5), (8, 5), (4, 4), (8, 4)):
        if (x, y) in ((4, 4), (8, 4)):
            b.set(x, y, 1, stone)
        else:
            b.set(x, y, 1, stone)
    for z in range(1, 6):
        b.set(4, 5, z, WALL)
        b.set(8, 5, z, WALL)
    for x in range(3, 10):   # a carved band over the arch, banners of the city colour
        b.set(x, 6, 0, stone)
    for x in (3, 9):
        b.fill(x, 1, 0, x, 8, 0, stone)
    for x in (5, 7):
        b.fill(x, 7, 0, x, 8, 0, WOOL)
    b.set(2, 3, 0, st.wall_torch, facing="north")
    b.set(10, 3, 0, st.wall_torch, facing="north")
    return b


# ── Mineshafts ────────────────────────────────────────────────────────────

@gen("mine_hut", kind="mineshaft_entrance", freq="very common", n=3)
def mine_hut(rng, st):
    """A hut over the shaft. The city puts the ladder in the middle (3, 3), against a stone
    column behind it (3, 4)."""
    b = new("Mine Hut", "mineshaft_entrance", 7, 7)
    lk = look(rng, st)
    s = Shell(1, 1, 5, 5, 1, h=4)
    shell(b, s, lk)
    b.fill(2, 0, 2, 4, 0, 4, "gravel")
    front_door(b, s, lk, 3, path=False)
    b.path(3, 0, 3, 0)
    windows(b, s, lk, sides=("east", "west"), every=1)
    roof(b, rng, st, s, lk, kind=pick(rng, ("hip", "gable")))
    b.set(2, 1, 4, "rail", shape="north_south", waterlogged="false")
    b.set(4, 1, 4, f"{pick(rng, st.woods)}_slab", type="bottom", waterlogged="false")
    b.set(4, 3, 4, st.wall_torch, facing="north")
    b.sign(4, 2, 0, "north", ["~Mine~Keep out~", "", "", ""], lk.wood)
    return b


@gen("mine_headframe", kind="mineshaft_entrance", freq="common", n=2)
def mine_headframe(rng, st):
    """A timber headframe over an open shaft."""
    b = new("Headframe", "mineshaft_entrance", 7, 7)
    wood = pick(rng, st.woods)
    b.fill(1, 0, 1, 5, 0, 5, pick(rng, st.found))
    b.set(3, 0, 3, "gravel")
    b.set(3, 0, 2, "gravel")
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
        b.fill(x, 1, z, x, 7, z, f"{wood}_log", axis="y")
    b.ring(1, 1, 5, 5, 7, 7, f"{wood}_log", axis="x")
    for z in range(1, 6):
        b.set(1, 7, z, f"{wood}_log", axis="z")
        b.set(5, 7, z, f"{wood}_log", axis="z")
    b.fill(1, 8, 2, 5, 8, 2, f"{wood}_log", axis="x")
    b.fill(3, 5, 2, 3, 7, 2, "iron_chain", axis="y", waterlogged="false")
    b.set(3, 4, 2, st.light, hanging="true", waterlogged="false")
    for x in (2, 4):
        b.set(x, 1, 1, f"{wood}_fence", waterlogged="false")
    for z in (2, 4):
        b.set(1, 1, z, f"{wood}_fence", waterlogged="false")
        b.set(5, 1, z, f"{wood}_fence", waterlogged="false")
    b.set(1, 8, 1, st.light, hanging="false", waterlogged="false")
    b.set(5, 8, 5, st.light, hanging="false", waterlogged="false")
    b.path(3, 0, 3, 0)
    return b


SECTION_LEVELS = {  # frequency1..7
    "storeroom": ("common", "common", "common", "average", "average", "rare", "rare"),
    "flooded": ("very rare", "rare", "average", "common", "common", "average", "rare"),
    "cave_in": ("rare", "average", "common", "common", "common", "common", "average"),
    "camp": ("average", "common", "average", "rare", "very rare", "exclude", "exclude"),
    "shrine": ("exclude", "exclude", "very rare", "rare", "average", "average", "common"),
    "crossing": ("very common", "very common", "common", "common", "average", "average", "average"),
    "vein": ("exclude", "very rare", "rare", "average", "common", "common", "very common"),
    "wrong_turn": ("exclude", "exclude", "exclude", "exclude", "very rare", "very rare", "rare"),
}


def section(name, rng, st, pattern="??? ?1? ???", unique=False):
    b = Build(name, "mineshaft_section", 7, 7)
    b.meta.update({"pattern": pattern, "pattern_position_x": "1", "pattern_position_z": "1"})
    for i, f in enumerate(SECTION_LEVELS[name.lower().replace(" ", "_")]):
        b.meta[f"frequency{i + 1}"] = f
    if unique:
        b.meta["unique"] = "yes"
    b.fill(0, 0, 0, 6, 4, 6, "stone")
    b.clear(1, 1, 1, 5, 3, 5)
    for x in range(2, 5):   # openings on all sides, to whatever corridors are there
        b.clear(x, 1, 0, x, 3, 0)
        b.clear(x, 1, 6, x, 3, 6)
        b.clear(0, 1, x, 0, 3, x)
        b.clear(6, 1, x, 6, 3, x)
    b.fill(0, 0, 0, 6, 0, 6, "gravel")
    return b


@gen("storeroom", kind="mineshaft_section", n=1)
def storeroom(rng, st):
    b = section("Storeroom", rng, st)
    wood = pick(rng, st.woods)
    b.fill(1, 0, 1, 5, 0, 5, f"{wood}_planks")
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
        b.fill(x, 1, z, x, 3, z, f"{wood}_log", axis="y")
    for x in (2, 4):
        b.barrel(x, 1, 1, [(pick(rng, ("coal", "iron_ingot", "torch", "rail")), rng.randint(4, 16))], facing="up")
        b.barrel(x, 1, 5, [], facing="up")
    b.chest(1, 1, 3, "east", TREASURE)
    b.set(5, 1, 3, "crafting_table")
    b.set(3, 3, 3, st.light, hanging="true", waterlogged="false")
    b.fill(1, 4, 1, 5, 4, 5, f"{wood}_planks")
    return b


@gen("flooded", kind="mineshaft_section", n=1)
def flooded(rng, st):
    b = section("Flooded", rng, st)
    b.fill(1, 0, 1, 5, 0, 5, "clay")
    b.fill(2, 0, 2, 4, 0, 4, "water", level="0")
    b.fill(2, -1, 2, 4, -1, 4, "clay")
    for x in (1, 5):
        b.set(x, 1, 1, "dripstone_block")
    b.set(1, 3, 1, "pointed_dripstone", vertical_direction="down", thickness="tip", waterlogged="false")
    b.set(5, 3, 5, "pointed_dripstone", vertical_direction="down", thickness="tip", waterlogged="false")
    return b


@gen("cave_in", kind="mineshaft_section", n=1)
def cave_in(rng, st):
    b = section("Cave In", rng, st)
    for x in range(1, 6):
        for z in range(1, 6):
            h = max(0, 3 - abs(x - 3) - abs(z - 2) + rng.randint(-1, 1))
            for y in range(1, h + 1):
                b.set(x, y, z, pick(rng, ("gravel", "cobblestone", "andesite", "gravel")))
    for _ in range(4):
        b.set(rng.randint(1, 5), 3, rng.randint(1, 5), "cobweb")
    b.set(3, 4, 3, "air")
    b.set(3, 3, 3, "air")
    return b


@gen("camp", kind="mineshaft_section", n=1)
def camp(rng, st):
    b = section("Camp", rng, st)
    b.set(3, 1, 3, "campfire", facing="north", lit="false", signal_fire="false", waterlogged="false")
    b.bed(1, 1, 2, "north", pick(rng, st.beds))
    b.chest(5, 1, 1, "west")
    b.set(5, 1, 5, "crafting_table")
    b.set(1, 1, 5, "furnace", facing="east", lit="false")
    b.set(5, 2, 1, st.wall_torch, facing="west")
    return b


@gen("shrine", kind="mineshaft_section", n=1)
def shrine(rng, st):
    b = section("Shrine", rng, st)
    stone = pick(rng, st.stone)
    b.fill(1, 0, 1, 5, 0, 5, stone)
    b.fill(2, 1, 5, 4, 1, 5, stone)
    b.set(3, 2, 5, "candle", candles="4", lit="false", waterlogged="false")
    b.set(2, 2, 5, "candle", candles="1", lit="false", waterlogged="false")
    b.set(4, 2, 5, "candle", candles="2", lit="false", waterlogged="false")
    b.chest(3, 1, 4, "north", TREASURE)
    for x in (1, 5):
        b.set(x, 1, 1, st.light, hanging="false", waterlogged="false")
    return b


@gen("crossing", kind="mineshaft_section", n=1)
def crossing(rng, st):
    b = section("Crossing", rng, st, pattern="?1? 111 ?1?")
    wood = pick(rng, st.woods)
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
        b.fill(x, 1, z, x, 3, z, f"{wood}_log", axis="y")
    b.fill(1, 4, 1, 5, 4, 5, f"{wood}_planks")
    b.set(3, 3, 3, st.light, hanging="true", waterlogged="false")
    for x in range(0, 7):
        b.set(x, 1, 3, "rail", shape="east_west", waterlogged="false")
    for z in range(0, 7):
        b.set(3, 1, z, "rail", shape="north_south", waterlogged="false")
    b.set(3, 1, 3, "rail", shape="north_south", waterlogged="false")
    return b


@gen("vein", kind="mineshaft_section", n=1)
def vein(rng, st):
    b = section("Vein", rng, st)
    ores = ("coal_ore", "iron_ore", "copper_ore", "gold_ore", "redstone_ore", "lapis_ore", "diamond_ore")
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if n == "stone" and rng.random() < 0.35:
            b.set(x, y, z, "deepslate_" + pick(rng, ores))
    b.set(3, 3, 3, st.light, hanging="true", waterlogged="false")
    b.fill(3, 4, 3, 3, 4, 3, "stone")
    return b


@gen("wrong_turn", kind="mineshaft_section", n=1)
def wrong_turn(rng, st):
    """A room that shouldn't be down here."""
    b = section("Wrong Turn", rng, st, unique=True)
    b.meta["unique"] = "yes"
    b.fill(0, 0, 0, 6, 4, 6, "smooth_sandstone")
    b.clear(1, 1, 1, 5, 3, 5)
    for x in range(2, 5):
        b.clear(x, 1, 0, x, 3, 0)
        b.clear(x, 1, 6, x, 3, 6)
        b.clear(0, 1, x, 0, 3, x)
        b.clear(6, 1, x, 6, 3, x)
    b.fill(1, 1, 1, 5, 1, 5, "yellow_carpet")
    for x, z in ((2, 2), (4, 4), (2, 4), (4, 2)):
        b.set(x, 4, z, "ochre_froglight", axis="y")
    b.fill(1, 2, 1, 1, 3, 5, "smooth_sandstone")
    b.clear(1, 2, 2, 1, 3, 4)
    return b


# ── Sky features ──────────────────────────────────────────────────────────

SKY = 37   # y of world y100, where Mace's sky features start

def balloon_envelope(b, cx, cy, cz, r, ry, stripe):
    for y in range(-ry, ry + 1):
        for z in range(-r, r + 1):
            for x in range(-r, r + 1):
                d = (x / (r + 0.5)) ** 2 + (z / (r + 0.5)) ** 2 + (y / (ry + 0.5)) ** 2
                if d <= 1 and d > 0.55:
                    vertical = (x + z) % 4 in (0, 1) if stripe else True
                    b.set(cx + x, cy + y, cz + z, WOOL if vertical else "white_wool")


@gen("balloon", kind="sky_feature", freq="very common", n=3)
def balloon(rng, st):
    r = rng.randint(4, 5)
    S = 2 * r + 3
    b = Build("Balloon", "sky_feature", S, S)
    c = S // 2
    ry = r + 2
    base = SKY   # the basket's floor: world y100
    cy = base + 5 + ry
    balloon_envelope(b, c, cy, c, r, ry, rng.random() < 0.6)
    wood = pick(rng, st.woods)
    b.fill(c - 1, base, c - 1, c + 1, base, c + 1, f"{wood}_planks")
    b.ring(c - 1, c - 1, c + 1, c + 1, base + 1, base + 1, f"{wood}_fence", waterlogged="false")
    for x, z in ((c - 1, c - 1), (c + 1, c - 1), (c - 1, c + 1), (c + 1, c + 1)):
        b.fill(x, base + 2, z, x, cy - ry + 1, z, "iron_chain", axis="y", waterlogged="false")
    b.set(c, base + 1, c, "campfire", facing="north", lit="true", signal_fire="false", waterlogged="false")
    b.set(c, base + 2, c, "air")
    return b


@gen("airship", kind="sky_feature", freq="common", n=3)
def airship(rng, st):
    L = rng.randint(15, 21)
    W = 7
    b = Build("Airship", "sky_feature", 11, L + 2)
    wood = pick(rng, st.woods)
    cx = 5
    deck = SKY + 2
    for z in range(1, L + 1):
        t = (z - 1) / (L - 1)
        half = 3 if 0.2 < t < 0.8 else 2 if 0.08 < t < 0.92 else 1
        for x in range(cx - half, cx + half + 1):
            b.set(x, deck, z, f"{wood}_planks")
            b.set(x, deck - 1, z, f"{wood}_planks") if abs(x - cx) < half else None
        if half > 1:
            b.set(cx - half, deck + 1, z, f"{wood}_fence", waterlogged="false")
            b.set(cx + half, deck + 1, z, f"{wood}_fence", waterlogged="false")
        b.set(cx, deck - 2, z, f"{wood}_planks") if half > 1 else None
    # the envelope above, a long ellipsoid
    ey = deck + 9
    for z in range(0, L + 2):
        for x in range(0, 11):
            for y in range(ey - 4, ey + 5):
                d = ((x - cx) / 5.4) ** 2 + ((y - ey) / 4.4) ** 2 + ((z - (L + 1) / 2) / ((L + 2) / 2)) ** 2
                if 0.6 < d <= 1:
                    b.set(x, y, z, WOOL if (z // 3) % 2 else "white_wool")
    for z in (3, L // 2, L - 2):
        for x in (cx - 2, cx + 2):
            b.fill(x, deck + 2, z, x, ey - 4, z, "iron_chain", axis="y", waterlogged="false")
    # a cabin aft, a propeller at the stern
    b.fill(cx - 2, deck + 1, L - 5, cx + 2, deck + 3, L - 2, f"{wood}_planks")
    b.clear(cx - 1, deck + 1, L - 4, cx + 1, deck + 2, L - 3)
    b.set(cx, deck + 1, L - 5, "air")
    b.set(cx, deck + 2, L - 5, "air")
    b.chest(cx - 1, deck + 1, L - 3, "east", TREASURE)
    b.set(cx, deck + 2, L + 1, f"{wood}_fence", waterlogged="false")
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        b.set(cx + dx, deck + 2 + dy, L + 1, f"{wood}_trapdoor", facing="south", half="bottom", open="true",
              powered="false", waterlogged="false")
    b.set(cx, deck + 1, 2, st.light, hanging="false", waterlogged="false")
    return b


# ── Trees for the countryside ─────────────────────────────────────────────

def make_tree(rng, st, kind):
    b = new(f"{kind.replace('_', ' ').title()} Tree", "tree", 7, 7)
    tree(b, rng, 3 if kind != "dark_oak" else 2, 3 if kind != "dark_oak" else 2, kind)
    return b


for _t in ("oak", "spruce", "birch", "acacia", "olive", "cypress", "dark_oak", "pale_oak"):
    gen(f"tree_{_t}", kind="tree", freq="common", n=3,
        styles=tuple(s for s, kinds in (("timber", ("oak", "spruce", "birch")), ("ochre", ("acacia", "olive", "cypress")),
                                        ("ashen", ("dark_oak", "spruce", "pale_oak"))) if _t in kinds))(
        lambda rng, st, _t=_t: make_tree(rng, st, _t))
