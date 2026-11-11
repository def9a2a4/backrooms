package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Level 974 — "Planetoids". Black space scattered with small spherical planets and lumpy asteroids,
 * after the 2011 Planetoids world type.
 *
 * <p>Space is cut into 40-block cubic cells. Each cell independently (hashed from the world seed and
 * the cell coordinate) holds nothing, one planet, or a cluster of asteroids, always fully inside the
 * cell — so bodies never overlap and a chunk only needs to look at the cells it touches. A
 * low-frequency noise over cell coordinates empties out large regions entirely.
 *
 * <p>Planets are perfect spheres layered from shell to core; see {@link PlanetPalettes} for the
 * materials. Asteroids are small, rare, noise-roughened, and rich in good ores.
 *
 * <p>The cell at (0, 3, 0) is always a grass planet centered on (0, 120, 0); spawn is on its top.
 */
public class Level974ChunkGenerator extends BackroomsChunkGenerator {

    private static final int CELL = 40;
    private static final int HALF = CELL / 2;
    private static final int MARGIN = 2;
    /** Cell layers in y: cy 1..5 covers y 20..220, inside the 0..256 build range. */
    private static final int CY_MIN = 1;
    private static final int CY_MAX = 5;

    private static final int SPAWN_CY = 3;
    private static final int SPAWN_R = 12;
    private static final int SPAWN_CENTER_Y = SPAWN_CY * CELL;

    private static final int ASTEROID_LUMP = 2;   // max outward bulge beyond r from the noise

    /**
     * Large empty regions: a low-frequency noise over cell coordinates; cells where it falls below
     * VOID_THRESHOLD hold nothing. Features span roughly 10 cells (~400 blocks). Cells within
     * SPAWN_CLEAR of the spawn cell ignore it, so spawn always has neighbours in view.
     */
    private static final double VOID_FREQ_XZ = 0.09;
    private static final double VOID_FREQ_Y = 0.2;
    private static final double VOID_THRESHOLD = -0.05;
    private static final int SPAWN_CLEAR = 2;
    private static final long VOID_SALT = 0x5A17E5L;

    private static final Material[] ASTEROID_ORES = {
            Material.IRON_ORE, Material.IRON_ORE, Material.IRON_ORE, Material.IRON_ORE, Material.IRON_ORE,
            Material.GOLD_ORE, Material.GOLD_ORE, Material.GOLD_ORE, Material.GOLD_ORE,
            Material.DIAMOND_ORE, Material.DIAMOND_ORE, Material.DIAMOND_ORE, Material.DIAMOND_ORE,
            Material.EMERALD_ORE, Material.EMERALD_ORE,
            Material.REDSTONE_ORE, Material.REDSTONE_ORE,
            Material.LAPIS_ORE, Material.LAPIS_ORE,
            Material.COAL_ORE,
    };

    private static final Material[] ASTEROID_ROCK = {
            Material.STONE, Material.ANDESITE, Material.TUFF, Material.COBBLESTONE,
    };

    private interface Body {
        /** Half-width of the axis-aligned box that contains every block of this body. */
        int extent();
        int x();
        int y();
        int z();
        void place(ChunkData chunkData, int localX, int wx, int wy, int localZ, int wz, long seed);
    }

    private record Planet(int x, int y, int z, int r, PlanetPalettes.Palette palette, long hash) implements Body {
        @Override public int extent() { return r; }

