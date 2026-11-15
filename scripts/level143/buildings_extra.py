"""More of Level 143's buildings: kinds beyond buildings.py, registered in the same CATALOG.

Same conventions as buildings.py: every generator takes (rng, style) and returns a schem.Build
facing north (its front at z = 0, on the street), with a ground margin for roof overhangs.
"""

import math

import functools

from buildings import HOUSE_GROUND, HOUSE_ONE, HOUSE_UPPER, farmland, flowers, new, yard_cells  # noqa: F401
from buildings import gen as _gen
from parts import (FULL, Room, Shell, chimney, flat, front_door, furnish, gable, hang, hip, ladder, lamp_post,
                   look, mat, pick, posts, shed, shell, table, tree, wall_cells, windows)
from schem import GROUND, OPPOSITE, PATH, STEP, TREASURE, WOOL

WL = dict(waterlogged="false")
_FLIMSY = ("air", "cave_air", "glass_pane", "iron_bars")


def mounts(b):
    """Wall signs and torches need something solid behind them: a window pane or bars that ended
    up there (a window cut beside a door) is walled up again with the block under it."""
    for (x, y, z), (n, p) in list(b.blocks.items()):
        if n.endswith(("wall_sign", "wall_torch")):
            dx, dz = STEP[OPPOSITE[p["facing"]]]
            back = b.get(x + dx, y, z + dz)
            if back in _FLIMSY or back.endswith("_pane"):
                under = b.get(x + dx, y - 1, z + dz)
                if under in _FLIMSY or under.endswith(("_pane", "_door", "stairs", "slab")):
                    under = "stone_bricks"
                b.set(x + dx, y, z + dz, under, **b.props(x + dx, y - 1, z + dz) if b.get(x + dx, y - 1, z + dz) == under else {})


def ladders(b):
    """A ladder needs a solid block behind it: a window pane or bars there (a window cut in the
    wall it climbs) is walled up with the wall block beside or under it."""
    from parts import solid
    for (x, y, z), (n, p) in list(b.blocks.items()):
        if n != "ladder":
            continue
        dx, dz = STEP[OPPOSITE[p["facing"]]]
        bx, bz = x + dx, z + dz
        back = b.get(bx, y, bz)
        if back in _FLIMSY or back.endswith("_pane"):
            near = [(bx + dz, y, bz + dx), (bx - dz, y, bz - dx), (bx, y - 1, bz), (bx, y + 1, bz)]
            src = next((c for c in near if solid(b, *c) and not b.get(*c).endswith(("_ore",))), None)
            if src:
                b.set(bx, y, bz, b.get(*src), **b.props(*src))
            else:
                b.set(bx, y, bz, "stone_bricks")


def doorways(b):
    """Nothing but air (or a carpet, a torch) right in front of or behind a closed door: shutters
    and pots dressed onto the wall beside a window can land in a doorway."""
    for (x, y, z), (n, p) in list(b.blocks.items()):
        if not n.endswith("_door") or p.get("half") != "lower" or p.get("open") == "true":
            continue
        dx, dz = STEP[p["facing"]]
        for sgn in (1, -1):
            for dy in (0, 1):
                c = (x + sgn * dx, y + dy, z + sgn * dz)
                m = b.get(*c)
                if m.endswith("_trapdoor") or m.startswith("potted_") or m == "flower_pot" or m.endswith("_leaves"):
                    b.set(*c, "air")


def gen(*args, **kw):
    """buildings.gen, with mounts(), ladders() and doorways() run over what the generator draws."""
    def deco(f):
        @functools.wraps(f)
        def make(rng, st):
            b = f(rng, st)
            mounts(b)
            ladders(b)
            doorways(b)
            return b
        _gen(*args, **kw)(make)
        return f
    return deco
LEAF = dict(persistent="true", distance="1", waterlogged="false")


# ── Small helpers ─────────────────────────────────────────────────────────

def stair(b, x, y, z, name, facing, half="bottom"):
    b.set(x, y, z, name, facing=facing, half=half, **WL)


def slab(b, x, y, z, name, typ="bottom"):
    b.set(x, y, z, name, type=typ, **WL)


def fslab(b, x0, y0, z0, x1, y1, z1, name, typ="bottom"):
    b.fill(x0, y0, z0, x1, y1, z1, name, type=typ, **WL)


_BASE = {v: k for k, v in FULL.items()}


def mat_of(block, fallback="stone_brick"):
    """The stairs/slab Mat for a full block, or the fallback's."""
    if block.endswith("_planks"):
        return mat(block[:-len("_planks")])
    return mat(_BASE.get(block, fallback))


WALLS = {"stone_bricks": "stone_brick_wall", "cobblestone": "cobblestone_wall", "mossy_cobblestone": "mossy_cobblestone_wall",
         "andesite": "andesite_wall", "smooth_sandstone": "sandstone_wall", "cut_sandstone": "sandstone_wall",
         "mud_bricks": "mud_brick_wall", "packed_mud": "mud_brick_wall", "deepslate_bricks": "deepslate_brick_wall",
         "polished_blackstone_bricks": "polished_blackstone_brick_wall", "tuff_bricks": "tuff_brick_wall",
         "cobbled_deepslate": "cobbled_deepslate_wall", "bricks": "brick_wall", "granite": "granite_wall",
         "red_sandstone": "red_sandstone_wall"}


def wall_of(block):
    return WALLS.get(block, "stone_brick_wall")


def stone(rng, st):
    return pick(rng, st.stone)


def brick(rng, st):
    """Masonry for the plainer brick buildings."""
    return pick(rng, {"timber": ("bricks", "stone_bricks", "bricks"),
                      "ochre": ("smooth_sandstone", "mud_bricks", "bricks"),
                      "ashen": ("deepslate_bricks", "polished_blackstone_bricks", "tuff_bricks")}[st.key])


def light(b, x, y, z, st):
    b.set(x, y, z, st.light, hanging="false", **WL)


def hanging(b, x, y, z, st):
    b.set(x, y, z, st.light, hanging="true", **WL)


def wall_torch(b, x, y, z, st, facing):
    b.set(x, y, z, st.wall_torch, facing=facing)


def candle(b, x, y, z, n=3, color=None):
    b.set(x, y, z, f"{color}_candle" if color else "candle", candles=str(n), lit="false", **WL)


def door(b, x, z, wood, y=1, facing="south", path=True, hinge="left"):
    """A door in a north wall at (x, z), a path from it to the street."""
    b.door(x, y, z, facing, wood, hinge=hinge)
    if path and z > 0:
        b.path(x, 0, x, z - 1)


def storey(b, x0, z0, x1, z1, ya, yb, lk, floor=True):
    """Walls x0..x1, z0..z1 from the course at ya up to the top course at yb, a floor at ya:
    shell() for a storey that doesn't start on the ground."""
    if floor:
        b.fill(x0 + 1, ya, z0 + 1, x1 - 1, ya, z1 - 1, lk.floor)
    b.ring(x0, z0, x1, z1, ya, yb, lk.infill)
    if not lk.timbered:
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            b.fill(x, ya, z, x, yb, z, lk.found if lk.found != lk.infill else lk.log, **({} if lk.found != lk.infill else {"axis": "y"}))
        return
    for y in (ya, yb):
        for x in range(x0, x1 + 1):
            b.set(x, y, z0, lk.log, axis="x")
            b.set(x, y, z1, lk.log, axis="x")
        for z in range(z0 + 1, z1):
            b.set(x0, y, z, lk.log, axis="z")
            b.set(x1, y, z, lk.log, axis="z")
    for x, z in posts(Shell(x0, z0, x1, z1, 1), lk.post_every):
        b.fill(x, ya + 1, z, x, yb - 1, z, lk.log, axis="y")


def disc(r):
    """The cells of a filled circle of radius r about (0, 0)."""
    rr = (r + 0.45) ** 2
    return [(dx, dz) for dx in range(-r, r + 1) for dz in range(-r, r + 1) if dx * dx + dz * dz <= rr]


def circle(r):
    inner = set(disc(r - 1)) if r > 0 else set()
    return [c for c in disc(r) if c not in inner]


def toward(dx, dz):
    """The way from (dx, dz) back to the middle."""
    if abs(dx) >= abs(dz):
        return "west" if dx > 0 else "east"
    return "north" if dz > 0 else "south"


def cone(b, cx, cz, y0, r, m, top=None):
    """A round roof: rings of stairs climbing to a point. Returns the top y."""
    y = y0
    for rr in range(r, 0, -1):
        for dx, dz in circle(rr):
            stair(b, cx + dx, y, cz + dz, m.stairs, toward(dx, dz))
        for dx, dz in disc(rr - 1):
            b.set(cx + dx, y, cz + dz, m.block)
        y += 1
    b.set(cx, y, cz, top or m.slab, **({"type": "bottom", **WL} if top is None else {}))
    return y


def spire(b, x0, z0, x1, z1, y0, m, pitch=2, over=0):
    """A steep pyramid: each course `pitch` high, stairs over full blocks."""
    xa, xb, za, zb, y = x0 - over, x1 + over, z0 - over, z1 + over, y0
    while xa < xb and za < zb:
        for yy in range(y, y + pitch):
            b.fill(xa + 1, yy, za + 1, xb - 1, yy, zb - 1, m.block)
            for x in range(xa, xb + 1):
                if yy == y + pitch - 1:
                    stair(b, x, yy, za, m.stairs, "south")
                    stair(b, x, yy, zb, m.stairs, "north")
                else:
                    b.set(x, yy, za, m.block)
                    b.set(x, yy, zb, m.block)
            for z in range(za + 1, zb):
                if yy == y + pitch - 1:
                    stair(b, xa, yy, z, m.stairs, "east")
                    stair(b, xb, yy, z, m.stairs, "west")
                else:
                    b.set(xa, yy, z, m.block)
                    b.set(xb, yy, z, m.block)
        xa, xb, za, zb, y = xa + 1, xb - 1, za + 1, zb - 1, y + pitch
    b.fill(xa, y, za, xb, y, zb, m.block)
    return y


def dome(b, cx, cz, y0, r, block, inner=None):
    """A half sphere on y0, hollow; `inner` fills what it covers (air by default)."""
    for dy in range(0, r + 1):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                d = math.sqrt(dx * dx + dy * dy + dz * dz)
                if d <= r + 0.5:
                    if d > r - 0.6:
                        b.set(cx + dx, y0 + dy, cz + dz, block)
                    elif inner:
                        b.set(cx + dx, y0 + dy, cz + dz, inner)
    return y0 + r


