package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.type.Slab;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Level 64637 — "The Library"
 * Inspired by Borges' Library of Babel. 7 vertical layers of endless 16x16 rooms
 * with floor-to-ceiling bookshelves. Three room variants: empty, center pillar
 * (with chiseled bookshelves), and staircase (connecting 2-3 adjacent layers).
 *
 * Layers 0 and 6 are identical (seed uses layer % 6). The world loops vertically.
 *
 * Staircase geometry: 6x6 center box decomposed into 3x3 grid of 2x2 quadrants.
 * Center 2x2 = open shaft (air). Corners = flat oak slabs. Faces = half-step (top/bottom slabs).
 * blockY = startY + offset + 6*r. One full revolution = 6 blocks of height.
 */
public class Level64637ChunkGenerator extends BackroomsChunkGenerator {

    // ── Geometry ─────────────────────────────────────────────────────────

    private static final int CELL_SIZE = 16;
    private static final int WALL_THICKNESS = 1;

    // Multi-layer layout
    public static final int NUM_LAYERS = 7;
    public static final int FLOOR_SPACING = 10;
    private static final int SOLID_THICKNESS = 3;
    private static final int CAP_MIN_Y = NUM_LAYERS * FLOOR_SPACING;  // 70
    private static final int CAP_MAX_Y = CAP_MIN_Y + SOLID_THICKNESS; // 73

    // Per-layer offsets (relative to layerBaseY = layer * FLOOR_SPACING)
    private static final int REL_AIR_MIN = 3;
    private static final int REL_SHELF_MIN = 4;
    private static final int REL_SHELF_MAX = 8;  // inclusive
    private static final int REL_AIR_MAX = 10;   // exclusive

    private static final int DOOR_HEIGHT = 3;

    // Wall face pattern
    private static final int FACE_START = WALL_THICKNESS;
    private static final int FACE_LENGTH = CELL_SIZE - 2 * WALL_THICKNESS;

    // Center pillar bounds (inclusive) — 4x4 for pillar rooms
    public static final int PILLAR_MIN = 6;
    public static final int PILLAR_MAX = 9;

    // Stairwell bounds (inclusive) — 6x6 footprint
    public static final int STAIR_MIN = 5;
    public static final int STAIR_MAX = 10;

    // Wrap teleportation offset (layer 0 ↔ layer 6, identical rooms)
    public static final int WRAP_OFFSET = 6 * FLOOR_SPACING; // 60

    public enum RoomType { EMPTY, PILLAR, STAIRCASE }

    public Level64637ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    // ── Terrain generation ───────────────────────────────────────────────

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        int cellX = Math.floorDiv(chunkX * 16, CELL_SIZE);
        int cellZ = Math.floorDiv(chunkZ * 16, CELL_SIZE);

        // Fill entire vertical extent with oak planks, including fake layers above/below
        chunkData.setRegion(0, -FLOOR_SPACING, 0, 16, CAP_MAX_Y + FLOOR_SPACING, 16, Material.OAK_PLANKS);

