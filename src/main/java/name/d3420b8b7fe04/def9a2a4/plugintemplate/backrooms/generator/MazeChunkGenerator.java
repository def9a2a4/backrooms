package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * Reusable, config-driven maze chunk generator.
 * Uses a binary-tree algorithm that guarantees full connectivity
 * without cross-chunk state.
 *
 * All dimensions, materials, and exit placement are configurable
 * via generator_config in the level YAML.
 */
public class MazeChunkGenerator extends BackroomsChunkGenerator {

    // Maze geometry
    private int corridorWidth = 3;
    private int wallThickness = 3;
    private int wallHeight = 20;

    // Y layout
    private int bedrockMinY = 48;
    private int bedrockMaxY = 64;
    private int obsidianLayers = 3;
    private int barrierLayers = 10;

    // Materials
    private Material floorMaterial = Material.GRASS_BLOCK;
    private Material subFloorMaterial = Material.OBSIDIAN;
    private Material[] wallOuterMaterials = {
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES,
            Material.DARK_OAK_LEAVES, Material.BIRCH_LEAVES
    };
    private Material wallInnerMaterial = Material.GREEN_TERRACOTTA;

    // Exit holes
    private int exitMinDistance = 200;
    private int exitChance = 300;

    // Derived
    private int cellSize;
    private int floorY;
    private int wallBaseY;
    private int wallTopY;
    private int barrierBaseY;
    private int barrierTopY;

