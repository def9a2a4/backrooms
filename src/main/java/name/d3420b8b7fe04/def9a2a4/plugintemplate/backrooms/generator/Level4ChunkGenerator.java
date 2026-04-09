package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Level 4 — "The Far Lands" / farlands.jar
 * Alpha-esque terrain generated WRONG. Massive vertical stone walls, swiss-cheese holes,
 * floating water/lava, grass in impossible places. Only blocks from Beta 1.7.3 era.
 * Permanent sunset. Nostalgic and deeply broken.
 */
public class Level4ChunkGenerator extends ChunkGenerator {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 256;

    // Multiple noise layers at different scales for Far Lands effect
    private static final double MACRO_SCALE = 1.0 / 200.0;
    private static final double MESO_SCALE = 1.0 / 50.0;
    private static final double MICRO_SCALE = 1.0 / 15.0;
    private static final double WARP_SCALE = 1.0 / 80.0;

    // Beta-era blocks only
    private static final Material[] SURFACE_MATERIALS = {
            Material.GRASS_BLOCK, Material.DIRT, Material.SAND, Material.GRAVEL
    };

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Domain warping — coordinates get distorted, causing the "Far Lands" stretching
                double warpX = SimplexNoise.noise2(seed + 50, worldX * WARP_SCALE, worldZ * WARP_SCALE) * 40;
                double warpZ = SimplexNoise.noise2(seed + 51, worldX * WARP_SCALE, worldZ * WARP_SCALE) * 40;
                double warpedX = worldX + warpX;
                double warpedZ = worldZ + warpZ;

                // Multiple octaves for terrain height
                double macro = SimplexNoise.noise2(seed, warpedX * MACRO_SCALE, warpedZ * MACRO_SCALE);
                double meso = SimplexNoise.noise2(seed + 1, warpedX * MESO_SCALE, warpedZ * MESO_SCALE);
                double micro = SimplexNoise.noise2(seed + 2, worldX * MICRO_SCALE, worldZ * MICRO_SCALE);

                // Far Lands: extreme amplification causing vertical walls
                double amplifiedMacro = macro * macro * Math.signum(macro) * 3.0;
                double height = 64 + amplifiedMacro * 120 + meso * 30 + micro * 8;
                int terrainHeight = Math.max(1, Math.min((int) height, MAX_Y - 1));

                // Swiss cheese: holes through terrain
                double cheese = SimplexNoise.noise2(seed + 3, worldX * 0.08, worldZ * 0.08);

                // Surface material
                double surfNoise = SimplexNoise.noise2(seed + 4, worldX * 0.05, worldZ * 0.05);
                Material surfaceMat = SURFACE_MATERIALS[Math.abs((int) (surfNoise * 100)) % SURFACE_MATERIALS.length];

                // Generate column
                for (int y = MIN_Y; y < terrainHeight; y++) {
                    // Swiss cheese holes
                    double cheeseY = SimplexNoise.noise2(seed + 5, worldX * 0.06 + y * 0.1, worldZ * 0.06);
                    if (Math.abs(cheese) < 0.08 && Math.abs(cheeseY) < 0.15) {
                        continue; // hole
                    }

                    if (y == terrainHeight - 1) {
                        chunkData.setBlock(x, y, z, surfaceMat);
                    } else if (y > terrainHeight - 5) {
                        chunkData.setBlock(x, y, z, Material.DIRT);
                    } else {
                        chunkData.setBlock(x, y, z, Material.STONE);
                    }
                }

                // Ore veins exposed on surface (wrong)
                if (chunkRng.nextDouble() < 0.02 && terrainHeight > 20) {
                    Material ore = chunkRng.nextDouble() < 0.5 ? Material.COAL_ORE : Material.IRON_ORE;
                    for (int dy = 0; dy < 3; dy++) {
                        if (terrainHeight - dy > MIN_Y) {
                            chunkData.setBlock(x, terrainHeight - dy, z, ore);
                        }
                    }
                }

                // Floating water sources (wrong)
                double waterNoise = SimplexNoise.noise2(seed + 6, worldX * 0.12, worldZ * 0.12);
                if (waterNoise > 0.6 && terrainHeight < 100) {
                    int waterY = terrainHeight + 5 + chunkRng.nextInt(10);
                    if (waterY < MAX_Y) {
                        chunkData.setBlock(x, waterY, z, Material.WATER);
                    }
                }

                // Floating lava (rare, wrong)
                double lavaNoise = SimplexNoise.noise2(seed + 7, worldX * 0.15, worldZ * 0.15);
                if (lavaNoise > 0.7 && terrainHeight < 80) {
                    int lavaY = terrainHeight + 8 + chunkRng.nextInt(15);
                    if (lavaY < MAX_Y) {
                        chunkData.setBlock(x, lavaY, z, Material.LAVA);
                    }
                }

                // Grass blocks underground (wrong)
                if (chunkRng.nextDouble() < 0.01 && terrainHeight > 30) {
                    int grassY = 10 + chunkRng.nextInt(terrainHeight - 20);
                    chunkData.setBlock(x, grassY, z, Material.GRASS_BLOCK);
                }
            }
        }

        // Scattered oak trees in impossible places
        if (chunkRng.nextDouble() < 0.3) {
            int tx = chunkRng.nextInt(12) + 2;
            int tz = chunkRng.nextInt(12) + 2;
            int worldTx = chunkX * 16 + tx;
            int worldTz = chunkZ * 16 + tz;
            // Find surface
            for (int y = MAX_Y - 1; y > MIN_Y; y--) {
                if (chunkData.getType(tx, y, tz) != Material.AIR) {
                    placeOakTree(chunkData, chunkRng, tx, y + 1, tz);
                    break;
                }
            }
        }

        // Floating gravel (doesn't obey gravity here)
        if (chunkRng.nextDouble() < 0.15) {
            int gx = chunkRng.nextInt(16);
            int gz = chunkRng.nextInt(16);
            int gy = 80 + chunkRng.nextInt(100);
            for (int dx = 0; dx < 2 + chunkRng.nextInt(3); dx++) {
                for (int dz = 0; dz < 2 + chunkRng.nextInt(3); dz++) {
                    if (gx + dx < 16 && gz + dz < 16 && gy < MAX_Y) {
                        chunkData.setBlock(gx + dx, gy, gz + dz, Material.GRAVEL);
                    }
                }
            }
        }
    }

    private void placeOakTree(ChunkData chunkData, Random rng, int x, int y, int z) {
        int trunkHeight = 4 + rng.nextInt(3);
        // Trunk
        for (int dy = 0; dy < trunkHeight; dy++) {
            if (y + dy < MAX_Y) {
                chunkData.setBlock(x, y + dy, z, Material.OAK_LOG);
            }
        }
        // Simple leaf blob
        int leafStart = y + trunkHeight - 2;
        for (int dy = 0; dy < 3; dy++) {
            int radius = dy == 2 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius) continue;
                    int lx = x + dx, lz = z + dz, ly = leafStart + dy;
                    if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16 && ly < MAX_Y) {
                        if (chunkData.getType(lx, ly, lz) == Material.AIR) {
                            chunkData.setBlock(lx, ly, lz, Material.OAK_LEAVES);
                        }
                    }
                }
            }
        }
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, 100, 8.5);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return Biome.THE_VOID;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.THE_VOID);
            }
        };
    }
}
