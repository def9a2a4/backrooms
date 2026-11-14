package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Blocks;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Blocks.Dir;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Grid;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Rng;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Roof;

import java.util.ArrayList;
import java.util.List;

/**
 * A house on Alpha, inside and out, after IronSource's house grammar (assets/shapes/house.shp)
 * and built from the old world's blocks: planks, cobblestone, logs, wool, brick, glass.
 *
 * <p>Walls and floors one block thick, a storey every four blocks (a floor and three of room),
 * windows on every storey, stairs switching back up a bay along the back. Wings to the left,
 * right and behind share the main house's wall, with a doorway through it on every storey both
 * have. Roofs are gabled, hipped, lean-to or flat, at 1:1 in stairs or 1:2 in slabs. Inside,
 * each floor is one open room, rooms cut out of rooms, or a row of rooms off a hallway; each
 * room gets a kind (living room, kitchen, pantry, workshop, bedroom, study) and is furnished
 * the way the grammar does it: its main thing against one wall, low things under the windows,
 * tall things on the inner walls, a table or a rug in the middle, corners and doorways clear.
 *
 * <p>Nobody lives in any of them. Some have been left longer than others.
 *
 * <p>The frame is {@link Grid}'s: the front (with the door) faces north, y = 0 is the first
 * block above the grass, and the ground floor replaces the grass at y = -1.
 */
final class AlphaHouse {

    static final int STOREY = 4;
    private static final int ROOM_MIN = 4;
    /** Reserved: air the furniture must leave alone (in front of a hearth, over stairs). */
    private static final String KEEP = "minecraft:cave_air";

    private enum Walls { PLANKS, COBBLE, TIMBER, LOG, BRICK, STONE }

    private enum Cover {
        WOOD("oak_stairs", "oak_slab"), COBBLE("cobblestone_stairs", "cobblestone_slab"),
        STONE("cobblestone_stairs", "smooth_stone_slab"), BRICK("brick_stairs", "brick_slab");

        final String stairs, slab;
        Cover(String stairs, String slab) { this.stairs = stairs; this.slab = slab; }
    }

    private static final int GABLE = 0, HIP = 1, SHED = 2, FLAT = 3;

    /** One part of the house: its outer walls, storeys and roof. */
    private record Part(String name, int x0, int z0, int x1, int z1, int n, int roof, int shared, boolean main) {
        int w() { return x1 - x0 + 1; }
        int d() { return z1 - z0 + 1; }
    }

    /** A room: its floor inside the walls, the storey, and its sides (bits by Dir ordinal). */
    private record Room(int x0, int z0, int x1, int z1, int k, int mask, int inner) {
        int w() { return x1 - x0 + 1; }
        int d() { return z1 - z0 + 1; }
    }

    /** A doorway through a wall between parts: at (x, z), on storeys below {@code n}. */
    private record Opening(int x, int z, int n) {}

    private final Rng rng;
    private final Grid g;
    private final Walls walls;
    private final Cover cover;
    private final boolean halfSlope;
    private final int windowEvery, windowWidth, windowHeight;
    private final int W, D, N, LW, RW, BW, LX;
    private final boolean bay, up;
    private final int doorX, doorWidth;
    private final List<Part> parts = new ArrayList<>();
    private final List<Opening> openings = new ArrayList<>();
    private final String rugColor;
    private final long wallSalt;
    /** Where each part's chimney stands: the side and the cell along it. */
    private final java.util.Map<String, int[]> chimneys = new java.util.HashMap<>();

    /** The finished house. */
    static Grid build(long seed) {
        return new AlphaHouse(seed).g;
    }

    private AlphaHouse(long seed) {
        rng = new Rng(seed);
        Rng r = rng.sub("house");
        W = r.range(7, 14);
        D = r.range(7, 11);
        boolean stairsFit = W - 2 >= 6 && D - 2 >= 5;
        N = stairsFit ? 1 + r.pick(3, 4, 1) : 1;
        walls = Walls.values()[r.pick(3, 3, 2, 2, 1, 1)];
        cover = walls == Walls.BRICK ? Cover.values()[r.pick(2, 1, 1, 2)] : Cover.values()[r.pick(3, 2, 1, 0)];
        int roofPick = r.pick(3, 2, 1, 1);
        halfSlope = cover == Cover.STONE || r.chance(0.4);
        windowEvery = r.range(2, 4);
        windowWidth = r.range(1, 2);
        windowHeight = r.range(1, 2);
        wallSalt = r.nextLong();
        rugColor = new String[]{ "red", "brown", "white", "gray", "light_gray", "orange" }[r.range(0, 5)];

        int wings = r.pick(4, 3, 2, 1);   // 0..3: left/right, then one behind
        boolean leftFirst = r.chance(0.5);
        LW = (wings >= 2 || wings == 1 && leftFirst) ? r.range(6, 8) : 0;
        RW = (wings >= 2 || wings == 1 && !leftFirst) ? r.range(6, 8) : 0;
        BW = wings >= 3 ? r.range(6, 8) : 0;
        LX = LW > 0 ? LW - 1 : 0;
        int RX = RW > 0 ? RW - 1 : 0, BY = BW > 0 ? BW - 1 : 0;

        up = stairsFit && roofPick == FLAT && r.chance(0.5);
        bay = N > 1 || up;
        doorWidth = r.range(1, 2);
        doorX = LX + (W - doorWidth) / 2;

        int pad = 3;
        g = new Grid(-pad, -1, -pad, LX + W + RX + 2 * pad, N * STOREY + 18, D + BY + 2 * pad);

        Part main = new Part("main", LX, 0, LX + W - 1, D - 1, N, roofPick, 0, true);
        parts.add(main);
        Rng wr = rng.sub("wings");
        if (LW > 0) {
            int d = Math.max(5, D - wr.range(1, 3)), z = wr.range(0, Math.min(1, D - d));
            int n = wingStoreys(wr.sub("left"));
            parts.add(new Part("left", 0, z, LX, z + d - 1, n, wingRoof(wr.sub("left"), roofPick), bit(Dir.EAST), false));
            openings.add(new Opening(LX, z + 2, n));
        }
        if (RW > 0) {
            int d = Math.max(5, D - wr.range(1, 3)), z = wr.range(0, Math.min(1, D - d));
            int n = wingStoreys(wr.sub("right"));
            int x0 = LX + W - 1;
            parts.add(new Part("right", x0, z, x0 + RW - 1, z + d - 1, n, wingRoof(wr.sub("right"), roofPick), bit(Dir.WEST), false));
            openings.add(new Opening(x0, z + 2, n));
        }
        if (BW > 0) {
            int p = wr.range(1, 2);
            // A narrow house's back wing keeps to one storey: upstairs its door would open onto the stairs.
            int n = W < 10 ? 1 : wingStoreys(wr.sub("back"));
            parts.add(new Part("back", LX + p, D - 1, LX + W - 2, D - 1 + BW - 1, n, wingRoof(wr.sub("back"), roofPick), bit(Dir.NORTH), false));
            openings.add(new Opening(LX + W - 3, D - 1, n));
        }

        for (Part p : parts) planChimney(p);
        for (Part p : parts) shell(p);
        if (bay) stairs(main);
        for (Part p : parts) interior(p);
        for (Part p : parts) chimney(p);
        for (Part p : parts) roof(p);
        path();
        decay();
    }

