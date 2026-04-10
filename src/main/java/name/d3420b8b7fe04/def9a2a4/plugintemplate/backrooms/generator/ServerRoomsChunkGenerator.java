package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Level 37 — "The Server Rooms"
 * Tall dark halls on a 24x24 grid. Mostly open floor plan with sparse
 * noise-driven orthogonal walls. Redstone component chains run along
 * a world-aligned grid on the floor.
 */
public class ServerRoomsChunkGenerator extends BackroomsChunkGenerator {

    public ServerRoomsChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    // Y layout
    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 4;
    private static final int CEILING_Y = 24;        // normal ceiling
    private static final int TALL_CEILING_Y = 32;    // tall rooms
    private static final int CEILING_MAX_Y = 36;     // top of ceiling slab
    private static final int MID_FLOOR_Y = 14;       // second story floor for short rooms
    private static final int INTERIOR_SIZE = 20;      // CELL_SIZE - 2*WALL_THICK

    // Room height classes
    private static final int CLASS_SHORT = 0;
    private static final int CLASS_NORMAL = 1;
    private static final int CLASS_TALL = 2;

    // Grid constants
    private static final int CELL_SIZE = 24;
    private static final int WALL_THICK = 2;
    private static final int DOOR_WIDTH = 6;
    private static final int DOOR_START = (CELL_SIZE - DOOR_WIDTH) / 2;
    private static final int DOOR_END = DOOR_START + DOOR_WIDTH;

    // Single material — sterile white concrete
    private static final Material BLOCK = Material.WHITE_CONCRETE;
    private static final Material GLASS = Material.WHITE_STAINED_GLASS;

    // Noise scales
    private static final double WALL_NOISE_SCALE = 1.0 / 32.0;
    private static final double WALL_AXIS_SCALE = 1.0 / 24.0;
    private static final double WALL_THRESHOLD = 0.6;

    // Redstone chain grid spacing
    private static final int CHAIN_SPACING = 4;
    private static final int CHAIN_PERIOD = 3; // component, dust, dust
    private static final double CHAIN_NOISE_SCALE = 1.0 / 48.0;
    private static final double CHAIN_THRESHOLD = 0.15;
    private static final double CHAIN_EDGE_THRESHOLD = 0.25;

