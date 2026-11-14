package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Blocks;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Blocks.Dir;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Frame;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Grid;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Rng;

/**
 * A control centre, after IronSource's control.shp: one-block walls and floors, a storey every
 * five blocks, built outward from the middle.
 * <ul>
 *   <li>the core: the control room, windowless, two or three storeys high in one; a wall of
 *       screens at its front, rows of consoles stepping up behind it a block a row, a window
 *       into it from the corridor behind on the second storey;</li>
 *   <li>stairwells beside the core, switchback flights of five steps a storey, one of them on up
 *       into a stair house on the roof;</li>
 *   <li>the main block round it all: a corridor ring, a ring of rooms with windows outside that
 *       (offices, meeting rooms, server rooms, records, a break room, toilets, now and then a
 *       conference room or a cafeteria), fluorescent lights every six blocks;</li>
 *   <li>wings off the left, right and back sides: a corridor with rooms both sides, opening on
 *       the main block's corridor through its ring of rooms.</li>
 * </ul>
 * Everything is still where it was left. The screens are mostly off.
 *
 * <p>The frame is {@link Grid}'s: the entrance faces north (-z) at the front's middle.
 */
final class ControlCentre {

    static final int STOREY = 5;
    private static final int SL = 6, VG = 3;
    private static final String FLOOR = "minecraft:smooth_stone";
    private static final String INNER = "minecraft:white_concrete";
    private static final String LIGHT = "minecraft:sea_lantern";
    private static final String KEEP = "minecraft:cave_air";

    private final Rng rng;
    final Grid g;
    private final String wall, roof;
    private final boolean glassWalls;
    private final int NM, M, R, CRX, CRY, CBY, CRS, MX, MY, SR, WWIN, OW;
    private final boolean tiered;
    /** The middle of the entrance, outside the front wall (frame coordinates). */
    final int entranceX, entranceZ;

    static Grid build(long seed) {
        return new ControlCentre(seed).g;
    }

    ControlCentre(long seed) {
        rng = new Rng(seed);
        Rng r = rng.sub("plan");
        NM = r.pick(2, 2, 1) + 3;                 // 3..5 storeys
        M = r.range(8, 10);
        R = M - 4;                                // the ring of rooms, deep, its door wall too
        CRX = r.range(12, 22);
        CRY = r.range(10, 16);
        CBY = Math.max(CRY, 9);
        CRS = Math.min(NM - 1, Math.max(2, Math.min(3, Math.round(Math.min(CRX, CRY) / (float) STOREY))));
        SR = r.chance(0.6) ? 6 : 0;
        MX = CRX + SL + SR + 2 * VG + 2 * M;
        MY = CBY + 2 * M;
        WWIN = r.range(1, 2);
        OW = r.range(4, 6);
        tiered = CRY >= 10 && CRX >= 11;
        int wm = r.pick(3, 2, 2, 1, 1);
        wall = new String[]{ "minecraft:light_gray_concrete", "minecraft:bricks", "minecraft:white_concrete",
                "minecraft:stone_bricks", "minecraft:light_gray_concrete" }[wm];
        glassWalls = wm == 4;
        roof = "minecraft:gray_concrete";

        // Wings: rolled first, so the grid is big enough.
        Rng wr = rng.sub("wings");
        int[][] wings = new int[3][];   // per side W, E, S: {offset, length, storeys, rd}
        Dir[] sides = { Dir.WEST, Dir.EAST, Dir.SOUTH };
        int extraW = 0, extraE = 0, extraS = 0;
        for (int i = 0; i < 3; i++) {
            Rng s = wr.sub(i);
            if (!s.chance(0.85)) continue;
            int rd = s.range(4, 6), ww = 2 * rd + 5, len = s.range(14, 30);
            int span = sides[i] == Dir.SOUTH ? MX : MY;
            // the wing's corridor must open on the main block's corridor ring
            int lo = R + 1 - (rd + 1), hi = span - 2 - R - (rd + 3);
            lo = Math.max(lo, 0);
            hi = Math.min(hi, span - ww);
            if (hi < lo) continue;
            wings[i] = new int[]{ s.range(lo, hi), len, s.range(1, Math.min(NM, 3)), rd };
            if (i == 0) extraW = len + 1;
            if (i == 1) extraE = len + 1;
            if (i == 2) extraS = len + 1;
        }
        g = new Grid(-extraW - 2, -1, -5, MX + extraW + extraE + 4, NM * STOREY + 12, MY + extraS + 7);

        mainBlock();
        for (int i = 0; i < 3; i++) if (wings[i] != null) wing(sides[i], wings[i], wr.sub("wing", i));
        entranceX = MX / 2;
        entranceZ = -1;
        entrance();
    }

