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
 * Level 2 — "The Pipe Works"
 * Mostly solid deepslate mass with sparse utility hallways carved through.
 * 2-wide hallways on a 16-block grid, with noise suppressing segments for sparsity.
 * Medium-scale noise rotates hallway direction, creating L-bends where regions meet.
 * Copper pipes (1x1) sometimes on ceiling, sometimes on walls, sometimes absent.
 */
public class Level2ChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 8;
    private static final int AIR_MIN_Y = 8;
    private static final int CEILING_MIN_Y = 13;
    private static final int CEILING_MAX_Y = 20;

    private static final int HALLWAY_PERIOD = 12;
    private static final int HALLWAY_WIDTH = 2;

    // Smaller region scale = more frequent direction changes
    private static final double REGION_SCALE = 1.0 / 48.0;
    // Suppression noise: large scale, creates rare but big gaps
    private static final double SUPPRESS_SCALE = 1.0 / 32.0;
    // Region boundary blend zone: carve both directions near transitions
    private static final double BLEND_THRESHOLD = 0.15;

    private static final Material[] PIPE_MATERIALS = {
            Material.COPPER_BLOCK, Material.EXPOSED_COPPER, Material.WEATHERED_COPPER,
            Material.OXIDIZED_COPPER, Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER
    };

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();

        // Floor
        chunkData.setRegion(0, FLOOR_Y, 0, 16, FLOOR_HEIGHT, 16, Material.DEEPSLATE);

        // Ceiling
        chunkData.setRegion(0, CEILING_MIN_Y, 0, 16, CEILING_MAX_Y, 16, Material.DEEPSLATE_BRICKS);

        // Fill entire air space solid — hallways carved out below
        chunkData.setRegion(0, AIR_MIN_Y, 0, 16, CEILING_MIN_Y, 16, Material.DEEPSLATE_BRICKS);

        // Pipe material for this chunk region
        double pipeRegionNoise = SimplexNoise.noise2(seed + 4, chunkX * 0.3, chunkZ * 0.3);
        Material pipeMat = PIPE_MATERIALS[Math.abs((int) (pipeRegionNoise * 1000)) % PIPE_MATERIALS.length];

        // First pass: carve hallways
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Region direction from noise — smaller scale means more direction changes
                double regionNoise = SimplexNoise.noise2(seed + 1, worldX * REGION_SCALE, worldZ * REGION_SCALE);

                // Check hallway lines for both directions
                int zMod = Math.floorMod(worldZ, HALLWAY_PERIOD);
                int xMod = Math.floorMod(worldX, HALLWAY_PERIOD);
                boolean onEWLine = (zMod < HALLWAY_WIDTH);
                boolean onNSLine = (xMod < HALLWAY_WIDTH);

                // Near region boundaries, accept either direction to ensure connectivity
                boolean onHallwayLine;
                if (Math.abs(regionNoise) < BLEND_THRESHOLD) {
                    onHallwayLine = onEWLine || onNSLine;
                } else if (regionNoise > 0.0) {
                    onHallwayLine = onEWLine;
                } else {
                    onHallwayLine = onNSLine;
                }

                if (!onHallwayLine) continue;

                // Suppression noise: rare large gaps
                double suppressNoise = SimplexNoise.noise2(seed + 2, worldX * SUPPRESS_SCALE, worldZ * SUPPRESS_SCALE);
                if (suppressNoise < -0.4) continue;

                // Carve air
                for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }

                // Floor surface variety
                double floorDetail = SimplexNoise.noise2(seed + 3, worldX * 0.2, worldZ * 0.2);
                if (floorDetail > 0.3) {
                    chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.DEEPSLATE_TILES);
                } else if (floorDetail > 0.0) {
                    chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.DEEPSLATE_BRICKS);
                }
            }
        }

        // Second pass: pipes (1x1, sometimes present, ceiling or wall)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Only place pipes in air columns
                if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;

                // Pipe presence noise: ~40% of hallway has pipes
                double pipeNoise = SimplexNoise.noise2(seed + 5, worldX * 0.08, worldZ * 0.08);
                if (pipeNoise < 0.1) continue;

                // Pipe placement noise: ceiling vs wall
                double placementNoise = SimplexNoise.noise2(seed + 6, worldX * 0.12, worldZ * 0.12);

                if (placementNoise > 0.2) {
                    // Ceiling pipe — only if adjacent to a wall on at least one side
                    boolean adjWall = false;
                    if (x > 0 && chunkData.getType(x - 1, AIR_MIN_Y, z) != Material.AIR) adjWall = true;
                    if (x < 15 && chunkData.getType(x + 1, AIR_MIN_Y, z) != Material.AIR) adjWall = true;
                    if (z > 0 && chunkData.getType(x, AIR_MIN_Y, z - 1) != Material.AIR) adjWall = true;
                    if (z < 15 && chunkData.getType(x, AIR_MIN_Y, z + 1) != Material.AIR) adjWall = true;
                    if (adjWall) {
                        chunkData.setBlock(x, CEILING_MIN_Y - 1, z, pipeMat);
                    }
                } else if (placementNoise < -0.2) {
                    // Wall pipe at mid-height — find an adjacent wall and place against it
                    int pipeY = AIR_MIN_Y + 2;
                    if (x > 0 && chunkData.getType(x - 1, pipeY, z) != Material.AIR) {
                        chunkData.setBlock(x, pipeY, z, pipeMat);
                    } else if (x < 15 && chunkData.getType(x + 1, pipeY, z) != Material.AIR) {
                        chunkData.setBlock(x, pipeY, z, pipeMat);
                    } else if (z > 0 && chunkData.getType(x, pipeY, z - 1) != Material.AIR) {
                        chunkData.setBlock(x, pipeY, z, pipeMat);
                    } else if (z < 15 && chunkData.getType(x, pipeY, z + 1) != Material.AIR) {
                        chunkData.setBlock(x, pipeY, z, pipeMat);
                    }
                }
                // else: no pipe in this segment
            }
        }

        // Third pass: lighting — soul lanterns, skip if pipe already there
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % 16 == 0 && worldZ % 16 == 0) {
                    if (chunkData.getType(x, AIR_MIN_Y, z) == Material.AIR
                            && chunkData.getType(x, CEILING_MIN_Y - 1, z) == Material.AIR) {
                        chunkData.setBlock(x, CEILING_MIN_Y - 1, z, Material.IRON_CHAIN);
                        chunkData.setBlock(x, CEILING_MIN_Y - 2, z, Material.SOUL_LANTERN);
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
        return new Location(world, 8.5, AIR_MIN_Y, 8.5);
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