    private static final Material[] REDSTONE_COMPONENTS = {
        Material.REPEATER,
        Material.COMPARATOR,
        Material.REDSTONE_TORCH,
        Material.REDSTONE_LAMP,
        Material.TARGET,
    };

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                int cellX = Math.floorDiv(worldX, CELL_SIZE);
                int cellZ = Math.floorDiv(worldZ, CELL_SIZE);
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);

                int roomClass = getRoomClass(cellX, cellZ, seed);
                int ceilingY = getCeilingY(roomClass);

                boolean inWallX = localX < WALL_THICK || localX >= CELL_SIZE - WALL_THICK;
                boolean inWallZ = localZ < WALL_THICK || localZ >= CELL_SIZE - WALL_THICK;

                boolean wallRemovedX = false;
                boolean wallRemovedZ = false;
                boolean isDoorX = false;
                boolean isDoorZ = false;

                if (inWallX) {
                    int neighborCellX = localX < WALL_THICK ? cellX - 1 : cellX + 1;
                    wallRemovedX = isWallRemoved(cellX, cellZ, neighborCellX, cellZ, seed);
                    if (!wallRemovedX && localZ >= DOOR_START && localZ < DOOR_END) {
                        isDoorX = isDoorOpen(cellX, cellZ, neighborCellX, cellZ, seed);
                    }
                }
                if (inWallZ) {
                    int neighborCellZ = localZ < WALL_THICK ? cellZ - 1 : cellZ + 1;
                    wallRemovedZ = isWallRemoved(cellX, cellZ, cellX, neighborCellZ, seed);
                    if (!wallRemovedZ && localX >= DOOR_START && localX < DOOR_END) {
                        isDoorZ = isDoorOpen(cellX, cellZ, cellX, neighborCellZ, seed);
                    }
                }

                boolean isWall;
                if (inWallX && inWallZ) {
                    isWall = !(wallRemovedX && wallRemovedZ);
                } else if (inWallX) {
                    isWall = !wallRemovedX && !isDoorX;
                } else if (inWallZ) {
                    isWall = !wallRemovedZ && !isDoorZ;
                } else {
                    isWall = false;
                }

                // Sub-floor fill
                chunkData.setRegion(x, FLOOR_Y, z, x + 1, FLOOR_HEIGHT, z + 1, BLOCK);

                if (isWall) {
                    // Perimeter wall
                    for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, BLOCK);
                    }
                    // Wall-mounted redstone lamps at symmetric positions
                    int edWallX = cellEdgeDist(localX);
                    int edWallZ = cellEdgeDist(localZ);
                    if (edWallX % 6 == 0 && edWallZ % 6 == 0) {
                        chunkData.setBlock(x, FLOOR_HEIGHT + 4, z, Material.REDSTONE_LAMP);
                        chunkData.setBlock(x, ceilingY - 2, z, Material.REDSTONE_LAMP);
                        if (ceilingY > CEILING_Y) {
                            // Extra lamp for tall rooms
                            chunkData.setBlock(x, (FLOOR_HEIGHT + ceilingY) / 2, z, Material.REDSTONE_LAMP);
                        }
                    }
                } else if (isDoorX || isDoorZ) {
                    // Doorway
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, BLOCK);
                    for (int y = FLOOR_HEIGHT + 1; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                    placeArchBlock(chunkData, x, z, localX, localZ, inWallX, ceilingY);
                } else {
                    // Interior — floor + air
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, BLOCK);
                    for (int y = FLOOR_HEIGHT + 1; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }

                    // Sparse noise-based interior walls
                    if (!inWallX && !inWallZ) {
                        placeInteriorWall(chunkData, x, z, worldX, worldZ, localX, localZ, seed, ceilingY);
                    }

                    // Room-type structures for short/tall rooms
                    if (roomClass != CLASS_NORMAL) {
                        int roomType = getRoomType(cellX, cellZ, seed, roomClass);
                        placeRoomStructure(chunkData, x, z, localX, localZ, cellX, cellZ, seed, ceilingY, roomType);
                    }
                }

                // Ceiling slab
                for (int y = ceilingY; y < CEILING_MAX_Y; y++) {
                    chunkData.setBlock(x, y, z, BLOCK);
                }

                // Ceiling-mounted redstone lamps on a grid
                int edCeilX = cellEdgeDist(localX);
                int edCeilZ = cellEdgeDist(localZ);
                if (edCeilX % 4 == 0 && edCeilZ % 4 == 0 && !isWall) {
                    chunkData.setBlock(x, ceilingY, z, Material.REDSTONE_LAMP);
                }
            }
        }

        // Second pass: redstone chains on the floor
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                placeRedstoneChain(chunkData, x, z, worldX, worldZ, seed);
            }
        }
    }

    // --- Cell helpers ---

    private static int cellEdgeDist(int localPos) {
        return Math.min(localPos, CELL_SIZE - 1 - localPos);
    }

    private static int interiorEdgeDist(int interiorPos) {
        return Math.min(interiorPos, INTERIOR_SIZE - 1 - interiorPos);
    }

    private static boolean symmetricMatch(int pos, int distFromCenter) {
        int center = INTERIOR_SIZE / 2; // 10
        return pos == center - 1 - distFromCenter || pos == center + distFromCenter;
    }

    // --- Height class ---

    private int getRoomClass(int cellX, int cellZ, long seed) {
        double n = SimplexNoise.noise2(seed + 999, cellX * 0.15, cellZ * 0.15);
        if (n < -0.3) return CLASS_SHORT;
        if (n > 0.3) return CLASS_TALL;
        return CLASS_NORMAL;
    }

    private int getCeilingY(int roomClass) {
        return roomClass == CLASS_TALL ? TALL_CEILING_Y : CEILING_Y;
    }

    private Random getCellRandom(int cellX, int cellZ, long seed) {
        return new Random(seed ^ ((long) cellX * 982451653L + (long) cellZ * 472882049L));
    }

    private int getRoomType(int cellX, int cellZ, long seed, int roomClass) {
        Random rng = getCellRandom(cellX, cellZ, seed);
        int roll = rng.nextInt(100);
        switch (roomClass) {
            case CLASS_SHORT: {
                if (roll < 25) return 10; // open two-level
                if (roll < 45) return 11; // partitioned lower
                if (roll < 65) return 12; // corner stair
                if (roll < 85) return 13; // mezzanine ring
                return 14;                // dense pillars two-level
            }
            case CLASS_TALL: {
                if (roll < 30) return 20; // grand cathedral
                if (roll < 55) return 21; // atrium
                if (roll < 80) return 22; // towering columns
                return 23;                // layered
            }
            default:
                return -1; // normal — no special room structure
        }
    }

    private long wallHash(int cellAX, int cellAZ, int cellBX, int cellBZ, long seed) {
        int minX = Math.min(cellAX, cellBX);
        int maxX = Math.max(cellAX, cellBX);
        int minZ = Math.min(cellAZ, cellBZ);
        int maxZ = Math.max(cellAZ, cellBZ);
        return seed ^ ((long) minX * 735632791L + (long) maxX * 524287L
                + (long) minZ * 433494437L + (long) maxZ * 291371L);
    }

    private boolean isWallRemoved(int cellAX, int cellAZ, int cellBX, int cellBZ, long seed) {
        long h = wallHash(cellAX, cellAZ, cellBX, cellBZ, seed);
        return Math.floorMod(h, 100) < 25;
    }

    private boolean isDoorOpen(int cellAX, int cellAZ, int cellBX, int cellBZ, long seed) {
        long h = wallHash(cellAX, cellAZ, cellBX, cellBZ, seed + 7);
        return Math.floorMod(h, 100) < 80;
    }

    // --- Structure placement ---

    private void placeArchBlock(ChunkData chunkData, int x, int z,
                                int localX, int localZ,
                                boolean inWallX, int ceilingY) {
        int doorLocalPos = inWallX ? localZ : localX;
        int distFromEdge = Math.min(doorLocalPos - DOOR_START, DOOR_END - 1 - doorLocalPos);

        if (distFromEdge == 0) {
            chunkData.setBlock(x, ceilingY - 1, z, BLOCK);
            chunkData.setBlock(x, ceilingY - 2, z, BLOCK);
            chunkData.setBlock(x, ceilingY - 3, z, BLOCK);
        } else if (distFromEdge == 1) {
            chunkData.setBlock(x, ceilingY - 1, z, BLOCK);
            chunkData.setBlock(x, ceilingY - 2, z, BLOCK);
        } else if (distFromEdge == 2) {
            chunkData.setBlock(x, ceilingY - 1, z, BLOCK);
        }
    }

    private void placeInteriorWall(ChunkData chunkData, int x, int z,
                                   int worldX, int worldZ,
                                   int localX, int localZ,
                                   long seed, int ceilingY) {
        // Don't place interior walls too close to perimeter walls
        if (localX < WALL_THICK + 1 || localX >= CELL_SIZE - WALL_THICK - 1
                || localZ < WALL_THICK + 1 || localZ >= CELL_SIZE - WALL_THICK - 1) {
            return;
        }

        double noise = SimplexNoise.noise2(seed + 555L, worldX * WALL_NOISE_SCALE, worldZ * WALL_NOISE_SCALE);
        if (noise < WALL_THRESHOLD) {
            return;
        }

        // Second noise layer determines wall axis
        double axisNoise = SimplexNoise.noise2(seed + 777L, worldX * WALL_AXIS_SCALE, worldZ * WALL_AXIS_SCALE);

        if (axisNoise >= 0) {
            // N-S wall: only place if aligned to even X positions (creates 1-wide walls running along Z)
            if (Math.floorMod(worldX, 2) == 0) {
                for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                    chunkData.setBlock(x, y, z, BLOCK);
                }
            }
        } else {
            // E-W wall: only place if aligned to even Z positions
            if (Math.floorMod(worldZ, 2) == 0) {
                for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                    chunkData.setBlock(x, y, z, BLOCK);
                }
            }
        }
    }

    // --- Room structure dispatch ---

    private void placeRoomStructure(ChunkData chunkData, int x, int z,
                                    int localX, int localZ,
                                    int cellX, int cellZ, long seed,
                                    int ceilingY, int roomType) {
        int ix = localX - WALL_THICK;
        int iz = localZ - WALL_THICK;
        if (ix < 0 || ix >= INTERIOR_SIZE || iz < 0 || iz >= INTERIOR_SIZE) return;

        int edX = interiorEdgeDist(ix);
        int edZ = interiorEdgeDist(iz);

        switch (roomType) {
            // SHORT room types (two-level)
            case 10 -> placeShortOpen(chunkData, x, z, ix, iz, edX, edZ);
            case 11 -> placeShortPartitioned(chunkData, x, z, ix, iz, edX, edZ);
            case 12 -> placeShortCorner(chunkData, x, z, ix, iz, edX, edZ, cellX, cellZ, seed);
            case 13 -> placeShortMezzanine(chunkData, x, z, ix, iz, edX, edZ);
            case 14 -> placeShortDensePillars(chunkData, x, z, ix, iz, edX, edZ);
            // TALL room types (cathedral)
            case 20 -> placeTallCathedral(chunkData, x, z, ix, iz, edX, edZ, ceilingY);
            case 21 -> placeTallAtrium(chunkData, x, z, ix, iz, edX, edZ, ceilingY);
            case 22 -> placeTallColumns(chunkData, x, z, ix, iz, edX, edZ, ceilingY);
            case 23 -> placeTallLayered(chunkData, x, z, ix, iz, edX, edZ, ceilingY);
        }
    }

    // --- SHORT: two-level rooms ---

    private void placeMidFloor(ChunkData chunkData, int x, int z) {
        chunkData.setBlock(x, MID_FLOOR_Y, z, BLOCK);
    }

    /** Case 10: open two-level with center void and symmetric staircases */
    private void placeShortOpen(ChunkData chunkData, int x, int z,
                                int ix, int iz, int edX, int edZ) {
        // Center 6x6 opening: no mid-floor
        boolean centerOpening = edX >= 7 && edZ >= 7;
        // West staircase: ix=0-2, iz=2-7 ascending
        boolean westStair = ix <= 2 && iz >= 2 && iz <= 7;
        // East staircase: ix=17-19, iz=12-17 ascending (180° rotational symmetry)
        boolean eastStair = ix >= 17 && iz >= 12 && iz <= 17;

        if (centerOpening) {
            // Pillar supports at corners of opening
            if (edX == 7 && edZ == 7) {
                for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                    chunkData.setBlock(x, y, z, BLOCK);
                }
            }
        } else if (westStair) {
            int stepY = FLOOR_HEIGHT + 1 + (iz - 2) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
        } else if (eastStair) {
            int stepY = FLOOR_HEIGHT + 1 + (17 - iz) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
        } else {
            placeMidFloor(chunkData, x, z);
            // Upper level lamp
            if (edX % 5 == 0 && edZ % 5 == 0) {
                chunkData.setBlock(x, MID_FLOOR_Y, z, Material.REDSTONE_LAMP);
            }
        }
    }

    /** Case 11: partitioned lower level (server rack aisles) with stairwells */
    private void placeShortPartitioned(ChunkData chunkData, int x, int z,
                                       int ix, int iz, int edX, int edZ) {
        // Stairwells centered at north/south walls
        boolean northStair = ix >= 8 && ix <= 11 && iz <= 5;
        boolean southStair = ix >= 8 && ix <= 11 && iz >= 14;

        if (northStair) {
            int stepY = FLOOR_HEIGHT + 1 + iz * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
        } else if (southStair) {
            int stepY = FLOOR_HEIGHT + 1 + (19 - iz) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
        } else {
            placeMidFloor(chunkData, x, z);
            // Lower level partition walls (server rack aisles)
            boolean onPartition = symmetricMatch(ix, 5) || symmetricMatch(ix, 0);
            if (onPartition && iz > 5 && iz < 14) {
                for (int y = FLOOR_HEIGHT + 1; y < MID_FLOOR_Y - 1; y++) {
                    chunkData.setBlock(x, y, z, BLOCK);
                }
            }
            // Under-floor lamps
            if (edX % 4 == 0 && edZ % 4 == 0) {
                chunkData.setBlock(x, MID_FLOOR_Y - 1, z, Material.REDSTONE_LAMP);
            }
        }
    }

    /** Case 12: asymmetric corner staircase */
    private void placeShortCorner(ChunkData chunkData, int x, int z,
                                  int ix, int iz, int edX, int edZ,
                                  int cellX, int cellZ, long seed) {
        Random rng = new Random(seed ^ ((long) cellX * 111111L + (long) cellZ * 222222L));
        int corner = rng.nextInt(4);

        boolean inStairZone;
        int progress;
        switch (corner) {
            case 0 -> { inStairZone = ix <= 5 && iz <= 5; progress = ix + iz; }
            case 1 -> { inStairZone = ix >= 14 && iz <= 5; progress = (19 - ix) + iz; }
            case 2 -> { inStairZone = ix >= 14 && iz >= 14; progress = (19 - ix) + (19 - iz); }
            default -> { inStairZone = ix <= 5 && iz >= 14; progress = ix + (19 - iz); }
        }

        if (inStairZone) {
            int stepY = FLOOR_HEIGHT + 1 + Math.min(progress, 9) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 9;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
        } else {
            placeMidFloor(chunkData, x, z);
            // Upper level: accent column near center
            if (edX >= 8 && edZ >= 8) {
                for (int y = MID_FLOOR_Y + 1; y < CEILING_Y; y++) {
                    chunkData.setBlock(x, y, z, BLOCK);
                }
                chunkData.setBlock(x, MID_FLOOR_Y + 1, z, Material.REDSTONE_LAMP);
                chunkData.setBlock(x, CEILING_Y - 1, z, Material.REDSTONE_LAMP);
            }
        }
    }

    /** Case 13: mezzanine ring — perimeter balcony, open center */
    private void placeShortMezzanine(ChunkData chunkData, int x, int z,
                                     int ix, int iz, int edX, int edZ) {
        boolean onRing = edX <= 3 || edZ <= 3;
        // 2-wide staircases centered on each wall
        boolean northStair = iz <= 5 && (ix == 9 || ix == 10);
        boolean southStair = iz >= 14 && (ix == 9 || ix == 10);
        boolean westStair = ix <= 5 && (iz == 9 || iz == 10);
        boolean eastStair = ix >= 14 && (iz == 9 || iz == 10);

        if (northStair) {
            int stepY = FLOOR_HEIGHT + 1 + iz * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
        } else if (southStair) {
            int stepY = FLOOR_HEIGHT + 1 + (19 - iz) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
        } else if (westStair) {
            int stepY = FLOOR_HEIGHT + 1 + ix * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
        } else if (eastStair) {
            int stepY = FLOOR_HEIGHT + 1 + (19 - ix) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
        } else if (onRing) {
            placeMidFloor(chunkData, x, z);
            // Railing at inner edge
            if ((edX == 3 && edZ > 3) || (edZ == 3 && edX > 3)) {
                chunkData.setBlock(x, MID_FLOOR_Y + 1, z, BLOCK);
            }
        }
        // Center floor lamp
        if (edX >= 8 && edZ >= 8) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.REDSTONE_LAMP);
        }
    }

    /** Case 14: dense pillars on both levels */
    private void placeShortDensePillars(ChunkData chunkData, int x, int z,
                                        int ix, int iz, int edX, int edZ) {
        // 4 symmetric 3x3 stairwells
        boolean stairX = (ix >= 5 && ix <= 7) || (ix >= 12 && ix <= 14);
        boolean stairZ = (iz >= 5 && iz <= 7) || (iz >= 12 && iz <= 14);
        boolean inStairZone = stairX && stairZ;

        if (inStairZone) {
            int sx = (ix >= 12) ? ix - 12 : ix - 5;
            int sz = (iz >= 12) ? iz - 12 : iz - 5;
            int progress = sx + sz;
            int stepY = FLOOR_HEIGHT + 1 + Math.min(progress, 4) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 4;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
        } else {
            placeMidFloor(chunkData, x, z);
            // Dense pillar grid on both levels
            if (edX % 4 == 1 && edZ % 4 == 1) {
                for (int y = FLOOR_HEIGHT; y < MID_FLOOR_Y; y++) {
                    chunkData.setBlock(x, y, z, BLOCK);
                }
                for (int y = MID_FLOOR_Y; y < CEILING_Y; y++) {
                    chunkData.setBlock(x, y, z, BLOCK);
                }
            }
            // Lamps between pillars
            if (edX % 4 == 3 && edZ % 4 == 3) {
                chunkData.setBlock(x, FLOOR_HEIGHT + 4, z, Material.REDSTONE_LAMP);
                chunkData.setBlock(x, MID_FLOOR_Y, z, Material.REDSTONE_LAMP);
            }
        }
    }

    // --- TALL: cathedral rooms ---

    /** Case 20: grand cathedral — massive pillars with glass band + floor inlay */
    private void placeTallCathedral(ChunkData chunkData, int x, int z,
                                    int ix, int iz, int edX, int edZ, int ceilingY) {
        // 4 massive 3x3 pillars with glass mid-section
        if (edX >= 4 && edX <= 6 && edZ >= 4 && edZ <= 6) {
            for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
            // Glass band at mid-height
            int midY = (FLOOR_HEIGHT + ceilingY) / 2;
            for (int y = midY - 1; y <= midY + 1; y++) {
                chunkData.setBlock(x, y, z, GLASS);
            }
            chunkData.setBlock(x, FLOOR_HEIGHT + 8, z, Material.REDSTONE_LAMP);
            chunkData.setBlock(x, FLOOR_HEIGHT + 18, z, Material.REDSTONE_LAMP);
        }
        // Concentric floor inlay
        if (edX >= 7 && edZ >= 7) {
            if (edX == 7 || edZ == 7 || (edX >= 9 && edZ >= 9)) {
                chunkData.setBlock(x, FLOOR_HEIGHT, z, BLOCK);
            }
        }
        // Perimeter trim
        if ((edX == 1 && edZ >= 1) || (edZ == 1 && edX >= 1)) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, BLOCK);
        }
    }

    /** Case 21: atrium — outer solid pillars, inner glass pillars */
    private void placeTallAtrium(ChunkData chunkData, int x, int z,
                                 int ix, int iz, int edX, int edZ, int ceilingY) {
        boolean outerPillar = edX == 5 && edZ == 5;
        boolean innerPillar = (edX == 9 && edZ == 5) || (edX == 5 && edZ == 9);

        if (outerPillar) {
            for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
            chunkData.setBlock(x, FLOOR_HEIGHT + 10, z, Material.REDSTONE_LAMP);
            chunkData.setBlock(x, ceilingY - 4, z, Material.REDSTONE_LAMP);
        } else if (innerPillar) {
            // Inner ring uses glass
            for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, GLASS);
            }
            chunkData.setBlock(x, FLOOR_HEIGHT + 10, z, Material.REDSTONE_LAMP);
            chunkData.setBlock(x, ceilingY - 4, z, Material.REDSTONE_LAMP);
        }
        // Floor ring
        if (edX + edZ == 8 && edX >= 2 && edZ >= 2) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, BLOCK);
        }
    }

    /** Case 22: towering columns — alternating solid and glass */
    private void placeTallColumns(ChunkData chunkData, int x, int z,
                                  int ix, int iz, int edX, int edZ, int ceilingY) {
        boolean colX = symmetricMatch(ix, 3) || symmetricMatch(ix, 7);
        boolean colZ = symmetricMatch(iz, 3) || symmetricMatch(iz, 7);
        if (colX && colZ) {
            // Alternate: columns at edgeDist 3 are glass, edgeDist 7 are solid
            boolean isGlassCol = (edX == 3 || edZ == 3) && !(edX == 7 && edZ == 7);
            Material colMat = isGlassCol ? GLASS : BLOCK;
            for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, colMat);
            }
            chunkData.setBlock(x, FLOOR_HEIGHT + 12, z, Material.REDSTONE_LAMP);
        }
        if (edX == 5 && edZ == 5) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.REDSTONE_LAMP);
        }
    }

    /** Case 23: layered — glass trim bands + corner pillars */
    private void placeTallLayered(ChunkData chunkData, int x, int z,
                                  int ix, int iz, int edX, int edZ, int ceilingY) {
        // Corner 2x2 pillars (solid)
        if (edX >= 2 && edX <= 3 && edZ >= 2 && edZ <= 3) {
            for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, BLOCK);
            }
        }
        // Horizontal trim bands near walls — glass
        if (edX <= 2 || edZ <= 2) {
            chunkData.setBlock(x, FLOOR_HEIGHT + 8, z, GLASS);
            chunkData.setBlock(x, FLOOR_HEIGHT + 18, z, GLASS);
            if (edX % 4 == 0 || edZ % 4 == 0) {
                chunkData.setBlock(x, FLOOR_HEIGHT + 8, z, Material.REDSTONE_LAMP);
                chunkData.setBlock(x, FLOOR_HEIGHT + 18, z, Material.REDSTONE_LAMP);
            }
        }
        // Center floor inlay
        if (edX >= 6 && edZ >= 6) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, BLOCK);
        }
    }

    private void placeRedstoneChain(ChunkData chunkData, int x, int z,
                                    int worldX, int worldZ,
                                    long seed) {
        boolean onNSChain = Math.floorMod(worldX, CHAIN_SPACING) == 0;
        boolean onEWChain = Math.floorMod(worldZ, CHAIN_SPACING) == 0;

        if (!onNSChain && !onEWChain) {
            return;
        }

        // Noise gate: only place chains in some areas to form sparse circuit clusters
        double chainNoise = SimplexNoise.noise2(seed + 1234L, worldX * CHAIN_NOISE_SCALE, worldZ * CHAIN_NOISE_SCALE);
        if (chainNoise < CHAIN_THRESHOLD) {
            return;
        }
        // Ragged edges: randomly terminate chains in the boundary zone
        if (chainNoise < CHAIN_EDGE_THRESHOLD) {
            long edgeHash = seed ^ ((long) worldX * 314159265L + (long) worldZ * 271828182L);
            if (Math.floorMod(edgeHash, 100) < 50) {
                return;
            }
        }

        // Guard: only place on floor, not inside walls or interior walls
        Material floorBlock = chunkData.getType(x, FLOOR_HEIGHT, z);
        if (floorBlock != BLOCK) {
            return;
        }
        Material aboveFloor = chunkData.getType(x, FLOOR_HEIGHT + 1, z);
        if (aboveFloor != Material.AIR) {
            return;
        }

        // Determine if this is a component or dust position
        boolean isComponent;
        if (onNSChain && onEWChain) {
            // Intersection: always a component
            isComponent = true;
        } else if (onNSChain) {
            isComponent = Math.floorMod(worldZ, CHAIN_PERIOD) == 0;
        } else {
            isComponent = Math.floorMod(worldX, CHAIN_PERIOD) == 0;
        }

        if (isComponent) {
            // Pick a component deterministically
            long posHash = seed ^ ((long) worldX * 198491317L + (long) worldZ * 6542989L + 12345L);
            int compIndex = Math.floorMod(posHash, REDSTONE_COMPONENTS.length);
            Material component = REDSTONE_COMPONENTS[compIndex];

            if (component == Material.REDSTONE_LAMP || component == Material.TARGET) {
                // Full-block components replace the floor
                chunkData.setBlock(x, FLOOR_HEIGHT, z, component);
            } else if (component == Material.REDSTONE_TORCH) {
                chunkData.setBlock(x, FLOOR_HEIGHT + 1, z, Material.REDSTONE_TORCH);
            } else {
                // Directional components (repeater, comparator)
                BlockFace facing;
                if (onNSChain && !onEWChain) {
                    facing = Math.floorMod(worldZ, 2) == 0 ? BlockFace.NORTH : BlockFace.SOUTH;
                } else if (onEWChain && !onNSChain) {
                    facing = Math.floorMod(worldX, 2) == 0 ? BlockFace.EAST : BlockFace.WEST;
                } else {
                    facing = BlockFace.NORTH;
                }
                try {
                    org.bukkit.block.data.BlockData blockData = Bukkit.createBlockData(component);
                    if (blockData instanceof Directional directional) {
                        directional.setFacing(facing);
                    }
                    chunkData.setBlock(x, FLOOR_HEIGHT + 1, z, blockData);
                } catch (Exception e) {
                    chunkData.setBlock(x, FLOOR_HEIGHT + 1, z, component);
                }
            }
        } else {
            // Redstone dust
            chunkData.setBlock(x, FLOOR_HEIGHT + 1, z, Material.REDSTONE_WIRE);
        }
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, FLOOR_HEIGHT + 2, 8.5);
    }
}
