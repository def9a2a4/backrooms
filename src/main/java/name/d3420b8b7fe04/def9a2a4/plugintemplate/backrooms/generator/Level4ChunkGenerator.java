package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Level 4 — "The Far Lands" / farlands.jar
 * A 4-chunk-wide strip of normal alpha terrain runs along the X axis.
 * On either side, the terrain transitions into massive swiss-cheese walls
 * with horizontal tunnels boring into them. Only Beta 1.7.3-era blocks.
 * Permanent sunset.
 */
public class Level4ChunkGenerator extends BackroomsChunkGenerator {

    public Level4ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    private static final int MIN_Y = 0;
    private static final int MAX_Y = 128;
    private static final int SEA_LEVEL = 62;

    // Zone boundary (measured as abs(worldZ)) — hard cutoff, no blend
    private static final int STRIP_HALF_WIDTH = 32;

    // Strip terrain center height
    private static final double STRIP_CENTER_Y = 66.0;

    // Far lands 3D density scales (Z heavily stretched for wall effect)
    private static final double FL_SCALE_X = 1.0 / 40.0;
    private static final double FL_SCALE_Y = 1.0 / 30.0;
    private static final double FL_SCALE_Z = 1.0 / 600.0;

    // Far lands floor taper
    private static final int FL_FLOOR_Y = 5;

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        boolean[][][] solid = new boolean[16][MAX_Y][16];

        // --- Pass 1: determine solid/air ---
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                boolean inFarLands = Math.abs(worldZ) > STRIP_HALF_WIDTH;