    public MazeChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
        recalcDerived();
    }

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config == null) return;

        corridorWidth = config.getInt("corridor_width", corridorWidth);
        wallThickness = config.getInt("wall_thickness", wallThickness);
        wallHeight = config.getInt("wall_height", wallHeight);
        bedrockMinY = config.getInt("bedrock_min_y", bedrockMinY);
        bedrockMaxY = config.getInt("bedrock_max_y", bedrockMaxY);
        obsidianLayers = config.getInt("obsidian_layers", obsidianLayers);
        barrierLayers = config.getInt("barrier_layers", barrierLayers);

        String floorStr = config.getString("floor_material");
        if (floorStr != null) {
            Material m = Material.matchMaterial(floorStr);
            if (m != null) floorMaterial = m;
        }
        String subFloorStr = config.getString("sub_floor_material");
        if (subFloorStr != null) {
            Material m = Material.matchMaterial(subFloorStr);
            if (m != null) subFloorMaterial = m;
        }
        String innerStr = config.getString("wall_inner_material");
        if (innerStr != null) {
            Material m = Material.matchMaterial(innerStr);
            if (m != null) wallInnerMaterial = m;
        }

        List<String> outerList = config.getStringList("wall_outer_materials");
        if (!outerList.isEmpty()) {
            Material[] parsed = outerList.stream()
                    .map(Material::matchMaterial)
                    .filter(m -> m != null)
                    .toArray(Material[]::new);
            if (parsed.length > 0) wallOuterMaterials = parsed;
        }

        exitMinDistance = config.getInt("exit_min_distance", exitMinDistance);
        exitChance = config.getInt("exit_chance", exitChance);

        recalcDerived();
    }

    private void recalcDerived() {
        cellSize = corridorWidth + wallThickness;
        floorY = bedrockMaxY + obsidianLayers;       // grass surface
        wallBaseY = floorY + 1;                       // first wall block
        wallTopY = wallBaseY + wallHeight;             // one above last wall block
        barrierBaseY = wallTopY;
        barrierTopY = barrierBaseY + barrierLayers;
    }

    @Override
    public int getSpawnY() {
        return floorY + 1; // feet on floor
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        // Spawn in the center of the origin cell's corridor
        double center = corridorWidth / 2.0;
        return new Location(world, center, floorY + 1, center);
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // --- Foundation layers ---
                // Bedrock
                for (int y = bedrockMinY; y < bedrockMaxY; y++) {
                    chunkData.setBlock(x, y, z, Material.BEDROCK);
                }
                // Sub-floor (obsidian)
                for (int y = bedrockMaxY; y < floorY; y++) {
                    chunkData.setBlock(x, y, z, subFloorMaterial);
                }

                // --- Maze logic ---
                boolean isWall = isWallAt(worldX, worldZ, seed);

                // Floor
                chunkData.setBlock(x, floorY, z, floorMaterial);

                if (isWall) {
                    // Build hedge wall
                    placeWallColumn(chunkData, x, z, worldX, worldZ, seed);
                } else {
                    // Corridor — check for exit hole
                    if (shouldPlaceExit(worldX, worldZ, seed)) {
                        // Punch hole from floor down to bedrock top
                        chunkData.setBlock(x, floorY, z, Material.AIR);
                        for (int y = bedrockMinY; y < floorY; y++) {
                            chunkData.setBlock(x, y, z, Material.AIR);
                        }
                    }
                }

                // --- Barrier ceiling ---
                for (int y = barrierBaseY; y < barrierTopY; y++) {
                    chunkData.setBlock(x, y, z, Material.BARRIER);
                }
            }
        }
    }

    /**
     * Determine whether the block at (worldX, worldZ) is part of a maze wall.
     */
    private boolean isWallAt(int worldX, int worldZ, long seed) {
        int cellX = Math.floorDiv(worldX, cellSize);
        int cellZ = Math.floorDiv(worldZ, cellSize);
        int localX = Math.floorMod(worldX, cellSize);
        int localZ = Math.floorMod(worldZ, cellSize);

        boolean inCorridorX = localX < corridorWidth;
        boolean inCorridorZ = localZ < corridorWidth;

        // Corner of cell (wall on both axes) — always wall
        if (!inCorridorX && !inCorridorZ) {
            return true;
        }

        // Pure corridor — never wall
        if (inCorridorX && inCorridorZ) {
            return false;
        }

        // West wall zone (localX >= corridorWidth, localZ < corridorWidth)
        // This wall belongs to the cell to the east: (cellX+1, cellZ).
        // It is carved if that cell chose to carve west.
        if (!inCorridorX) {
            int eastCellX = cellX + 1;
            return !carvesWest(eastCellX, cellZ, seed);
        }

        // North wall zone (localZ >= corridorWidth, localX < corridorWidth)
        // This wall belongs to the cell to the south: (cellX, cellZ+1).
        // It is carved if that cell chose to carve north.
        int southCellZ = cellZ + 1;
        return !carvesNorth(cellX, southCellZ, seed);
    }

    /**
     * Binary tree decision: each cell carves either north or west.
     * Boundary cells are forced to carve in the only available direction.
     */
    private boolean carvesNorth(int cellX, int cellZ, long seed) {
        // Cell at Z=0 cannot carve north (nothing north of it in the negative direction)
        // Actually for binary tree: cell carves toward -X or -Z.
        // At cellZ <= 0 boundary, must carve west (toward -X).
        // At cellX <= 0 boundary, must carve north (toward -Z).
        if (cellZ <= 0 && cellX <= 0) {
            // Origin cell — no carving needed (but won't be queried normally)
            return false;
        }
        if (cellX <= 0) return true;  // must carve north
        if (cellZ <= 0) return false; // must carve west

        long hash = seed ^ ((long) cellX * 735632791L + (long) cellZ * 524287L + 31L);
        return Math.floorMod(hash, 2) == 0;
    }

    private boolean carvesWest(int cellX, int cellZ, long seed) {
        return !carvesNorth(cellX, cellZ, seed);
    }

    /**
     * Place a wall column of hedge blocks at this position.
     */
    private void placeWallColumn(ChunkData chunkData, int x, int z,
                                 int worldX, int worldZ, long seed) {
        // Determine position within the wall to decide material layering.
        // For walls running along X axis (north wall zone): depth is localZ - corridorWidth
        // For walls running along Z axis (west wall zone): depth is localX - corridorWidth
        int localX = Math.floorMod(worldX, cellSize);
        int localZ = Math.floorMod(worldZ, cellSize);

        boolean inWestWall = localX >= corridorWidth && localZ < corridorWidth;
        boolean inNorthWall = localZ >= corridorWidth && localX < corridorWidth;

        int depth;
        if (inWestWall) {
            depth = localX - corridorWidth;
        } else if (inNorthWall) {
            depth = localZ - corridorWidth;
        } else {
            // Corner block — use minimum depth
            depth = Math.min(localX - corridorWidth, localZ - corridorWidth);
        }

        Material material = getWallMaterial(depth, worldX, worldZ, seed);
        BlockData blockData = material.createBlockData();
        if (blockData instanceof org.bukkit.block.data.type.Leaves leaves) {
            leaves.setPersistent(true);
        }

        for (int y = wallBaseY; y < wallTopY; y++) {
            chunkData.setBlock(x, y, z, blockData);
        }
    }

    /**
     * Determine wall material based on depth within the wall.
     * Outer layers get random leaf types, inner layers get the core material.
     */
    private Material getWallMaterial(int depth, int worldX, int worldZ, long seed) {
        // Outer layer: depth 0 or depth (wallThickness - 1)
        boolean isOuter = depth == 0 || depth == wallThickness - 1;

        if (isOuter) {
            // Pick leaf type based on wall segment hash
            // Quantize position to wall segments for consistent material per segment
            int segX = Math.floorDiv(worldX, cellSize);
            int segZ = Math.floorDiv(worldZ, cellSize);
            long hash = seed ^ ((long) segX * 198491317L + (long) segZ * 6542989L + depth * 17L);
            int idx = (int) Math.floorMod(hash, wallOuterMaterials.length);
            return wallOuterMaterials[idx];
        }

        return wallInnerMaterial;
    }

    /**
     * Check if a 1x1 exit hole should be placed at this corridor position.
     * Only eligible in corridor centers, far enough from origin.
     */
    private boolean shouldPlaceExit(int worldX, int worldZ, long seed) {
        if (exitChance <= 0) return false;

        // Must be far enough from origin
        double dist = Math.sqrt((double) worldX * worldX + (double) worldZ * worldZ);
        if (dist < exitMinDistance) return false;

        // Must be at the center of a corridor cell
        int localX = Math.floorMod(worldX, cellSize);
        int localZ = Math.floorMod(worldZ, cellSize);
        int centerX = corridorWidth / 2;
        int centerZ = corridorWidth / 2;
        if (localX != centerX || localZ != centerZ) return false;

        // Hash-based probability
        int cellX = Math.floorDiv(worldX, cellSize);
        int cellZ = Math.floorDiv(worldZ, cellSize);
        long hash = seed ^ ((long) cellX * 433494437L + (long) cellZ * 291371L + 0xE517L);
        return Math.floorMod(hash, exitChance) == 0;
    }
}