    // ── The main block ───────────────────────────────────────────────────

    private void mainBlock() {
        int top = NM * STOREY - 1;
        // outer walls, windows a storey at a time
        for (int y = -1; y <= top + 1; y++) {
            for (int x = 0; x < MX; x++) { g.set(x, y, 0, wall); g.set(x, y, MY - 1, wall); }
            for (int z = 0; z < MY; z++) { g.set(0, y, z, wall); g.set(MX - 1, y, z, wall); }
        }
        for (int k = 0; k < NM; k++) {
            int y0 = k * STOREY;
            windows(new Frame(1, 0, Dir.EAST, Dir.SOUTH), MX - 2, y0);
            windows(new Frame(MX - 2, MY - 1, Dir.WEST, Dir.NORTH), MX - 2, y0);
            windows(new Frame(0, MY - 2, Dir.NORTH, Dir.EAST), MY - 2, y0);
            windows(new Frame(MX - 1, 1, Dir.SOUTH, Dir.WEST), MY - 2, y0);
        }
        // the roof: a slab, a parapet, the stair house
        int xa = M, zc0 = M + (CBY - CRY) / 2;
        int xc0 = xa + SL + VG, xc1 = xc0 + CRX - 1, zc1 = zc0 + CRY - 1;
        g.fill(1, top, 1, MX - 2, top, MY - 2, roof);

        // floors first, so stairs can open them (the control room has none between its storeys)
        for (int k = 0; k < NM; k++) {
            for (int x = 1; x < MX - 1; x++) {
                for (int z = 1; z < MY - 1; z++) {
                    boolean inCore = x > xc0 && x < xc1 && z > zc0 && z < zc1;
                    if (k > 0 && k < CRS && inCore) continue;
                    g.set(x, k * STOREY - 1, z, FLOOR);
                }
            }
        }
        for (int k = 0; k < NM; k++) {
            int y0 = k * STOREY;
            Rng r = rng.sub("storey", k);
            // the rings of rooms: the side bands the whole length, front and back between them
            int span = MY - 2;
            band(new Frame(0, 1, Dir.SOUTH, Dir.EAST), span, R, y0, R, span - 1 - R, r.sub("west"));
            band(new Frame(MX - 1, MY - 2, Dir.NORTH, Dir.WEST), span, R, y0, R, span - 1 - R, r.sub("east"));
            int fl = MX - 2 - 2 * R;
            band(new Frame(1 + R, 0, Dir.EAST, Dir.SOUTH), fl, R, y0, 0, fl - 1, r.sub("front"));
            band(new Frame(MX - 2 - R, MY - 1, Dir.WEST, Dir.NORTH), fl, R, y0, 0, fl - 1, r.sub("back"));
            // the corridor ring and the halls beside the core: lights every six blocks
            for (int x = R + 1; x <= MX - 2 - R; x++) {
                for (int z = R + 1; z <= MY - 2 - R; z++) {
                    boolean core = x >= xa && x <= MX - 1 - M && z >= M && z <= M + CBY - 1;
                    boolean hall = (x >= xa + SL && x < xc0) || (x > xc1 && x <= xc1 + VG);
                    if (core && !hall) continue;
                    if (Math.floorMod(x - R - 2, 6) == 0 && Math.floorMod(z - R - 2, 6) == 0) g.set(x, y0 + 4, z, LIGHT);
                }
            }
            // stairwells
            stairwell(xa, M, k, false, k == NM - 1);
            if (SR > 0) stairwell(xc1 + VG + 1, M, k, true, false);
            // the core: the control room, or offices over it
            if (k == 0) controlRoom(xc0, zc0, xc1, zc1);
            else if (k >= CRS) openPlan(xc0, zc0, xc1, zc1, y0, r.sub("open"));
            if (k == 1) observation(xc0, xc1, zc1, y0);
            // the strips of core block in front of and behind a short control room
            for (int z = M; z <= M + CBY - 1; z++) {
                if (z >= zc0 && z <= zc1) continue;
                for (int x = xc0; x <= xc1; x++) {
                    if (Math.floorMod(x - xc0 - 2, 6) == 0 && Math.floorMod(z - M, 6) == 2) g.set(x, y0 + 4, z, LIGHT);
                }
            }
        }
        // rooftop: a few boxes of plant, an aerial
        Rng rr = rng.sub("rooftop");
        for (int i = 0, n = rr.range(2, 5); i < n; i++) {
            int w = rr.range(2, 4), d = rr.range(2, 4);
            int x = rr.range(2, MX - 3 - w), z = rr.range(2, MY - 3 - d);
            if (x < xa + SL + 2 && z < M + CBY + 2) continue;   // clear of the stair house
            g.fill(x, top + 1, z, x + w - 1, top + rr.range(1, 2), z + d - 1, "minecraft:smooth_stone");
        }
        int ax = rr.range(3, MX - 4), az = rr.range(3, MY - 4);
        for (int y = top + 1; y <= top + 9; y++) g.set(ax, y, az, Blocks.joined("iron_bars", false, false, false, false));
        // parapet
        for (int x = 0; x < MX; x++) { g.set(x, top + 1, 0, wall); g.set(x, top + 1, MY - 1, wall); }
        for (int z = 0; z < MY; z++) { g.set(0, top + 1, z, wall); g.set(MX - 1, top + 1, z, wall); }
    }

