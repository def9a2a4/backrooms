"""More of Level 143's sky features: balloons, blimps and airships over the city, and the docking
towers they tie up to.

Every sky feature lies between y = SKY (world y100) and SKY + 26, its lowest block at SKY (the
city moves them up and down and turns them). The envelopes are WOOL (one colour per building)
with accents; each style has its own kit (see kit()):
  timber  wood and canvas: planked hulls with stripped-log wales, canvas envelopes, wooden props;
  ochre   bright canvas and terracotta: warm envelopes with terracotta ribs, copper engines;
  ashen   dark iron: deepslate and blackstone hulls, black envelopes, iron bars, soul lanterns.
Noses point north (z = 0).
"""

import math

from buildings import SKY, gen
from parts import hang, pick, tree
from schem import OPPOSITE, PATH, STEP, TREASURE, WOOL, Build

TOP = SKY + 26
NOWATER = dict(waterlogged="false")
ACCENTS = ("white_wool", "light_gray_wool", "black_wool", "white_wool")


def done(b):
    """Checks the feature fits the sky band (between SKY and TOP)."""
    ys = [y for (_, y, _), (n, _) in b.blocks.items() if n != "air"]
    if min(ys) < SKY or max(ys) > TOP:
        raise ValueError(f"{b.name}: y {min(ys)}..{max(ys)} outside {SKY}..{TOP}")
    return b


# ── A style's kit ─────────────────────────────────────────────────────────

class Kit:
    """The materials one airship is built of, from its style (see the module doc)."""

    def log(self, wood=None):
        w = wood or self.wood
        return f"{w}_log"

    def stripped(self, wood=None):
        return f"stripped_{wood or self.wood}_log"


def kit(rng, st, wood=None):
    k = Kit()
    k.key, k.light, k.st = st.key, st.light, st
    if st.key == "ashen":
        k.wood = wood or pick(rng, ("dark_oak", "spruce", "dark_oak"))
        k.deck = pick(rng, ("dark_oak", "spruce"))
        k.plank, k.stairs, k.slab = "deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab"
        k.wale, k.wale_stairs = "polished_blackstone_bricks", "polished_blackstone_brick_stairs"
        k.keel = "polished_blackstone"
        k.rail = "iron_bars"
        k.post = "polished_blackstone_wall"
        k.blade, k.hub, k.engine, k.cowl = "iron_trapdoor", "iron_chain", "blast_furnace", "polished_blackstone_stairs"
        k.canvas = pick(rng, ("black_wool", "gray_wool", "black_wool"))
        k.alt = "gray_wool" if k.canvas == "black_wool" else "black_wool"
        k.rib = pick(rng, ("gray_wool", "light_gray_wool")) if k.canvas == "black_wool" else "black_wool"
        k.cap = "polished_blackstone"
        k.fin = pick(rng, ("black_wool", "gray_wool"))
        k.trim = WOOL
        k.wall = pick(rng, ("polished_blackstone_bricks", "deepslate_bricks", f"{k.wood}_planks"))
        k.corner = "polished_blackstone"
        k.roof = "polished_blackstone_brick_slab"
        k.pane = pick(rng, ("gray_stained_glass_pane", "black_stained_glass_pane", "glass_pane"))
        k.flag = (WOOL, "black_wool")
    elif st.key == "ochre":
        k.wood = wood or pick(rng, ("acacia", "jungle", "acacia"))
        k.deck = pick(rng, ("birch", "acacia", "jungle", "birch"))
        k.plank, k.stairs, k.slab = f"{k.wood}_planks", f"{k.wood}_stairs", f"{k.wood}_slab"
        k.wale = pick(rng, ("orange_terracotta", "white_terracotta", "terracotta", "red_terracotta"))
        k.wale_stairs = k.stairs
        k.keel = f"{k.wood}_log"
        k.rail = f"{k.wood}_fence"
        k.post = f"{k.wood}_fence"
        k.blade, k.hub, k.engine, k.cowl = "waxed_copper_trapdoor", "waxed_lightning_rod", "waxed_cut_copper", \
            "waxed_cut_copper_stairs"
        k.canvas = pick(rng, ("orange_wool", "yellow_wool", WOOL, WOOL, "white_wool"))
        k.alt = pick(rng, [c for c in ("white_wool", "yellow_wool", "orange_wool", WOOL) if c != k.canvas])
        k.rib = pick(rng, ("orange_terracotta", "white_terracotta", "terracotta", "red_terracotta"))
        k.cap = pick(rng, ("terracotta", "red_terracotta", "orange_terracotta"))
        k.fin = pick(rng, ("orange_terracotta", "red_terracotta", "white_terracotta"))
        k.trim = WOOL if WOOL not in (k.canvas, k.alt) else "white_wool"
        k.wall = pick(rng, ("white_terracotta", "smooth_sandstone", f"{k.wood}_planks", "orange_terracotta"))
        k.corner = f"stripped_{k.wood}_log"
        k.roof = f"{k.wood}_slab"
        k.pane = "glass_pane"
        k.flag = (WOOL, "yellow_wool")
    else:
        k.wood = wood or pick(rng, ("spruce", "dark_oak", "oak", "spruce"))
        k.deck = pick(rng, [w for w in ("oak", "spruce", "birch") if w != k.wood])
        k.plank, k.stairs, k.slab = f"{k.wood}_planks", f"{k.wood}_stairs", f"{k.wood}_slab"
        k.wale, k.wale_stairs = f"stripped_{k.wood}_log", k.stairs
        k.keel = "dark_oak_log" if k.wood != "dark_oak" else "spruce_log"
        k.rail = f"{k.wood}_fence"
        k.post = f"{k.wood}_fence"
        k.blade, k.hub, k.engine, k.cowl = f"{k.wood}_trapdoor", f"{k.wood}_fence", "blast_furnace", f"{k.wood}_stairs"
        k.canvas = pick(rng, (WOOL, WOOL, "white_wool"))
        k.alt = "white_wool" if k.canvas == WOOL else WOOL
        k.rib = pick(rng, ("light_gray_wool", "white_wool", "brown_wool"))
        k.cap = pick(rng, (f"{k.wood}_planks", "light_gray_wool", "white_wool"))
        k.fin = pick(rng, ("white_wool", "light_gray_wool", WOOL))
        k.trim = WOOL if k.canvas != WOOL else "white_wool"
        k.wall = pick(rng, (f"{k.wood}_planks", "white_terracotta", f"{k.deck}_planks"))
        k.corner = f"{k.wood}_log"
        k.roof = f"{k.wood}_slab"
        k.pane = "glass_pane"
        k.flag = (WOOL, "white_wool")
    k.door = f"{k.wood}_door"
    k.fence = f"{k.wood}_fence"
    k.deckplank = f"{k.deck}_planks"
    k.trapdoor = f"{k.wood}_trapdoor"
    return k


def prune(b, y0=None):
    """Takes away single blocks touching nothing face to face (scraps left by holes), above y0."""
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if n != "air" and (y0 is None or y >= y0) and all(b.empty(x + dx, y + dy, z + dz) for dx, dy, dz in N6):
            b.set(x, y, z, "air")


def axis(state, a):
    """{axis: a} for a block that has one (logs, basalt, pillars)."""
    return {"axis": a} if state.endswith(("_log", "_basalt", "basalt", "_pillar", "_wood", "hay_block")) else {}


def put(b, x, y, z, state, **props):
    """Sets a block only where it's empty."""
    if b.empty(x, y, z):
        b.set(x, y, z, state, **props)


def railing(b, k, x, y, z):
    if k.rail == "iron_bars":
        b.set(x, y, z, "iron_bars", **NOWATER)
    else:
        b.set(x, y, z, k.rail, **NOWATER)


# ── Shapes ────────────────────────────────────────────────────────────────