    private int wingStoreys(Rng r) {
        return Math.max(1, N - (r.chance(0.6) ? r.range(1, 2) : 0));
    }

    private static int wingRoof(Rng r, int mainRoof) {
        return r.chance(0.15) ? r.pick(3, 2, 1, 1) : mainRoof;
    }

    private static int bit(Dir d) {
        return 1 << d.ordinal();
    }

    // ── Materials ────────────────────────────────────────────────────────

    private String wallBlock(int x, int y, int z, Dir side, int u, int len, int n) {
        if (y < 0) return "minecraft:cobblestone";
        long h = Rng.mix(x * 73428767L ^ y * 912931L ^ z * 2654435761L ^ wallSalt);
        return switch (walls) {
            case PLANKS -> "minecraft:oak_planks";
            case COBBLE -> Math.floorMod(h, 100) < 22 ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone";
            case BRICK -> "minecraft:bricks";
            case STONE -> Math.floorMod(h, 100) < 8 ? "minecraft:cobblestone" : "minecraft:stone";
            case LOG -> Blocks.axis("oak_log", side == Dir.NORTH || side == Dir.SOUTH ? 'x' : 'z');
            case TIMBER -> {
                boolean beam = Math.floorMod(y + 1, STOREY) == 0 || y == n * STOREY - 1;
                boolean post = u % 3 == 2 && u < len - 1;
                if (beam) yield Blocks.axis("oak_log", side == Dir.NORTH || side == Dir.SOUTH ? 'x' : 'z');
                if (post) yield Blocks.axis("oak_log", 'y');
                yield "minecraft:white_wool";
            }
        };
    }

    private String cornerBlock() {
        return switch (walls) {
            case PLANKS, LOG, TIMBER -> Blocks.axis("oak_log", 'y');
            case COBBLE, STONE -> "minecraft:cobblestone";
            case BRICK -> "minecraft:bricks";
        };
    }

    private String partitionBlock() {
        return switch (walls) {
            case COBBLE, STONE -> "minecraft:cobblestone";
            case BRICK -> "minecraft:bricks";
            default -> "minecraft:oak_planks";
        };
    }

    private String endBlock() {
        return switch (walls) {
            case LOG, TIMBER -> "oak_planks";
            case COBBLE -> "cobblestone";
            case BRICK -> "bricks";
            case STONE -> "stone";
            default -> "oak_planks";
        };
    }

    private String groundFloor() {
        return walls == Walls.COBBLE || walls == Walls.STONE ? "minecraft:cobblestone" : "minecraft:oak_planks";
    }

    // ── Shell: floors, walls, openings ───────────────────────────────────

    private void shell(Part p) {
        // Floors: the ground floor in place of the grass, a slab under each storey above, the ceiling.
        for (int k = 0; k <= p.n; k++) {
            int y = k * STOREY - 1;
            String floor = k == 0 ? groundFloor() : "minecraft:oak_planks";
            for (int x = p.x0 + 1; x < p.x1; x++)
                for (int z = p.z0 + 1; z < p.z1; z++) g.set(x, y, z, floor);
        }
        int top = p.n * STOREY - 1;
        for (Dir side : Dir.values()) {
            if ((p.shared & bit(side)) != 0) continue;   // the other part's wall
            int len = sideLength(p, side);
            for (int u = 0; u < len; u++) {
                int[] c = sideCell(p, side, u);
                for (int y = -1; y <= top; y++) g.set(c[0], y, c[1], wallBlock(c[0], y, c[1], side, u, len, p.n));
            }
            for (int k = 0; k < p.n; k++) openings(p, side, k, len);
        }
        // Corner posts, except where a side either way is shared.
        int[][] corners = { { p.x0, p.z0 }, { p.x1, p.z0 }, { p.x1, p.z1 }, { p.x0, p.z1 } };
        Dir[][] cornerSides = { { Dir.NORTH, Dir.WEST }, { Dir.NORTH, Dir.EAST }, { Dir.SOUTH, Dir.EAST }, { Dir.SOUTH, Dir.WEST } };
        for (int i = 0; i < 4; i++) {
            if ((p.shared & (bit(cornerSides[i][0]) | bit(cornerSides[i][1]))) != 0) continue;
            for (int y = -1; y <= top; y++) g.set(corners[i][0], y, corners[i][1], y < 0 ? "minecraft:cobblestone" : cornerBlock());
        }
    }

