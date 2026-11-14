package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Blocks;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Blocks.Dir;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Grid;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Rng;

/**
 * Great radars and antennas after IronSource's radar.shp, voxelized: radomes ("golf balls") on
 * drums, a tracking dish tipped up on its pedestal, a phased-array pyramid, a Chain Home line of
 * lattice masts with wires slung between, and a great fixed dish on a forest of columns with its
 * feed hung over it from three towers. Plus an arched hangar, after military.shp's.
 *
 * <p>Each builds a {@link Grid} with its lot from (0, 0) and y = 0 the first block above ground.
 */
final class Radars {

    private static final String CONCRETE = "minecraft:light_gray_concrete";
    private static final String CONCRETE_DARK = "minecraft:stone";
    private static final String STEEL = "minecraft:polished_andesite";
    private static final String STEEL_DARK = "minecraft:cyan_terracotta";
    private static final String PAINT = "minecraft:white_concrete";

    private Radars() {}

    enum Kind { RADOME, DISH, ARRAY, CHAIN, BOWL, HANGAR }

    static Grid build(Kind kind, Rng r) {
        return switch (kind) {
            case RADOME -> radomes(r);
            case DISH -> dish(r);
            case ARRAY -> array(r);
            case CHAIN -> chain(r);
            case BOWL -> bowl(r);
            case HANGAR -> hangar(r);
        };
    }

    // ── Radomes ──────────────────────────────────────────────────────────

