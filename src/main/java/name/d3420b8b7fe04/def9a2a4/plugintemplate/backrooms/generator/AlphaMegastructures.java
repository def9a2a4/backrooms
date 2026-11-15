package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.skin.PlayerSkinCache;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.skin.Skin;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.skin.SkinModel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.skin.SkinPalette;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Alpha's megastructures: things far too big for one chunk, out past the
 * point where the old world stops pretending to be ordinary. Pillar fields,
 * an obsidian cube, a glass terrarium around a house, a dirt sheet hanging in
 * the sky, a staircase to nowhere, a giant alpha oak, and giant portraits —
 * a face, a head, a whole statue — painted from the skins of real players,
 * and (rarely) an installation: a control centre among radars and hangars.
 * Plus the Wall, which runs for kilometres and has one gap.
 *
 * The world is divided into square cells (megastructure_cell_size, 384-1024
 * blocks); each holds at most one
 * megastructure, chosen and placed from the world seed and the cell
 * coordinates alone, and always fully inside its cell. Walls use their own
 * coarser grid. Every chunk lies inside exactly one cell of each grid, so it
 * only has to ask those two cells what to draw, and draws just its own slice.
 * Nothing is stored except which skin a portrait got (see PlayerSkinCache).
 */
final class AlphaMegastructures {

    static final int WALL_CELL = 2048;
    private static final int EDGE = 16;
    private static final int BASE_Y = Level101ChunkGenerator.GROUND_Y + 1;
    private static final int MIN_Y = -64, MAX_Y = 319;

    private enum Kind {
        PILLAR_FIELD(5), CUBE(2), TERRARIUM(3), SKY_SHEET(3), STAIRCASE(4),
        GIANT_OAK(4), FACE(3), HEAD(3), STATUE(3), INSTALLATION(1);

        final int weight;
        Kind(int weight) { this.weight = weight; }
    }

    private static final int TOTAL_WEIGHT;
    static {
        int sum = 0;
        for (Kind k : Kind.values()) sum += k.weight;
        TOTAL_WEIGHT = sum;
    }

    /** Builds a house inside one chunk (the terrarium's exhibit). */
    @FunctionalInterface
    interface HousePlacer {
        void place(ChunkData chunkData, Random rng);
    }

    private final int cell;
    private final int minDistance;
    private final double cellChance;
    private final double wallChance;
    private final HousePlacer housePlacer;

    AlphaMegastructures(int cellSize, int minDistance, double cellChancePercent, double wallChancePercent,
                        HousePlacer housePlacer) {
        // Multiple of 16 so every chunk sits inside exactly one cell; at least 384 so the
        // biggest footprints (pillar fields, sky sheets) fit with clearance.
        this.cell = Math.max(384, Math.min(1024, cellSize / 16 * 16));
        this.minDistance = minDistance;
        this.cellChance = cellChancePercent / 100.0;
        this.wallChance = wallChancePercent / 100.0;
        this.housePlacer = housePlacer;
    }

    // ── Public surface ───────────────────────────────────────────────────

    /** The megastructures with any part within {@code pad} blocks of this chunk. */
    List<Site> sitesNear(long seed, int chunkX, int chunkZ, int pad) {
        List<Site> out = new ArrayList<>(2);
        Site here = siteAt(seed, Math.floorDiv(chunkX * 16, cell), Math.floorDiv(chunkZ * 16, cell));
        if (here != null && here.touches(chunkX, chunkZ, pad)) out.add(here);
        Site wall = wallAt(seed, Math.floorDiv(chunkX * 16, WALL_CELL), Math.floorDiv(chunkZ * 16, WALL_CELL));
        if (wall != null && wall.touches(chunkX, chunkZ, pad)) out.add(wall);
        return out;
    }

    /** Every kind {@link #nearest} can find. */
    static final List<String> KINDS = List.of("pillar_field", "cube", "terrarium", "sky_sheet", "staircase",
            "giant_oak", "face", "head", "statue", "installation", "wall");