                if (!inFarLands) {
                    // Strip: 3D density with vertical gradient
                    for (int y = MIN_Y; y < MAX_Y; y++) {
                        if (y == 0) { solid[x][y][z] = true; continue; }
                        double gradient = (STRIP_CENTER_Y - y) / 20.0;
                        double n1 = SimplexNoise.noise3(seed + 10, worldX / 80.0, y / 60.0, worldZ / 80.0);
                        double n2 = SimplexNoise.noise3(seed + 11, worldX / 30.0, y / 25.0, worldZ / 30.0);
                        double density = gradient + n1 * 0.6 + n2 * 0.25;
                        solid[x][y][z] = density > 0;
                    }
                } else {
                    // Far lands: 3D density swiss cheese
                    // Frequency modulation: varies between ~0.6x and ~1.4x base frequency
                    double freqMod = 1.0 + SimplexNoise.noise3(seed + 5, worldX / 250.0, 0, worldZ / 250.0) * 0.4;

                    for (int y = MIN_Y; y < MAX_Y; y++) {
                        if (y == 0) { solid[x][y][z] = true; continue; }

                        double n1 = SimplexNoise.noise3(seed + 20,
                                worldX * FL_SCALE_X * freqMod, y * FL_SCALE_Y, worldZ * FL_SCALE_Z * freqMod);
                        double n2 = SimplexNoise.noise3(seed + 21,
                                worldX * FL_SCALE_X * 2.3 * freqMod, y * FL_SCALE_Y * 2.3, worldZ * FL_SCALE_Z * 2.3 * freqMod);
                        double rawDensity = 0.7 * n1 + 0.3 * n2;

                        // Floor taper only — top left ragged by noise
                        double envelope = 1.0;
                        if (y < FL_FLOOR_Y + 10) envelope = (y - FL_FLOOR_Y) / 10.0;
                        envelope = Math.max(0, Math.min(1, envelope));

                        // Distance bias: far lands get denser further from the strip
                        double distFromEdge = Math.abs(worldZ) - STRIP_HALF_WIDTH;
                        double distBias = Math.min(distFromEdge / 200.0, 0.15);

                        solid[x][y][z] = rawDensity * envelope + distBias > 0.15;
                    }
                }
            }
        }

        // --- Pass 2: material assignment ---
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                boolean inFarLands = Math.abs(worldZ) > STRIP_HALF_WIDTH;

                // Surface noise for sand/gravel patches
                double surfNoise = SimplexNoise.noise2(seed + 30, worldX * 0.05, worldZ * 0.05);

                int depthBelowSurface = 0;

                for (int y = MAX_Y - 1; y >= 0; y--) {
                    if (!solid[x][y][z]) {
                        depthBelowSurface = 0;
                        // Water fill in the strip only
                        if (!inFarLands && y <= SEA_LEVEL && y > 0) {
                            chunkData.setBlock(x, y, z, Material.WATER);
                        }
                        continue;
                    }

                    // Check if this is a surface (air above)
                    boolean airAbove = (y >= MAX_Y - 1) || !solid[x][y + 1][z];
                    if (airAbove) depthBelowSurface = 0;

                    // Bedrock
                    if (y == 0) {
                        chunkData.setBlock(x, y, z, Material.BEDROCK);
                    } else if (y <= 3 && chunkRng.nextInt(4) < (4 - y)) {
                        chunkData.setBlock(x, y, z, Material.BEDROCK);
                    }
                    // Surface layers
                    else if (depthBelowSurface == 0) {
                        // Near water or beach: sand/gravel
                        if (!inFarLands && y <= SEA_LEVEL + 2 && y >= SEA_LEVEL - 2) {
                            chunkData.setBlock(x, y, z, surfNoise > 0.3 ? Material.GRAVEL : Material.SAND);
                        } else {
                            chunkData.setBlock(x, y, z, Material.GRASS_BLOCK);
                        }
                    } else if (depthBelowSurface <= 3) {
                        chunkData.setBlock(x, y, z, Material.DIRT);
                    }
                    // Stone core with ores
                    else {
                        int roll = chunkRng.nextInt(1000);
                        if (y < 50 && roll < 20) {
                            chunkData.setBlock(x, y, z, Material.COAL_ORE);
                        } else if (y < 40 && roll < 30) {
                            chunkData.setBlock(x, y, z, Material.IRON_ORE);
                        } else if (y < 25 && roll < 33) {
                            chunkData.setBlock(x, y, z, Material.GOLD_ORE);
                        } else {
                            chunkData.setBlock(x, y, z, Material.STONE);
                        }
                    }

                    depthBelowSurface++;
                }
            }
        }

        // --- Pass 3: trees in/near the strip ---
        if (chunkRng.nextDouble() < 0.3) {
            int count = 1 + chunkRng.nextInt(2);
            for (int i = 0; i < count; i++) {
                int tx = chunkRng.nextInt(12) + 2;
                int tz = chunkRng.nextInt(12) + 2;
                int worldZ = chunkZ * 16 + tz;
                if (Math.abs(worldZ) > STRIP_HALF_WIDTH) continue;

                for (int y = MAX_Y - 1; y > MIN_Y; y--) {
                    if (solid[tx][y][tz] && (y >= MAX_Y - 1 || !solid[tx][y + 1][tz])) {
                        if (y >= SEA_LEVEL + 2 && chunkData.getType(tx, y, tz) == Material.GRASS_BLOCK) {
                            placeOakTree(chunkData, chunkRng, tx, y + 1, tz);
                        }
                        break;
                    }
                }
            }
        }
    }

    private void placeOakTree(ChunkData chunkData, Random rng, int x, int y, int z) {
        int trunkHeight = 4 + rng.nextInt(3); // 4-6
        int topY = y + trunkHeight - 1;

        // Trunk
        for (int dy = 0; dy < trunkHeight; dy++) {
            if (y + dy < MAX_Y) {
                chunkData.setBlock(x, y + dy, z, Material.OAK_LOG);
            }
        }

        // Leaf layers: 2 wide layers, then 2 narrow layers (classic oak shape)
        // Layer 0,1 (bottom two): radius 2 with random corner removal
        // Layer 2,3 (top two): radius 1 with leaf on top of trunk
        for (int layer = 0; layer < 4; layer++) {
            int ly = topY - 2 + layer; // starts 2 below trunk top
            if (ly < 0 || ly >= MAX_Y) continue;
            int radius = layer < 2 ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && layer < 3) continue; // trunk column (except top)
                    // Skip corners randomly on wide layers
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && rng.nextBoolean()) continue;
                    int lx = x + dx, lz = z + dz;
                    if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                        if (chunkData.getType(lx, ly, lz) == Material.AIR) {
                            chunkData.setBlock(lx, ly, lz, Material.OAK_LEAVES);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getSpawnY() {
        return (int) STRIP_CENTER_Y + 1;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, (int) STRIP_CENTER_Y + 1, 0.5);
    }
}