    /** One storey of windows along an outer wall: a window every WWIN + 2 (sill 1, 2 high), or glass all over. */
    private void windows(Frame f, int len, int y0) {
        if (glassWalls) {
            for (int u = 0; u < len; u++) {
                if (u % 4 == 3) continue;
                for (int y = y0; y < y0 + 4; y++) f.set(g, u, y, 0, "minecraft:glass");
            }
            return;
        }
        int bay = WWIN + 2;
        for (int u = 0; u + bay <= len; u += bay) {
            for (int i = 0; i < WWIN; i++) {
                f.set(g, u + 1 + i, y0 + 1, 0, "minecraft:glass");
                f.set(g, u + 1 + i, y0 + 2, 0, "minecraft:glass");
            }
        }
    }

    // ── Rooms ────────────────────────────────────────────────────────────

    /**
     * A row of rooms along an outer wall: u along it, v in from it (v = 0 the outer wall, the
     * door wall at v = depth). Rooms OW wide with walls between, now and then a conference room
     * or a cafeteria; each room's door in its door wall, within [lo, hi] along.
     */
    private void band(Frame f, int len, int depth, int y0, int lo, int hi, Rng r) {
        int u = 0, i = 0;
        while (u < len) {
            Rng rr = r.sub(i++);
            String kind;
            int w;
            int left = len - u;
            if (left >= 13 && rr.chance(0.25)) { kind = "conference"; w = rr.range(8, Math.min(14, left - 5)); }
            else if (left >= 12 && rr.chance(0.1)) { kind = "cafeteria"; w = rr.range(8, Math.min(14, left - 4)); }
            else {
                kind = new String[]{ "office", "meeting", "server", "records", "break", "toilets" }[rr.pick(6, 2, 1, 1, 1, 1)];
                w = OW;
            }
            if (u + w - 1 < lo + 1) w = lo + 2 - u;          // reach the corridor
            if (len - (u + w) < OW + 1) w = len - u;          // the last room takes what is left
            int door = Math.max(lo, Math.min(hi, u + w / 2));
            if (door > u + w - 1) door = u + w - 1;
            room(f, u, u + w - 1, depth, y0, door, kind, rr);
            u += w;
            if (u < len) {
                for (int y = y0; y < y0 + 4; y++) for (int v = 1; v <= depth; v++) f.set(g, u, y, v, INNER);
                u++;
            }
        }
    }

