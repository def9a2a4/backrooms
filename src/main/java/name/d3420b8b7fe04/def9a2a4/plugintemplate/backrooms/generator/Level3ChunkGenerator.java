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
 * Level 3 — "The Server Room" / err_redstone_overflow
 * Iron blocks, observer blocks, redstone lamps, command blocks, stone bricks.
 * Narrow 2-3 block tall corridors. Redstone dust everywhere. Observers facing inward.
 * Feels like being inside Minecraft's nervous system.
 */
public class Level3ChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 10;
    private static final int AIR_MIN_Y = 10;
    private static final int AIR_MAX_Y = 13; // only 3 blocks tall — claustrophobic
    private static final int CEILING_MIN_Y = 13;
    private static final int CEILING_MAX_Y = 20;

    private static final int CELL_SIZE = 4;
    private static final int CELLS_PER_AXIS = 16 / CELL_SIZE;
    private static final double REGION_SCALE = 1.0 / 32.0;

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // Floor: polished blackstone
        chunkData.setRegion(0, FLOOR_Y, 0, 16, FLOOR_HEIGHT, 16, Material.POLISHED_BLACKSTONE);

        // Ceiling: iron blocks
        chunkData.setRegion(0, CEILING_MIN_Y, 0, 16, CEILING_MAX_Y, 16, Material.IRON_BLOCK);

        // Generate corridor structure
        for (int cx = 0; cx < CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < CELLS_PER_AXIS; cz++) {
                int baseX = cx * CELL_SIZE;
                int baseZ = cz * CELL_SIZE;

                double worldCenterX = (chunkX * 16 + baseX + CELL_SIZE / 2.0) * REGION_SCALE;
                double worldCenterZ = (chunkZ * 16 + baseZ + CELL_SIZE / 2.0) * REGION_SCALE;
                double noise = SimplexNoise.noise2(seed, worldCenterX, worldCenterZ);

                placeCell(chunkData, chunkRng, baseX, baseZ, noise, seed, chunkX, chunkZ);
            }
        }

        // Redstone lamp lighting grid
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % 6 == 0 && worldZ % 6 == 0) {
                    chunkData.setBlock(x, CEILING_MIN_Y, z, Material.REDSTONE_LAMP);
                }
            }
        }

        // Redstone dust on floor surface
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                double redstoneNoise = SimplexNoise.noise2(seed + 10, worldX * 0.3, worldZ * 0.3);
                if (redstoneNoise > 0.2 && chunkData.getType(x, AIR_MIN_Y, z) == Material.AIR) {
                    chunkData.setBlock(x, AIR_MIN_Y, z, Material.REDSTONE_WIRE);
                }
            }
        }
    }

    private void placeCell(ChunkData chunkData, Random rng, int baseX, int baseZ, double noise,
                           long seed, int chunkX, int chunkZ) {
        if (noise < -0.2) {
            // Open server rack room
            for (int x = baseX; x < baseX + CELL_SIZE && x < 16; x++) {
                for (int z = baseZ; z < baseZ + CELL_SIZE && z < 16; z++) {
                    for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                }
            }
            // Place observer "server racks" facing inward on edges
            placeObserverWalls(chunkData, rng, baseX, baseZ);

        } else if (noise < 0.2) {
            // Corridor with walls
            int dir = rng.nextInt(2);
            for (int x = baseX; x < baseX + CELL_SIZE && x < 16; x++) {
                for (int z = baseZ; z < baseZ + CELL_SIZE && z < 16; z++) {
                    // Create 2-wide corridors
                    boolean isPath;
                    if (dir == 0) {
                        isPath = (z - baseZ) >= 1 && (z - baseZ) <= 2;
                    } else {
                        isPath = (x - baseX) >= 1 && (x - baseX) <= 2;
                    }
                    if (isPath) {
                        for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                            chunkData.setBlock(x, y, z, Material.AIR);
                        }
                    } else {
                        // Walls made of iron/stone bricks with embedded components
                        for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                            Material wallMat = pickWallMaterial(rng);
                            chunkData.setBlock(x, y, z, wallMat);
                        }
                    }
                }
            }
        } else {
            // Dense equipment room
            for (int x = baseX; x < baseX + CELL_SIZE && x < 16; x++) {
                for (int z = baseZ; z < baseZ + CELL_SIZE && z < 16; z++) {
                    for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                        Material mat = pickEquipmentMaterial(rng);
                        chunkData.setBlock(x, y, z, mat);
                    }
                }
            }
            // Carve a 2x3 doorway on a random side
            int side = rng.nextInt(4);
            carveDoorway(chunkData, baseX, baseZ, side);
        }
    }

    private void placeObserverWalls(ChunkData chunkData, Random rng, int baseX, int baseZ) {
        // Place observers on the perimeter of the cell facing inward
        for (int i = 0; i < CELL_SIZE; i++) {
            // North wall
            if (baseX + i < 16 && baseZ < 16 && rng.nextDouble() < 0.6) {
                chunkData.setBlock(baseX + i, AIR_MIN_Y + 1, baseZ, Material.OBSERVER);
            }
            // South wall
            if (baseX + i < 16 && baseZ + CELL_SIZE - 1 < 16 && rng.nextDouble() < 0.6) {
                chunkData.setBlock(baseX + i, AIR_MIN_Y + 1, baseZ + CELL_SIZE - 1, Material.OBSERVER);
            }
            // West wall
            if (baseX < 16 && baseZ + i < 16 && rng.nextDouble() < 0.6) {
                chunkData.setBlock(baseX, AIR_MIN_Y + 1, baseZ + i, Material.OBSERVER);
            }
            // East wall
            if (baseX + CELL_SIZE - 1 < 16 && baseZ + i < 16 && rng.nextDouble() < 0.6) {
                chunkData.setBlock(baseX + CELL_SIZE - 1, AIR_MIN_Y + 1, baseZ + i, Material.OBSERVER);
            }
        }
    }

    private Material pickWallMaterial(Random rng) {
        double roll = rng.nextDouble();
        if (roll < 0.4) return Material.IRON_BLOCK;
        if (roll < 0.6) return Material.STONE_BRICKS;
        if (roll < 0.7) return Material.OBSERVER;
        if (roll < 0.8) return Material.COMMAND_BLOCK;
        if (roll < 0.85) return Material.REPEATING_COMMAND_BLOCK;
        if (roll < 0.9) return Material.CHAIN_COMMAND_BLOCK;
        if (roll < 0.95) return Material.REDSTONE_LAMP;
        return Material.CRACKED_STONE_BRICKS;
    }

    private Material pickEquipmentMaterial(Random rng) {
        double roll = rng.nextDouble();
        if (roll < 0.3) return Material.IRON_BLOCK;
        if (roll < 0.5) return Material.OBSERVER;
        if (roll < 0.6) return Material.COMMAND_BLOCK;
        if (roll < 0.7) return Material.REDSTONE_LAMP;
        if (roll < 0.8) return Material.STONE_BRICKS;
        if (roll < 0.85) return Material.REPEATER;
        if (roll < 0.9) return Material.COMPARATOR;
        return Material.REDSTONE_BLOCK;
    }

    private void carveDoorway(ChunkData chunkData, int baseX, int baseZ, int side) {
        int x1, z1;
        switch (side) {
            case 0 -> { x1 = baseX + 1; z1 = baseZ; }
            case 1 -> { x1 = baseX + 1; z1 = baseZ + CELL_SIZE - 1; }
            case 2 -> { x1 = baseX; z1 = baseZ + 1; }
            default -> { x1 = baseX + CELL_SIZE - 1; z1 = baseZ + 1; }
        }
        for (int dx = 0; dx < 2; dx++) {
            for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                int x = (side <= 1) ? x1 + dx : x1;
                int z = (side >= 2) ? z1 + dx : z1;
                if (x >= 0 && x < 16 && z >= 0 && z < 16) {
                    chunkData.setBlock(x, y, z, Material.AIR);
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
