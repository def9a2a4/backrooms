package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.DebugLog;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.ShapeLibrary.Shape;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.ShapeLibrary.VoxelGrid;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.logging.Logger;

/**
 * Level 418 — "I'm a Teapot". You fell out of Minecraft's block engine into a voxel engine's test
 * scene: an endless checkerboard plane scattered with the classic graphics test assets, all in
 * flat colored concrete. Utah teapots, Suzanne, the Stanford bunny / dragon / armadillo / Lucy,
 * primitives, and Cornell boxes. The only real Minecraft in the level is a small stone-and-dirt
 * cube with one oak log on top, one per super-cell; breaking wood is the way out.
 *
 * <p>Layout: the plane is split into 48-block cells, each holding at most one object (or a Cornell
 * box) that stays inside the cell. Cells group into 8×8 super-cells (384 blocks); each super-cell
 * may hold one giant, which empties the small cells under it, and always holds one Minecraft cube.
 * Chunks align to both grids, so each chunk reads exactly one cell and one super-cell. Everything
 * is hashed from the world seed and cell coordinates; shapes come voxelized from
 * {@link ShapeLibrary}.
 */
public class Level418ChunkGenerator extends BackroomsChunkGenerator {

    private static final Logger LOG = Logger.getLogger("Backrooms");

    private static final int CELL = 48;
    private static final int SUPER = 8;                // cells per super-cell side
    private static final int MARGIN = 2;               // blocks kept clear at each cell edge
    private static final int CHECK = 4;                // floor checker size
    private static final int MC_CUBE = 7;

    private static final Material[] COLORS = {
            Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.PINK_CONCRETE,
            Material.GRAY_CONCRETE, Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE,
            Material.BLUE_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE,
            Material.BLACK_CONCRETE, Material.BROWN_CONCRETE,
    };
    private static final Material[] TEAPOT_COLORS = {
            Material.RED_CONCRETE, Material.RED_CONCRETE, Material.RED_CONCRETE,
            Material.WHITE_CONCRETE, Material.WHITE_CONCRETE,
            Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.CYAN_CONCRETE,
            Material.PINK_CONCRETE, Material.LIME_CONCRETE,
    };

    // weighted pools: {shape, weight}
    private static final Object[][] SMALL = {
            {Shape.TEAPOT, 30}, {Shape.SUZANNE, 10}, {Shape.BUNNY, 10}, {Shape.DRAGON, 6},
            {Shape.ARMADILLO, 8}, {Shape.LUCY, 4}, {Shape.CUBE, 8}, {Shape.PYRAMID, 6},
            {Shape.CONE, 6}, {Shape.SPHERE, 8}, {Shape.TORUS, 8},
    };
    private static final Object[][] GIANT = {
            {Shape.LUCY, 3}, {Shape.TEAPOT, 3}, {Shape.BUNNY, 2}, {Shape.DRAGON, 2},
            {Shape.ARMADILLO, 2}, {Shape.SUZANNE, 1}, {Shape.SPHERE, 1}, {Shape.TORUS, 1},
    };

    private int floorY = 8;
    private double emptyChance = 0.3;
    private double cornellChance = 0.08;
    private double giantChance = 0.6;

    private volatile ShapeLibrary shapes;

    /** One thing placed on the plane. Shapes use the grid; the room and the cube are built by hand. */
    private enum Kind { SHAPE, CORNELL, MINECRAFT }

    /**
     * (px, pz) is the shape pivot, or the min corner for the room and the cube. size is the shape
     * height, the room side or the cube side; yaw is in shape steps, or quarter turns for the room.
     */
    private record Placement(Kind kind, Shape shape, int size, int yaw, boolean flip, boolean shell,
                             int px, int baseY, int pz, Material block) {}

    /** What a super-cell holds: an optional giant and the Minecraft cube's cell. */
    private record SuperCell(@Nullable Placement giant, int mcCellX, int mcCellZ) {}

