package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.PerlinOctaveGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Level 95 — "Skylands". Floating islands in a bright sky, after the Aether mod: teal grass (from
 * the {@code backrooms:skylands} biome) over holystone, skyroot and golden oak trees, and white
 * aerclouds of stained glass drifting between the islands.
 *
 * <p>The islands are 3D Perlin noise, squashed vertically so they come out as flattish slabs, and
 * faded out above and below a band of sky so there is open air over and under them. Density is
 * sampled every 4 blocks on a world-aligned grid and interpolated between, so any column can be
 * recomputed exactly from outside its chunk (trees, spawn).
 *
 * <p>Aether blocks in vanilla: holystone is diorite and calcite with mossy cobblestone patches,
 * quicksoil is sand on thin island edges, ambrosium is glowstone, zanite is amethyst, gravitite is
 * crying obsidian (in island undersides only), icestone is packed ice (high islands only).
 *
 * <p>A solid blob is always added around (0, SPAWN_Y, 0); spawn is on top of its island.
 */
public class Level95ChunkGenerator extends BackroomsChunkGenerator {

    /** Density grid: samples every GRID blocks in x, y and z, from Y0 to Y1 inclusive. */
    private static final int GRID = 4;
    private static final int Y0 = 32;
    private static final int Y1 = 240;
    private static final int NY = (Y1 - Y0) / GRID + 1;

    /** Islands live between these heights, fading out over FADE blocks at each end. */
    private static final int ISLAND_LO = 40;
    private static final int ISLAND_HI = 232;
    private static final int FADE = 36;

    /** Perlin scale: wide in x/z, squashed in y, so islands are flatter than they are wide. */
    private static final double SCALE_XZ = 1.0 / 90;
    private static final double SCALE_Y = 1.0 / 38;
    private static final int OCTAVES = 3;
    /** Solid where density exceeds this. */
    private static final double THRESHOLD = 0.2;

    private static final int SPAWN_Y = 128;
    private static final double SPAWN_R = 30;
    private static final double SPAWN_BONUS = 1.2;
    /** No trees this close to the origin. */
    private static final int SPAWN_CLEAR = 4;

    /** Above this height the holystone carries icestone. */
    private static final int ICE_Y = 185;

    /** Islands thinner than this under their surface are quicksoil at the top. */
    private static final int QUICKSOIL_THIN = 5;

    /** Ore odds per 1000 holystone blocks. */
    private static final int AMBROSIUM = 7;
    private static final int ZANITE = 3;
    private static final int GRAVITITE = 2;

    /**
     * Aercloud layers: flattened 3D noise around these heights. Each higher layer needs a higher
     * noise value, so clouds thin out with altitude.
     */
    private static final int[] CLOUD_BANDS = {30, 92, 150, 205};
    private static final double[] CLOUD_THRESHOLDS = {0.56, 0.6, 0.66, 0.72};
    private static final int CLOUD_HALF = 6;
    private static final long CLOUD_SALT = 0xC10D5L;

    /** Trees: one candidate column per TREE_CELL square. */
    private static final int TREE_CELL = 7;
    private static final int TREE_R = 2;
    private static final long TREE_SALT = 0x7EE5L;

    private static final long STONE_SALT = 0x5707EL;

    private static final Material[] FLOWERS = {
            Material.WHITE_TULIP, Material.AZURE_BLUET, Material.LILY_OF_THE_VALLEY, Material.OXEYE_DAISY,
    };

    private volatile PerlinOctaveGenerator perlin;
    private volatile long perlinSeed;

    public Level95ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    private PerlinOctaveGenerator perlin(long seed) {
        PerlinOctaveGenerator p = perlin;
        if (p != null && perlinSeed == seed) return p;
        synchronized (this) {
            if (perlin == null || perlinSeed != seed) {
                PerlinOctaveGenerator g = new PerlinOctaveGenerator(seed, OCTAVES);
                g.setXScale(SCALE_XZ);
                g.setYScale(SCALE_Y);
                g.setZScale(SCALE_XZ);
                perlinSeed = seed;
                perlin = g;
            }
            return perlin;
        }
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        Map<Long, double[]> gridCache = new HashMap<>();

        // Solid blocks for the chunk plus a one-column border, so clouds can keep clear of islands.
        boolean[][][] solid = new boolean[18][18][];
        for (int x = 0; x < 18; x++) {
            for (int z = 0; z < 18; z++) {
                solid[x][z] = solidColumn(seed, baseX + x - 1, baseZ + z - 1, gridCache);
            }
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                fillColumn(chunkData, x, z, baseX + x, baseZ + z, solid[x + 1][z + 1], seed);
                placeClouds(chunkData, x, z, baseX + x, baseZ + z, solid, seed);
            }
        }

