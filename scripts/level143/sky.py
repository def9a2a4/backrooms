"""More of Level 143's sky features (balloons and airships float over each city's mine entrance).

Every feature lies between y = SKY (world y100) and SKY + 26 (world y126, under the top of the
city's buffer), its lowest block at SKY. The envelopes are WOOL (one colour per city) with fixed
accents; woods and lights come from the style.
"""

import math

from buildings import SKY, gen
from parts import pick, tree
from schem import TREASURE, WOOL, Build

TOP = SKY + 26
NOWATER = dict(waterlogged="false")
ACCENTS = ("white_wool", "light_gray_wool", "black_wool", "white_wool")


def done(b):
    """Checks the feature fits the sky band (between SKY and TOP)."""
    ys = [y for (_, y, _), (n, _) in b.blocks.items() if n != "air"]
    if min(ys) < SKY or max(ys) > TOP:
        raise ValueError(f"{b.name}: y {min(ys)}..{max(ys)} outside {SKY}..{TOP}")
    return b


# ── Shapes ────────────────────────────────────────────────────────────────

def body(b, box, inside, paint, hollow=True):
    """Draws the blocks of {@code inside} in box (x0, y0, z0, x1, y1, z1): only its skin if hollow.
    paint(x, y, z) gives the block, or None for a hole."""
    x0, y0, z0, x1, y1, z1 = box
    for y in range(y0, y1 + 1):
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                if not inside(x, y, z):
                    continue
                if hollow and all(inside(x + dx, y + dy, z + dz) for dx, dy, dz in
                                  ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
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


def underside(inside, x, y, z):
    """The lowest y at or below y still inside (for hanging things off an envelope)."""
    while inside(x, y - 1, z):
        y -= 1
    return y


def fins(b, inside, cx, cy, ztail, n, reach, state, dirs="udlr", jag=False):
    """Tail fins: n rows forward from ztail, reaching `reach` past the axis at the tail."""
    for k in range(n):
        z = ztail - k
        ext = round(reach * (1 - k / (n + 0.5)))
        if jag and k % 2:
            ext -= 1
        for d in range(1, ext + 1):
            for c, sx, sy in (("u", 0, 1), ("d", 0, -1), ("l", -1, 0), ("r", 1, 0)):
                if c in dirs:
                    x, y = cx + sx * d, cy + sy * d
                    if not inside(x, y, z) and b.empty(x, y, z):
                        b.set(x, y, z, state)


def chain(b, x, y0, y1, z):
    b.fill(x, y0, z, x, y1, z, "iron_chain", axis="y", **NOWATER)


def rope(b, inside, x, y0, z):
    """A chain from y0 up to the envelope above (stops under its skin)."""
    y = y0
    while y < TOP and not inside(x, y + 1, z) and b.empty(x, y, z):
        b.set(x, y, z, "iron_chain", axis="y", **NOWATER)
        y += 1


def propeller(b, x, y, z, wood, facing="south"):
    b.set(x, y, z, f"{wood}_fence", **NOWATER)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        b.set(x + dx, y + dy, z, f"{wood}_trapdoor", facing=facing, half="bottom", open="true",
              powered="false", **NOWATER)


def engine(b, x, y, z, wood):
    """An engine pod: a blast furnace with a propeller behind it (towards +z)."""
    b.set(x, y, z, "blast_furnace", facing="north", lit="false")
    b.set(x, y, z - 1, f"{wood}_trapdoor", facing="north", half="bottom", open="true", powered="false", **NOWATER)
    propeller(b, x, y, z + 1, wood)


def hanging(b, x, y, z, light):
    b.set(x, y, z, light, hanging="true", **NOWATER)


def standing(b, x, y, z, light):
    b.set(x, y, z, light, hanging="false", **NOWATER)


def hull(b, cx, z0, z1, deck, half, depth, wood, deck_wood=None, side=None, rail=True, keep=None):
    """A boat's hull, bow at z0: the deck at y = deck, the keel `depth` below it. side(dy) gives the
    block for the hull's layer dy below the deck; keep(x, y, z) False knocks a hole. Returns the
    half-width at the deck for each z."""
    plank = f"{wood}_planks"
    dplank = f"{deck_wood or wood}_planks"
    keep = keep or (lambda x, y, z: True)
    n = max(1, z1 - z0)
    widths = {}

    def put(x, y, z, s):
        if keep(x, y, z):
            b.set(x, y, z, s)

    for z in range(z0, z1 + 1):
        t = (z - z0) / n
        s = min(1.0, (t / 0.33) ** 0.6, 0.55 + 0.45 * (1 - t) / 0.15)
        h = max(0, round(half * s))
        widths[z] = h
        prev = h
        for dy in range(depth + 1):
            hh = h if dy == 0 else max(0, round(h * math.sqrt(max(0.0, 1 - (dy / (depth + 0.6)) ** 2))))
            y = deck - dy
            st = dplank if dy == 0 else (side(dy) if side else plank)
            if dy == 0 or dy == depth or z in (z0, z1):
                for x in range(cx - hh, cx + hh + 1):
                    put(x, y, z, st)
            else:
                for a in range(hh, max(hh, prev) + 1):
                    put(cx - a, y, z, st)
                    put(cx + a, y, z, st)
            prev = hh
        if rail:
            if z in (z0, z1):
                for x in range(cx - h, cx + h + 1):
                    put(x, deck + 1, z, f"{wood}_fence")
            else:
                put(cx - h, deck + 1, z, f"{wood}_fence")
                put(cx + h, deck + 1, z, f"{wood}_fence")
    # close the side walls where the width steps by more than one along z
    for z in range(z0 + 1, z1 + 1):
        a, c = widths[z - 1], widths[z]
        if abs(a - c) > 1:
            for w in range(min(a, c) + 1, max(a, c)):
                put(cx - w, deck, z if a > c else z - 1, dplank)
                put(cx + w, deck, z if a > c else z - 1, dplank)
    return widths


def cabin(b, x0, z0, x1, z1, y, h, wall, roof, pane, corner=None, windows=True, every=2, floor=None):
    """A box room: floor at y, walls y+1..y+h, a flat roof at y+h+1; panes along the walls."""
    b.fill(x0, y, z0, x1, y, z1, floor or (wall if wall.endswith("_planks") else roof))
    b.ring(x0, z0, x1, z1, y + 1, y + h, wall)
    b.fill(x0, y + h + 1, z0, x1, y + h + 1, z1, roof)
    b.clear(x0 + 1, y + 1, z0 + 1, x1 - 1, y + h, z1 - 1)
    if corner:
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            b.fill(x, y + 1, z, x, y + h, z, corner, **({"axis": "y"} if "log" in corner else {}))
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


def stripes(z0, every, a, c, cap=0, z1=None, cap_state=None):
    """An envelope paint: bands of c every `every` along z, else a; caps at the ends."""
    def paint(x, y, z):
        if cap and (z < z0 + cap or (z1 is not None and z > z1 - cap)):
            return cap_state or c
        return c if (z - z0) % every == 0 else a
    return paint


# ── Zeppelin ──────────────────────────────────────────────────────────────

@gen("zeppelin", kind="sky_feature", freq="common", n=3)
def zeppelin(rng, st):
    L = rng.randint(21, 25)
    W, cx = 15, 7
    b = Build("Zeppelin", "sky_feature", W, L)
    wood = pick(rng, st.woods)
    acc = pick(rng, ACCENTS)
    R = rng.choice((4.4, 4.7))
    cy = SKY + 10
    belt = rng.random() < 0.5

    def paint(x, y, z):
        if z <= 1 or (belt and y == cy):
            return acc
        return WOOL

    inside = envelope(b, cx, cy, 0, L - 1, R + 0.3, R, paint, nose=0.4, pf=2.6, pb=1.9)
    fins(b, inside, cx, cy, L - 1, 6, int(R) + 3, acc)
    # the gondola, under the front third
    g0, g1 = 4, 4 + rng.randint(6, 8)
    gy = SKY + 1
    cabin(b, cx - 2, g0, cx + 2, g1, gy, 3, f"{wood}_planks", f"{wood}_planks",
          "glass_pane", corner=f"{wood}_log", every=1)
    b.fill(cx - 1, SKY, g0 + 1, cx + 1, SKY, g1 - 1, f"{wood}_planks")
    b.set(cx, gy + 1, g0, "glass_pane", **NOWATER)
    hanging(b, cx, gy + 3, (g0 + g1) // 2, st.light)
    b.set(cx - 1, gy + 1, g0 + 1, "lectern", facing="south", has_book="false", powered="false")
    if rng.random() < 0.4:
        b.chest(cx + 1, gy + 1, g1 - 1, "west", TREASURE)
    for x in (cx - 2, cx + 2):
        for z in (g0, g1):
            rope(b, inside, x, gy + 4, z)
    # engine pods on struts either side
    ez = L // 2 + 2
    for s in (-1, 1):
        x = cx + s * (int(R) + 2)
        y = cy - 2
        b.set(x, y, ez, "blast_furnace", facing="north", lit="false")
        propeller(b, x, y, ez + 1, wood)
        for xx in range(cx + s * (int(R) - 1), x, s):
            if not inside(xx, y, ez) and b.empty(xx, y, ez):
                b.set(xx, y, ez, f"{wood}_fence", **NOWATER)
    return done(b)


# ── Galleon under a balloon ───────────────────────────────────────────────

def mast(b, x, z, y0, y1, wood, sail, width, bulge=True):
    """A mast from y0 to y1 with a square sail before it (towards -z) on yards."""
    b.fill(x, y0, z, x, y1, z, f"{wood}_log", axis="y")
    top, bot = y1 - 1, y0 + 2
    for y in range(bot, top + 1):
        w = width - (1 if y == top else 0)
        for dx in range(-w, w + 1):
            zz = z - 1 - (1 if bulge and abs(dx) < w - 1 and bot < y < top else 0)
            b.set(x + dx, y, zz, sail)
    for y in (top + 1, bot - 1):
        for dx in range(-width, width + 1):
            if dx:
                b.set(x + dx, y, z - 1, f"{wood}_fence", **NOWATER)
        b.set(x, y, z - 1, f"{wood}_fence", **NOWATER)


@gen("galleon", kind="sky_feature", freq="average", n=3)
def galleon(rng, st):
    L = rng.randint(21, 24)
    W, cx = 13, 6
    b = Build("Sky Galleon", "sky_feature", W, L)
    wood, deck_wood = pick(rng, st.woods), pick(rng, st.woods)
    deck = SKY + 3
    z0, z1 = 2, L - 2
    widths = hull(b, cx, z0, z1, deck, 3, 3, wood, deck_wood,
                  side=lambda dy: f"stripped_{wood}_log[axis=z]" if dy == 1 and wood != "pale_oak" else f"{wood}_planks")
    # bowsprit
    for z in range(0, z0 + 1):
        b.set(cx, deck + 1 + (1 if z == 0 else 0), z, f"{wood}_fence", **NOWATER)
    b.set(cx, deck + 1, 0, f"{wood}_fence", **NOWATER)
    # the aft cabin with a raised deck
    c0 = z1 - 5
    cabin(b, cx - 2, c0, cx + 2, z1 - 1, deck, 2, f"{wood}_planks", f"{deck_wood}_planks", "glass_pane",
          corner=f"{wood}_log", every=2)
    for x in range(cx - 2, cx + 3):
        b.set(x, deck + 4, c0, f"{wood}_fence", **NOWATER)
    for z in range(c0, z1):
        b.set(cx - 2, deck + 4, z, f"{wood}_fence", **NOWATER)
        b.set(cx + 2, deck + 4, z, f"{wood}_fence", **NOWATER)
    b.door(cx, deck + 1, c0, "north", wood)
    if rng.random() < 0.5:
        b.chest(cx + 1, deck + 1, z1 - 2, "west", TREASURE)
    else:
        b.bed(cx + 1, deck + 1, z1 - 2, "north", pick(rng, st.beds))
    hanging(b, cx, deck + 2, z1 - 2, st.light)
    for x in (cx - 2, cx + 2):
        standing(b, x, deck + 5, z1 - 1, st.light)
    # masts and sails
    sail = rng.choice(("white_wool", "white_wool", "light_gray_wool"))
    masts = [min(c0 - 2, z0 + round((z1 - z0) * f)) for f in ((0.3, 0.58) if L < 23 else (0.25, 0.47, 0.66))]
    for i, mz in enumerate(masts):
        mast(b, cx, mz, deck + 1, deck + 9, wood, sail, 3 if i == len(masts) - 1 or len(masts) == 2 else 2)
    # the balloon over it all
    cy = deck + 14

    inside = envelope(b, cx, cy, 1, L - 1, 5.6, 4.2, lambda x, y, z: "white_wool" if z % 4 == 0 else WOOL,
                      nose=0.5, pf=2.6, pb=2.6)
    for z in (z0 + 3, z1 - 1):
        for x in (cx - widths[z], cx + widths[z]):
            rope(b, inside, x, deck + 2, z)
    for mz in masts:
        rope(b, inside, cx, deck + 10, mz)
    b.set(cx, deck + 1, z0 + 2, st.light, hanging="false", **NOWATER)
    return done(b)


# ── Catamaran ─────────────────────────────────────────────────────────────

@gen("catamaran", kind="sky_feature", freq="average", n=3)
def catamaran(rng, st):
    L = rng.randint(19, 22)
    W = 17
    b = Build("Twin-Hull Airship", "sky_feature", W, L + 1)
    wood = pick(rng, st.woods)
    deck = SKY + 2
    acc = pick(rng, ACCENTS)
    xs = (3, 13)
    cy = deck + 10
    insides = []
    for hx in xs:
        hull(b, hx, 1, L - 2, deck, 1, 2, wood)
        insides.append(envelope(b, hx, cy, 0, L - 1, 3.3, 3.4,
                                lambda x, y, z: acc if z in (0, 1, L - 2, L - 1) or (z - 1) % 6 == 0 else WOOL,
                                nose=0.45, pf=2.5, pb=2.0))
        fins(b, insides[-1], hx, cy, L - 1, 4, 5, acc, dirs="u")
        engine(b, hx, deck, L - 1, wood)
    both = lambda x, y, z: any(f(x, y, z) for f in insides)
    # the bridge deck between the hulls, with a cabin
    b0, b1 = round(L * 0.3), round(L * 0.72)
    b.fill(4, deck, b0, 12, deck, b1, f"{wood}_planks")
    for x in range(4, 13):
        if x not in range(7, 10):
            b.set(x, deck + 1, b0, f"{wood}_fence", **NOWATER)
        b.set(x, deck + 1, b1, f"{wood}_fence", **NOWATER)
    for hx in xs:
        for z in range(b0, b1 + 1):
            b.set(hx + (1 if hx < 8 else -1), deck + 1, z, "air")
    cabin(b, 6, b0 + 2, 10, b1 - 1, deck, 3, f"{wood}_planks", f"{wood}_planks", pick(rng, st.panes) if
          pick(rng, st.panes) != "air" else "glass_pane", corner=f"{wood}_log")
    b.door(8, deck + 1, b0 + 2, "north", wood)
    hanging(b, 8, deck + 3, (b0 + b1) // 2 + 1, st.light)
    if rng.random() < 0.5:
        b.chest(7, deck + 1, b1 - 2, "east", TREASURE)
    b.set(9, deck + 1, b1 - 2, "crafting_table")
    # beams joining the envelopes, and rigging
    for z in (3, L // 2, L - 5):
        for x in range(0, W):
            if not both(x, cy, z) and b.empty(x, cy, z) and 3 < x < 13:
                b.set(x, cy, z, f"{wood}_log", axis="x")
        for hx in xs:
            rope(b, both, hx, deck + 2, z)
    for x in (6, 10):
        rope(b, lambda x, y, z: y > deck + 8, x, deck + 5, b0 + 2)
        rope(b, lambda x, y, z: y > deck + 8, x, deck + 5, b1 - 1)
        b.fill(x, deck + 5, b0 + 2, x, deck + 5, b1 - 1, f"{wood}_fence", **NOWATER)
    return done(b)


# ── Scout blimp ───────────────────────────────────────────────────────────

@gen("scout_blimp", kind="sky_feature", freq="very common", n=3)
def scout_blimp(rng, st):
    L = rng.randint(12, 15)
    W, cx = 9, 4
    b = Build("Scout Blimp", "sky_feature", W, L)
    wood = pick(rng, st.woods)
    acc = pick(rng, ACCENTS)
    cy = SKY + 6
    inside = envelope(b, cx, cy, 0, L - 2, 3.4, 3.2, lambda x, y, z: acc if z == 0 or (x == cx and y > cy) else WOOL,
                      nose=0.45, pf=2.2, pb=1.8)
    fins(b, inside, cx, cy, L - 2, 3, 4, acc)
    # an open basket
    g0 = L // 2 - 3
    b.fill(cx - 1, SKY, g0, cx + 1, SKY, g0 + 3, f"{wood}_planks")
    b.ring(cx - 1, g0, cx + 1, g0 + 3, SKY + 1, SKY + 1, f"{wood}_fence", **NOWATER)
    b.set(cx, SKY + 1, g0 + 1, f"{wood}_trapdoor", facing="north", half="top", open="false", powered="false", **NOWATER)
    for x in (cx - 1, cx + 1):
        for z in (g0, g0 + 3):
            rope(b, inside, x, SKY + 2, z)
    standing(b, cx, SKY + 1, g0 + 2, st.light)
    propeller(b, cx, cy - 2, L - 1, wood)
    b.set(cx, cy - 2, L - 2, f"{wood}_fence", **NOWATER) if not inside(cx, cy - 2, L - 2) else None
    return done(b)


# ── Cargo airship ─────────────────────────────────────────────────────────

CARGO = (("coal", 32), ("wheat", 24), ("iron_ingot", 6), ("bread", 12), ("string", 16), ("leather", 8),
         ("potato", 20), ("copper_ingot", 12), ("paper", 16), ("wool", 10))


@gen("cargo_airship", kind="sky_feature", freq="common", n=3)
def cargo_airship(rng, st):
    L = rng.randint(21, 24)
    W, cx = 13, 6
    b = Build("Cargo Airship", "sky_feature", W, L)
    wood = pick(rng, st.woods)
    acc = pick(rng, ACCENTS)
    cy = SKY + 18
    R = 4.4
    inside = envelope(b, cx, cy, 0, L - 1, R + 0.2, R,
                      lambda x, y, z: acc if (z + 1) % 5 == 0 else WOOL, nose=0.4, pf=2.6, pb=2.0)
    fins(b, inside, cx, cy, L - 1, 5, 6, acc)
    ky = cy - 5
    b.fill(cx, ky, 2, cx, ky, L - 4, f"{wood}_log", axis="z")
    # the pilot's cab, forward
    cabin(b, cx - 1, 2, cx + 1, 6, ky - 4, 2, f"{wood}_planks", f"{wood}_planks", "glass_pane",
          corner=f"{wood}_log", every=1)
    b.fill(cx, ky - 1, 3, cx, ky - 1, 5, f"{wood}_fence", **NOWATER)
    hanging(b, cx, ky - 2, 4, st.light)
    # pallets slung on chains
    z = 8
    while z + 3 <= L - 4:
        py = SKY + rng.randint(0, 4)
        b.fill(cx - 2, ky, z + 1, cx + 2, ky, z + 1, f"{wood}_log", axis="x")
        b.fill(cx - 1, py, z, cx + 1, py, z + 2, f"{wood}_slab", type="bottom", **NOWATER)
        hgt = rng.randint(1, 2)
        for dz in range(3):
            for dx in (-1, 0, 1):
                for dy in range(1, hgt + 1):
                    if rng.random() < 0.85 or dy == 1:
                        if rng.random() < 0.25:
                            b.set(cx + dx, py + dy, z + dz, "hay_block", axis="y")
                        else:
                            b.barrel(cx + dx, py + dy, z + dz, [pick(rng, CARGO)] if rng.random() < 0.3 else ())
        for dx in (-2, 2):
            chain(b, cx + dx, py + 1, ky - 1, z + 1)
            b.set(cx + dx, py, z + 1, f"{wood}_slab", type="bottom", **NOWATER)
        z += 4
    for z in (2, L - 4):
        rope(b, inside, cx, ky + 1, z)
    for s in (-1, 1):
        engine(b, cx + s * 4, ky + 1, L - 6, wood)
        b.fill(cx + s, ky + 1, L - 6, cx + s * 3, ky + 1, L - 6, f"{wood}_fence", **NOWATER)
    return done(b)


# ── Passenger liner ───────────────────────────────────────────────────────

@gen("liner", kind="sky_feature", freq="rare", n=3)
def liner(rng, st):
    L = 25
    W, cx = 17, 8
    b = Build("Passenger Liner", "sky_feature", W, L)
    wood = pick(rng, st.woods)
    trim = pick(rng, st.woods)
    acc = pick(rng, ("white_wool", "light_gray_wool"))
    cy = SKY + 16
    R = 5.4

    def paint(x, y, z):
        if z <= 1 or abs(y - cy) == 0 and z % 2 == 0:
            return acc
        return WOOL

    inside = envelope(b, cx, cy, 0, L - 1, R + 0.3, R, paint, nose=0.4, pf=2.6, pb=2.0)
    fins(b, inside, cx, cy, L - 1, 6, 7, acc)
    # the long gondola: bridge forward, cabins, a lounge aft and a balcony
    g0, g1 = 4, L - 7
    gy = SKY + 2
    wall = pick(rng, ("white_terracotta", "smooth_quartz", f"{wood}_planks"))
    cabin(b, cx - 3, g0, cx + 3, g1, gy, 3, wall, f"{trim}_planks", "glass_pane",
          corner=f"stripped_{trim}_log", every=2)
    b.fill(cx - 2, gy - 1, g0, cx + 2, gy - 1, g1, f"{trim}_planks")
    b.fill(cx - 1, gy - 2, g0 + 1, cx + 1, gy - 2, g1 - 1, f"{trim}_planks")
    for x in range(cx - 2, cx + 3):
        for y in (gy + 2, gy + 3):
            b.set(x, y, g0, "glass_pane", **NOWATER)
    b.set(cx - 1, gy + 1, g0 + 1, "lectern", facing="south", has_book="false", powered="false")
    b.set(cx + 1, gy + 1, g0 + 1, f"{trim}_stairs", facing="north", half="bottom", shape="straight", **NOWATER)
    # sleeping compartments either side of an aisle
    beds = pick(rng, st.beds)
    z = g0 + 3
    while z + 3 <= g1 - 3:
        for s in (-1, 1):
            b.fill(cx + s, gy + 1, z, cx + 3 * s, gy + 3, z, wall)
            b.bed(cx + 2 * s, gy + 1, z + 1, "south", beds)
        z += 3
    for s in (-1, 1):
        b.fill(cx + s, gy + 1, z, cx + 3 * s, gy + 3, z, wall)
    # aft lounge
    for s in (-1, 1):
        b.set(cx + 2 * s, gy + 1, g1 - 1, f"{trim}_stairs", facing="west" if s < 0 else "east",
              half="bottom", shape="straight", **NOWATER)
    b.set(cx, gy + 1, g1 - 2, f"{trim}_fence", **NOWATER)
    b.set(cx, gy + 2, g1 - 2, f"{trim}_pressure_plate", powered="false")
    if rng.random() < 0.5:
        b.chest(cx + 2, gy + 1, z + 1, "west", TREASURE)
    for zz in range(g0 + 2, g1, 3):
        hanging(b, cx, gy + 3, zz, st.light)
    b.door(cx, gy + 1, g1, "south", trim)
    b.fill(cx - 2, gy, g1 + 1, cx + 2, gy, g1 + 2, f"{trim}_planks")
    for x in range(cx - 2, cx + 3):
        b.set(x, gy + 1, g1 + 2, f"{trim}_fence", **NOWATER)
    for z in (g1 + 1, g1 + 2):
        b.set(cx - 2, gy + 1, z, f"{trim}_fence", **NOWATER)
        b.set(cx + 2, gy + 1, z, f"{trim}_fence", **NOWATER)
    # struts, and a rail along the roof
    for z in range(g0, g1 + 1, 3):
        for x in (cx - 3, cx + 3):
            rope(b, inside, x, gy + 5, z)
    for s in (-1, 1):
        engine(b, cx + s * 7, cy - 1, L - 7, trim)
        for x in range(cx + s * 5, cx + s * 7, s):
            if not inside(x, cy - 1, L - 7):
                b.set(x, cy - 1, L - 7, f"{trim}_fence", **NOWATER)
    return done(b)


# ── Observation dirigible ─────────────────────────────────────────────────

@gen("observation_dirigible", kind="sky_feature", freq="average", n=3)
def observation_dirigible(rng, st):
    L = rng.randint(19, 22)
    W, cx = 15, 7
    b = Build("Observation Dirigible", "sky_feature", W, L)
    wood = pick(rng, st.woods)
    acc = pick(rng, ACCENTS)
    cy = SKY + 14
    inside = envelope(b, cx, cy, 0, L - 1, 4.8, 4.5,
                      lambda x, y, z: acc if z <= 1 or y == cy - 1 or y == cy + 2 else WOOL,
                      nose=0.45, pf=2.4, pb=2.0)
    fins(b, inside, cx, cy, L - 1, 5, 6, acc, dirs="ulr")
    # the round deck, a bowl underneath with a glass floor at the bottom
    cz = L // 2 - 1
    dy = SKY + 3
    rx, rz = 5.5, 6.5
    ell = lambda x, z, k=1.0: ((x - cx) / (rx * k)) ** 2 + ((z - cz) / (rz * k)) ** 2 <= 1
    for z in range(L):
        for x in range(W):
            if ell(x, z):
                b.set(x, dy, z, f"{wood}_planks")
            for k, inner, y in ((0.78, 0.52, dy - 1), (0.52, 0.28, dy - 2)):
                if ell(x, z, k) and not ell(x, z, inner):
                    b.set(x, y, z, f"{wood}_planks")
            if ell(x, z, 0.28):
                b.set(x, dy - 3, z, "glass" if ell(x, z, 0.15) else f"{wood}_planks")
    edge = [(x, z) for z in range(L) for x in range(W) if ell(x, z) and not all(
        ell(x + a, z + c) for a, c in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, z in edge:
        b.set(x, dy + 1, z, f"{wood}_fence", **NOWATER)
    # a hatch down into the bowl
    b.set(cx, dy, cz, f"{wood}_trapdoor", facing="north", half="top", open="false", powered="false", **NOWATER)
    hanging(b, cx, dy - 1, cz + 1, st.light)
    b.set(cx, dy - 1, cz + 1, "air")
    # lantern posts round the rail, chains up to the envelope
    posts = [(x, z) for x, z in edge if (x + 2 * z) % 4 == 0]
    for x, z in posts:
        b.set(x, dy + 2, z, f"{wood}_fence", **NOWATER)
        standing(b, x, dy + 3, z, st.light)
    for x, z in ((cx - 4, cz - 3), (cx + 4, cz - 3), (cx - 4, cz + 3), (cx + 4, cz + 3)):
        b.fill(x, dy + 1, z, x, dy + 2, z, f"{wood}_log", axis="y")
        rope(b, inside, x, dy + 3, z)
    # benches looking out, a telescope and a chart table
    for z in (cz - 2, cz + 2):
        b.set(cx - 3, dy + 1, z, f"{wood}_stairs", facing="east", half="bottom", shape="straight", **NOWATER)
        b.set(cx + 3, dy + 1, z, f"{wood}_stairs", facing="west", half="bottom", shape="straight", **NOWATER)
    b.set(cx, dy + 1, cz - 4, f"{wood}_fence", **NOWATER)
    b.set(cx, dy + 2, cz - 4, "lightning_rod", facing="north", powered="false", **NOWATER)
    if st.key == "ashen":
        b.set(cx, dy + 1, cz + 3, "lectern", facing="north", has_book="false", powered="false")
    else:
        b.set(cx, dy + 1, cz + 3, "cartography_table")
    chain(b, cx, dy + 4, underside(inside, cx, cy, cz) - 1, cz)
    hanging(b, cx, dy + 3, cz, st.light)
    propeller(b, cx, cy - 1, L - 1, wood) if not inside(cx, cy - 1, L - 1) else None
    return done(b)


# ── Derelict ──────────────────────────────────────────────────────────────

@gen("derelict_airship", kind="sky_feature", freq="rare", n=3)
def derelict(rng, st):
    L = rng.randint(19, 22)
    W, cx = 13, 6
    b = Build("Derelict Airship", "sky_feature", W, L)
    wood = pick(rng, ("dark_oak", "spruce", pick(rng, st.woods)))
    deck = SKY + 3
    z0, z1 = 1, L - 2
    holes = {(rng.randrange(W), rng.randrange(z0, z1 + 1)) for _ in range(10)}
    keep = lambda x, y, z: not ((x, z) in holes and y < deck) and not ((x + 1, z) in holes and y == deck)
    widths = hull(b, cx, z0, z1, deck, 3, 3, wood, keep=keep,
                  side=lambda dy: pick(rng, (f"{wood}_planks", f"{wood}_planks", f"stripped_{wood}_log[axis=z]"
                                             if wood != "pale_oak" else "pale_oak_planks")))
    # broken rail
    for z in range(z0, z1 + 1):
        for x in range(W):
            if b.get(x, deck + 1, z).endswith("_fence") and rng.random() < 0.3:
                b.set(x, deck + 1, z, "air")
    # a mast snapped off, its top lying across the deck
    mz = z0 + (z1 - z0) // 3
    b.fill(cx, deck + 1, mz, cx, deck + 3, mz, f"{wood}_log", axis="y")
    for i in range(5):
        b.set(cx - 1 + i // 2, deck + 1, mz + 2 + i, f"{wood}_log", axis="z")
    torn = ("white_wool", "light_gray_wool", None, "cobweb")
    for y in (deck + 2, deck + 3):
        for x in range(cx - 2, cx + 3):
            if x != cx and rng.random() < 0.6:
                b.set(x, y, mz - 1, pick(rng, torn) or "air")
    # the second mast still standing, its sail in rags
    m2 = z0 + 2 * (z1 - z0) // 3
    b.fill(cx, deck + 1, m2, cx, deck + 8, m2, f"{wood}_log", axis="y")
    for y in range(deck + 4, deck + 8):
        for x in range(cx - 2, cx + 3):
            if x != cx and rng.random() < 0.5 + 0.1 * (y - deck - 4):
                s = pick(rng, torn)
                if s:
                    b.set(x, y, m2 - 1, s)
    # the envelope sags and goes slack towards the tail, torn and patched
    cy = deck + 13

    def paint(x, y, z):
        r = rng.random()
        if r < 0.12:
            return "cobweb" if rng.random() < 0.3 else None
        if r < 0.3:
            return pick(rng, ("gray_wool", "light_gray_wool", "brown_wool"))
        return WOOL

    inside = envelope(b, cx, cy, 0, L - 1, 5.2, 4.0, paint, nose=0.5, pf=2.4, pb=2.4,
                      sag=lambda t: (-round(6 * t ** 1.5), 1 - 0.6 * t))
    for z, x in ((z0 + 3, cx - widths[z0 + 3]), (z1 - 2, cx + widths[z1 - 2])):
        rope(b, inside, x, deck + 2, z)
    # chains that let go
    for z, x in ((z0 + 4, cx + 3), (z1 - 4, cx - 3)):
        y = underside(inside, x, cy, z) - 1
        if y > deck + 2:
            chain(b, x, y - rng.randint(1, 3), y, z)
    # cobwebs in the corners, a hold with something left in it
    for _ in range(14):
        x, z = rng.randrange(W), rng.randrange(z0, z1 + 1)
        for y in (deck + 1, deck + 2, deck - 1):
            if b.empty(x, y, z) and not b.empty(x, y - 1, z):
                b.set(x, y, z, "cobweb")
                break
    b.chest(cx, deck - 2, (z0 + z1) // 2, "north", TREASURE)
    b.set(cx, deck, (z0 + z1) // 2 + 1, "air")
    hanging(b, cx + 1, deck + 3, z1 - 1, "soul_lantern")
    b.set(cx + 1, deck + 4, z1 - 1, f"{wood}_fence", **NOWATER) if b.get(cx + 1, deck + 3, z1 - 1) != "air" else None
    return done(b)


# ── A cluster of tethered balloons ────────────────────────────────────────

PARTY = ("white_wool", "yellow_wool", "light_blue_wool", "pink_wool", "lime_wool", "orange_wool", "magenta_wool")


@gen("balloon_cluster", kind="sky_feature", freq="very common", n=3)
def balloon_cluster(rng, st):
    S = 23
    c = S // 2
    b = Build("Balloon Cluster", "sky_feature", S, S)
    wood = pick(rng, st.woods)
    # the raft
    b.fill(c - 1, SKY, c - 1, c + 1, SKY, c + 1, f"{wood}_planks")
    b.ring(c - 1, c - 1, c + 1, c + 1, SKY + 1, SKY + 1, f"{wood}_fence", **NOWATER)
    b.set(c, SKY + 1, c, "barrel", facing="up", open="false")
    standing(b, c - 1, SKY + 2, c - 1, st.light)
    knot = SKY + 6
    chain(b, c, SKY + 2, knot, c)
    n = rng.randint(4, 5)
    a0 = rng.random() * math.tau
    colours = [WOOL] + [pick(rng, PARTY) for _ in range(n)]
    balloons = [(c, c, rng.randint(SKY + 19, SKY + 21), 3)]
    for i in range(n):
        a = a0 + i * math.tau / n + rng.uniform(-0.2, 0.2)
        d = rng.uniform(6.5, 7.5)
        r = rng.choice((2, 3, 3))
        y = SKY + 11 + 5 * (i % 2) + rng.randint(0, 2)
        balloons.append((round(c + d * math.cos(a)), round(c + d * math.sin(a)), y, r))
    for (x, z, y, r), col in zip(balloons, colours):
        ry = r + 0.8
        inside = lambda xx, yy, zz, x=x, y=y, z=z, r=r, ry=ry: \
            ((xx - x) / (r + 0.3)) ** 2 + ((yy - y) / ry) ** 2 + ((zz - z) / (r + 0.3)) ** 2 <= 1
        body(b, (x - r - 1, y - r - 2, z - r - 1, x + r + 1, y + r + 2, z + r + 1), inside, lambda *_, col=col: col)
        bot = underside(inside, x, y, z)
        b.set(x, bot - 1, z, col)
        # the string: across from the knot, then up
        if (x, z) != (c, c):
            sx = 1 if x > c else -1
            for xx in range(c, x, sx):
                b.set(xx, knot + 1, c, f"{wood}_fence", **NOWATER)
            sz = 1 if z > c else -1
            for zz in range(c, z + sz, sz) if z != c else ():
                b.set(x, knot + 1, zz, f"{wood}_fence", **NOWATER)
            b.set(x, knot + 1, c, f"{wood}_fence", **NOWATER)
        chain(b, x, knot + 2 if (x, z) != (c, c) else knot + 1, bot - 2, z)
    return done(b)


# ── Floating island ───────────────────────────────────────────────────────

@gen("sky_island", kind="sky_feature", freq="rare", n=3)
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

@gen("glider", kind="sky_feature", freq="common", n=3)
def glider(rng, st):
    L = rng.randint(11, 13)
    W = 2 * (L + 1) + 1 if L < 11 else 23
    cx = W // 2
    b = Build("Glider", "sky_feature", W, L + 2)
    wood = pick(rng, st.woods)
    acc = pick(rng, ("white_wool", "black_wool", "yellow_wool", "white_wool"))
    wy = SKY + 4
    span = (W // 2) / (L - 1)
    for z in range(L):
        h = round(z * span)
        for dx in range(-h, h + 1):
            # the trailing edge notched in the middle
            if z == L - 1 and abs(dx) < 3:
                continue
            y = wy + (1 if abs(dx) > h * 0.55 + 2 else 0)
            col = acc if (z + abs(dx)) % 5 == 0 or abs(dx) == h else WOOL
            b.set(cx + dx, y, z, col)
    b.fill(cx, wy, 0, cx, wy, L - 1, f"stripped_{wood}_log" if wood != "pale_oak" else "pale_oak_log", axis="z")
    b.set(cx, wy + 1, L - 2, acc)
    b.set(cx, wy + 2, L - 1, acc)
    b.set(cx, wy + 1, L - 1, acc)
    # the control frame and the pilot's seat
    mz = L // 2 + 1
    b.fill(cx, SKY + 1, mz, cx, wy - 1, mz, f"{wood}_fence", **NOWATER)
    b.fill(cx - 2, SKY + 1, mz, cx + 2, SKY + 1, mz, f"{wood}_fence", **NOWATER)
    b.set(cx, SKY, mz, f"{wood}_trapdoor", facing="north", half="top", open="false", powered="false", **NOWATER)
    b.set(cx, SKY, mz + 1, f"{wood}_stairs", facing="south", half="top", shape="straight", **NOWATER)
    b.set(cx, SKY + 1, mz + 1, "air")
    hanging(b, cx, wy - 1, 2, st.light)
    # a streamer tail
    for i in range(1, 3):
        b.set(cx, wy - 1 + (i % 2), L - 1 + i, "iron_chain", axis="z", **NOWATER)
    return done(b)


# ── The ashen black airship ───────────────────────────────────────────────

@gen("black_airship", kind="sky_feature", freq="average", n=3, styles=("ashen",))
def black_airship(rng, st):
    L = rng.randint(21, 24)
    W, cx = 15, 7
    b = Build("Black Airship", "sky_feature", W, L)
    wood = "dark_oak"
    deck = SKY + 3
    z0, z1 = 2, L - 2
    widths = hull(b, cx, z0, z1, deck, 3, 3, wood, "dark_oak",
                  side=lambda dy: "polished_blackstone_bricks" if dy == 1 else "deepslate_tiles")
    for x in range(W):
        for z in range(L):
            if b.get(x, deck + 1, z) == "dark_oak_fence" and (x + z) % 3 == 0:
                b.set(x, deck + 1, z, "polished_blackstone_wall")
    # a ram and spikes
    b.fill(cx, deck - 1, z0 - 2, cx, deck - 1, z0 - 1, "polished_blackstone_wall")
    b.set(cx, deck, z0 - 1, "polished_blackstone_brick_stairs", facing="south", half="bottom", **NOWATER)
    # the bridge: a black tower aft
    c0 = z1 - 6
    cabin(b, cx - 2, c0, cx + 2, z1 - 1, deck, 3, "polished_blackstone_bricks", "polished_blackstone_brick_slab[type=bottom,waterlogged=false]",
          "black_stained_glass_pane", corner="deepslate_tiles", every=2, floor="dark_oak_planks")
    b.door(cx, deck + 1, c0, "north", material="dark_oak_door")
    b.chest(cx - 1, deck + 1, z1 - 2, "east", TREASURE)
    hanging(b, cx, deck + 3, z1 - 3, "soul_lantern")
    b.set(cx + 1, deck + 1, z1 - 2, "lectern", facing="west", has_book="false", powered="false")
    # braziers on deck
    for z in (z0 + 3, c0 - 3):
        b.set(cx, deck + 1, z, "soul_campfire", facing="north", lit="true", signal_fire="false", **NOWATER)
    # the envelope: black, ribbed grey, the city's colour round its middle
    cy = deck + 13

    def paint(x, y, z):
        if y == cy or y == cy + 1:
            return WOOL
        if z % 3 == 0:
            return "gray_wool"
        return "black_wool"

    inside = envelope(b, cx, cy, 0, L - 1, 5.3, 4.6, paint, nose=0.35, pf=1.8, pb=1.6)
    fins(b, inside, cx, cy, L - 1, 6, 7, "black_wool", jag=True)
    # soul lanterns hanging from the envelope along the sides, and the rigging
    for z in range(4, L - 4, 4):
        for x in (cx - 4, cx + 4):
            y = underside(inside, x, cy, z) - 1
            if y - 2 > deck + 2:
                chain(b, x, y - 1, y, z)
                hanging(b, x, y - 2, z, "soul_lantern")
    for z in (z0 + 3, (z0 + z1) // 2, z1 - 1):
        for x in (cx - widths[z], cx + widths[z]):
            rope(b, inside, x, deck + 2, z)
    for s in (-1, 1):
        engine(b, cx + s * 6, cy - 2, L - 7, wood)
    return done(b)


# ── The ochre canvas barge ────────────────────────────────────────────────

@gen("canvas_barge", kind="sky_feature", freq="average", n=3, styles=("ochre",))
def canvas_barge(rng, st):
    L = rng.randint(19, 22)
    W, cx = 13, 6
    b = Build("Canvas Barge", "sky_feature", W, L)
    wood = pick(rng, ("acacia", "jungle"))
    deck = SKY + 2
    z0, z1 = 1, L - 2
    hull(b, cx, z0, z1, deck, 4, 2, wood, side=lambda dy: "terracotta" if dy == 1 else f"{wood}_planks")
    # an awning of striped canvas on poles over the middle of the deck
    a0, a1 = z0 + 5, z1 - 4
    for z in (a0, a1):
        for x in (cx - 3, cx + 3):
            b.fill(x, deck + 1, z, x, deck + 3, z, f"{wood}_fence", **NOWATER)
    for z in range(a0, a1 + 1):
        for x in range(cx - 3, cx + 4):
            b.set(x, deck + 4, z, "orange_wool" if (x - cx) % 2 == 0 else "white_wool")
    for x in range(cx - 3, cx + 4):
        b.set(x, deck + 5, (a0 + a1) // 2, "orange_wool" if (x - cx) % 2 == 0 else "white_wool") if abs(x - cx) < 3 else None
    # rugs, cushions and cargo underneath
    rugs = ("red_carpet", "orange_carpet", "yellow_carpet")
    for z in range(a0 + 1, a1):
        for x in range(cx - 2, cx + 3):
            b.set(x, deck + 1, z, rugs[(abs(x - cx) + z) % 3 if (x - cx) % 2 else 0])
    for z in (a0 + 1, a1 - 1):
        b.barrel(cx - 2, deck + 1, z, [pick(rng, CARGO)] if rng.random() < 0.5 else ())
        b.set(cx + 2, deck + 1, z, "potted_dead_bush")
    if rng.random() < 0.5:
        b.chest(cx + 2, deck + 1, (a0 + a1) // 2, "west", TREASURE)
    hanging(b, cx, deck + 3, (a0 + a1) // 2, st.light)
    # a helm forward
    b.set(cx, deck + 1, z0 + 3, f"{wood}_fence", **NOWATER)
    standing(b, cx, deck + 2, z0 + 3, st.light)
    b.set(cx - 1, deck + 1, z1 - 1, "decorated_pot", facing="north", cracked="false", **NOWATER)
    # the envelope in orange and terracotta panels with the city's colour round its waist
    cy = deck + 14

    def paint(x, y, z):
        if z <= 1 or z >= L - 2:
            return "terracotta"
        if abs(y - cy) <= 0 and z % 2:
            return WOOL
        return "orange_wool" if (z // 3) % 2 else "orange_terracotta"

    inside = envelope(b, cx, cy, 0, L - 1, 5.3, 4.5, paint, nose=0.5, pf=2.8, pb=2.8)
    for z in (z0 + 3, z1 - 2):
        for x in (cx - 3, cx + 3):
            rope(b, inside, x, deck + 2, z)
    for z in (a0, a1):
        for x in (cx - 3, cx + 3):
            rope(b, inside, x, deck + 5, z)
    propeller(b, cx, deck + 1, z1 + 1, wood)
    return done(b)


# ── A whale-shaped blimp ──────────────────────────────────────────────────

@gen("whale_blimp", kind="sky_feature", freq="average", n=3)
def whale_blimp(rng, st):
    """A blimp built as a whale: a blunt head, a pale belly, an eye and a mouth line, a harness
    of the city's colour, flukes for a tail and a spout of glass from its blowhole."""
    L = rng.randint(21, 23)
    W, cx = 15, 7
    b = Build("Whale Blimp", "sky_feature", W, L)
    wood = pick(rng, st.woods)
    back = pick(rng, ("gray_wool", "light_gray_wool", "cyan_wool", "blue_wool"))
    cy = SKY + 12
    ze = L - 5          # where the body ends, the flukes behind it
    harness = (ze // 3, 2 * ze // 3)

    def paint(x, y, z):
        if z in harness:
            return WOOL
        if y <= cy - 2:
            return "white_wool"
        return back

    inside = envelope(b, cx, cy, 0, ze, 4.8, 4.3, paint, nose=0.3, pf=3.4, pb=1.5)
    # the tail stock and the flukes, flat either side
    for z in range(ze, L):
        b.set(cx, cy, z, back)
    for k, (a, c) in enumerate(((1, 2), (2, 4), (3, 6), (5, 6))):
        z = ze + 1 + k
        if z >= L:
            break
        for d in range(a, c + 1):
            for s in (-1, 1):
                b.set(cx + s * d, cy, z, back if k < 3 else "white_wool")
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
    # the gondola slung under its middle
    g0, g1 = 6, 6 + rng.randint(5, 6)
    cabin(b, cx - 2, g0, cx + 2, g1, SKY, 3, f"{wood}_planks", f"{wood}_planks", "glass_pane",
          corner=f"{wood}_log" if wood != "pale_oak" else "stripped_pale_oak_log", every=2)
    b.door(cx, SKY + 1, g0, "south", wood)
    b.fill(cx - 1, SKY, g0 - 1, cx + 1, SKY, g0 - 1, f"{wood}_slab", type="top", **NOWATER)   # a step-out porch
    for x in (cx - 1, cx + 1):
        b.set(x, SKY + 1, g0 - 1, f"{wood}_fence", **NOWATER)
    hanging(b, cx, SKY + 3, (g0 + g1) // 2, st.light)
    b.set(cx - 1, SKY + 1, g1 - 1, "lectern", facing="north", has_book="false", powered="false")
    b.chest(cx + 1, SKY + 1, g1 - 1, "west", TREASURE) if rng.random() < 0.5 else b.barrel(cx + 1, SKY + 1, g1 - 1, [pick(rng, CARGO)])
    stair(b, cx + 1, SKY + 1, g0 + 1, f"{wood}_stairs", "east")
    for x in (cx - 2, cx + 2):
        for z in (g0, g1):
            rope(b, inside, x, SKY + 5, z)
    for s in (-1, 1):   # pectoral fins as engines
        engine(b, cx + s * 6, cy - 1, ze // 2 + 1, wood)
    return done(b)


def stair(b, x, y, z, name, facing, half="bottom"):
    b.set(x, y, z, name, facing=facing, half=half, **NOWATER)


# ── Sky lanterns ──────────────────────────────────────────────────────────

LANTERN_COLOURS = ("orange", "red", "yellow", "white", "pink", "orange")


def sky_lantern(b, x, y, z, colour, glow):
    """A paper lantern: a glowing body under a coloured paper top, a candle-cup under it."""
    b.set(x, y + 2, z, f"{colour}_wool")
    b.set(x, y + 1, z, glow)
    b.set(x, y, z, f"{colour}_stained_glass")


@gen("lantern_festival", kind="sky_feature", freq="average", n=3)
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


# ── A balloon race ────────────────────────────────────────────────────────

def small_balloon(b, rng, x, y, z, r, colour, wood, stripe):
    """A little hot-air balloon whose basket floor is at y, centred on (x, z)."""
    ry = r + 1
    cy = y + 4 + ry
    inside = lambda xx, yy, zz: ((xx - x) / (r + 0.4)) ** 2 + ((yy - cy) / (ry + 0.4)) ** 2 + ((zz - z) / (r + 0.4)) ** 2 <= 1 \
        and not (yy < cy - ry + 1 and abs(xx - x) + abs(zz - z) <= 0)
    body(b, (x - r - 1, cy - ry - 1, z - r - 1, x + r + 1, cy + ry + 1, z + r + 1), inside,
         lambda xx, yy, zz: stripe if (xx + zz) % 3 == 0 else colour)
    b.fill(x - 1, y, z - 1, x + 1, y, z + 1, f"{wood}_planks")
    b.ring(x - 1, z - 1, x + 1, z + 1, y + 1, y + 1, f"{wood}_fence", **NOWATER)
    b.set(x, y + 1, z, "campfire", facing="north", lit="true", signal_fire="false", **NOWATER)
    for xx, zz in ((x - 1, z - 1), (x + 1, z - 1), (x - 1, z + 1), (x + 1, z + 1)):
        yy = y + 2
        while yy < TOP and not inside(xx, yy + 1, zz) and b.empty(xx, yy, zz):
            b.set(xx, yy, zz, "iron_chain", axis="y", **NOWATER)
            yy += 1
    return cy + ry


@gen("balloon_race", kind="sky_feature", freq="common", n=3)
def balloon_race(rng, st):
    """Four or five little balloons strung out in a race, each its own colours, the leader lowest."""
    S = 23
    b = Build("Balloon Race", "sky_feature", S, S)
    wood = pick(rng, st.woods)
    n = rng.randint(4, 5)
    spots = [(4, 4), (18, 5), (11, 11), (5, 18), (17, 17)]
    rng.shuffle(spots)
    colours = [WOOL] + [pick(rng, PARTY) for _ in range(n - 1)]
    for i, ((x, z), col) in enumerate(zip(spots[:n], colours)):
        r = 3 if i == 0 else rng.choice((2, 3))
        y = SKY if i == 0 else rng.randint(SKY + 1, TOP - (2 * r + 7))
        stripe = pick(rng, ("white_wool", "black_wool", "yellow_wool", "red_wool"))
        small_balloon(b, rng, x, y, z, r, col, wood, stripe)
    return done(b)


# ── A lighthouse on a floating rock ───────────────────────────────────────

@gen("sky_lighthouse", kind="sky_feature", freq="rare", n=3)
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


def disc_cells(r):
    rr = (r + 0.45) ** 2
    return [(dx, dz) for dx in range(-r, r + 1) for dz in range(-r, r + 1) if dx * dx + dz * dz <= rr]


def circle_cells(r):
    inner = set(disc_cells(r - 1))
    return [c for c in disc_cells(r) if c not in inner]


# ── A tethered barrage balloon ────────────────────────────────────────────

@gen("barrage_balloon", kind="sky_feature", freq="common", n=3)
def barrage_balloon(rng, st):
    """A fat silver balloon with three fat fins, its cable hanging straight down with a lamp on
    the end, guy lines gathering to it from the belly."""
    L = rng.randint(17, 19)
    W, cx = 15, 7
    b = Build("Barrage Balloon", "sky_feature", W, L)
    silver = pick(rng, ("light_gray_wool", "white_wool", "gray_wool"))
    cy = TOP - 5
    inside = envelope(b, cx, cy, 0, L - 5, 4.6, 4.4, lambda x, y, z: WOOL if z in (2, 3) else silver,
                      nose=0.42, pf=2.2, pb=1.7)
    # three lobed fins: up and out to each side, swollen
    ze = L - 5
    for k in range(4):
        z = ze + k - 1
        for d in range(1, 5 - k // 2):
            for sx, sy in ((0, 1), (-1, -1), (1, -1)):
                x, y = cx + sx * d, cy + sy * d
                if b.inside(x, z) and not inside(x, y, z) and y <= TOP:
                    b.set(x, y, z, silver)
                    if sy == -1:
                        b.set(x, y + 1, z, silver) if not inside(x, y + 1, z) else None
    # the cable: straight down from the belly
    cz = (L - 5) // 2
    bot = underside(inside, cx, cy, cz)
    chain(b, cx, SKY + 1, bot - 1, cz)
    hanging(b, cx, SKY, cz, st.light)
    gy = bot - 3
    for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):   # guy lines down to the cable
        x, z = cx + dx, cz + dz
        y = underside(inside, x, cy, z) - 1
        while (x, z) != (cx, cz) and y > gy:
            b.set(x, y, z, "iron_chain", axis="y", **NOWATER)
            x += (cx > x) - (cx < x)
            z += (cz > z) - (cz < z)
            y -= 1
    b.set(cx, gy, cz, "iron_block")
    return done(b)


# ── A sky market ──────────────────────────────────────────────────────────

@gen("sky_market", kind="sky_feature", freq="rare", n=3)
def sky_market(rng, st):
    """A broad barge under a great envelope, its deck set out with stalls under striped awnings."""
    L = rng.randint(21, 23)
    W, cx = 15, 7
    b = Build("Sky Market", "sky_feature", W, L)
    wood = pick(rng, st.woods)
    deck = SKY + 2
    z0, z1 = 1, L - 2
    widths = hull(b, cx, z0, z1, deck, 5, 2, wood)
    goods = (("apple", 12), ("bread", 8), ("melon_slice", 16), ("cookie", 12), ("emerald", 2), ("string", 9),
             ("paper", 12), ("leather", 6))
    stalls = []
    for z in range(z0 + 4, z1 - 3, 4):
        for s in (-1, 1):
            if widths[z] >= 4 and widths[z + 2] >= 4:
                stalls.append((s, z))
    for s, z in stalls:
        xo = cx + s * (widths[z] - 1)
        xi = cx + s * (widths[z] - 3)
        for zz in (z, z + 2):
            b.fill(xi, deck + 1, zz, xi, deck + 2, zz, f"{wood}_fence", **NOWATER)
        for zz in range(z, z + 3):
            for x in range(min(xo, xi), max(xo, xi) + 1):
                b.set(x, deck + 3, zz, WOOL if zz % 2 else "white_wool")
            b.barrel(xo, deck + 1, zz, [pick(rng, goods)]) if zz != z + 1 else b.set(xo, deck + 1, zz, f"{wood}_slab", type="top", **NOWATER)
        g = pick(rng, ("cake", "melon", "pumpkin", "hay_block", "decorated_pot"))
        props = {"cake": {"bites": "0"}, "hay_block": {"axis": "y"}, "decorated_pot": {"facing": "north", "cracked": "false", "waterlogged": "false"}}.get(g, {})
        b.set(xo, deck + 2, z + 1, g, **props)
        hanging(b, xo - s, deck + 2, z + 1, st.light)
    # the middle aisle: a lamp post, a counting table
    b.set(cx, deck + 1, z0 + 2, f"{wood}_fence", **NOWATER)
    standing(b, cx, deck + 2, z0 + 2, st.light)
    b.chest(cx, deck + 1, z1 - 2, "north", TREASURE) if rng.random() < 0.4 else b.barrel(cx, deck + 1, z1 - 2, [pick(rng, goods)])
    cy = deck + 15

    def paint(x, y, z):
        if z <= 1 or z >= L - 2:
            return "white_wool"
        return WOOL if (x + z) % 4 < 2 else "white_wool"

    inside = envelope(b, cx, cy, 0, L - 1, 6.0, 4.5, paint, nose=0.45, pf=2.4, pb=2.2)
    for z in (z0 + 2, (z0 + z1) // 2, z1 - 2):
        for x in (cx - widths[z], cx + widths[z]):
            rope(b, inside, x, deck + 2, z)
    for zz in range(z0 + 4, z1 - 2, 4):
        rope(b, inside, cx, deck + 1, zz) if b.empty(cx, deck + 1, zz) else None
    propeller(b, cx, deck + 1, z1 + 1, wood)
    return done(b)


# ── The mail dirigible ────────────────────────────────────────────────────

@gen("mail_dirigible", kind="sky_feature", freq="average", n=3)
def mail_dirigible(rng, st):
    """A slim fast dirigible in the post's livery, its gondola full of mail sacks, a bag hung on a
    hook below for pick-ups on the fly."""
    L = rng.randint(19, 21)
    W, cx = 13, 6
    b = Build("Mail Dirigible", "sky_feature", W, L)
    wood = pick(rng, st.woods)
    cy = SKY + 13
    livery = pick(rng, ("red_wool", "red_wool", "blue_wool"))

    def paint(x, y, z):
        if z <= 1:
            return livery
        if abs(y - cy) <= 0:
            return livery
        return WOOL if (z // 4) % 2 else "white_wool"

    inside = envelope(b, cx, cy, 0, L - 1, 3.8, 3.6, paint, nose=0.38, pf=2.6, pb=1.7)
    fins(b, inside, cx, cy, L - 1, 5, 6, livery)
    g0, g1 = 5, 12
    gy = SKY + 3
    cabin(b, cx - 2, g0, cx + 2, g1, gy, 3, f"{wood}_planks", f"{wood}_planks", "glass_pane",
          corner=f"{wood}_log" if wood != "pale_oak" else "stripped_pale_oak_log", every=2)
    b.door(cx, gy + 1, g1, "south", wood)
    for z in range(g0 + 1, g1 - 1):
        b.barrel(cx - 1, gy + 1, z, [("paper", rng.randint(4, 32))], facing="east")
        if z % 2:
            b.barrel(cx - 1, gy + 2, z, [("paper", rng.randint(1, 9))], facing="east")
    b.set(cx + 1, gy + 1, g0 + 1, "lectern", facing="west", has_book="false", powered="false")
    b.set(cx + 1, gy + 1, g0 + 2, "cartography_table")
    hanging(b, cx, gy + 3, (g0 + g1) // 2, st.light)
    for x in (cx - 2, cx + 2):
        for z in (g0, g1):
            rope(b, inside, x, gy + 4, z)
    # the hook and its bag
    chain(b, cx, SKY + 1, gy - 1, g0 + 2)
    b.set(cx, SKY, g0 + 2, "brown_wool")
    b.sign(cx + 3, gy + 2, g0 + 2, "east", ["Air Mail~Next stop~[adjectives]~[roadtypes]", "", "", ""], wood)
    b.sign(cx - 3, gy + 2, g0 + 2, "west", ["Air Mail", "", "", ""], wood)
    b.fill(cx - 1, gy, g1 + 1, cx + 1, gy, g1 + 1, f"{wood}_slab", type="top", **NOWATER)   # the drop porch
    for x in (cx - 1, cx + 1):
        b.set(x, gy + 1, g1 + 1, f"{wood}_fence", **NOWATER)
    for s in (-1, 1):
        engine(b, cx + s * 5, cy - 1, L - 6, wood)
    return done(b)


# ── A wreck tangled in a balloon ──────────────────────────────────────────

@gen("tangled_wreck", kind="sky_feature", freq="rare", n=3)
def tangled_wreck(rng, st):
    """The bow of a broken ship hanging nose down from a stray hot-air balloon that caught it,
    rigging wound round the lot, the envelope sagging and torn."""
    S = 17
    c = S // 2
    b = Build("Tangled Wreck", "sky_feature", S, S)
    wood = pick(rng, ("dark_oak", "spruce", pick(rng, st.woods)))
    # the balloon, slumped to one side
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
                if rng.random() < 0.85:
                    b.set(c + dx, y, zz, f"{wood}_planks")
            b.set(c + dx, y, zz - 1, f"{wood}_planks") if abs(dx) == hw and hw > 0 else None
        if i == 2:
            b.set(c, y, zz, f"{wood}_log", axis="y")
    # the broken mast across it, a scrap of sail
    my = top - 3
    for k in range(-3, 4):
        b.set(c + k, my, c - 1, f"{wood}_log", axis="x")
    for k in range(-2, 3):
        if rng.random() < 0.7:
            b.set(c + k, my - 1, c - 2, pick(rng, ("white_wool", "light_gray_wool", "cobweb")))
    # the rigging: chains from the balloon's skirt down to the wreck
    for dx, dz in ((-3, -2), (3, -2), (-2, 3), (2, 2)):
        x, z = c + dx, c + dz
        y = underside(inside, x, cy, z) - 1
        while y > top - 2 and b.empty(x, y, z):
            b.set(x, y, z, "iron_chain", axis="y", **NOWATER)
            y -= 1
    for x in range(c - 2, c + 3):
        b.set(x, top + 1, c, "iron_chain", axis="x", **NOWATER)
    chain(b, c, top + 2, underside(inside, c, cy, c) - 1, c)
    # what's left aboard
    for _ in range(8):
        x, y, z = c + rng.randint(-2, 2), rng.randint(SKY, top), c + rng.randint(-1, 4)
        if b.empty(x, y, z):
            b.set(x, y, z, "cobweb")
    hy = top - 1
    b.set(c, hy + 1, c + 0, f"{wood}_planks") if b.empty(c, hy + 1, c) else None
    b.set(c - 1, top + 1, c + 1, f"{wood}_fence", **NOWATER)
    hanging(b, c - 1, top, c + 1, "soul_lantern")
    b.container(c + 1, top - 1, c, "chest", TREASURE, facing="north", type="single", waterlogged="false")
    b.set(c + 1, top - 2, c, f"{wood}_planks")
    return done(b)
