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
 * flat colored concrete. Utah teapots, Suzanne, the Stanford bunny / dragon / Lucy, primitives,
 * and Cornell boxes, upright or tumbled at random in 3D, none of them touching the floor. The
 * only real Minecraft in the level is a small stone-and-dirt cube with one oak log on top, sitting
 * on the floor like it obeys gravity; there is one per 384-block region, and breaking wood is the
 * way out.
 *
 * <p>Layout: each 384-block region is a quadtree. A node either holds one thing (a shape sized to
 * fill it, a Cornell box, or nothing) or splits into four, down to 48-block leaves. Nodes near
 * spawn almost always split; far away they split less, so things get bigger the further you go.
 * Detailed meshes need many blocks to read, so they only fit in big leaves. Leaves are multiples
 * of 16 and aligned, so each chunk lies in exactly one leaf. Everything is hashed from the world
 * seed and node coordinates; shapes come voxelized from {@link ShapeLibrary}.
 */
public class Level418ChunkGenerator extends BackroomsChunkGenerator {

    private static final Logger LOG = Logger.getLogger("Backrooms");

    private static final int REGION = 384;
    private static final int LEAF = 48;                // smallest quadtree node
    private static final int MARGIN = 2;               // blocks kept clear at each leaf edge
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

    // weighted pool: {shape, weight}. Shapes too detailed for a leaf are re-picked.
    private static final Object[][] POOL = {
            {Shape.TEAPOT, 30}, {Shape.SUZANNE, 10}, {Shape.BUNNY, 10}, {Shape.DRAGON, 8},
            {Shape.LUCY, 8}, {Shape.CUBE, 8}, {Shape.PYRAMID, 6}, {Shape.CONE, 6},
            {Shape.SPHERE, 8}, {Shape.TORUS, 8},
    };
    private static final double TUMBLE = 0.6;          // chance a shape is rotated in 3D

    // chance a node of 384 / 192 / 96 blocks splits, near spawn and from far_distance on
    private static final double[] SPLIT_NEAR = {0.97, 0.9, 0.75};
    private static final double[] SPLIT_FAR = {0.45, 0.45, 0.45};

    private int floorY = 8;
    private double emptyChance = 0.3;
    private double cornellChance = 0.08;
    private double farDistance = 2500;

    private volatile ShapeLibrary shapes;

    /** One thing placed on the plane. Shapes use the grid; the room and the cube are built by hand. */
    private enum Kind { SHAPE, CORNELL, MINECRAFT }

    /**
     * (px, pz) is the shape pivot, or the min corner for the room and the cube. size is the shape
     * size, the room side or the cube side; orient is a {@link ShapeLibrary} orientation, or quarter
     * turns for the room.
     */
    private record Placement(Kind kind, Shape shape, int size, int orient, boolean shell,
                             int px, int baseY, int pz, Material block) {}

    /** A quadtree leaf: min corner and side, in blocks. */
    private record Leaf(int x0, int z0, int size) {}