        placeTrees(chunkData, baseX, baseZ, seed, gridCache);
    }

    // ---- density -------------------------------------------------------------------------

    /** Raw density at one grid point. */
    private double density(long seed, int wx, int wy, int wz) {
        double d = perlin(seed).noise(wx, wy, wz, 2.0, 0.5, true);
        if (wy < ISLAND_LO + FADE) {
            double t = Math.max(0, (double) (ISLAND_LO + FADE - wy) / FADE);
            d -= 0.9 * t * t;
        }
        if (wy > ISLAND_HI - FADE) {
            double t = Math.max(0, (double) (wy - (ISLAND_HI - FADE)) / FADE);
            d -= 0.9 * t * t;
        }
        double dy = (wy - SPAWN_Y) * 2.0;   // the spawn blob is flattened too
        double r = Math.sqrt((double) wx * wx + dy * dy + (double) wz * wz);
        if (r < SPAWN_R) d += SPAWN_BONUS * (1 - r / SPAWN_R);
        return d;
    }

    /** Densities at grid column (gx, gz) for grid rows Y0..Y1, cached per chunk. */
    private double[] gridColumn(long seed, int gx, int gz, Map<Long, double[]> cache) {
        long key = ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
        double[] col = cache.get(key);
        if (col == null) {
            col = new double[NY];
            for (int i = 0; i < NY; i++) col[i] = density(seed, gx * GRID, Y0 + i * GRID, gz * GRID);
            cache.put(key, col);
        }
        return col;
    }

    /**
     * Whether each block from Y0 to Y1 in column (wx, wz) is solid, trilinearly interpolated from
     * the grid. The same for a column whichever chunk asks.
     */
    private boolean[] solidColumn(long seed, int wx, int wz, Map<Long, double[]> cache) {
        int gx = Math.floorDiv(wx, GRID), gz = Math.floorDiv(wz, GRID);
        double fx = (wx - gx * GRID) / (double) GRID, fz = (wz - gz * GRID) / (double) GRID;
        double[] c00 = gridColumn(seed, gx, gz, cache), c10 = gridColumn(seed, gx + 1, gz, cache);
        double[] c01 = gridColumn(seed, gx, gz + 1, cache), c11 = gridColumn(seed, gx + 1, gz + 1, cache);
        double[] col = new double[NY];
        for (int i = 0; i < NY; i++) {
            double a = c00[i] + (c10[i] - c00[i]) * fx;
            double b = c01[i] + (c11[i] - c01[i]) * fx;
            col[i] = a + (b - a) * fz;
        }
        boolean[] out = new boolean[Y1 - Y0 + 1];
        for (int y = Y0; y <= Y1; y++) {
            int i = Math.min(NY - 2, (y - Y0) / GRID);
            double fy = (y - Y0 - i * GRID) / (double) GRID;
            out[y - Y0] = col[i] + (col[i + 1] - col[i]) * fy > THRESHOLD;
        }
        return out;
    }

    private static boolean isSolid(boolean[] col, int y) {
        return y >= Y0 && y <= Y1 && col[y - Y0];
    }

    // ---- islands -------------------------------------------------------------------------

    private static void fillColumn(ChunkData chunkData, int x, int z, int wx, int wz, boolean[] col, long seed) {
        // Solid run lengths: blocks of solid above (to the surface) and below (to the underside).
        int n = col.length;
        int[] below = new int[n];
        for (int i = 0; i < n; i++) below[i] = col[i] ? (i > 0 && col[i - 1] ? below[i - 1] + 1 : 0) : -1;

        long colHash = blockHash(seed, wx, 0, wz);
        int soil = 2 + (int) (colHash & 1);
        int above = -1;
        boolean thin = false;
        for (int i = n - 1; i >= 0; i--) {
            if (!col[i]) { above = -1; continue; }
            above++;
            int wy = Y0 + i;
            if (above == 0) thin = below[i] + 1 < QUICKSOIL_THIN;
            Material m;
            if (thin && above < 3) {
                m = Material.SAND;
            } else if (above == 0) {
                m = Material.GRASS_BLOCK;
            } else if (above <= soil) {
                m = Material.DIRT;
            } else {
                m = holystone(seed, wx, wy, wz, below[i] <= 2);
            }
            chunkData.setBlock(x, wy, z, m);

            if (above == 0 && m == Material.GRASS_BLOCK && wy + 1 <= Y1) {
                int roll = (int) ((blockHash(seed, wx, wy, wz) >>> 16) % 100);
                if (roll < 22) {
                    chunkData.setBlock(x, wy + 1, z, Material.SHORT_GRASS);
                } else if (roll < 26) {
                    chunkData.setBlock(x, wy + 1, z, FLOWERS[roll % FLOWERS.length]);
                }
            }
        }
    }

    private static Material holystone(long seed, int wx, int wy, int wz, boolean underside) {
        long h = blockHash(seed ^ STONE_SALT, wx, wy, wz);
        int ore = (int) ((h >>> 8) % 1000);
        if (ore < AMBROSIUM) return Material.GLOWSTONE;
        if (ore < AMBROSIUM + ZANITE) return Material.AMETHYST_BLOCK;
        if (underside && ore < AMBROSIUM + ZANITE + GRAVITITE) return Material.CRYING_OBSIDIAN;

        double patch = SimplexNoise.noise3(seed ^ STONE_SALT, wx * 0.08, wy * 0.08, wz * 0.08);
        if (wy >= ICE_Y && patch > 0.6) return Material.PACKED_ICE;
        if (patch < -0.55) return Material.MOSSY_COBBLESTONE;
        if (patch > 0.25) return Material.CALCITE;
        return Material.DIORITE;
    }

    // ---- aerclouds -----------------------------------------------------------------------

    private static void placeClouds(ChunkData chunkData, int x, int z, int wx, int wz, boolean[][][] solid, long seed) {
        int minY = chunkData.getMinHeight(), maxY = chunkData.getMaxHeight() - 1;
        for (int b = 0; b < CLOUD_BANDS.length; b++) {
            long bs = seed ^ CLOUD_SALT ^ (b * 0x9E3779B97F4A7C15L);
            int center = CLOUD_BANDS[b] + (int) Math.round(5 * SimplexNoise.noise2(bs, wx * 0.008, wz * 0.008));
            for (int wy = Math.max(minY, center - CLOUD_HALF); wy <= Math.min(maxY, center + CLOUD_HALF); wy++) {
                double falloff = Math.abs(wy - center) / (double) CLOUD_HALF;
                double v = SimplexNoise.noise3(bs, wx * 0.022, wy * 0.11, wz * 0.022) - 0.45 * falloff * falloff;
                if (v < CLOUD_THRESHOLDS[b]) continue;
                if (nearIsland(solid, x + 1, z + 1, wy)) continue;
                chunkData.setBlock(x, wy, z, Material.WHITE_STAINED_GLASS);
            }
        }
    }

    /** True if any island block is within one block of (sx, wy, sz) in the solid grid. */
    private static boolean nearIsland(boolean[][][] solid, int sx, int sz, int wy) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean[] col = solid[sx + dx][sz + dz];
                if (isSolid(col, wy - 1) || isSolid(col, wy) || isSolid(col, wy + 1)) return true;
            }
        }
        return false;
    }

    // ---- trees ---------------------------------------------------------------------------

    /**
     * Each TREE_CELL square has one root column; every grass surface in it with headroom may grow a
     * tree. A chunk visits every cell whose canopy could reach into it and recomputes the root
     * column, so trees that cross chunk borders come out whole.
     */
    private void placeTrees(ChunkData chunkData, int baseX, int baseZ, long seed, Map<Long, double[]> cache) {
        int cx0 = Math.floorDiv(baseX - TREE_R, TREE_CELL), cx1 = Math.floorDiv(baseX + 15 + TREE_R, TREE_CELL);
        int cz0 = Math.floorDiv(baseZ - TREE_R, TREE_CELL), cz1 = Math.floorDiv(baseZ + 15 + TREE_R, TREE_CELL);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long ch = mix(blockHash(seed ^ TREE_SALT, cx, 0, cz));
                // Roots stay off the cell's last row and column so neighbouring trunks never touch.
                int tx = cx * TREE_CELL + (int) ((ch >>> 8) % (TREE_CELL - 1));
                int tz = cz * TREE_CELL + (int) ((ch >>> 20) % (TREE_CELL - 1));
                if (Math.abs(tx) <= SPAWN_CLEAR && Math.abs(tz) <= SPAWN_CLEAR) continue;
                boolean[] col = solidColumn(seed, tx, tz, cache);
                for (int y = Y0; y < Y1; y++) {
                    if (!isSolid(col, y) || isSolid(col, y + 1)) continue;
                    long h = mix(ch ^ y);
                    if ((h & 0xFF) >= 110) continue;   // ~43% of surfaces
                    boolean golden = ((h >>> 32) & 0xF) == 0;
                    int height = (golden ? 5 : 4) + (int) ((h >>> 36) % 3);
                    if (!groundForTree(col, y, height)) continue;
                    placeTree(chunkData, baseX, baseZ, tx, y + 1, tz, height, golden, h);
                }
            }
        }
    }

    /** Grass (not a thin quicksoil edge) at y, and open air for the whole tree above it. */
    private static boolean groundForTree(boolean[] col, int y, int height) {
        for (int i = 1; i < QUICKSOIL_THIN; i++) if (!isSolid(col, y - i)) return false;
        for (int i = 1; i <= height + 3; i++) if (isSolid(col, y + i)) return false;
        return true;
    }

    private static void placeTree(ChunkData chunkData, int baseX, int baseZ,
                                  int tx, int ty, int tz, int height, boolean golden, long h) {
        int trunkTop = ty + height - 1;
        Material log = golden ? Material.OAK_LOG : Material.BIRCH_LOG;

        for (int dx = -TREE_R; dx <= TREE_R; dx++) {
            for (int dz = -TREE_R; dz <= TREE_R; dz++) {
                int lx = tx + dx - baseX, lz = tz + dz - baseZ;
                if (lx < 0 || lx > 15 || lz < 0 || lz > 15) continue;
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                boolean corner = Math.abs(dx) == TREE_R && Math.abs(dz) == TREE_R;
                long ch = mix(h ^ ((dx + 8) * 31L + (dz + 8)));

                if (dx == 0 && dz == 0) {
                    chunkData.setBlock(lx, ty - 1, lz, Material.DIRT);
                    for (int y = ty; y <= trunkTop; y++) chunkData.setBlock(lx, y, lz, log);
                }
                // Two wide layers at the top of the trunk, then a narrow layer and a cap above it.
                for (int y = trunkTop - 1; y <= trunkTop + 2; y++) {
                    if (dx == 0 && dz == 0 && y <= trunkTop) continue;
                    int r = y <= trunkTop ? TREE_R : 1;
                    if (ring > r) continue;
                    if (y == trunkTop + 2 && Math.abs(dx) + Math.abs(dz) > 1) continue;
                    if (r == TREE_R && corner && ((ch >>> (y & 7)) & 1) == 0) continue;
                    if (!chunkData.getType(lx, y, lz).isAir()) continue;
                    int dist = Math.abs(dx) + Math.abs(dz) + Math.max(0, y - trunkTop);
                    placeLeaf(chunkData, lx, y, lz, leafMaterial(golden, ch ^ y), dist);
                }
            }
        }
    }

    private static Material leafMaterial(boolean golden, long h) {
        if (!golden) return Material.OAK_LEAVES;
        return (mix(h) & 3) == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES;
    }

    /** Leaves with an explicit distance so worldgen-placed canopy doesn't decay. */
    private static void placeLeaf(ChunkData chunkData, int x, int y, int z, Material material, int distance) {
        Leaves leaves = (Leaves) material.createBlockData();
        leaves.setDistance(Math.max(1, Math.min(6, distance)));
        chunkData.setBlock(x, y, z, leaves);
    }

    // ---- hashing -------------------------------------------------------------------------

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long blockHash(long hash, int x, int y, int z) {
        return mix(hash ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L));
    }

    // ---- spawn ---------------------------------------------------------------------------

    /** Inside the spawn blob; SpawnFinder scans up from here to the island's top. */
    @Override
    public int getSpawnY() { return SPAWN_Y; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        boolean[] col = solidColumn(world.getSeed(), 0, 0, new HashMap<>());
        int y = SPAWN_Y;
        while (y < Y1 && isSolid(col, y)) y++;
        return new Location(world, 0.5, y, 0.5);
    }
}