    private void room(Frame f, int a, int b, int depth, int y0, int door, String kind, Rng r) {
        // the door wall, a doorway 3 high
        for (int u = a; u <= b; u++) {
            for (int y = y0; y < y0 + 4; y++) f.set(g, u, y, depth, u == door && y < y0 + 3 ? KEEP : INNER);
        }
        f.set(g, door, y0, depth - 1, KEEP);
        int mid = (a + b) / 2, in = depth - 1;          // rooms run v = 1 .. depth - 1
        f.set(g, mid, y0 + 4, Math.max(1, depth / 2), LIGHT);
        Dir toWindow = f.across().opposite(), toDoor = f.across();
        switch (kind) {
            case "office" -> {
                int dv = in >= 4 ? 2 : 1;
                f.put(g, mid, y0, dv, "minecraft:smooth_stone_slab[type=top,waterlogged=false]");
                if (dv + 1 <= in) f.put(g, mid, y0, dv + 1, Blocks.stairs("spruce_stairs", toDoor, false));
                if (dv + 2 <= in) f.put(g, a, y0, dv + 2, Blocks.facing("barrel", f.along()) );
                carpet(f, a, b, in, y0, "gray");
            }
            case "meeting" -> {
                int dv = (in + 1) / 2;
                for (int u = a + 1; u <= b - 1; u++) {
                    f.put(g, u, y0, dv, "minecraft:spruce_slab[type=top,waterlogged=false]");
                    if (dv - 1 >= 1 && r.sub(u).chance(0.7)) f.put(g, u, y0, dv - 1, Blocks.stairs("spruce_stairs", toWindow, false));
                    if (dv + 1 <= in && r.sub(u).chance(0.7)) f.put(g, u, y0, dv + 1, Blocks.stairs("spruce_stairs", toDoor, false));
                }
                carpet(f, a, b, in, y0, "light_gray");
            }
            case "server" -> {
                for (int u = a; u <= b; u += 2) {
                    for (int v = 1; v <= in - 1; v++) {
                        f.put(g, u, y0, v, "minecraft:blast_furnace[facing=" + f.along().opposite().id() + ",lit=false]");
                        f.put(g, u, y0 + 1, v, "minecraft:blast_furnace[facing=" + f.along().opposite().id() + ",lit=false]");
                    }
                }
            }
            case "records" -> {
                for (int u = a; u <= b; u += 2) {
                    for (int v = 1; v <= in - 1; v++) {
                        f.put(g, u, y0, v, Blocks.facing("barrel", f.along().opposite()));
                        if (r.sub(u * 31 + v).chance(0.7)) f.put(g, u, y0 + 1, v, Blocks.facing("barrel", f.along().opposite()));
                    }
                }
            }
            case "break" -> {
                for (int v = 1; v <= in; v++) f.put(g, a, y0, v, v == in ? "minecraft:white_concrete" : "minecraft:polished_andesite");
                f.put(g, a, y0 + 1, in, "minecraft:white_concrete");
                int tv = Math.min(b, a + 2);
                f.put(g, tv, y0, (in + 1) / 2, Blocks.joined("spruce_fence", false, false, false, false));
                f.put(g, tv, y0 + 1, (in + 1) / 2, "minecraft:light_weighted_pressure_plate[power=0]");
                f.put(g, mid + 1 <= b ? mid + 1 : mid, y0 + 1, 1, "minecraft:black_concrete");
            }
            case "toilets" -> {
                for (int u = a; u <= b; u += 2) f.put(g, u, y0, 1, "minecraft:cauldron");
                for (int u = a; u <= b; u += 2) if (in >= 3) f.put(g, u, y0, in, "minecraft:smooth_quartz_slab[type=top,waterlogged=false]");
            }
            case "conference" -> {
                int dv = (in + 1) / 2;
                for (int u = a + 2; u <= b - 1; u++) {
                    f.put(g, u, y0, dv, "minecraft:dark_oak_slab[type=top,waterlogged=false]");
                    if (dv - 1 >= 1) f.put(g, u, y0, dv - 1, Blocks.stairs("spruce_stairs", toWindow, false));
                    if (dv + 1 <= in) f.put(g, u, y0, dv + 1, Blocks.stairs("spruce_stairs", toDoor, false));
                }
                f.put(g, a, y0, dv, "minecraft:lectern[facing=" + f.along().id() + ",has_book=false,powered=false]");
                for (int v = 1; v <= in; v++) f.put(g, b, y0 + 1, v, "minecraft:white_wool");
                carpet(f, a, b, in, y0, "blue");
            }
            case "cafeteria" -> {
                for (int v = 1; v <= in; v++) f.put(g, b, y0, v, "minecraft:polished_andesite");
                for (int u = a + 1; u <= b - 3; u += 3) {
                    for (int v = 2; v <= in - 1; v += 3) {
                        f.put(g, u, y0, v, Blocks.joined("spruce_fence", false, false, false, false));
                        f.put(g, u, y0 + 1, v, "minecraft:light_weighted_pressure_plate[power=0]");
                        if (r.sub(u * 7 + v).chance(0.6)) f.put(g, u + 1, y0, v, Blocks.stairs("spruce_stairs", f.along(), false));
                        if (r.sub(u * 7 + v + 3).chance(0.6)) f.put(g, u - 1, y0, v, Blocks.stairs("spruce_stairs", f.along().opposite(), false));
                    }
                }
            }
            default -> { }
        }
    }

