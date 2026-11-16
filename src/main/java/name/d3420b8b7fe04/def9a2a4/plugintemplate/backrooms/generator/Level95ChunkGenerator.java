package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Level 95 — "Skylands". Floating islands in a bright sky, after the Aether mod: three tiers of
 * islands with teal grass (from the {@code backrooms:skylands} biome) on top and long tapering
 * holystone undersides, skyroot and golden oak trees, and aerclouds of stained glass between.
 *
 * <p>Everything is per column. Each tier has its own low-frequency island mask; where the mask
 * passes the tier's threshold there is an island, and how far past it decides how high the top
 * domes and how deep the underside hangs, so islands thin out to a rim at their edges. A higher
 * tier is cut off above a lower one so they never fuse.
 *
 * <p>Aether blocks in vanilla: holystone is diorite and calcite with mossy cobblestone patches,
 * quicksoil is sand at island rims, ambrosium is glowstone, zanite is amethyst, gravitite is
 * crying obsidian (deep in undersides only), icestone is packed ice (top tier only).
 *
 * <p>The middle tier always has an island at the origin; spawn is on its top.
 */
public class Level95ChunkGenerator extends BackroomsChunkGenerator {

    /**
     * One layer of islands. {@code base} is the height where an island's rim sits; the top domes
     * up to {@code topAmp} above it and the underside hangs up to {@code depthAmp} below.
     */
    private record Tier(int index, int base, double freq, double threshold, int topAmp, int depthAmp, long salt) {}

    private static final Tier[] TIERS = {
            new Tier(0, 80, 0.010, 0.30, 14, 60, 0x5C1A0L),
            new Tier(1, 135, 0.012, 0.34, 12, 50, 0x5C1A1L),
            new Tier(2, 190, 0.016, 0.42, 9, 38, 0x5C1A2L),
    };
    private static final int SPAWN_TIER = 1;

    /** Mask boost around the origin on the spawn tier, fading to nothing at SPAWN_ISLAND_R. */
    private static final double SPAWN_ISLAND_R = 44;
    private static final double SPAWN_BONUS = 0.7;
    /** No trees this close to the origin. */
    private static final int SPAWN_CLEAR = 4;

    /** A higher tier's underside stops this far above the top of the tier below. */
    private static final int TIER_GAP = 8;

    /** Island fraction (0 at the rim, 1 at the heart) below which the top is quicksoil. */
    private static final double QUICKSOIL_EDGE = 0.07;

    /** Ore odds per 1000 holystone blocks. */
    private static final int AMBROSIUM = 7;
    private static final int ZANITE = 3;
    private static final int GRAVITITE = 1;

    /** Aercloud bands: flattened 3D noise around these heights, between and below the tiers. */
    private static final int[] CLOUD_BANDS = {40, 108, 163, 222};
    private static final int CLOUD_HALF = 7;
    private static final double CLOUD_THRESHOLD = 0.5;
    private static final long CLOUD_SALT = 0xC10D5L;
    private static final long CLOUD_COLOR_SALT = 0xC0102L;

    /** Trees: one candidate per TREE_CELL square per tier. */
    private static final int TREE_CELL = 7;
    private static final int TREE_R = 2;
    private static final long TREE_SALT = 0x7EE5L;

    private static final long DETAIL_SALT = 0xDE7A11L;
    private static final long STONE_SALT = 0x5707EL;

    private static final Material[] FLOWERS = {
            Material.WHITE_TULIP, Material.AZURE_BLUET, Material.LILY_OF_THE_VALLEY, Material.OXEYE_DAISY,
    };

    /** Where tier {@code t} has an island in one column: blocks bottom..top inclusive. */
    private record Span(int top, int bottom, double frac) {}

    public Level95ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;