        @Override
        public void place(ChunkData chunkData, int localX, int wx, int wy, int localZ, int wz, long seed) {
            int dx = wx - x, dy = wy - y, dz = wz - z;
            double rr = r + 0.5;
            double outer = rr * rr;
            int d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > outer) return;
            boolean top = dx * dx + (dy + 1) * (dy + 1) + dz * dz > outer;
            BlockData b = palette.picker().pick(Math.sqrt(d2) / rr, top, blockHash(hash, wx, wy, wz), wx, wy, wz, hash);
            if (b != null) chunkData.setBlock(localX, wy, localZ, b);
        }
    }

    private record Asteroid(int x, int y, int z, int r, Material rock, long hash) implements Body {
        @Override public int extent() { return r + ASTEROID_LUMP; }

        @Override
        public void place(ChunkData chunkData, int localX, int wx, int wy, int localZ, int wz, long seed) {
            int dx = wx - x, dy = wy - y, dz = wz - z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double lump = ASTEROID_LUMP * SimplexNoise.noise3(seed ^ hash, wx * 0.3, wy * 0.3, wz * 0.3);
            if (d > r + 0.5 + lump) return;
            long h = blockHash(hash, wx, wy, wz);
            Material m = (h & 0xFF) < 64 ? ASTEROID_ORES[(int) ((h >>> 8) & 0x7FFFFFFF) % ASTEROID_ORES.length] : rock;
            chunkData.setBlock(localX, wy, localZ, m);
        }
    }

    public Level974ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
        PlanetPalettes.ALL.size();   // build the palette BlockData on the main thread, not a chunk worker
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        int minY = chunkData.getMinHeight();
        int maxY = chunkData.getMaxHeight();

        int cxMin = Math.floorDiv(baseX + HALF, CELL);
        int cxMax = Math.floorDiv(baseX + 15 + HALF, CELL);
        int czMin = Math.floorDiv(baseZ + HALF, CELL);
        int czMax = Math.floorDiv(baseZ + 15 + HALF, CELL);

        List<Body> bodies = new ArrayList<>();
        for (int cx = cxMin; cx <= cxMax; cx++) {
            for (int cz = czMin; cz <= czMax; cz++) {
                for (int cy = CY_MIN; cy <= CY_MAX; cy++) {
                    bodies.clear();
                    cellBodies(seed, cx, cy, cz, bodies);
                    for (Body b : bodies) {
                        placeBody(b, chunkData, baseX, baseZ, minY, maxY, seed);
                    }
                }
            }
        }
    }

    private static void placeBody(Body b, ChunkData chunkData, int baseX, int baseZ, int minY, int maxY, long seed) {
        int e = b.extent();
        int x0 = Math.max(baseX, b.x() - e), x1 = Math.min(baseX + 15, b.x() + e);
        int z0 = Math.max(baseZ, b.z() - e), z1 = Math.min(baseZ + 15, b.z() + e);
        int y0 = Math.max(minY, b.y() - e), y1 = Math.min(maxY - 1, b.y() + e);
        if (x0 > x1 || z0 > z1 || y0 > y1) return;
        for (int wx = x0; wx <= x1; wx++) {
            for (int wz = z0; wz <= z1; wz++) {
                for (int wy = y0; wy <= y1; wy++) {
                    b.place(chunkData, wx - baseX, wx, wy, wz - baseZ, wz, seed);
                }
            }
        }
    }

    /** Deterministically fills {@code out} with the bodies in cell (cx, cy, cz). */
    private static void cellBodies(long seed, int cx, int cy, int cz, List<Body> out) {
        long hash = cellHash(seed, cx, cy, cz);
        if (cx == 0 && cy == SPAWN_CY && cz == 0) {
            out.add(new Planet(0, SPAWN_CENTER_Y, 0, SPAWN_R, PlanetPalettes.GRASS, hash));
            return;
        }
        boolean nearSpawn = Math.abs(cx) <= SPAWN_CLEAR && Math.abs(cz) <= SPAWN_CLEAR;
        if (!nearSpawn && SimplexNoise.noise3(seed ^ VOID_SALT,
                cx * VOID_FREQ_XZ, cy * VOID_FREQ_Y, cz * VOID_FREQ_XZ) < VOID_THRESHOLD) {
            return;   // inside a void region
        }
        Random rng = new Random(hash);
        int roll = rng.nextInt(100);
        if (roll < 35) return;   // empty

        if (roll < 92) {
            PlanetPalettes.Palette p = PlanetPalettes.pick(rng);
            int r = p.rMin() + rng.nextInt(p.rMax() - p.rMin() + 1);
            out.add(new Planet(
                    jitter(rng, cx, r), jitter(rng, cy, r), jitter(rng, cz, r), r, p, hash));
            return;
        }

        int count = 1 + rng.nextInt(3);
        for (int i = 0; i < count; i++) {
            int r = 2 + rng.nextInt(4);
            int ext = r + ASTEROID_LUMP;
            Material rock = ASTEROID_ROCK[rng.nextInt(ASTEROID_ROCK.length)];
            out.add(new Asteroid(
                    jitter(rng, cx, ext), jitter(rng, cy, ext), jitter(rng, cz, ext), r, rock, mix(hash + i + 1)));
        }
    }

    /** A center coordinate in cell {@code c} such that center ± extent stays inside the cell with margin. */
    private static int jitter(Random rng, int c, int extent) {
        int slack = HALF - extent - MARGIN;
        if (slack <= 0) return c * CELL;
        return c * CELL - slack + rng.nextInt(2 * slack + 1);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long cellHash(long seed, int x, int y, int z) {
        return mix(seed ^ mix(x * 0x9E3779B97F4A7C15L ^ mix(y * 0xC2B2AE3D27D4EB4FL ^ mix(z))));
    }

    private static long blockHash(long hash, int x, int y, int z) {
        return mix(hash ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L));
    }

    @Override
    public int getSpawnY() { return SPAWN_CENTER_Y; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, SPAWN_CENTER_Y + SPAWN_R + 1, 0.5);
    }
}