    private void carpet(Frame f, int a, int b, int in, int y0, String color) {
        for (int u = a; u <= b; u++) for (int v = 1; v <= in; v++) f.put(g, u, y0, v, Blocks.id(color + "_carpet"));
    }

    // ── Stairs ───────────────────────────────────────────────────────────

    /**
     * A stairwell SL wide and CBY deep at (x0, z0): walls round it, a door on the side to its
     * hall, a walkway and two lanes; a flight of five steps a storey up one lane or back down the
     * other by turns, the floor above open over it. {@code roofHouse}: its last flight goes on
     * up into a stair house on the roof.
     */
    private void stairwell(int x0, int z0, int k, boolean mirror, boolean roofHouse) {
        int y0 = k * STOREY, d = CBY;
        int top = roofHouse ? y0 + 2 * STOREY - 1 : y0 + STOREY - 1;
        for (int y = y0; y <= top; y++) {
            for (int x = x0; x < x0 + SL; x++) { g.set(x, y, z0, INNER); g.set(x, y, z0 + d - 1, INNER); }
            for (int z = z0; z < z0 + d; z++) { g.set(x0, y, z, INNER); g.set(x0 + SL - 1, y, z, INNER); }
        }
        for (int yy = 0; yy < (roofHouse ? 2 : 1); yy++) {
            int yb = y0 + yy * STOREY;
            int doorX = mirror ? x0 : x0 + SL - 1;
            for (int y = yb; y < yb + 3; y++) g.set(doorX, y, z0 + 1, KEEP);
            for (int x = x0 + 1; x < x0 + SL - 1; x++)
                for (int z = z0 + 1; z < z0 + d - 1; z++)
                    for (int y = yb; y < yb + 4; y++) g.set(x, y, z, Blocks.AIR);
        }
        if (roofHouse) g.fill(x0, y0 + 2 * STOREY - 1, z0, x0 + SL - 1, y0 + 2 * STOREY - 1, z0 + d - 1, roof);
        // lanes: lane 0 rises +z, lane 1 back -z
        int laneX0 = mirror ? x0 + 1 : x0 + 3, laneX1 = mirror ? x0 + 2 : x0 + 4;
        int lane = k % 2, lx = lane == 0 ? laneX0 : laneX1;
        Dir rise = lane == 0 ? Dir.SOUTH : Dir.NORTH;
        for (int i = 0; i < STOREY; i++) {
            int z = lane == 0 ? z0 + 2 + i : z0 + 6 - i;
            int y = y0 + i;
            g.set(lx, y, z, Blocks.stairs("stone_brick_stairs", rise, false));
            for (int c = y + 1; c <= y + 2; c++) {
                if (c == y0 + STOREY - 1 || g.free(lx, c, z)) g.set(lx, c, z, KEEP);
            }
        }
        g.set((x0 + x0 + SL - 1) / 2, y0 + 4, z0 + d - 2, LIGHT);
    }

