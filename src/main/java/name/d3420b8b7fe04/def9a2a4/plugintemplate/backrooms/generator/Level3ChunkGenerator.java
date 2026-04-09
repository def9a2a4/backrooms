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
 * Uniform iron block walls, polished blackstone floor. 3 blocks tall — claustrophobic.
 * Cell-based grid like Level 0: noise classifies regions → rectangular structures.
 * Observer blocks as server racks, redstone lamps in ceiling, redstone_block cable runs.
 */
public class Level3ChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 10;
    private static final int AIR_MIN_Y = 10;
    private static final int CEILING_MIN_Y = 13; // 3 blocks tall — claustrophobic
    private static final int CEILING_MAX_Y = 20;

    private static final int CELL_SIZE = 4;
    private static final int CELLS_PER_AXIS = 16 / CELL_SIZE;
    private static final double REGION_SCALE = 1.0 / 32.0;

    private static final int REGION_RACK = 0;     // open room with server racks
    private static final int REGION_CORRIDOR = 1;  // 2-wide corridor
    private static final int REGION_EQUIPMENT = 2; // solid with doorway
    private static final int REGION_CORNER = 3;    // L-corner walls

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();

        // Floor: polished blackstone
        chunkData.setRegion(0, FLOOR_Y, 0, 16, FLOOR_HEIGHT, 16, Material.POLISHED_BLACKSTONE);

        // Ceiling: iron blocks
        chunkData.setRegion(0, CEILING_MIN_Y, 0, 16, CEILING_MAX_Y, 16, Material.IRON_BLOCK);

        // Fill air space with iron block walls (carve out like Level 0)
        chunkData.setRegion(0, AIR_MIN_Y, 0, 16, CEILING_MIN_Y, 16, Material.IRON_BLOCK);

        // Cell-based structure placement
        for (int cx = 0; cx < CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < CELLS_PER_AXIS; cz++) {
                int baseX = cx * CELL_SIZE;
                int baseZ = cz * CELL_SIZE;

                int cellWorldCol = chunkX * CELLS_PER_AXIS + cx;
                int cellWorldRow = chunkZ * CELLS_PER_AXIS + cz;

                double worldCenterX = (chunkX * 16 + baseX + CELL_SIZE / 2.0) * REGION_SCALE;
                double worldCenterZ = (chunkZ * 16 + baseZ + CELL_SIZE / 2.0) * REGION_SCALE;
                double noise = SimplexNoise.noise2(seed, worldCenterX, worldCenterZ);

                int region = classifyRegion(noise);
                placeStructure(chunkData, baseX, baseZ, region, cellWorldCol, cellWorldRow);
            }
        }

        // Redstone lamps in ceiling on 6x6 grid
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % 6 == 0 && worldZ % 6 == 0) {
                    chunkData.setBlock(x, CEILING_MIN_Y, z, Material.REDSTONE_LAMP);
                }
            }
        }

        // Redstone block cable runs along corridor walls at floor level
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                // Place redstone_block at floor level next to walls
                if (chunkData.getType(x, AIR_MIN_Y, z) == Material.AIR) {
                    boolean adjacentWall = false;
                    if (x > 0 && chunkData.getType(x - 1, AIR_MIN_Y, z) == Material.IRON_BLOCK) adjacentWall = true;
                    if (x < 15 && chunkData.getType(x + 1, AIR_MIN_Y, z) == Material.IRON_BLOCK) adjacentWall = true;
                    if (z > 0 && chunkData.getType(x, AIR_MIN_Y, z - 1) == Material.IRON_BLOCK) adjacentWall = true;
                    if (z < 15 && chunkData.getType(x, AIR_MIN_Y, z + 1) == Material.IRON_BLOCK) adjacentWall = true;
                    // Only every other block to keep it tidy
                    if (adjacentWall && (worldX + worldZ) % 2 == 0) {
                        chunkData.setBlock(x, AIR_MIN_Y, z, Material.REDSTONE_BLOCK);
                    }
                }
            }
        }
    }

    private int classifyRegion(double noise) {
        if (noise < -0.2) return REGION_RACK;
        if (noise < 0.2) return REGION_CORRIDOR;
        if (noise < 0.45) return REGION_EQUIPMENT;
        return REGION_CORNER;
    }

    private void placeStructure(ChunkData chunkData, int baseX, int baseZ,
                                int region, int cellWorldCol, int cellWorldRow) {
        switch (region) {
            case REGION_RACK -> placeRackRoom(chunkData, baseX, baseZ, cellWorldCol, cellWorldRow);
            case REGION_CORRIDOR -> placeCorridor(chunkData, baseX, baseZ, cellWorldRow);
            case REGION_EQUIPMENT -> placeEquipmentRoom(chunkData, baseX, baseZ, cellWorldCol, cellWorldRow);
            case REGION_CORNER -> placeLCorner(chunkData, baseX, baseZ, cellWorldCol, cellWorldRow);
        }
    }

    private void placeRackRoom(ChunkData chunkData, int baseX, int baseZ,
                               int cellWorldCol, int cellWorldRow) {
        // Clear the cell
        for (int x = baseX; x < baseX + CELL_SIZE && x < 16; x++) {
            for (int z = baseZ; z < baseZ + CELL_SIZE && z < 16; z++) {
                for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }
            }
        }
        // Observer server rack along one wall edge (deterministic: north or east)
        boolean northRack = (cellWorldCol + cellWorldRow) % 2 == 0;
        for (int i = 0; i < CELL_SIZE; i++) {
            int x, z;
            if (northRack) {
                x = baseX + i;
                z = baseZ;
            } else {
                x = baseX + CELL_SIZE - 1;
                z = baseZ + i;
            }
            if (x < 16 && z < 16) {
                chunkData.setBlock(x, AIR_MIN_Y, z, Material.OBSERVER);
                chunkData.setBlock(x, AIR_MIN_Y + 1, z, Material.OBSERVER);
            }
        }
    }

    private void placeCorridor(ChunkData chunkData, int baseX, int baseZ, int cellWorldRow) {
        int dir = cellWorldRow % 2; // 0 = E-W, 1 = N-S
        for (int x = baseX; x < baseX + CELL_SIZE && x < 16; x++) {
            for (int z = baseZ; z < baseZ + CELL_SIZE && z < 16; z++) {
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
                }
            }
        }
    }

    private void placeEquipmentRoom(ChunkData chunkData, int baseX, int baseZ,
                                    int cellWorldCol, int cellWorldRow) {
        // Solid iron block (already filled), carve a 2-wide doorway on one side
        int side = Math.abs((cellWorldCol * 3 + cellWorldRow * 7) % 4);
        int x1, z1;
        switch (side) {
            case 0 -> { x1 = baseX + 1; z1 = baseZ; }
            case 1 -> { x1 = baseX + 1; z1 = baseZ + CELL_SIZE - 1; }
            case 2 -> { x1 = baseX; z1 = baseZ + 1; }
            default -> { x1 = baseX + CELL_SIZE - 1; z1 = baseZ + 1; }
        }
        for (int d = 0; d < 2; d++) {
            int x = (side <= 1) ? x1 + d : x1;
            int z = (side >= 2) ? z1 + d : z1;
            if (x >= 0 && x < 16 && z >= 0 && z < 16) {
                for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }
            }
        }
    }

    private void placeLCorner(ChunkData chunkData, int baseX, int baseZ,
                              int cellWorldCol, int cellWorldRow) {
        int corner = Math.abs((cellWorldCol * 3 + cellWorldRow * 7) % 4);
        switch (corner) {
            case 0 -> {
                placeWall(chunkData, baseX, baseZ, 0);
                placeWall(chunkData, baseX, baseZ, 2);
            }
            case 1 -> {
                placeWall(chunkData, baseX, baseZ, 0);
                placeWall(chunkData, baseX, baseZ, 3);
            }
            case 2 -> {
                placeWall(chunkData, baseX, baseZ, 1);
                placeWall(chunkData, baseX, baseZ, 2);
            }
            default -> {
                placeWall(chunkData, baseX, baseZ, 1);
                placeWall(chunkData, baseX, baseZ, 3);
            }
        }
    }

    private void placeWall(ChunkData chunkData, int baseX, int baseZ, int direction) {
        for (int i = 0; i < CELL_SIZE; i++) {
            int x, z;
            switch (direction) {
                case 0 -> { x = baseX + i; z = baseZ; }
                case 1 -> { x = baseX + i; z = baseZ + CELL_SIZE - 1; }
                case 2 -> { x = baseX; z = baseZ + i; }
                default -> { x = baseX + CELL_SIZE - 1; z = baseZ + i; }
            }
            if (x < 16 && z < 16) {
                for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                    chunkData.setBlock(x, y, z, Material.IRON_BLOCK);
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