    private static String kindOf(Site s) {
        return switch (s) {
            case PillarField p -> "pillar_field";
            case Cube c -> "cube";
            case Terrarium t -> "terrarium";
            case SkySheet k -> "sky_sheet";
            case Staircase k -> "staircase";
            case GiantOak o -> "giant_oak";
            case Portrait p -> p.kind().name().toLowerCase(java.util.Locale.ROOT);
            case Installation i -> "installation";
            case Wall w -> "wall";
            default -> "";
        };
    }

    /**
     * The world box {minX, minZ, maxX, maxZ} of the nearest megastructure of {@code kind} to
     * (x, z), searching out to {@code maxCells} cells; null if there is none that near. For an
     * installation, the box of its control centre (the area round it is mostly empty plain).
     */
    @Nullable int[] nearest(long seed, String kind, int x, int z, int maxCells) {
        boolean wall = kind.equals("wall");
        int size = wall ? WALL_CELL : cell;
        int cx = Math.floorDiv(x, size), cz = Math.floorDiv(z, size);
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int ring = 0; ring <= maxCells; ring++) {
            if (best != null && (ring - 1) * (double) size > bestD) break;
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
                    Site s = wall ? wallAt(seed, cx + i, cz + j) : siteAt(seed, cx + i, cz + j);
                    if (s == null || !kindOf(s).equals(kind)) continue;
                    int[] box = { s.minX(), s.minZ(), s.maxX(), s.maxZ() };
                    if (s instanceof Installation in) {
                        var c = AlphaInstallation.get(seed, in.x0(), in.z0(), in.size()).parts.get(1);
                        box = new int[]{ c.minX(), c.minZ(), c.maxX(), c.maxZ() };
                    }
                    double dx = Math.max(box[0] - x, Math.max(0, x - box[2]));
                    double dz = Math.max(box[1] - z, Math.max(0, z - box[3]));
                    double d = Math.hypot(dx, dz);
                    if (d < bestD) { bestD = d; best = box; }
                }
            }
        }
        return best;
    }

    /** True if a megastructure (or the Wall) overlaps the world box. The box must lie inside one cell. */
    boolean occupied(long seed, int minX, int minZ, int maxX, int maxZ) {
        Site s = siteAt(seed, Math.floorDiv(minX, cell), Math.floorDiv(minZ, cell));
        if (s != null && overlaps(s, minX, minZ, maxX, maxZ)) return true;
        Site w = wallAt(seed, Math.floorDiv(minX, WALL_CELL), Math.floorDiv(minZ, WALL_CELL));
        return w != null && overlaps(w, minX, minZ, maxX, maxZ);
    }

    private static boolean overlaps(Site s, int minX, int minZ, int maxX, int maxZ) {
        return s.maxX() >= minX && s.minX() <= maxX && s.maxZ() >= minZ && s.minZ() <= maxZ;
    }

    void place(ChunkData data, long seed, int chunkX, int chunkZ, List<Site> sites) {
        Slice slice = new Slice(data, chunkX, chunkZ, seed);
        for (Site s : sites) s.place(slice);
    }

    /** World position of the sign on top of a staircase to nowhere, if it falls in this chunk. */
    @Nullable int[] staircaseSign(long seed, int chunkX, int chunkZ) {
        Site s = siteAt(seed, Math.floorDiv(chunkX * 16, cell), Math.floorDiv(chunkZ * 16, cell));
        if (!(s instanceof Staircase st)) return null;
        int[] p = st.signPos();
        if (Math.floorDiv(p[0], 16) != chunkX || Math.floorDiv(p[2], 16) != chunkZ) return null;
        return p;
    }

    // ── Site selection ───────────────────────────────────────────────────

    private @Nullable Site siteAt(long seed, int cellX, int cellZ) {
        int cellMinX = cellX * cell, cellMinZ = cellZ * cell;
        if (!farEnough(cellMinX + cell / 2, cellMinZ + cell / 2, cell)) return null;
        Random r = new Random(hash(cellX, cellZ, seed, 0x3E6A));
        if (r.nextDouble() >= cellChance * ramp(cellMinX + cell / 2, cellMinZ + cell / 2)) return null;

        int roll = r.nextInt(TOTAL_WEIGHT);
        Kind kind = Kind.PILLAR_FIELD;
        for (Kind k : Kind.values()) {
            if (roll < k.weight) { kind = k; break; }
            roll -= k.weight;
        }
        long siteSeed = r.nextLong();

        switch (kind) {
            case PILLAR_FIELD -> {
                int radius = Math.min(90 + r.nextInt(70), cell / 2 - EDGE - 1);
                return new PillarField(center(r, cellMinX, radius), center(r, cellMinZ, radius), radius);
            }
            case CUBE -> {
                int size = 40 + r.nextInt(17);
                int x0 = center(r, cellMinX, size / 2 + 1) - size / 2;
                int z0 = center(r, cellMinZ, size / 2 + 1) - size / 2;
                return new Cube(x0, z0, size, r.nextInt(13));
            }
            case TERRARIUM -> {
                int tcx = cellX * (cell / 16) + 2 + r.nextInt(cell / 16 - 4);
                int tcz = cellZ * (cell / 16) + 2 + r.nextInt(cell / 16 - 4);
                return new Terrarium(tcx, tcz, siteSeed, housePlacer);
            }
            case SKY_SHEET -> {
                int radius = Math.min(80 + r.nextInt(45), (int) ((cell / 2 - EDGE - 2) / 1.15));
                int reach = (int) Math.ceil(radius * 1.15);
                return new SkySheet(center(r, cellMinX, reach), center(r, cellMinZ, reach),
                        radius, 185 + r.nextInt(50));
            }
            case STAIRCASE -> {
                BlockFace dir = new BlockFace[]{ BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST }[r.nextInt(4)];
                int steps = Math.min(110 + r.nextInt(70), cell - 2 * EDGE - 12);
                int half = (steps + 3) / 2 + 2;
                int mx = center(r, cellMinX, half), mz = center(r, cellMinZ, half);
                return new Staircase(mx - dir.getModX() * (steps + 3) / 2, mz - dir.getModZ() * (steps + 3) / 2,
                        dir, steps);
            }
            case GIANT_OAK -> {
                int s = 7 + r.nextInt(3);
                int half = 5 * s / 2 + 1;
                return new GiantOak(center(r, cellMinX, half) - 5 * s / 2, center(r, cellMinZ, half) - 5 * s / 2,
                        s, siteSeed);
            }
            case INSTALLATION -> {
                int size = Math.min(cell - 2 * EDGE - 2, 320);
                int x0 = center(r, cellMinX, size / 2 + 1) - size / 2;
                int z0 = center(r, cellMinZ, size / 2 + 1) - size / 2;
                return new Installation(x0, z0, size);
            }
            default -> {
                int scale, side;
                switch (kind) {
                    case FACE -> { scale = portraitScale(r, 6); side = 8 * scale; }
                    case HEAD -> { scale = portraitScale(r, 4); side = 8 * scale; }
                    default -> { scale = portraitScale(r, 3); side = 16 * scale; }
                }
                int x0 = center(r, cellMinX, side / 2 + 1) - side / 2;
                int z0 = center(r, cellMinZ, side / 2 + 1) - side / 2;
                int sink = kind == Kind.HEAD ? r.nextInt(2 * scale + 1) : 0;
                return new Portrait(kind, x0, z0, side, scale, r.nextInt(4), sink,
                        seed + "/" + cellX + "/" + cellZ);
            }
        }
    }

    private @Nullable Site wallAt(long seed, int wcx, int wcz) {
        int cellMinX = wcx * WALL_CELL, cellMinZ = wcz * WALL_CELL;
        if (!farEnough(cellMinX + WALL_CELL / 2, cellMinZ + WALL_CELL / 2, WALL_CELL)) return null;
        Random r = new Random(hash(wcx, wcz, seed, 0x3A11));
        if (r.nextDouble() >= wallChance) return null;
        boolean alongX = r.nextBoolean();
        int length = 1100 + r.nextInt(750);
        int from = (alongX ? cellMinX : cellMinZ) + 32 + r.nextInt(WALL_CELL - 64 - length);
        int perp = (alongX ? cellMinZ : cellMinX) + 64 + r.nextInt(WALL_CELL - 128);
        return new Wall(alongX, perp, from, from + length - 1, from + 40 + r.nextInt(length - 80));
    }

    /** True when the whole cell lies beyond the spawn exclusion radius. */
    private boolean farEnough(int cx, int cz, int cellSize) {
        double nearest = Math.sqrt((double) cx * cx + (double) cz * cz) - cellSize * 0.71;
        return nearest >= minDistance;
    }

    /** Megastructures thicken with distance: 60% of the configured odds at the threshold, full odds 600 blocks further. */
    private double ramp(int cx, int cz) {
        double d = Math.sqrt((double) cx * cx + (double) cz * cz);
        return Math.min(1.0, 0.6 + (d - minDistance) / 1500.0);
    }

    /**
     * Blocks per skin pixel: mostly one (a face 8 blocks across, a statue 32 high), two a quarter
     * of the time, bigger rarely, up to {@code max}.
     */
    private static int portraitScale(Random r, int max) {
        int roll = r.nextInt(100);
        int scale = roll < 60 ? 1 : roll < 85 ? 2 : roll < 95 ? 3 : 4 + r.nextInt(Math.max(1, max - 3));
        return Math.min(max, scale);
    }

    /** A center coordinate keeping {@code half} blocks of footprint plus the edge clearance inside the cell. */
    private int center(Random r, int cellMin, int half) {
        int lo = cellMin + EDGE + half, hi = cellMin + cell - EDGE - half - 1;
        return lo + r.nextInt(Math.max(1, hi - lo + 1));
    }

    static long hash(long a, long b, long seed, int salt) {
        long h = seed;
        h ^= a * 0x517cc1b727220a95L;
        h ^= b * 0x6c62272e07bb0142L;
        h += salt * 0x6364136223846793L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    // ── Chunk slice ──────────────────────────────────────────────────────

    /** One chunk's view of the world: bounds checks and writes in world coordinates. */
    static final class Slice {
        final ChunkData data;
        final int minX, minZ;
        final long seed;
        final int chunkX, chunkZ;

        Slice(ChunkData data, int chunkX, int chunkZ, long seed) {
            this.data = data;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minX = chunkX * 16;
            this.minZ = chunkZ * 16;
            this.seed = seed;
        }

        void set(int wx, int y, int wz, Material m) {
            if (y < MIN_Y || y > MAX_Y) return;
            int lx = wx - minX, lz = wz - minZ;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15) return;
            data.setBlock(lx, y, lz, m);
        }

        void set(int wx, int y, int wz, BlockData b) {
            if (y < MIN_Y || y > MAX_Y) return;
            int lx = wx - minX, lz = wz - minZ;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15) return;
            data.setBlock(lx, y, lz, b);
        }

        /** Every world column in [x0..x1] × [z0..z1] that lies in this chunk. */
        void columns(int x0, int x1, int z0, int z1, ColumnFn fn) {
            int ax = Math.max(x0, minX), bx = Math.min(x1, minX + 15);
            int az = Math.max(z0, minZ), bz = Math.min(z1, minZ + 15);
            for (int x = ax; x <= bx; x++) {
                for (int z = az; z <= bz; z++) fn.at(x, z);
            }
        }
    }

    @FunctionalInterface
    interface ColumnFn {
        void at(int wx, int wz);
    }

    interface Site {
        int minX();
        int minZ();
        int maxX();
        int maxZ();
        void place(Slice s);

        default boolean touches(int chunkX, int chunkZ, int pad) {
            int cx0 = chunkX * 16, cz0 = chunkZ * 16;
            return maxX() + pad >= cx0 && minX() - pad <= cx0 + 15
                    && maxZ() + pad >= cz0 && minZ() - pad <= cz0 + 15;
        }
    }

    // ── Installation ─────────────────────────────────────────────────────

    /** A control centre with its radars and hangars (AlphaInstallation), abandoned on the plain. */
    private record Installation(int x0, int z0, int size) implements Site {
        public int minX() { return x0; }
        public int minZ() { return z0; }
        public int maxX() { return x0 + size - 1; }
        public int maxZ() { return z0 + size - 1; }

        public void place(Slice s) {
            AlphaInstallation.get(s.seed, x0, z0, size).place(s.data, s.chunkX, s.chunkZ);
        }
    }

    // ── Pillar field ─────────────────────────────────────────────────────

    /**
     * A crowd of 1x1 dirt and cobblestone pillars — the old way of getting up
     * high, jump and place — tallest at the middle, as if everyone climbed
     * toward the same thing and left. Some still have a torch on top.
     */
    private record PillarField(int cx, int cz, int r) implements Site {
        public int minX() { return cx - r; }
        public int minZ() { return cz - r; }
        public int maxX() { return cx + r; }
        public int maxZ() { return cz + r; }

        public void place(Slice s) {
            s.columns(minX(), maxX(), minZ(), maxZ(), (x, z) -> {
                double d = Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz));
                if (d > r) return;
                long h = hash(x, z, s.seed, 0x91A2);
                if (Math.floorMod(h, 70) != 0) return;
                int height = (int) ((24 + Math.floorMod(h >>> 8, 176)) * (0.35 + 0.65 * (1 - d / r)));
                Material m = Math.floorMod(h >>> 20, 10) < 7 ? Material.DIRT : Material.COBBLESTONE;
                for (int y = BASE_Y; y < BASE_Y + height; y++) s.set(x, y, z, m);
                if (Math.floorMod(h >>> 28, 6) == 0) s.set(x, BASE_Y + height, z, Material.TORCH);
            });
        }
    }

    // ── The cube ─────────────────────────────────────────────────────────

    /** A perfect obsidian cube, partly sunk into the plain. No door. Hollow, if you dig. */
    private record Cube(int x0, int z0, int size, int sink) implements Site {
        public int minX() { return x0; }
        public int minZ() { return z0; }
        public int maxX() { return x0 + size - 1; }
        public int maxZ() { return z0 + size - 1; }

        public void place(Slice s) {
            int y0 = BASE_Y - sink, y1 = y0 + size - 1;
            s.columns(minX(), maxX(), minZ(), maxZ(), (x, z) -> {
                boolean wallX = x - x0 < 2 || maxX() - x < 2;
                boolean wallZ = z - z0 < 2 || maxZ() - z < 2;
                for (int y = y0; y <= y1; y++) {
                    if (wallX || wallZ || y - y0 < 2 || y1 - y < 2) {
                        s.set(x, y, z, Material.OBSIDIAN);
                    } else if (y < BASE_Y) {
                        s.set(x, y, z, Material.AIR); // the sunk part is hollow too
                    }
                }
            });
        }
    }

    // ── Terrarium ────────────────────────────────────────────────────────

    /**
     * A glass display case, log-framed, around one ordinary house — somebody's
     * first build, kept. The house goes in the case's center chunk; the bed
     * inside still works, if you break the glass to reach it.
     */
    private record Terrarium(int chunkX, int chunkZ, long siteSeed, HousePlacer house) implements Site {
        private static final int HEIGHT = 20;

        public int minX() { return chunkX * 16 - 7; }
        public int minZ() { return chunkZ * 16 - 7; }
        public int maxX() { return chunkX * 16 + 22; }
        public int maxZ() { return chunkZ * 16 + 22; }

        public void place(Slice s) {
            if (s.chunkX == chunkX && s.chunkZ == chunkZ) {
                house.place(s.data, new Random(siteSeed));
            }
            int roof = BASE_Y + HEIGHT;
            s.columns(minX(), maxX(), minZ(), maxZ(), (x, z) -> {
                boolean edgeX = x == minX() || x == maxX();
                boolean edgeZ = z == minZ() || z == maxZ();
                if (edgeX && edgeZ) {
                    for (int y = BASE_Y; y <= roof; y++) s.set(x, y, z, log(Axis.Y));
                    return;
                }
                if (edgeX || edgeZ) {
                    for (int y = BASE_Y; y < roof; y++) s.set(x, y, z, Material.GLASS);
                    s.set(x, roof, z, log(edgeX ? Axis.Z : Axis.X));
                    return;
                }
                s.set(x, roof, z, Material.GLASS);
            });
        }
    }

    private static BlockData log(Axis axis) {
        Orientable log = (Orientable) Material.OAK_LOG.createBlockData();
        log.setAxis(axis);
        return log;
    }

    // ── Sky sheet ────────────────────────────────────────────────────────

    /**
     * A ragged sheet of grass-topped dirt, one block thick, hanging far above
     * the fog. Nobody sees it. They notice the light going under it.
     */
    private record SkySheet(int cx, int cz, int r, int y) implements Site {
        public int minX() { return cx - (int) Math.ceil(r * 1.15); }
        public int minZ() { return cz - (int) Math.ceil(r * 1.15); }
        public int maxX() { return cx + (int) Math.ceil(r * 1.15); }
        public int maxZ() { return cz + (int) Math.ceil(r * 1.15); }

        public void place(Slice s) {
            s.columns(minX(), maxX(), minZ(), maxZ(), (x, z) -> {
                double d = Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz));
                double edge = r * (0.85 + 0.3 * SimplexNoise.noise2(s.seed + 77, x / 45.0, z / 45.0));
                if (d >= edge) return;
                s.set(x, y, z, Material.DIRT);
                s.set(x, y + 1, z, Material.GRASS_BLOCK);
            });
        }
    }

    // ── Staircase to nowhere ─────────────────────────────────────────────

    /**
     * Cobblestone stairs, three wide, climbing one block per block for well
     * over a hundred steps, propped on thin columns, ending at a small landing
     * in the air with a sign on it (text from level_101_signs.yml).
     */
    private record Staircase(int sx, int sz, BlockFace dir, int steps) implements Site {
        private int px() { return -dir.getModZ(); }
        private int pz() { return dir.getModX(); }
        private int endX() { return sx + dir.getModX() * (steps + 2); }
        private int endZ() { return sz + dir.getModZ() * (steps + 2); }

        public int minX() { return Math.min(sx, endX()) - 1; }
        public int minZ() { return Math.min(sz, endZ()) - 1; }
        public int maxX() { return Math.max(sx, endX()) + 1; }
        public int maxZ() { return Math.max(sz, endZ()) + 1; }

        int topY() { return BASE_Y + steps - 1; }

        int[] signPos() {
            int along = steps + 1;
            return new int[]{ sx + dir.getModX() * along, topY() + 1, sz + dir.getModZ() * along };
        }

        public void place(Slice s) {
            Stairs stair = (Stairs) Material.COBBLESTONE_STAIRS.createBlockData();
            stair.setFacing(dir);
            for (int i = 0; i < steps; i++) {
                int y = BASE_Y + i;
                for (int w = -1; w <= 1; w++) {
                    int x = sx + dir.getModX() * i + px() * w;
                    int z = sz + dir.getModZ() * i + pz() * w;
                    s.set(x, y, z, stair);
                    s.set(x, y - 1, z, mossy(x, y, z, s.seed));
                }
                if (i % 16 == 8) {
                    int x = sx + dir.getModX() * i, z = sz + dir.getModZ() * i;
                    for (int y2 = BASE_Y; y2 < y - 1; y2++) s.set(x, y2, z, mossy(x, y2, z, s.seed));
                }
            }
            for (int along = steps; along <= steps + 2; along++) {
                for (int w = -1; w <= 1; w++) {
                    int x = sx + dir.getModX() * along + px() * w;
                    int z = sz + dir.getModZ() * along + pz() * w;
                    s.set(x, topY(), z, Material.COBBLESTONE);
                }
            }
            int[] p = signPos();
            Rotatable sign = (Rotatable) Material.OAK_SIGN.createBlockData();
            sign.setRotation(dir.getOppositeFace());
            s.set(p[0], p[1], p[2], sign);
        }
    }

    private static Material mossy(int x, int y, int z, long seed) {
        return Math.floorMod(hash(x * 31L + y, z, seed, 0x0055), 4) == 0
                ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE;
    }

    // ── Giant oak ────────────────────────────────────────────────────────

    /**
     * The alpha oak exactly — trunk of five, two wide leaf layers, two narrow —
     * with every block replaced by an s×s×s cube. It looks wrong only because
     * of its size. Leaves are persistent: nothing that big would ever decay.
     */
    private record GiantOak(int x0, int z0, int s, long siteSeed) implements Site {
        public int minX() { return x0; }
        public int minZ() { return z0; }
        public int maxX() { return x0 + 5 * s - 1; }
        public int maxZ() { return z0 + 5 * s - 1; }

        public void place(Slice sl) {
            Leaves leaves = (Leaves) Material.OAK_LEAVES.createBlockData();
            leaves.setPersistent(true);
            BlockData trunk = Material.OAK_LOG.createBlockData();
            sl.columns(minX(), maxX(), minZ(), maxZ(), (x, z) -> {
                int ox = Math.floorDiv(x - x0, s) - 2, oz = Math.floorDiv(z - z0, s) - 2;
                for (int oy = 0; oy < 5; oy++) {
                    BlockData b = cell(ox, oy, oz) ? trunk : leaf(ox, oy, oz) ? leaves : null;
                    if (b == null) continue;
                    for (int y = BASE_Y + oy * s; y < BASE_Y + (oy + 1) * s; y++) sl.set(x, y, z, b);
                }
            });
        }

        /** Trunk cells: the center column, below the top layer. */
        private boolean cell(int ox, int oy, int oz) {
            return ox == 0 && oz == 0 && oy < 4;
        }

        private boolean leaf(int ox, int oy, int oz) {
            int reach = oy == 1 || oy == 2 ? 2 : oy >= 3 ? 1 : 0;
            if (reach == 0 || Math.abs(ox) > reach || Math.abs(oz) > reach) return false;
            boolean corner = Math.abs(ox) == reach && Math.abs(oz) == reach;
            return !corner || (hash(ox * 7L + oy, oz, siteSeed, 0x0A4E) & 1) == 0;
        }
    }

    // ── Portraits ────────────────────────────────────────────────────────

    /**
     * A real player, built from their skin: a flat face (FACE), a head-cube
     * half sunk into the grass (HEAD), or the whole player standing (STATUE).
     * Pixel colours are matched to alpha-era blocks. The skin is pinned per
     * canvas, so every chunk paints the same person; with no skins known yet
     * the portrait is blank white — faceless.
     */
    private record Portrait(Kind kind, int x0, int z0, int side, int scale, int rot, int sink,
                            String canvasKey) implements Site {
        public int minX() { return x0; }
        public int minZ() { return z0; }
        public int maxX() { return x0 + side - 1; }
        public int maxZ() { return z0 + side - 1; }

        public void place(Slice s) {
            PlayerSkinCache cache = PlayerSkinCache.get();
            Skin skin = cache == null ? null : cache.skinForCanvas(canvasKey);
            int baseY = BASE_Y - sink;
            List<SkinModel.Box> boxes = kind == Kind.HEAD ? SkinModel.head()
                    : kind == Kind.STATUE ? SkinModel.body(skin != null && skin.slim()) : List.of();
            int height = kind == Kind.STATUE ? 32 * scale : 8 * scale;

            s.columns(minX(), maxX(), minZ(), maxZ(), (x, z) -> {
                int a = x - x0, b = z - z0;
                int lx, lz;
                switch (rot) {
                    case 1 -> { lx = b; lz = side - 1 - a; }
                    case 2 -> { lx = side - 1 - a; lz = side - 1 - b; }
                    case 3 -> { lx = side - 1 - b; lz = a; }
                    default -> { lx = a; lz = b; }
                }
                for (int ly = 0; ly < height; ly++) {
                    Material m = kind == Kind.FACE ? face(skin, lx, ly, lz) : modelVoxel(skin, boxes, lx, ly, lz);
                    if (m != null) s.set(x, baseY + ly, z, m);
                }
            });
        }

        /** The flat mural: the head's front face (with hat layer), one block thick. */
        private @Nullable Material face(@Nullable Skin skin, int lx, int ly, int lz) {
            if (lz != side / 2) return null;
            int col = lx / scale, row = 7 - ly / scale;
            if (skin == null) return Material.WHITE_WOOL;
            int over = skin.pixel(40 + col, 8 + row);
            return paint(Skin.opaque(over) ? over : skin.pixel(8 + col, 8 + row));
        }

        /** Shell voxels of the box model; interiors stay hollow. */
        private @Nullable Material modelVoxel(@Nullable Skin skin, List<SkinModel.Box> boxes, int lx, int ly, int lz) {
            for (SkinModel.Box box : boxes) {
                int bx0 = box.x() * scale, by0 = box.y() * scale, bz0 = box.z() * scale;
                int bx1 = bx0 + box.w() * scale - 1, by1 = by0 + box.h() * scale - 1, bz1 = bz0 + box.d() * scale - 1;
                if (lx < bx0 || lx > bx1 || ly < by0 || ly > by1 || lz < bz0 || lz > bz1) continue;
                SkinModel.Face face;
                if (lz == bz1) face = SkinModel.Face.FRONT;
                else if (lz == bz0) face = SkinModel.Face.BACK;
                else if (lx == bx0) face = SkinModel.Face.RIGHT;
                else if (lx == bx1) face = SkinModel.Face.LEFT;
                else if (ly == by1) face = SkinModel.Face.TOP;
                else if (ly == by0) face = SkinModel.Face.BOTTOM;
                else return null;
                if (skin == null) return Material.WHITE_WOOL;
                return paint(SkinModel.color(skin, box, face, lx / scale, ly / scale, lz / scale));
            }
            return null;
        }

        private static Material paint(int argb) {
            return Skin.opaque(argb) ? SkinPalette.blockFor(argb) : Material.WHITE_WOOL;
        }
    }

    // ── The Wall ─────────────────────────────────────────────────────────

    /**
     * Cobblestone, sixty high and three thick, dead straight for one to two
     * kilometres. Exactly one gap, a doorway's size, somewhere along it. The
     * other side looks the same.
     */
    private record Wall(boolean alongX, int perp, int from, int to, int gap) implements Site {
        private static final int HEIGHT = 60;

        public int minX() { return alongX ? from : perp - 1; }
        public int minZ() { return alongX ? perp - 1 : from; }
        public int maxX() { return alongX ? to : perp + 1; }
        public int maxZ() { return alongX ? perp + 1 : to; }

        public void place(Slice s) {
            s.columns(minX(), maxX(), minZ(), maxZ(), (x, z) -> {
                int along = alongX ? x : z;
                for (int y = BASE_Y; y < BASE_Y + HEIGHT; y++) {
                    if (along == gap && y < BASE_Y + 2) continue;
                    s.set(x, y, z, mossy(x, y, z, s.seed));
                }
            });
        }
    }
}
