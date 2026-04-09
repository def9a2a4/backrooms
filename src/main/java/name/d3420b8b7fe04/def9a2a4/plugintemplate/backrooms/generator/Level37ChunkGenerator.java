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
 * Rectangular rooms on a 24-block grid with large palette regions.
 * Shallow pools flush with floor tiles, skylights, arches, lots of light.
 * Overworld at fixed midday.
 */
public class Level37ChunkGenerator extends ChunkGenerator {

    // Y layout
    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 8;   // floor surface
    private static final int CEILING_Y = 20;     // bottom of ceiling slab
    private static final int CEILING_MAX_Y = 26; // top of ceiling slab

    // Grid constants
    private static final int CELL_SIZE = 24;
    private static final int WALL_THICK = 2;
    private static final int DOOR_WIDTH = 6;
    private static final int DOOR_START = (CELL_SIZE - DOOR_WIDTH) / 2; // 9
    private static final int DOOR_END = DOOR_START + DOOR_WIDTH;        // 15

    // Palette region size (~7 chunks)
    private static final int REGION_SIZE = 112;

    // Room types
    private static final int ROOM_OPEN = 0;
    private static final int ROOM_PILLARED = 1;
    private static final int ROOM_SUBDIVIDED = 2;
    private static final int ROOM_GALLERY = 3;

    // Pool types
    private static final int POOL_DRY = 0;
    private static final int POOL_SHALLOW = 1;
    private static final int POOL_DEEP = 2;

    // Palette indices
    private static final int PAL_WALL = 0;
    private static final int PAL_FLOOR = 1;
    private static final int PAL_PILLAR = 2;
    private static final int PAL_CEILING = 3;

    private static final Material[][] PALETTES = {
        // Quartz
        { Material.QUARTZ_BLOCK, Material.SMOOTH_QUARTZ, Material.QUARTZ_PILLAR, Material.QUARTZ_BRICKS },
        // White concrete
        { Material.WHITE_CONCRETE, Material.WHITE_CONCRETE, Material.WHITE_CONCRETE, Material.WHITE_CONCRETE },
        // Smooth stone
        { Material.SMOOTH_STONE, Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.SMOOTH_STONE },
        // Dark prismarine
        { Material.DARK_PRISMARINE, Material.DARK_PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE },
        // End stone bricks
        { Material.END_STONE_BRICKS, Material.END_STONE_BRICKS, Material.END_STONE, Material.END_STONE_BRICKS },
    };

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                Material[] palette = getPalette(worldX, worldZ, seed);