    // ── The core ─────────────────────────────────────────────────────────

    private void controlRoom(int x0, int z0, int x1, int z1) {
        int h = CRS * STOREY - 1;                      // its ceiling
        for (int y = 0; y < h; y++) {
            for (int x = x0; x <= x1; x++) { g.set(x, y, z0, INNER); g.set(x, y, z1, INNER); }
            for (int z = z0; z <= z1; z++) { g.set(x0, y, z, INNER); g.set(x1, y, z, INNER); }
            for (int x = x0 + 1; x < x1; x++) for (int z = z0 + 1; z < z1; z++) g.set(x, y, z, Blocks.AIR);
        }
        // its doors, out the sides near the front
        for (int y = 0; y < 3; y++) { g.set(x0, y, z0 + 2, KEEP); g.set(x1, y, z0 + 2, KEEP); }
        // the window from the observation corridor behind, on the second storey
        for (int x = x0 + 2; x <= x1 - 2; x++) for (int y = STOREY + 1; y <= STOREY + 3; y++) g.set(x, y, z1, "minecraft:glass");
        // lights in its ceiling
        for (int x = x0 + 3; x < x1; x += 6) for (int z = z0 + 3; z < z1; z += 6) g.set(x, h, z, LIGHT);
        // the screens along the front: monitors 3 x 2, most of them off
        Rng sr = rng.sub("screens");
        int sTop = h - 3;
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            for (int y = 0; y <= sTop; y++) {
                int mx = (x - x0 - 2) / 3, my = y / 2;
                Rng m = sr.sub(mx * 101 + my);
                boolean on = m.chance(0.18);
                boolean edge = (x - x0 - 2) % 3 == 2 || y % 2 == 1;
                String c = !on || edge || y == 0 ? "minecraft:black_concrete"
                        : new String[]{ "minecraft:light_blue_concrete", "minecraft:lime_concrete", "minecraft:white_concrete", "minecraft:cyan_concrete" }[m.range(0, 3)];
                g.set(x, y, z0 + 1, c);
            }
        }
        if (!tiered) {
            // too small for tiers: consoles along the sides and before the screens, facing in
            for (int z = z0 + 3; z < z1 - 1; z++) {
                g.put(x0 + 1, 0, z, Blocks.facing("observer", Dir.WEST) .replace("]", ",powered=false]"));
                g.put(x1 - 1, 0, z, Blocks.facing("observer", Dir.EAST).replace("]", ",powered=false]"));
            }
            for (int x = x0 + 2; x <= x1 - 2; x++) g.put(x, 0, z0 + 3, Blocks.facing("observer", Dir.NORTH).replace("]", ",powered=false]"));
            return;
        }
        // rows of consoles stepping up toward the back, aisles at the sides
        int din = CRY - 2, n = Math.max(1, (din - 7) / 2);
        int zr = z0 + 5, maxH = 2 * CRS - 2;
        for (int j = 0; j < n; j++, zr += 2) {
            int hj = Math.min(j, maxH);
            for (int z = zr; z <= zr + 1; z++)
                for (int x = x0 + 1; x < x1; x++)
                    for (int y = 0; y < hj; y++) g.set(x, y, z, "minecraft:polished_andesite");
            if (hj > 0) {
                for (int x : new int[]{ x0 + 1, x0 + 2, x1 - 2, x1 - 1 }) g.set(x, hj - 1, zr, Blocks.stairs("polished_andesite_stairs", Dir.SOUTH, false));
            }
            for (int x = x0 + 3; x <= x1 - 3; x++) {
                g.set(x, hj, zr, Blocks.facing("observer", Dir.SOUTH).replace("]", ",powered=false]"));
                boolean e = x < x1 - 3, w = x > x0 + 3;
                g.set(x, hj + 1, zr, Blocks.joined("black_stained_glass_pane", false, e, false, w).replace("]", ",waterlogged=false]"));
                g.set(x, hj, zr + 1, Blocks.stairs("spruce_stairs", Dir.SOUTH, false));
            }
        }
        // the back area level with the last row
        int hb = Math.min(n - 1, maxH);
        for (int z = zr; z < z1; z++)
            for (int x = x0 + 1; x < x1; x++)
                for (int y = 0; y < hb; y++) g.set(x, y, z, "minecraft:polished_andesite");
    }

    /** Behind the control room on its second storey: a row of seats facing the window. */
    private void observation(int x0, int x1, int z1, int y0) {
        for (int x = x0 + 2; x <= x1 - 2; x += 1) {
            if (Math.floorMod(x - x0 - 2, 4) == 3) continue;
            g.put(x, y0, z1 + 2, Blocks.stairs("spruce_stairs", Dir.SOUTH, false));
        }
    }

    /** Open-plan offices: a desk and its chair every 3 blocks, a walkway round. */
    private void openPlan(int x0, int z0, int x1, int z1, int y0, Rng r) {
        for (int x = x0 + 1; x <= x1 - 1; x++)
            for (int z = z0 + 1; z <= z1 - 1; z++)
                if (Math.floorMod(x - x0 - 3, 6) == 0 && Math.floorMod(z - z0 - 3, 6) == 0) g.set(x, y0 + 4, z, LIGHT);
        for (int x = x0 + 1; x + 1 <= x1 - 1; x += 3) {
            for (int z = z0 + 1; z + 2 <= z1 - 1; z += 3) {
                if (!r.sub(x * 131 + z).chance(0.85)) continue;
                g.put(x, y0, z, "minecraft:smooth_stone_slab[type=top,waterlogged=false]");
                g.put(x, y0, z + 1, Blocks.stairs("spruce_stairs", Dir.SOUTH, false));
            }
        }
    }

    // ── Wings ────────────────────────────────────────────────────────────

    /**
     * A wing off side {@code s}: a corridor with a row of rooms each side, out from the main
     * block's wall, its corridor through the main block's ring of rooms to its corridor ring.
     * {@code w}: offset along the side, length, storeys, rooms' depth.
     */
    private void wing(Dir s, int[] w, Rng r) {
        int off = w[0], len = w[1], ws = w[2], rd = w[3], ww = 2 * rd + 5;
        Dir across = s.cw();
        // the wing's frame: u out from the main block's wall (u = 0 is that wall), v across
        int ox, oz;
        switch (s) {
            case WEST -> { ox = 0; oz = MY - 1 - off; }     // across = NORTH
            case EAST -> { ox = MX - 1; oz = off; }        // across = SOUTH
            default -> { ox = MX - 1 - off; oz = MY - 1; }  // SOUTH, across = WEST
        }
        Frame f = new Frame(ox, oz, s, across);
        int top = ws * STOREY - 1;
        // shell: two long walls, the far end, floors, roof, parapet
        for (int y = -1; y <= top + 1; y++) {
            for (int u = 1; u <= len; u++) { f.set(g, u, y, 0, wall); f.set(g, u, y, ww - 1, wall); }
            for (int v = 0; v < ww; v++) f.set(g, len, y, v, wall);
        }
        for (int k = 0; k <= ws; k++) {
            int y = k * STOREY - 1;
            for (int u = 1; u < len; u++) for (int v = 1; v < ww - 1; v++) f.set(g, u, y, v, k == ws ? roof : FLOOR);
        }
        for (int k = 0; k < ws; k++) {
            int y0 = k * STOREY;
            Rng kr = r.sub(k);
            windows(new Frame(f.x(1, 0), f.z(1, 0), s, across.opposite()), len - 1, y0);
            windows(new Frame(f.x(len - 1, ww - 1), f.z(len - 1, ww - 1), s.opposite(), across), len - 1, y0);
            // rooms: each side a band along the wing (u = 1 .. len - 1)
            band(new Frame(f.x(1, 0), f.z(1, 0), s, across), len - 1, rd, y0, 0, len - 2, kr.sub("a"));
            band(new Frame(f.x(len - 1, ww - 1), f.z(len - 1, ww - 1), s.opposite(), across.opposite()), len - 1, rd, y0, 0, len - 2, kr.sub("b"));
            for (int u = 3; u < len; u += 6) f.set(g, u, y0 + 4, rd + 2, LIGHT);
            // through the main block: the corridor's three blocks, from the wall to the ring
            for (int t = 0; t <= R; t++) {
                for (int v = rd + 1; v <= rd + 3; v++) {
                    int x = f.x(-t, v), z = f.z(-t, v);
                    for (int y = y0; y < y0 + 4; y++) g.set(x, y, z, y < y0 + 3 ? KEEP : INNER);
                    g.set(x, y0 - 1, z, FLOOR);
                }
                for (int v : new int[]{ rd, rd + 4 }) {
                    if (t == 0) continue;
                    int x = f.x(-t, v), z = f.z(-t, v);
                    for (int y = y0; y < y0 + 4; y++) g.set(x, y, z, INNER);
                }
            }
            g.set(f.x(-R / 2, rd + 2), y0 + 4, f.z(-R / 2, rd + 2), LIGHT);
        }
        for (int u = 0; u <= len; u++) for (int v = 0; v < ww; v++) {
            if (u == 0 || u == len || v == 0 || v == ww - 1) f.set(g, u, top + 1, v, wall);
        }
        // the main block's wall closes the wing's near end above it
    }

    /** Through the front band at its middle: a passage three wide, a canopy outside. */
    private void entrance() {
        int x0 = MX / 2 - 1;
        for (int z = 0; z <= R; z++) {
            for (int x = x0 - 1; x <= x0 + 3; x++) {
                boolean side = x == x0 - 1 || x == x0 + 3;
                for (int y = 0; y < 4; y++) {
                    if (side) { if (z > 0) g.set(x, y, z, INNER); }
                    else g.set(x, y, z, y < 3 ? KEEP : (z == 0 ? wall : INNER));
                }
                if (!side) g.set(x, -1, z, FLOOR);
            }
        }
        g.set(x0 + 1, 4, R / 2 + 1, LIGHT);
        for (int z = -3; z <= -1; z++) for (int x = x0 - 1; x <= x0 + 3; x++) {
            g.set(x, 3, z, "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]");
            g.set(x, -1, z, FLOOR);
        }
        for (int x = x0 - 1; x <= x0 + 3; x += 4) for (int y = 0; y < 3; y++) g.set(x, y, -3, "minecraft:polished_andesite");
    }
}