    public Level418ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config != null) {
            floorY = config.getInt("floor_y", floorY);
            emptyChance = config.getDouble("empty_chance", emptyChance);
            cornellChance = config.getDouble("cornell_chance", cornellChance);
            farDistance = config.getDouble("far_distance", farDistance);
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
        for (Placement p : contents(seed, leaf(seed, x0, z0))) {
            switch (p.kind()) {
                case SHAPE -> writeShape(p, x0, z0, minY, maxY, chunkData);
                case CORNELL -> writeCornell(p, x0, z0, chunkData);
                case MINECRAFT -> writeMinecraftCube(p, x0, z0, chunkData);
            }
        }
    }

    private void writeShape(Placement p, int x0, int z0, int minY, int maxY, ChunkData chunkData) {
        VoxelGrid g = shapes().grid(p.shape(), p.size(), p.orient(), p.shell());
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
     * panel in the ceiling, open at the front. Walls and ceiling are two blocks thick so the colors
     * and the light only show inside; outside it is a plain white box. Local (u, w) is
     * (left→right, front→back) before the room's quarter turn.
     */
    private void writeCornell(Placement p, int x0, int z0, ChunkData chunkData) {
        int s = p.size(), base = p.baseY();
        int light0 = s / 2 - s / 6, light1 = s / 2 + s / 6;
        for (int x = Math.max(x0, p.px()); x < Math.min(x0 + 16, p.px() + s); x++) {
            for (int z = Math.max(z0, p.pz()); z < Math.min(z0 + 16, p.pz() + s); z++) {
                int[] uw = toRoom(x - p.px(), z - p.pz(), s, p.orient());
                int u = uw[0], w = uw[1];
                for (int v = 0; v < s; v++) {
                    Material m;
                    if (u == 0 || u == s - 1 || v == s - 1 || w == s - 1 || v == 0) {
                        m = Material.WHITE_CONCRETE;                 // outer shell
                    } else if (v == s - 2) {
                        m = u >= light0 && u <= light1 && w >= light0 && w <= light1
                                ? Material.SEA_LANTERN : Material.WHITE_CONCRETE;
                    } else if (u == 1) {
                        m = Material.RED_CONCRETE;
                    } else if (u == s - 2) {
                        m = Material.GREEN_CONCRETE;
                    } else if (w == s - 2) {
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

    /** The quadtree leaf containing block (x, z). */
    private Leaf leaf(long seed, int x, int z) {
        int rx = Math.floorDiv(x, REGION), rz = Math.floorDiv(z, REGION);
        int[] mc = minecraftCell(seed, rx, rz);
        int nx = rx * REGION, nz = rz * REGION, size = REGION;
        for (int depth = 0; size > LEAF; depth++) {
            boolean split;
            if (holds(nx, nz, size, mc[0] * LEAF, mc[1] * LEAF) || holds(nx, nz, size, 0, 0)) {
                split = true;   // the Minecraft cube and the spawn teapot each get a smallest leaf
            } else {
                double cx = nx + size / 2.0, cz = nz + size / 2.0;
                double t = Math.min(1, Math.hypot(cx, cz) / farDistance);
                double chance = SPLIT_NEAR[depth] + (SPLIT_FAR[depth] - SPLIT_NEAR[depth]) * t;
                split = rng(seed, nx, nz, 10 + depth).nextDouble() < chance;
            }
            if (!split) break;
            size /= 2;
            if (x >= nx + size) nx += size;
            if (z >= nz + size) nz += size;
        }
        return new Leaf(nx, nz, size);
    }

    private static boolean holds(int nx, int nz, int size, int x, int z) {
        return x >= nx && x < nx + size && z >= nz && z < nz + size;
    }

    /** The smallest-leaf cell (in LEAF units) of region (rx, rz) that holds its Minecraft cube. */
    private static int[] minecraftCell(long seed, int rx, int rz) {
        SplittableRandom r = rng(seed, rx, rz, 2);
        int n = REGION / LEAF;
        int cx = rx * n + r.nextInt(n), cz = rz * n + r.nextInt(n);
        if (cx == 0 && cz == 0) cx += 2;   // the spawn leaf has its teapot
        return new int[]{cx, cz};
    }

    /** What a leaf holds: nothing, one shape, a Cornell box, or the Minecraft cube. */
    private List<Placement> contents(long seed, Leaf leaf) {
        int x0 = leaf.x0(), z0 = leaf.z0(), side = leaf.size();
        int ground = ground();
        if (side == LEAF) {
            int[] mc = minecraftCell(seed, Math.floorDiv(x0, REGION), Math.floorDiv(z0, REGION));
            if (x0 == mc[0] * LEAF && z0 == mc[1] * LEAF) {
                SplittableRandom r = rng(seed, x0, z0, 3);
                int off = MARGIN + r.nextInt(LEAF - 2 * MARGIN - MC_CUBE);
                int off2 = MARGIN + r.nextInt(LEAF - 2 * MARGIN - MC_CUBE);
                return List.of(new Placement(Kind.MINECRAFT, null, MC_CUBE, 0, false,
                        x0 + off, ground, z0 + off2, Material.STONE));
            }
            if (x0 == 0 && z0 == 0) {   // the first thing you see: a red teapot
                return List.of(new Placement(Kind.SHAPE, Shape.TEAPOT, 18, 6, true,
                        LEAF / 2, ground + 1, LEAF / 2, Material.RED_CONCRETE));
            }
        }

        SplittableRandom r = rng(seed, x0, z0, 1);
        if (r.nextDouble() < (side == LEAF ? emptyChance : emptyChance / 3)) return List.of();
        int half = side / 2 - MARGIN;

        if (side <= 2 * LEAF && r.nextDouble() < cornellChance) {
            int s = 19 + 2 * r.nextInt((Math.min(side - 2 * MARGIN, 47) - 19) / 2 + 1);   // odd sides
            int q = r.nextInt(4);
            int rx = x0 + MARGIN + r.nextInt(side - 2 * MARGIN - s + 1);
            int rz = z0 + MARGIN + r.nextInt(side - 2 * MARGIN - s + 1);
            int base = ground + 1;
            List<Placement> out = new ArrayList<>();
            out.add(new Placement(Kind.CORNELL, null, s, q, false, rx, base, rz, Material.WHITE_CONCRETE));
            if (r.nextDouble() < 0.75) {
                out.add(roomPiece(Shape.TALL_BOX, (int) (s * 0.55), 1, s * 0.33, s * 0.62, s, q, rx, rz, base));
                out.add(roomPiece(Shape.CUBE, (int) (s * 0.28), -1, s * 0.66, s * 0.35, s, q, rx, rz, base));
            } else {
                Shape guest = r.nextBoolean() ? Shape.TEAPOT : Shape.BUNNY;
                out.add(roomPiece(guest, (int) (s * 0.4), r.nextInt(ShapeLibrary.UPRIGHT),
                        s * 0.5, s * 0.5, s, q, rx, rz, base));
            }
            return out;
        }

        // pick a shape and orientation that fit the leaf at the shape's minimum size
        ShapeLibrary lib = shapes();
        int maxTop = worldTop() - ground - 2;
        Shape shape = Shape.CUBE;
        int orient = 0, minSize = 0, maxSize = 0;
        for (int tries = 0; tries < 8; tries++) {
            shape = tries < 7 ? pick(POOL, r) : Shape.SPHERE;
            orient = r.nextDouble() < TUMBLE
                    ? ShapeLibrary.UPRIGHT + r.nextInt(ShapeLibrary.ORIENTATIONS - ShapeLibrary.UPRIGHT)
                    : r.nextInt(ShapeLibrary.UPRIGHT);
            maxSize = (int) Math.min((half - 1) / lib.footprint(shape, orient), maxTop / lib.height(shape, orient));
            minSize = minSize(shape);
            if (maxSize >= minSize) break;
        }
        if (maxSize < minSize) return List.of();
        // lean large: the leaf is the size class, the shape mostly fills it
        int size = minSize + (int) ((maxSize - minSize) * (0.35 + 0.65 * r.nextDouble()));
        int rb = (int) Math.ceil(lib.footprint(shape, orient) * size) + 1;
        int px = x0 + side / 2 + jitter(r, half - rb);
        int pz = z0 + side / 2 + jitter(r, half - rb);
        boolean teapot = shape == Shape.TEAPOT;
        Material color = teapot ? TEAPOT_COLORS[r.nextInt(TEAPOT_COLORS.length)] : COLORS[r.nextInt(COLORS.length)];

        // never on the floor: hover a block or three, or float well up
        int h = (int) Math.ceil(lib.height(shape, orient) * size);
        int room = worldTop() - ground - h - 1;
        int base = r.nextDouble() < 0.6 || room < 8
                ? ground + 1 + r.nextInt(Math.max(1, Math.min(3, room)))
                : ground + 4 + r.nextInt(Math.min(60, room - 4));
        return List.of(new Placement(Kind.SHAPE, shape, size, orient, teapot, px, base, pz, color));
    }

    /** Smallest size at which the shape still reads as itself: detailed meshes need more blocks. */
    private static int minSize(Shape shape) {
        return switch (shape) {
            case LUCY -> 90;
            case DRAGON -> 50;
            case BUNNY -> 24;
            case SUZANNE, TEAPOT -> 16;
            default -> 6;
        };
    }

    /** A white shape inside a Cornell room, at room-local (u, w), turned with the room. */
    private Placement roomPiece(Shape shape, int h, int yaw, double u, double w, int s, int q, int rx, int rz, int base) {
        int[] ab = fromRoom((int) u, (int) w, s, q);
        int orient = Math.floorMod(yaw + q * ShapeLibrary.UPRIGHT / 4, ShapeLibrary.UPRIGHT);
        return new Placement(Kind.SHAPE, shape, Math.max(3, h), orient,
                false, rx + ab[0], base + 1, rz + ab[1], Material.WHITE_CONCRETE);
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