def stepped_gable(b, x0, x1, z, yb, pitch, block, cap):
    """A Dutch stepped gable on the wall at z over a gable(along="z") roof of the same pitch."""
    ridge = yb + pitch * ((x1 - x0) // 2 + 1)
    for x in range(x0, x1 + 1):
        d = min(x - x0, x1 - x)
        h = min(yb + pitch * (2 * (d // 2) + 2), ridge + 1)
        b.fill(x, yb + 1, z, x, h, z, block)
        slab(b, x, h + 1, z, cap)


def counter(b, x0, x1, z, y, wood):
    for x in range(x0, x1 + 1):
        slab(b, x, y, z, f"{wood}_slab", "top")


def awning(b, x0, x1, z, y, st_block=WOOL):
    for x in range(x0, x1 + 1):
        b.set(x, y, z, st_block)


def bracket(b, x, y, z, wood, facing):
    """An upside-down stair holding up what's above, its back to `facing`."""
    stair(b, x, y, z, f"{wood}_stairs", facing, "top")


def potted(rng):
    return pick(rng, ("potted_fern", "potted_red_tulip", "potted_azure_bluet", "potted_cornflower",
                      "potted_spruce_sapling", "potted_dead_bush", "potted_allium", "potted_oxeye_daisy"))


def flower_box(b, rng, st, x0, x1, y, z, wood):
    """Trapdoor-fronted planter under a window on the north wall."""
    for x in range(x0, x1 + 1):
        b.set(x, y, z, f"{wood}_trapdoor", facing="north", half="top", open="false", powered="false", **WL)


# ── Houses ────────────────────────────────────────────────────────────────

@gen("semi_detached", freq="very common", n=4)
def semi_detached(rng, st):
    """Two cottages sharing a party wall, mirrored, under one roof, each with its porch."""
    hw, D = rng.randint(5, 6), rng.randint(6, 7)
    yard = 3
    W = 2 * hw - 1
    b = new("Semi-Detached Cottages", "city", W + 2, yard + D + 1)
    lk = look(rng, st)
    floors = rng.choice((1, 2, 2))
    halves = (Shell(1, yard, hw, yard + D - 1, floors), Shell(hw, yard, W, yard + D - 1, floors))
    for i, s in enumerate(halves):
        shell(b, s, lk)
    for i, s in enumerate(halves):
        dx = s.x0 + 1 if i == 0 else s.x1 - 1
        front_door(b, s, lk, dx)
        windows(b, s, lk, sides=("north", "south", "west" if i == 0 else "east"), skip={(dx, s.z0)}, every=2)
        lx = s.x1 - 1 if i == 0 else s.x0 + 1
        if floors > 1:
            ladder(b, lx, s.z1 - 1, 1, s.h, "north")
        for k in range(floors):
            keep = {(dx, s.z0 + 1), (lx, s.z1 - 1), (lx, s.z1 - 2)}
            room = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, k * s.h + 1, keep)
            items = pick(rng, HOUSE_ONE) if floors == 1 else (pick(rng, HOUSE_GROUND)[:4] if k == 0 else pick(rng, HOUSE_UPPER))
            furnish(room, rng, st, lk, items)
        # a porch: two posts and a little roof over the step
        for px in (dx - 1, dx + 1):
            b.fill(px, 1, s.z0 - 2, px, 2, s.z0 - 2, f"{lk.wood}_fence", **WL)
        fslab(b, dx - 1, 3, s.z0 - 2, dx + 1, 3, s.z0 - 1, pick(rng, st.roofs).slab)
        if rng.random() < 0.6:
            b.sign(dx + (1 if i == 0 else -1), 2, s.z0 - 1, "north", "[house]", lk.wood)
        flowers(b, rng, st, [(x, z) for x in range(s.x0, s.x1 + 1) for z in range(0, yard - 2)], 0.4)
    # a low fence between the two front gardens
    for z in range(0, yard - 1):
        b.set(hw, 1, z, f"{lk.wood}_fence", **WL)
    whole = Shell(1, yard, W, yard + D - 1, floors)
    m = pick(rng, st.roofs)
    if st.key == "ochre" and rng.random() < 0.6:
        top = flat(b, whole.x0, whole.z0, whole.x1, whole.z1, whole.top, lk.found)
        b.ring(hw, whole.z0, hw, whole.z1, whole.top + 1, whole.top + 1, lk.found)
    else:
        top = gable(b, whole.x0, whole.z0, whole.x1, whole.z1, whole.top, m, along="x", fill=lk.infill,
                    pitch=2 if rng.random() < st.pitch2 else 1)
    chimney(b, hw, whole.z0 + D // 2, whole.top, top + 2, pick(rng, ("bricks", lk.found)))
    return b


SHOPFRONT = [["barrel", "chest", "plant", "crafting_table"], ["loom", WOOL, "barrel", "chest"],
             ["shelf", "lectern", "chest"], ["barrel", "barrel", "composter", "hay_block"],
             ["smoker", "barrel", "chest", "cake"]]


@gen("jettied_house", freq="common", n=4, styles=("timber", "ashen"))
def jettied_house(rng, st):
    """A shop in a stone ground floor, timber-framed rooms jettied out over the street above it,
    the gable end facing the street."""
    W, D = rng.choice((7, 7, 9)), rng.randint(7, 9)
    yard = 3
    b = new("Jettied House", "city", W + 2, yard + D + 1)
    lk = look(rng, st, timbered=True)
    base = look(rng, st, timbered=False)
    base.infill = base.found
    base.wood, base.floor = lk.wood, lk.floor
    s = Shell(1, yard, W, yard + D - 1, 1)
    shell(b, s, base)
    mx = (s.x0 + s.x1) // 2
    dx = front_door(b, s, base, s.x0 + 1 if rng.random() < 0.5 else s.x1 - 1)
    for x in range(s.x0 + 1, s.x1):   # the shop window
        if abs(x - dx) > 1:
            b.set(x, 2, s.z0, "glass_pane")
    windows(b, s, base, sides=("east", "west"), every=3)
    up = rng.choice((1, 2))
    top = 4 + 4 * up
    storey(b, s.x0, s.z0 - 1, s.x1, s.z1, 4, top, lk)
    for k in range(1, up):
        b.fill(s.x0 + 1, 4 + 4 * k, s.z0, s.x1 - 1, 4 + 4 * k, s.z1 - 1, lk.floor)
    upper = Shell(s.x0, s.z0 - 1, s.x1, s.z1, up + 1)
    windows(b, upper, lk, floors=range(1, up + 1), sides=("north", "south", "east", "west"), every=2)
    for x in range(s.x0, s.x1 + 1, 2):   # brackets under the jetty
        bracket(b, x, 3, s.z0 - 1, lk.wood, "south")
    if rng.random() < 0.5:   # window boxes on the jetty
        for x in range(s.x0 + 1, s.x1):
            if b.get(x, 6, s.z0 - 1).endswith("pane"):
                b.set(x, 5, s.z0 - 2, f"{lk.wood}_trapdoor", facing="north", half="top", open="false", powered="false", **WL)
                b.set(x, 6, s.z0 - 2, potted(rng))
    # inside: a counter, the shop's things, beds above
    counter(b, s.x0 + 1, s.x1 - 1, s.z0 + 3, 1, lk.wood)
    b.set(dx, 1, s.z0 + 3, "air")
    lx = s.x1 - 1 if dx == s.x0 + 1 else s.x0 + 1
    ladder(b, lx, s.z1 - 1, 1, 4 * up, "north", support=lk.infill)
    shop_room = Room(b, s.x0 + 1, s.z0 + 4, s.x1 - 1, s.z1 - 1, 1, {(lx, s.z1 - 1), (lx, s.z1 - 2), (dx, s.z0 + 4)})
    furnish(shop_room, rng, st, lk, pick(rng, SHOPFRONT), light=False)
    hang(b, mx, s.z0 + 1, 1, st)
    hang(b, mx, s.z0 + 5, 1, st)
    for k in range(up):
        y = 4 * (k + 1) + 1
        room = Room(b, s.x0 + 1, s.z0, s.x1 - 1, s.z1 - 1, y, {(lx, s.z1 - 1), (lx, s.z1 - 2)})
        furnish(room, rng, st, lk, pick(rng, HOUSE_UPPER) if k == up - 1 else ["bed", "chest", "furnace", "plant"])
    m = pick(rng, st.roofs)
    ridge = gable(b, s.x0, s.z0 - 1, s.x1, s.z1, top, m, along="z", fill=lk.infill, pitch=2 if rng.random() < st.pitch2 else 1)
    chimney(b, lx, s.z1, top, ridge + 1, pick(rng, ("bricks", base.found)))
    b.sign(dx + (1 if dx < mx else -1), 2, s.z0 - 1, "north", "2[surnames]~& Daughters" if rng.random() < 0.4 else "[house]", lk.wood)
    return b


@gen("merchant_house", freq="common", n=4)
def merchant_house(rng, st):
    """A narrow tall brick house with a stepped gable to the street and a hoist beam at the top."""
    W, D = rng.choice((5, 7, 7)), rng.randint(7, 9)
    yard = rng.randint(1, 2)
    b = new("Merchant House", "city", W + 2, yard + D + 1)
    lk = look(rng, st, timbered=False)
    lk.infill = brick(rng, st)
    lk.found = pick(rng, st.found)
    lk.window_tall = True
    floors = rng.choice((3, 3, 4))
    s = Shell(1, yard, W, yard + D - 1, floors)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    dx = front_door(b, s, lk, s.x0 + 1)
    windows(b, s, lk, sides=("north", "south"), skip={(dx, s.z0)}, every=2)
    lx = s.x1 - 1
    ladder(b, lx, s.z1 - 1, 1, (floors - 1) * s.h, "north", support=lk.infill)
    for k in range(floors):
        room = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, k * s.h + 1, {(dx, s.z0 + 1), (lx, s.z1 - 1), (lx, s.z1 - 2)})
        if k == 0:
            items = ["barrel", "barrel", "chest", "barrel", "crafting_table"]
        elif k == floors - 1:
            items = ["barrel", "hay_block", "barrel", "chest"]   # the store under the roof
        else:
            items = pick(rng, HOUSE_UPPER)
        furnish(room, rng, st, lk, items)
    cap = mat_of(lk.infill).slab
    pitch = 2
    m = pick(rng, st.roofs)
    ridge = gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along="z", fill=lk.infill, pitch=pitch, end_over=0)
    for z in (s.z0, s.z1):
        stepped_gable(b, s.x0, s.x1, z, s.top, pitch, lk.infill, cap)
    # the hoist: a beam out over the street, a loading door under it
    hy = s.top + pitch * 2 + 1
    b.set(mx, hy, s.z0 - 1, f"{lk.log}", axis="z")
    b.set(mx, hy - 1, s.z0 - 1, "iron_chain", axis="y", **WL)
    b.set(mx, hy - 2, s.z0 - 1, "iron_chain", axis="y", **WL)
    b.set(mx, s.top - 1, s.z0, f"{lk.wood}_trapdoor", facing="north", half="bottom", open="true", powered="false", **WL)
    b.set(mx, s.top - 2, s.z0, f"{lk.wood}_trapdoor", facing="north", half="bottom", open="true", powered="false", **WL)
    for x in range(s.x0, s.x1 + 1):   # a string course over the ground floor
        stair(b, x, s.h, s.z0 - 1, mat_of(lk.found, "stone_brick").stairs, "south", "top")
    b.set(dx, s.h, s.z0 - 1, "air")
    b.sign(dx + 1, 2, s.z0 - 1, "north", ["2[surnames]~Imports", "", "", ""] if rng.random() < 0.5 else "[house]", lk.wood)
    return b


# ── Public buildings ──────────────────────────────────────────────────────

@gen("bathhouse", freq="average", n=3)
def bathhouse(rng, st):
    """A hall around a sunken pool, columns at its corners, benches along the walls."""
    W, D = rng.choice((11, 13)), rng.choice((11, 13))
    yard = 2
    b = new("Bathhouse", "city", W + 2, yard + D + 1)
    lk = look(rng, st, timbered=False)
    lk.infill = stone(rng, st)
    lk.floor = {"timber": "smooth_stone", "ochre": "cut_sandstone", "ashen": "polished_deepslate"}[st.key]
    s = Shell(1, yard, W, yard + D - 1, 1, h=5)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    front_door(b, s, lk, mx)
    b.door(mx - 1, 1, s.z0, "south", lk.wood, hinge="left")
    b.door(mx, 1, s.z0, "south", lk.wood, hinge="right")
    b.set(mx - 1, 0, s.z0, lk.floor)
    b.path(mx - 1, 0, mx - 1, s.z0 - 1)
    for z in range(s.z0 + 2, s.z1 - 1, 2):   # high windows
        for x in (s.x0, s.x1):
            b.set(x, 3, z, "glass_pane")
            b.set(x, 4, z, "glass_pane")
    px0, pz0, px1, pz1 = s.x0 + 3, s.z0 + 3, s.x1 - 3, s.z1 - 3
    tile = {"timber": "prismarine_bricks", "ochre": "cyan_terracotta", "ashen": "dark_prismarine"}[st.key]
    rim = {"timber": "polished_andesite", "ochre": "smooth_sandstone", "ashen": "polished_blackstone"}[st.key]
    b.fill(px0 - 1, -3, pz0 - 1, px1 + 1, 0, pz1 + 1, tile)
    b.fill(px0, -2, pz0, px1, 0, pz1, "water", level="0")
    b.ring(px0 - 1, pz0 - 1, px1 + 1, pz1 + 1, 0, 0, rim)
    for x, z in ((px0 - 1, pz0 - 1), (px1 + 1, pz0 - 1), (px0 - 1, pz1 + 1), (px1 + 1, pz1 + 1)):
        b.fill(x, 1, z, x, s.top - 1, z, wall_of(lk.infill) if rng.random() < 0.5 else lk.found)
    stair(b, mx, 0, pz0 - 1, mat_of(rim, "stone_brick").stairs, "south")   # steps into the pool
    stair(b, mx, -1, pz0, mat_of(rim, "stone_brick").stairs, "south")
    # benches along the side walls, a steaming cauldron, the boiler at the back
    seat = mat_of(lk.infill).stairs
    for z in range(s.z0 + 2, s.z1 - 1):
        if z % 3:
            stair(b, s.x0 + 1, 1, z, seat, "west")
            stair(b, s.x1 - 1, 1, z, seat, "east")
    b.set(s.x0 + 1, 1, s.z1 - 1, "water_cauldron", level="3")
    b.set(s.x1 - 1, 1, s.z1 - 1, "water_cauldron", level="3")
    b.set(mx, 1, s.z1 - 1, "furnace", facing="north", lit="true")
    b.barrel(mx - 1, 1, s.z1 - 1, [("white_wool", rng.randint(2, 8))], facing="up")
    b.barrel(mx + 1, 1, s.z1 - 1, [("sponge", 1)] if rng.random() < 0.2 else [], facing="up")
    chimney(b, mx, s.z1, 1, s.top + 4, "bricks")
    b.set(s.x0 + 1, 1, s.z0 + 1, potted(rng))
    b.set(s.x1 - 1, 1, s.z0 + 1, potted(rng))
    for x in (s.x0 + 2, s.x1 - 2):
        for z in (s.z0 + 2, s.z1 - 2):
            hang(b, x, z, 1, st, height=4)
    if st.key == "ochre":
        flat(b, s.x0, s.z0, s.x1, s.z1, s.top, lk.found)
        cz = (pz0 + pz1) // 2
        r = min(px1 - px0, pz1 - pz0) // 2 + 1
        b.fill(mx - r + 1, s.top, cz - r + 1, mx + r - 1, s.top, cz + r - 1, "air")
        dome(b, mx, cz, s.top, r, pick(rng, ("white_terracotta", "smooth_sandstone", "light_blue_terracotta")))
        b.set(mx, s.top + r + 1, cz, "lightning_rod", facing="up", powered="false", **WL)
        hanging(b, mx, s.top + r - 1, cz, st)
    elif st.key == "timber":
        top = hip(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs))
        b.fill(mx - 1, top, (s.z0 + s.z1) // 2 - 1, mx + 1, top + 1, (s.z0 + s.z1) // 2 + 1, "glass")
        hip(b, mx - 1, (s.z0 + s.z1) // 2 - 1, mx + 1, (s.z0 + s.z1) // 2 + 1, top + 2, pick(rng, st.roofs))
    else:
        gable(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), along="z", fill=lk.infill, pitch=2)
    for x in (mx - 2, mx + 1):
        wall_torch(b, x, 3, s.z0 - 1, st, "north")
    b.sign(mx + 1, 2, s.z0 - 1, "north", ["Public~Baths", "", "", ""], lk.wood)
    b.sign(mx - 2, 2, s.z0 - 1, "north", ["Water is~changed daily.~Nobody has~used it.", "", "", ""], lk.wood)
    return b


@gen("granary", freq="average", n=3)
def granary(rng, st):
    """A round silo with a cone roof and a lean-to, or a timber granary up on staddle stones."""
    if rng.random() < 0.5:
        r = 3
        W, D = 2 * r + 7, 2 * r + 4
        b = new("Granary", "city", W, D)
        cx, cz = r + 1, r + 2
        wall = brick(rng, st)
        h = rng.randint(9, 13)
        for dx, dz in disc(r):
            b.set(cx + dx, 0, cz + dz, "spruce_planks" if st.key != "ochre" else "packed_mud")
        for dx, dz in circle(r):
            b.fill(cx + dx, 1, cz + dz, cx + dx, h, cz + dz, wall)
        for y in range(4, h, 3):   # slits
            for dx, dz in ((r, 0), (-r, 0), (0, r)):
                b.set(cx + dx, y, cz + dz, "iron_bars", **WL)
        for y in range(1, 4):   # grain inside
            for dx, dz in disc(r - 1):
                if (dx, dz) != (0, -r + 1) and rng.random() < (0.9 if y < 3 else 0.5) \
                        and (y == 1 or b.get(cx + dx, y - 1, cz + dz) == "hay_block"):
                    b.set(cx + dx, y, cz + dz, "hay_block", axis="y")
        b.door(cx, 1, cz - r, "south", pick(rng, st.woods))
        b.path(cx, 0, cx, cz - r - 1)
        b.set(cx, 3, cz - r - 1, st.wall_torch, facing="north")
        cone(b, cx, cz, h + 1, r + 1, pick(rng, st.roofs))
        b.fill(cx - r + 1, h, cz - r + 1, cx + r - 1, h, cz + r - 1, "air")
        hanging(b, cx, h, cz, st)
        # the lean-to on its east side
        lk = look(rng, st)
        sx0, sx1 = cx + r, W - 2
        s = Shell(sx0, cz - 2, sx1, cz + 2, 1)
        shell(b, s, lk)
        b.clear(sx0 + 1, 1, s.z0, sx1 - 1, 3, s.z0)
        b.path(sx0 + 1, 0, sx1 - 1, s.z0)
        for z in range(s.z0 + 1, s.z1):
            b.barrel(sx1 - 1, 1, z, [("wheat", rng.randint(8, 32))] if rng.random() < 0.4 else [])
        b.set(sx0 + 1, 1, s.z1 - 1, "composter", level=str(rng.randint(0, 7)))
        hang(b, sx0 + 1, s.z0 + 2, 1, st)
        shed(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), high="west", over=1)
        b.set(cx, 4, cz - r, wall)
        return b
    # staddle granary: a timber box on stone mushrooms, steps up to its door
    W, D = rng.choice((7, 9)), rng.choice((5, 7))
    b = new("Granary", "city", W + 2, D + 5)
    lk = look(rng, st, timbered=True)
    x0, z0, x1, z1 = 1, 3, W, D + 2
    pier = wall_of(pick(rng, st.found))
    cap = mat_of(pick(rng, st.found), "stone_brick").slab
    for x in range(x0, x1 + 1, 2 if W % 2 else 3):
        for z in (z0, z1) if D < 7 else (z0, (z0 + z1) // 2, z1):
            b.set(x, 1, z, pier, **WL)
            slab(b, x, 2, z, cap, "bottom")
    storey(b, x0, z0, x1, z1, 3, 7, lk)
    mx = (x0 + x1) // 2
    b.door(mx, 4, z0, "south", lk.wood)
    b.set(mx, 6, z0, lk.log, axis="x")
    for i, (y, z) in enumerate(((3, z0 - 1), (2, z0 - 2), (1, z0 - 3))):
        stair(b, mx, y, z, f"{lk.wood}_stairs", "south")
        if y > 1:
            b.fill(mx, 1, z, mx, y - 1, z, lk.found)
    b.path(mx, 0, mx, z0 - 3)
    for z in range(z0 + 1, z1):
        for x in range(x0 + 1, x1):
            if (x, z) != (mx, z0 + 1) and rng.random() < 0.7:
                b.set(x, 4, z, "hay_block", axis="x")
                if rng.random() < 0.4:
                    b.set(x, 5, z, "hay_block", axis="z")
    b.barrel(x0 + 1, 4, z0 + 1, [("wheat", rng.randint(8, 40))])
    light(b, x0 + 1, 5, z0 + 1, st)   # on the barrel
    for z in range(z0 + 1, z1, 2):
        b.set(x0, 5, z, f"{lk.wood}_trapdoor", facing="west", half="bottom", open="true", powered="false", **WL)
        b.set(x1, 5, z, f"{lk.wood}_trapdoor", facing="east", half="bottom", open="true", powered="false", **WL)
    gable(b, x0, z0, x1, z1, 7, pick(rng, st.roofs), along="x", fill=lk.infill, pitch=1)
    return b


@gen("dovecote", freq="average", n=3)
def dovecote(rng, st):
    """A round or square tower of nesting holes in a little walled garden."""
    S = 11
    b = new("Dovecote", "city", S, S)
    c = S // 2
    wall = brick(rng, st)
    h = rng.randint(7, 9)
    m = pick(rng, st.roofs)
    roundish = rng.random() < 0.5
    cells = circle(2) if roundish else [(dx, dz) for dx in range(-2, 3) for dz in range(-2, 3) if max(abs(dx), abs(dz)) == 2]
    for dx, dz in cells:
        b.fill(c + dx, 1, c + dz, c + dx, h, c + dz, wall)
    b.fill(c - 1, 0, c - 1, c + 1, 0, c + 1, "coarse_dirt")
    for y in range(4, h, 2):   # nest holes, a perch under each
        for i, (dx, dz) in enumerate(cells):
            if (i + y) % 2 == 0 and (dx == 0 or dz == 0 or not roundish):
                if abs(dx) == 2 and abs(dz) == 2:
                    continue
                b.set(c + dx, y, c + dz, "air")
    for dx, dz in cells:   # a string course
        if abs(dx) == 2 and abs(dz) == 2:
            continue
        f = toward(dx, dz)
        stair(b, c + dx + STEP[OPPOSITE[f]][0], 3, c + dz + STEP[OPPOSITE[f]][1], mat_of(wall).stairs, f, "top")
    b.door(c, 1, c - 2, "south", pick(rng, st.woods))
    b.path(c, 0, c, c - 3)
    b.set(c, 3, c - 3, "air")
    if roundish:
        top = cone(b, c, c, h + 1, 3, m)
    else:
        b.fill(c - 2, h + 1, c - 2, c + 2, h + 1, c + 2, f"{pick(rng, st.woods)}_planks")
        top = hip(b, c - 2, c - 2, c + 2, c + 2, h + 1, m)
    b.fill(c, top + 1, c, c, top + 1, c, f"{pick(rng, st.woods)}_fence", **WL)
    b.set(c, top + 2, c, "lightning_rod", facing="up", powered="false", **WL)
    for y in range(2, h, 2):   # nesting boxes inside
        for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            if rng.random() < 0.5:
                slab(b, c + dx, y, c + dz, f"{pick(rng, st.woods)}_slab")
    b.set(c, 1, c + 1, "hay_block", axis="y")
    hanging(b, c, h, c, st)
    # the garden around it: a low wall, a gate, a bench and flowers
    lw = wall_of(pick(rng, st.found))
    for i in range(S):
        for x, z in ((i, 0), (i, S - 1), (0, i), (S - 1, i)):
            if not (z == 0 and abs(x - c) <= 0):
                b.set(x, 1, z, lw, **WL)
    b.path(c, 0, c, 0)
    for x, z in ((c - 1, 0), (c + 1, 0)):
        light(b, x, 2, z, st)
    flowers(b, rng, st, [(x, z) for x in range(1, S - 1) for z in range(1, S - 1)], 0.35)
    b.set(2, 1, S - 2, "composter", level="3")
    return b


@gen("market_hall", freq="average", n=3)
def market_hall(rng, st):
    """Stalls under an open ground floor of posts, a council room above them."""
    W, D = rng.choice((11, 13, 15)), rng.choice((7, 9))
    yard = 1
    b = new("Market Hall", "city", W + 2, yard + D + 1)
    lk = look(rng, st)
    pillar = pick(rng, st.stone)
    x0, z0, x1, z1 = 1, yard, W, yard + D - 1
    b.fill(x0, 0, z0, x1, 0, z1, PATH)
    cols = [(x, z) for x in range(x0, x1 + 1, 2 if (x1 - x0) % 4 else 4) for z in (z0, z1)]
    cols += [(x, z) for z in range(z0 + 2, z1 - 1, 2 if (z1 - z0) % 4 else 4) for x in (x0, x1)]
    for x, z in set(cols):
        b.fill(x, 1, z, x, 3, z, pillar)
    for x, z in set(cols):   # arch springers
        for f in ("east", "west") if z in (z0, z1) else ("north", "south"):
            dx, dz = STEP[f]
            if b.empty(x + dx, 3, z + dz) and x0 <= x + dx <= x1 and z0 <= z + dz <= z1:
                stair(b, x + dx, 3, z + dz, mat_of(pillar).stairs, OPPOSITE[f], "top")
    storey(b, x0, z0, x1, z1, 4, 8, lk)
    up = Shell(x0, z0, x1, z1, 2, 4)
    windows(b, up, lk, floors=[1], every=2)
    # stalls under the hall
    goods = ("melon", "pumpkin", "hay_block", "barrel", "composter", "barrel", "cake", "barrel")
    for x in range(x0 + 2, x1 - 1, 3):
        for z in (z0 + 2, z1 - 2):
            for dx in (0, 1):
                g = pick(rng, goods)
                if g == "barrel":
                    b.barrel(x + dx, 1, z, [(pick(rng, ("apple", "bread", "carrot", "potato", "beetroot", "cookie")), rng.randint(2, 12))])
                elif g == "hay_block":
                    b.set(x + dx, 1, z, g, axis="y")
                elif g == "composter":
                    b.set(x + dx, 1, z, g, level=str(rng.randint(0, 7)))
                elif g == "cake":
                    slab(b, x + dx, 1, z, f"{lk.wood}_slab", "top")
                    b.set(x + dx, 2, z, "cake", bites="0")
                else:
                    b.set(x + dx, 1, z, g)
    for x in range(x0 + 2, x1 - 1, 4):
        hang(b, x, (z0 + z1) // 2, 1, st)
    # up a ladder by the back middle post to the hall
    lxp = next(x for x, z in sorted(cols) if z == z1 and x >= (x0 + x1) // 2)
    ladder(b, lxp, z1 - 1, 1, 4, "north")
    room = Room(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 5, {(lxp, z1 - 1), (lxp, z1 - 2)})
    for x in range(x0 + 3, x1 - 2):
        slab(b, x, 5, (z0 + z1) // 2, f"{lk.wood}_slab", "top")
    for x in range(x0 + 3, x1 - 2, 2):
        for dz in (-1, 1):
            stair(b, x, 5, (z0 + z1) // 2 + dz, f"{lk.wood}_stairs", "north" if dz < 0 else "south")
    furnish(room, rng, st, lk, ["lectern", "shelf", "chest", WOOL, WOOL, "plant"], light=False)
    for x in range(x0 + 2, x1 - 1, 4):
        hang(b, x, (z0 + z1) // 2, 5, st)
    m = pick(rng, st.roofs)
    if rng.random() < 0.5 or W - D > 6:
        ridge = gable(b, x0, z0, x1, z1, 8, m, along="x", fill=lk.infill, pitch=2 if rng.random() < st.pitch2 else 1)
    else:
        ridge = hip(b, x0, z0, x1, z1, 8, m)
    mx = (x0 + x1) // 2
    if rng.random() < 0.7:   # a little bell turret on the ridge
        b.fill(mx, ridge + 1, (z0 + z1) // 2, mx, ridge + 2, (z0 + z1) // 2, f"{lk.wood}_fence", **WL)
        b.set(mx, ridge + 3, (z0 + z1) // 2, m.slab, type="bottom", **WL)
    b.sign(mx, 4, z0 - 1, "north", ["2Market Hall~of the~[adjectives] Ward", "", "", ""], lk.wood)
    return b


@gen("mill_house", freq="rare", n=3)
def mill_house(rng, st):
    """A mill on a stone-lined race, its waterwheel turning on an axle out of the wall."""
    W, D = rng.choice((7, 8)), rng.randint(7, 9)
    yard = 2
    b = new("Mill", "city", W + 7, yard + D + 2)
    lk = look(rng, st)
    s = Shell(1, yard, W, yard + D - 1, 2)
    shell(b, s, lk)
    dx = front_door(b, s, lk, s.x0 + 2)
    windows(b, s, lk, sides=("north", "south", "west"), skip={(dx, s.z0)}, every=2)
    lining = pick(rng, st.found)
    # the race: banks either side of a channel running front to back
    rx = s.x1 + 3
    b.fill(rx - 1, -3, 0, rx + 1, 0, b.l - 1, lining)
    b.fill(rx, -2, 1, rx, 0, b.l - 2, "water", level="0")   # closed at both ends: no water in the street
    for x in (rx - 1, rx + 1):
        for z in range(b.l):
            if z % 3 == 0:
                b.set(x, 1, z, wall_of(lining), **WL)
    # the wheel in the race: rim, spokes, hub on an axle into the mill
    cy, cz = 3, (s.z0 + s.z1) // 2
    wood = lk.wood
    for dy, dz in circle(3):
        b.set(rx, cy + dy, cz + dz, f"stripped_{wood}_log" if wood != "pale_oak" else "stripped_pale_oak_log", axis="x")
    for i in range(1, 3):
        for dy, dz in ((i, 0), (-i, 0), (0, i), (0, -i)):
            b.set(rx, cy + dy, cz + dz, f"{wood}_fence", **WL)
    for x in range(s.x1 + 1, rx + 1):
        b.set(x, cy, cz, lk.log, axis="x")
    for dy, dz in circle(3):   # paddles in the water
        if cy + dy <= 0:
            b.set(rx, cy + dy, cz + dz, f"{wood}_planks")
    # millstones and sacks inside, sleeping loft above
    for x, z in ((s.x1 - 1, cz), (s.x1 - 1, cz + 1)):
        b.set(x, 1, z, "grindstone", face="floor", facing="west")
    b.set(s.x1 - 2, 1, cz, "stonecutter", facing="east")
    lx = s.x0 + 1
    ladder(b, lx, s.z1 - 1, 1, s.h, "north")
    room = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, 1, {(dx, s.z0 + 1), (lx, s.z1 - 1), (lx, s.z1 - 2)})
    furnish(room, rng, st, lk, ["barrel", "hay_block", "barrel", "chest", "composter"])
    up = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, s.h + 1, {(lx, s.z1 - 1), (lx, s.z1 - 2)})
    furnish(up, rng, st, lk, ["bed", "chest", "hay_block", "hay_block", "plant"])
    top = gable(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), along="z", fill=lk.infill,
                pitch=2 if rng.random() < st.pitch2 else 1)
    chimney(b, s.x0 + 1, s.z1, s.top, top + 1, pick(rng, ("bricks", lk.found)))
    b.sign(dx + 1, 2, s.z0 - 1, "north", ["2[surnames]'s~Mill", "", "", ""], lk.wood)
    return b


@gen("barracks", freq="rare", n=2)
def barracks(rng, st):
    """A long stone barracks: mess and armoury below, rows of beds above, a drill yard in front
    with straw dummies, a flag on the battlements."""
    W, D = rng.choice((13, 15)), rng.choice((8, 9))
    yard = 5
    b = new("Barracks", "city", W + 2, yard + D + 1)
    lk = look(rng, st, timbered=False)
    lk.infill = stone(rng, st)
    s = Shell(1, yard, W, yard + D - 1, 2)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    front_door(b, s, lk, mx)
    windows(b, s, lk, skip={(mx, s.z0), (mx - 1, s.z0), (mx + 1, s.z0)}, every=2)
    for x in range(s.x0, s.x1 + 1):
        for y in (2, 6):
            if b.get(x, y, s.z0).endswith("pane"):
                b.set(x, y, s.z0, "iron_bars", **WL)
    # mess hall: a long table; armoury racks at the ends
    tz = (s.z0 + s.z1) // 2 + 1
    for x in range(s.x0 + 3, s.x1 - 2):
        slab(b, x, 1, tz, f"{lk.wood}_slab", "top")
        if x % 2:
            stair(b, x, 1, tz - 1, f"{lk.wood}_stairs", "north")
            stair(b, x, 1, tz + 1, f"{lk.wood}_stairs", "south")
    lx = s.x1 - 1
    ladder(b, lx, s.z0 + 1, 1, s.h, "south")
    room = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, 1, {(mx, s.z0 + 1), (lx, s.z0 + 1), (lx, s.z0 + 2)})
    furnish(room, rng, st, lk, ["anvil", "grindstone", "smithing_table", "chest", "barrel", "barrel", "furnace"], light=False)
    for x in range(s.x0 + 2, s.x1 - 1, 4):
        hang(b, x, tz, 1, st)
    # dormitory: beds along both long walls
    y = s.h + 1
    for x in range(s.x0 + 1, s.x1 - 1, 2):
        b.bed(x, y, s.z0 + 2, "north", pick(rng, st.beds))
        if x != lx:
            b.bed(x, y, s.z1 - 2, "south", pick(rng, st.beds))
        if x + 1 < s.x1 - 1 and rng.random() < 0.5:
            b.chest(x + 1, y, s.z1 - 1, "north")
    for x in range(s.x0 + 2, s.x1 - 1, 4):
        hang(b, x, (s.z0 + s.z1) // 2, y, st)
    # battlements
    flat(b, s.x0, s.z0, s.x1, s.z1, s.top, lk.infill)
    for x in range(s.x0, s.x1 + 1):
        for z in (s.z0, s.z1):
            if (x - s.x0) % 2 == 0:
                b.set(x, s.top + 2, z, lk.infill)
    for z in range(s.z0, s.z1 + 1):
        for x in (s.x0, s.x1):
            if (z - s.z0) % 2 == 0:
                b.set(x, s.top + 2, z, lk.infill)
    b.set(lx, s.top, s.z0 + 1, f"{lk.wood}_trapdoor", facing="south", half="top", open="false", powered="false", **WL)
    ladder(b, lx, s.z0 + 1, s.h + 1, s.top - 1, "south")
    fx, fz = s.x0 + 1, s.z1 - 1
    b.fill(fx, s.top + 1, fz, fx, s.top + 7, fz, f"{lk.wood}_fence", **WL)
    b.fill(fx + 1, s.top + 5, fz, fx + 3, s.top + 7, fz, WOOL)
    # the drill yard
    b.fill(1, 0, 1, W, 0, yard - 2, "coarse_dirt")
    b.path(mx, 0, mx, yard - 1)
    for x in range(2, W, 3):
        if abs(x - mx) <= 1:
            continue
        if rng.random() < 0.5:
            b.set(x, 1, 2, f"{lk.wood}_fence", **WL)
            b.set(x, 2, 2, "hay_block", axis="y")
            b.set(x, 3, 2, "carved_pumpkin", facing="north")
        else:
            b.set(x, 1, 2, "target", power="0")
    for x in (mx - 2, mx + 2):
        lamp_post(b, x, yard - 1, st, lk.wood)
    b.sign(mx + 1, 2, s.z0 - 1, "north", ["Barracks", "", "", ""], lk.wood)
    b.sign(mx - 1, 2, s.z0 - 1, "north", ["2[barracks]", "", "", ""], lk.wood)
    return b


@gen("stables", freq="average", n=3)
def stables(rng, st):
    """A row of stalls open to the street behind gates, a tack room at one end, a hay loft."""
    n = rng.randint(3, 4)
    W, D = 4 * n + 4, 7
    yard = 2
    b = new("Stables", "city", W + 2, W + 2)   # square already: a paddock fills the back
    lk = look(rng, st, timbered=True)
    s = Shell(1, yard, W, yard + D - 1, 1, h=5)
    b.fill(s.x0, 0, s.z0, s.x1, 0, s.z1, "coarse_dirt")
    b.ring(s.x0, s.z0, s.x1, s.z1, 0, 0, lk.found)
    # back and end walls, posts along the open front
    b.fill(s.x0, 1, s.z1, s.x1, s.top, s.z1, lk.infill)
    b.fill(s.x0, 1, s.z0, s.x0, s.top, s.z1, lk.infill)
    b.fill(s.x1, 1, s.z0, s.x1, s.top, s.z1, lk.infill)
    b.ring(s.x0, s.z0, s.x1, s.z1, s.top, s.top, lk.log, axis="x")
    for x in range(s.x0, s.x1 + 1, 4):
        b.fill(x, 1, s.z0, x, s.top - 1, s.z0, lk.log, axis="y")
        b.fill(x, 1, s.z1, x, s.top - 1, s.z1, lk.log, axis="y")
    # the tack room at the east end
    tx = s.x1 - 4
    b.fill(tx, 1, s.z0, tx, s.top, s.z1, lk.infill)
    b.fill(tx, 1, s.z0, s.x1, s.top - 1, s.z0, lk.infill)
    b.fill(tx + 1, 0, s.z0 + 1, s.x1 - 1, 0, s.z1 - 1, lk.floor)
    b.set(tx + 2, 0, s.z0, lk.floor)
    b.door(tx + 2, 1, s.z0, "south", lk.wood)
    b.path(tx + 2, 0, tx + 2, 0)
    b.set(tx + 3, 2, s.z0, "glass_pane")
    b.set(tx + 1, 2, s.z0, "glass_pane")
    tack = Room(b, tx + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, 1, {(tx + 2, s.z0 + 1)})
    furnish(tack, rng, st, lk, ["chest", "barrel", "anvil", "barrel", "crafting_table"], light=False)
    hanging(b, tx + 2, s.top - 1, s.z0 + 3, st)
    # stalls: fence partitions, a manger of hay and a trough at the back, a gate at the front
    for i in range(n):
        x0 = s.x0 + 4 * i
        for z in range(s.z0 + 1, s.z1):
            if i > 0:
                b.set(x0, 1, z, f"{lk.wood}_fence", **WL)
        for x in range(x0 + 1, x0 + 4):
            if x >= tx:
                continue
            b.set(x, 1, s.z0, f"{lk.wood}_fence_gate" if x == x0 + 2 else f"{lk.wood}_fence",
                  **({"facing": "north", "in_wall": "false", "open": "false", "powered": "false"} if x == x0 + 2 else WL))
        if x0 + 3 < tx:
            b.set(x0 + 1, 1, s.z1 - 1, "hay_block", axis="y")
            b.set(x0 + 3, 1, s.z1 - 1, "water_cauldron", level="3")
            b.set(x0 + 2, 1, s.z1 - 1, "composter", level=str(rng.randint(0, 6)))
            hanging(b, x0 + 2, s.top - 1, s.z0 + 3, st)
            b.path(x0 + 2, 0, x0 + 2, s.z0 - 1)
    # the hay loft under the roof
    for x in range(s.x0 + 1, s.x1):
        for z in range(s.z0 + 3, s.z1):
            b.set(x, s.top, z, f"{lk.wood}_planks")
            if rng.random() < 0.6 and x < tx:
                b.set(x, s.top + 1, z, "hay_block", axis="x")
    m = pick(rng, st.roofs)
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along="x", fill=lk.infill, pitch=1, over=1)
    b.set(s.x0 + 1, 1, s.z0 - 1, "water_cauldron", level="3")
    b.sign(tx + 1, 3, s.z0 - 1, "north", ["2[surnames]~Livery", "", "", ""], lk.wood)
    # the paddock behind, through a gate in the back wall of the middle stall
    pz0, pz1 = s.z1 + 2, b.l - 2
    for x in range(s.x0, s.x1 + 1):
        for z in range(pz0, pz1 + 1):
            if x in (s.x0, s.x1) or z in (pz0, pz1):
                b.set(x, 1, z, f"{lk.wood}_fence", **WL)
            elif rng.random() < 0.25:
                b.set(x, 0, z, "coarse_dirt")
            elif rng.random() < 0.15:
                b.set(x, 1, z, "short_grass" if st.key != "ochre" else "short_dry_grass")
    gx = s.x0 + 2
    b.set(gx, 1, s.z1, f"{lk.wood}_fence_gate", facing="north", in_wall="false", open="false", powered="false")
    b.set(gx, 1, s.z1 - 1, "air")
    b.set(gx, 2, s.z1, "air")
    b.set(gx, 1, pz0, f"{lk.wood}_fence_gate", facing="north", in_wall="false", open="false", powered="false")
    b.path(gx, s.z1, gx, pz0)
    b.set(s.x1 - 2, 1, pz1 - 1, "water_cauldron", level="3")
    b.set(s.x1 - 3, 1, pz1 - 1, "hay_block", axis="y")
    if pz1 - pz0 > 6:
        tree(b, rng, s.x0 + 3, pz1 - 3, pick(rng, st.trees), size=4)
    return b


@gen("coaching_inn", freq="rare", n=3)
def coaching_inn(rng, st):
    """An inn around a courtyard: the front range over a carriage arch, a guest wing on one side,
    a stable wing on the other, a well in the yard."""
    W, D = rng.choice((19, 21)), rng.choice((15, 17))
    yard = 2
    b = new("Coaching Inn", "city", W + 2, yard + D + 1)
    lk = look(rng, st)
    x0, x1, z0, z1 = 1, W, yard, yard + D - 1
    fd = 6
    front = Shell(x0, z0, x1, z0 + fd - 1, 2)
    ww = 6
    west = Shell(x0, front.z1, x0 + ww - 1, z1, 1)
    east = Shell(x1 - ww + 1, front.z1, x1, z1, 1)
    m = pick(rng, st.roofs)
    # the wings first, so the front range's walls cut their roofs off
    shell(b, west, lk)
    windows(b, west, lk, sides=("west", "east"), every=2)
    gable(b, west.x0, west.z0, west.x1, west.z1, west.top, m, along="z", fill=lk.infill, pitch=1)
    b.fill(east.x0, 0, east.z0, east.x1, 0, east.z1, "coarse_dirt")
    b.ring(east.x0, east.z0, east.x1, east.z1, 0, 0, lk.found)
    b.fill(east.x1, 1, east.z0, east.x1, east.top, east.z1, lk.infill)
    b.fill(east.x0, 1, east.z1, east.x1, east.top, east.z1, lk.infill)
    for z in range(east.z0, east.z1 + 1, 3):
        b.fill(east.x0, 1, z, east.x0, east.top - 1, z, lk.log, axis="y")
    b.fill(east.x0, east.top, east.z0, east.x0, east.top, east.z1, lk.log, axis="z")
    for z in range(east.z0 + 2, east.z1 - 1, 3):
        b.fill(east.x0 + 2, 1, z, east.x1 - 1, 1, z, f"{lk.wood}_fence", **WL)
        b.set(east.x1 - 1, 1, z + 1, "hay_block", axis="y")
    gable(b, east.x0, east.z0, east.x1, east.z1, east.top, m, along="z", fill=lk.infill, pitch=1)
    # the back wall closing the yard
    yw = wall_of(lk.found)
    for x in range(west.x1 + 1, east.x0):
        b.set(x, 1, z1, lk.found)
        b.set(x, 2, z1, yw, **WL)
    # the front range over the arch
    shell(b, front, lk)
    mx = (x0 + x1) // 2
    b.clear(mx - 1, 1, front.z0, mx + 1, 3, front.z1)
    b.fill(mx - 1, 0, 0, mx + 1, 0, front.z1, PATH)
    for z in (front.z0, front.z1):
        stair(b, mx - 1, 3, z, mat_of(lk.found, "stone_brick").stairs, "east", "top")
        stair(b, mx + 1, 3, z, mat_of(lk.found, "stone_brick").stairs, "west", "top")
    b.fill(mx - 2, 1, front.z0 + 1, mx - 2, 3, front.z1 - 1, lk.infill)
    b.fill(mx + 2, 1, front.z0 + 1, mx + 2, 3, front.z1 - 1, lk.infill)
    hanging(b, mx, 3, (front.z0 + front.z1) // 2, st)
    tdx = front_door(b, front, lk, mx - 5)
    windows(b, front, lk, skip={(tdx, front.z0), (mx - 1, front.z0), (mx, front.z0), (mx + 1, front.z0),
                                (mx - 1, front.z1), (mx, front.z1), (mx + 1, front.z1)}, every=2)
    # taproom (west of the arch) and kitchen (east)
    for x in range(x0 + 2, mx - 3):
        slab(b, x, 1, front.z1 - 2, f"{lk.wood}_slab", "top")
        b.barrel(x, 1, front.z1 - 1, [("honey_bottle", rng.randint(1, 3))] if x % 3 == 0 else [], facing="north")
    for tx in range(x0 + 2, mx - 3, 3):
        if b.empty(tx, 1, front.z0 + 2):
            table(b, tx, 1, front.z0 + 2, lk.wood, chairs=("east", "west"))
    b.set(x0 + 1, 1, front.z0 + 2, "campfire", facing="east", lit="true", signal_fire="false", **WL)
    chimney(b, x0, front.z0 + 2, 1, front.top + 5, "bricks", smoke=True)
    for x in range(x0 + 2, mx - 2, 3):
        hang(b, x, front.z0 + 3, 1, st)
    kitchen = Room(b, mx + 3, front.z0 + 1, x1 - 1, front.z1 - 1, 1, {(mx + 3, front.z1 - 1)})
    furnish(kitchen, rng, st, lk, ["smoker", "furnace", "barrel", "barrel", "crafting_table", "chest", "barrel"])
    b.set(mx + 3, 1, front.z1, "air")   # into the yard
    b.door(mx + 3, 1, front.z1, "north", lk.wood)
    # rooms upstairs, reached by a ladder in the taproom
    lx = mx - 3
    ladder(b, lx, front.z0 + 1, 1, front.h, "south")
    for xa, xb in ((x0 + 1, mx - 2), (mx + 2, x1 - 1)):
        room = Room(b, xa, front.z0 + 1, xb, front.z1 - 1, front.h + 1, {(lx, front.z0 + 1), (lx, front.z0 + 2)})
        furnish(room, rng, st, lk, ["bed", "bed", "chest", "bed", "plant", "barrel"])
    b.fill(mx - 1, front.h + 1, front.z0 + 1, mx + 1, front.h + 1, front.z1 - 1, "air")
    b.fill(mx - 2, front.h + 1, front.z0 + 1, mx - 2, front.top - 1, front.z1 - 1, lk.infill)
    b.set(mx - 2, front.h + 1, front.z0 + 2, "air")
    b.set(mx - 2, front.h + 2, front.z0 + 2, "air")
    # the guest wing: doors and beds off the yard
    b.door(west.x1, 1, (west.z0 + west.z1) // 2, "west", lk.wood)
    gw = Room(b, west.x0 + 1, west.z0 + 1, west.x1 - 1, west.z1 - 1, 1, {(west.x1 - 1, (west.z0 + west.z1) // 2)})
    furnish(gw, rng, st, lk, ["bed", "bed", "bed", "chest", "bed", "barrel"])
    gable(b, front.x0, front.z0, front.x1, front.z1, front.top, m, along="x", fill=lk.infill,
          pitch=2 if rng.random() < st.pitch2 else 1)
    # the yard
    cx, cz = (west.x1 + east.x0) // 2, (front.z1 + z1) // 2 + 1
    b.fill(west.x1 + 1, 0, front.z1 + 1, east.x0 - 1, 0, z1 - 1, PATH)
    b.ring(cx - 1, cz - 1, cx + 1, cz + 1, 1, 1, lk.found)
    b.set(cx, 1, cz, "water", level="0")
    b.set(cx, 0, cz, lk.found)
    for x, z in ((cx - 1, cz - 1), (cx + 1, cz + 1)):
        b.fill(x, 2, z, x, 3, z, f"{lk.wood}_fence", **WL)
    hip(b, cx - 1, cz - 1, cx + 1, cz + 1, 4, m, over=0)
    hanging(b, cx, 4, cz, st)
    b.set(west.x1 + 2, 1, z1 - 2, "hay_block", axis="x")
    b.set(west.x1 + 3, 1, z1 - 2, "hay_block", axis="x")
    b.sign(tdx + 1, 2, front.z0 - 1, "north", ["2The [tavernadj]~[tavernnoun]~Coaching Inn", "", "", ""], lk.wood)
    for x in (mx - 2, mx + 2):
        wall_torch(b, x, 3, front.z0 - 1, st, "north")
    return b


# ── Towers ────────────────────────────────────────────────────────────────

def string_course(b, x0, z0, x1, z1, y, m):
    """A ledge of upside-down stairs around a tower, one block out from its walls."""
    for x in range(x0 - 1, x1 + 2):
        stair(b, x, y, z0 - 1, m.stairs, "south", "top")
        stair(b, x, y, z1 + 1, m.stairs, "north", "top")
    for z in range(z0, z1 + 1):
        stair(b, x0 - 1, y, z, m.stairs, "east", "top")
        stair(b, x1 + 1, y, z, m.stairs, "west", "top")


def belfry(b, x0, z0, x1, z1, y, h, block):
    """Open arches on all four sides from y to y + h - 1: piers left at the corners."""
    for yy in range(y, y + h):
        for x in range(x0 + 1, x1):
            b.set(x, yy, z0, "air")
            b.set(x, yy, z1, "air")
        for z in range(z0 + 1, z1):
            b.set(x0, yy, z, "air")
            b.set(x1, yy, z, "air")


@gen("campanile", freq="rare", n=3, styles=("ochre",))
def campanile(rng, st):
    """A tall slender bell tower in a little piazza: plain shaft, string courses, a double-arched
    belfry and a low pyramid roof."""
    S = 11
    b = new("Campanile", "city", S, S)
    b.fill(0, 0, 0, S - 1, 0, S - 1, PATH)
    wall = pick(rng, ("bricks", "smooth_sandstone", "mud_bricks", "terracotta"))
    trim = pick(rng, ("smooth_sandstone", "cut_sandstone", "white_terracotta", "calcite"))
    tm = mat_of(trim, "smooth_sandstone")
    x0, z0, x1, z1 = 3, 3, 7, 7
    h = rng.randint(20, 25)
    b.fill(x0, 0, z0, x1, 0, z1, trim)
    b.ring(x0, z0, x1, z1, 1, h, wall)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.fill(x, 1, z, x, h, z, trim)
    for y in range(6, h - 5, 6):
        string_course(b, x0, z0, x1, z1, y, tm)
        b.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, "spruce_planks" if rng.random() < 0.5 else "jungle_planks")
        b.set(x0 + 1, y, z1 - 1, "air")
    for y in range(3, h - 6, 4):   # slit windows
        b.set((x0 + x1) // 2, y, z1, "air")
        b.set(x1, y, (z0 + z1) // 2, "air")
        b.set(x0, y, (z0 + z1) // 2, "air")
    # belfry: two arches a side
    by = h - 4
    b.fill(x0 + 1, by - 1, z0 + 1, x1 - 1, by - 1, z1 - 1, "spruce_planks")
    for yy in range(by, by + 3):
        for i in (1, 3):
            b.set(x0 + i, yy, z0, "air")
            b.set(x0 + i, yy, z1, "air")
            b.set(x0, yy, z0 + i, "air")
            b.set(x1, yy, z0 + i, "air")
    b.set((x0 + x1) // 2, h - 1, (z0 + z1) // 2, "bell", attachment="ceiling", facing="north", powered="false")
    b.fill(x0 + 1, h, z0 + 1, x1 - 1, h, z1 - 1, wall)
    string_course(b, x0, z0, x1, z1, h, tm)
    top = hip(b, x0, z0, x1, z1, h + 1, pick(rng, st.roofs), over=1)
    b.set((x0 + x1) // 2, top + 1, (z0 + z1) // 2, "lightning_rod", facing="up", powered="false", **WL)
    ladder(b, x0 + 1, z1 - 1, 1, by - 1, "north")
    door(b, (x0 + x1) // 2, z0, pick(rng, st.woods))
    b.set((x0 + x1) // 2, 0, z0, trim)
    b.path((x0 + x1) // 2, 0, (x0 + x1) // 2, 0)
    hang(b, (x0 + x1) // 2, (z0 + z1) // 2, 1, st, height=5)
    for x, z in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        lamp_post(b, x, z, st, "acacia")
    for x, z in ((1, 5), (S - 2, 5)):
        tree(b, rng, x, z, "cypress", size=rng.randint(6, 8))
        b.set(x, 0, z, GROUND)
    b.sign((x0 + x1) // 2 + 1, 2, z0 - 1, "north", ["The bell~rings when~someone~moves in", "", "", ""], "acacia")
    return b


def clock_face(b, cx, cy, z, x_axis=True, hour=0):
    """A clock on a wall: a white face, a dark rim, two hands."""
    for d1 in range(-2, 3):
        for d2 in range(-2, 3):
            if abs(d1) == 2 and abs(d2) == 2:
                continue
            rim = abs(d1) == 2 or abs(d2) == 2
            x, y, zz = (cx + d1, cy + d2, z) if x_axis else (cx, cy + d2, z + d1)
            b.set(x, y, zz, "polished_blackstone" if rim else "white_concrete")
    hands = [(0, 0), (0, 1), ((1, 0), (-1, 0), (0, -1))[hour % 3]]
    for d1, d2 in hands:
        x, y, zz = (cx + d1, cy + d2, z) if x_axis else (cx, cy + d2, z + d1)
        b.set(x, y, zz, "black_concrete")


@gen("clock_tower", freq="very rare", n=3, unique=True)
def clock_tower(rng, st):
    """The city's clock: a square tower on a plinth, a clock on every face, all of them stopped
    at a different time; an open belfry and a spire above."""
    S = 13
    b = new("Clock Tower", "city", S, S)
    b.fill(0, 0, 0, S - 1, 0, S - 1, PATH)
    wall = stone(rng, st)
    trim = pick(rng, st.found)
    tm = mat_of(trim, "stone_brick")
    x0, z0, x1, z1 = 3, 3, 9, 9
    mx = (x0 + x1) // 2
    h = rng.randint(24, 28)
    # plinth with steps
    b.fill(x0 - 1, 0, z0 - 1, x1 + 1, 0, z1 + 1, trim)
    for x in range(x0 - 2, x1 + 3):
        stair(b, x, 0, z0 - 2, tm.stairs, "south")
    b.ring(x0, z0, x1, z1, 1, h, wall)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.fill(x, 1, z, x, h, z, trim)
    b.ring(x0, z0, x1, z1, 1, 1, trim)
    for y in (8, 15):
        string_course(b, x0, z0, x1, z1, y, tm)
    for y in range(3, 15, 4):   # tall narrow windows
        for x, z in ((mx, z1), (x0, mx), (x1, mx)):
            b.set(x, y, z, "glass_pane")
            b.set(x, y + 1, z, "glass_pane")
    for y in range(4, 16, 4):   # floors with a hatch
        b.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, f"{pick(rng, st.woods)}_planks")
    cy = h - 6
    hours = rng.sample(range(3), 3) + [rng.randrange(3)]
    clock_face(b, mx, cy, z0 - 1, True, hours[0])
    clock_face(b, mx, cy, z1 + 1, True, hours[1])
    clock_face(b, x0 - 1, cy, mx, False, hours[2])
    clock_face(b, x1 + 1, cy, mx, False, hours[3])
    b.fill(x0 + 1, cy - 3, z0 + 1, x1 - 1, cy - 3, z1 - 1, "spruce_planks")
    for x in range(x0 + 2, x1 - 1):   # the works
        for z in range(z0 + 2, z1 - 1):
            if (x + z) % 2 == 0:
                b.set(x, cy - 2, z, pick(rng, ("copper_block", "exposed_copper", "iron_block")))
    b.set(mx, cy - 1, mx, "observer", facing="up", powered="false")
    b.set(mx, cy, mx, "piston", facing="up", extended="false")
    # belfry and spire
    by = h - 2
    b.fill(x0 + 1, by, z0 + 1, x1 - 1, by, z1 - 1, trim)
    b.ring(x0, z0, x1, z1, by + 1, by + 5, wall)
    belfry(b, x0 + 1, z0, x1 - 1, z1, by + 1, 4, wall)
    belfry(b, x0, z0 + 1, x1, z1 - 1, by + 1, 4, wall)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.fill(x, by + 1, z, x, by + 5, z, trim)
    for x in range(x0 + 1, x1):
        for z in (z0, z1):
            b.set(x, by + 4, z, wall)
            b.set(x, by + 5, z, wall)
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            b.set(x, by + 4, z, wall)
            b.set(x, by + 5, z, wall)
    b.set(mx, by + 4, mx, "bell", attachment="ceiling", facing="north", powered="false")
    b.fill(x0 + 1, by + 5, z0 + 1, x1 - 1, by + 5, z1 - 1, wall)
    string_course(b, x0, z0, x1, z1, by + 5, tm)
    top = spire(b, x0, z0, x1, z1, by + 6, pick(rng, st.roofs), pitch=2, over=0)
    b.set(mx, top + 1, mx, "lightning_rod", facing="up", powered="false", **WL)
    hanging(b, mx, by + 3, mx, st)
    # the way in and up
    door(b, mx, z0, pick(rng, st.woods))
    b.set(mx, 0, z0, trim)
    b.path(mx, 0, mx, z0 - 3)
    ladder(b, x1 - 1, z1 - 1, 1, by, "north")
    hang(b, mx, mx, 1, st)
    for x, z in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        lamp_post(b, x, z, st, pick(rng, st.woods), h=3)
    b.sign(mx + 1, 2, z0 - 1, "north", ["2[clock]", "", "", ""], pick(rng, st.woods))
    return b


# ── Halls ─────────────────────────────────────────────────────────────────

@gen("theatre", freq="very rare", n=2, unique=True)
def theatre(rng, st):
    """A stage house on the street, a half bowl of seats behind it rising away from the stage:
    the audience comes in through the stage doors."""
    W, D = 25, 21
    b = new("Theatre", "city", W, D)
    wall = stone(rng, st)
    trim = pick(rng, st.found)
    tm = mat_of(wall)
    seat = mat_of(pick(rng, st.woods) + "_planks") if rng.random() < 0.5 else mat_of(trim, "stone_brick")
    cx, cz = W // 2, 7
    sz0, sz1 = 6, 7
    rows = range(4, 11)
    # the bowl: each ring one higher than the last, filled underneath
    for dx in range(-11, 12):
        for dz in range(0, 12):
            d = math.sqrt(dx * dx + dz * dz)
            r = int(round(d))
            x, z = cx + dx, cz + dz
            if not b.inside(x, z) or z >= D - 1:
                continue
            if r < 4:
                b.set(x, 0, z, PATH)
            elif r <= 10 and z > sz1:
                y = r - 3
                b.fill(x, 1, z, x, y - 1, z, trim)
                if dx == 0:   # the aisle steps
                    stair(b, x, y, z, tm.stairs, OPPOSITE[toward(dx, dz)])
                else:
                    stair(b, x, y, z, seat.stairs, OPPOSITE[toward(dx, dz)])
            elif r == 11:
                b.fill(x, 1, z, x, 8, z, wall)
                if (dx + dz) % 3 == 0:
                    b.set(x, 9, z, wall)
    for (x, y, z), (n, _) in list(b.blocks.items()):   # lamps along the top of the outer wall
        if y == 8 and n == wall and (x - cx) % 5 == 0 and z > sz1 + 1 and b.empty(x, 9, z):
            light(b, x, 9, z, st)
    # the stage
    for x in range(cx - 6, cx + 7):
        for z in range(sz0, sz1 + 1):
            b.set(x, 1, z, "acacia_planks" if st.key == "ochre" else "spruce_planks")
    for x in range(cx - 1, cx + 2):
        stair(b, x, 1, sz1 + 1, tm.stairs, "north")
    # the stage house: a tall facade on the street, doors onto the stage behind it
    hx0, hx1, hz0, hz1 = 2, W - 3, 2, 5
    b.fill(hx0, 0, hz0, hx1, 0, hz1, trim)
    b.ring(hx0, hz0, hx1, hz1, 1, 10, wall)
    b.fill(hx0 + 1, 5, hz0 + 1, hx1 - 1, 5, hz1 - 1, f"{pick(rng, st.woods)}_planks")
    for x in range(hx0, hx1 + 1, 3):   # pilasters on the front, columns on the stage side
        if abs(x - cx) % 4:
            b.fill(x, 1, hz0 - 1, x, 9, hz0 - 1, trim)
        b.fill(x, 1, hz1, x, 9, hz1, trim)
    for x in range(hx0 + 1, hx1):
        if (x - hx0) % 3:
            for y in (2, 3) if abs(x - cx) > 1 else ():
                b.set(x, y, hz1, WOOL)   # curtains
            b.set(x, 7, hz0, "glass_pane")
    for x in (cx - 4, cx, cx + 4):   # doors through: front and onto the stage
        b.door(x, 1, hz0, "south", pick(rng, st.woods))
        b.path(x, 0, x, 0)
        b.fill(x, 1, hz1, x, 3, hz1, "air")
    flat(b, hx0, hz0, hx1, hz1, 10, wall, cap=tm.slab)
    for x in range(hx0, hx1 + 1):
        stair(b, x, 10, hz0 - 1, tm.stairs, "south", "top")
    hip(b, cx - 2, hz0, cx + 2, hz1, 11, pick(rng, st.roofs), over=0)
    # inside: a dressing room, props
    for x in range(hx0 + 1, hx1):
        if abs(x - cx) > 1 and abs(x - cx + 4) > 0 and abs(x - cx - 4) > 0 and b.empty(x, 1, hz0 + 2):
            r = rng.random()
            if r < 0.15:
                b.chest(x, 1, hz0 + 2, "north")
            elif r < 0.35:
                b.barrel(x, 1, hz0 + 2)
            elif r < 0.5:
                b.set(x, 1, hz0 + 2, "carved_pumpkin", facing="north")
    for x in range(hx0 + 2, hx1 - 1, 4):
        hang(b, x, hz0 + 2, 1, st, height=4)
        hanging(b, x, 9, hz0 + 2, st)
    b.fill(cx - 1, 9, hz1 + 1, cx + 1, 9, hz1 + 1, tm.slab, type="top", **WL)
    for x in (cx - 6, cx + 6):
        light(b, x, 2, sz1, st)
    b.sign(cx + 1, 3, hz0 - 1, "north", ["2[playbill]", "", "", ""], pick(rng, st.woods))
    b.sign(cx - 1, 3, hz0 - 1, "north", ["Theatre", "", "", ""], pick(rng, st.woods))
    return b


@gen("school_house", freq="rare", n=3)
def school_house(rng, st):
    """One schoolroom of desks facing a blackboard, a bell cupola, a fenced playground."""
    W, D = rng.choice((9, 11)), rng.choice((9, 11))
    yard = 2
    pw = 6
    b = new("School House", "city", W + 2 + pw, yard + D + 1)
    lk = look(rng, st)
    s = Shell(1, yard, W, yard + D - 1, 1, h=5)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    front_door(b, s, lk, mx)
    lk.window_tall = True
    windows(b, s, lk, sides=("east", "west"), every=2)
    windows(b, s, lk, sides=("north",), skip={(mx, s.z0)}, every=2)
    # the blackboard on the back wall, the teacher's desk before it
    for x in range(mx - 2, mx + 3):
        b.set(x, 2, s.z1, "black_concrete")
        b.set(x, 3, s.z1, "black_concrete")
    b.set(mx, 1, s.z1 - 2, "lectern", facing="north", has_book="false", powered="false")
    b.set(mx + 1, 1, s.z1 - 2, f"{lk.wood}_slab", type="top", **WL)
    b.set(mx + 1, 2, s.z1 - 2, "candle", candles="1", lit="false", **WL)
    for x in (s.x0 + 1, s.x1 - 1):
        b.set(x, 1, s.z1 - 1, "bookshelf")
        b.set(x, 2, s.z1 - 1, "bookshelf")
    # desks in rows, facing the board
    for z in range(s.z0 + 2, s.z1 - 3, 2):
        for x in range(s.x0 + 1, s.x1):
            if x == mx:
                continue
            slab(b, x, 1, z + 1, f"{lk.wood}_slab", "top")
            stair(b, x, 1, z, f"{lk.wood}_stairs", "north")
    b.chest(s.x0 + 1, 1, s.z0 + 1, "south")
    b.set(s.x1 - 1, 1, s.z0 + 1, potted(rng))
    for z in range(s.z0 + 2, s.z1 - 1, 3):
        hang(b, mx, z, 1, st, height=4)
    m = pick(rng, st.roofs)
    ridge = gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along="z", fill=lk.infill, pitch=1)
    # the bell cupola on the ridge over the door
    cz = s.z0 + 1
    for dx in (-1, 1):
        for dz in (-1, 1):
            b.fill(mx + dx, ridge + 1, cz + 1 + dz, mx + dx, ridge + 2, cz + 1 + dz, f"{lk.wood}_fence", **WL)
    b.set(mx, ridge + 2, cz + 1, "bell", attachment="ceiling", facing="north", powered="false")
    hip(b, mx - 1, cz, mx + 1, cz + 2, ridge + 3, m, over=0)
    b.set(mx, ridge + 3, cz + 1, m.block)   # the bell hangs from the cap
    b.fill(mx - 1, ridge, cz, mx + 1, ridge, cz + 2, m.block)
    # the playground
    px0 = s.x1 + 2
    for x in range(px0, b.w - 1):
        for z in range(yard, s.z1 + 1):
            if x in (px0, b.w - 2) or z in (yard, s.z1):
                b.set(x, 1, z, f"{lk.wood}_fence", **WL)
            else:
                b.set(x, 0, z, "coarse_dirt" if rng.random() < 0.3 else GROUND)
    b.set(s.x1 + 1, 1, s.z1 - 2, "air")
    b.set(px0, 1, s.z1 - 2, f"{lk.wood}_fence_gate", facing="east", in_wall="false", open="false", powered="false")
    b.door(s.x1, 1, s.z1 - 2, "west", lk.wood)
    b.set(s.x1 + 1, 0, s.z1 - 2, PATH)
    sx = px0 + 2   # a swing frame and a see-saw
    b.fill(sx, 1, yard + 2, sx, 3, yard + 2, f"{lk.wood}_fence", **WL)
    b.fill(sx + 2, 1, yard + 2, sx + 2, 3, yard + 2, f"{lk.wood}_fence", **WL)
    b.set(sx + 1, 3, yard + 2, lk.log, axis="x")
    b.set(sx + 1, 2, yard + 2, "iron_chain", axis="y", **WL)
    b.set(sx + 1, 1, yard + 2, f"{lk.wood}_slab", type="top", **WL)
    b.set(sx + 1, 1, s.z1 - 3, f"{lk.wood}_fence", **WL)
    for z in (s.z1 - 4, s.z1 - 3, s.z1 - 2):
        b.set(sx + 1, 2, z, f"{lk.wood}_slab", type="bottom", **WL)
    b.sign(mx + 1, 2, s.z0 - 1, "north", ["2[school]", "", "", ""], lk.wood)
    return b


@gen("almshouse", freq="average", n=3)
def almshouse(rng, st):
    """A row of one-room dwellings behind a shared veranda, a warden's gable in the middle."""
    n = rng.choice((4, 5))
    uw = 4
    W, D = n * uw + 1, 6
    yard = 4
    b = new("Almshouse", "city", W + 2, W + 2)   # square: allotments behind
    lk = look(rng, st)
    s = Shell(1, yard, W, yard + D - 1, 1)
    shell(b, s, lk)
    for i in range(1, n):
        b.fill(1 + i * uw, 1, s.z0 + 1, 1 + i * uw, s.top, s.z1 - 1, lk.infill)
    for i in range(n):
        ux0 = 1 + i * uw
        dx = ux0 + 1 if i % 2 == 0 else ux0 + 3
        wx = ux0 + 3 if i % 2 == 0 else ux0 + 1
        b.set(dx, 0, s.z0, lk.floor)
        b.door(dx, 1, s.z0, "south", lk.wood, hinge="left" if i % 2 == 0 else "right")
        b.set(wx, 2, s.z0, lk.pane if lk.pane != "air" else "glass_pane")
        b.path(dx, 0, dx, s.z0 - 1)
        room = Room(b, ux0 + 1, s.z0 + 1, ux0 + 3, s.z1 - 1, 1, {(dx, s.z0 + 1), (ux0 + 2, s.z1 - 1)})
        furnish(room, rng, st, lk, ["bed", "chest", "furnace", "plant"])
        b.sign(wx, 2, s.z0 - 1, "north", ["2[surnames]" if rng.random() < 0.5 else "Vacant", "", "", ""], lk.wood)
    # the veranda: posts, a lean-to roof, benches between the doors
    vz = s.z0 - 2
    b.fill(s.x0, 0, vz, s.x1, 0, s.z0 - 1, PATH)
    for x in range(s.x0, s.x1 + 1, uw):
        b.fill(x, 1, vz, x, 3, vz, f"{lk.wood}_fence", **WL)
        b.set(x, 1, vz, lk.log, axis="y")
    for x in range(s.x0, s.x1 + 1):
        b.set(x, 4, vz, lk.log, axis="x")
    m = pick(rng, st.roofs)
    for x in range(s.x0 - 1, s.x1 + 2):
        stair(b, x, 4, vz - 1, m.stairs, "south")
        stair(b, x, 5, vz, m.stairs, "south")
        slab(b, x, 5, vz + 1, m.slab, "bottom")
    for x in range(s.x0, s.x1 + 1):
        b.set(x, 4, vz, lk.log, axis="x")
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along="x", fill=lk.infill, pitch=1)
    for x in range(s.x0 + 2, s.x1, uw):
        if b.empty(x, 1, vz + 1):
            hanging(b, x, 3, vz + 1, st)
    # the warden's gable over the middle, with the trust's name
    mx = (s.x0 + s.x1) // 2
    b.fill(mx - 2, s.top + 1, s.z0, mx + 2, s.top + 2, s.z0, lk.infill)
    b.set(mx, s.top + 3, s.z0, lk.infill)
    stair(b, mx - 3, s.top + 1, s.z0, m.stairs, "east")
    stair(b, mx + 3, s.top + 1, s.z0, m.stairs, "west")
    b.sign(mx, s.top + 1, s.z0 - 1, "north", ["2Almshouse~of the~[surnames]~Trust", "", "", ""], lk.wood)
    for i in range(1, n):
        if i % 2 == 0:
            chimney(b, 1 + i * uw, s.z1 - 1, s.top, s.top + 4, pick(rng, ("bricks", lk.found)))
    flowers(b, rng, st, [(x, z) for x in range(b.w) for z in range(0, vz - 1)], 0.35)
    # an allotment behind each dwelling, a path down the middle, a shared water butt
    az0, az1 = s.z1 + 2, b.l - 2
    crop = pick(rng, st.crops)
    for i in range(n):
        ux0 = 1 + i * uw
        for x in range(ux0 + 1, ux0 + uw):
            for z in range(az0 + 1, az1):
                if (z - az0) % 3 == 0:
                    b.set(x, 0, z, "water", level="0")
                elif rng.random() < 0.8:
                    farmland(b, x, z, crop if rng.random() < 0.7 else pick(rng, st.crops), rng)
        for z in range(az0, az1 + 1):
            b.set(ux0, 0, z, "coarse_dirt")
    for x in range(s.x0, s.x1 + 1):
        b.set(x, 1, az1, f"{lk.wood}_fence", **WL)
    b.path(s.x0, az0 - 1, s.x1, az0 - 1)
    b.set(s.x1, 1, az0, "water_cauldron", level="3")
    b.set(s.x0, 1, az0, "composter", level="4")
    for i in range(n):   # back doors
        bx = 1 + i * uw + 2
        b.door(bx, 1, s.z1, "north", lk.wood)
        b.set(bx, 0, s.z1, lk.floor)
    return b


@gen("pottery", freq="average", n=3)
def pottery(rng, st):
    """A potter's workshop and the bottle kiln beside it."""
    W, D = rng.choice((7, 8)), rng.choice((7, 8))
    yard = 2
    kr = 3
    b = new("Pottery", "city", W + 2 * kr + 4, yard + D + 1)
    lk = look(rng, st)
    s = Shell(1, yard, W, yard + D - 1, 1)
    shell(b, s, lk)
    dx = front_door(b, s, lk, s.x0 + 2)
    windows(b, s, lk, sides=("north", "west", "south"), skip={(dx, s.z0)}, every=2)
    # the kiln: a fat bottle of brick tapering to a chimney
    kx, kz = s.x1 + kr + 1, (s.z0 + s.z1) // 2
    kb = pick(rng, ("bricks", "bricks", "mud_bricks" if st.key == "ochre" else "nether_bricks" if st.key == "ashen" else "bricks"))
    km = mat_of(kb, "brick")
    for dxx, dzz in disc(kr):
        b.set(kx + dxx, 0, kz + dzz, kb)
    for dxx, dzz in circle(kr):
        b.fill(kx + dxx, 1, kz + dzz, kx + dxx, 5, kz + dzz, kb)
    for dxx, dzz in circle(kr):
        stair(b, kx + dxx, 6, kz + dzz, km.stairs, toward(dxx, dzz))
    for dxx, dzz in disc(kr - 1):
        b.set(kx + dxx, 6, kz + dzz, kb)
    for dxx, dzz in circle(kr - 2):
        b.fill(kx + dxx, 7, kz + dzz, kx + dxx, 10, kz + dzz, kb)
    b.clear(kx, 6, kz, kx, 10, kz)
    for dxx, dzz in circle(kr - 2):
        stair(b, kx + dxx, 11, kz + dzz, km.stairs, OPPOSITE[toward(dxx, dzz)], "top")
    b.set(kx, 1, kz, "campfire", facing="north", lit="true", signal_fire="false", **WL)
    for dxx, dzz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        b.set(kx + dxx, 1, kz + dzz, "decorated_pot", facing="north", cracked="false", **WL)
        if rng.random() < 0.5:
            b.set(kx + dxx, 2, kz + dzz, "decorated_pot", facing="north", cracked="false", **WL)
    b.set(kx - kr, 1, kz, "air")   # the kiln mouth, opening onto the workshop's yard side
    b.set(kx - kr, 2, kz, "air")
    b.set(s.x1, 1, kz, "air")
    b.set(s.x1, 2, kz, "air")
    for x in range(s.x1 + 1, kx - kr):
        b.set(x, 0, kz, PATH)
        b.fill(x, 1, kz - 1, x, 2, kz - 1, kb)
        b.fill(x, 1, kz + 1, x, 2, kz + 1, kb)
        b.set(x, 3, kz, kb)
    b.path(kx, 0, kx, kz - kr - 1)
    for i in range(3):
        b.set(kx - 1 + i, 1, kz - kr - 1, pick(rng, ("terracotta", "orange_terracotta", "white_terracotta", "clay")))
    # the workshop: wheel, clay, shelves of pots
    lx = s.x0 + 1
    b.set(s.x0 + 2, 1, s.z1 - 2, "stonecutter", facing="north")
    stair(b, s.x0 + 2, 1, s.z1 - 3, f"{lk.wood}_stairs", "north")
    room = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, 1, {(dx, s.z0 + 1), (s.x1 - 1, kz), (s.x0 + 2, s.z1 - 3)})
    furnish(room, rng, st, lk, ["clay", "barrel", "chest", "crafting_table", "water_cauldron",
                                lambda b, x, y, z, f: (b.set(x, y, z, f"{lk.wood}_slab", type="top", **WL),
                                                       b.set(x, y + 1, z, "decorated_pot", facing=f, cracked="false", **WL)),
                                lambda b, x, y, z, f: b.set(x, y, z, "flower_pot")])
    for (x, y, z), (n, p) in list(b.blocks.items()):
        if n == "water_cauldron" and "level" not in p:
            b.set(x, y, z, "water_cauldron", level="2")
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), along="z", fill=lk.infill, pitch=1)
    b.sign(dx + 1, 2, s.z0 - 1, "north", ["2[surnames]~Pottery", "", "", ""], lk.wood)
    return b


# ── Ashen ─────────────────────────────────────────────────────────────────

@gen("mausoleum", freq="rare", n=3, styles=("ashen",))
def mausoleum(rng, st):
    """A family tomb: a pillared porch under a pediment, coffins inside, a crypt below."""
    W, D = rng.choice((7, 9)), rng.choice((8, 10))
    b = new("Mausoleum", "city", W + 4, D + 6)
    wall = pick(rng, ("polished_blackstone_bricks", "deepslate_bricks", "deepslate_tiles"))
    trim = pick(rng, ("polished_blackstone", "polished_deepslate", "chiseled_tuff"))
    tm = mat_of(pick(rng, ("polished_blackstone", "polished_deepslate")))
    x0, x1, z0, z1 = 2, W + 1, 4, D + 3
    mx = (x0 + x1) // 2
    b.fill(x0 - 1, 0, 1, x1 + 1, 0, z1 + 1, trim)
    for x in range(x0 - 1, x1 + 2):
        stair(b, x, 1, 1, tm.stairs, "south")
    b.fill(x0 - 1, 1, 2, x1 + 1, 1, z1 + 1, trim)
    b.ring(x0, z0, x1, z1, 2, 6, wall)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.fill(x, 2, z, x, 6, z, trim)
    for x in (x0, x0 + 2, x1 - 2, x1):   # the porch columns
        b.fill(x, 2, 2, x, 5, 2, "polished_basalt", axis="y")
    b.fill(x0, 6, 2, x1, 6, z0, trim)
    b.fill(x0 + 1, 6, z0 + 1, x1 - 1, 6, z1 - 1, wall)
    gable(b, x0, 2, x1, z1, 6, mat_of(wall), along="z", fill=wall, pitch=1, over=1, end_over=0)
    b.door(mx, 2, z0, "south", material="iron_door")
    b.fill(mx - 1, 2, z0 - 1, mx + 1, 2, z0 - 1, "air")
    for x in (mx - 1, mx + 1):
        b.set(x, 4, z0, "chiseled_polished_blackstone")
    b.path(mx, 0, mx, 0)
    b.sign(mx, 5, z0 - 1, "north", ["2The [surnames]~Family~[epitaph]", "", "", ""], "dark_oak")
    for x in (x0 + 1, x1 - 1):
        b.set(x, 2, 3, "soul_lantern", hanging="false", **WL)
    # inside: coffins along the walls with candles, an altar at the back
    for z in range(z0 + 2, z1 - 1, 2):
        for x in (x0 + 1, x1 - 1):
            b.set(x, 2, z, wall)
            slab(b, x, 3, z, tm.slab)
            candle(b, x, 4, z, rng.randint(1, 3), "black" if rng.random() < 0.5 else None)
    b.set(mx, 2, z1 - 1, "chiseled_polished_blackstone")
    candle(b, mx, 3, z1 - 1, 4, "white")
    b.set(mx, 2, z1 - 2, "soul_lantern", hanging="false", **WL)
    hanging(b, mx, 5, (z0 + z1) // 2, st)
    # the crypt: a ladder down through a hatch
    b.fill(x0, -5, z0, x1, -1, z1, wall)
    b.clear(x0 + 1, -4, z0 + 1, x1 - 1, -1, z1 - 1)
    b.fill(x0 + 1, 0, z0 + 1, x1 - 1, 1, z1 - 1, trim)
    hx, hz = x0 + 1, z0 + 1
    b.set(hx, 1, hz, "air")
    b.set(hx, 2, hz, "dark_oak_trapdoor", facing="south", half="bottom", open="false", powered="false", **WL)
    ladder(b, hx, hz, -4, 1, "south")
    for z in range(z0 + 2, z1, 2):   # niches
        for x in (x0, x1):
            b.set(x, -3, z, "air")
            candle(b, x, -3, z, rng.randint(1, 4))
    b.chest(mx, -4, z1 - 1, "north", TREASURE if rng.random() < 0.5 else 3)
    hanging(b, mx, -1, (z0 + z1) // 2 + 1, st)
    return b


@gen("lamplighter", freq="average", n=3, styles=("ashen",))
def lamplighter(rng, st):
    """The lamplighter's hut: spare lanterns on a rack, ladders on the wall, a tall lamp outside."""
    b = new("Lamplighter's Hut", "city", 10, 10)
    lk = look(rng, st)
    s = Shell(1, 3, 6, 8, 1)
    shell(b, s, lk)
    dx = front_door(b, s, lk, 3)
    windows(b, s, lk, sides=("north", "west"), skip={(dx, s.z0)}, every=2)
    room = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, 1, {(dx, s.z0 + 1)})
    furnish(room, rng, st, lk, ["bed", "chest", "barrel", lambda b, x, y, z, f: (slab(b, x, y, z, f"{lk.wood}_slab", "top"),
                                                                                 light(b, x, y + 1, z, st)), "furnace"])
    shed(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), high="south")
    chimney(b, s.x1 - 1, s.z1, s.top, s.top + 5, lk.found)
    # the rack of spare lanterns on the east side
    rx = s.x1 + 2
    b.fill(rx, 1, s.z0, rx, 3, s.z0, f"{lk.wood}_fence", **WL)
    b.fill(rx, 1, s.z1, rx, 3, s.z1, f"{lk.wood}_fence", **WL)
    for z in range(s.z0, s.z1 + 1):
        b.set(rx, 4, z, lk.log, axis="z")
    for z in range(s.z0 + 1, s.z1):
        if rng.random() < 0.8:
            hanging(b, rx, 3, z, st)
    ladder(b, s.x1 + 1, s.z0 + 1, 1, 3, "east")
    ladder(b, s.x1 + 1, s.z0 + 3, 1, 2, "east")
    lamp_post(b, 8, 1, st, lk.wood, h=5)
    b.barrel(rx - 1, 1, s.z1, [("candle", rng.randint(4, 16))])
    b.sign(dx + 1, 2, s.z0 - 1, "north", ["2[lamplighter]", "", "", ""], lk.wood)
    return b


@gen("soul_shrine", freq="average", n=3, styles=("ashen",))
def soul_shrine(rng, st):
    """A square around a basin of soul fire under a canopy, candles and benches about it."""
    S = rng.choice((9, 11))
    b = new("Soul Shrine", "city", S, S)
    b.fill(0, 0, 0, S - 1, 0, S - 1, PATH)
    c = S // 2
    stone_ = pick(rng, ("polished_blackstone", "polished_deepslate", "polished_blackstone_bricks"))
    b.fill(c - 2, 0, c - 2, c + 2, 0, c + 2, stone_)
    for dx, dz in circle(1) + [(0, 0)]:
        b.set(c + dx, 0, c + dz, "soul_soil")
        b.set(c + dx, 1, c + dz, "soul_fire")
    b.ring(c - 2, c - 2, c + 2, c + 2, 1, 1, wall_of(stone_))
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        b.set(c + dx, 1, c + dz, wall_of(stone_), **WL)
        b.set(c + dx, 0, c + dz, stone_)
    for x, z in ((c - 2, c - 2), (c + 2, c - 2), (c - 2, c + 2), (c + 2, c + 2)):
        b.fill(x, 1, z, x, 5, z, "polished_basalt", axis="y")
    b.ring(c - 2, c - 2, c + 2, c + 2, 6, 6, stone_)
    top = hip(b, c - 2, c - 2, c + 2, c + 2, 7, mat_of(pick(rng, ("deepslate_tiles", "polished_blackstone_bricks"))), over=1)
    b.fill(c - 1, 6, c - 1, c + 1, 6, c + 1, "air")
    y = 4
    while y < top and b.empty(c, y, c):   # the lantern on a chain from the roof's point
        b.set(c, y, c, "iron_chain", axis="y", **WL)
        y += 1
    hanging(b, c, 3, c, st)
    for x, z in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        b.set(x, 1, z, stone_)
        light(b, x, 2, z, st)
    for i in range(3):   # benches facing the fire
        stair(b, c - 1 + i, 1, S - 2, "dark_oak_stairs", "south")
        stair(b, 1, 1, c - 1 + i, "dark_oak_stairs", "west")
        stair(b, S - 2, 1, c - 1 + i, "dark_oak_stairs", "east")
    for x, z in ((c - 3, c - 3), (c + 3, c + 3), (c + 3, c - 3), (c - 3, c + 3)):
        if 0 < x < S - 1 and 0 < z < S - 1:
            candle(b, x, 1, z, rng.randint(2, 4), pick(rng, ("black", "gray", "white", None)))
    b.sign(c, 1, c - 3, "north", ["2[shrine]", "", "", ""], "dark_oak", wall=False)
    return b


# ── Ochre ─────────────────────────────────────────────────────────────────

@gen("domed_house", freq="common", n=4, styles=("ochre",))
def domed_house(rng, st):
    """A white cube of a house with a dome over its main room and a roof terrace beside it."""
    W, D = rng.choice((7, 9)), rng.choice((7, 9))
    yard = rng.randint(1, 3)
    b = new("Domed House", "city", W + 2, yard + D + 1)
    lk = look(rng, st, timbered=False)
    lk.infill = pick(rng, ("white_terracotta", "calcite", "smooth_sandstone", "white_concrete"))
    lk.found = pick(rng, st.found)
    floors = rng.choice((1, 2))
    s = Shell(1, yard, W, yard + D - 1, floors)
    shell(b, s, lk)
    mx = (s.x0 + s.x1) // 2
    dx = front_door(b, s, lk, mx)
    stair(b, dx - 1, 3, s.z0 - 1, mat_of(lk.found, "smooth_sandstone").stairs, "east", "top")
    stair(b, dx + 1, 3, s.z0 - 1, mat_of(lk.found, "smooth_sandstone").stairs, "west", "top")
    windows(b, s, lk, skip={(dx, s.z0)}, every=2)
    lx = s.x0 + 1
    for k in range(floors):
        room = Room(b, s.x0 + 1, s.z0 + 1, s.x1 - 1, s.z1 - 1, k * s.h + 1, {(dx, s.z0 + 1), (lx, s.z1 - 1), (lx, s.z1 - 2)})
        furnish(room, rng, st, lk, pick(rng, HOUSE_ONE) if floors == 1 else (pick(rng, HOUSE_GROUND) if k == 0 else pick(rng, HOUSE_UPPER)),
                carpet=pick(rng, ("orange", "red", "yellow", "cyan")) if rng.random() < 0.5 else None)
    ladder(b, lx, s.z1 - 1, 1, s.top, "north")
    flat(b, s.x0, s.z0, s.x1, s.z1, s.top, lk.infill)
    b.set(lx, s.top, s.z1 - 1, "ladder", facing="north", **WL)
    # the dome over the front half, a pergola and plants on the back half
    r = (W - 1) // 2 - 1
    while r > 2 and s.z0 + 2 * r + 1 >= s.z1 - 1:   # clear of the ladder's hatch at the back
        r -= 1
    dz = s.z0 + r + 1
    dome(b, mx, dz, s.top, r, pick(rng, ("white_terracotta", "light_blue_terracotta", "cyan_terracotta", "calcite")))
    b.set(mx, s.top + r + 1, dz, "lightning_rod", facing="up", powered="false", **WL)
    for x in range(s.x0 + 1, s.x1):
        if rng.random() < 0.3 and b.empty(x, s.top + 1, s.z1 - 1) and x != lx:
            b.set(x, s.top + 1, s.z1 - 1, potted(rng))
    if s.z1 - dz - r > 1:
        light(b, s.x1 - 1, s.top + 1, s.z1 - 1, st)
    hang(b, mx, dz, (floors - 1) * s.h + 1, st)
    flowers(b, rng, st, yard_cells(b, yard), 0.3)
    if rng.random() < 0.5:
        b.sign(dx + 1, 2, s.z0 - 1, "north", "[house]", lk.wood)
    return b


GLAZED = ("cyan_glazed_terracotta", "blue_glazed_terracotta", "light_blue_glazed_terracotta", "orange_glazed_terracotta",
          "white_glazed_terracotta", "yellow_glazed_terracotta")


@gen("riad", freq="average", n=3, styles=("ochre",))
def riad(rng, st):
    """Two storeys of rooms about a tiled court with a fountain, a gallery round the upper floor."""
    S = rng.choice((19, 21))
    b = new("Riad", "city", S, S)
    lk = look(rng, st, timbered=False)
    lk.infill = pick(rng, ("terracotta", "orange_terracotta", "smooth_sandstone", "white_terracotta"))
    s = Shell(1, 1, S - 2, S - 2, 2)
    shell(b, s, lk)
    c = S // 2
    ci = 2   # the court's half width
    gx0, gx1 = c - ci - 1, c + ci + 1   # the gallery ring round it
    b.fill(gx0, 1, gx0, gx1, s.top, gx1, "air")
    b.fill(gx0, s.h, gx0, gx1, s.h, gx1, lk.floor)
    b.fill(c - ci, s.h, c - ci, c + ci, s.h, c + ci, "air")
    b.ring(gx0 - 1, gx0 - 1, gx1 + 1, gx1 + 1, 1, s.top, lk.infill)
    tile = pick(rng, GLAZED)
    for x in range(gx0, gx1 + 1):
        for z in range(gx0, gx1 + 1):
            b.set(x, 0, z, tile, facing=("north", "east", "south", "west")[(x % 2) * 2 + (z % 2)])
    for x, z in ((gx0, gx0), (gx1, gx0), (gx0, gx1), (gx1, gx1)):   # gallery columns
        b.fill(x, 1, z, x, s.h - 1, z, lk.found)
        b.fill(x, s.h + 1, z, x, s.top - 1, z, lk.found)
    for i in range(c - ci, c + ci + 1):   # railing round the gallery
        for x, z in ((i, c - ci), (i, c + ci), (c - ci, i), (c + ci, i)):
            b.set(x, s.h + 1, z, f"{lk.wood}_fence", **WL)
    b.clear(c - ci + 1, s.h + 1, c - ci + 1, c + ci - 1, s.h + 1, c + ci - 1)
    # arches from the rooms into the court
    for i in (c - 1, c, c + 1):
        for (x, z) in ((i, gx0 - 1), (i, gx1 + 1), (gx0 - 1, i), (gx1 + 1, i)):
            if i == c:
                b.clear(x, 1, z, x, 2, z)
                b.clear(x, s.h + 1, z, x, s.h + 2, z)
            else:
                b.set(x, 2, z, lk.pane if lk.pane != "air" else "glass_pane")
    # the fountain
    b.ring(c - 1, c - 1, c + 1, c + 1, 1, 1, pick(rng, ("smooth_sandstone_slab", "cut_sandstone_slab")), type="bottom", **WL)
    b.set(c, 0, c, "water", level="0")
    b.set(c, 1, c, "water", level="0")
    b.ring(c - 1, c - 1, c + 1, c + 1, 0, 0, "smooth_sandstone")
    for x, z in ((gx0 + 1, gx0 + 1), (gx1 - 1, gx1 - 1), (gx1 - 1, gx0 + 1), (gx0 + 1, gx1 - 1)):
        b.set(x, 1, z, "potted_flowering_azalea_bush")
    # the street door, a passage to the court
    dx = front_door(b, s, lk, c)
    b.set(dx, 3, s.z0 - 1, "air")
    windows(b, s, lk, sides=("north",), skip={(dx, s.z0)}, floors=[1], every=2)
    windows(b, s, lk, sides=("east", "west", "south"), floors=[1], every=3)
    b.fill(c - 1, 1, s.z0 + 1, c - 1, 3, gx0 - 2, lk.infill)
    b.fill(c + 1, 1, s.z0 + 1, c + 1, 3, gx0 - 2, lk.infill)
    ladder(b, s.x0 + 1, s.z0 + 1, 1, s.h, "south")
    rooms = [((s.x0 + 1, s.z0 + 1, c - 2, gx0 - 2), ["chest", "barrel", "furnace", "crafting_table", "smoker"]),
             ((c + 2, s.z0 + 1, s.x1 - 1, gx0 - 2), ["barrel", "plant", "chest", "loom"]),
             ((s.x0 + 1, gx1 + 2, s.x1 - 1, s.z1 - 1), ["bed", "plant", "chest", "shelf"]),
             ((s.x0 + 1, gx0, gx0 - 2, gx1), ["bed", "barrel", "plant"]),
             ((gx1 + 2, gx0, s.x1 - 1, gx1), ["bed", "chest", "plant"])]
    for (xa, za, xb, zb), items in rooms:
        for k in range(2):
            furnish(Room(b, xa, za, xb, zb, k * s.h + 1, {(s.x0 + 1, s.z0 + 1), (s.x0 + 1, s.z0 + 2), (dx, s.z0 + 1)}),
                    rng, st, lk, items if k == 0 else pick(rng, HOUSE_UPPER), light=False)
            hang(b, (xa + xb) // 2, (za + zb) // 2, k * s.h + 1, st)
    for x, z in ((gx0, c), (gx1, c), (c, gx0), (c, gx1)):
        hang(b, x, z, 1, st)
    # the roof terrace
    b.fill(s.x0, s.top, s.z0, s.x1, s.top, s.z1, lk.found)
    b.clear(gx0, s.top, gx0, gx1, s.top, gx1)
    b.fill(gx0, s.top, gx0, gx1, s.top, gx1, lk.floor)
    b.clear(c - ci, s.top, c - ci, c + ci, s.top, c + ci)
    b.ring(s.x0, s.z0, s.x1, s.z1, s.top + 1, s.top + 1, lk.infill)
    for x in range(s.x0, s.x1 + 1, 2):
        b.set(x, s.top + 2, s.z0, lk.infill)
        b.set(x, s.top + 2, s.z1, lk.infill)
    for z in range(s.z0, s.z1 + 1, 2):
        b.set(s.x0, s.top + 2, z, lk.infill)
        b.set(s.x1, s.top + 2, z, lk.infill)
    ladder(b, s.x0 + 1, s.z0 + 1, s.h + 1, s.top, "south")
    b.ring(c - ci, c - ci, c + ci, c + ci, s.top + 1, s.top + 1, f"{lk.wood}_fence", **WL)
    b.sign(dx + 1, 2, s.z0 - 1, "north", "[house]", lk.wood)
    return b


@gen("olive_press", freq="average", n=3, styles=("ochre",))
def olive_press(rng, st):
    """A press yard: a stone edge-mill turning in a round basin, a shaded shed of jars, olive trees."""
    W, D = 15, 13
    b = new("Olive Press", "city", W, D)
    wood = pick(rng, st.woods)
    lk = look(rng, st)
    # a low wall round the yard
    lw = wall_of(pick(rng, st.found))
    for x in range(1, W - 1):
        for z in range(1, D - 1):
            if x in (1, W - 2) or z in (1, D - 2):
                b.set(x, 1, z, lw, **WL)
    b.clear(6, 1, 1, 8, 1, 1)
    b.path(6, 0, 8, 3)
    # the basin and the millstone
    cx, cz = 5, 7
    for dx, dz in disc(2):
        b.set(cx + dx, 0, cz + dz, "smooth_stone")
    for dx, dz in circle(2):
        b.set(cx + dx, 1, cz + dz, "smooth_stone_slab", type="bottom", **WL)
    b.fill(cx, 1, cz, cx, 3, cz, f"{wood}_log", axis="y")
    b.fill(cx - 1, 1, cz, cx - 1, 2, cz, "polished_andesite")
    b.fill(cx - 1, 1, cz - 1, cx - 1, 1, cz + 1, "polished_andesite")
    b.fill(cx + 1, 3, cz, cx + 3, 3, cz, f"{wood}_log", axis="x")
    b.set(cx + 3, 2, cz, f"{wood}_fence", **WL)
    # the shed: posts, a lean-to roof, jars and barrels under it
    sx0, sz0, sx1, sz1 = 9, 4, W - 3, D - 3
    for x, z in ((sx0, sz0), (sx1, sz0), (sx0, sz1), (sx1, sz1)):
        b.fill(x, 1, z, x, 3, z, lk.log, axis="y")
    b.fill(sx1, 1, sz0 + 1, sx1, 3, sz1 - 1, lk.infill)
    b.fill(sx0, 0, sz0, sx1, 0, sz1, PATH)
    shed(b, sx0, sz0, sx1, sz1, 4, pick(rng, st.roofs), high="east")
    for z in range(sz0 + 1, sz1):
        b.set(sx1 - 1, 1, z, "decorated_pot", facing="west", cracked="false", **WL)
        if rng.random() < 0.5:
            b.barrel(sx0 + 1, 1, z, [("honey_bottle", rng.randint(1, 4))] if rng.random() < 0.3 else [])
    b.set(sx0 + 1, 1, sz1 - 1, "water_cauldron", level="3")
    wall_torch(b, sx1 - 1, 3, (sz0 + sz1) // 2, st, "west")
    tree(b, rng, 3, 3, "olive")
    tree(b, rng, 2, D - 3, "olive")
    b.sign(9, 2, 1, "north", ["2[surnames]~Oil Press", "", "", ""], wood, wall=False)
    flowers(b, rng, st, [(x, z) for x in range(2, W - 2) for z in range(2, D - 2)], 0.15)
    return b


# ── Timber ────────────────────────────────────────────────────────────────

@gen("guildhall", freq="rare", n=3, styles=("timber",))
def guildhall(rng, st):
    """A guild's hall: a tall stone undercroft, two timber-framed storeys jettied out on all sides
    above it, banners on the front, a row of gables to the street."""
    W, D = rng.choice((13, 15)), rng.choice((9, 11))
    yard = 3
    b = new("Guildhall", "city", W + 4, yard + D + 2)
    lk = look(rng, st, timbered=True)
    base = look(rng, st, timbered=False)
    base.infill = pick(rng, st.stone)
    base.wood, base.floor = lk.wood, lk.floor
    x0, x1, z0, z1 = 2, W + 1, yard, yard + D - 1
    s = Shell(x0, z0, x1, z1, 1, h=5)
    shell(b, s, base)
    mx = (x0 + x1) // 2
    front_door(b, s, base, mx, wide=True)
    b.set(mx, 4, z0, base.infill)
    b.set(mx + 1, 4, z0, base.infill)
    for x in range(x0 + 2, x1 - 1, 2):
        if abs(x - mx - 0.5) > 1.5:
            b.fill(x, 2, z0, x, 3, z0, "glass_pane")
    windows(b, s, base, sides=("east", "west", "south"), every=2)
    # the jettied storeys
    ux0, uz0, ux1, uz1 = x0 - 1, z0 - 1, x1 + 1, z1 + 1
    for y in (5, 9):
        storey(b, ux0, uz0, ux1, uz1, y, y + 4, lk)
    for x in range(ux0, ux1 + 1, 2):
        bracket(b, x, 4, uz0, lk.wood, "south")
        bracket(b, x, 4, uz1, lk.wood, "north")
    for z in range(uz0 + 1, uz1, 2):
        bracket(b, ux0, 4, z, lk.wood, "east")
        bracket(b, ux1, 4, z, lk.wood, "west")
    upper = Shell(ux0, uz0, ux1, uz1, 3, 4)
    for k, y in ((1, 5), (2, 9)):
        for side in ("north", "south", "east", "west"):
            for xx, zz in wall_cells(upper, side)[0]:
                if (xx + zz) % 2 == 0 and b.get(xx, y + 2, zz) == lk.infill:
                    b.set(xx, y + 2, zz, "glass_pane")
                    if y == 5:
                        b.set(xx, y + 3, zz, "glass_pane")
    # banners on the front
    for x in (ux0 + 2, ux1 - 2):
        b.fill(x, 10, uz0 - 1, x, 12, uz0 - 1, WOOL)
    # inside: the hall below, the guild's room and the archive above
    tz = (z0 + z1) // 2
    for x in range(x0 + 3, x1 - 2):
        slab(b, x, 1, tz, f"{lk.wood}_slab", "top")
        stair(b, x, 1, tz - 1, f"{lk.wood}_stairs", "north")
        stair(b, x, 1, tz + 1, f"{lk.wood}_stairs", "south")
    stair(b, x0 + 2, 1, tz, f"{lk.wood}_stairs", "west")
    b.set(x1 - 2, 1, tz, "lectern", facing="west", has_book="false", powered="false")
    hall = Room(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, {(mx, z0 + 1), (mx + 1, z0 + 1), (x0 + 1, z1 - 1), (x0 + 1, z1 - 2)})
    furnish(hall, rng, st, lk, ["chest", "shelf", "shelf", WOOL, WOOL, "plant", "plant"], light=False)
    for x in range(x0 + 2, x1 - 1, 4):
        hang(b, x, tz, 1, st, height=4)
    b.fill(x0 + 1, 6, z1, x0 + 1, 9, z1, lk.infill)   # backing for the ladder above the undercroft
    ladder(b, x0 + 1, z1 - 1, 1, 9, "north")
    up1 = Room(b, ux0 + 1, uz0 + 1, ux1 - 1, uz1 - 1, 6, {(x0 + 1, z1 - 1), (x0 + 1, z1 - 2)})
    furnish(up1, rng, st, lk, ["shelf", "shelf", "lectern", "chest", "cartography_table", "shelf", "chest", "plant"])
    up2 = Room(b, ux0 + 1, uz0 + 1, ux1 - 1, uz1 - 1, 10, {(x0 + 1, z1 - 1), (x0 + 1, z1 - 2)})
    furnish(up2, rng, st, lk, ["bed", "chest", "barrel", "bed", "shelf", "plant"])
    for x in range(ux0 + 3, ux1 - 2, 4):
        hang(b, x, (uz0 + uz1) // 2, 6, st)
    # the roof: a long ridge and gables to the street
    m = pick(rng, st.roofs)
    gable(b, ux0, uz0, ux1, uz1, 13, m, along="x", fill=lk.infill, pitch=2)
    ridge = 13 + 2 * ((uz1 - uz0 + 2) // 2)
    for x in (ux0 + 2, ux1 - 2):   # a pair of finials on the ridge
        b.set(x, ridge + 1, (uz0 + uz1) // 2, f"{lk.wood}_fence", **WL)
        b.set(x, ridge + 2, (uz0 + uz1) // 2, "lightning_rod", facing="up", powered="false", **WL)
    chimney(b, ux1 - 1, uz1 - 1, 13, 20, "bricks")
    b.sign(mx, 6, uz0 - 1, "north", ["2Hall of the~[guild]~Guild", "", "", ""], lk.wood)
    for x in (mx - 1, mx + 2):
        wall_torch(b, x, 3, z0 - 1, st, "north")
    return b


@gen("brewery", freq="average", n=3, styles=("timber",))
def brewery(rng, st):
    """A brewhouse: copper kettles over a fire, a cooling floor, casks stacked to the beams,
    a hop yard of poles beside it."""
    W, D = rng.choice((9, 11)), rng.choice((8, 9))
    yard = 2
    hw = 5
    b = new("Brewery", "city", W + 2 + hw, yard + D + 1)
    lk = look(rng, st)
    s = Shell(1, yard, W, yard + D - 1, 2, h=4)
    shell(b, s, lk)
    b.fill(s.x0 + 1, s.h, s.z0 + 1, s.x0 + 4, s.h, s.z1 - 1, "air")   # the kettles rise through both floors
    dx = front_door(b, s, lk, s.x1 - 2, wide=False)
    windows(b, s, lk, skip={(dx, s.z0)}, every=2)
    # kettles: copper over a fire, a chimney through the roof
    kx, kz = s.x0 + 2, (s.z0 + s.z1) // 2
    for dz in (-1, 1):
        b.set(kx, 1, kz + dz, "blast_furnace", facing="east", lit="true")
        b.fill(kx, 2, kz + dz, kx, 3, kz + dz, pick(rng, ("copper_block", "exposed_copper", "cut_copper")))
        b.set(kx, 4, kz + dz, "water_cauldron", level="3")
    b.set(kx + 1, 1, kz, "water_cauldron", level="3")
    b.set(kx + 1, 2, kz, "iron_chain", axis="y", **WL)
    chimney(b, s.x0 + 1, kz, 1, s.top + 6, "bricks")
    b.set(s.x0 + 1, 1, kz, "air")
    b.set(s.x0, 1, kz, "bricks")
    # casks along the far wall, lying down, three high
    for z in range(s.z0 + 1, s.z1):
        for y in (1, 2, 3):
            if z == s.z0 + 1 or b.get(s.x1 - 1, y, z) != "air":
                continue
            b.barrel(s.x1 - 1, y, z, [("honey_bottle", rng.randint(1, 4))] if rng.random() < 0.2 else [], facing="west")
    b.set(s.x1 - 1, 1, s.z0 + 1, "air")
    lx = s.x1 - 2
    ladder(b, lx, s.z1 - 1, 1, s.h, "north")
    up = Room(b, s.x0 + 5, s.z0 + 1, s.x1 - 1, s.z1 - 1, s.h + 1, {(lx, s.z1 - 1), (lx, s.z1 - 2)})
    for x in range(s.x0 + 5, s.x1):   # the malting floor
        for z in range(s.z0 + 1, s.z1):
            if up.free(x, z) and rng.random() < 0.4:
                if rng.random() < 0.3:
                    b.set(x, s.h + 1, z, "hay_block", axis="y")
                else:
                    b.set(x, s.h + 1, z, "yellow_carpet")
    furnish(up, rng, st, lk, ["bed", "chest", "composter"])
    hang(b, kx + 2, kz, 1, st, height=7)
    hang(b, s.x1 - 3, s.z0 + 2, 1, st)
    m = pick(rng, st.roofs)
    gable(b, s.x0, s.z0, s.x1, s.z1, s.top, m, along="x", fill=lk.infill, pitch=2 if rng.random() < st.pitch2 else 1)
    # the hop yard
    hx0 = s.x1 + 2
    for x in range(hx0, b.w - 1, 2):
        for z in range(yard, s.z1 + 1, 2):
            b.fill(x, 1, z, x, 4, z, f"{lk.wood}_fence", **WL)
            if rng.random() < 0.8:
                b.set(x, 5, z, "azalea_leaves", **LEAF)
            for y in (2, 3, 4):   # hop bines climbing the poles
                if rng.random() < 0.6:
                    b.set(x + 1, y, z, "vine", west="true", north="false", south="false", east="false", up="false", **{})
            b.set(x, 0, z, "coarse_dirt")
    b.sign(dx - 1, 2, s.z0 - 1, "north", ["2[surnames]~Brewery", "", "", ""], lk.wood)
    b.barrel(dx + 1, 1, s.z0 - 1, [], facing="north")
    return b


# ── Farms ─────────────────────────────────────────────────────────────────

@gen("rice_terraces", kind="farm", freq="common", n=3, styles=("ochre",))
def rice_terraces(rng, st):
    """Paddies stepping up the slope behind a mud wall each, water held on every step, a stair
    up one side and a little shrine of a hut at the top."""
    W = rng.randint(11, 15)
    steps = rng.randint(3, 4)
    band = 3
    D = steps * band + 4
    b = new("Terraced Paddies", "farm", W, D)
    wallb = pick(rng, ("mud_bricks", "packed_mud", "mud_bricks"))
    crop = pick(rng, ("wheat", "wheat", "beetroots"))
    for i in range(steps):
        y = i + 1
        za, zb = 1 + i * band, 1 + (i + 1) * band - 1
        if y > 1:
            b.fill(1, 1, za, W - 2, y - 1, D - 2, "dirt")
        for x in range(1, W - 1):
            b.set(x, y, za, wallb)
            for z in range(za + 1, zb + 1):
                if x == 1:
                    b.set(x, y, z, wallb)
                elif (x + z) % 4 == 0 or z == zb:
                    b.set(x, y, z, "water", level="0")
                else:
                    b.set(x, y, z, "farmland", moisture="7")
                    b.set(x, y + 1, z, crop, age=str(rng.randint(3, 7) if crop == "wheat" else rng.randint(1, 3)))
    # the stair up the east side
    for i in range(steps):
        stair(b, W - 2, i + 1, 1 + i * band, "mud_brick_stairs", "south")
        for z in range(1 + i * band + 1, 1 + (i + 1) * band):
            b.set(W - 2, i + 1, z, PATH if i == 0 else "packed_mud")
    b.path(W - 2, 0, W - 2, 0)
    ty = steps
    tz0 = 1 + steps * band
    b.fill(1, 1, tz0, W - 2, ty, D - 2, "dirt")
    b.fill(1, ty, tz0, W - 2, ty, D - 2, "packed_mud")
    # a shade hut at the top
    for x, z in ((2, tz0), (5, tz0), (2, D - 2), (5, D - 2)):
        b.fill(x, ty + 1, z, x, ty + 2, z, "bamboo_fence", **WL)
    b.fill(1, ty + 3, tz0 - 1, 6, ty + 3, D - 1, "bamboo_mosaic_slab", type="bottom", **WL)
    b.barrel(3, ty + 1, D - 2, [("wheat", rng.randint(4, 20))])
    b.set(4, ty + 1, D - 2, "composter", level="5")
    hanging(b, 3, ty + 2, tz0 + 1, st)
    return b


@gen("sheep_fold", kind="farm", freq="common", n=3)
def sheep_fold(rng, st):
    """A round fold of dry stone wall, a thatched shelter against it, a trough and a shearing bench."""
    S = rng.choice((13, 15))
    b = new("Sheep Fold", "farm", S, S)
    c = S // 2
    r = c - 1
    wallb = pick(rng, ("cobblestone_wall", "mossy_cobblestone_wall", "cobbled_deepslate_wall" if st.key == "ashen" else "sandstone_wall" if st.key == "ochre" else "stone_brick_wall"))
    for dx, dz in circle(r):
        b.set(c + dx, 1, c + dz, wallb, **WL)
        if rng.random() < 0.3:
            b.set(c + dx, 2, c + dz, wallb, **WL)
    b.set(c, 1, c - r, "oak_fence_gate" if st.key == "timber" else f"{pick(rng, st.woods)}_fence_gate",
          facing="north", in_wall="false", open="false", powered="false")
    b.set(c, 2, c - r, "air")
    b.path(c, 0, c, c - r)
    for dx, dz in disc(r - 1):
        if rng.random() < 0.25:
            b.set(c + dx, 1, c + dz, "short_grass" if st.key != "ochre" else "short_dry_grass")
        elif rng.random() < 0.1:
            b.set(c + dx, 0, c + dz, "coarse_dirt")
    # the shelter: a lean-to of hay bales roofed with slabs
    wood = pick(rng, st.woods)
    for x in range(c - 2, c + 3):
        b.set(x, 1, c + r - 2, "air")
        for y in (1, 2):
            b.set(x, y, c + r - 1, "hay_block", axis="x")
    for x in (c - 2, c + 2):
        b.fill(x, 1, c + r - 3, x, 2, c + r - 3, f"{wood}_fence", **WL)
    shed(b, c - 2, c + r - 3, c + 2, c + r - 1, 3, mat(wood), high="south", over=0)
    for i in range(3):
        if i == 1:
            b.set(c - r + 2 + i, 1, c, "water_cauldron", level="3")
        else:
            slab(b, c - r + 2 + i, 1, c, f"{wood}_slab")
    b.set(c + r - 2, 1, c - 1, f"{wood}_slab", type="top", **WL)
    b.set(c + r - 2, 1, c, f"{wood}_slab", type="top", **WL)
    b.set(c + r - 2, 2, c, "white_carpet")
    for _ in range(3):
        x, z = c + rng.randint(-2, 2), c + rng.randint(-2, 1)
        if b.empty(x, 1, z):
            b.set(x, 1, z, "hay_block", axis="y")
    for x in (c - 1, c + 1):   # lamps either side of the gate
        if b.get(x, 1, c - r).endswith("wall"):
            b.set(x, 2, c - r, wallb, **WL)
            light(b, x, 3, c - r, st)
    return b


@gen("chicken_coop", kind="farm", freq="common", n=3)
def chicken_coop(rng, st):
    """A henhouse up on legs with a ramp, nest boxes inside, a wired run round it."""
    S, T = rng.choice((11, 12)), rng.choice((10, 11))
    b = new("Chicken Coop", "farm", S, T)
    wood = pick(rng, st.woods)
    for x in range(S):
        for z in range(T):
            if x in (1, S - 2) and 1 <= z <= T - 2 or z in (1, T - 2) and 1 <= x <= S - 2:
                b.set(x, 1, z, f"{wood}_fence", **WL)
            elif 1 < x < S - 2 and 1 < z < T - 2 and rng.random() < 0.35:
                b.set(x, 0, z, "coarse_dirt")
    b.set(S // 2, 1, 1, f"{wood}_fence_gate", facing="north", in_wall="false", open="false", powered="false")
    b.path(S // 2, 0, S // 2, 1)
    # the coop on legs
    x0, z0, x1, z1 = S - 7, T - 7, S - 3, T - 3
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.fill(x, 1, z, x, 1, z, f"{wood}_fence", **WL)
    b.fill(x0, 2, z0, x1, 2, z1, f"{wood}_planks")
    b.ring(x0, z0, x1, z1, 3, 4, f"{wood}_planks")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.fill(x, 2, z, x, 4, z, f"{wood}_log", axis="y")
    b.set(x0 + 2, 4, z0, f"{wood}_trapdoor", facing="north", half="bottom", open="true", powered="false", **WL)
    b.set(x0 + 2, 3, z0, "air")
    for i, (y, z) in enumerate(((2, z0 - 1), (1, z0 - 2))):
        stair(b, x0 + 2, y, z, f"{wood}_stairs", "south")
    for x in range(x0 + 1, x1):   # nest boxes
        b.set(x, 3, z1 - 1, "hay_block", axis="x")
        b.set(x, 3, z1 - 2, "air")
    b.set(x1 - 1, 3, z0 + 1, "composter", level="7")
    gable(b, x0, z0, x1, z1, 5, mat(wood), along="x", fill=f"{wood}_planks", pitch=1, over=1)
    light(b, x0 + 1, 3, z0 + 1, st)
    # feed and water in the run
    b.set(2, 1, 2, "composter", level="4")
    b.set(3, 1, 2, "water_cauldron", level="3")
    for x in range(2, S - 2):
        for z in range(T - 3, T - 2):
            if b.empty(x, 1, z) and b.get(x, 0, z) != "coarse_dirt" and x < x0 - 1:
                farmland(b, x, z, "wheat", rng)
    for x in range(2, x0 - 1):
        b.set(x, 1, T - 2, f"{wood}_fence", **WL)
    return b


@gen("root_cellar", kind="farm", freq="average", n=3)
def root_cellar(rng, st):
    """A cellar dug into a turfed mound: a stone face and door, barrels of roots inside."""
    W, D = rng.choice((9, 11)), rng.choice((9, 10))
    b = new("Root Cellar", "farm", W, D)
    lk = look(rng, st, timbered=False)
    face = pick(rng, st.found)
    cx = W // 2
    # the mound
    for x in range(1, W - 1):
        for z in range(2, D - 1):
            h = 3 - max(0, int(max(abs(x - cx) - (W // 2 - 3), abs(z - (D + 1) // 2) - (D // 2 - 3))))
            if h > 0:
                b.fill(x, 1, z, x, h, z, "dirt")
                b.set(x, h, z, "grass_block" if st.key != "ochre" else "coarse_dirt", **({"snowy": "false"} if st.key != "ochre" else {}))
                f = pick(rng, st.flowers)
                if rng.random() < 0.25 and b.empty(x, h + 1, z) and f != "brown_mushroom":
                    b.set(x, h + 1, z, f)
    # the cellar inside
    b.fill(cx - 2, -3, 3, cx + 2, 0, D - 3, face)
    b.clear(cx - 1, -2, 4, cx + 1, 2, D - 4)
    b.fill(cx - 2, 3, 3, cx + 2, 3, D - 3, "dirt")
    b.fill(cx - 1, -3, 4, cx + 1, -3, D - 4, "packed_mud" if st.key == "ochre" else "coarse_dirt")
    for z in range(4, D - 3):
        for x in (cx - 1, cx + 1):
            for y in (-2, -1):
                if rng.random() < 0.7:
                    b.barrel(x, y, z, [(pick(rng, ("potato", "carrot", "beetroot", "apple")), rng.randint(4, 24))])
    b.chest(cx, -2, D - 4, "north")
    hanging(b, cx, 1, (D) // 2, st)
    b.fill(cx - 2, 1, 2, cx + 2, 3, 2, face)
    b.fill(cx - 2, 4, 2, cx + 2, 4, 2, mat_of(face, "stone_brick").slab, type="bottom", **WL)
    b.set(cx, 0, 2, face)
    b.set(cx, 0, 3, "air")
    b.door(cx, 1, 2, "south", lk.wood)
    stair(b, cx, 0, 4, mat_of(face, "stone_brick").stairs, "north")
    stair(b, cx, -1, 5, mat_of(face, "stone_brick").stairs, "north")
    b.set(cx, 0, 3, lk.floor)
    b.fill(cx, 1, 3, cx, 2, 5, "air")
    b.set(cx, 0, 5, "air")
    for x in (cx - 1, cx + 1):
        wall_torch(b, x, 2, 1, st, "north")
    b.path(cx, 0, cx, 1)
    return b


@gen("flower_nursery", kind="farm", freq="average", n=3)
def flower_nursery(rng, st):
    """A glasshouse of potted flowers on benches, raised beds outside it."""
    W, D = rng.choice((7, 9)), rng.choice((9, 11))
    b = new("Flower Nursery", "farm", W + 6, D + 3)
    wood = pick(rng, st.woods)
    x0, z0, x1, z1 = 1, 2, W, D + 1
    b.fill(x0, 0, z0, x1, 0, z1, "coarse_dirt")
    b.ring(x0, z0, x1, z1, 0, 0, pick(rng, st.found))
    b.ring(x0, z0, x1, z1, 1, 1, pick(rng, st.found))
    b.ring(x0, z0, x1, z1, 2, 3, "glass")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        b.fill(x, 1, z, x, 4, z, f"{wood}_log", axis="y")
    # a glass roof on a timber ridge
    mx = (x0 + x1) // 2
    for i in range(0, (x1 - x0) // 2 + 1):
        for z in range(z0, z1 + 1):
            b.set(x0 + i, 4 + i, z, "glass")
            b.set(x1 - i, 4 + i, z, "glass")
    for z in range(z0, z1 + 1):
        b.set(mx, 4 + (x1 - x0) // 2, z, f"{wood}_log", axis="z")
    for i in range(1, (x1 - x0) // 2):
        for z in (z0, z1):
            if x0 + i + 1 <= x1 - i - 1:
                b.fill(x0 + i + 1, 4, z, x1 - i - 1, 4 + i, z, "glass")
    b.clear(mx, 1, z0, mx, 2, z0)
    b.door(mx, 1, z0, "south", wood)
    b.set(mx, 0, z0, f"{wood}_planks")
    b.path(mx, 0, mx, z0 - 1)
    # benches of pots
    for x in (x0 + 1, x1 - 1):
        for z in range(z0 + 1, z1):
            slab(b, x, 1, z, f"{wood}_slab", "top")
            b.set(x, 2, z, potted(rng) if rng.random() < 0.8 else "flower_pot")
    for z in range(z0 + 2, z1 - 1):
        if W >= 9:
            b.set(mx - 1, 1, z, pick(rng, ("moss_block", "rooted_dirt")))
            b.set(mx - 1, 2, z, pick(rng, ("azalea", "flowering_azalea")))
    b.set(mx, 1, z1 - 1, "water_cauldron", level="3")
    for z in range(z0 + 2, z1 - 1, 3):
        hanging(b, mx, 3 + (x1 - x0) // 2, z, st)
    # raised beds outside
    for bx in (x1 + 2, x1 + 4):
        for z in range(z0, z1 + 1):
            if z in (z0, z1):
                b.set(bx, 1, z, f"{wood}_planks")
            else:
                b.set(bx, 1, z, "grass_block", snowy="false")
                b.set(bx, 2, z, pick(rng, ("poppy", "dandelion", "allium", "cornflower", "oxeye_daisy", "azure_bluet",
                                           "red_tulip", "orange_tulip", "pink_tulip", "lily_of_the_valley")))
    return b


@gen("compost_yard", kind="farm", freq="common", n=2)
def compost_yard(rng, st):
    """Bays of compost behind board walls, heaps of earth, a tool shed."""
    W, D = rng.choice((11, 13)), rng.choice((9, 10))
    b = new("Compost Yard", "farm", W, D)
    wood = pick(rng, st.woods)
    lk = look(rng, st)
    for x in range(1, W - 1):
        for z in range(1, D - 1):
            if rng.random() < 0.5:
                b.set(x, 0, z, pick(rng, ("coarse_dirt", "podzol" if st.key != "ochre" else "coarse_dirt", "rooted_dirt")))
    # bays along the back
    bz = D - 4
    for i, x in enumerate(range(1, W - 4, 3)):
        b.fill(x, 1, bz, x, 2, D - 2, f"{wood}_planks")
        b.fill(x, 1, D - 2, x + 3, 2, D - 2, f"{wood}_planks")
        for xx in (x + 1, x + 2):
            for z in range(bz, D - 2):
                b.set(xx, 1, z, "composter", level=str(rng.randint(2, 8) if rng.random() < 0.5 else 8))
    # heaps
    for _ in range(2):
        hx, hz = rng.randint(2, W - 6), rng.randint(2, bz - 2)
        for dx, dz in disc(1):
            b.set(hx + dx, 1, hz + dz, pick(rng, ("dirt", "coarse_dirt", "rooted_dirt")))
        b.set(hx, 2, hz, "coarse_dirt")
    # the tool shed
    s = Shell(W - 4, 1, W - 2, 4, 1, h=3)
    shell(b, s, lk)
    b.door(s.x0 + 1, 1, s.z0, "south", lk.wood)
    b.path(s.x0 + 1, 0, s.x0 + 1, 0)
    b.barrel(s.x0 + 1, 1, s.z1 - 1, [("bone_meal", rng.randint(4, 16))])
    shed(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), high="south")
    b.set(2, 1, 1, "water_cauldron", level="2")
    lamp_post(b, 1, 1, st, wood, h=2)
    return b


@gen("mushroom_grove", kind="farm", freq="average", n=3, styles=("ashen",))
def mushroom_grove(rng, st):
    """Huge mushrooms grown on dark mycelium beds under a few dead trees, a picker's hut."""
    S = rng.choice((13, 15))
    b = new("Mushroom Grove", "farm", S, S)
    for x in range(1, S - 1):
        for z in range(1, S - 1):
            b.set(x, 0, z, pick(rng, ("mycelium", "podzol", "mycelium")), snowy="false")
            if rng.random() < 0.15:
                b.set(x, 1, z, pick(rng, ("brown_mushroom", "red_mushroom")))
    sides = dict(north="true", south="true", east="true", west="true", up="true", down="false")
    spots = [(3, 4), (S - 4, 4), (S // 2, S - 4)]
    for i, (x, z) in enumerate(spots):
        red = rng.random() < 0.5
        h = rng.randint(4, 6)
        b.fill(x, 1, z, x, h, z, "mushroom_stem", **{**sides, "up": "false"})
        if red:
            for y in range(h - 1, h + 2):
                r = 2 if y < h + 1 else 1
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        if (y < h + 1 and max(abs(dx), abs(dz)) == r and abs(dx) + abs(dz) < 2 * r) or y == h + 1:
                            b.set(x + dx, y, z + dz, "red_mushroom_block", **sides)
        else:
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if abs(dx) + abs(dz) < 4:
                        b.set(x + dx, h + 1, z + dz, "brown_mushroom_block", **sides)
    # the picker's hut
    lk = look(rng, st)
    s = Shell(1, S - 6, 5, S - 2, 1, h=3)
    shell(b, s, lk)
    b.door(3, 1, s.z0, "south", lk.wood)
    b.path(3, 0, 3, s.z0 - 1)
    b.barrel(2, 1, s.z1 - 1, [("brown_mushroom", rng.randint(4, 20))])
    b.barrel(4, 1, s.z1 - 1, [("red_mushroom", rng.randint(4, 20))])
    hanging(b, 3, 2, s.z0 + 2, st)
    shed(b, s.x0, s.z0, s.x1, s.z1, s.top, pick(rng, st.roofs), high="south")
    lamp_post(b, S // 2, 1, st, lk.wood, h=2)
    return b
