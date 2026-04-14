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
 * Center = bookshelf pillar. Corners = flat. Faces = half-block transition (oak + slabs).
 * One revolution = 2 blocks of height.
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

    // Wrap teleportation offset (layer 0 ↔ layer 6)
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

        // Fill entire vertical extent with oak planks first (all solids)
        chunkData.setRegion(0, 0, 0, 16, CAP_MAX_Y, 16, Material.OAK_PLANKS);

        // Generate each layer
        for (int layer = 0; layer < NUM_LAYERS; layer++) {
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
    }

    /**
     * Punches air through a solid region at stairwell shaft positions (excluding center pillar).
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
                    int sx = localX - STAIR_MIN;
                    int sz = localZ - STAIR_MIN;
                    if (STAIR_OFFSETS[sz][sx] == -1) continue; // keep center pillar solid
                    for (int y = yMin; y < yMax; y++) {
                        data.setBlock(x, y, z, Material.AIR);
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
        return new Random(hash).nextDouble() < 0.06;
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
     * 2 layers (~75%), 3 layers (~23%), all 7 layers (~2%).
     */
    public static int getStaircaseSpan(long seed, int cellX, int cellZ) {
        long hash = seed ^ ((long) cellX * 1103515245L
                + (long) cellZ * 12345L + 35L);
        double value = new Random(hash).nextDouble();
        if (value < 0.02) return NUM_LAYERS; // all layers
        if (value < 0.25) return 3;
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
     * Height offset table for the 6x6 stairwell, decomposed into 2x2 quadrants.
     * Values are in half-blocks (0-4). -1 = center pillar (no stair block).
     *
     * Clockwise spiral: SW corner(0) → S face(0→1) → SE corner(1) →
     *   E face(1→2) → NE corner(2) → N face(2→3) → NW corner(3) → W face(3→4)
     *
     * One full revolution = 4 half-blocks = 2 blocks of height.
     */
    private static final int[][] STAIR_OFFSETS = {
        //  sx=0  1    2    3    4    5
            { 3,  3,   3,   2,   2,   2 },  // sz=0: NW corner | N face | NE corner
            { 3,  3,   3,   2,   2,   2 },  // sz=1
            { 3,  3,  -1,  -1,   2,   2 },  // sz=2: W face | center | E face
            { 4,  4,  -1,  -1,   1,   1 },  // sz=3
            { 0,  0,   0,   1,   1,   1 },  // sz=4: SW corner | S face | SE corner
            { 0,  0,   0,   1,   1,   1 },  // sz=5
    };

    /**
     * Places a column within the 6x6 stairwell.
     * The staircase spans multiple layers with a half-block spiral.
     */
    private void placeStaircaseColumn(ChunkData data, int x, int z,
                                       int localX, int localZ, int layer,
                                       long seed, int cellX, int cellZ) {
        int baseY = layer * FLOOR_SPACING;
        int airMin = baseY + REL_AIR_MIN;
        int airMax = baseY + REL_AIR_MAX;

        int sx = localX - STAIR_MIN; // 0-5
        int sz = localZ - STAIR_MIN; // 0-5
        int offset = STAIR_OFFSETS[sz][sx];

        // Center 2x2 pillar (positions 7-8 = sx 2-3, sz 2-3)
        if (offset == -1) {
            placeCenterPillar(data, x, z, airMin, airMax, seed, cellX, cellZ);
            return;
        }

        // Clear entire air column for this stairwell position
        for (int y = airMin; y < airMax; y++) {
            data.setBlock(x, y, z, Material.AIR);
        }

        // Also clear the solid between this layer and the one below if the staircase
        // connects to the layer below
        if (layer > 0 && isStaircaseOnLayer(seed, layer - 1, cellX, cellZ)) {
            int solidMin = baseY;
            for (int y = solidMin; y < airMin; y++) {
                data.setBlock(x, y, z, Material.AIR);
            }
        }

        // Place spiral blocks for each revolution that fits in this layer's span
        // The staircase ascends from this layer's floor upward.
        // Total height for one floor connection: FLOOR_SPACING (10) blocks = 20 half-blocks = 5 revolutions
        int span = getStaircaseSpan(seed, cellX, cellZ);
        int totalFloors = (span >= NUM_LAYERS) ? NUM_LAYERS : span;
        int maxH = totalFloors * FLOOR_SPACING * 2; // in half-blocks

        // Compute the absolute base of the staircase (lowest layer's airMin)
        int startLayer = getStaircaseStartLayer(seed, cellX, cellZ);
        int absBaseAirMin;
        if (span >= NUM_LAYERS) {
            absBaseAirMin = 0 + REL_AIR_MIN; // starts at layer 0
        } else {
            absBaseAirMin = startLayer * FLOOR_SPACING + REL_AIR_MIN;
        }

        // Place blocks for all revolutions that intersect this layer
        for (int rev = 0; rev < maxH / 4 + 1; rev++) {
            int h = rev * 4 + offset;
            if (h <= 0 || h > maxH) continue;

            int blockY = absBaseAirMin + (h - 1) / 2;

            // Only place if this blockY falls within this layer's column range
            int colMin = (layer > 0 && isStaircaseOnLayer(seed, layer - 1, cellX, cellZ))
                    ? baseY : airMin;
            int colMax = airMax;
            if (blockY < colMin || blockY >= colMax) continue;

            if (h % 2 == 1) {
                // Bottom slab
                Slab slabData = (Slab) Material.OAK_SLAB.createBlockData();
                slabData.setType(Slab.Type.BOTTOM);
                data.setBlock(x, blockY, z, slabData);
            } else {
                // Full block
                data.setBlock(x, blockY, z, Material.OAK_PLANKS);
            }
        }
    }

    /**
     * Places the center 2x2 pillar of the stairwell (bookshelves or oak).
     */
    private void placeCenterPillar(ChunkData data, int x, int z,
                                    int airMin, int airMax, long seed, int cellX, int cellZ) {
        // Most staircase rooms have bookshelf pillar
        long hash = seed ^ ((long) cellX * 555555555L + (long) cellZ * 999999999L + 36L);
        boolean bookshelves = new Random(hash).nextDouble() < 0.75;

        if (bookshelves) {
            int shelfMin = airMin + 1;
            int shelfMax = airMax - 2;
            data.setBlock(x, airMin, z, Material.OAK_PLANKS);     // baseboard
            for (int y = shelfMin; y <= shelfMax; y++) {
                data.setBlock(x, y, z, Material.BOOKSHELF);
            }
            data.setBlock(x, airMax - 1, z, Material.OAK_PLANKS); // crown
        }
        // else: oak planks, already filled by initial setRegion
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
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, REL_AIR_MIN, 8.5); // layer 0 floor
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
