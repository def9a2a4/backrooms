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
 * Long parallel utility hallways with copper pipes running along walls and ceilings.
 * Repeating 6-block pattern creates 3-wide hallways separated by 1-block walls.
 * Large-scale noise rotates hallway direction between E-W and N-S regions.
 * Cross-corridors every 20 blocks create T-junctions.
 */
public class Level2ChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 8;
    private static final int AIR_MIN_Y = 8;
    private static final int CEILING_MIN_Y = 13;
    private static final int CEILING_MAX_Y = 20;

    // Hallway pattern: repeats every 6 blocks
    // [wall, hall, hall, hall, wall, hall, hall, hall] but offset so we get:
    // 0=wall, 1-3=hallway, 4=wall, 5=hallway (wraps to next period)
    private static final int HALLWAY_PERIOD = 6;
    private static final int CROSS_CORRIDOR_PERIOD = 20;
    private static final int CROSS_CORRIDOR_WIDTH = 2;

    private static final double REGION_SCALE = 1.0 / 96.0;

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

        // Pipe material for this chunk region
        double pipeRegionNoise = SimplexNoise.noise2(seed + 4, chunkX * 0.3, chunkZ * 0.3);
        Material pipeMat = PIPE_MATERIALS[Math.abs((int) (pipeRegionNoise * 1000)) % PIPE_MATERIALS.length];

        // Structure pass: determine wall vs air per column
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Region direction from large-scale noise
                double regionNoise = SimplexNoise.noise2(seed + 1, worldX * REGION_SCALE, worldZ * REGION_SCALE);
                boolean eastWest = regionNoise > 0.0;

                boolean isWall;
                if (eastWest) {
                    // E-W hallways: walls run along Z axis
                    int zMod = Math.floorMod(worldZ, HALLWAY_PERIOD);
                    boolean isMainWall = (zMod == 0);
                    // Cross-corridors cut through walls perpendicular (N-S)
                    int xMod = Math.floorMod(worldX, CROSS_CORRIDOR_PERIOD);
                    boolean isCross = (xMod < CROSS_CORRIDOR_WIDTH);
                    isWall = isMainWall && !isCross;
                } else {
                    // N-S hallways: walls run along X axis
                    int xMod = Math.floorMod(worldX, HALLWAY_PERIOD);
                    boolean isMainWall = (xMod == 0);
                    // Cross-corridors cut through walls perpendicular (E-W)
                    int zMod = Math.floorMod(worldZ, CROSS_CORRIDOR_PERIOD);
                    boolean isCross = (zMod < CROSS_CORRIDOR_WIDTH);
                    isWall = isMainWall && !isCross;
                }

                if (isWall) {
                    // Solid wall floor to ceiling
                    for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.DEEPSLATE_BRICKS);
                    }
                } else {
                    // Open hallway
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

                    // Pipes along ceiling: run along hallway direction
                    if (eastWest) {
                        // Pipe runs E-W: place on blocks adjacent to walls
                        int zMod = Math.floorMod(worldZ, HALLWAY_PERIOD);
                        if (zMod == 1 || zMod == HALLWAY_PERIOD - 1) {
                            chunkData.setBlock(x, CEILING_MIN_Y - 1, z, pipeMat);
                        }
                    } else {
                        // Pipe runs N-S: place on blocks adjacent to walls
                        int xMod = Math.floorMod(worldX, HALLWAY_PERIOD);
                        if (xMod == 1 || xMod == HALLWAY_PERIOD - 1) {
                            chunkData.setBlock(x, CEILING_MIN_Y - 1, z, pipeMat);
                        }
                    }
                }
            }
        }

        // Lighting: soul lanterns on chains every 12 blocks in open areas
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % 12 == 0 && worldZ % 12 == 0) {
                    if (chunkData.getType(x, AIR_MIN_Y, z) == Material.AIR
                            && chunkData.getType(x, CEILING_MIN_Y - 1, z) != Material.SEA_LANTERN) {
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