    private static int sideLength(Part p, Dir side) {
        return (side == Dir.NORTH || side == Dir.SOUTH ? p.w() : p.d()) - 2;
    }

    /** Cell u (from the low end) of a side, corners excluded. */
    private static int[] sideCell(Part p, Dir side, int u) {
        return switch (side) {
            case NORTH -> new int[]{ p.x0 + 1 + u, p.z0 };
            case SOUTH -> new int[]{ p.x0 + 1 + u, p.z1 };
            case WEST -> new int[]{ p.x0, p.z0 + 1 + u };
            case EAST -> new int[]{ p.x1, p.z0 + 1 + u };
        };
    }

    /** One storey of one side: the front door, doorways into wings, or a row of window bays. */
    private void openings(Part p, Dir side, int k, int len) {
        int y0 = k * STOREY;
        // A wall onto another part: a doorway through it on the storeys both have, plain on
        // those and on the one above (which the other part's roof leans against).
        boolean plain = false;
        for (Opening o : openings) {
            if (!p.main || k > o.n) continue;
            for (int u = 0; u < len; u++) {
                int[] c = sideCell(p, side, u);
                if (c[0] != o.x || c[1] != o.z) continue;
                plain = true;
                if (k < o.n) {
                    g.set(c[0], y0, c[1], Blocks.AIR);
                    g.set(c[0], y0 + 1, c[1], Blocks.AIR);
                }
            }
        }
        if (plain) return;

        int skipFrom = -1, skipTo = -1;
        if (p.main && side == Dir.NORTH && k == 0) {
            skipFrom = doorX - p.x0 - 1;
            skipTo = skipFrom + doorWidth - 1;
            for (int i = 0; i < doorWidth; i++) {
                int x = doorX + i;
                String[] door = Blocks.door("oak_door", Dir.SOUTH, doorWidth == 2 && i == 0);
                g.set(x, y0, p.z0, door[0]);
                g.set(x, y0 + 1, p.z0, door[1]);
                g.set(x, y0 - 1, p.z0, "minecraft:cobblestone");
                g.set(x, y0, p.z0 + 1, KEEP);
                g.set(x, y0 + 1, p.z0 + 1, KEEP);
            }
            // a torch outside beside the door
            g.set(doorX - 1, y0 + 2, p.z0 - 1, Blocks.wallTorch(Dir.NORTH));
            skipFrom--;
            skipTo++;
        }
        if (len < 3) return;
        int bays = Math.max(1, Math.round((float) len / windowEvery));
        for (int b = 0; b < bays; b++) {
            int a = b * len / bays, e = (b + 1) * len / bays;
            int bw = e - a;
            if (bw < windowWidth + 1) continue;
            int s = a + (bw - windowWidth) / 2;
            if (s + windowWidth - 1 >= skipFrom && s <= skipTo) continue;
            for (int u = s; u < s + windowWidth; u++) {
                int[] c = sideCell(p, side, u);
                int[] ch = chimneys.get(p.name);
                if (ch != null && ch[0] == side.ordinal() && ch[1] == u) continue;
                for (int y = y0 + 1; y <= y0 + Math.min(windowHeight, 2); y++) g.set(c[0], y, c[1], "minecraft:glass");
            }
        }
    }

    // ── Stairs ───────────────────────────────────────────────────────────

    /**
     * Two lanes against the back wall, the first six blocks of it: a block of landing at each
     * end, four between. Flights go up lane 0 (eastward) and lane 1 (back west) by turns, a
     * storey each, the floor above opened over the flight that comes up through it.
     */
    private void stairs(Part p) {
        int bx = p.x0 + 1;
        int top = up ? p.n : p.n - 1;
        for (int k = 0; k < top; k++) {
            int lane = k % 2;
            int z = lane == 0 ? p.z1 - 2 : p.z1 - 1;
            Dir rise = lane == 0 ? Dir.EAST : Dir.WEST;
            for (int i = 0; i < 4; i++) {
                int x = lane == 0 ? bx + 1 + i : bx + 4 - i;
                int y = k * STOREY + i;
                g.set(x, y, z, Blocks.stairs("oak_stairs", rise, false));
                for (int c = y + 1; c <= y + 2; c++) {
                    if (c % STOREY == STOREY - 1 || g.free(x, c, z)) g.set(x, c, z, KEEP);
                }
            }
            // keep the landings at both ends clear
            for (int kk = k; kk <= k + 1; kk++) {
                for (int y = kk * STOREY; y < kk * STOREY + 3; y++) {
                    g.put(bx, y, p.z1 - 2, KEEP);
                    g.put(bx, y, p.z1 - 1, KEEP);
                    g.put(bx + 5, y, p.z1 - 2, KEEP);
                    g.put(bx + 5, y, p.z1 - 1, KEEP);
                }
            }
        }
        // the hatch onto a flat roof gets a railing of nothing: the parapet is enough
    }