        // Generate each layer (including fake layers -1 and NUM_LAYERS for visual wrap continuity)
        for (int layer = -1; layer <= NUM_LAYERS; layer++) {
            int baseY = layer * FLOOR_SPACING;
            int airMin = baseY + REL_AIR_MIN;
            int airMax = baseY + REL_AIR_MAX;

            RoomType roomType = getRoomType(seed, layer, cellX, cellZ);

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int worldX = chunkX * 16 + x;
                    int worldZ = chunkZ * 16 + z;
                    int localX = Math.floorMod(worldX, CELL_SIZE);
                    int localZ = Math.floorMod(worldZ, CELL_SIZE);

                    boolean isWallX = localX < WALL_THICKNESS || localX >= CELL_SIZE - WALL_THICKNESS;
                    boolean isWallZ = localZ < WALL_THICKNESS || localZ >= CELL_SIZE - WALL_THICKNESS;

                    if (isWallX && isWallZ) {
                        // Corner post — solid (already filled)
                    } else if (isWallX) {
                        int boundaryX = localX == 0 ? cellX : cellX + 1;
                        placeWallColumn(chunkData, x, z, localZ, seed, boundaryX, cellZ, true, layer);
                    } else if (isWallZ) {
                        int boundaryZ = localZ == 0 ? cellZ : cellZ + 1;
                        placeWallColumn(chunkData, x, z, localX, seed, cellX, boundaryZ, false, layer);
                    } else if (roomType == RoomType.STAIRCASE
                            && localX >= STAIR_MIN && localX <= STAIR_MAX
                            && localZ >= STAIR_MIN && localZ <= STAIR_MAX) {
                        placeStaircaseColumn(chunkData, x, z, localX, localZ, layer, seed, cellX, cellZ);
                    } else if (roomType == RoomType.PILLAR
                            && localX >= PILLAR_MIN && localX <= PILLAR_MAX
                            && localZ >= PILLAR_MIN && localZ <= PILLAR_MAX) {
                        placePillarColumn(chunkData, x, z, localX, localZ, layer);
                    } else {
                        // Interior — air
                        for (int y = airMin; y < airMax; y++) {
                            chunkData.setBlock(x, y, z, Material.AIR);
                        }
                    }
                }
            }

            // Lanterns
            if (roomType == RoomType.STAIRCASE || roomType == RoomType.PILLAR) {
                placeLantern(chunkData, 3, 3, layer);
                placeLantern(chunkData, 3, 12, layer);
                placeLantern(chunkData, 12, 3, layer);
                placeLantern(chunkData, 12, 12, layer);
            } else {
                placeLantern(chunkData, 5, 5, layer);
                placeLantern(chunkData, 5, 10, layer);
                placeLantern(chunkData, 10, 5, layer);
                placeLantern(chunkData, 10, 10, layer);
            }
        }

        // Punch stairwell through base (Y=0-2) and ceiling cap (Y=70-72)
        // for all-layer staircases that need the wrap connection
        if (isStaircase(seed, cellX, cellZ) && getStaircaseSpan(seed, cellX, cellZ) >= NUM_LAYERS) {
            punchStairwellShaft(chunkData, 0, SOLID_THICKNESS, chunkX, chunkZ);
            punchStairwellShaft(chunkData, CAP_MIN_Y, CAP_MAX_Y, chunkX, chunkZ);
        }

        // Bedrock boundaries: Y=0-1 floor, Y=71-72 ceiling.
        // Y=2 and Y=70 stay as oak planks so the visible floor/ceiling surface is wood, not bedrock.
        applyBoundaryLayer(chunkData, 0, REL_AIR_MIN - 1, Material.BEDROCK, false);
        applyBoundaryLayer(chunkData, CAP_MIN_Y + 1, CAP_MAX_Y, Material.BEDROCK, false);

        // Re-punch stairwell shaft through bedrock and line the exposed walls with oak planks.
        if (isStaircase(seed, cellX, cellZ) && getStaircaseSpan(seed, cellX, cellZ) >= NUM_LAYERS) {
            punchStairwellShaft(chunkData, 0, REL_AIR_MIN - 1, chunkX, chunkZ);
            punchStairwellShaft(chunkData, CAP_MIN_Y + 1, CAP_MAX_Y, chunkX, chunkZ);
            lineShaftWalls(chunkData, 0, REL_AIR_MIN - 1, chunkX, chunkZ);
            lineShaftWalls(chunkData, CAP_MIN_Y + 1, CAP_MAX_Y, chunkX, chunkZ);
        }

        // Punch a hole in the center of the ceiling cap so the fake room above is visible
        punchCeilingHole(chunkData, chunkX, chunkZ);

        // Place wrap slabs in the base and cap shafts (after bedrock so they aren't overwritten)
        // Cap slabs skip the ceiling hole area (see inCeilingHole guard)
        placeWrapSlabs(chunkData, chunkX, chunkZ, seed, cellX, cellZ);
    }

    /**
     * Punches air through a solid region at all stairwell positions (including center shaft).
     */
    private void punchStairwellShaft(ChunkData data, int yMin, int yMax, int chunkX, int chunkZ) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);

                if (localX >= STAIR_MIN && localX <= STAIR_MAX
                        && localZ >= STAIR_MIN && localZ <= STAIR_MAX) {
                    for (int y = yMin; y < yMax; y++) {
                        data.setBlock(x, y, z, Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * Replaces bedrock with oak planks on the 1-block perimeter surrounding the stairwell shaft,
     * so raw bedrock isn't visible from inside the shaft.
     */
    private void lineShaftWalls(ChunkData data, int yMin, int yMax, int chunkX, int chunkZ) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);

                boolean adjX = localX == STAIR_MIN - 1 || localX == STAIR_MAX + 1;
                boolean adjZ = localZ == STAIR_MIN - 1 || localZ == STAIR_MAX + 1;
                boolean inRangeX = localX >= STAIR_MIN - 1 && localX <= STAIR_MAX + 1;
                boolean inRangeZ = localZ >= STAIR_MIN - 1 && localZ <= STAIR_MAX + 1;

                if ((adjX && inRangeZ) || (adjZ && inRangeX)) {
                    for (int y = yMin; y < yMax; y++) {
                        data.setBlock(x, y, z, Material.OAK_PLANKS);
                    }
                }
            }
        }
    }

    /**
     * Punches a 4x4 hole in the center of the ceiling cap for each cell,
     * so the fake room above layer 6 is visible from below.
     */
    private void punchCeilingHole(ChunkData data, int chunkX, int chunkZ) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);

                if (localX >= PILLAR_MIN && localX <= PILLAR_MAX
                        && localZ >= PILLAR_MIN && localZ <= PILLAR_MAX) {
                    for (int y = CAP_MIN_Y; y < CAP_MAX_Y; y++) {
                        data.setBlock(x, y, z, Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * Places staircase slabs in the base shaft (Y=0–2) and cap shaft (Y=70–72)
     * for all-layer wrapping staircases, using r=−1 and high-r revolutions.
     * Called after bedrock + re-punch so the slabs aren't overwritten.
     */
    private void placeWrapSlabs(ChunkData data, int chunkX, int chunkZ,
                                long seed, int cellX, int cellZ) {
        if (!isStaircase(seed, cellX, cellZ)
                || getStaircaseSpan(seed, cellX, cellZ) < NUM_LAYERS) return;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);

                if (localX < STAIR_MIN || localX > STAIR_MAX
                        || localZ < STAIR_MIN || localZ > STAIR_MAX) continue;

                int sx = localX - STAIR_MIN;
                int sz = localZ - STAIR_MIN;
                int offset = STAIR_OFFSET[sz][sx];
                if (offset == -1) continue; // center shaft stays air

                boolean isTop = STAIR_IS_TOP[sz][sx];
                Slab slab = (Slab) Material.OAK_SLAB.createBlockData();
                slab.setType(isTop ? Slab.Type.TOP : Slab.Type.BOTTOM);

                // Base shaft: r = -1
                int baseBlockY = REL_AIR_MIN + offset - 6;
                if (baseBlockY >= 0 && baseBlockY < REL_AIR_MIN) {
                    data.setBlock(x, baseBlockY, z, slab);
                }

                // Cap shaft: find r where blockY lands in [CAP_MIN_Y, CAP_MAX_Y)
                // Skip positions inside the ceiling hole so the fake room above stays visible
                boolean inCeilingHole = localX >= PILLAR_MIN && localX <= PILLAR_MAX
                        && localZ >= PILLAR_MIN && localZ <= PILLAR_MAX;
                if (!inCeilingHole) {
                    for (int r = (CAP_MIN_Y - REL_AIR_MIN - offset + 5) / 6; ; r++) {
                        int blockY = REL_AIR_MIN + offset + 6 * r;
                        if (blockY >= CAP_MAX_Y) break;
                        if (blockY >= CAP_MIN_Y) {
                            data.setBlock(x, blockY, z, slab);
                        }
                    }
                }
            }
        }
    }

    // ── Wall columns ────────────────────────────────────────────────────

    private void placeWallColumn(ChunkData data, int x, int z, int localPos,
                                 long seed, int coordA, int coordB, boolean xWall, int layer) {
        int baseY = layer * FLOOR_SPACING;
        int airMin = baseY + REL_AIR_MIN;
        int airMax = baseY + REL_AIR_MAX;
        int shelfMin = baseY + REL_SHELF_MIN;
        int shelfMax = baseY + REL_SHELF_MAX;

        int offset = localPos - FACE_START;
        if (offset < 0 || offset >= FACE_LENGTH) {
            return; // already solid
        }

        WallElement element = getWallElement(offset);

        if (element == WallElement.PILASTER) {
            // already solid
        } else if (element == WallElement.DOOR && hasDoor(seed, coordA, coordB, xWall, layer)) {
            placeDoorColumn(data, x, z, airMin, shelfMax, airMax);
        } else {
            placeShelfColumn(data, x, z, airMin, shelfMin, shelfMax, airMax);
        }
    }

    private WallElement getWallElement(int offset) {
        return switch (offset) {
            case 0, 4, 9, 13 -> WallElement.PILASTER;
            case 6, 7 -> WallElement.DOOR;
            default -> WallElement.BOOKSHELF;
        };
    }

    private enum WallElement { PILASTER, BOOKSHELF, DOOR }

    private boolean hasDoor(long seed, int boundaryCoordA, int boundaryCoordB, boolean xWall, int layer) {
        long hash = seed ^ ((long) boundaryCoordA * 341873128712L
                + (long) boundaryCoordB * 132897987541L
                + (long) (layer % 6) * 987654321L
                + (xWall ? 30L : 31L));
        double value = new Random(hash).nextDouble();
        return value < 0.85;
    }

    // ── Room type selection ─────────────────────────────────────────────

    /**
     * Determines if this XZ position is a staircase room on any layer.
     */
    public static boolean isStaircase(long seed, int cellX, int cellZ) {
        long hash = seed ^ ((long) cellX * 6364136223846793005L
                + (long) cellZ * 1442695040888963407L + 33L);
        return new Random(hash).nextDouble() < 0.12;
    }

    /**
     * Returns the starting layer (0-5) for a staircase at this position.
     * Uses % 6 so layers 0 and 6 are consistent.
     */
    public static int getStaircaseStartLayer(long seed, int cellX, int cellZ) {
        long hash = seed ^ ((long) cellX * 2862933555777941757L
                + (long) cellZ * 3037000499L + 34L);
        return (int) (Math.abs(hash) % 6);
    }

    /**
     * Returns the number of layers this staircase spans.
     * 2 layers (~65%), 3 layers (~25%), all 7 layers (~10%).
     */
    public static int getStaircaseSpan(long seed, int cellX, int cellZ) {
        long hash = seed ^ ((long) cellX * 1103515245L
                + (long) cellZ * 12345L + 35L);
        double value = new Random(hash).nextDouble();
        if (value < 0.10) return NUM_LAYERS; // all layers
        if (value < 0.35) return 3;
        return 2;
    }

    /**
     * Checks if a staircase at (cellX, cellZ) is active on the given layer.
     */
    public static boolean isStaircaseOnLayer(long seed, int layer, int cellX, int cellZ) {
        if (!isStaircase(seed, cellX, cellZ)) return false;
        int span = getStaircaseSpan(seed, cellX, cellZ);
        if (span >= NUM_LAYERS) return true; // all-layer staircase
        int start = getStaircaseStartLayer(seed, cellX, cellZ);
        // Check if layer % 6 falls within [start, start + span - 1] mod 6
        int layerMod = layer % 6;
        for (int i = 0; i < span; i++) {
            if ((start + i) % 6 == layerMod) return true;
        }
        return false;
    }

    /**
     * Determines room type. Uses layer % 6 so layers 0 and 6 match.
     */
    public static RoomType getRoomType(long seed, int layer, int cellX, int cellZ) {
        if (isStaircaseOnLayer(seed, layer, cellX, cellZ)) return RoomType.STAIRCASE;
        long hash = seed ^ ((long) cellX * 341873128712L
                + (long) cellZ * 132897987541L
                + (long) (layer % 6) * 777777777L + 32L);
        return new Random(hash).nextDouble() < 0.5 ? RoomType.PILLAR : RoomType.EMPTY;
    }

    public static boolean hasCenterPillar(long seed, int layer, int cellX, int cellZ) {
        return getRoomType(seed, layer, cellX, cellZ) == RoomType.PILLAR;
    }

    // ── Pillar room ─────────────────────────────────────────────────────

    private void placePillarColumn(ChunkData data, int x, int z, int localX, int localZ, int layer) {
        int baseY = layer * FLOOR_SPACING;
        int airMin = baseY + REL_AIR_MIN;
        int airMax = baseY + REL_AIR_MAX;
        int shelfMin = baseY + REL_SHELF_MIN;
        int shelfMax = baseY + REL_SHELF_MAX;

        boolean edgeX = localX == PILLAR_MIN || localX == PILLAR_MAX;
        boolean edgeZ = localZ == PILLAR_MIN || localZ == PILLAR_MAX;

        if (edgeX && edgeZ) {
            // Pilaster corner — already solid
        } else if (edgeX || edgeZ) {
            placeShelfColumn(data, x, z, airMin, shelfMin, shelfMax, airMax);
        } else {
            // Interior — already solid
        }
    }

    // ── Staircase room ──────────────────────────────────────────────────

    /**
     * Block Y offset for each (sx, sz) position in the 6x6 stairwell.
     * -1 = center shaft (no slab, kept as air).
     *
     * Clockwise spiral (SW→S→SE→E→NE→N→NW→W→SW):
     *   blockY(sx, sz, r) = startY + STAIR_OFFSET[sz][sx] + 6 * r
     *
     * One full revolution = 6 blocks of height (12 steps of +0.5 each).
     */
    private static final int[][] STAIR_OFFSET = {
        //  sx=0  1    2    3    4    5
            { 4,  4,   4,   3,   3,   3 },  // sz=0: NW(4) | N near-NW(4), N near-NE(3) | NE(3)
            { 4,  4,   4,   3,   3,   3 },  // sz=1
            { 5,  5,  -1,  -1,   2,   2 },  // sz=2: W near-NW(5) | shaft | E near-NE(2)
            { 5,  5,  -1,  -1,   2,   2 },  // sz=3: W near-SW(5) | shaft | E near-SE(2)
            { 0,  0,   0,   1,   1,   1 },  // sz=4: SW(0) | S near-SW(0), S near-SE(1) | SE(1)
            { 0,  0,   0,   1,   1,   1 },  // sz=5
    };

    /**
     * true = TOP slab (feet at blockY+1), false = BOTTOM slab (feet at blockY+0.5).
     * SW corner starts as BOTTOM so entry from floor is +0.5 (walkable without jumping).
     * Each clockwise step transitions +0.5 blocks of walking height.
     */
    private static final boolean[][] STAIR_IS_TOP = {
        //     sx=0   1      2      3      4      5
            { true,  true,  false, true,  false, false },  // sz=0: NW(T) | N near-NW(B), N near-NE(T) | NE(B)
            { true,  true,  false, true,  false, false },  // sz=1
            { false, false, false, false, true,  true  },  // sz=2: W near-NW(B) | shaft | E near-NE(T)
            { true,  true,  false, false, false, false },  // sz=3: W near-SW(T) | shaft | E near-SE(B)
            { false, false, true,  false, true,  true  },  // sz=4: SW(B) | S near-SW(T), S near-SE(B) | SE(T)
            { false, false, true,  false, true,  true  },  // sz=5
    };

    /**
     * Places a column within the 6x6 stairwell for one layer.
     *
     * Center 2×2 (sx=2-3, sz=2-3) is the open vertical shaft — just air.
     * All other positions get oak slabs (top or bottom) at blockY = startY + offset + 6*r.
     */
    private void placeStaircaseColumn(ChunkData data, int x, int z,
                                       int localX, int localZ, int layer,
                                       long seed, int cellX, int cellZ) {
        int baseY = layer * FLOOR_SPACING;
        int airMin = baseY + REL_AIR_MIN;
        int airMax = baseY + REL_AIR_MAX;

        int sx = localX - STAIR_MIN; // 0-5
        int sz = localZ - STAIR_MIN; // 0-5
        int offset = STAIR_OFFSET[sz][sx];

        // Determine vertical range for this column (may include carved floor solid below).
        // Guard with layer < NUM_LAYERS to prevent fake top layer from carving into the cap.
        int colMin = (layer > 0 && layer < NUM_LAYERS && isStaircaseOnLayer(seed, layer - 1, cellX, cellZ))
                ? baseY : airMin;
        int colMax = airMax;

        // Clear the column to air
        for (int y = colMin; y < colMax; y++) {
            data.setBlock(x, y, z, Material.AIR);
        }

        // Center shaft: just air, no slabs
        if (offset == -1) return;

        // Compute staircase base Y (airMin of the bottom layer of this span)
        int span = getStaircaseSpan(seed, cellX, cellZ);
        int startY = (span >= NUM_LAYERS)
                ? REL_AIR_MIN
                : getStaircaseStartLayer(seed, cellX, cellZ) * FLOOR_SPACING + REL_AIR_MIN;
        int maxBlockY = startY + 10 * Math.min(span, NUM_LAYERS) - 1;

        boolean isTop = STAIR_IS_TOP[sz][sx];

        // Place one slab per revolution wherever it falls in this layer's column range
        for (int r = 0; startY + offset + 6 * r <= maxBlockY; r++) {
            int blockY = startY + offset + 6 * r;
            if (blockY >= colMin && blockY < colMax) {
                Slab slab = (Slab) Material.OAK_SLAB.createBlockData();
                slab.setType(isTop ? Slab.Type.TOP : Slab.Type.BOTTOM);
                data.setBlock(x, blockY, z, slab);
            }
        }
    }

    // ── Shared building methods ─────────────────────────────────────────

    private void placeShelfColumn(ChunkData data, int x, int z,
                                   int airMin, int shelfMin, int shelfMax, int airMax) {
        data.setBlock(x, airMin, z, Material.OAK_PLANKS);       // baseboard
        for (int y = shelfMin; y <= shelfMax; y++) {
            data.setBlock(x, y, z, Material.BOOKSHELF);
        }
        data.setBlock(x, airMax - 1, z, Material.OAK_PLANKS);  // crown
    }

    private void placeDoorColumn(ChunkData data, int x, int z,
                                  int airMin, int shelfMax, int airMax) {
        for (int y = airMin; y < airMin + DOOR_HEIGHT; y++) {
            data.setBlock(x, y, z, Material.AIR);
        }
        data.setBlock(x, airMin + DOOR_HEIGHT, z, Material.OAK_PLANKS);  // lintel
        for (int y = airMin + DOOR_HEIGHT + 1; y <= shelfMax; y++) {
            data.setBlock(x, y, z, Material.BOOKSHELF);
        }
        data.setBlock(x, airMax - 1, z, Material.OAK_PLANKS);  // crown
    }

    private void placeLantern(ChunkData data, int x, int z, int layer) {
        int y = layer * FLOOR_SPACING + REL_AIR_MAX - 1;
        org.bukkit.block.data.type.Lantern lantern =
                (org.bukkit.block.data.type.Lantern) Material.LANTERN.createBlockData();
        lantern.setHanging(true);
        data.setBlock(x, y, z, lantern);
    }

    @Override
    public int getSpawnY() { return 3 * FLOOR_SPACING + REL_AIR_MIN; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, 3 * FLOOR_SPACING + REL_AIR_MIN, 8.5); // layer 3 floor
    }

    // ── Book Config ──────────────────────────────────────────────────────

    public record BookConfig(
            String gibberishChars,
            double cursedChance,
            List<String> cursedSnippets,
            List<String> cursedTitles,
            List<String> cursedAuthors
    ) {
        public static final BookConfig DEFAULT = new BookConfig(
                "abcdefghijklmnopqrstuvwxyz .,;:'-",
                0.15,
                List.of("you have been here before and you will be here again"),
                List.of("FOR YOU"),
                List.of("The Librarian")
        );
    }
}