    public Level418ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config != null) {
            floorY = config.getInt("floor_y", floorY);
            emptyChance = config.getDouble("empty_chance", emptyChance);
            cornellChance = config.getDouble("cornell_chance", cornellChance);
            giantChance = config.getDouble("giant_chance", giantChance);
        }
        shapes = load();   // on the main thread, before any chunk generates
    }

    private ShapeLibrary load() {
        long t0 = System.nanoTime();
        ShapeLibrary lib = ShapeLibrary.load();
        DebugLog.log(LOG, String.format("Level 418 meshes loaded in %.1f ms", (System.nanoTime() - t0) / 1e6));
        return lib;
    }

    private ShapeLibrary shapes() {
        ShapeLibrary s = shapes;
        if (s == null) {
            synchronized (this) {
                s = shapes;
                if (s == null) shapes = s = load();
            }
        }
        return s;
    }

    private int ground() {
        return floorY + 1;
    }

    // ── generation ───────────────────────────────────────────────────────────────────────────────

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        int minY = chunkData.getMinHeight(), maxY = chunkData.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunkData.setBlock(x, minY, z, Material.BEDROCK);
                for (int y = minY + 1; y < floorY; y++) chunkData.setBlock(x, y, z, Material.GRAY_CONCRETE);
                boolean dark = ((Math.floorDiv(x0 + x, CHECK) + Math.floorDiv(z0 + z, CHECK)) & 1) != 0;
                chunkData.setBlock(x, floorY, z, dark ? Material.LIGHT_GRAY_CONCRETE : Material.WHITE_CONCRETE);
            }
        }

        long seed = worldInfo.getSeed();
        int cx = Math.floorDiv(x0, CELL), cz = Math.floorDiv(z0, CELL);
        SuperCell sc = superCell(seed, Math.floorDiv(cx, SUPER), Math.floorDiv(cz, SUPER));
        List<Placement> pieces = new ArrayList<>(cell(seed, cx, cz, sc));
        if (sc.giant() != null) pieces.add(sc.giant());

        for (Placement p : pieces) {
            switch (p.kind()) {
                case SHAPE -> writeShape(p, x0, z0, minY, maxY, chunkData);
                case CORNELL -> writeCornell(p, x0, z0, chunkData);
                case MINECRAFT -> writeMinecraftCube(p, x0, z0, chunkData);
            }
        }
    }

    private void writeShape(Placement p, int x0, int z0, int minY, int maxY, ChunkData chunkData) {
        VoxelGrid g = shapes().grid(p.shape(), p.size(), p.yaw(), p.flip(), p.shell());
        int gx = p.px() + g.ox, gz = p.pz() + g.oz;   // world coords of grid (0, 0)
        int ix0 = Math.max(0, x0 - gx), ix1 = Math.min(g.sx, x0 + 16 - gx);
        int iz0 = Math.max(0, z0 - gz), iz1 = Math.min(g.sz, z0 + 16 - gz);
        for (int ix = ix0; ix < ix1; ix++) {
            for (int iz = iz0; iz < iz1; iz++) {
                for (int iy = 0; iy < g.sy; iy++) {
                    int y = p.baseY() + iy;
                    if (y <= minY || y >= maxY || !g.get(ix, iy, iz)) continue;
                    chunkData.setBlock(gx + ix - x0, y, gz + iz - z0, p.block());
                }
            }
        }
    }

    /**
     * The Cornell box: white floor, ceiling and back wall, red left wall, green right wall, a light
     * panel in the ceiling, open at the front. Local (u, w) is (left→right, front→back) before the
     * room's quarter turn.
     */
    private void writeCornell(Placement p, int x0, int z0, ChunkData chunkData) {
        int s = p.size(), base = p.baseY();
        int light0 = s / 2 - s / 6, light1 = s / 2 + s / 6;
        for (int x = Math.max(x0, p.px()); x < Math.min(x0 + 16, p.px() + s); x++) {
            for (int z = Math.max(z0, p.pz()); z < Math.min(z0 + 16, p.pz() + s); z++) {
                int[] uw = toRoom(x - p.px(), z - p.pz(), s, p.yaw());
                int u = uw[0], w = uw[1];
                for (int v = 0; v < s; v++) {
                    Material m;
                    if (v == s - 1) {
                        m = u >= light0 && u <= light1 && w >= light0 && w <= light1
                                ? Material.SEA_LANTERN : Material.WHITE_CONCRETE;
                    } else if (u == 0) {
                        m = Material.RED_CONCRETE;
                    } else if (u == s - 1) {
                        m = Material.GREEN_CONCRETE;
                    } else if (v == 0 || w == s - 1) {
                        m = Material.WHITE_CONCRETE;
                    } else {
                        continue;
                    }
                    chunkData.setBlock(x - x0, base + v, z - z0, m);
                }
            }
        }
    }

    /** World offset (a, b) in an s×s room → room-local (u, w) for quarter turn q. */
    private static int[] toRoom(int a, int b, int s, int q) {
        return switch (q & 3) {
            case 1 -> new int[]{b, s - 1 - a};
            case 2 -> new int[]{s - 1 - a, s - 1 - b};
            case 3 -> new int[]{s - 1 - b, a};
            default -> new int[]{a, b};
        };
    }

    /** Room-local (u, w) → world offset (a, b); the inverse of {@link #toRoom}. */
    private static int[] fromRoom(int u, int w, int s, int q) {
        return switch (q & 3) {
            case 1 -> new int[]{s - 1 - w, u};
            case 2 -> new int[]{s - 1 - u, s - 1 - w};
            case 3 -> new int[]{w, s - 1 - u};
            default -> new int[]{u, w};
        };
    }

    /** The one real thing: stone, a layer of dirt on top, and a single oak log. */
    private void writeMinecraftCube(Placement p, int x0, int z0, ChunkData chunkData) {
        int s = p.size(), mid = s / 2;
        for (int x = Math.max(x0, p.px()); x < Math.min(x0 + 16, p.px() + s); x++) {
            for (int z = Math.max(z0, p.pz()); z < Math.min(z0 + 16, p.pz() + s); z++) {
                for (int v = 0; v < s; v++) {
                    chunkData.setBlock(x - x0, p.baseY() + v, z - z0, v == s - 1 ? Material.DIRT : Material.STONE);
                }
                if (x - p.px() == mid && z - p.pz() == mid) {
                    chunkData.setBlock(x - x0, p.baseY() + s, z - z0, Material.OAK_LOG);
                }
            }
        }
    }

    // ── layout ───────────────────────────────────────────────────────────────────────────────────

    /** The contents of small cell (cx, cz): nothing, one shape, a Cornell box, or the Minecraft cube. */
    private List<Placement> cell(long seed, int cx, int cz, SuperCell sc) {
        int x0 = cx * CELL, z0 = cz * CELL;
        int ground = ground();
        if (cx == sc.mcCellX() && cz == sc.mcCellZ()) {
            SplittableRandom r = rng(seed, cx, cz, 3);
            int off = MARGIN + r.nextInt(CELL - 2 * MARGIN - MC_CUBE);
            int off2 = MARGIN + r.nextInt(CELL - 2 * MARGIN - MC_CUBE);
            return List.of(new Placement(Kind.MINECRAFT, null, MC_CUBE, 0, false, false,
                    x0 + off, ground, z0 + off2, Material.STONE));
        }
        if (sc.giant() != null && covers(sc.giant(), x0, z0, CELL, 4)) return List.of();

        ShapeLibrary lib = shapes();
        if (cx == 0 && cz == 0) {   // the first thing you see: a red teapot
            return List.of(new Placement(Kind.SHAPE, Shape.TEAPOT, 14, 6, false, true,
                    x0 + CELL / 2, ground, z0 + CELL / 2, Material.RED_CONCRETE));
        }

        SplittableRandom r = rng(seed, cx, cz, 1);
        if (r.nextDouble() < emptyChance) return List.of();
        int half = CELL / 2 - MARGIN;

        if (r.nextDouble() < cornellChance) {
            int s = 15 + 2 * r.nextInt(7);   // odd sides 15..27
            int q = r.nextInt(4);
            int rx = x0 + MARGIN + r.nextInt(CELL - 2 * MARGIN - s + 1);
            int rz = z0 + MARGIN + r.nextInt(CELL - 2 * MARGIN - s + 1);
            List<Placement> out = new ArrayList<>();
            out.add(new Placement(Kind.CORNELL, null, s, q, false, false, rx, ground, rz, Material.WHITE_CONCRETE));
            if (r.nextDouble() < 0.75) {
                out.add(roomPiece(Shape.TALL_BOX, (int) (s * 0.55), 1, s * 0.33, s * 0.62, s, q, rx, rz));
                out.add(roomPiece(Shape.CUBE, (int) (s * 0.28), -1, s * 0.66, s * 0.35, s, q, rx, rz));
            } else {
                Shape guest = r.nextBoolean() ? Shape.TEAPOT : Shape.BUNNY;
                out.add(roomPiece(guest, (int) (s * 0.4), r.nextInt(ShapeLibrary.YAW_STEPS),
                        s * 0.5, s * 0.5, s, q, rx, rz));
            }
            return out;
        }

        Shape shape = pick(SMALL, r);
        double radius = lib.radius(shape);
        int maxH = Math.min(shape == Shape.LUCY ? 40 : 30, (int) ((half - 1) / radius));
        int minH = Math.min(maxH, minHeight(shape));
        int h = minH + r.nextInt(maxH - minH + 1);
        int rb = (int) Math.ceil(radius * h) + 1;
        int px = x0 + CELL / 2 + jitter(r, half - rb);
        int pz = z0 + CELL / 2 + jitter(r, half - rb);
        int yaw = r.nextInt(ShapeLibrary.YAW_STEPS);
        boolean teapot = shape == Shape.TEAPOT;
        Material color = teapot ? TEAPOT_COLORS[r.nextInt(TEAPOT_COLORS.length)] : COLORS[r.nextInt(COLORS.length)];

        boolean flip = false;
        int base;
        double pose = r.nextDouble();
        if (pose < 0.5) {
            base = ground;
        } else if (pose < 0.65) {
            base = ground - (int) (h * (0.2 + 0.25 * r.nextDouble()));
        } else {
            flip = pose >= 0.9;
            base = ground + 4 + r.nextInt(Math.max(1, Math.min(40, worldTop() - ground - h - 4)));
        }
        return List.of(new Placement(Kind.SHAPE, shape, h, yaw, flip, teapot, px, base, pz, color));
    }

    /** Smallest height at which the shape still reads as itself. */
    private static int minHeight(Shape shape) {
        return switch (shape) {
            case LUCY -> 18;
            case ARMADILLO -> 10;
            case DRAGON, TEAPOT -> 8;
            default -> 6;
        };
    }

    /** A white shape inside a Cornell room, at room-local (u, w), turned with the room. */
    private Placement roomPiece(Shape shape, int h, int yaw, double u, double w, int s, int q, int rx, int rz) {
        int[] ab = fromRoom((int) u, (int) w, s, q);
        return new Placement(Kind.SHAPE, shape, Math.max(3, h), yaw + q * ShapeLibrary.YAW_STEPS / 4,
                false, false, rx + ab[0], ground() + 1, rz + ab[1], Material.WHITE_CONCRETE);
    }

    /** Super-cell (gx, gz): which cell holds the Minecraft cube, and maybe a giant. */
    private SuperCell superCell(long seed, int gx, int gz) {
        SplittableRandom r = rng(seed, gx, gz, 2);
        int mcx = gx * SUPER + r.nextInt(SUPER), mcz = gz * SUPER + r.nextInt(SUPER);
        if (mcx == 0 && mcz == 0) mcx += 2;   // the spawn cell has its teapot

        Placement giant = null;
        if (r.nextDouble() < giantChance) {
            ShapeLibrary lib = shapes();
            Shape shape = pick(GIANT, r);
            double radius = lib.radius(shape);
            int span = SUPER * CELL;
            int ground = ground();
            int maxH = Math.min(worldTop() - ground - 4, (int) ((span / 2.0 - 24) / radius));
            int minH = Math.min(60, maxH);
            int h = minH + r.nextInt(Math.max(1, maxH - minH + 1));
            int rb = (int) Math.ceil(radius * h) + 1;
            int px = gx * span + span / 2 + jitter(r, span / 2 - 8 - rb);
            int pz = gz * span + span / 2 + jitter(r, span / 2 - 8 - rb);
            int base = r.nextDouble() < 0.75 ? ground : ground - h / 5;
            Material color = shape == Shape.TEAPOT ? TEAPOT_COLORS[r.nextInt(TEAPOT_COLORS.length)]
                    : COLORS[r.nextInt(COLORS.length)];
            giant = new Placement(Kind.SHAPE, shape, h, r.nextInt(ShapeLibrary.YAW_STEPS), false,
                    shape == Shape.TEAPOT, px, base, pz, color);
            // keep spawn and the Minecraft cube clear
            if (covers(giant, -CELL, -CELL, 2 * CELL, 8) || covers(giant, mcx * CELL, mcz * CELL, CELL, 4)) {
                giant = null;
            }
        }
        return new SuperCell(giant, mcx, mcz);
    }

    /** Whether the giant's footprint circle, grown by pad, touches the square [x0, x0+size)². */
    private boolean covers(Placement giant, int x0, int z0, int size, int pad) {
        double r = shapes().radius(giant.shape()) * giant.size() + pad;
        double dx = Math.max(Math.max(x0 - giant.px(), 0), giant.px() - (x0 + size));
        double dz = Math.max(Math.max(z0 - giant.pz(), 0), giant.pz() - (z0 + size));
        return dx * dx + dz * dz < r * r;
    }

    private int worldTop() {
        return 252;
    }

    private static int jitter(SplittableRandom r, int range) {
        return range <= 0 ? 0 : r.nextInt(2 * range + 1) - range;
    }

    private static Shape pick(Object[][] pool, SplittableRandom r) {
        int total = 0;
        for (Object[] e : pool) total += (int) e[1];
        int roll = r.nextInt(total);
        for (Object[] e : pool) {
            roll -= (int) e[1];
            if (roll < 0) return (Shape) e[0];
        }
        return (Shape) pool[0][0];
    }

    private static SplittableRandom rng(long seed, int a, int b, int salt) {
        long h = seed ^ 0x418_7EA_907L;
        h = mix(h + a * 0x9E3779B97F4A7C15L);
        h = mix(h + b * 0xC2B2AE3D27D4EB4FL);
        return new SplittableRandom(mix(h + salt));
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public int getSpawnY() {
        return ground();
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, ground(), 0.5);
    }
}