N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
N26 = tuple((dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if dx or dy or dz)


def body(b, box, inside, paint, hollow=True, thick=True):
    """Draws the blocks of {@code inside} in box (x0, y0, z0, x1, y1, z1): only its skin if hollow,
    a skin that touches face to face all round when thick (no gaps seen corner-on). paint(x, y, z)
    gives the block, or None for a hole."""
    x0, y0, z0, x1, y1, z1 = box
    near = N26 if thick else N6
    for y in range(y0, y1 + 1):
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                if not inside(x, y, z):
                    continue
                if hollow and all(inside(x + dx, y + dy, z + dz) for dx, dy, dz in near):
                    continue
                s = paint(x, y, z)
                if s:
                    b.set(x, y, z, s)


def envelope(b, cx, cy, z0, z1, rx, ry, paint, nose=0.42, pf=2.4, pb=2.0, sag=None):
    """A gas envelope along z from z0 (nose) to z1 (tail): blunter in front (exponent pf), finer
    behind (pb). sag(t) -> (dy, ry factor) droops it along its length. Returns its inside test."""
    span = z1 - z0 + 1
    rf, rb = span * nose, span * (1 - nose)
    cz = z0 - 0.5 + rf

    def inside(x, y, z):
        if not z0 <= z <= z1:
            return False
        dz = z - cz
        r, p = (rf, pf) if dz < 0 else (rb, pb)
        yc, ryz = cy, ry
        if sag:
            dy, k = sag(min(1.0, max(0.0, (z - z0) / max(1, span - 1))))
            yc, ryz = cy + dy, ry * k
        return ((x - cx) / rx) ** 2 + ((y - yc) / ryz) ** 2 + abs(dz / r) ** p <= 1

    box = (math.floor(cx - rx), math.floor(cy - ry - 8), z0, math.ceil(cx + rx), math.ceil(cy + ry), z1)
    body(b, box, inside, paint)
    return inside


def livery(k, cx, cy, z0, z1, kind, every=4, belt=False):
    """An envelope paint in the kit's colours: "gores" (panels round it), "bands" (rings along it),
    "ribs" (thin rib rings on the canvas), "belly" (pale underneath); caps at both ends."""
    def paint(x, y, z):
        if (z <= z0 + 1 or z >= z1 - 1) and math.hypot(x - cx, y - cy) <= 2.3:
            return k.cap   # the mooring cone and the tail cap
        if belt and y == cy:
            return k.trim
        if kind == "gores":
            a = math.atan2(y - cy, x - cx)
            return k.canvas if int((a + math.pi) / (math.tau / 8) + 0.5) % 2 else k.alt
        if kind == "bands":
            return k.alt if ((z - z0) // every) % 2 else k.canvas
        if kind == "belly":
            return k.alt if y < cy - 1 else (k.rib if (z - z0) % every == 0 else k.canvas)
        return k.rib if (z - z0) % every == 0 else k.canvas
    return paint


def underside(inside, x, y, z):
    """The lowest y at or below y still inside (for hanging things off an envelope)."""
    while inside(x, y - 1, z):
        y -= 1
    return y


def fins(b, inside, cx, cy, ztail, n, reach, state, dirs="udlr", trim=None, jag=False, tail=True):
    """Tail fins: n rows forward from ztail, standing out `reach` past the envelope at the back,
    their leading edges swept. trim edges them; tail adds a row behind the envelope's end."""
    rows = [(ztail - k, k) for k in range(n)]
    if tail and b.inside(cx, ztail + 1):
        rows.append((ztail + 1, -1))
    for z, k in rows:
        h = reach if k <= n * 0.4 else max(1, round(reach * (n - k) / (n * 0.6)))
        if jag and k % 2 and h > 1:
            h -= 1
        for c, sx, sy in (("u", 0, 1), ("d", 0, -1), ("l", -1, 0), ("r", 1, 0)):
            if c not in dirs:
                continue
            d = 0
            while inside(cx + sx * d, cy + sy * d, z) and d < 20:
                d += 1
            d = max(d, 1)
            for e in range(d, d + h):
                x, y = cx + sx * e, cy + sy * e
                if b.empty(x, y, z) and b.inside(x, z) and SKY <= y <= TOP:
                    edge = trim and (e == d + h - 1 or k == -1)
                    b.set(x, y, z, trim if edge else state)


def chain(b, x, y0, y1, z, state="iron_chain"):
    b.fill(x, y0, z, x, y1, z, state, axis="y", **NOWATER)


def rig(b, x, y0, z, upto=TOP, state="iron_chain"):
    """A chain from y0 up to the first block above it, if there's one within reach. Returns its top."""
    y = y0
    while b.empty(x, y, z):
        y += 1
        if y > upto:
            return None
    if y == y0:
        return None
    chain(b, x, y0, y - 1, z, state)
    return y - 1


def rope(b, inside, x, y0, z):
    """A chain from y0 up to the envelope above (the first block over it)."""
    return rig(b, x, y0, z)


def propeller(b, x, y, z, k, facing="south"):
    """A hub with four blades round it, turning about the z axis (or x, facing east/west)."""
    ax = "z" if facing in ("north", "south") else "x"
    if k.hub.endswith("lightning_rod"):
        b.set(x, y, z, k.hub, facing=facing, powered="false", **NOWATER)
    elif k.hub.endswith("chain"):
        b.set(x, y, z, k.hub, axis=ax, **NOWATER)
    else:
        b.set(x, y, z, k.hub, **NOWATER)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if ax == "z":
            b.set(x + dx, y + dy, z, k.blade, facing=facing, half="bottom", open="true", powered="false", **NOWATER)
        else:
            b.set(x, y + dy, z + dx, k.blade, facing=facing, half="bottom", open="true", powered="false", **NOWATER)


def engine(b, x, y, z, k, aft=1):
    """An engine pod: a cowl, the engine and a propeller behind (towards +z, or -z with aft=-1)."""
    back, front = ("south", "north") if aft > 0 else ("north", "south")
    b.set(x, y, z - aft, k.cowl, facing=back, half="bottom", **NOWATER)
    if k.engine == "blast_furnace":
        b.set(x, y, z, "blast_furnace", facing="east" if x < b.w // 2 else "west", lit="true")
    else:
        b.set(x, y, z, k.engine)
    propeller(b, x, y, z + aft, k, back)


def strut(b, k, a0, a1, y, c, along="x"):
    """A horizontal strut of the kit's posts from a0 to a1 (along x at z = c, or along z at x = c),
    where empty."""
    for a in range(min(a0, a1), max(a0, a1) + 1):
        x, z = (a, c) if along == "x" else (c, a)
        put(b, x, y, z, k.post, **NOWATER)


def hanging(b, x, y, z, light):
    b.set(x, y, z, light, hanging="true", **NOWATER)


def standing(b, x, y, z, light):
    b.set(x, y, z, light, hanging="false", **NOWATER)


def stair(b, x, y, z, name, facing, half="bottom"):
    b.set(x, y, z, name, facing=facing, half=half, **NOWATER)


def flag(b, x, y, z, colours, length=3, height=2, along="z", pole=None):
    """A pole up from (x, y, z) to y + height + 1 and a wool flag flying from its top along +z (or
    +x), in two colours striped."""
    top = y + height
    b.fill(x, y, z, x, top + 1, z, pole or "oak_fence", **NOWATER)
    for i in range(1, length + 1):
        for j in range(height):
            if i == length and j == 0 and height > 1:
                continue   # a swallowtail
            c = colours[(j + (i == 1)) % len(colours)]
            if along == "z":
                b.set(x, top + 1 - j, z + i, c)
            else:
                b.set(x + i, top + 1 - j, z, c)


def anchor(b, x, y, z, drop=3, k=None):
    """An anchor hanging on a chain from under (x, y + 1, z): the chain, then the anchor's crown."""
    chain(b, x, y - drop + 1, y, z)
    b.set(x, y - drop, z, "anvil", facing="east")


# ── Hulls ─────────────────────────────────────────────────────────────────

def boat(b, k, cx, z0, z1, deck, half, depth, *, bow=0.38, stern=0.2, transom=0.62, side=None, keep=None,
         rail=True, smooth=True, keel=True, rudder=True, gap=()):
    """A boat's hull, bow at z0, stern (a transom) at z1: the deck at y = deck, the keel `depth`
    under it. The bow curves to a point and its forefoot sweeps up; the sides round under to the
    keel, smoothed with upside-down stairs; a darker wale under the deck rim; a rudder behind.
    side(dy) overrides the hull block for the layer dy under the deck; keep(x, y, z) False knocks a
    hole; gap: (x, z) cells left out of the rail. Returns (widths by z, the hull's cells)."""
    n = max(1, z1 - z0)

    def width(z):
        t = (z - z0) / n
        if t < bow:
            f = 1 - (1 - t / bow) ** 2
        elif t > 1 - stern:
            u = (t - (1 - stern)) / stern
            f = 1 - (1 - transom) * u * u
        else:
            f = 1.0
        return half * f

    def drop(z):
        t = (z - z0) / n
        f = min(1.0, (t / (bow * 0.9)) ** 0.55) if t < bow * 0.9 else 1.0
        if t > 1 - stern:
            f = min(f, 1 - 0.3 * (t - (1 - stern)) / stern)
        return depth * f

    cells = set()
    widths = {}
    for z in range(z0, z1 + 1):
        w, d = width(z), drop(z)
        widths[z] = max(0, round(w - 0.05))
        for dy in range(0, depth + 1):
            if dy > d + 0.35 and dy > 0:
                break
            hh = w if dy == 0 else w * math.sqrt(max(0.0, 1 - (dy / (d + 0.9)) ** 2))
            for x in range(cx - half - 1, cx + half + 2):
                if abs(x - cx) <= hh + 0.45 or x == cx:
                    cells.add((x, deck - dy, z))
    keep = keep or (lambda x, y, z: True)
    plank = k.plank
    for (x, y, z) in cells:
        if y < deck and all((x + dx, y + dy, z + dz) in cells for dx, dy, dz in N6):
            continue   # the hold
        if not keep(x, y, z):
            continue
        dy = deck - y
        rim = any((x + dx, y, z + dz) not in cells for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if dy == 0:
            s = plank if rim else k.deckplank
        else:
            s = side(dy) if side else (k.wale if dy == 1 else plank)
        props = {}
        if s.endswith("_log"):
            props = {"axis": "z"}
        below = (x, y - 1, z) not in cells
        if smooth and dy > 0 and below:
            st_ = k.wale_stairs if s == k.wale else (k.stairs if not s.endswith(("_log", "terracotta")) else k.stairs)
            if s == "deepslate_tiles":
                st_ = "deepslate_tile_stairs"
            if x != cx and (x + (1 if x > cx else -1), y, z) not in cells:
                b.set(x, y, z, st_, facing="east" if x < cx else "west", half="top", **NOWATER)
                continue
            if (x, y, z - 1) not in cells and z < (z0 + z1) / 2:
                b.set(x, y, z, st_, facing="south", half="top", **NOWATER)
                continue
        b.set(x, y, z, s, **props)
    # a lamp in the hold, under the deck
    hold = sorted((x, y, z) for (x, y, z) in cells if y == deck - 1 and x == cx and
                  all((x + dx, y + dy, z + dz) in cells for dx, dy, dz in N6))
    if hold:
        x, y, z = hold[len(hold) // 2]
        if keep(x, y + 1, z):
            hanging(b, x, y, z, k.light)
    # the keel: a spine under the bottom
    if keel:
        for z in range(z0 + 1, z1 + 1):
            ys = [y for (x, y, zz) in cells if x == cx and zz == z]
            if ys and drop(z) >= depth - 0.6 and keep(cx, min(ys) - 1, z):
                b.set(cx, min(ys) - 1, z, k.keel, **axis(k.keel, "z"))
    # the rail along the deck's edge
    if rail:
        for (x, y, z) in cells:
            if y == deck and (x, z) not in gap and any((x + dx, y, z + dz) not in cells for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                if keep(x, deck + 1, z):
                    railing(b, k, x, deck + 1, z)
    # the rudder
    if rudder and b.inside(cx, z1 + 2):
        lo = min(y for (x, y, z) in cells if z == z1 and x == cx)
        for y in range(lo - (1 if keel else 0), deck):
            b.set(cx, y, z1 + 1, k.keel if not k.keel.endswith("_log") else k.keel, **({"axis": "y"} if k.keel.endswith("_log") else {}))
        for y in range(lo - (1 if keel else 0), deck - 1):
            b.set(cx, y, z1 + 2, k.plank if k.plank != "deepslate_tiles" else "polished_blackstone_bricks")
        stair(b, cx, deck - 1, z1 + 2, k.stairs, "north", "top")
    return widths, cells


def bowsprit(b, k, cx, z, y, n=3):
    """A spar out over the bow, rising: from (cx, y, z) forward n blocks."""
    for i in range(min(n, z + 1)):
        b.set(cx, y + i // 2, z - i, k.fence if k.key != "ashen" else "polished_blackstone_wall", **NOWATER)


def cabin(b, x0, z0, x1, z1, y, h, wall, roof, pane, corner=None, windows=True, every=2, floor=None):
    """A box room: floor at y, walls y+1..y+h, a flat roof at y+h+1; panes along the walls."""
    b.fill(x0, y, z0, x1, y, z1, floor or (wall if wall.endswith("_planks") else roof))
    b.ring(x0, z0, x1, z1, y + 1, y + h, wall)
    b.fill(x0, y + h + 1, z0, x1, y + h + 1, z1, roof)
    b.clear(x0 + 1, y + 1, z0 + 1, x1 - 1, y + h, z1 - 1)
    if corner:
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            b.fill(x, y + 1, z, x, y + h, z, corner, **({"axis": "y"} if corner.endswith("_log") else {}))
    if windows:
        wy = range(y + 2, y + h) if h >= 3 else range(y + 1, y + h)
        for yy in wy:
            for x in range(x0 + 1, x1):
                if (x - x0) % every == 1 or every == 1:
                    b.set(x, yy, z0, pane, **NOWATER)
                    b.set(x, yy, z1, pane, **NOWATER)
            for z in range(z0 + 1, z1):
                if (z - z0) % every == 1 or every == 1:
                    b.set(x0, yy, z, pane, **NOWATER)
                    b.set(x1, yy, z, pane, **NOWATER)


def slab_roof(b, x0, z0, x1, z1, y, slab):
    b.fill(x0, y, z0, x1, y, z1, slab, type="bottom", **NOWATER)


def stripes(z0, every, a, c, cap=0, z1=None, cap_state=None):
    """An envelope paint: bands of c every `every` along z, else a; caps at the ends."""
    def paint(x, y, z):
        if cap and (z < z0 + cap or (z1 is not None and z > z1 - cap)):
            return cap_state or c
        return c if (z - z0) % every == 0 else a
    return paint


def disc_cells(r):
    rr = (r + 0.45) ** 2
    return [(dx, dz) for dx in range(-r, r + 1) for dz in range(-r, r + 1) if dx * dx + dz * dz <= rr]


def circle_cells(r):
    inner = set(disc_cells(r - 1))
    return [c for c in disc_cells(r) if c not in inner]


# ── Hot-air balloons ──────────────────────────────────────────────────────

def hot_air(b, k, rng, cx, base, cz, R, colours=None, pattern=None, big=False, burner=True):
    """A hot-air balloon whose basket floor is at y = base round (cx, cz): a round crown narrowing
    to a skirt over the burner, in gores, spirals or bands; lines from the basket's corners up to
    the skirt. big: a 5x5 basket. Returns the envelope's top y."""
    a, c = colours or (k.canvas, k.alt)
    pattern = pattern or pick(rng, ("gores", "gores", "spiral", "bands", "crown"))
    half = 2 if big else 1
    mouth = half * 1.42 - 0.1
    hb = round(R * 1.3)
    ys = base + 3 + half          # the skirt
    cy = ys + hb                  # the equator
    sectors = rng.choice((8, 12)) if R >= 4 else 6

    def radius(y):
        if y >= cy:
            return R * math.sqrt(max(0.0, 1 - ((y - cy) / (R * 1.05 + 0.4)) ** 2))
        t = (cy - y) / hb
        return mouth + (R - mouth) * math.sqrt(max(0.0, 1 - t ** 1.7))

    def inside(x, y, z):
        r = radius(y)
        return ys <= y <= cy + R + 1 and r >= 1.2 and math.hypot(x - cx, z - cz) <= r + 0.35

    def paint(x, y, z):
        d = math.hypot(x - cx, z - cz)
        if y == ys and d < mouth - 0.5:
            return None   # the mouth over the burner
        if y == ys:
            return k.cap if k.key != "timber" else "white_wool"
        ang = math.atan2(z - cz, x - cx)
        s = int((ang + math.pi) / (math.tau / sectors))
        if pattern == "spiral":
            s = int((ang + math.pi) / (math.tau / sectors) + (y - ys) * 0.35)
        if pattern == "bands":
            return a if ((y - ys) // 2) % 2 else c
        if pattern == "crown" and y >= cy + R - 1:
            return c
        if pattern == "crown" and y == cy:
            return k.trim if k.trim not in (a,) else c
        return a if s % 2 else c

    body(b, (cx - R - 1, ys, cz - R - 1, cx + R + 1, cy + R + 2, cz + R + 1), inside, paint)
    # the basket
    b.fill(cx - half, base, cz - half, cx + half, base, cz + half, k.plank if k.key != "ashen" else "dark_oak_planks")
    wall = {"timber": f"stripped_{k.wood}_log", "ochre": "bamboo_block", "ashen": "polished_blackstone_bricks"}[k.key]
    for x in range(cx - half, cx + half + 1):
        for z in range(cz - half, cz + half + 1):
            if max(abs(x - cx), abs(z - cz)) == half:
                if abs(x - cx) == half and abs(z - cz) == half:
                    b.set(x, base + 1, z, k.post if k.key == "ashen" else k.fence, **NOWATER)
                else:
                    b.set(x, base + 1, z, k.trapdoor if k.key != "ashen" else "iron_trapdoor",
                          facing="north" if z == cz - half else "south" if z == cz + half else
                          "west" if x == cx - half else "east", half="bottom", open="true", powered="false", **NOWATER)
    # the lines up from the corners to the skirt
    for x, z in ((cx - half, cz - half), (cx + half, cz - half), (cx - half, cz + half), (cx + half, cz + half)):
        rig(b, x, base + 2, z)
    if burner:
        b.set(cx, base + 1, cz, "soul_campfire" if k.key == "ashen" else "campfire", facing="north", lit="true",
              signal_fire="false", **NOWATER)
        if big:
            for x, z in ((cx - 1, cz), (cx + 1, cz)):
                b.barrel(x, base + 1, z, [("coal", rng.randint(4, 30))] if k.key != "ochre" else [("blaze_powder", 3)])
    # sandbags over the side
    for x, z in rng.sample([(cx - half - 1, cz), (cx + half + 1, cz), (cx, cz - half - 1), (cx, cz + half + 1)], 2):
        b.set(x, base, z, "brown_wool" if k.key != "ashen" else "gray_wool")
    return cy + R


# ── Zeppelin ──────────────────────────────────────────────────────────────

def gondola(b, k, rng, cx, z0, z1, y, h=3, half=2, door="south", nose=True, lectern=True, chest=0.4):
    """A control car: floor at y, walls to y + h, windows all round, the front rounded off with
    stairs, a door at the back onto a railed step. Returns the floor's bounds."""
    x0, x1 = cx - half, cx + half
    cabin(b, x0, z0, x1, z1, y, h, k.wall, k.wall if not k.wall.endswith("_planks") else k.plank, k.pane,
          corner=k.corner, every=1, floor=k.deckplank)
    b.fill(x0 + 1, y + 1, z0, x1 - 1, y + h - 1, z0, k.pane, **NOWATER)   # the wide front windscreen
    if nose:
        for x in (x0, x1):   # the corners cut back
            b.set(x, y, z0, "air")
            b.set(x, y + h + 1, z0, "air")
            for yy in range(y + 1, y + h + 1):
                b.set(x, yy, z0, "air")
            b.set(x, y + h + 1, z0 + 1, k.stairs, facing="east" if x == x1 else "west", half="bottom", **NOWATER)
            b.set(x, y, z0 + 1, k.stairs, facing="east" if x == x1 else "west", half="top", **NOWATER)
        b.fill(x0 + 1, y, z0, x1 - 1, y, z0, k.stairs, facing="south", half="top", **NOWATER)
        b.fill(x0 + 1, y + h + 1, z0, x1 - 1, y + h + 1, z0, k.stairs, facing="south", half="bottom", **NOWATER)
        for x in (x0 + 1, x1 - 1):
            b.set(x, y, z0, k.stairs, facing="south", half="top", **NOWATER)
    # under it, a keel strip
    b.fill(cx - 1, y - 1, z0 + 2, cx + 1, y - 1, z1 - 1, k.slab, type="top", **NOWATER)
    zd = z1 if door == "south" else z0
    b.door(cx, y + 1, zd, door, k.wood)
    b.set(cx, y + h, zd, k.wall if not k.wall.endswith("_log") else k.plank)
    dz = 1 if door == "south" else -1
    b.fill(cx - 1, y, zd + dz, cx + 1, y, zd + dz, k.slab, type="top", **NOWATER)
    for x in (cx - 1, cx + 1):
        railing(b, k, x, y + 1, zd + dz)
    if lectern:
        b.set(cx, y + 1, z0 + 1, "lectern", facing="north", has_book="false", powered="false")
        stair(b, cx - 1, y + 1, z0 + 2, f"{k.wood}_stairs", "south")
    if rng.random() < chest:
        b.chest(x1 - 1, y + 1, z1 - 1, "west", TREASURE)
    else:
        b.set(x1 - 1, y + 1, z1 - 1, "cartography_table")
    for zz in range(z0 + 2, z1, 3):
        hanging(b, cx, y + h, zz, k.light)
    return x0, z0, x1, z1


@gen("zeppelin", kind="sky_feature", freq="very common", n=3)
def zeppelin(rng, st):
    """A rigid zeppelin: a long ribbed hull, cross fins with trimmed edges, the control car built
    up against its belly and two engine cars out on struts."""
    k = kit(rng, st)
    L = rng.randint(21, 25)
    W, cx = 15, 7
    W, cx = 17, 8
    b = Build("Zeppelin", "sky_feature", W, L)
    R = rng.choice((4.3, 4.6))
    gy = SKY + 1
    g0, g1 = 3, 3 + rng.randint(6, 7)
    cy = gy + 4 + round(R) + 1
    paint = livery(k, cx, cy, 0, L - 1, pick(rng, ("ribs", "ribs", "bands", "belly")), every=3, belt=rng.random() < 0.6)
    inside = envelope(b, cx, cy, 0, L - 1, R + 0.4, R, paint, nose=0.36, pf=2.6, pb=1.8)
    fins(b, inside, cx, cy, L - 1, 6, 3, k.fin, trim=k.trim if k.trim != k.fin else k.rib)
    gondola(b, k, rng, cx, g0, g1, gy, 3)
    for x in (cx - 2, cx + 2):
        for z in (g0 + 1, g1):
            rig(b, x, gy + 5, z)
    # a rear engine car slung under the tail, and two on struts either side
    ez = L - 7
    ey = underside(inside, cx, cy, ez) - 2
    engine(b, cx, ey, ez, k)
    b.set(cx, ey + 1, ez, k.post, **NOWATER)
    for s in (-1, 1):
        x = cx + s * (round(R) + 2)
        y = cy - 1
        engine(b, x, y, ez - 3, k)
        strut(b, k, cx + s * 2, x - s, y, ez - 3)
    # mooring lights fore and aft
    hanging(b, cx, underside(inside, cx, cy, 2) - 1, 2, k.light)
    return done(b)


# ── Galleon under a balloon ───────────────────────────────────────────────

def mast(b, k, x, z, y0, y1, sail, width, bulge=True):
    """A mast from y0 to y1 with a square sail before it (towards -z) on yards."""
    b.fill(x, y0, z, x, y1, z, k.log() if k.key != "ashen" else "dark_oak_log", axis="y")
    top, bot = y1 - 1, y0 + 2
    for y in range(bot, top + 1):
        w = width - (1 if y == top else 0)
        for dx in range(-w, w + 1):
            b.set(x + dx, y, z - 1, sail)
            if bulge and abs(dx) < w - 1 and bot < y < top:
                b.set(x + dx, y, z - 2, sail)   # the belly
    for y in (top + 1, bot - 1):
        for dx in range(-width, width + 1):
            b.set(x + dx, y, z - 1, k.fence, **NOWATER)
    b.set(x, top + 1, z - 1, k.fence, **NOWATER)


def crows_nest(b, k, x, y, z):
    """A railed platform round a mast at y."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz:
                b.set(x + dx, y, z + dz, k.trapdoor, facing="north", half="top", open="false", powered="false", **NOWATER)
                put(b, x + dx, y + 1, z + dz, k.fence, **NOWATER)


@gen("galleon", kind="sky_feature", freq="very common", n=3)
def galleon(rng, st):
    """A sailing ship slung under a long balloon: a curved hull with a quarterdeck over the
    captain's cabin, two or three masts of bellied sails with a crow's nest, a bowsprit, an anchor
    catted at the bow and a flag at the stern."""
    k = kit(rng, st)
    L = rng.randint(22, 24)
    W, cx = 13, 6
    b = Build("Sky Galleon", "sky_feature", W, L)
    deck = SKY + 4
    z0, z1 = 2, L - 4
    widths, cells = boat(b, k, cx, z0, z1, deck, 3, 3)
    bowsprit(b, k, cx, z0 - 1, deck + 1, 3)
    # the quarterdeck over the captain's cabin
    c0 = z1 - 5
    for z in range(c0, z1 + 1):
        w = widths[z]
        for x in range(cx - w, cx + w + 1):
            if abs(x - cx) == w or z in (c0, z1):
                b.fill(x, deck + 1, z, x, deck + 2, z, k.plank if k.key != "ashen" else k.wale)
            else:
                b.fill(x, deck + 1, z, x, deck + 2, z, "air")
            b.set(x, deck + 3, z, k.deckplank)
            if abs(x - cx) == w or z == z1:
                railing(b, k, x, deck + 4, z)
    for x in range(cx - widths[c0], cx + widths[c0] + 1):
        if abs(x - cx) > 1:
            railing(b, k, x, deck + 4, c0)
    for z in (c0 + 2, c0 + 3):
        for s in (-1, 1):
            b.set(cx + s * widths[z], deck + 2, z, k.pane, **NOWATER)
    b.set(cx - 1, deck + 2, z1, k.pane, **NOWATER)
    b.set(cx + 1, deck + 2, z1, k.pane, **NOWATER)
    b.door(cx, deck + 1, c0, "north", k.wood)
    for x in (cx - 1, cx + 1):   # ladders up either side of the door
        b.set(x, deck + 1, c0 - 1, "ladder", facing="north", **NOWATER)
        b.set(x, deck + 2, c0 - 1, "ladder", facing="north", **NOWATER)
    for x in (cx - 1, cx + 1):
        b.set(x, deck + 4, c0, "air")
    if rng.random() < 0.5:
        b.chest(cx + 1, deck + 1, z1 - 1, "west", TREASURE)
    else:
        b.bed(cx + 1, deck + 1, z1 - 2, "south", pick(rng, st.beds))
    b.set(cx - 1, deck + 1, z1 - 1, "cartography_table" if k.key != "ashen" else "lectern",
          **({} if k.key != "ashen" else dict(facing="east", has_book="false", powered="false")))
    hanging(b, cx, deck + 2, c0 + 2, k.light)
    for x in (cx - widths[z1], cx + widths[z1]):
        standing(b, x, deck + 5, z1, k.light)
    # masts and sails
    sail = pick(rng, ("white_wool", "white_wool", "light_gray_wool")) if k.key != "ashen" else pick(rng, ("gray_wool", "light_gray_wool"))
    if k.key == "ochre":
        sail = pick(rng, ("white_wool", "yellow_wool"))
    span = c0 - 1 - z0
    masts = [z0 + round(span * f) for f in ((0.45, 0.9) if span < 14 else (0.35, 0.68, 1.0))]
    masts = [min(m, c0 - 1) for m in masts]
    cy = deck + 14
    inside = envelope(b, cx, cy, 1, L - 1, 5.6, 4.2, livery(k, cx, cy, 1, L - 1, pick(rng, ("bands", "ribs", "gores")), every=4),
                      nose=0.5, pf=2.6, pb=2.6)
    for i, mz in enumerate(masts):
        top = underside(inside, cx, cy, mz) - 1
        mast(b, k, cx, mz, deck + 1, top, sail, 3 if len(masts) == 2 or i == 1 else 2)
        if i == (1 if len(masts) == 3 else 0):
            crows_nest(b, k, cx, top - 1, mz)
            b.clear(cx - 3, top - 1, mz - 1, cx + 3, top, mz - 1)
            for dx in (-1, 0, 1):
                b.set(cx + dx, top - 1, mz - 1, k.trapdoor, facing="north", half="top", open="false", powered="false", **NOWATER)
                b.set(cx + dx, top, mz - 1, k.fence, **NOWATER)
    # rigging: from the rails up to the envelope
    for z in (z0 + 3, (z0 + c0) // 2 + 1, c0 - 1):
        for s in (-1, 1):
            x = cx + s * widths[z]
            if b.get(x, deck + 1, z) in (k.rail,):
                rig(b, x, deck + 2, z)
    for z in (c0 + 1, z1 - 1):
        for s in (-1, 1):
            rig(b, cx + s * widths[z], deck + 5, z)
    # the anchor catted at the bow, the flag at the stern, a lamp on deck
    s = rng.choice((-1, 1))
    az = z0 + 3
    ax = cx + s * (widths[az] + 1)
    b.set(ax, deck, az, k.fence, **NOWATER)
    anchor(b, ax, deck - 1, az, 2)
    flag(b, cx, deck + 4, z1 - 1, k.flag, length=3, height=2, pole=k.fence) if b.inside(cx, z1 + 2) else None
    b.set(cx, deck + 1, masts[0] + 2, k.fence, **NOWATER)
    standing(b, cx, deck + 2, masts[0] + 2, k.light)
    return done(b)


# ── Catamaran ─────────────────────────────────────────────────────────────

@gen("catamaran", kind="sky_feature", freq="common", n=3)
def catamaran(rng, st):
    """Two slim hulls each under its own envelope, a bridge deck between them with the cabin, the
    envelopes braced together by beams; an engine at each stern."""
    k = kit(rng, st)
    L = rng.randint(20, 22)
    W = 17
    b = Build("Twin-Hull Airship", "sky_feature", W, L)
    deck = SKY + 3
    xs = (3, 13)
    cy = deck + 11
    insides = []
    for hx in xs:
        boat(b, k, hx, 1, L - 4, deck, 2, 2, bow=0.42, stern=0.15, transom=0.5, rudder=False)
        insides.append(envelope(b, hx, cy, 0, L - 1, 3.2, 3.3, livery(k, hx, cy, 0, L - 1, "bands", every=3),
                                nose=0.42, pf=2.5, pb=2.0))
        fins(b, insides[-1], hx, cy, L - 1, 4, 2, k.fin, dirs="u" + ("l" if hx < 8 else "r"), trim=k.trim)
    # the bridge deck between the hulls, with a cabin
    b0, b1 = round(L * 0.3), round(L * 0.75)
    for z in range(b0, b1 + 1):
        for x in range(4, 13):
            if b.empty(x, deck, z) or b.get(x, deck + 1, z) == k.rail:
                b.set(x, deck, z, k.deckplank)
            if b.get(x, deck + 1, z) in (k.rail,):
                b.set(x, deck + 1, z, "air")
    for x in range(5, 12):
        railing(b, k, x, deck + 1, b1)
        if x not in (7, 8, 9):
            railing(b, k, x, deck + 1, b0)
    cabin(b, 6, b0 + 2, 10, b1 - 1, deck, 3, k.wall, k.plank, k.pane, corner=k.corner)
    b.fill(5, deck + 5, b0 + 1, 11, deck + 5, b1, k.slab, type="bottom", **NOWATER)
    b.door(8, deck + 1, b0 + 2, "north", k.wood)
    hanging(b, 8, deck + 3, (b0 + b1) // 2 + 1, k.light)
    if rng.random() < 0.5:
        b.chest(7, deck + 1, b1 - 2, "east", TREASURE)
    b.set(9, deck + 1, b1 - 2, "crafting_table")
    b.set(7, deck + 1, b0 + 3, "lectern", facing="north", has_book="false", powered="false")
    # beams joining the envelopes, rigging from the rails
    for z in (4, L // 2, L - 5):
        for x in range(4, 13):
            if not any(f(x, cy, z) for f in insides):
                put(b, x, cy, z, k.log(), axis="x")
    for hx in xs:
        for z in (4, L // 2 - 1, L - 6):
            for s in (-1, 1):
                x = hx + s * 2
                if b.get(x, deck + 1, z) == k.rail:
                    rig(b, x, deck + 2, z)
        engine(b, hx, deck, L - 3, k)
        standing(b, hx, deck + 2, 2, k.light) if b.get(hx, deck + 1, 2) == k.rail else None
    for x in (7, 9):
        rig(b, x, deck + 6, (b0 + b1) // 2)
    return done(b)


# ── Scout blimp ───────────────────────────────────────────────────────────

@gen("scout_blimp", kind="sky_feature", freq="average", n=3)
def scout_blimp(rng, st):
    """A small blimp with a glazed two-seat car under it, a pusher propeller behind the car."""
    k = kit(rng, st)
    L = rng.randint(13, 15)
    W, cx = 11, 5
    b = Build("Scout Blimp", "sky_feature", W, L)
    gy = SKY + 1
    R = 3.4
    cy = gy + 3 + 4
    inside = envelope(b, cx, cy, 0, L - 2, R + 0.2, R, livery(k, cx, cy, 0, L - 2, pick(rng, ("bands", "belly", "gores")), every=3),
                      nose=0.42, pf=2.3, pb=1.8)
    fins(b, inside, cx, cy, L - 2, 4, 2, k.fin, trim=k.trim)
    # the car: 3 wide, glazed, a slab roof hung on short struts
    g0, g1 = L // 2 - 4, L // 2
    b.fill(cx - 1, SKY, g0 + 1, cx + 1, SKY, g1, k.slab, type="top", **NOWATER)
    b.fill(cx - 1, gy, g0, cx + 1, gy, g1, k.deckplank)
    b.ring(cx - 1, g0, cx + 1, g1, gy + 1, gy + 2, k.pane, **NOWATER)
    for x, z in ((cx - 1, g0), (cx + 1, g0), (cx - 1, g1), (cx + 1, g1)):
        b.fill(x, gy + 1, z, x, gy + 2, z, k.corner, **({"axis": "y"} if k.corner.endswith("_log") else {}))
    b.fill(cx - 1, gy + 3, g0, cx + 1, gy + 3, g1, k.plank)
    b.set(cx, gy + 1, g0 + 1, k.stairs, facing="south", half="bottom", **NOWATER)
    b.door(cx, gy + 1, g1, "south", k.wood)
    b.set(cx, gy, g1 + 1, k.slab, type="top", **NOWATER)
    hanging(b, cx, gy + 2, g0 + 2, k.light)
    for x in (cx - 1, cx + 1):
        for z in (g0, g1):
            rig(b, x, gy + 4, z)
    # the pusher on the roof's tail, behind the door
    b.set(cx, gy + 3, g1 + 1, k.engine, **({"facing": "south", "lit": "true"} if k.engine == "blast_furnace" else {}))
    propeller(b, cx, gy + 3, g1 + 2, k)
    return done(b)


# ── Cargo airship ─────────────────────────────────────────────────────────

CARGO = (("coal", 32), ("wheat", 24), ("iron_ingot", 6), ("bread", 12), ("string", 16), ("leather", 8),
         ("potato", 20), ("copper_ingot", 12), ("paper", 16), ("white_wool", 10))


@gen("cargo_airship", kind="sky_feature", freq="very common", n=3)
def cargo_airship(rng, st):
    """A freighter: a long envelope over a keel beam, the pilot's cab forward, pallets of barrels
    and bales slung under the beam on chains, engines either side aft."""
    k = kit(rng, st)
    L = rng.randint(21, 24)
    W, cx = 13, 6
    b = Build("Cargo Airship", "sky_feature", W, L)
    cy = SKY + 18
    R = 4.4
    inside = envelope(b, cx, cy, 0, L - 1, R + 0.2, R, livery(k, cx, cy, 0, L - 1, pick(rng, ("ribs", "bands")), every=5),
                      nose=0.4, pf=2.6, pb=2.0)
    fins(b, inside, cx, cy, L - 1, 5, 3, k.fin, trim=k.trim)
    ky = underside(inside, cx, cy, L // 2) - 1
    b.fill(cx, ky, 2, cx, ky, L - 4, k.log() if k.key != "ashen" else "polished_basalt", axis="z")
    for z in range(2, L - 3):
        if not b.empty(cx, ky + 1, z):
            continue
        rig(b, cx, ky + 1, z) if z % 4 == 2 else None
    # the pilot's cab, forward, hung off the beam
    cy0 = ky - 4
    cabin(b, cx - 1, 2, cx + 1, 6, cy0, 2, k.wall, k.plank, k.pane, corner=k.corner, every=1)
    b.fill(cx - 1, cy0 + 1, 2, cx + 1, cy0 + 2, 2, k.pane, **NOWATER)
    b.set(cx, cy0 + 1, 2, k.wall)
    b.fill(cx, ky - 1, 3, cx, ky - 1, 5, k.post, **NOWATER)
    hanging(b, cx, cy0 + 2, 4, k.light)
    b.set(cx, cy0 + 1, 3, "lectern", facing="north", has_book="false", powered="false")
    b.door(cx, cy0 + 1, 6, "south", k.wood)
    b.fill(cx - 1, cy0, 7, cx + 1, cy0, 7, k.slab, type="top", **NOWATER)
    railing(b, k, cx - 1, cy0 + 1, 7)
    railing(b, k, cx + 1, cy0 + 1, 7)
    # pallets slung on chains from cross beams
    z = 9
    while z + 3 <= L - 4:
        py = SKY + rng.randint(0, 3)
        b.fill(cx - 2, ky, z + 1, cx + 2, ky, z + 1, k.log() if k.key != "ashen" else "polished_basalt", axis="x")
        b.fill(cx - 1, py, z, cx + 1, py, z + 2, k.slab, type="bottom", **NOWATER)
        hgt = rng.randint(1, 2)
        for dz in range(3):
            for dx in (-1, 0, 1):
                for dy in range(1, hgt + 1):
                    if dy == 1 or rng.random() < 0.7 and not b.empty(cx + dx, py + dy - 1, z + dz):
                        if rng.random() < 0.25:
                            b.set(cx + dx, py + dy, z + dz, "hay_block", axis="y")
                        else:
                            b.barrel(cx + dx, py + dy, z + dz, [pick(rng, CARGO)] if rng.random() < 0.3 else ())
        for dx in (-2, 2):
            b.set(cx + dx, py, z + 1, k.slab, type="bottom", **NOWATER)
            b.set(cx + dx, py, z + 1, k.slab, type="top", **NOWATER)
            chain(b, cx + dx, py + 1, ky - 1, z + 1)
            b.fill(cx + dx // 2, py, z + 1, cx + dx // 2, py, z + 1, k.slab, type="bottom", **NOWATER)
        z += 4
    for s in (-1, 1):
        engine(b, cx + s * 5, ky, L - 7, k)
        strut(b, k, cx + s, cx + s * 4, ky, L - 7)
    hanging(b, cx, ky - 1, L - 4, k.light)
    return done(b)


# ── Passenger liner ───────────────────────────────────────────────────────

@gen("liner", kind="sky_feature", freq="very common", n=3)
def liner(rng, st):
    """A great liner: a long envelope over a long gondola of berths, a bridge with a wide
    windscreen forward, a lounge with an open promenade aft, four engines on outriggers."""
    k = kit(rng, st)
    L = 25
    W, cx = 17, 8
    b = Build("Passenger Liner", "sky_feature", W, L)
    trim = k.deck
    cy = SKY + 16
    R = 5.4
    inside = envelope(b, cx, cy, 0, L - 1, R + 0.3, R, livery(k, cx, cy, 0, L - 1, pick(rng, ("ribs", "belly")), every=3, belt=True),
                      nose=0.4, pf=2.6, pb=2.0)
    fins(b, inside, cx, cy, L - 1, 6, 3, k.fin, trim=k.trim)
    g0, g1 = 4, L - 7
    gy = SKY + 2
    wall = k.wall
    cabin(b, cx - 3, g0, cx + 3, g1, gy, 3, wall, f"{trim}_planks", k.pane, corner=k.corner, every=2, floor=f"{trim}_planks")
    b.fill(cx - 2, gy - 1, g0, cx + 2, gy - 1, g1, f"{trim}_planks")
    b.fill(cx - 1, gy - 2, g0 + 1, cx + 1, gy - 2, g1 - 1, f"{trim}_slab", type="top", **NOWATER)
    b.fill(cx - 2, gy + 1, g0, cx + 2, gy + 3, g0, k.pane, **NOWATER)
    b.fill(cx - 3, gy + 4, g0, cx + 3, gy + 4, g0, f"{trim}_stairs", facing="south", half="bottom", **NOWATER)
    b.set(cx - 1, gy + 1, g0 + 1, "lectern", facing="south", has_book="false", powered="false")
    stair(b, cx + 1, gy + 1, g0 + 1, f"{trim}_stairs", "south")
    # berths either side of an aisle
    beds = pick(rng, st.beds)
    z = g0 + 3
    while z + 3 <= g1 - 3:
        for s in (-1, 1):
            b.fill(cx + s * 2, gy + 1, z, cx + 2 * s, gy + 3, z, wall)
            b.bed(cx + 2 * s, gy + 1, z + 1, "south", beds)
        z += 3
    for s in (-1, 1):
        b.fill(cx + s * 2, gy + 1, z, cx + 2 * s, gy + 3, z, wall)
    for s in (-1, 1):
        stair(b, cx + 2 * s, gy + 1, g1 - 1, f"{trim}_stairs", "west" if s < 0 else "east")
    b.set(cx, gy + 1, g1 - 2, f"{trim}_fence", **NOWATER)
    b.set(cx, gy + 2, g1 - 2, f"{trim}_pressure_plate", powered="false")
    if rng.random() < 0.5:
        b.chest(cx + 1, gy + 1, g1 - 1, "west", TREASURE)
    for zz in range(g0 + 2, g1, 3):
        hanging(b, cx, gy + 3, zz, k.light)
    # the promenade aft
    b.door(cx, gy + 1, g1, "south", k.wood)
    b.fill(cx - 3, gy, g1 + 1, cx + 3, gy, g1 + 3, f"{trim}_planks")
    b.fill(cx - 2, gy - 1, g1 + 1, cx + 2, gy - 1, g1 + 2, f"{trim}_slab", type="top", **NOWATER)
    for x in range(cx - 3, cx + 4):
        railing(b, k, x, gy + 1, g1 + 3)
    for z in (g1 + 1, g1 + 2):
        railing(b, k, cx - 3, gy + 1, z)
        railing(b, k, cx + 3, gy + 1, z)
    for x in (cx - 3, cx + 3):
        b.set(x, gy + 2, g1 + 3, k.post, **NOWATER) if k.post != "iron_bars" else None
        standing(b, x, gy + 2 + (k.post != "iron_bars"), g1 + 3, k.light)
    # struts up to the envelope
    for z in range(g0 + 1, g1 + 1, 3):
        for x in (cx - 3, cx + 3):
            rig(b, x, gy + 5, z)
    # four engines on outriggers
    for s in (-1, 1):
        for ez in (8, L - 8):
            x = cx + s * 7
            ey = cy - 2
            engine(b, x, ey, ez, k)
            for xx in range(cx + s * 3, x, s):
                if not inside(xx, ey, ez):
                    put(b, xx, ey, ez, k.post, **NOWATER)
    return done(b)


# ── Observation dirigible ─────────────────────────────────────────────────

@gen("observation_dirigible", kind="sky_feature", freq="common", n=3)
def observation_dirigible(rng, st):
    """An open round deck under a dirigible: a bowl hull with a glass floor at its bottom, a rail
    with lamps, benches looking out, a telescope and a chart table."""
    k = kit(rng, st)
    L = rng.randint(19, 22)
    W, cx = 15, 7
    b = Build("Observation Dirigible", "sky_feature", W, L)
    cy = SKY + 15
    inside = envelope(b, cx, cy, 0, L - 1, 4.8, 4.5, livery(k, cx, cy, 0, L - 1, pick(rng, ("belly", "ribs")), every=4, belt=True),
                      nose=0.45, pf=2.4, pb=2.0)
    fins(b, inside, cx, cy, L - 1, 5, 3, k.fin, dirs="ulr", trim=k.trim)
    cz = L // 2 - 1
    dy = SKY + 3
    rx, rz = 5.5, 6.5
    ell = lambda x, z, f=1.0: ((x - cx) / (rx * f)) ** 2 + ((z - cz) / (rz * f)) ** 2 <= 1
    for z in range(L):
        for x in range(W):
            if ell(x, z):
                b.set(x, dy, z, k.deckplank if ell(x, z, 0.82) else k.plank)
            for f, inner, y in ((0.86, 0.55, dy - 1), (0.58, 0.2, dy - 2)):
                if ell(x, z, f) and not ell(x, z, inner):
                    s_ = k.plank if y == dy - 1 or k.wale.endswith("_log") else k.wale
                    b.set(x, y, z, s_, **axis(s_, "z"))
            if ell(x, z, 0.3):
                b.set(x, dy - 3, z, "glass" if ell(x, z, 0.17) else k.plank)
    # stairs round the bowl's edges, smoothing it
    for z in range(L):
        for x in range(W):
            if ell(x, z) and not ell(x, z, 0.86) and b.empty(x, dy - 1, z):
                f = "east" if x < cx else "west"
                if abs(z - cz) * rx > abs(x - cx) * rz:
                    f = "south" if z < cz else "north"
                b.set(x, dy - 1, z, k.stairs, facing=f, half="top", **NOWATER)
    edge = [(x, z) for z in range(L) for x in range(W) if ell(x, z) and not all(
        ell(x + a, z + c) for a, c in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, z in edge:
        railing(b, k, x, dy + 1, z)
    # a hatch down into the bowl, lit
    b.set(cx, dy, cz, k.trapdoor, facing="north", half="top", open="false", powered="false", **NOWATER)
    b.set(cx + 1, dy - 1, cz, k.light, hanging="true", **NOWATER)
    # lamp posts round the rail, struts up to the envelope
    for i, (x, z) in enumerate(e for e in edge if (e[0] + 2 * e[1]) % 5 == 0):
        b.set(x, dy + 2, z, k.post, **NOWATER) if k.post != "iron_bars" else b.set(x, dy + 2, z, "iron_bars", **NOWATER)
        standing(b, x, dy + 3, z, k.light)
    for x, z in ((cx - 4, cz - 3), (cx + 4, cz - 3), (cx - 4, cz + 3), (cx + 4, cz + 3)):
        b.fill(x, dy + 1, z, x, dy + 2, z, k.log() if k.key != "ashen" else "polished_basalt", axis="y")
        rig(b, x, dy + 3, z)
    for z in (cz - 2, cz + 2):
        stair(b, cx - 3, dy + 1, z, f"{k.deck}_stairs", "west")
        stair(b, cx + 3, dy + 1, z, f"{k.deck}_stairs", "east")
    b.set(cx, dy + 1, cz - 4, k.fence, **NOWATER)
    b.set(cx, dy + 2, cz - 4, "lightning_rod", facing="north", powered="false", **NOWATER)
    if k.key == "ashen":
        b.set(cx, dy + 1, cz + 3, "lectern", facing="north", has_book="false", powered="false")
    else:
        b.set(cx, dy + 1, cz + 3, "cartography_table")
    top = underside(inside, cx, cy, cz) - 1
    chain(b, cx, dy + 3, top, cz)
    hanging(b, cx, dy + 2, cz, k.light)
    return done(b)


# ── Derelict ──────────────────────────────────────────────────────────────

@gen("derelict_airship", kind="sky_feature", freq="very rare", n=3)
def derelict(rng, st):
    """An abandoned ship still aloft: holed planking, the rail half gone, one mast snapped and
    lying across the deck, the other in rags, the envelope torn and sagging towards the tail."""
    k = kit(rng, st, wood=pick(rng, ("dark_oak", "spruce")))
    L = rng.randint(19, 22)
    W, cx = 13, 6
    b = Build("Derelict Airship", "sky_feature", W, L)
    deck = SKY + 3
    z0, z1 = 1, L - 4
    holes = {(rng.randrange(cx - 3, cx + 4), rng.randrange(z0 + 2, z1 - 1)) for _ in range(9)}
    keep = lambda x, y, z: not ((x, z) in holes and deck - 2 < y < deck) and not ((x + 1, z) in holes and y == deck)
    worn = lambda dy: pick(rng, (k.plank, k.plank, f"stripped_{k.wood}_log" if k.key != "ashen" else "cracked_deepslate_tiles",
                                 "mossy_cobblestone" if k.key == "timber" else k.plank))
    widths, cells = boat(b, k, cx, z0, z1, deck, 3, 2, side=worn, keep=keep)
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if y == deck + 1 and n == k.rail and rng.random() < 0.35:
            b.set(x, y, z, "air")
    # a mast snapped off, its top lying across the deck
    mz = z0 + (z1 - z0) // 3
    b.fill(cx, deck + 1, mz, cx, deck + 3, mz, k.log(), axis="y")
    for i in range(5):
        b.set(cx - 1 + i // 2, deck + 1, mz + 2 + i, k.log(), axis="z")
    torn = ("white_wool", "light_gray_wool", None, "cobweb")
    # the second mast still standing, its sail in rags hung on its yard
    m2 = z0 + 2 * (z1 - z0) // 3
    cy = deck + 13
    sag = lambda t: (-round(5 * t ** 1.5), 1 - 0.55 * t)

    def paint(x, y, z):
        r = rng.random()
        if r < 0.1:
            return "cobweb" if rng.random() < 0.3 else None
        if r < 0.3:
            return pick(rng, ("gray_wool", "light_gray_wool", "brown_wool"))
        return k.canvas
    inside = envelope(b, cx, cy, 0, L - 1, 5.0, 4.0, paint, nose=0.5, pf=2.4, pb=2.4, sag=sag)
    top = underside(inside, cx, cy, m2) - 1
    b.fill(cx, deck + 1, m2, cx, top, m2, k.log(), axis="y")
    yard = top - 1
    for dx in range(-2, 3):
        b.set(cx + dx, yard, m2 - 1, k.fence, **NOWATER)
    for x in range(cx - 2, cx + 3):
        y = yard - 1
        while y > yard - 5 and rng.random() < 0.8:
            s = pick(rng, torn)
            if not s:
                break
            b.set(x, y, m2 - 1, s)
            y -= 1
    # what still holds it up: two lines, one snapped and dangling
    for z, s in ((z0 + 4, -1), (z1 - 2, 1)):
        rig(b, cx + s * widths[z], deck + 2, z) if b.get(cx + s * widths[z], deck + 1, z) == k.rail else \
            (b.set(cx + s * widths[z], deck + 1, z, k.rail, **NOWATER), rig(b, cx + s * widths[z], deck + 2, z))
    for z, x in ((z0 + 6, cx + 3), (z1 - 5, cx - 3)):
        y = underside(inside, x, cy, z) - 1
        if not b.empty(x, y + 1, z) and y > deck + 3:
            chain(b, x, y - rng.randint(1, 3), y, z)
    for _ in range(14):
        x, z = rng.randrange(W), rng.randrange(z0, z1 + 1)
        for y in (deck + 1, deck + 2):
            if b.empty(x, y, z) and not b.empty(x, y - 1, z) and b.get(x, y - 1, z) not in ("cobweb",):
                b.set(x, y, z, "cobweb")
                break
    hz = (z0 + z1) // 2
    b.chest(cx, deck - 1, hz, "north", TREASURE)
    b.set(cx, deck, hz + 1, "air")
    b.set(cx, deck - 1, hz + 1, "air")
    b.set(cx + 1, deck + 1, z1 - 1, k.fence, **NOWATER)
    b.set(cx + 1, deck + 2, z1 - 1, k.fence, **NOWATER)
    b.set(cx + 1, deck + 3, z1 - 1, "soul_lantern", hanging="false", **NOWATER)
    prune(b, deck + 4)
    return done(b)


# ── A cluster of tethered balloons ────────────────────────────────────────

PARTY = ("white_wool", "yellow_wool", "light_blue_wool", "pink_wool", "lime_wool", "orange_wool", "magenta_wool")


@gen("balloon_cluster", kind="sky_feature", freq="average", n=3)
def balloon_cluster(rng, st):
    """A little raft lifted by a bunch of toy balloons, each on its own string gathered to a ring
    over the raft."""
    S = 23
    c = S // 2
    b = Build("Balloon Cluster", "sky_feature", S, S)
    k = kit(rng, st)
    b.fill(c - 1, SKY, c - 1, c + 1, SKY, c + 1, k.plank if k.key != "ashen" else "dark_oak_planks")
    b.ring(c - 1, c - 1, c + 1, c + 1, SKY + 1, SKY + 1, k.fence, **NOWATER)
    b.set(c, SKY + 1, c - 1, "air")
    b.barrel(c, SKY + 1, c + 1, [("string", rng.randint(8, 30))])
    standing(b, c - 1, SKY + 2, c - 1, k.light)
    knot = SKY + 6
    b.fill(c, SKY + 1, c, c, knot, c, k.fence, **NOWATER)
    n = rng.randint(4, 5)
    a0 = rng.random() * math.tau
    colours = [WOOL] + [pick(rng, PARTY) for _ in range(n)]
    balloons = [(c, c, rng.randint(SKY + 19, SKY + 21), 3)]
    for i in range(n):
        a = a0 + i * math.tau / n + rng.uniform(-0.2, 0.2)
        d = rng.uniform(6.0, 7.2)
        r = rng.choice((2, 3, 3))
        y = SKY + 11 + 4 * (i % 2) + rng.randint(0, 2)
        balloons.append((round(c + d * math.cos(a)), round(c + d * math.sin(a)), y, r))
    for (x, z, y, r), col in zip(balloons, colours):
        ry = r + 0.8
        inside = lambda xx, yy, zz, x=x, y=y, z=z, r=r, ry=ry: \
            ((xx - x) / (r + 0.3)) ** 2 + ((yy - y) / ry) ** 2 + ((zz - z) / (r + 0.3)) ** 2 <= 1
        body(b, (x - r - 1, y - r - 2, z - r - 1, x + r + 1, y + r + 2, z + r + 1), inside, lambda *_, col=col: col)
        bot = underside(inside, x, y, z)
        b.set(x, bot - 1, z, col)   # the knot
        # the string: a spoke across from the knot, then a chain up
        if (x, z) != (c, c):
            sx = 1 if x > c else -1
            for xx in range(c, x + sx, sx):
                b.set(xx, knot, c, k.fence, **NOWATER)
            sz = 1 if z > c else -1
            for zz in range(c, z + sz, sz):
                b.set(x, knot, zz, k.fence, **NOWATER)
        chain(b, x, knot + 1, bot - 2, z)
    return done(b)


# ── Floating island ───────────────────────────────────────────────────────

@gen("sky_island", kind="sky_feature", freq="very rare", n=3)
def sky_island(rng, st):
    S = 21
    c = S // 2
    b = Build("Floating Island", "sky_feature", S, S)
    wood = pick(rng, st.woods)
    top = SKY + 13
    depth = top - SKY
    waves = [(rng.uniform(0.4, 1.1), rng.randint(2, 4), rng.random() * math.tau) for _ in range(3)]
    R = 8.2

    def radius(a):
        return R + sum(amp * math.sin(k * a + ph) for amp, k, ph in waves) - 0.6

    rock = ("stone", "stone", "stone", "andesite", "tuff", "cobblestone", "dirt")
    for d in range(depth + 1):
        y = top - d
        f = 1.0 if d <= 1 else max(0.0, 1 - (d - 1) / depth) ** 0.75
        for z in range(S):
            for x in range(S):
                dx, dz = x - c, z - c
                r = math.hypot(dx, dz)
                if r > radius(math.atan2(dz, dx)) * f + (0.5 if d == depth else 0):
                    continue
                if d == 0:
                    s = "grass_block[snowy=false]"
                elif d <= 2:
                    s = "dirt" if rng.random() < 0.85 else "coarse_dirt"
                else:
                    s = pick(rng, rock)
                    if rng.random() < 0.05:
                        s = rng.choice(("coal_ore", "copper_ore"))
                b.set(x, y, z, s)
    # roots and dripstone under the overhangs
    for (x, y, z), (n, _) in list(b.blocks.items()):
        if y > SKY and b.empty(x, y - 1, z):
            if n in ("dirt", "coarse_dirt") and rng.random() < 0.35:
                b.set(x, y - 1, z, "hanging_roots", **NOWATER)
            elif n in ("stone", "andesite", "tuff") and rng.random() < 0.08 and y - 2 >= SKY:
                b.set(x, y - 1, z, "pointed_dripstone", thickness="base", vertical_direction="down", **NOWATER)
                b.set(x, y - 2, z, "pointed_dripstone", thickness="tip", vertical_direction="down", **NOWATER)
    # the cottage
    hx, hz = c - 4, c - 3
    wall = pick(rng, st.infill)
    b.fill(hx, top, hz, hx + 4, top, hz + 4, f"{wood}_planks")
    b.ring(hx, hz, hx + 4, hz + 4, top + 1, top + 3, wall)
    for x, z in ((hx, hz), (hx + 4, hz), (hx, hz + 4), (hx + 4, hz + 4)):
        b.fill(x, top + 1, z, x, top + 3, z, f"{wood}_log", axis="y")
    pane = pick(rng, st.panes)
    pane = "glass_pane" if pane == "air" else pane
    for x in (hx + 1, hx + 3):
        b.set(x, top + 2, hz, pane, **NOWATER)
        b.set(x, top + 2, hz + 4, pane, **NOWATER)
    b.set(hx, top + 2, hz + 2, pane, **NOWATER)
    b.set(hx + 4, top + 2, hz + 2, pane, **NOWATER)
    b.door(hx + 2, top + 1, hz, "north", wood)
    stairs = f"{wood}_stairs"
    for k in range(3):
        y = top + 4 + k
        for x in range(hx - 1, hx + 6):
            b.set(x, y, hz - 1 + k, stairs, facing="south", half="bottom", shape="straight", **NOWATER)
            b.set(x, y, hz + 5 - k, stairs, facing="north", half="bottom", shape="straight", **NOWATER)
        for x in (hx, hx + 4):
            b.fill(x, y, hz + k, x, y, hz + 4 - k, f"{wood}_planks")
    b.fill(hx - 1, top + 6, hz + 2, hx + 5, top + 6, hz + 2, f"{wood}_slab", type="bottom", **NOWATER)
    b.fill(hx + 1, top + 4, hz + 1, hx + 3, top + 4, hz + 3, "air")
    b.fill(hx + 1, top + 5, hz + 2, hx + 3, top + 5, hz + 2, "air")
    b.bed(hx + 1, top + 1, hz + 2, "south", pick(rng, st.beds))
    b.chest(hx + 3, top + 1, hz + 3, "west", TREASURE)
    b.set(hx + 3, top + 1, hz + 1, "crafting_table")
    hanging(b, hx + 2, top + 3, hz + 2, st.light)
    chain(b, hx + 2, top + 4, top + 5, hz + 2)
    # a path to the edge, a tree, a fence and flowers
    for z in range(0, hz):
        if b.get(hx + 2, top, z).startswith("grass"):
            b.set(hx + 2, top, z, "dirt_path")
    tx, tz = c + 4, c + 3
    tree(b, rng, tx, tz, pick(rng, st.trees), y=top + 1, size=5)
    for z in range(c - 4, c + 5):
        x = c + 6
        if b.get(x, top, z).startswith("grass") and b.empty(x, top + 1, z):
            b.set(x, top + 1, z, f"{wood}_fence", **NOWATER)
    for z in range(S):
        for x in range(S):
            if b.get(x, top, z).startswith("grass") and b.empty(x, top + 1, z) and rng.random() < 0.18:
                f = pick(rng, st.flowers)
                if f != "brown_mushroom":
                    b.set(x, top + 1, z, f)
    standing(b, hx + 2, top + 1, max(0, hz - 3), st.light) if b.get(hx + 2, top, max(0, hz - 3)) != "air" else None
    return done(b)


# ── Glider ────────────────────────────────────────────────────────────────

@gen("glider", kind="sky_feature", freq="average", n=3)
def glider(rng, st):
    """A hang glider: a swept canvas wing with a little dihedral and battens, a keel tube with a
    fin at the back, the pilot's frame and seat slung under it."""
    k = kit(rng, st)
    L = rng.randint(11, 13)
    W = 23
    cx = W // 2
    b = Build("Glider", "sky_feature", W, L + 2)
    acc = k.trim if k.trim != k.canvas else k.alt
    wy = SKY + 5
    span = (W // 2) / (L - 1)
    for z in range(L):
        h = round(z * span)
        for dx in range(-h, h + 1):
            if z == L - 1 and 0 < abs(dx) < 3:
                continue   # the trailing edge notched in the middle
            y = wy + (1 if abs(dx) > 6 else 0)
            col = acc if abs(dx) == h or (abs(dx) in (4, 8) and z > abs(dx) / span) else k.canvas
            b.set(cx + dx, y, z, col)
            if abs(dx) == 7:   # the dihedral's step, closed underneath
                b.set(cx + dx, wy, z, col)
    keel = f"stripped_{k.wood}_log" if k.key != "ashen" else "polished_basalt"
    b.fill(cx, wy, 0, cx, wy, L - 1, keel, axis="z")
    b.set(cx, wy, L, keel, axis="z")
    b.set(cx, wy + 1, L - 1, acc)
    b.set(cx, wy + 1, L, acc)
    b.set(cx, wy + 2, L, acc)
    # the control frame (a triangle of posts), the seat and its lamp
    mz = L // 2 + 1
    b.fill(cx, SKY + 2, mz, cx, wy - 1, mz, k.fence, **NOWATER)
    b.fill(cx - 2, SKY + 1, mz, cx + 2, SKY + 1, mz, k.fence, **NOWATER)
    for s in (-1, 1):
        b.set(cx + s * 2, SKY + 2, mz, k.fence, **NOWATER)
        b.set(cx + s, SKY + 3, mz, k.fence, **NOWATER)
    b.set(cx, SKY + 1, mz, k.fence, **NOWATER)
    b.set(cx, SKY, mz, k.slab, type="bottom", **NOWATER)
    b.set(cx, SKY, mz + 1, k.stairs, facing="south", half="bottom", **NOWATER)
    b.set(cx, SKY + 1, mz + 1, k.trapdoor, facing="north", half="bottom", open="true", powered="false", **NOWATER)
    hanging(b, cx, wy - 1, 2, k.light)
    # a streamer off the tail
    b.set(cx, wy, L + 1, "iron_chain", axis="z", **NOWATER)
    return done(b)


# ── The ashen black airship ───────────────────────────────────────────────

@gen("black_airship", kind="sky_feature", freq="common", n=3, styles=("ashen",))
def black_airship(rng, st):
    """An iron warship: a hull of deepslate and blackstone with a ram, iron rails, a bridge
    tower aft with soul lanterns, a black ribbed envelope with the city's colour round its waist,
    iron-bladed engines on outriggers and soul lanterns hanging under the envelope."""
    k = kit(rng, st, wood="dark_oak")
    L = rng.randint(22, 24)
    W, cx = 15, 7
    b = Build("Black Airship", "sky_feature", W, L)
    deck = SKY + 3
    z0, z1 = 2, L - 4
    widths, cells = boat(b, k, cx, z0, z1, deck, 3, 2, bow=0.42, transom=0.7)
    # the ram
    b.set(cx, deck - 1, z0 - 1, "polished_blackstone_brick_stairs", facing="south", half="top", **NOWATER)
    b.set(cx, deck - 1, z0 - 2, "polished_blackstone_wall")
    b.set(cx, deck, z0 - 1, "polished_blackstone_wall")
    b.set(cx, deck + 1, z0, "polished_blackstone_wall")
    # the bridge: a tower aft
    c0 = z1 - 6
    cabin(b, cx - 2, c0, cx + 2, z1 - 1, deck, 3, "polished_blackstone_bricks", "polished_blackstone_bricks",
          k.pane, corner="deepslate_tiles", every=2, floor="dark_oak_planks")
    for x in range(cx - 2, cx + 3):
        for z in (c0, z1 - 1):
            b.set(x, deck + 5, z, "polished_blackstone_wall")
    for z in range(c0, z1):
        b.set(cx - 2, deck + 5, z, "polished_blackstone_wall")
        b.set(cx + 2, deck + 5, z, "polished_blackstone_wall")
    b.set(cx, deck + 4, c0 + 2, "iron_trapdoor", facing="north", half="top", open="false", powered="false", **NOWATER)
    b.door(cx, deck + 1, c0, "north", material="dark_oak_door")
    b.chest(cx - 1, deck + 1, z1 - 2, "east", TREASURE)
    hanging(b, cx, deck + 3, z1 - 3, "soul_lantern")
    b.set(cx + 1, deck + 1, z1 - 2, "lectern", facing="west", has_book="false", powered="false")
    b.set(cx + 1, deck + 1, c0 + 1, "ladder", facing="west", **NOWATER)
    for y in range(deck + 1, deck + 4):
        b.set(cx + 1, y, c0 + 2, "ladder", facing="west", **NOWATER)
    b.set(cx + 1, deck + 1, c0 + 1, "air")
    b.set(cx + 2, deck + 1, c0 + 2, "polished_blackstone_bricks")
    b.set(cx + 2, deck + 2, c0 + 2, "polished_blackstone_bricks")
    b.set(cx + 2, deck + 3, c0 + 2, "polished_blackstone_bricks")
    b.set(cx + 1, deck + 4, c0 + 2, "iron_trapdoor", facing="north", half="top", open="false", powered="false", **NOWATER)
    b.set(cx, deck + 4, c0 + 2, "polished_blackstone_bricks")
    for x, z in ((cx - 2, c0), (cx + 2, c0), (cx - 2, z1 - 1), (cx + 2, z1 - 1)):
        standing(b, x, deck + 6, z, "soul_lantern")
    # braziers on deck
    for z in (z0 + 3, c0 - 3):
        b.set(cx, deck + 1, z, "soul_campfire", facing="north", lit="true", signal_fire="false", **NOWATER)
    # the envelope: black, ribbed, the city's colour round its middle
    cy = deck + 14

    def paint(x, y, z):
        if y in (cy, cy + 1):
            return WOOL
        if z <= 1 or z >= L - 2:
            return "polished_blackstone"
        return "gray_wool" if z % 3 == 0 else "black_wool"

    inside = envelope(b, cx, cy, 0, L - 1, 5.3, 4.6, paint, nose=0.35, pf=2.0, pb=1.7)
    fins(b, inside, cx, cy, L - 1, 6, 3, "black_wool", trim="gray_wool", jag=True)
    for z in range(5, L - 5, 4):
        for x in (cx - 4, cx + 4):
            y = underside(inside, x, cy, z) - 1
            if y - 2 > deck + 3:
                chain(b, x, y - 1, y, z)
                hanging(b, x, y - 2, z, "soul_lantern")
    for z in (z0 + 3, (z0 + c0) // 2 + 1, c0 - 1):
        for x in (cx - widths[z], cx + widths[z]):
            rig(b, x, deck + 2, z)
    for z in (c0 + 1, z1 - 2):
        for x in (cx - 2, cx + 2):
            rig(b, x, deck + 6, z)
    for s in (-1, 1):
        x = cx + s * 6
        engine(b, x, cy - 2, L - 8, k)
        strut(b, k, cx + s * 3, x - s, cy - 2, L - 8)
    return done(b)


# ── The ochre canvas barge ────────────────────────────────────────────────

@gen("canvas_barge", kind="sky_feature", freq="common", n=3, styles=("ochre",))
def canvas_barge(rng, st):
    """A broad pleasure barge in terracotta and painted wood: a striped awning over rugs and
    cushions, a helm forward, an envelope in orange and terracotta panels."""
    k = kit(rng, st)
    L = rng.randint(20, 22)
    W, cx = 13, 6
    b = Build("Canvas Barge", "sky_feature", W, L)
    deck = SKY + 3
    z0, z1 = 1, L - 4
    widths, cells = boat(b, k, cx, z0, z1, deck, 4, 2, bow=0.3, stern=0.2, transom=0.75,
                         side=lambda dy: k.wale if dy == 1 else k.plank)
    a0, a1 = z0 + 5, z1 - 3
    stripe = pick(rng, ("orange_wool", "red_wool", "yellow_wool"))
    for z in (a0, a1):
        for x in (cx - 3, cx + 3):
            b.fill(x, deck + 1, z, x, deck + 3, z, k.fence, **NOWATER)
    for z in range(a0, a1 + 1):
        for x in range(cx - 3, cx + 4):
            b.set(x, deck + 4 + (1 if abs(x - cx) <= 1 else 0), z, stripe if (x - cx) % 2 == 0 else "white_wool")
            if abs(x - cx) == 2:
                b.set(x, deck + 5, z, stripe if (x - cx) % 2 == 0 else "white_wool")
    for z in range(a0, a1 + 1):   # the scalloped valance
        if z % 2 == 0:
            for x in (cx - 3, cx + 3):
                put(b, x, deck + 3, z, k.trapdoor, facing="east" if x < cx else "west",
                    half="top", open="true", powered="false", **NOWATER)
    rugs = ("red_carpet", "orange_carpet", "yellow_carpet")
    for z in range(a0 + 1, a1):
        for x in range(cx - 2, cx + 3):
            b.set(x, deck + 1, z, rugs[(abs(x - cx) + z) % 3 if (x - cx) % 2 else 0])
    for z in (a0 + 1, a1 - 1):
        b.barrel(cx - 2, deck + 1, z, [pick(rng, CARGO)] if rng.random() < 0.5 else ())
        b.set(cx + 2, deck + 1, z, "potted_dead_bush")
    if rng.random() < 0.5:
        b.chest(cx + 2, deck + 1, (a0 + a1) // 2, "west", TREASURE)
    hanging(b, cx, deck + 4, (a0 + a1) // 2, k.light)
    # the helm forward, a lamp at the bow
    b.set(cx, deck + 1, z0 + 3, k.fence, **NOWATER)
    standing(b, cx, deck + 2, z0 + 3, k.light)
    stair(b, cx, deck + 1, z1 - 1, f"{k.deck}_stairs", "south")
    b.set(cx - 1, deck + 1, z1 - 1, "decorated_pot", facing="north", cracked="false", **NOWATER)
    cy = deck + 14

    def paint(x, y, z):
        if z <= 1 or z >= L - 2:
            return k.cap
        if y == cy and z % 2:
            return k.trim
        return k.canvas if (z // 3) % 2 else k.rib

    inside = envelope(b, cx, cy, 0, L - 1, 5.3, 4.5, paint, nose=0.5, pf=2.8, pb=2.8)
    for z in (z0 + 3, (a0 + a1) // 2, z1 - 1):
        for s in (-1, 1):
            rig(b, cx + s * widths[z], deck + 2, z)
    for z in (a0, a1):
        for x in (cx - 3, cx + 3):
            rig(b, x, deck + 5, z)
    return done(b)


# ── A whale-shaped blimp ──────────────────────────────────────────────────

@gen("whale_blimp", kind="sky_feature", freq="common", n=3)
def whale_blimp(rng, st):
    """A blimp built as a whale: a blunt head, a pale belly, an eye and a mouth line, a harness
    of the city's colour, flukes for a tail and a spout of glass from its blowhole."""
    k = kit(rng, st)
    L = rng.randint(21, 23)
    W, cx = 15, 7
    b = Build("Whale Blimp", "sky_feature", W, L)
    back = pick(rng, ("gray_wool", "light_gray_wool", "cyan_wool", "blue_wool"))
    cy = SKY + 12
    ze = L - 5
    harness = (ze // 3, 2 * ze // 3)

    def paint(x, y, z):
        if z in harness:
            return WOOL
        if y <= cy - 2:
            return "white_wool"
        return back

    inside = envelope(b, cx, cy, 0, ze, 4.8, 4.3, paint, nose=0.3, pf=3.4, pb=1.5)
    # the tail stock and the flukes, flat either side, two thick
    for z in range(ze - 1, L):
        b.set(cx, cy, z, back)
        b.set(cx, cy - 1, z, back) if z < L - 2 else None
    for k_, (a, c) in enumerate(((1, 2), (1, 4), (2, 6), (4, 6))):
        z = ze + 1 + k_
        if z >= L:
            break
        for d in range(a, c + 1):
            for s in (-1, 1):
                b.set(cx + s * d, cy, z, back if k_ < 3 else "white_wool")
    # an eye each side, the mouth along the head
    for s in (-1, 1):
        x = cx + s * 5
        while not inside(x, cy, 3) and x != cx:
            x -= s
        b.set(x, cy, 3, "black_wool")
        b.set(x, cy + 1, 3, "white_wool")
        for z in range(0, 6):
            xx = cx + s * 5
            while not inside(xx, cy - 2, z) and xx != cx:
                xx -= s
            if inside(xx, cy - 2, z):
                b.set(xx, cy - 2, z, "black_wool")
    # the spout
    top = max(y for y in range(cy, TOP) if inside(cx, y, 4))
    for i, s in enumerate(("light_blue_stained_glass", "light_blue_stained_glass", "white_stained_glass")):
        b.set(cx, top + 1 + i, 4, s)
    for dx in (-1, 1):
        b.set(cx + dx, top + 3, 4, "white_stained_glass")
    # the gondola slung under its middle, a porch at the back
    g0, g1 = 6, 6 + rng.randint(5, 6)
    cabin(b, cx - 2, g0, cx + 2, g1, SKY, 3, k.wall, k.plank, k.pane, corner=k.corner, every=2, floor=k.deckplank)
    b.door(cx, SKY + 1, g1, "south", k.wood)
    b.fill(cx - 1, SKY, g1 + 1, cx + 1, SKY, g1 + 1, k.slab, type="top", **NOWATER)
    for x in (cx - 1, cx + 1):
        railing(b, k, x, SKY + 1, g1 + 1)
    hanging(b, cx, SKY + 3, (g0 + g1) // 2, k.light)
    b.set(cx - 1, SKY + 1, g0 + 1, "lectern", facing="north", has_book="false", powered="false")
    b.chest(cx + 1, SKY + 1, g0 + 1, "west", TREASURE) if rng.random() < 0.5 else b.barrel(cx + 1, SKY + 1, g0 + 1, [pick(rng, CARGO)])
    stair(b, cx + 1, SKY + 1, g1 - 2, f"{k.wood}_stairs", "east")
    for x in (cx - 2, cx + 2):
        for z in (g0, g1):
            rig(b, x, SKY + 5, z)
    for s in (-1, 1):   # pectoral fins: flat flippers
        z = ze // 2 - 1
        x = cx + s * 4
        while inside(x, cy - 2, z):
            x += s
        for i in range(3):
            b.set(x + s * i, cy - 2, z + i // 2, back)
            b.set(x + s * i, cy - 2, z + 1 + i // 2, back)
    return done(b)


# ── A balloon race ────────────────────────────────────────────────────────

@gen("balloon_race", kind="sky_feature", freq="average", n=3)
def balloon_race(rng, st):
    """Three or four little hot-air balloons strung out in a race, each in its own colours."""
    S = 23
    b = Build("Balloon Race", "sky_feature", S, S)
    k = kit(rng, st)
    n = rng.randint(3, 4)
    spots = [(5, 5), (17, 6), (6, 17), (17, 17)]
    rng.shuffle(spots)
    colours = [(WOOL, "white_wool")] + [(pick(rng, PARTY), pick(rng, ("white_wool", "black_wool", "yellow_wool", "red_wool")))
                                        for _ in range(n - 1)]
    for i, ((x, z), col) in enumerate(zip(spots[:n], colours)):
        R = 4 if i == 0 else rng.choice((3, 4))
        height = 4 + round(R * 1.3) + R + 2
        y = SKY if i == 0 else rng.randint(SKY + 1, TOP - height)
        hot_air(b, k, rng, x, y, z, R, colours=col)
    return done(b)


# ── A tethered barrage balloon ────────────────────────────────────────────

@gen("barrage_balloon", kind="sky_feature", freq="average", n=3)
def barrage_balloon(rng, st):
    """A fat silver balloon with three swollen fins, its cable hanging straight down with a lamp
    on the end, guy lines gathering to it from the belly."""
    L = rng.randint(17, 19)
    W, cx = 15, 7
    b = Build("Barrage Balloon", "sky_feature", W, L)
    silver = pick(rng, ("light_gray_wool", "white_wool", "light_gray_wool"))
    if st.key == "ashen":
        silver = pick(rng, ("gray_wool", "black_wool"))
    cy = TOP - 5
    ze = L - 5
    inside = envelope(b, cx, cy, 0, ze, 4.6, 4.4, lambda x, y, z: WOOL if z in (2, 3) else silver,
                      nose=0.42, pf=2.2, pb=1.7)
    # three lobed fins: up and out to each side, swollen, closed at their roots
    for kk in range(5):
        z = ze + kk - 2
        for sx, sy in ((0, 1), (-1, -1), (1, -1)):
            d = 0
            while inside(cx + sx * d, cy + sy * d, z) and d < 9:
                d += 1
            reach = 3 if kk < 4 else 2
            for e in range(max(1, d - 1), d + reach):
                for w in (0, 1):
                    x, y = cx + sx * e, cy + sy * e
                    if sy == -1:
                        y += w
                    elif w:
                        x += 1 if e % 2 else -1
                        x = cx + sx * e
                        y += 0
                    if b.inside(x, z) and SKY <= y <= TOP and not inside(x, y, z):
                        b.set(x, y, z, silver)
    # the cable: straight down from the belly
    cz = ze // 2
    bot = underside(inside, cx, cy, cz)
    chain(b, cx, SKY + 1, bot - 1, cz)
    hanging(b, cx, SKY, cz, st.light)
    gy = bot - 3
    for d in range(-2, 3):   # a yoke on the cable, its arms on lines up to the belly
        b.set(cx + d, gy, cz, "iron_chain", axis="x", **NOWATER)
        b.set(cx, gy, cz + d, "iron_chain", axis="z", **NOWATER)
    for dx, dz in ((-2, 0), (2, 0), (0, -2), (0, 2)):
        rig(b, cx + dx, gy + 1, cz + dz)
    b.set(cx, gy, cz, "iron_block")
    return done(b)


# ── A sky market ──────────────────────────────────────────────────────────

@gen("sky_market", kind="sky_feature", freq="common", n=3)
def sky_market(rng, st):
    """A broad barge under a great envelope, its deck set out with stalls under striped awnings."""
    k = kit(rng, st)
    L = rng.randint(21, 23)
    W, cx = 15, 7
    b = Build("Sky Market", "sky_feature", W, L)
    deck = SKY + 3
    z0, z1 = 1, L - 4
    widths, cells = boat(b, k, cx, z0, z1, deck, 5, 2, bow=0.3, transom=0.75)
    goods = (("apple", 12), ("bread", 8), ("melon_slice", 16), ("cookie", 12), ("emerald", 2), ("string", 9),
             ("paper", 12), ("leather", 6))
    stalls = []
    for z in range(z0 + 5, z1 - 3, 4):
        for s in (-1, 1):
            if widths[z] >= 4 and widths[z + 2] >= 4:
                stalls.append((s, z))
    awning = k.trim if k.trim != "white_wool" else pick(rng, ("red_wool", "blue_wool", "green_wool"))
    for s, z in stalls:
        xo = cx + s * (min(widths[z], widths[z + 2]) - 1)
        xi = xo - s * 2
        for zz in (z, z + 2):
            b.fill(xi, deck + 1, zz, xi, deck + 2, zz, k.fence, **NOWATER)
        for zz in range(z, z + 3):
            for x in range(min(xo, xi), max(xo, xi) + 1):
                b.set(x, deck + 3, zz, awning if zz % 2 else "white_wool")
            if zz != z + 1:
                b.barrel(xo, deck + 1, zz, [pick(rng, goods)])
            else:
                b.set(xo, deck + 1, zz, k.slab, type="top", **NOWATER)
        g = pick(rng, ("cake", "melon", "pumpkin", "hay_block", "decorated_pot"))
        props = {"cake": {"bites": "0"}, "hay_block": {"axis": "y"},
                 "decorated_pot": {"facing": "north", "cracked": "false", "waterlogged": "false"}}.get(g, {})
        b.set(xo, deck + 2, z + 1, g, **props)
        hanging(b, xo - s, deck + 2, z + 1, k.light)
    b.set(cx, deck + 1, z0 + 2, k.fence, **NOWATER)
    standing(b, cx, deck + 2, z0 + 2, k.light)
    if rng.random() < 0.4:
        b.chest(cx, deck + 1, z1 - 2, "north", TREASURE)
    else:
        b.barrel(cx, deck + 1, z1 - 2, [pick(rng, goods)])
    b.set(cx - 1, deck + 1, z1 - 2, k.fence, **NOWATER)
    standing(b, cx - 1, deck + 2, z1 - 2, k.light)
    cy = deck + 15

    def paint(x, y, z):
        if z <= 1 or z >= L - 2:
            return k.cap
        return k.canvas if (x + z) % 4 < 2 else k.alt

    inside = envelope(b, cx, cy, 0, L - 1, 6.0, 4.5, paint, nose=0.45, pf=2.4, pb=2.2)
    for z in (z0 + 3, (z0 + z1) // 2, z1 - 1):
        for x in (cx - widths[z], cx + widths[z]):
            rig(b, x, deck + 2, z)
    for zz in range(z0 + 5, z1 - 2, 4):
        if b.empty(cx, deck + 1, zz):
            b.set(cx, deck + 1, zz, k.fence, **NOWATER)
            rig(b, cx, deck + 2, zz)
    return done(b)


# ── The mail dirigible ────────────────────────────────────────────────────

@gen("mail_dirigible", kind="sky_feature", freq="common", n=3)
def mail_dirigible(rng, st):
    """A slim fast dirigible in the post's livery, its gondola full of mail sacks, a bag hung on a
    hook below for pick-ups on the fly."""
    k = kit(rng, st)
    L = rng.randint(19, 21)
    W, cx = 13, 6
    b = Build("Mail Dirigible", "sky_feature", W, L)
    cy = SKY + 13
    livery_ = pick(rng, ("red_wool", "red_wool", "blue_wool"))

    def paint(x, y, z):
        if z <= 1 or y == cy:
            return livery_
        return WOOL if (z // 4) % 2 else "white_wool"

    inside = envelope(b, cx, cy, 0, L - 1, 3.8, 3.6, paint, nose=0.38, pf=2.6, pb=1.7)
    fins(b, inside, cx, cy, L - 1, 5, 3, livery_, trim="white_wool")
    g0, g1 = 5, 12
    gy = SKY + 3
    cabin(b, cx - 2, g0, cx + 2, g1, gy, 3, k.wall, k.plank, k.pane, corner=k.corner, every=2, floor=k.deckplank)
    b.door(cx, gy + 1, g1, "south", k.wood)
    for z in range(g0 + 1, g1 - 1):
        b.barrel(cx - 1, gy + 1, z, [("paper", rng.randint(4, 32))], facing="east")
        if z % 2:
            b.barrel(cx - 1, gy + 2, z, [("paper", rng.randint(1, 9))], facing="east")
    b.set(cx + 1, gy + 1, g0 + 1, "lectern", facing="west", has_book="false", powered="false")
    b.set(cx + 1, gy + 1, g0 + 2, "cartography_table")
    hanging(b, cx, gy + 3, (g0 + g1) // 2, k.light)
    for x in (cx - 2, cx + 2):
        for z in (g0, g1):
            rig(b, x, gy + 5, z)
    # the hook and its bag
    chain(b, cx, SKY + 1, gy - 1, g0 + 2)
    b.set(cx, SKY, g0 + 2, "brown_wool")
    b.sign(cx + 3, gy + 2, g0 + 3, "east", ["Air Mail~Next stop~[adjectives]~[roadtypes]", "", "", ""], k.wood)
    b.sign(cx - 3, gy + 2, g0 + 3, "west", ["Air Mail", "", "", ""], k.wood)
    b.set(cx + 2, gy + 2, g0 + 3, k.wall if not k.wall.endswith("_log") else k.plank)
    b.set(cx - 2, gy + 2, g0 + 3, k.wall if not k.wall.endswith("_log") else k.plank)
    b.fill(cx - 1, gy, g1 + 1, cx + 1, gy, g1 + 1, k.slab, type="top", **NOWATER)   # the drop porch
    for x in (cx - 1, cx + 1):
        railing(b, k, x, gy + 1, g1 + 1)
    for s in (-1, 1):
        engine(b, cx + s * 5, cy - 1, L - 6, k)
        strut(b, k, cx + s * 3, cx + s * 4, cy - 1, L - 6)
    return done(b)


# ── A wreck tangled in a balloon ──────────────────────────────────────────

@gen("tangled_wreck", kind="sky_feature", freq="very rare", n=3)
def tangled_wreck(rng, st):
    """The bow of a broken ship hanging nose down from a stray hot-air balloon that caught it,
    rigging wound round the lot, the envelope sagging and torn."""
    S = 17
    c = S // 2
    b = Build("Tangled Wreck", "sky_feature", S, S)
    wood = pick(rng, ("dark_oak", "spruce", pick(rng, st.woods)))
    r, ry = 6, 5
    cy = TOP - ry

    def inside(x, y, z):
        lean = (y - cy) * 0.25
        return ((x - c - lean) / (r + 0.4)) ** 2 + ((y - cy) / (ry + 0.4)) ** 2 + ((z - c) / (r - 0.6)) ** 2 <= 1

    def paint(x, y, z):
        q = rng.random()
        if q < 0.1:
            return None
        if q < 0.25:
            return pick(rng, ("gray_wool", "light_gray_wool", "brown_wool"))
        return WOOL if (x + z) % 4 < 2 else "white_wool"

    body(b, (0, cy - ry - 1, 0, S - 1, TOP, S - 1), inside, paint)
    # the bow, hanging nose down: each layer of the hull a step further over
    top = underside(inside, c, cy, c) - 3
    for i, y in enumerate(range(top, SKY - 1, -1)):
        hw = max(0, 2 - i // 4)
        zz = c + (i // 3)
        for dx in range(-hw, hw + 1):
            if abs(dx) == hw or i % 4 == 0:
                if rng.random() < 0.85 or abs(dx) == hw:
                    b.set(c + dx, y, zz, f"{wood}_planks")
            if abs(dx) == hw and hw > 0:
                b.set(c + dx, y, zz - 1, f"{wood}_planks")
        b.set(c, y, zz, f"{wood}_log", axis="y")   # the keel, holding it together
        if i % 3 == 0 and i:
            b.set(c, y, zz - 1, f"{wood}_planks")
    # the broken mast across it, a scrap of sail
    my = top - 3
    for kk in range(-3, 4):
        b.set(c + kk, my, c - 1, f"{wood}_log", axis="x")
    for kk in range(-2, 3):
        if rng.random() < 0.7:
            b.set(c + kk, my - 1, c - 1, pick(rng, ("white_wool", "light_gray_wool", "cobweb")))
    # the rigging: chains from the balloon's skirt down to the wreck
    for dx, dz in ((-2, -1), (2, -1), (-2, 1), (2, 1)):
        x, z = c + dx, c + dz
        y = underside(inside, x, cy, z) - 1
        if b.empty(x, y + 1, z):
            continue   # a hole there
        while y >= top and b.empty(x, y, z):
            b.set(x, y, z, "iron_chain", axis="y", **NOWATER)
            y -= 1
    for x in range(c - 2, c + 3):
        b.set(x, top + 1, c, "iron_chain", axis="x", **NOWATER)
    b.set(c, underside(inside, c, cy, c), c, WOOL)
    chain(b, c, top + 2, underside(inside, c, cy, c) - 1, c)
    b.set(c, top + 1, c, f"{wood}_planks")
    for _ in range(8):
        x, y, z = c + rng.randint(-2, 2), rng.randint(SKY, top), c + rng.randint(-1, 4)
        if b.empty(x, y, z) and any(not b.empty(x + dx, y + dy, z + dz) for dx, dy, dz in N6):
            b.set(x, y, z, "cobweb")
    b.set(c - 1, top + 1, c + 1, f"{wood}_fence", **NOWATER)
    b.set(c - 1, top + 2, c + 1, f"{wood}_fence", **NOWATER)
    hanging(b, c - 1, top, c + 1, "soul_lantern")
    b.container(c + 1, top - 1, c, "chest", TREASURE, facing="north", type="single", waterlogged="false")
    b.set(c + 1, top - 2, c, f"{wood}_planks")
    prune(b, top + 3)
    return done(b)


# ── Sky lanterns ──────────────────────────────────────────────────────────

LANTERN_COLOURS = ("orange", "red", "yellow", "white", "pink", "orange")


def sky_lantern(b, x, y, z, colour, glow):
    """A paper lantern: a glowing body under a coloured paper top, a candle-cup under it."""
    b.set(x, y + 2, z, f"{colour}_wool")
    b.set(x, y + 1, z, glow)
    b.set(x, y, z, f"{colour}_stained_glass")


@gen("lantern_festival", kind="sky_feature", freq="very rare", n=3)
def lantern_festival(rng, st):
    """A cloud of paper lanterns let go from a little raft, drifting up at every height."""
    S = 21
    c = S // 2
    b = Build("Lantern Festival", "sky_feature", S, S)
    wood = pick(rng, st.woods)
    b.fill(c - 2, SKY, c - 2, c + 2, SKY, c + 2, f"{wood}_planks")
    b.ring(c - 2, c - 2, c + 2, c + 2, SKY + 1, SKY + 1, f"{wood}_fence", **NOWATER)
    b.set(c, SKY + 1, c - 2, f"{wood}_fence_gate", facing="north", in_wall="false", open="true", powered="false")
    b.fill(c, SKY + 1, c, c, SKY + 3, c, f"{wood}_fence", **NOWATER)
    b.set(c, SKY + 4, c, f"{wood}_slab", type="bottom", **NOWATER)
    standing(b, c - 1, SKY + 1, c + 1, st.light)
    b.barrel(c + 1, SKY + 1, c + 1, [("candle", rng.randint(8, 32))])
    b.barrel(c + 1, SKY + 1, c - 1, [("paper", rng.randint(8, 32))])
    glows = ("shroomlight", "ochre_froglight", "pearlescent_froglight", "glowstone") if st.key != "ashen" else \
        ("shroomlight", "verdant_froglight", "glowstone", "sea_lantern")
    placed = []
    tries = 0
    n = rng.randint(18, 26)
    while len(placed) < n and tries < 400:
        tries += 1
        y = rng.randint(SKY + 3, TOP - 2)
        r = 2 + (y - SKY) * 0.35   # the cloud widens as it rises
        a = rng.random() * math.tau
        d = rng.random() * min(r, c - 1)
        x, z = round(c + d * math.cos(a)), round(c + d * math.sin(a))
        if abs(x - c) <= 2 and abs(z - c) <= 2 and y < SKY + 6:
            continue
        if any(abs(x - px) <= 1 and abs(z - pz) <= 1 and abs(y - py) <= 3 for px, py, pz in placed):
            continue
        placed.append((x, y, z))
        sky_lantern(b, x, y, z, pick(rng, LANTERN_COLOURS), pick(rng, glows))
    return done(b)


# ── A lighthouse on a floating rock ───────────────────────────────────────

@gen("sky_lighthouse", kind="sky_feature", freq="very rare", n=3)
def sky_lighthouse(rng, st):
    """A banded lighthouse on a crag adrift in the sky: a gallery round its lamp, a keeper's door."""
    S = 15
    c = S // 2
    b = Build("Sky Lighthouse", "sky_feature", S, S)
    wood = pick(rng, st.woods)
    top = SKY + 6
    rock = ("stone", "andesite", "stone", "cobblestone", "tuff") if st.key != "ochre" else \
        ("sandstone", "terracotta", "smooth_sandstone", "granite")
    for d in range(top - SKY + 1):
        y = top - d
        R = 6.2 - d * 0.9 + rng.uniform(-0.3, 0.3)
        for x in range(S):
            for z in range(S):
                if math.hypot(x - c, z - c) <= R:
                    b.set(x, y, z, ("grass_block[snowy=false]" if st.key != "ochre" else "coarse_dirt") if d == 0
                          else pick(rng, rock))
    if b.empty(c, SKY, c):
        b.set(c, SKY, c, pick(rng, rock))
    band = pick(rng, (WOOL, "red_terracotta", "black_terracotta" if st.key == "ashen" else "red_terracotta"))
    white = "white_terracotta" if st.key != "ashen" else "light_gray_terracotta"
    t0, t1 = top + 1, top + 14
    for y in range(t0, t1 + 1):
        for dx, dz in circle_cells(2):
            b.set(c + dx, y, c + dz, band if ((y - t0) // 3) % 2 else white)
    for y in (t0 + 4, t0 + 8, t0 + 11):
        b.set(c, y, c - 2, "glass_pane", **NOWATER)
    for dx, dz in disc_cells(1):
        b.set(c + dx, top, c + dz, f"{wood}_planks")
    b.door(c, t0, c - 2, "south", wood)
    for y in range(t0, t1 + 1):
        b.set(c, y, c + 1, "ladder", facing="north", **NOWATER)
    b.set(c - 1, t0 + 2, c, st.wall_torch, facing="east")
    # the gallery and lamp room
    g = t1 + 1
    for dx, dz in disc_cells(3):
        b.set(c + dx, g, c + dz, white)
    for dx, dz in circle_cells(3):
        b.set(c + dx, g + 1, c + dz, "iron_bars", **NOWATER)
    b.set(c, g, c + 1, "air")
    b.set(c, g, c + 1, "ladder", facing="north", **NOWATER)
    b.set(c, g, c + 2, white)
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        b.fill(c + dx, g + 1, c + dz, c + dx, g + 2, c + dz, "iron_bars", **NOWATER)
    for dx, dz in ((0, -1), (-1, 0), (1, 0)):
        b.fill(c + dx, g + 1, c + dz, c + dx, g + 2, c + dz, "glass")
    b.set(c, g + 1, c + 1, "air")
    b.set(c, g + 2, c + 1, "glass")
    b.set(c, g + 1, c, "sea_lantern" if st.key != "ashen" else "crying_obsidian")
    b.set(c, g + 2, c, "glowstone" if st.key != "ashen" else "soul_campfire", **({} if st.key != "ashen" else
          dict(facing="north", lit="true", signal_fire="false", waterlogged="false")))
    m = pick(rng, st.roofs)
    b.fill(c - 1, g + 3, c - 1, c + 1, g + 3, c + 1, m.block)
    for d, (dx, dz) in (("south", (0, -2)), ("north", (0, 2)), ("east", (-2, 0)), ("west", (2, 0))):
        stair(b, c + dx, g + 3, c + dz, m.stairs, d)
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2), (-1, -2), (1, -2), (-1, 2), (1, 2), (-2, -1), (-2, 1), (2, -1), (2, 1)):
        if abs(dx) == 2 and abs(dz) == 2:
            continue
        stair(b, c + dx, g + 3, c + dz, m.stairs, "south" if dz == -2 else "north" if dz == 2 else "east" if dx == -2 else "west")
    stair(b, c, g + 4, c - 1, m.stairs, "south")
    stair(b, c, g + 4, c + 1, m.stairs, "north")
    stair(b, c - 1, g + 4, c, m.stairs, "east")
    stair(b, c + 1, g + 4, c, m.stairs, "west")
    b.set(c, g + 4, c, m.block)
    b.set(c, g + 5, c, "lightning_rod", facing="up", powered="false", **NOWATER)
    # the keeper's things on the rock: a path, a bench, a barrel of oil, a fence at the edge
    for z in range(0, c - 2):
        if b.get(c, top, z) not in ("air",):
            b.set(c, top, z, "dirt_path" if st.key != "ochre" else "gravel")
    b.barrel(c + 2, top + 1, c - 3, [("glowstone_dust", rng.randint(2, 9))])
    standing(b, c - 2, top + 1, c - 3, st.light)
    for x in range(S):
        for z in range(S):
            if b.get(x, top, z) not in ("air",) and b.empty(x, top + 1, z) and rng.random() < 0.15 \
                    and math.hypot(x - c, z - c) > 3:
                f = pick(rng, st.flowers)
                if f != "brown_mushroom" and b.get(x, top, z).startswith("grass"):
                    b.set(x, top + 1, z, f)
    return done(b)


# ── Airship docking towers (city buildings) ───────────────────────────────

def moored_ship(b, k, rng, cx, z0, z1, deck, cy, hz0):
    """A small airship moored nose first: the envelope from z0 (its nose, at the mast) to z1 round
    y = cy, the hull from hz0 to z1 - 2 with its deck at y = deck, a cabin aft, a propeller at the
    stern. Returns the hull's widths by z."""
    hz1 = z1 - 2
    widths, _ = boat(b, k, cx, hz0, hz1, deck, 3, 2, bow=0.3, rudder=False, gap={(cx, hz0)})
    paint = livery(k, cx, cy, z0, z1, pick(rng, ("bands", "ribs", "gores")), every=3)
    inside = envelope(b, cx, cy, z0, z1, 5.2, 4.2, paint, nose=0.4, pf=2.3, pb=2.0)
    fins(b, inside, cx, cy, z1, 4, 2, k.fin, dirs="ulr", trim=k.trim, tail=False)
    c0 = hz1 - 4
    cabin(b, cx - 2, c0, cx + 2, hz1 - 1, deck, 3, k.wall, k.plank, k.pane, corner=k.corner, every=2, floor=k.deckplank)
    b.door(cx, deck + 1, c0, "north", k.wood)
    hanging(b, cx, deck + 3, c0 + 2, k.light)
    if rng.random() < 0.5:
        b.chest(cx + 1, deck + 1, hz1 - 2, "west", TREASURE)
    else:
        b.bed(cx + 1, deck + 1, hz1 - 2, "north", pick(rng, k.st.beds))
    b.set(cx - 1, deck + 1, hz1 - 2, "barrel", facing="up", open="false")
    for z in (hz0 + 3, c0 - 1):
        for s in (-1, 1):
            if b.get(cx + s * widths[z], deck + 1, z) == k.rail:
                rig(b, cx + s * widths[z], deck + 2, z)
    for x in (cx - 2, cx + 2):
        rig(b, x, deck + 5, hz1 - 1)
    b.set(cx, deck + 1, hz1, k.engine, **({"facing": "south", "lit": "true"} if k.engine == "blast_furnace" else {}))
    propeller(b, cx, deck + 1, hz1 + 1, k)
    return widths, inside


def spiral(b, x0, z0, x1, z1, floor_y, block, stairs, entry, torch=None):
    """Stairs climbing round the ring inside the walls x0..x1, z0..z1 (a 5x5 inside round a 3x3
    core), from the ground at entry = (x, z) (a side cell) to a last step level with floor_y;
    the corners are landings. Returns {cell: the highest step's y there}."""
    ring = [(x, z0) for x in range(x0, x1 + 1)] + [(x1, z) for z in range(z0 + 1, z1 + 1)] + \
        [(x, z1) for x in range(x1 - 1, x0 - 1, -1)] + [(x0, z) for z in range(z1 - 1, z0, -1)]
    corners = {(x0, z0), (x1, z0), (x0, z1), (x1, z1)}
    i = ring.index(entry)
    h, top, n = 0, {}, 0
    while h < floor_y:
        i = (i + 1) % len(ring)
        x, z = ring[i]
        nx, nz = ring[(i + 1) % len(ring)]
        if (x, z) in corners:
            if h >= 1:
                b.set(x, h, z, block)
                top[(x, z)] = h
            continue
        h += 1
        face = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(nx - x, nz - z)]
        b.set(x, h, z, stairs, facing=face, half="bottom", **NOWATER)
        if h > 1:
            b.set(x, h - 1, z, stairs, facing=OPPOSITE[face], half="top", **NOWATER)   # the soffit
        top[(x, z)] = h
        n += 1
        if torch and n % 5 == 3 and h + 2 < floor_y - 2:
            cx_, cz_ = (x0 + x1) // 2, (z0 + z1) // 2
            out = "west" if x == x0 else "east" if x == x1 else "north" if z == z0 else "south"
            if (x == x0 or x == x1) != (z == z0 or z == z1):
                b.set(x, h + 2, z, torch, facing=out)
    return top, ring[(i + 1) % len(ring)]


@gen("airship_dock", kind="city", freq="rare", unique=True, n=3)
def airship_dock(rng, st):
    """The city's air station: a stone tower with a stair climbing round its core to a railed
    mooring platform, a lattice mast over it with a beacon, a wind sock and a flag. Most days a
    small airship is moored nose to the mast, a gangway down to its deck; some days the mast stands
    empty. A waiting room at its foot, the next departure always pending."""
    from buildings import new
    from parts import gable, lamp_post, bench
    S = 25
    b = new("Airship Dock", "city", S, S)
    k = kit(rng, st)
    wood = pick(rng, st.woods)
    wall = pick(rng, st.stone)
    found = pick(rng, st.found)
    m = pick(rng, st.roofs)
    tx0, tz0, tx1, tz1 = 9, 2, 15, 8          # the tower
    tcx, tcz = 12, 5
    F = rng.randint(28, 31)                   # the platform's floor
    # the tower: walls, corner quoins, slit windows, the core
    b.fill(tx0, 0, tz0, tx1, 0, tz1, found)
    b.ring(tx0, tz0, tx1, tz1, 1, F - 1, wall)
    for x, z in ((tx0, tz0), (tx1, tz0), (tx0, tz1), (tx1, tz1)):
        b.fill(x, 1, z, x, F - 1, z, found)
    b.fill(tx0 + 2, 1, tz0 + 2, tx1 - 2, F, tz1 - 2, found)
    for y in range(4, F - 2, 5):
        for x, z in ((tcx, tz0), (tcx, tz1), (tx0, tcz), (tx1, tcz)):
            if (x, z) != (tx0, tcz) or y > 3:
                b.set(x, y, z, k.pane if k.pane != "glass_pane" else "glass_pane", **NOWATER)
                b.set(x, y + 1, z, k.pane if k.pane != "glass_pane" else "glass_pane", **NOWATER)
    for y in (F // 3, 2 * F // 3):   # string courses
        for x in range(tx0 - 1, tx1 + 2):
            for z in (tz0 - 1, tz1 + 1):
                stair(b, x, y, z, stairs_for(found), "south" if z < tcz else "north", "top")
        for z in range(tz0, tz1 + 1):
            stair(b, tx0 - 1, y, z, stairs_for(found), "east", "top")
            stair(b, tx1 + 1, y, z, stairs_for(found), "west", "top")
    # the platform, its floor on corbels, railed all round but for the gangway
    px0, pz0, px1, pz1 = tx0 - 1, tz0 - 1, tx1 + 1, tz1 + 1
    plank = f"{wood}_planks" if st.key != "ashen" else pick(rng, ("polished_blackstone", "dark_oak_planks"))
    b.fill(px0, F, pz0, px1, F, pz1, plank)
    top, exit_ = spiral(b, tx0 + 1, tz0 + 1, tx1 - 1, tz1 - 1, F, f"{wood}_planks", f"{wood}_stairs", (tx0 + 1, tcz),
                        torch=st.wall_torch)
    for (x, z), h in top.items():   # the stairwell's opening
        if F - 3 <= h < F:
            b.set(x, F, z, "air")
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            if x in (px0, px1) or z in (pz0, pz1):
                f = "south" if z == pz0 else "north" if z == pz1 else "east" if x == px0 else "west"
                stair(b, x, F - 1, z, stairs_for(plank), f, "top")
    gang = (tcx, pz1)
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            if (x in (px0, px1) or z in (pz0, pz1)) and (x, z) != gang:
                railing(b, k, x, F + 1, z)
    for x, z in ((px0, pz0), (px1, pz0), (px0, pz1), (px1, pz1)):
        b.set(x, F + 1, z, k.post if k.post != "iron_bars" else "polished_blackstone_wall", **NOWATER)
        standing(b, x, F + 2, z, st.light)
    # the hole's rail, so no one walks into the stairwell
    for (x, z), h in top.items():
        if F - 3 <= h < F:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                c = (x + dx, z + dz)
                if b.get(c[0], F, c[1]) == plank and c != exit_ and (c not in top or top[c] < F - 3) and \
                        tx0 < c[0] < tx1 and tz0 < c[1] < tz1 and not (tx0 + 2 <= c[0] <= tx1 - 2 and tz0 + 2 <= c[1] <= tz1 - 2):
                    put(b, c[0], F + 1, c[1], k.rail if k.rail != "iron_bars" else "iron_bars", **NOWATER)
    # the mast: a lattice over the core, braced every three blocks, to the mooring head
    M = F + 11
    post = k.post if k.post != "polished_blackstone_wall" else "iron_bars"
    for x in (tcx - 1, tcx + 1):
        for z in (tcz - 1, tcz + 1):
            b.fill(x, F + 1, z, x, M - 1, z, post, **NOWATER)
    for y in range(F + 3, M, 3):
        b.ring(tcx - 1, tcz - 1, tcx + 1, tcz + 1, y, y, post, **NOWATER)
    b.fill(tcx, F + 1, tcz, tcx, M - 1, tcz, "iron_chain", axis="y", **NOWATER)
    head = {"timber": "iron_block", "ochre": "waxed_cut_copper", "ashen": "polished_blackstone"}[st.key]
    b.fill(tcx - 1, M, tcz - 1, tcx + 1, M, tcz + 1, head)
    for x, z, f in ((tcx, tcz - 2, "south"), (tcx, tcz + 2, "north"), (tcx - 2, tcz, "east"), (tcx + 2, tcz, "west")):
        stair(b, x, M, z, stairs_for(head), f, "top")
    beacon = {"timber": "sea_lantern", "ochre": "ochre_froglight", "ashen": "crying_obsidian"}[st.key]
    b.set(tcx, M + 1, tcz, beacon, **({"axis": "y"} if beacon.endswith("froglight") else {}))
    standing(b, tcx, M + 2, tcz, st.light)
    for x, z in ((tcx - 1, tcz - 1), (tcx + 1, tcz - 1), (tcx - 1, tcz + 1), (tcx + 1, tcz + 1)):
        hanging(b, x, M - 1, z, st.light) if b.empty(x, M - 1, z) else None
    # the wind sock on the north-west corner, a flag on the north-east
    b.fill(tcx - 1, M + 1, tcz - 1, tcx - 1, M + 2, tcz - 1, k.fence, **NOWATER)
    sock = ("orange_wool", "white_wool", "orange_wool") if st.key != "ashen" else ("red_wool", "white_wool", "red_wool")
    for i, c in enumerate(sock):
        b.set(tcx - 2 - i, M + 2, tcz - 1, c)
    flag(b, tcx + 1, M + 1, tcz - 1, k.flag, length=3, height=2, along="x", pole=k.fence)
    # the moored ship (most days), its nose chained to the head, a gangway down to its deck
    moored = rng.random() > 0.2   # most days
    sz0 = tcz + 3
    if moored:
        deck = F
        widths, inside = moored_ship(b, k, rng, tcx, sz0, S - 2, deck, M, pz1 + 2)
        for z in range(tcz + 2, sz0):
            b.set(tcx, M, z, "iron_chain", axis="z", **NOWATER)
        b.set(tcx, F, pz1 + 1, k.slab, type="top", **NOWATER)
        for x in (tcx - 1, tcx + 1):
            railing(b, k, x, F + 1, pz1 + 1)
    else:
        for z in (tcz + 2,):
            b.fill(tcx, M - 3, z, tcx, M - 1, z, "iron_chain", axis="y", **NOWATER)   # the slack mooring line
        b.set(tcx, M, tcz + 2, stairs_for(head), facing="north", half="top", **NOWATER)
        railing(b, k, *gang[:1], F + 1, gang[1])
        b.set(tcx, F + 1, pz1, f"{wood}_fence_gate", facing="south", in_wall="false", open="false", powered="false")
    # the waiting room at the tower's foot (west), its door on the street
    wx0, wz0, wx1, wz1 = 2, 1, 8, 7
    b.fill(wx0, 0, wz0, wx1, 0, wz1, f"{wood}_planks")
    b.ring(wx0, wz0, wx1, wz1, 1, 4, wall)
    for x, z in ((wx0, wz0), (wx1, wz0), (wx0, wz1), (wx1, wz1)):
        b.fill(x, 1, z, x, 4, z, found)
    b.clear(wx0 + 1, 1, wz0 + 1, wx1 - 1, 4, wz1 - 1)
    gable(b, wx0, wz0, wx1, wz1, 5, m, along="x", fill=wall, end_over=0)
    dx = (wx0 + wx1) // 2
    b.door(dx, 1, wz0, "south", wood)
    b.path(dx, 0, dx, 0)
    b.set(dx, 0, wz0, found)
    for x in (wx0 + 1, wx1 - 1):
        b.set(x, 2, wz0, "glass_pane", **NOWATER)
        b.set(x, 3, wz0, "glass_pane", **NOWATER)
    for z in (3, 5):
        b.set(wx0, 2, z, "glass_pane", **NOWATER)
        b.set(wx0, 3, z, "glass_pane", **NOWATER)
    b.sign(dx + 1, 3, wz0 - 1, "north", ["", "Air", "Station", ""], wood)
    b.sign(dx - 1, 3, wz0 - 1, "north", "[departures]", wood)
    # the doorway through to the tower's stair
    b.clear(wx1, 1, tcz, wx1, 2, tcz)
    b.clear(tx0, 1, tcz, tx0, 2, tcz)
    b.set(tx0, 0, tcz, found)
    # inside: the ticket window by the door, benches facing the board, lamps
    b.fill(wx1 - 2, 1, wz0 + 2, wx1 - 1, 1, wz0 + 2, f"{wood}_slab", type="top", **NOWATER)
    b.set(wx1 - 1, 2, wz0 + 2, "bell", attachment="floor", facing="west", powered="false")
    b.chest(wx1 - 1, 1, wz0 + 1, "west")
    for z in range(wz0 + 3, wz1):
        stair(b, wx0 + 1, 1, z, f"{wood}_stairs", "west")
    b.sign(wx0 + 2, 3, wz1 - 1, "north", "[destinations]", wood)
    b.sign(wx0 + 3, 3, wz1 - 1, "north", "[departures]", wood)
    b.set(wx0 + 4, 1, wz1 - 1, "potted_fern" if st.key != "ochre" else "potted_cactus")
    hang(b, wx0 + 2, wz0 + 3, 1, st)
    hang(b, wx1 - 2, wz0 + 4, 1, st)
    # the yard east of the tower: a lamp, a bench, luggage, and the path round to the door
    lamp_post(b, 18, 2, st, wood)
    bench(b, 19, 4, "north", wood, 2)
    for x, z in ((20, 7), (21, 7), (20, 8)):
        b.barrel(x, 1, z, [("paper", rng.randint(1, 6))] if rng.random() < 0.4 else ())
    b.path(17, 0, 17, 1)
    b.path(dx - 1, 0, dx + 1, 0)
    return b


def stairs_for(block):
    """The stairs that go with a full block (planks give their wood)."""
    from parts import stairs_of
    if block == "iron_block":
        return "polished_andesite_stairs"
    if block == "waxed_cut_copper":
        return "waxed_cut_copper_stairs"
    if block == "polished_blackstone":
        return "polished_blackstone_stairs"
    return stairs_of(block, "stone_brick_stairs")


@gen("mooring_mast", kind="city", freq="average", n=3)
def mooring_mast(rng, st):
    """A lattice mooring mast on a plinth: a ladder up its core to a railed platform, a mooring
    head with a lamp over it, and (most days) a small blimp tied up nose to it. A sign by the
    path gives the next departure."""
    from buildings import new
    from parts import lamp_post, bench
    S = 15
    b = new("Mooring Mast", "city", S, S)
    k = kit(rng, st)
    wood = pick(rng, st.woods)
    found = pick(rng, st.found)
    c, mz = S // 2, 4
    F = rng.randint(17, 20)   # the platform
    M = F + 4                 # the mooring head
    b.fill(c - 2, 0, mz - 2, c + 2, 0, mz + 2, found)
    for x in range(c - 2, c + 3):
        for z in (mz - 2, mz + 2):
            stair(b, x, 1, z, stairs_for(found), "south" if z > mz else "north")
    for z in range(mz - 1, mz + 2):
        stair(b, c - 2, 1, z, stairs_for(found), "east")
        stair(b, c + 2, 1, z, stairs_for(found), "west")
    b.fill(c - 1, 1, mz - 1, c + 1, 1, mz + 1, found)
    # the core with its ladder, the lattice round it
    core = k.log() if st.key != "ashen" else "polished_basalt"
    b.fill(c, 2, mz, c, M - 1, mz, core, axis="y")
    b.set(c, 1, mz - 1, "air")
    b.set(c, 1, mz - 1, "ladder", facing="north", **NOWATER)
    b.fill(c, 2, mz - 1, c, F, mz - 1, "ladder", facing="north", **NOWATER)
    post = k.post if k.post != "polished_blackstone_wall" else "iron_bars"
    for x in (c - 1, c + 1):
        for z in (mz - 1, mz + 1):
            b.fill(x, 2, z, x, F - 1, z, post, **NOWATER)
    for y in range(4, F, 3):
        for x in (c - 1, c, c + 1):
            put(b, x, y, mz + 1, post, **NOWATER)
        for z in (mz - 1, mz, mz + 1):
            put(b, c - 1, y, z, post, **NOWATER)
            put(b, c + 1, y, z, post, **NOWATER)
    # the platform
    plank = f"{wood}_planks" if st.key != "ashen" else "dark_oak_planks"
    for x in range(c - 2, c + 3):
        for z in range(mz - 2, mz + 3):
            if (x, z) != (c, mz - 1) and (x, z) != (c, mz):
                b.set(x, F, z, plank)
            if max(abs(x - c), abs(z - mz)) == 2:
                railing(b, k, x, F + 1, z)
                stair(b, x, F - 1, z, stairs_for(plank), "south" if z == mz - 2 else "north" if z == mz + 2 else
                      "east" if x == c - 2 else "west", "top")
    head = {"timber": "iron_block", "ochre": "waxed_cut_copper", "ashen": "polished_blackstone"}[st.key]
    b.set(c, M, mz, head)
    stair(b, c, M, mz + 1, stairs_for(head), "north", "top")
    b.set(c, M + 1, mz, k.fence if st.key != "ashen" else "iron_bars", **NOWATER)
    standing(b, c, M + 2, mz, st.light)
    for x, z in ((c - 2, mz - 2), (c + 2, mz + 2)):
        b.set(x, F + 2, z, post, **NOWATER)
        standing(b, x, F + 3, z, st.light)
    # the blimp
    if rng.random() < 0.8:
        cy = M
        z0, z1 = mz + 2, S - 2
        R = 3.0

        inside = envelope(b, c, cy, z0, z1, R + 0.3, R, livery(k, c, cy, z0, z1, pick(rng, ("bands", "belly")), every=3),
                          nose=0.4, pf=2.2, pb=1.8)
        fins(b, inside, c, cy, z1, 3, 2, k.fin, trim=k.trim, tail=False)
        # the car under it
        gy = cy - 6
        g0, g1 = z0 + 3, z0 + 6
        b.fill(c - 1, gy, g0, c + 1, gy, g1, k.deckplank)
        b.ring(c - 1, g0, c + 1, g1, gy + 1, gy + 2, k.pane, **NOWATER)
        for x, z in ((c - 1, g0), (c + 1, g0), (c - 1, g1), (c + 1, g1)):
            b.fill(x, gy + 1, z, x, gy + 2, z, k.corner, **({"axis": "y"} if k.corner.endswith("_log") else {}))
        b.fill(c - 1, gy + 3, g0, c + 1, gy + 3, g1, k.plank)
        hanging(b, c, gy + 2, g0 + 1, k.light)
        for x in (c - 1, c + 1):
            for z in (g0, g1):
                rig(b, x, gy + 4, z)
        b.set(c, gy + 1, g1 - 1, k.stairs, facing="south", half="bottom", **NOWATER)
        b.set(c, M, mz + 2, "iron_chain", axis="z", **NOWATER) if b.empty(c, M, mz + 2) else None
    else:
        b.fill(c, M - 2, mz + 1, c, M - 1, mz + 1, "iron_chain", axis="y", **NOWATER)
        b.set(c, M - 1, mz + 1, "iron_chain", axis="y", **NOWATER)
    # the path, a lamp, a bench and the departures sign
    b.path(c, 0, c, mz - 3)
    lamp_post(b, c + 3, 1, st, wood)
    bench(b, c - 4, 2, "north", wood, 2)
    b.sign(c - 2, 1, 1, "north", "[departures]", wood, wall=False)
    return b
