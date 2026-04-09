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
 * Long dark hallways with copper/oxidized copper pipes running along walls and ceilings.
 * Industrial utility tunnels. Narrow corridors, occasional wider junction rooms.
 * Lots of copper blocks, cut copper, oxidized variants, chains, iron bars.
 */
public class Level2ChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 8;
    private static final int AIR_MIN_Y = 8;
    private static final int AIR_MAX_Y = 13;
    private static final int CEILING_MIN_Y = 13;
    private static final int CEILING_MAX_Y = 20;

    private static final double CORRIDOR_SCALE = 1.0 / 48.0;
    private static final double PIPE_SCALE = 1.0 / 12.0;
    private static final double ROOM_SCALE = 1.0 / 120.0;

    private static final Material[] PIPE_MATERIALS = {
            Material.COPPER_BLOCK, Material.EXPOSED_COPPER, Material.WEATHERED_COPPER,
            Material.OXIDIZED_COPPER, Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER,
            Material.WEATHERED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER
    };

    private static final Material[] WALL_MATERIALS = {
            Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
            Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES
    };

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // Fill floor and ceiling
        chunkData.setRegion(0, FLOOR_Y, 0, 16, FLOOR_HEIGHT, 16, Material.DEEPSLATE);
        chunkData.setRegion(0, CEILING_MIN_Y, 0, 16, CEILING_MAX_Y, 16, Material.DEEPSLATE_BRICKS);

        // Default: fill air space with walls (carve out corridors)
        chunkData.setRegion(0, AIR_MIN_Y, 0, 16, CEILING_MIN_Y, 16, Material.STONE_BRICKS);

        // Carve corridors using noise
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Two perpendicular corridor networks
                double corridorX = SimplexNoise.noise2(seed, worldX * CORRIDOR_SCALE, worldZ * CORRIDOR_SCALE * 0.15);
                double corridorZ = SimplexNoise.noise2(seed + 100, worldX * CORRIDOR_SCALE * 0.15, worldZ * CORRIDOR_SCALE);
                double room = SimplexNoise.noise2(seed + 200, worldX * ROOM_SCALE, worldZ * ROOM_SCALE);

                boolean isOpen = false;

                // Main X-aligned corridors (2-3 blocks wide)
                if (Math.abs(corridorX) < 0.15) {
                    isOpen = true;
                }
                // Main Z-aligned corridors
                if (Math.abs(corridorZ) < 0.12) {
                    isOpen = true;
                }
                // Junction rooms at intersections
                if (room > 0.4) {
                    isOpen = true;
                }

                if (isOpen) {
                    // Carve air space
                    for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }

                    // Floor surface variety
                    double floorDetail = SimplexNoise.noise2(seed + 3, worldX * 0.2, worldZ * 0.2);
                    if (floorDetail > 0.4) {
                        chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.DEEPSLATE_TILES);
                    } else if (floorDetail > 0.1) {
                        chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.DEEPSLATE_BRICKS);
                    }

                    // Pipe runs along ceiling
                    double pipeNoise = SimplexNoise.noise2(seed + 4, worldX * PIPE_SCALE, worldZ * PIPE_SCALE);
                    if (pipeNoise > 0.3) {
                        Material pipeMat = PIPE_MATERIALS[Math.abs((int) (pipeNoise * 1000)) % PIPE_MATERIALS.length];
                        chunkData.setBlock(x, CEILING_MIN_Y - 1, z, pipeMat);

                        // Occasional dripping
                        if (pipeNoise > 0.55 && chunkRng.nextDouble() < 0.1) {
                            chunkData.setBlock(x, CEILING_MIN_Y - 2, z, Material.IRON_CHAIN);
                        }
                    }

                    // Pipe runs along walls (where corridor meets solid)
                    boolean nearWall = false;
                    for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = x + offset[0];
                        int nz = z + offset[1];
                        if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16) {
                            nearWall = true;
                            break;
                        }
                        int nwx = chunkX * 16 + nx;
                        int nwz = chunkZ * 16 + nz;
                        double ncX = SimplexNoise.noise2(seed, nwx * CORRIDOR_SCALE, nwz * CORRIDOR_SCALE * 0.3);
                        double ncZ = SimplexNoise.noise2(seed + 100, nwx * CORRIDOR_SCALE * 0.3, nwz * CORRIDOR_SCALE);
                        double nRoom = SimplexNoise.noise2(seed + 200, nwx * ROOM_SCALE, nwz * ROOM_SCALE);
                        if (Math.abs(ncX) >= 0.15 && Math.abs(ncZ) >= 0.12 && nRoom <= 0.4) {
                            nearWall = true;
                            break;
                        }
                    }
                    if (nearWall && chunkRng.nextDouble() < 0.4) {
                        Material pipeMat = PIPE_MATERIALS[chunkRng.nextInt(PIPE_MATERIALS.length)];
                        chunkData.setBlock(x, AIR_MIN_Y + 3, z, pipeMat);
                    }
                }
            }
        }

        // Sparse lighting: redstone lamps in ceiling at junctions
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % 12 == 0 && worldZ % 12 == 0) {
                    if (chunkData.getType(x, AIR_MIN_Y, z) == Material.AIR) {
                        chunkData.setBlock(x, CEILING_MIN_Y - 1, z, Material.REDSTONE_LAMP);
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
