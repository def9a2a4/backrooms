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
 * Level 37 — "The Poolrooms"
 * Vast open chambers of light blue terracotta and prismarine tiles.
 * Shallow, warm, perfectly still water fills most rooms.
 * Sea lanterns in walls/floors. The ONLY level that feels safe.
 * Waterslide tunnels of packed ice and blue glass between rooms.
 */
public class Level37ChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 8;
    private static final int WATER_Y = 9; // Knee-deep water
    private static final int AIR_MIN_Y = 9;
    private static final int CEILING_Y = 18;
    private static final int CEILING_MAX_Y = 24;

    private static final double ROOM_SCALE = 1.0 / 60.0;
    private static final double POOL_SCALE = 1.0 / 30.0;
    private static final double DETAIL_SCALE = 1.0 / 10.0;

    private static final Material[] TILE_MATERIALS = {
            Material.LIGHT_BLUE_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA,
            Material.PRISMARINE, Material.PRISMARINE_BRICKS,
            Material.LIGHT_BLUE_CONCRETE, Material.CYAN_TERRACOTTA
    };

    private static final Material[] WALL_MATERIALS = {
            Material.LIGHT_BLUE_TERRACOTTA, Material.PRISMARINE_BRICKS,
            Material.LIGHT_BLUE_CONCRETE, Material.CYAN_TERRACOTTA
    };

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Room structure noise
                double roomNoise = SimplexNoise.noise2(seed, worldX * ROOM_SCALE, worldZ * ROOM_SCALE);
                double poolDepth = SimplexNoise.noise2(seed + 1, worldX * POOL_SCALE, worldZ * POOL_SCALE);
                double detail = SimplexNoise.noise2(seed + 2, worldX * DETAIL_SCALE, worldZ * DETAIL_SCALE);

                // Floor tile material
                int matIndex = Math.abs((int) (detail * 1000)) % TILE_MATERIALS.length;
                Material tileMat = TILE_MATERIALS[matIndex];

                // Sub-floor
                chunkData.setRegion(x, FLOOR_Y, z, x + 1, FLOOR_HEIGHT, z + 1, Material.PRISMARINE);

                // Determine if this is wall or open space
                boolean isWall = roomNoise > 0.35;
                boolean isPillar = Math.abs(roomNoise) > 0.28 && Math.abs(roomNoise) < 0.35;

                if (isWall) {
                    // Wall: floor to ceiling
                    Material wallMat = WALL_MATERIALS[Math.abs((int) (roomNoise * 100)) % WALL_MATERIALS.length];
                    for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, wallMat);
                    }
                    // Embed sea lanterns in walls occasionally
                    if (detail > 0.4) {
                        chunkData.setBlock(x, FLOOR_HEIGHT + 2, z, Material.SEA_LANTERN);
                    }
                } else if (isPillar) {
                    // Rounded pillar
                    for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.PRISMARINE_BRICKS);
                    }
                } else {
                    // Open space: tile floor
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, tileMat);

                    // Water depth varies
                    int waterDepth;
                    if (poolDepth > 0.3) {
                        // Deep pool: 3-4 blocks
                        waterDepth = 3 + (poolDepth > 0.5 ? 1 : 0);
                        // Dig out floor for deep pool
                        for (int dy = 1; dy < waterDepth; dy++) {
                            chunkData.setBlock(x, FLOOR_HEIGHT + 1 - dy, z, Material.WATER);
                        }
                        chunkData.setBlock(x, FLOOR_HEIGHT - waterDepth, z, Material.PRISMARINE);
                    } else if (poolDepth > -0.1) {
                        // Shallow water: 1 block
                        waterDepth = 1;
                    } else {
                        // Dry area
                        waterDepth = 0;
                    }

                    if (waterDepth > 0) {
                        chunkData.setBlock(x, FLOOR_HEIGHT + 1, z, Material.WATER);
                    }

                    // Air above water to ceiling
                    int airStart = FLOOR_HEIGHT + (waterDepth > 0 ? 2 : 1);
                    for (int y = airStart; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                }

                // Ceiling
                for (int y = CEILING_Y; y < CEILING_MAX_Y; y++) {
                    chunkData.setBlock(x, y, z, Material.LIGHT_BLUE_TERRACOTTA);
                }
            }
        }

        // Sea lantern grid in ceiling (warm, even lighting)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % 4 == 0 && worldZ % 4 == 0) {
                    chunkData.setBlock(x, CEILING_Y, z, Material.SEA_LANTERN);
                }
            }
        }

        // Sea lanterns in floor under water (for that glowing pool effect)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % 6 == 0 && worldZ % 6 == 0) {
                    if (chunkData.getType(x, FLOOR_HEIGHT + 1, z) == Material.WATER) {
                        chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.SEA_LANTERN);
                    }
                }
            }
        }

        // Waterslide tunnels: rare packed ice + blue glass corridors
        if (chunkRng.nextDouble() < 0.1) {
            placeWaterslide(chunkData, chunkRng);
        }
    }

    private void placeWaterslide(ChunkData chunkData, Random rng) {
        int dir = rng.nextInt(2); // 0=X-aligned, 1=Z-aligned
        int pos = 4 + rng.nextInt(8); // position on cross-axis
        int slideY = FLOOR_HEIGHT + 2;

        for (int i = 0; i < 16; i++) {
            int x = dir == 0 ? i : pos;
            int z = dir == 1 ? i : pos;
            if (x >= 0 && x < 16 && z >= 0 && z < 16) {
                // Ice floor
                chunkData.setBlock(x, slideY, z, Material.PACKED_ICE);
                // Blue glass walls
                int wx = dir == 0 ? x : pos - 1;
                int wz = dir == 1 ? z : pos - 1;
                if (wx >= 0 && wx < 16 && wz >= 0 && wz < 16) {
                    chunkData.setBlock(wx, slideY + 1, wz, Material.LIGHT_BLUE_STAINED_GLASS);
                }
                wx = dir == 0 ? x : pos + 1;
                wz = dir == 1 ? z : pos + 1;
                if (wx >= 0 && wx < 16 && wz >= 0 && wz < 16) {
                    chunkData.setBlock(wx, slideY + 1, wz, Material.LIGHT_BLUE_STAINED_GLASS);
                }
                // Air passage
                chunkData.setBlock(x, slideY + 1, z, Material.AIR);
                chunkData.setBlock(x, slideY + 2, z, Material.AIR);
                // Glass ceiling
                chunkData.setBlock(x, slideY + 3, z, Material.LIGHT_BLUE_STAINED_GLASS);
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
        return new Location(world, 8.5, FLOOR_HEIGHT + 2, 8.5);
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