    // ── Rooms ────────────────────────────────────────────────────────────

    private void interior(Part p) {
        for (int k = 0; k < p.n; k++) {
            Rng r = rng.sub(p.name, k);
            int mask = 0;
            if (p.main && k == 0) mask |= bit(Dir.NORTH);
            if (p.main) {
                for (Part q : parts) {
                    if (q.main || k >= q.n) continue;
                    if (q.name.equals("left")) mask |= bit(Dir.WEST);
                    if (q.name.equals("right")) mask |= bit(Dir.EAST);
                    if (q.name.equals("back")) mask |= bit(Dir.SOUTH);
                }
            } else {
                mask |= p.shared;
            }
            int x0 = p.x0 + 1, x1 = p.x1 - 1, z0 = p.z0 + 1, z1 = p.z1 - 1;
            if (p.main && bay) {
                z1 = p.z1 - 3;
                mask |= bit(Dir.SOUTH);
                // the landing past the stairs: lit
                int lx = (p.x0 + 7 + p.x1 - 1) / 2;
                if (lx > p.x0 + 6) g.set(lx, k * STOREY + 3, p.z1 - 2, "minecraft:glowstone");
            }
            if (x1 - x0 < 2 || z1 - z0 < 2) continue;
            Room room = new Room(x0, z0, x1, z1, k, mask, 0);
            int dx = p.main && k == 0 ? doorX : -99;
            int layout = k == 0 ? r.pick(1, 3, 1) : r.pick(0.5, 6, 1);
            switch (layout) {
                case 0 -> furnish(room, r.sub("open"));
                case 1 -> divide(room, r.sub("divide"), dx);
                default -> hall(room, r.sub("hall"), dx, p.main && bay);
            }
        }
    }

    /** Cut a room in two with a wall across its long way, again and again, until too small or by chance. */
    private void divide(Room room, Rng r, int dx) {
        boolean split = room.k == 0 ? r.chance(0.5) : r.chance(0.85);
        if (Math.max(room.w(), room.d()) < 2 * ROOM_MIN + 1 || !split) {
            furnish(room, r);
            return;
        }
        if (room.w() >= room.d()) {
            int at = r.range(ROOM_MIN, room.w() - ROOM_MIN - 1);
            int c = room.x0 + at;
            if (c >= dx - 1 && c <= dx + doorWidth) {   // in the front door's way: move it clear
                int a = dx - 2, b = dx + doorWidth + 1;
                if (a - room.x0 >= ROOM_MIN) c = a;
                else if (room.x1 - b >= ROOM_MIN) c = b;
                else { furnish(room, r); return; }
            }
            partition(c, room.z0, c, room.z1, room.k, r.chance(0.5));
            divide(new Room(room.x0, room.z0, c - 1, room.z1, room.k, room.mask & ~bit(Dir.EAST), room.inner | bit(Dir.EAST)), r.sub(0), dx);
            divide(new Room(c + 1, room.z0, room.x1, room.z1, room.k, room.mask & ~bit(Dir.WEST), room.inner | bit(Dir.WEST)), r.sub(1), dx);
        } else {
            int at = r.range(ROOM_MIN, room.d() - ROOM_MIN - 1);
            int c = room.z0 + at;
            partition(room.x0, c, room.x1, c, room.k, r.chance(0.5));
            divide(new Room(room.x0, room.z0, room.x1, c - 1, room.k, room.mask & ~bit(Dir.SOUTH), room.inner | bit(Dir.SOUTH)), r.sub(0), dx);
            divide(new Room(room.x0, c + 1, room.x1, room.z1, room.k, room.mask & ~bit(Dir.NORTH), room.inner | bit(Dir.NORTH)), r.sub(1), -99);
        }
    }