                int cellX = Math.floorDiv(worldX, CELL_SIZE);
                int cellZ = Math.floorDiv(worldZ, CELL_SIZE);
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);

                boolean inWallX = localX < WALL_THICK || localX >= CELL_SIZE - WALL_THICK;
                boolean inWallZ = localZ < WALL_THICK || localZ >= CELL_SIZE - WALL_THICK;

                // Check if this wall position is a doorway
                boolean isDoorX = false;
                boolean isDoorZ = false;
                if (inWallX && localZ >= DOOR_START && localZ < DOOR_END) {
                    // Doorway on X wall — check if open
                    int neighborCellX = localX < WALL_THICK ? cellX - 1 : cellX + 1;
                    isDoorX = isDoorOpen(cellX, cellZ, neighborCellX, cellZ, seed);
                }
                if (inWallZ && localX >= DOOR_START && localX < DOOR_END) {
                    int neighborCellZ = localZ < WALL_THICK ? cellZ - 1 : cellZ + 1;
                    isDoorZ = isDoorOpen(cellX, cellZ, cellX, neighborCellZ, seed);
                }

                boolean isWall = (inWallX || inWallZ) && !(isDoorX || isDoorZ);
                // Corner blocks are always wall even if both axes have doors
                if (inWallX && inWallZ) {
                    isWall = true;
                }

                // Sub-floor fill
                chunkData.setRegion(x, FLOOR_Y, z, x + 1, FLOOR_HEIGHT, z + 1, palette[PAL_FLOOR]);

                if (isWall) {
                    // Floor to ceiling wall
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette[PAL_WALL]);
                    for (int y = FLOOR_HEIGHT + 1; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_WALL]);
                    }
                    // Embedded wall lights every 8 blocks
                    if (Math.floorMod(worldX, 8) == 0 && Math.floorMod(worldZ, 8) == 0) {
                        chunkData.setBlock(x, FLOOR_HEIGHT + 3, z, Material.SEA_LANTERN);
                    }
                } else if (isDoorX || isDoorZ) {
                    // Doorway: floor tile + air + arch at top
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette[PAL_FLOOR]);
                    for (int y = FLOOR_HEIGHT + 1; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                    // Arch: fill top blocks at doorway edges
                    placeArchBlock(chunkData, x, z, localX, localZ, inWallX, inWallZ, palette);
                } else {
                    // Interior: floor tile
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette[PAL_FLOOR]);
                    for (int y = FLOOR_HEIGHT + 1; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }

                    // Room interior features (pillars, subdivisions)
                    placeInterior(chunkData, x, z, localX, localZ, cellX, cellZ, seed, palette);

                    // Water pools
                    placePool(chunkData, x, z, localX, localZ, cellX, cellZ, seed, palette);
                }

                // Ceiling
                boolean skylight = isSkylightRoom(cellX, cellZ, seed);
                boolean inSkylightZone = skylight
                        && localX >= 8 && localX < 16
                        && localZ >= 8 && localZ < 16;

                if (inSkylightZone && !isWall) {
                    // Open to sky
                    for (int y = CEILING_Y; y < CEILING_MAX_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                } else {
                    for (int y = CEILING_Y; y < CEILING_MAX_Y; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_CEILING]);
                    }
                }
            }
        }

        // Ceiling sea lantern grid
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (Math.floorMod(worldX, 4) == 0 && Math.floorMod(worldZ, 4) == 0) {
                    if (chunkData.getType(x, CEILING_Y, z) != Material.AIR) {
                        chunkData.setBlock(x, CEILING_Y, z, Material.SEA_LANTERN);
                    }
                }
            }
        }

        // Floor sea lanterns under water
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (Math.floorMod(worldX, 6) == 0 && Math.floorMod(worldZ, 6) == 0) {
                    if (chunkData.getType(x, FLOOR_HEIGHT, z) == Material.WATER) {
                        // Find bottom of pool and place lantern there
                        for (int y = FLOOR_HEIGHT; y >= FLOOR_Y; y--) {
                            Material mat = chunkData.getType(x, y, z);
                            if (mat != Material.WATER) {
                                chunkData.setBlock(x, y, z, Material.SEA_LANTERN);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private Material[] getPalette(int worldX, int worldZ, long seed) {
        int regionX = Math.floorDiv(worldX, REGION_SIZE);
        int regionZ = Math.floorDiv(worldZ, REGION_SIZE);
        long regionSeed = seed ^ (regionX * 198491317L + regionZ * 6542989L);
        int paletteIndex = (int) (Math.abs(regionSeed) % PALETTES.length);
        return PALETTES[paletteIndex];
    }

    private Random getCellRandom(int cellX, int cellZ, long seed) {
        return new Random(seed ^ ((long) cellX * 982451653L + (long) cellZ * 472882049L));
    }

    private boolean isDoorOpen(int cellAX, int cellAZ, int cellBX, int cellBZ, long seed) {
        // Symmetric: always use min/max so both sides agree
        int minX = Math.min(cellAX, cellBX);
        int maxX = Math.max(cellAX, cellBX);
        int minZ = Math.min(cellAZ, cellBZ);
        int maxZ = Math.max(cellAZ, cellBZ);
        long doorSeed = seed ^ ((long) minX * 735632791L + (long) maxX * 524287L
                + (long) minZ * 433494437L + (long) maxZ * 291371L);
        // ~75% of doors are open
        return (Math.abs(doorSeed) % 100) < 75;
    }

    private int getRoomType(int cellX, int cellZ, long seed) {
        Random rng = getCellRandom(cellX, cellZ, seed);
        int roll = rng.nextInt(100);
        if (roll < 40) return ROOM_OPEN;
        if (roll < 65) return ROOM_PILLARED;
        if (roll < 85) return ROOM_SUBDIVIDED;
        return ROOM_GALLERY;
    }

    private int getPoolType(int cellX, int cellZ, long seed) {
        Random rng = getCellRandom(cellX, cellZ, seed);
        rng.nextInt(); // skip one to decorrelate from room type
        int roll = rng.nextInt(100);
        if (roll < 30) return POOL_DRY;
        if (roll < 80) return POOL_SHALLOW;
        return POOL_DEEP;
    }

    private boolean isSkylightRoom(int cellX, int cellZ, long seed) {
        Random rng = getCellRandom(cellX, cellZ, seed);
        rng.nextInt(); rng.nextInt(); // skip to decorrelate
        return rng.nextInt(100) < 20;
    }

    private void placeArchBlock(ChunkData chunkData, int x, int z,
                                int localX, int localZ,
                                boolean inWallX, boolean inWallZ,
                                Material[] palette) {
        // Arch shoulders at the edges of the doorway opening
        int doorLocalPos;
        if (inWallX) {
            // Door runs along Z axis
            doorLocalPos = localZ;
        } else {
            // Door runs along X axis
            doorLocalPos = localX;
        }
        int distFromEdge = Math.min(doorLocalPos - DOOR_START, DOOR_END - 1 - doorLocalPos);

        if (distFromEdge == 0) {
            // Outermost door blocks: arch shoulder (2 blocks down from ceiling)
            chunkData.setBlock(x, CEILING_Y - 1, z, palette[PAL_PILLAR]);
            chunkData.setBlock(x, CEILING_Y - 2, z, palette[PAL_PILLAR]);
        } else if (distFromEdge == 1) {
            // Second blocks: just 1 block down
            chunkData.setBlock(x, CEILING_Y - 1, z, palette[PAL_PILLAR]);
        }
        // Inner blocks: fully open (already set to air above)
    }

    private void placeInterior(ChunkData chunkData, int x, int z,
                               int localX, int localZ,
                               int cellX, int cellZ, long seed,
                               Material[] palette) {
        int roomType = getRoomType(cellX, cellZ, seed);
        // Interior zone (inside the walls)
        int interiorStart = WALL_THICK;
        int interiorEnd = CELL_SIZE - WALL_THICK;
        int interiorLocalX = localX - interiorStart;
        int interiorLocalZ = localZ - interiorStart;
        int interiorSize = interiorEnd - interiorStart; // 20

        switch (roomType) {
            case ROOM_PILLARED -> {
                // 4 pillars at roughly quarter-points of the room
                int q1 = interiorSize / 4;      // 5
                int q3 = interiorSize * 3 / 4;  // 15
                boolean isPillarX = (interiorLocalX == q1 || interiorLocalX == q3);
                boolean isPillarZ = (interiorLocalZ == q1 || interiorLocalZ == q3);
                if (isPillarX && isPillarZ) {
                    for (int y = FLOOR_HEIGHT + 1; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_PILLAR]);
                    }
                    // Don't place water at pillar bases
                }
            }
            case ROOM_SUBDIVIDED -> {
                // Cross-wall at the center of the room dividing into 4 sub-rooms
                int mid = interiorSize / 2; // 10
                boolean onCrossX = (interiorLocalX == mid || interiorLocalX == mid + 1);
                boolean onCrossZ = (interiorLocalZ == mid || interiorLocalZ == mid + 1);
                if (onCrossX || onCrossZ) {
                    // Doorways in cross-walls: 4-block gaps centered in each half
                    int halfCenter = interiorSize / 4; // 5
                    boolean inDoorGap;
                    if (onCrossX && !onCrossZ) {
                        inDoorGap = Math.abs(interiorLocalZ - halfCenter) <= 1
                                || Math.abs(interiorLocalZ - (interiorSize - halfCenter)) <= 1;
                    } else if (onCrossZ && !onCrossX) {
                        inDoorGap = Math.abs(interiorLocalX - halfCenter) <= 1
                                || Math.abs(interiorLocalX - (interiorSize - halfCenter)) <= 1;
                    } else {
                        // Intersection of cross walls — always wall
                        inDoorGap = false;
                    }
                    if (!inDoorGap) {
                        for (int y = FLOOR_HEIGHT + 1; y < CEILING_Y; y++) {
                            chunkData.setBlock(x, y, z, palette[PAL_WALL]);
                        }
                    }
                }
            }
            case ROOM_GALLERY -> {
                // Half-height walls creating partial partitions at 1/3 and 2/3
                int t1 = interiorSize / 3;
                int t2 = interiorSize * 2 / 3;
                boolean onPartition = (interiorLocalX == t1 || interiorLocalX == t2)
                        && (interiorLocalZ > 2 && interiorLocalZ < interiorSize - 3);
                if (onPartition) {
                    // Half-height wall (6 blocks, doesn't reach ceiling)
                    for (int y = FLOOR_HEIGHT + 1; y < FLOOR_HEIGHT + 7; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_WALL]);
                    }
                }
            }
            // ROOM_OPEN: nothing to place
        }
    }

    private void placePool(ChunkData chunkData, int x, int z,
                           int localX, int localZ,
                           int cellX, int cellZ, long seed,
                           Material[] palette) {
        // Don't place water if there's already a pillar/wall here
        if (chunkData.getType(x, FLOOR_HEIGHT + 1, z) != Material.AIR) {
            return;
        }

        int poolType = getPoolType(cellX, cellZ, seed);
        if (poolType == POOL_DRY) {
            return;
        }

        // Keep a 1-block dry border inside walls
        int interiorStart = WALL_THICK + 1;
        int interiorEnd = CELL_SIZE - WALL_THICK - 1;
        if (localX < interiorStart || localX >= interiorEnd
                || localZ < interiorStart || localZ >= interiorEnd) {
            return;
        }

        if (poolType == POOL_SHALLOW) {
            // Dig 1 block below floor, fill with water flush at floor level
            chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
            chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, palette[PAL_FLOOR]);
        } else if (poolType == POOL_DEEP) {
            // Dig 3 blocks below floor
            chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
            chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.WATER);
            chunkData.setBlock(x, FLOOR_HEIGHT - 2, z, Material.WATER);
            chunkData.setBlock(x, FLOOR_HEIGHT - 3, z, palette[PAL_FLOOR]);
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
                return Biome.PLAINS;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.PLAINS);
            }
        };
    }
}