        // Island spans for the chunk plus a one-column border, so clouds can keep clear of islands.
        Span[][][] spans = new Span[18][18][];
        for (int x = 0; x < 18; x++) {
            for (int z = 0; z < 18; z++) {
                spans[x][z] = columnSpans(seed, baseX + x - 1, baseZ + z - 1);
            }
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = baseX + x, wz = baseZ + z;
                Span[] col = spans[x + 1][z + 1];
                for (Tier t : TIERS) {
                    Span s = col[t.index()];
                    if (s != null) fillIsland(chunkData, x, z, wx, wz, s, t, seed);
                }
                placeClouds(chunkData, x, z, wx, wz, spans, seed);
            }
        }

        placeTrees(chunkData, baseX, baseZ, seed);
    }

    // ---- islands -------------------------------------------------------------------------

    /** The island span of every tier in column (wx, wz); null entries where a tier has none. */
    private static Span[] columnSpans(long seed, int wx, int wz) {
        Span[] out = new Span[TIERS.length];
        int below = Integer.MIN_VALUE;   // top of the nearest island underneath
        for (Tier t : TIERS) {
            Span s = tierSpan(seed, t, wx, wz);
            if (s != null && below != Integer.MIN_VALUE) {
                int floor = below + TIER_GAP;
                if (s.top() - 2 < floor) s = null;
                else if (s.bottom() < floor) s = new Span(s.top(), floor, s.frac());
            }
            out[t.index()] = s;
            if (s != null) below = s.top();
        }
        return out;
    }

    private static Span tierSpan(long seed, Tier t, int wx, int wz) {
        double f = t.freq();
        double mask = 0.8 * SimplexNoise.noise2(seed ^ t.salt(), wx * f, wz * f)
                + 0.2 * SimplexNoise.noise2(seed ^ (t.salt() * 31), wx * f * 4, wz * f * 4);
        if (t.index() == SPAWN_TIER) {
            double d = Math.sqrt((double) wx * wx + (double) wz * wz);
            if (d < SPAWN_ISLAND_R) mask += SPAWN_BONUS * (1 - d / SPAWN_ISLAND_R);
        }
        if (mask <= t.threshold()) return null;
        double frac = Math.min(1, (mask - t.threshold()) / (1 - t.threshold()));

        long ds = seed ^ DETAIL_SALT ^ t.salt();
        double bump = SimplexNoise.noise2(ds, wx * 0.06, wz * 0.06);
        int top = t.base() + (int) Math.round(Math.sqrt(frac) * t.topAmp() + bump * 1.5);
        // Undersides taper to a point: depth grows faster than the top, with ragged drips.
        double drip = 0.75 + 0.25 * SimplexNoise.noise2(ds * 7, wx * 0.09, wz * 0.09)
                + 0.2 * Math.max(0, SimplexNoise.noise2(ds * 13, wx * 0.25, wz * 0.25));
        int depth = 2 + (int) Math.round(Math.pow(frac, 0.8) * t.depthAmp() * drip);
        return new Span(top, top - depth, frac);
    }

    private static void fillIsland(ChunkData chunkData, int x, int z, int wx, int wz, Span s, Tier t, long seed) {
        int minY = chunkData.getMinHeight(), maxY = chunkData.getMaxHeight() - 1;
        long colHash = blockHash(seed, wx, t.index(), wz);
        boolean quicksoil = s.frac() < QUICKSOIL_EDGE;
        int soil = 2 + (int) (colHash & 1);
        int deepLimit = s.bottom() + (s.top() - s.bottom()) / 3;

        for (int wy = Math.max(minY, s.bottom()); wy <= Math.min(maxY, s.top()); wy++) {
            int below = s.top() - wy;
            Material m;
            if (quicksoil && below < 3) {
                m = Material.SAND;
            } else if (below == 0) {
                m = Material.GRASS_BLOCK;
            } else if (below <= soil) {
                m = Material.DIRT;
            } else {
                m = holystone(seed, wx, wy, wz, t, wy <= deepLimit);
            }
            chunkData.setBlock(x, wy, z, m);
        }

        if (!quicksoil && s.top() + 1 <= maxY) {
            int roll = (int) ((colHash >>> 16) % 100);
            if (roll < 22) {
                chunkData.setBlock(x, s.top() + 1, z, Material.SHORT_GRASS);
            } else if (roll < 26) {
                chunkData.setBlock(x, s.top() + 1, z, FLOWERS[(int) ((colHash >>> 32) & 0xFFFF) % FLOWERS.length]);
            }
        }
    }

    private static Material holystone(long seed, int wx, int wy, int wz, Tier t, boolean deep) {
        long h = blockHash(seed ^ STONE_SALT, wx, wy, wz);
        int ore = (int) ((h >>> 8) % 1000);
        if (ore < AMBROSIUM) return Material.GLOWSTONE;
        if (ore < AMBROSIUM + ZANITE) return Material.AMETHYST_BLOCK;
        if (deep && ore < AMBROSIUM + ZANITE + GRAVITITE) return Material.CRYING_OBSIDIAN;

        double patch = SimplexNoise.noise3(seed ^ STONE_SALT, wx * 0.08, wy * 0.08, wz * 0.08);
        if (t.index() == TIERS.length - 1 && patch > 0.6) return Material.PACKED_ICE;
        if (patch < -0.55) return Material.MOSSY_COBBLESTONE;
        if (patch > 0.25) return Material.CALCITE;
        return Material.DIORITE;
    }

    // ---- aerclouds -----------------------------------------------------------------------

    private static void placeClouds(ChunkData chunkData, int x, int z, int wx, int wz, Span[][][] spans, long seed) {
        Material color = cloudColor(seed, wx, wz);
        for (int b = 0; b < CLOUD_BANDS.length; b++) {
            long bs = seed ^ CLOUD_SALT ^ (b * 0x9E3779B97F4A7C15L);
            int center = CLOUD_BANDS[b] + (int) Math.round(5 * SimplexNoise.noise2(bs, wx * 0.008, wz * 0.008));
            for (int wy = center - CLOUD_HALF; wy <= center + CLOUD_HALF; wy++) {
                double falloff = Math.abs(wy - center) / (double) CLOUD_HALF;
                double v = SimplexNoise.noise3(bs, wx * 0.022, wy * 0.11, wz * 0.022) - 0.45 * falloff * falloff;
                if (v < CLOUD_THRESHOLD) continue;
                if (nearIsland(spans, x + 1, z + 1, wy)) continue;
                chunkData.setBlock(x, wy, z, color);
            }
        }
    }

    /** Cold (white) aerclouds mostly, blue ones in places, golden ones rarely. */
    private static Material cloudColor(long seed, int wx, int wz) {
        double c = SimplexNoise.noise2(seed ^ CLOUD_COLOR_SALT, wx * 0.005, wz * 0.005);
        if (c > 0.72) return Material.YELLOW_STAINED_GLASS;
        if (c > 0.4) return Material.LIGHT_BLUE_STAINED_GLASS;
        return Material.WHITE_STAINED_GLASS;
    }

    /** True if any island block is within one block of (sx, wy, sz) in the span grid. */
    private static boolean nearIsland(Span[][][] spans, int sx, int sz, int wy) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (Span s : spans[sx + dx][sz + dz]) {
                    if (s != null && wy >= s.bottom() - 1 && wy <= s.top() + 1) return true;
                }
            }
        }
        return false;
    }

    // ---- trees ---------------------------------------------------------------------------

    /**
     * Each TREE_CELL square of each tier may hold one tree. A chunk visits every cell whose
     * canopy could reach into it and recomputes the island at the tree's root, so trees that
     * cross chunk borders come out whole.
     */
    private static void placeTrees(ChunkData chunkData, int baseX, int baseZ, long seed) {
        int cx0 = Math.floorDiv(baseX - TREE_R, TREE_CELL), cx1 = Math.floorDiv(baseX + 15 + TREE_R, TREE_CELL);
        int cz0 = Math.floorDiv(baseZ - TREE_R, TREE_CELL), cz1 = Math.floorDiv(baseZ + 15 + TREE_R, TREE_CELL);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                for (Tier t : TIERS) {
                    long h = mix(blockHash(seed ^ TREE_SALT, cx, t.index(), cz));
                    if ((h & 0xFF) >= 110) continue;   // ~43% of cells
                    // Roots stay off the cell's last row and column so neighbouring trunks never touch.
                    int tx = cx * TREE_CELL + (int) ((h >>> 8) % (TREE_CELL - 1));
                    int tz = cz * TREE_CELL + (int) ((h >>> 20) % (TREE_CELL - 1));
                    if (Math.abs(tx) <= SPAWN_CLEAR && Math.abs(tz) <= SPAWN_CLEAR) continue;
                    Span s = columnSpans(seed, tx, tz)[t.index()];
                    if (s == null || s.frac() < QUICKSOIL_EDGE * 2) continue;
                    boolean golden = ((h >>> 32) & 0xF) == 0;
                    int height = (golden ? 5 : 4) + (int) ((h >>> 36) % 3);
                    placeTree(chunkData, baseX, baseZ, tx, s.top() + 1, tz, height, golden, h);
                }
            }
        }
    }

    private static void placeTree(ChunkData chunkData, int baseX, int baseZ,
                                  int tx, int ty, int tz, int height, boolean golden, long h) {
        int maxY = chunkData.getMaxHeight() - 1;
        int trunkTop = ty + height - 1;
        if (trunkTop + 2 > maxY) return;
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
                    if (y == trunkTop + 2 && ring > 0 && Math.abs(dx) + Math.abs(dz) > 1) continue;
                    if (ring == r && r == TREE_R && corner && ((ch >>> (y & 7)) & 1) == 0) continue;
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

    @Override
    public int getSpawnY() { return TIERS[SPAWN_TIER].base(); }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        Span s = columnSpans(world.getSeed(), 0, 0)[SPAWN_TIER];
        int y = s != null ? s.top() + 1 : TIERS[SPAWN_TIER].base() + 1;
        return new Location(world, 0.5, y, 0.5);
    }
}