    /** One to four white balls in a row, each a little more than half above its concrete drum. */
    private static Grid radomes(Rng r) {
        int b = r.range(14, 28), n = r.range(1, 4), slot = Math.round(b * 1.3f);
        int drum = Math.round(b * 0.18f);
        double rad = b / 2.0;
        Grid g = new Grid(0, -1, -4, slot * n, drum + b + 2, slot + 4);
        for (int i = 0; i < n; i++) {
            double cx = i * slot + slot / 2.0, cz = slot / 2.0;
            double cy = drum + 0.35 * b;
            int x0 = i * slot, x1 = x0 + slot - 1;
            for (int x = x0; x <= x1; x++) {
                for (int z = 0; z < slot; z++) {
                    double dx = x + 0.5 - cx, dz = z + 0.5 - cz, dr = Math.sqrt(dx * dx + dz * dz);
                    if (dr <= rad * 0.92) {
                        for (int y = -1; y < drum; y++) g.set(x, y, z, y < 0 ? CONCRETE : (dr > rad * 0.92 - 1.2 ? CONCRETE : "minecraft:air"));
                        g.set(x, drum - 1, z, CONCRETE);
                    }
                    for (int y = drum; y < drum + b + 1; y++) {
                        double dy = y + 0.5 - cy, d = Math.sqrt(dr * dr + dy * dy);
                        if (d <= rad && d > rad - 1.3) {
                            // geodesic seams, roughly: lines of latitude and longitude
                            double lat = Math.asin(Math.max(-1, Math.min(1, dy / d))), lon = Math.atan2(dz, dx);
                            boolean seam = frac(lat * 7 / Math.PI) < 0.07 || frac(lon * 14 / (2 * Math.PI) + (Math.floor(lat * 7 / Math.PI) % 2) * 0.5) < 0.05;
                            g.set(x, y, z, seam ? "minecraft:light_gray_concrete" : PAINT);
                        }
                    }
                }
            }
            // a door into the drum, a box of plant beside it
            int dx = (int) cx;
            for (int y = 0; y < Math.min(2, drum - 1); y++) {
                for (int z = 0; z < slot / 2; z++) {
                    double ddz = z + 0.5 - cz;
                    if (Math.abs(ddz) > rad * 0.92 - 1.5 && Math.abs(ddz) <= rad * 0.92) g.set(dx, y, z, "minecraft:air");
                }
            }
            g.fill(dx - 2, -1, -4, dx + 1, 2, -1, CONCRETE_DARK);
        }
        return g;
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    // ── Tracking dish ────────────────────────────────────────────────────

    /** A dish on a yoke on an octagonal pedestal, tipped up and turned; four legs to its feed. */
    private static Grid dish(Rng r) {
        int d = r.range(20, 36);
        double elev = Math.toRadians(r.range(15.0, 80.0)), az = r.range(0.0, Math.PI * 2);
        int ped = Math.round(d * 0.22f), hub = ped + Math.round(d * 0.55f);
        int half = d / 2 + 3;
        Grid g = new Grid(-half, -1, -half, 2 * half + 1, hub + d + 4, 2 * half + 1);
        // pedestal (an octagon) and turntable
        double pr = d * 0.16;
        for (int x = -half; x <= half; x++) for (int z = -half; z <= half; z++) {
            if (octagon(x, z, pr)) for (int y = -1; y < ped; y++) g.set(x, y, z, CONCRETE);
            if (octagon(x, z, d * 0.33)) g.set(x, ped, z, STEEL);
        }
        // the dish's axis: up ELEV from the horizon, turned AZ
        double nx = Math.cos(elev) * Math.sin(az), ny = Math.sin(elev), nz = Math.cos(elev) * Math.cos(az);
        // the elevation axle, horizontal and square to the axis; the yoke's arms at its ends
        double ax = Math.cos(az), azz = -Math.sin(az);
        for (int side = -1; side <= 1; side += 2) {
            int bx = (int) Math.round(ax * d * 0.32 * side), bz = (int) Math.round(azz * d * 0.32 * side);
            for (int y = ped + 1; y <= hub; y++) { g.set(bx, y, bz, STEEL); g.set(bx + 1, y, bz, STEEL); g.set(bx, y, bz + 1, STEEL); }
        }
        line(g, -ax * d * 0.32, hub, -azz * d * 0.32, ax * d * 0.32, hub, azz * d * 0.32, STEEL);
        // the dish: a paraboloid, vertex 0.15 d out from the hub along the axis
        double vx = nx * d * 0.15, vy = hub + ny * d * 0.15, vz = nz * d * 0.15;
        double rim = d / 2.0, deep = d * 0.156, f = rim * rim / (4 * deep);
        for (int x = -half; x <= half; x++) for (int z = -half; z <= half; z++) for (int y = ped; y < hub + d + 3; y++) {
            double px = x + 0.5 - vx, py = y + 0.5 - vy, pz = z + 0.5 - vz;
            double h = px * nx + py * ny + pz * nz;
            double qx = px - h * nx, qy = py - h * ny, qz = pz - h * nz;
            double rr = Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (rr > rim) continue;
            double hs = rr * rr / (4 * f);
            double slope = rr / (2 * f);
            if (Math.abs(h - hs) <= 0.55 * Math.sqrt(1 + slope * slope)) g.set(x, y, z, PAINT);
        }
        // the quadripod and the feed at the focus
        double fx = vx + nx * f, fy = vy + ny * f, fz = vz + nz * f;
        // two unit vectors square to the axis: "up" across the dish, and the one square to both
        double ux = -ny * nx, uy = 1 - ny * ny, uz = -ny * nz, ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-6) { ux = 1; uy = 0; uz = 0; } else { ux /= ul; uy /= ul; uz /= ul; }
        double wx = ny * uz - nz * uy, wy = nz * ux - nx * uz, wz = nx * uy - ny * ux;
        for (int i = 0; i < 4; i++) {
            double t = i * Math.PI / 2, c = Math.cos(t), s = Math.sin(t);
            double rr = rim * 0.92, hh = rr * rr / (4 * f);
            double ex = vx + nx * hh + (ux * c + wx * s) * rr, ey = vy + ny * hh + (uy * c + wy * s) * rr, ez = vz + nz * hh + (uz * c + wz * s) * rr;
            line(g, ex, ey, ez, fx, fy, fz, STEEL);
        }
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++)
            g.set((int) Math.floor(fx) + x, (int) Math.floor(fy) + y, (int) Math.floor(fz) + z, PAINT);
        return g;
    }

    private static boolean octagon(int x, int z, double r) {
        double ax = Math.abs(x + 0.5 - 0.5), az = Math.abs(z + 0.5 - 0.5);
        return ax <= r && az <= r && ax + az <= r * 1.41;
    }

    /** Blocks along a line, a block thick. */
    private static void line(Grid g, double x0, double y0, double z0, double x1, double y1, double z1, String m) {
        double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        int steps = (int) Math.ceil(len * 2) + 1;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            g.set((int) Math.floor(x0 + (x1 - x0) * t), (int) Math.floor(y0 + (y1 - y0) * t), (int) Math.floor(z0 + (z1 - z0) * t), m);
        }
    }

    // ── Phased-array pyramid ─────────────────────────────────────────────

    /** A concrete block, one to three of its faces sloping back 2:1, an octagonal array on each. */
    private static Grid array(Rng r) {
        int a = r.range(20, 30), faces = r.range(1, 3), w = a / 2;
        Grid g = new Grid(-w, -1, -w, a + 2 * w, a + 2, a + 2 * w);
        g.fill(0, -1, 0, a - 1, a - 1, a - 1, CONCRETE);
        Dir[] order = { Dir.NORTH, Dir.EAST, Dir.WEST };
        for (int i = 0; i < faces; i++) {
            Dir out = order[i];
            for (int u = 0; u < a; u++) {
                for (int v = 1; v <= w; v++) {
                    int top = a - 2 * v;     // 2:1
                    int x, z;
                    switch (out) {
                        case NORTH -> { x = u; z = -v; }
                        case EAST -> { x = a - 1 + v; z = u; }
                        default -> { x = -v; z = u; }
                    }
                    for (int y = -1; y < top; y++) g.set(x, y, z, CONCRETE);
                    // the array: an octagon on the slope, its middle halfway up
                    double du = u + 0.5 - a / 2.0, dy = top - a / 2.0, rr = a * 0.36;
                    if (top >= 1 && Math.abs(du) <= rr && Math.abs(dy) <= rr && Math.abs(du) + Math.abs(dy) <= rr * 1.35) {
                        g.set(x, top - 1, z, "minecraft:gray_concrete");
                        if (v == 1) g.set(out == Dir.NORTH ? u : out == Dir.EAST ? a - 1 : 0, top, out == Dir.NORTH ? 0 : u, "minecraft:gray_concrete");
                    }
                }
            }
        }
        return g;
    }

    // ── Chain Home ───────────────────────────────────────────────────────

    /** Three or four lattice masts in a line on concrete bases, platforms up them, wires between. */
    private static Grid chain(Rng r) {
        int n = r.range(3, 4), h = r.range(60, 85), gap = 36;
        Grid g = new Grid(-4, -1, -4, (n - 1) * gap + 13, h + 4, 13);
        for (int i = 0; i < n; i++) {
            int x0 = i * gap;
            g.fill(x0 - 1, -1, -1, x0 + 5, 0, 5, CONCRETE);
            lattice(g, x0, 1, 0, 5, h, STEEL_DARK);
            for (int p : new int[]{ h / 3, 2 * h / 3, h - 1 }) {
                for (int x = x0 - 2; x <= x0 + 6; x++) for (int z = -2; z <= 6; z++) {
                    if (x >= x0 && x <= x0 + 4 && z >= 0 && z <= 4) continue;
                    g.set(x, p, z, "minecraft:smooth_stone_slab[type=top,waterlogged=false]");
                }
            }
            if (i < n - 1) {
                for (int level : new int[]{ h / 3, 2 * h / 3, h - 3 }) {
                    // slung wires: chains with a sag
                    for (int x = x0 + 5; x < x0 + gap; x++) {
                        double t = (x - x0 - 5) / (double) (gap - 5);
                        int sag = (int) Math.round(4 * t * (1 - t) * 3);
                        g.set(x, level - sag, 2, "minecraft:iron_chain[axis=x,waterlogged=false]");
                    }
                }
            }
        }
        return g;
    }

    /**
     * A lattice mast w square from (x0, y0, z0) up h: four legs, and on each side a diagonal
     * brace a tier at a time, the way turning tier to tier, with a strut across each tier.
     */
    private static void lattice(Grid g, int x0, int y0, int z0, int w, int h, String m) {
        int x1 = x0 + w - 1, z1 = z0 + w - 1;
        for (int y = y0; y < y0 + h; y++) {
            g.set(x0, y, z0, m); g.set(x1, y, z0, m); g.set(x0, y, z1, m); g.set(x1, y, z1, m);
        }
        int tier = w - 1;
        for (int t = 0; t * tier < h; t++) {
            int yb = y0 + t * tier;
            for (int i = 0; i <= tier; i++) {
                int y = (t % 2 == 0) ? yb + i : yb + tier - i;
                if (y >= y0 + h) continue;
                g.set(x0 + i, y, z0, m); g.set(x0 + i, y, z1, m);
                g.set(x0, y, z0 + i, m); g.set(x1, y, z0 + i, m);
                g.set(x0 + i, yb, z0, m); g.set(x0 + i, yb, z1, m);
                g.set(x0, yb, z0 + i, m); g.set(x1, yb, z0 + i, m);
            }
        }
    }

    // ── The great fixed dish ─────────────────────────────────────────────

    /** A dish in the ground's lap on a forest of columns, its feed hung over it from three towers. */
    private static Grid bowl(Rng r) {
        int d = r.range(80, 120);
        double br = d * 0.42, bd = d * 0.1, focus = 3 + br * br / (4 * bd);
        int towerH = (int) Math.round(focus) + 8;
        Grid g = new Grid(0, -1, 0, d, towerH + 3, d);
        double c = d / 2.0;
        for (int x = 0; x < d; x++) for (int z = 0; z < d; z++) {
            double rr = Math.hypot(x + 0.5 - c, z + 0.5 - c);
            if (rr > br) continue;
            int y = bowlY(x, z, c, br, bd);
            // down to the lowest neighbour, so the steep rim has no gaps
            int low = y;
            for (int[] n : new int[][]{ { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                if (Math.hypot(x + n[0] + 0.5 - c, z + n[1] + 0.5 - c) <= br) low = Math.min(low, bowlY(x + n[0], z + n[1], c, br, bd) + 1);
            }
            for (int yy = Math.min(low, y); yy <= y; yy++) g.set(x, yy, z, "minecraft:light_gray_concrete");
            // where a column falls, it runs from the ground up to the dish
            if (Math.floorMod(x - (int) c, 5) == 0 && Math.floorMod(z - (int) c, 5) == 0 && rr < br * 0.96) {
                for (int yy = 0; yy < y; yy++) g.set(x, yy, z, CONCRETE_DARK);
            }
        }
        // three towers round it, the feed platform at the focus, cables from the tower tops
        int[][] towers = { { (int) c - 3, 0 }, { (int) (d * 0.06), d - 7 }, { (int) (d * 0.94) - 6, d - 7 } };
        for (int[] t : towers) lattice(g, t[0], 0, t[1], 6, towerH, STEEL_DARK);
        int pw = Math.max(5, (int) (d * 0.1)), px0 = (int) c - pw / 2, fy = (int) Math.round(focus);
        g.fill(px0, fy, px0, px0 + pw - 1, fy + 1, px0 + pw - 1, STEEL);
        for (int[] t : towers) {
            line(g, t[0] + 3, towerH, t[1] + 3, c, fy + 2, c, STEEL_DARK);
        }
        return g;
    }

    private static int bowlY(int x, int z, double c, double br, double bd) {
        double rr = Math.hypot(x + 0.5 - c, z + 0.5 - c);
        return (int) Math.floor(3 + bd * rr * rr / (br * br));
    }

    // ── Hangar ───────────────────────────────────────────────────────────

    /**
     * An arched hangar: its roof a segment of a circle over the floor (the centre below the
     * floor for a low arch), the back wall solid up to it, the front open under a door head, a
     * concrete pad before it. The door faces north. Empty, but for drums and crates down its sides.
     */
    private static Grid hangar(Rng r) {
        int w = r.range(24, 44), dep = r.range(20, 36);
        int h = (int) Math.min(Math.round(w / 2.0), Math.round(8 + w * 0.22));
        double rad = (w * w / 4.0 + h * h) / (2.0 * h), zc = h - rad, cx = w / 2.0;
        int pad = 8;
        Grid g = new Grid(0, -1, -pad, w, h + 2, dep + pad);
        int door = (int) Math.round(h * 0.6);
        for (int x = 0; x < w; x++) {
            double dx = x + 0.5 - cx;
            double outer = zc + Math.sqrt(Math.max(0, rad * rad - dx * dx));
            double inner = zc + Math.sqrt(Math.max(0, (rad - 1.2) * (rad - 1.2) - dx * dx));
            for (int z = 0; z < dep; z++) {
                g.set(x, -1, z, "minecraft:smooth_stone");
                String rib = z % 6 == 0 ? "minecraft:gray_concrete" : "minecraft:light_gray_concrete";
                for (int y = 0; y < Math.ceil(outer); y++) {
                    boolean shell = y + 0.5 > inner || (rad - 1.2) <= Math.abs(dx);
                    if (shell) g.set(x, y, z, rib);
                    else if (z == dep - 1) g.set(x, y, z, "minecraft:light_gray_concrete");
                    else if (z == 0 && y >= door) g.set(x, y, z, "minecraft:light_gray_concrete");
                }
            }
        }
        for (int x = 0; x < w; x++) for (int z = -pad; z < 0; z++) g.set(x, -1, z, "minecraft:smooth_stone");
        // stores down the sides
        for (int z = 3; z < dep - 3; z += 2) {
            if (r.sub(z).chance(0.6)) g.put(2, 0, z, "minecraft:cauldron");
            if (r.sub(z + 1000).chance(0.6)) g.put(w - 3, 0, z, "minecraft:barrel[facing=up,open=false]");
        }
        return g;
    }
}