    /** A wall from (ax, az) to (bx, bz), a storey high, its doorway at one end. */
    private void partition(int ax, int az, int bx, int bz, int k, boolean doorAtStart) {
        int y0 = k * STOREY;
        String m = partitionBlock();
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                boolean door = doorAtStart ? (x == ax && z == az) : (x == bx && z == bz);
                for (int y = y0; y < y0 + 3; y++) g.set(x, y, z, door && y < y0 + 2 ? KEEP : m);
            }
        }
    }

    /** Rooms in a row along a hallway at the back (the landing, where there are stairs), a door each. */
    private void hall(Room room, Rng r, int dx, boolean landing) {
        int hallDepth = landing ? 0 : 2;
        if (room.w() < ROOM_MIN || room.d() < ROOM_MIN + 1 + hallDepth) {
            divide(room, r, dx);
            return;
        }
        int wallZ = room.z1 - hallDepth;
        if (!landing) {
            // the corridor: a lamp over it, a runner down it
            int cz = room.z1 - 1;
            for (int x = room.x0; x <= room.x1; x++) {
                if (r.chance(0.6)) g.set(x, room.k * STOREY - 1, cz, Blocks.id(rugColor + "_wool"));
            }
            g.set((room.x0 + room.x1) / 2, room.k * STOREY + 3, cz, "minecraft:glowstone");
        }
        hallCut(new Room(room.x0, room.z0, room.x1, wallZ - 1, room.k, room.mask & ~bit(Dir.SOUTH), room.inner), wallZ, r, dx);
    }

    private void hallCut(Room room, int wallZ, Rng r, int dx) {
        if (room.w() >= 2 * ROOM_MIN + 1 && r.chance(0.8)) {
            int c = room.x0 + r.range(ROOM_MIN, room.w() - ROOM_MIN - 1);
            if (!(c >= dx - 1 && c <= dx + doorWidth)) {
                for (int y = room.k * STOREY; y < room.k * STOREY + 3; y++)
                    for (int z = room.z0; z <= room.z1; z++) g.set(c, y, z, partitionBlock());
                hallCut(new Room(room.x0, room.z0, c - 1, room.z1, room.k, room.mask, room.inner | bit(Dir.EAST)), wallZ, r.sub(0), dx);
                hallCut(new Room(c + 1, room.z0, room.x1, room.z1, room.k, room.mask, room.inner | bit(Dir.WEST)), wallZ, r.sub(1), dx);
                return;
            }
        }
        // the door wall behind it, a doorway somewhere along it
        int door = room.x0 + r.range(1, Math.max(1, room.w() - 2));
        for (int x = room.x0 - 1; x <= room.x1 + 1; x++) {
            for (int y = room.k * STOREY; y < room.k * STOREY + 3; y++) {
                if (x == door && y < room.k * STOREY + 2) g.set(x, y, wallZ, KEEP);
                else g.put(x, y, wallZ, partitionBlock());
            }
        }
        furnish(new Room(room.x0, room.z0, room.x1, room.z1, room.k, room.mask | bit(Dir.SOUTH), room.inner | bit(Dir.SOUTH)), r);
    }

    // ── Furniture ────────────────────────────────────────────────────────

    /** Cell (x, z) at u along wall f (left to right, facing it from inside) and v out from it. */
    private static int[] at(Room r, Dir f, int u, int v) {
        return switch (f) {
            case NORTH -> new int[]{ r.x0 + u, r.z0 + v };
            case SOUTH -> new int[]{ r.x1 - u, r.z1 - v };
            case EAST -> new int[]{ r.x1 - v, r.z0 + u };
            case WEST -> new int[]{ r.x0 + v, r.z1 - u };
        };
    }

    private static int along(Room r, Dir f) {
        return f == Dir.NORTH || f == Dir.SOUTH ? r.w() : r.d();
    }

    private static int deep(Room r, Dir f) {
        return f == Dir.NORTH || f == Dir.SOUTH ? r.d() : r.w();
    }

    private void furnish(Room room, Rng r) {
        if (room.w() < 3 || room.d() < 3) return;
        String kind;
        if (room.k == 0) {
            kind = new String[]{ "living", "kitchen", "pantry", "workshop", "bedroom" }[r.pick(3, 3, 1, 1, N == 1 ? 3 : 0)];
        } else {
            kind = r.pick(4, 1) == 0 ? "bedroom" : "study";
        }
        int y = room.k * STOREY;
        // the main wall: an outer one if there is one, never one kept clear
        int start = r.range(0, 3);
        Dir f = null;
        for (int pass = 0; pass < 2 && f == null; pass++) {
            for (int i = 0; i < 4; i++) {
                Dir d = Dir.values()[(start + i) % 4];
                int avoid = pass == 0 ? room.mask | room.inner : room.mask;
                if ((avoid & bit(d)) == 0) { f = d; break; }
            }
        }
        int depth = 0;
        if (f != null) depth = feature(room, f, kind, y, r.sub("feature"));
        // the other walls
        for (Dir s : Dir.values()) {
            if (s == f || (room.mask & bit(s)) != 0) continue;
            int len = along(room, s);
            Rng sr = r.sub("side", s.ordinal());
            if (kind.equals("living") && f != null && s == f.opposite() && len >= 3) {
                seating(room, s, y, sr);
                continue;
            }
            for (int u = 1; u < len - 1; u++) {
                int[] c = at(room, s, u, 0);
                if (f != null && insideStrip(room, f, depth, c)) continue;
                if ((room.inner & bit(s)) != 0) innerSlot(c, y, s, kind, sr.sub(u));
                else low(c, y, s, kind, sr.sub(u));
            }
        }
        middle(room, f, depth, kind, y, r.sub("middle"));
    }

    private static boolean insideStrip(Room room, Dir f, int depth, int[] c) {
        if (depth <= 0) return false;
        return switch (f) {
            case NORTH -> c[1] < room.z0 + depth;
            case SOUTH -> c[1] > room.z1 - depth;
            case EAST -> c[0] > room.x1 - depth;
            case WEST -> c[0] < room.x0 + depth;
        };
    }

    /** The room's main thing along wall f; returns how deep a strip it took. */
    private int feature(Room room, Dir f, String kind, int y, Rng r) {
        int len = along(room, f);
        Dir in = f.opposite();
        int mid = len / 2;
        switch (kind) {
            case "living" -> {
                for (int u = Math.max(1, mid - 1); u <= Math.min(len - 2, mid); u++) {
                    int[] c = at(room, f, u, 0);
                    tall(c, y, "minecraft:bookshelf");
                }
                return 1;
            }
            case "kitchen" -> {
                for (int u = 1; u < len - 1; u++) {
                    int[] c = at(room, f, u, 0);
                    int pick = u == mid ? 1 : r.sub(u).pick(5, 1, 1);
                    g.put(c[0], y, c[1], switch (pick) {
                        case 0 -> "minecraft:smooth_stone_slab[type=double,waterlogged=false]";
                        case 1 -> Blocks.facing("furnace", in);
                        default -> "minecraft:cauldron";
                    });
                    if (deep(room, f) >= 5 && r.sub(u).chance(0.6)) {
                        int[] s = at(room, f, u, 1);
                        g.put(s[0], y, s[1], Blocks.joined("oak_fence", false, false, false, false));
                    }
                }
                return 2;
            }
            case "pantry" -> {
                for (int u = 1; u < len - 1; u++) {
                    int[] c = at(room, f, u, 0);
                    if (r.sub(u).chance(0.8)) g.put(c[0], y, c[1], Blocks.chest(in));
                }
                return 1;
            }
            case "workshop" -> {
                String[] row = { Blocks.facing("furnace", in), "minecraft:crafting_table", Blocks.chest(in) };
                for (int i = 0; i < 3 && mid - 1 + i < len - 1; i++) {
                    int[] c = at(room, f, Math.max(1, mid - 1 + i), 0);
                    g.put(c[0], y, c[1], row[i]);
                }
                return 1;
            }
            case "study" -> {
                int[] c = at(room, f, mid, 0);
                table(c, y, r.chance(0.5));
                if (deep(room, f) >= 4) {
                    int[] s = at(room, f, mid, 1);
                    g.put(s[0], y, s[1], Blocks.stairs("oak_stairs", in, false));
                }
                if (len >= 5) tall(at(room, f, mid - 2 >= 1 ? mid - 2 : mid + 2, 0), y, "minecraft:bookshelf");
                return 2;
            }
            default -> {   // bedroom
                int beds = len >= 6 && deep(room, f) >= 5 && r.chance(0.3) ? 2 : 1;
                int u0 = Math.max(1, mid - beds / 2);
                if (u0 + beds - 1 > len - 2) return 0;
                for (int b = 0; b < beds; b++) {
                    int[] head = at(room, f, u0 + b, 0), foot = at(room, f, u0 + b, 1);
                    String[] bed = Blocks.bed("red", f);
                    if (g.free(head[0], y, head[1]) && g.free(foot[0], y, foot[1])) {
                        g.set(foot[0], y, foot[1], bed[0]);
                        g.set(head[0], y, head[1], bed[1]);
                    }
                }
                Rng sr = r.sub("bedside");
                if (u0 - 1 >= 1 && sr.chance(0.75)) table(at(room, f, u0 - 1, 0), y, sr.chance(0.5));
                if (u0 + beds <= len - 2 && sr.chance(0.75)) table(at(room, f, u0 + beds, 0), y, sr.chance(0.5));
                if (deep(room, f) >= 4 && sr.chance(0.6)) {
                    int[] c = at(room, f, u0, 2);
                    g.put(c[0], y, c[1], Blocks.chest(f));
                }
                return 3;
            }
        }
    }

    /** A small table: a fence post with a pressure plate on it, or a torch (a lamp). */
    private void table(int[] c, int y, boolean lamp) {
        if (!g.free(c[0], y, c[1]) || !g.free(c[0], y + 1, c[1])) return;
        g.set(c[0], y, c[1], Blocks.joined("oak_fence", false, false, false, false));
        g.set(c[0], y + 1, c[1], lamp ? "minecraft:torch" : "minecraft:oak_pressure_plate[powered=false]");
    }

    private void tall(int[] c, int y, String block) {
        if (!g.free(c[0], y, c[1]) || !g.free(c[0], y + 1, c[1])) return;
        g.set(c[0], y, c[1], block);
        g.set(c[0], y + 1, c[1], block);
    }

    /** Facing the main wall from the far one: a bench of stairs with a lamp table each end. */
    private void seating(Room room, Dir s, int y, Rng r) {
        int len = along(room, s);
        int w = len >= 6 ? 3 : 2, u0 = (len - w) / 2;
        for (int u = u0; u < u0 + w; u++) {
            int[] c = at(room, s, u, 0);
            g.put(c[0], y, c[1], Blocks.stairs("oak_stairs", s, false));
        }
        if (u0 - 1 >= 1) table(at(room, s, u0 - 1, 0), y, r.chance(0.6));
        if (u0 + w <= len - 2) table(at(room, s, u0 + w, 0), y, r.chance(0.6));
    }

    /** Against a wall with windows: low things only. */
    private void low(int[] c, int y, Dir s, String kind, Rng r) {
        Dir in = s.opposite();
        int pick = switch (kind) {
            case "kitchen" -> r.pick(2, 1, 1, 3);
            case "living" -> r.pick(2, 0, 1, 4);
            case "pantry" -> r.pick(6, 0, 0, 1);
            case "workshop" -> r.pick(3, 1, 0, 2);
            case "study" -> r.pick(1, 0, 0, 3);
            default -> r.pick(2, 0, 0, 4);
        };
        switch (pick) {
            case 0 -> g.put(c[0], y, c[1], Blocks.chest(in));
            case 1 -> g.put(c[0], y, c[1], kind.equals("kitchen") ? "minecraft:cauldron" : "minecraft:crafting_table");
            case 2 -> g.put(c[0], y, c[1], Blocks.stairs("oak_stairs", s, false));
            default -> { }
        }
    }

    /** Against an inner wall: tall things, a torch over a low thing, or nothing. */
    private void innerSlot(int[] c, int y, Dir s, String kind, Rng r) {
        switch (r.pick(4, 2, 2)) {
            case 0 -> {
                switch (kind) {
                    case "living", "study" -> tall(c, y, "minecraft:bookshelf");
                    case "bedroom" -> { if (r.chance(0.5)) tall(c, y, Blocks.axis("oak_log", 'y')); else g.put(c[0], y, c[1], "minecraft:bookshelf"); }
                    default -> { if (r.chance(0.5)) tall(c, y, Blocks.axis("oak_log", 'y')); else g.put(c[0], y, c[1], Blocks.chest(s.opposite())); }
                }
            }
            case 1 -> {
                g.put(c[0], y + 1, c[1], Blocks.wallTorch(s.opposite()));
                low(c, y, s, kind, r.sub("under"));
            }
            default -> { }
        }
    }

    /** The middle, a block clear of every wall: a rug and a lamp, a table and chairs, a chest. */
    private void middle(Room room, Dir f, int depth, String kind, int y, Rng r) {
        int x0 = room.x0 + 1, x1 = room.x1 - 1, z0 = room.z0 + 1, z1 = room.z1 - 1;
        if (f != null && depth > 0) {
            switch (f) {
                case NORTH -> z0 = Math.max(z0, room.z0 + depth);
                case SOUTH -> z1 = Math.min(z1, room.z1 - depth);
                case EAST -> x1 = Math.min(x1, room.x1 - depth);
                case WEST -> x0 = Math.max(x0, room.x0 + depth);
            }
        }
        int cx = (room.x0 + room.x1) / 2, cz = (room.z0 + room.z1) / 2;
        boolean lamp = kind.equals("living") || kind.equals("kitchen") || r.chance(0.35);
        if (lamp) g.set(cx, y + 3, cz, "minecraft:glowstone");
        if (x1 < x0 || z1 < z0) return;
        switch (kind) {
            case "kitchen", "workshop" -> {
                if (kind.equals("workshop") && r.chance(0.6)) {
                    g.put((x0 + x1) / 2, y, (z0 + z1) / 2, "minecraft:crafting_table");
                } else {
                    dining(x0, z0, x1, z1, y, r);
                }
            }
            case "pantry" -> { if (r.chance(0.5)) g.put((x0 + x1) / 2, y, (z0 + z1) / 2, Blocks.chest(Dir.SOUTH)); }
            default -> {
                if (r.chance(0.6)) {
                    String wool = Blocks.id(rugColor + "_wool");
                    for (int x = x0; x <= x1; x++)
                        for (int z = z0; z <= z1; z++) g.set(x, y - 1, z, wool);
                }
            }
        }
    }

    /** A table down the long way of the middle, chairs along both sides. */
    private void dining(int x0, int z0, int x1, int z1, int y, Rng r) {
        boolean alongX = x1 - x0 >= z1 - z0;
        int w = alongX ? x1 - x0 + 1 : z1 - z0 + 1, d = alongX ? z1 - z0 + 1 : x1 - x0 + 1;
        if (w < 1 || d < 1) return;
        int len = Math.max(1, w - 2), from = (w - len) / 2;
        int mid = (d - 1) / 2;
        for (int i = from; i < from + len; i++) {
            int tx = alongX ? x0 + i : x0 + mid, tz = alongX ? z0 + mid : z0 + i;
            boolean a = i > from, b = i < from + len - 1;
            String fence = alongX ? Blocks.joined("oak_fence", false, b, false, a) : Blocks.joined("oak_fence", a, false, b, false);
            if (g.put(tx, y, tz, fence)) g.put(tx, y + 1, tz, "minecraft:oak_pressure_plate[powered=false]");
            if (d >= 3) {
                for (int side = -1; side <= 1; side += 2) {
                    if (!r.sub(i * 2 + (side + 1) / 2).chance(0.7)) continue;
                    int sx = alongX ? tx : tx + side, sz = alongX ? tz + side : tz;
                    Dir back = alongX ? (side < 0 ? Dir.NORTH : Dir.SOUTH) : (side < 0 ? Dir.WEST : Dir.EAST);
                    g.put(sx, y, sz, Blocks.stairs("oak_stairs", back, false));
                }
            }
        }
    }

    // ── Chimney ──────────────────────────────────────────────────────────

    /**
     * On a side that has no door and opens onto no other part: an end wall (on the ridge's
     * line) if one is free, else a side wall; in its middle. Rolled before the walls are built,
     * so no window lands on it.
     */
    private void planChimney(Part p) {
        Rng r = rng.sub("chimney", parts.indexOf(p));
        if (p.roof == FLAT || !r.chance(0.6)) return;
        int busy = p.shared | (p.main ? bit(Dir.NORTH) : 0);
        if (p.main) {
            for (Part q : parts) {
                if (q.name.equals("left")) busy |= bit(Dir.WEST);
                if (q.name.equals("right")) busy |= bit(Dir.EAST);
                if (q.name.equals("back")) busy |= bit(Dir.SOUTH);
            }
        }
        boolean alongX = ridgeAlongX(p);
        Dir[] ends = alongX ? new Dir[]{ Dir.WEST, Dir.EAST } : new Dir[]{ Dir.NORTH, Dir.SOUTH };
        Dir[] sides = alongX ? new Dir[]{ Dir.NORTH, Dir.SOUTH } : new Dir[]{ Dir.WEST, Dir.EAST };
        int flip = r.range(0, 1);
        for (Dir[] group : new Dir[][]{ ends, sides }) {
            for (int i = 0; i < 2; i++) {
                Dir d = group[(i + flip) % 2];
                if ((busy & bit(d)) == 0) {
                    chimneys.put(p.name, new int[]{ d.ordinal(), sideLength(p, d) / 2, r.range(1, 2) });
                    return;
                }
            }
        }
    }

    /** The hearth in the wall on the ground floor, the flue up the wall, the stack outside up past the roof. */
    private void chimney(Part p) {
        int[] ch = chimneys.get(p.name);
        if (ch == null) return;
        Dir side = Dir.values()[ch[0]];
        int[] c = sideCell(p, side, ch[1]);
        String m = walls == Walls.BRICK ? "minecraft:bricks" : "minecraft:cobblestone";
        int top = p.n * STOREY - 1;
        for (int y = 0; y <= top; y++) g.set(c[0], y, c[1], m);
        g.set(c[0], 0, c[1], Blocks.facing("furnace", side.opposite()));
        g.set(c[0] - side.dx, 0, c[1] - side.dz, KEEP);
        g.set(c[0] - side.dx, 1, c[1] - side.dz, KEEP);
        int ox = c[0] + side.dx, oz = c[1] + side.dz;
        int roofTop = p.n * STOREY + roofRise(p) + ch[2];
        for (int y = -1; y <= roofTop; y++) g.set(ox, y, oz, m);
    }

    // ── Roofs ────────────────────────────────────────────────────────────

    private boolean ridgeAlongX(Part p) {
        if (!p.main) return p.name.equals("left") || p.name.equals("right");
        return p.w() >= p.d();
    }

    private boolean partHalfSlope(Part p) {
        int span = ridgeAlongX(p) ? p.d() : p.w();
        return halfSlope || (span + 2) / 2 > 6;
    }

    /** Roughly how high the roof rises over the walls (for the chimney). */
    private int roofRise(Part p) {
        int span = (p.roof == SHED ? (ridgeAlongX(p) ? p.d() : p.w()) : (ridgeAlongX(p) ? p.d() : p.w()) / 2) + 1;
        if (p.roof == HIP) span = Math.min(p.w(), p.d()) / 2 + 1;
        return partHalfSlope(p) ? (span + 1) / 2 : span;
    }

    private void roof(Part p) {
        int baseY = p.n * STOREY;
        if (p.roof == FLAT) {
            for (Dir side : Dir.values()) {
                if ((p.shared & bit(side)) != 0) continue;
                for (int u = -1; u <= sideLength(p, side); u++) {
                    int[] c = sideCell(p, side, u);
                    g.add(c[0], baseY, c[1], wallBlock(c[0], baseY, c[1], side, Math.max(0, u), sideLength(p, side), p.n));
                }
            }
            return;
        }
        Roof.Kind kind = p.roof == GABLE ? Roof.Kind.GABLE : p.roof == HIP ? Roof.Kind.HIP : Roof.Kind.SHED;
        // A lean-to rises toward the part it leans on (the main house's back, for the main house).
        Dir low = p.shared != 0 ? sharedDir(p).opposite() : Dir.NORTH;
        Roof.build(g, p.x0, p.z0, p.x1, p.z1, baseY, kind, ridgeAlongX(p), low, p.shared, partHalfSlope(p),
                cover.stairs, cover.slab, endBlock());
    }

    private static Dir sharedDir(Part p) {
        for (Dir d : Dir.values()) if ((p.shared & bit(d)) != 0) return d;
        return Dir.NORTH;
    }

    // ── Outside, and time ────────────────────────────────────────────────

    /** A gravel path from the front door out into the grass. */
    private void path() {
        Rng r = rng.sub("path");
        if (!r.chance(0.7)) return;
        int len = r.range(2, 3);
        for (int z = -1; z >= -len; z--) {
            for (int i = 0; i < doorWidth; i++) g.set(doorX + i, -1, z, "minecraft:gravel");
        }
    }

    /**
     * How long it has been left: most houses are only empty; some have lost glass and gone to
     * moss; a few are falling in, the roof first, then the upper walls and floors.
     */
    private void decay() {
        Rng r = rng.sub("decay");
        int level = r.pick(6, 3, 1);
        if (level == 0) return;
        long salt = r.nextLong();
        for (int y = g.minY; y <= g.maxY(); y++) {
            for (int z = g.minZ; z <= g.maxZ(); z++) {
                for (int x = g.minX; x <= g.maxX(); x++) {
                    String s = g.get(x, y, z);
                    if (s == null || s.equals(Blocks.AIR) || s.equals(KEEP)) continue;
                    long h = Rng.mix(salt ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L);
                    double roll = (h >>> 11) * 0x1.0p-53;
                    // patches: the same roll for a 3x3x3 neighbourhood, nudged per block
                    long ph = Rng.mix(salt ^ Math.floorDiv(x, 3) * 31L ^ Math.floorDiv(y, 3) * 1031L ^ Math.floorDiv(z, 3) * 104729L);
                    double patch = (ph >>> 11) * 0x1.0p-53;
                    String m = Blocks.material(s);
                    if (m.equals("minecraft:glass") && roll < (level == 1 ? 0.25 : 0.7)) {
                        g.set(x, y, z, Blocks.AIR);
                    } else if (m.equals("minecraft:cobblestone") && roll < 0.3) {
                        g.set(x, y, z, "minecraft:mossy_cobblestone");
                    } else if (level == 2 && y >= STOREY) {
                        double height = (double) (y - STOREY) / Math.max(1, g.maxY() - STOREY);
                        if (patch < 0.25 + 0.6 * height && roll < 0.85) g.set(x, y, z, Blocks.AIR);
                    } else if (level == 2 && y >= 0 && y < STOREY && !isStructural(m) && roll < 0.5) {
                        g.set(x, y, z, Blocks.AIR);   // furniture carried off, or rotted
                    }
                }
            }
        }
    }

    private static boolean isStructural(String m) {
        return m.endsWith("planks") || m.endsWith("cobblestone") || m.endsWith("log") || m.endsWith("bricks")
                || m.endsWith("wool") || m.equals("minecraft:stone") || m.equals("minecraft:glass")
                || m.endsWith("_door") || m.equals("minecraft:gravel");
    }
}
