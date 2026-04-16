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
 * Uses independent per-wall hash decisions for maze connectivity,
 * producing an unbiased random maze without cross-chunk state.
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

    // Maze openness: percentage of wall segments that are carved open (0-100)
    private int wallOpenChance = 55;

    // Materials
    private Material floorMaterial = Material.GRASS_BLOCK;
    private Material subFloorMaterial = Material.OBSIDIAN;
    private Material[] wallOuterMaterials = {
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES,
            Material.DARK_OAK_LEAVES, Material.BIRCH_LEAVES
    };
    private Material wallInnerMaterial = Material.DARK_OAK_LOG;

    // Exit holes
    private int exitMinDistance = 200;
    private int exitChance = 60;

    // Teleport ceiling offset above wallTopY
    private int teleportCeilingOffset = 0;

    // Ground decoration — weighted entries; totalDecorationWeight is the sum of all weights
    private Material[] decorationMaterials = {
            Material.MOSS_CARPET, Material.LEAF_LITTER,
            Material.SHORT_GRASS, Material.FERN, Material.DEAD_BUSH,
            Material.RED_MUSHROOM, Material.BROWN_MUSHROOM,
            Material.FIREFLY_BUSH
    };
    private int[] decorationWeights = { 15, 25, 15, 10, 5, 4, 4, 1 };
    private int totalDecorationWeight = 79;
    private int decorationEmptyChance = 50; // percent chance of nothing (0-100)

    // Derived
    private int cellSize;
    private int floorY;
    private int wallBaseY;
    private int wallTopY;

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
        wallOpenChance = config.getInt("wall_open_chance", wallOpenChance);
        teleportCeilingOffset = config.getInt("teleport_ceiling_offset", teleportCeilingOffset);

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

        decorationEmptyChance = config.getInt("decoration_empty_chance", decorationEmptyChance);
        ConfigurationSection decoSection = config.getConfigurationSection("ground_decorations");
        if (decoSection != null) {
            var keys = decoSection.getKeys(false);
            var mats = new java.util.ArrayList<Material>();
            var weights = new java.util.ArrayList<Integer>();
            for (String key : keys) {
                Material m = Material.matchMaterial(key);
                if (m != null) {
                    mats.add(m);
                    weights.add(decoSection.getInt(key, 1));
                }
            }
            if (!mats.isEmpty()) {
                decorationMaterials = mats.toArray(new Material[0]);
                decorationWeights = weights.stream().mapToInt(Integer::intValue).toArray();
                totalDecorationWeight = 0;
                for (int w : decorationWeights) totalDecorationWeight += w;
            }
        }

        recalcDerived();
    }

    private void recalcDerived() {
        cellSize = corridorWidth + wallThickness;
        floorY = bedrockMaxY + obsidianLayers;       // grass surface
        wallBaseY = floorY + 1;                       // first wall block
        wallTopY = wallBaseY + wallHeight;             // one above last wall block
    }

    @Override
    public int getSpawnY() {
        return floorY + 1; // feet on floor
    }

    /** Y above which the above_y exit trigger should fire. */
    public int getTeleportCeilingY() {
        return wallTopY + teleportCeilingOffset;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
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
                for (int y = bedrockMinY; y < bedrockMaxY; y++) {
                    chunkData.setBlock(x, y, z, Material.BEDROCK);
                }
                for (int y = bedrockMaxY; y < floorY; y++) {
                    chunkData.setBlock(x, y, z, subFloorMaterial);
                }

                // --- Maze logic ---
                boolean isWall = isWallAt(worldX, worldZ, seed);

                chunkData.setBlock(x, floorY, z, floorMaterial);

                if (isWall) {
                    placeWallColumn(chunkData, x, z, worldX, worldZ, seed);
                } else {
                    if (shouldPlaceExit(worldX, worldZ, seed)) {
                        // Punch hole through everything
                        chunkData.setBlock(x, floorY, z, Material.AIR);
                        for (int y = bedrockMinY; y < floorY; y++) {
                            chunkData.setBlock(x, y, z, Material.AIR);
                        }
                    } else {
                        placeGroundDecoration(chunkData, x, z, worldX, worldZ, seed);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------
    //  Maze topology — independent per-wall hash decisions
    // ---------------------------------------------------------------

    /**
     * Determine whether the block at (worldX, worldZ) is part of a maze wall.
     */
    private boolean isWallAt(int worldX, int worldZ, long seed) {
        int localX = Math.floorMod(worldX, cellSize);
        int localZ = Math.floorMod(worldZ, cellSize);

        boolean inCorridorX = localX < corridorWidth;
        boolean inCorridorZ = localZ < corridorWidth;

        // Corner of cell (wall on both axes) — always wall pillar
        if (!inCorridorX && !inCorridorZ) {
            return true;
        }

        // Pure corridor — never wall
        if (inCorridorX && inCorridorZ) {
            return false;
        }

        int cellX = Math.floorDiv(worldX, cellSize);
        int cellZ = Math.floorDiv(worldZ, cellSize);

        // East wall of cell (cellX, cellZ) — separates this cell from (cellX+1, cellZ)
        if (!inCorridorX) {
            return !isWallOpen(cellX, cellZ, 0, seed);
        }

        // South wall of cell (cellX, cellZ) — separates this cell from (cellX, cellZ+1)
        return !isWallOpen(cellX, cellZ, 1, seed);
    }

    /**
     * Independently decide whether a wall segment is open (carved).
     * Each wall segment gets a deterministic hash; open with probability wallOpenChance/100.
     */
    private boolean isWallOpen(int cellX, int cellZ, int wallType, long seed) {
        long h = seed;
        h ^= (long) cellX * 0x517cc1b727220a95L;
        h ^= (long) cellZ * 0x6c62272e07bb0142L;
        h += wallType * 0x6364136223846793L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return Math.floorMod(h, 100) < wallOpenChance;
    }

    // ---------------------------------------------------------------
    //  Wall column placement
    // ---------------------------------------------------------------

    /**
     * Place a wall column.
     * Inner material (logs) only if all 4 cardinal neighbors are also wall,
     * ensuring it's never visible from a corridor. Otherwise, IID leaf blocks.
     */
    private void placeWallColumn(ChunkData chunkData, int x, int z,
                                 int worldX, int worldZ, long seed) {
        boolean allNeighborsWall =
                isWallAt(worldX - 1, worldZ, seed) &&
                isWallAt(worldX + 1, worldZ, seed) &&
                isWallAt(worldX, worldZ - 1, seed) &&
                isWallAt(worldX, worldZ + 1, seed);

        if (allNeighborsWall) {
            for (int y = wallBaseY; y < wallTopY; y++) {
                chunkData.setBlock(x, y, z, wallInnerMaterial);
            }
        } else {
            for (int y = wallBaseY; y < wallTopY; y++) {
                Material leafMat = getRandomLeaf(worldX, y, worldZ, seed);
                BlockData blockData = leafMat.createBlockData();
                if (blockData instanceof org.bukkit.block.data.type.Leaves leaves) {
                    leaves.setPersistent(true);
                }
                chunkData.setBlock(x, y, z, blockData);
            }
        }
    }

    /** IID sample a leaf material per block position. */
    private Material getRandomLeaf(int worldX, int y, int worldZ, long seed) {
        long h = seed;
        h ^= (long) worldX * 0x517cc1b727220a95L;
        h ^= (long) y * 0x9e3779b97f4a7c15L;
        h ^= (long) worldZ * 0x6c62272e07bb0142L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        int idx = (int) Math.floorMod(h, wallOuterMaterials.length);
        return wallOuterMaterials[idx];
    }

    // ---------------------------------------------------------------
    //  Ground decoration
    // ---------------------------------------------------------------

    private void placeGroundDecoration(ChunkData chunkData, int x, int z,
                                       int worldX, int worldZ, long seed) {
        if (decorationMaterials.length == 0 || totalDecorationWeight <= 0) return;
        long h = blockHash(worldX, worldZ, seed, 0xDE_C0);
        // Empty chance check
        if (Math.floorMod(h, 100) < decorationEmptyChance) return;
        // Weighted selection
        int roll = (int) Math.floorMod(h >>> 16, totalDecorationWeight);
        Material mat = decorationMaterials[0];
        int cumulative = 0;
        for (int i = 0; i < decorationMaterials.length; i++) {
            cumulative += decorationWeights[i];
            if (roll < cumulative) {
                mat = decorationMaterials[i];
                break;
            }
        }
        BlockData blockData = mat.createBlockData();
        if (blockData instanceof org.bukkit.block.data.type.Leaves leaves) {
            leaves.setPersistent(true);
        }
        chunkData.setBlock(x, floorY + 1, z, blockData);
    }

    // ---------------------------------------------------------------
    //  Exit holes
    // ---------------------------------------------------------------

    /**
     * Check if a 1x1 exit hole should be placed at this corridor position.
     * Only eligible in corridor centers, far enough from origin.
     */
    private boolean shouldPlaceExit(int worldX, int worldZ, long seed) {
        if (exitChance <= 0) return false;

        double dist = Math.sqrt((double) worldX * worldX + (double) worldZ * worldZ);
        if (dist < exitMinDistance) return false;

        // Must be at the center of a corridor cell
        int localX = Math.floorMod(worldX, cellSize);
        int localZ = Math.floorMod(worldZ, cellSize);
        int centerX = corridorWidth / 2;
        int centerZ = corridorWidth / 2;
        if (localX != centerX || localZ != centerZ) return false;

        int cellX = Math.floorDiv(worldX, cellSize);
        int cellZ = Math.floorDiv(worldZ, cellSize);
        long hash = seed ^ ((long) cellX * 433494437L + (long) cellZ * 291371L + 0xE517L);
        return Math.floorMod(hash, exitChance) == 0;
    }

    // ---------------------------------------------------------------
    //  Utility
    // ---------------------------------------------------------------

    private static long blockHash(int worldX, int worldZ, long seed, int salt) {
        long h = seed;
        h ^= (long) worldX * 0x517cc1b727220a95L;
        h ^= (long) worldZ * 0x6c62272e07bb0142L;
        h += salt * 0x6364136223846793L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }
}
