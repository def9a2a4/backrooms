package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Level 64637 — "The Library"
 * Inspired by Borges' Library of Babel. 7 vertical layers of endless 16x16 rooms
 * with floor-to-ceiling bookshelves. Three room variants: empty, center pillar
 * (with chiseled bookshelves), and staircase (connecting layers).
 *
 * Layers 0 and 6 are identical (seed uses layer % 6). The world loops vertically:
 * ascending past layer 6 wraps to layer 0, descending past layer 0 wraps to layer 6.
 *
 * Vertical layout per layer (10 blocks each):
 *   Solid: layerBaseY + 0..2   (floor/ceiling mass)
 *   Air:   layerBaseY + 3..9   (room interior, 7 blocks tall)
 *
 * Wall face pattern (positions 1-14, 14 elements, symmetric):
 *   P BBB P B DD B P BBB P
 */
public class Level64637ChunkGenerator extends BackroomsChunkGenerator {

    // ── Geometry ─────────────────────────────────────────────────────────

    private static final int CELL_SIZE = 16;
    private static final int WALL_THICKNESS = 1;

    // Multi-layer layout
    public static final int NUM_LAYERS = 7;
    public static final int FLOOR_SPACING = 10;
    private static final int SOLID_THICKNESS = 3;     // floor/ceiling mass per layer
    // Ceiling cap above layer 6
    private static final int CAP_MIN_Y = NUM_LAYERS * FLOOR_SPACING;  // 70
    private static final int CAP_MAX_Y = CAP_MIN_Y + SOLID_THICKNESS; // 73

    // Per-layer offsets (relative to layerBaseY = layer * FLOOR_SPACING)
    private static final int REL_SOLID_MIN = 0;
    private static final int REL_SOLID_MAX = 3;  // exclusive
    private static final int REL_AIR_MIN = 3;    // walking surface / baseboard
    private static final int REL_SHELF_MIN = 4;
    private static final int REL_SHELF_MAX = 8;  // inclusive
    private static final int REL_AIR_MAX = 10;   // exclusive (= next layer's solid start)

    private static final int DOOR_HEIGHT = 3;

    // Wall face pattern
    private static final int FACE_START = WALL_THICKNESS;
    private static final int FACE_LENGTH = CELL_SIZE - 2 * WALL_THICKNESS;

    // Center pillar bounds (inclusive)
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

        boolean staircase = isStaircase(seed, cellX, cellZ);

        // Fill entire vertical extent with oak planks first (all solids)
        chunkData.setRegion(0, 0, 0, 16, CAP_MAX_Y, 16, Material.OAK_PLANKS);

        // Generate each layer
        for (int layer = 0; layer < NUM_LAYERS; layer++) {
            int baseY = layer * FLOOR_SPACING;
            int airMin = baseY + REL_AIR_MIN;
            int airMax = baseY + REL_AIR_MAX;

            RoomType roomType = staircase ? RoomType.STAIRCASE : getRoomType(seed, layer, cellX, cellZ);

            // Carve room interior per column
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
                        placeStaircaseColumn(chunkData, x, z, localX, localZ, layer, seed);
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
            if (roomType == RoomType.PILLAR) {
                placeLantern(chunkData, 3, 3, layer);
                placeLantern(chunkData, 3, 12, layer);
                placeLantern(chunkData, 12, 3, layer);
                placeLantern(chunkData, 12, 12, layer);
            } else if (roomType == RoomType.STAIRCASE) {
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

        // Punch stairwell shaft through base (Y=0-2) and ceiling cap (Y=70-72)
        if (staircase) {
            punchStairwellShaft(chunkData, 0, REL_SOLID_MAX, chunkX, chunkZ);
            punchStairwellShaft(chunkData, CAP_MIN_Y, CAP_MAX_Y, chunkX, chunkZ);
        }
    }

    /**
     * Punches air through a solid region at stairwell shaft positions.
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
                    boolean cornerX = localX == STAIR_MIN || localX == STAIR_MAX;
                    boolean cornerZ = localZ == STAIR_MIN || localZ == STAIR_MAX;
                    if (cornerX && cornerZ) continue; // keep pilaster corners solid

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
            // Already solid from initial fill
            return;
        }

        WallElement element = getWallElement(offset);

        if (element == WallElement.PILASTER) {
            // Already solid from initial fill
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

    /**
     * Determines if a wall face has a doorway.
     * Uses a hash for independence and boundary coordinates so shared walls agree.
     * Includes layer % 6 so layers 0 and 6 produce the same doors.
     */
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
     * Determines if this XZ position is a staircase room (same on all layers).
     */
    public static boolean isStaircase(long seed, int cellX, int cellZ) {
        long hash = seed ^ ((long) cellX * 6364136223846793005L
                + (long) cellZ * 1442695040888963407L + 33L);
        return new Random(hash).nextDouble() < 0.16;
    }

    /**
     * Determines room type for non-staircase rooms. Uses layer % 6 so layers 0 and 6 match.
     */
    public static RoomType getRoomType(long seed, int layer, int cellX, int cellZ) {
        if (isStaircase(seed, cellX, cellZ)) return RoomType.STAIRCASE;
        long hash = seed ^ ((long) cellX * 341873128712L
                + (long) cellZ * 132897987541L
                + (long) (layer % 6) * 777777777L + 32L);
        return new Random(hash).nextDouble() < 0.5 ? RoomType.PILLAR : RoomType.EMPTY;
    }

    /**
     * Checks if this room has a center pillar (convenience for listener).
     */
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
            // Pilaster corner — already solid from initial fill
        } else if (edgeX || edgeZ) {
            placeShelfColumn(data, x, z, airMin, shelfMin, shelfMax, airMax);
        } else {
            // Interior — already solid from initial fill
        }
    }

    // ── Staircase room ──────────────────────────────────────────────────

    /**
     * Places a column within the 6x6 stairwell (positions 5-10).
     * 2-wide spiral staircase around a 2x2 open center shaft.
     * The shaft also punches through the solid between floors.
     */
    private void placeStaircaseColumn(ChunkData data, int x, int z,
                                       int localX, int localZ, int layer, long seed) {
        int baseY = layer * FLOOR_SPACING;
        int airMin = baseY + REL_AIR_MIN;
        int airMax = baseY + REL_AIR_MAX;
        int solidMin = baseY + REL_SOLID_MIN;

        boolean cornerX = localX == STAIR_MIN || localX == STAIR_MAX;
        boolean cornerZ = localZ == STAIR_MIN || localZ == STAIR_MAX;

        if (cornerX && cornerZ) {
            // Pilaster corners — already solid from initial fill
            return;
        }

        // Clear air space for the stairwell room area
        for (int y = airMin; y < airMax; y++) {
            data.setBlock(x, y, z, Material.AIR);
        }

        // Also clear the solid between this layer and the one below (stairwell shaft)
        if (layer > 0) {
            for (int y = solidMin; y < airMin; y++) {
                data.setBlock(x, y, z, Material.AIR);
            }
        }

        // Place spiral stairs
        // Stairwell inner coords: 0-5 mapped from localX/Z - STAIR_MIN
        int sx = localX - STAIR_MIN; // 0-5
        int sz = localZ - STAIR_MIN; // 0-5

        placeSpiral(data, x, z, sx, sz, baseY);
    }

    /**
     * Places spiral stair blocks within the 6x6 stairwell.
     * The spiral ascends from the floor of the current layer through the
     * solid above into the next layer's floor level.
     *
     * Stairwell coordinates (sx, sz) range 0-5:
     *   Corners (0,0)(0,5)(5,0)(5,5) = pilasters (handled above)
     *   Ring positions form the 2-wide spiral path
     *   Center 2x2 (2-3, 2-3) = open shaft
     *
     * 28 ring positions (6x6 - 4 corners - 4 center), clockwise:
     *   South side: sz=4-5, sx=1-4  (8 steps: 0-7)
     *   East side:  sx=4-5, sz=1-3  (6 steps: 8-13)
     *   North side: sz=0-1, sx=1-4  (8 steps: 14-21)
     *   West side:  sx=0-1, sz=2-3  (4 steps: 22-25)
     *
     * Note: The west side has fewer positions because the corners of the
     * adjacent sides (south sz≥4 and north sz≤1) claim the outer positions.
     * Steps 26-27 are unused; the spiral wraps at 28 steps.
     */
    private void placeSpiral(ChunkData data, int x, int z, int sx, int sz, int baseY) {
        int step = getSpiralStep(sx, sz);
        if (step < 0) return; // center shaft or invalid — leave as air

        // Total height to span: room (7) + solid between floors (3) = 10
        // 28 step positions around the ring
        int airMin = baseY + REL_AIR_MIN;
        int totalHeight = FLOOR_SPACING; // 10
        int totalSteps = 28;

        int stepY = airMin + step * totalHeight / totalSteps;

        // Place the stair block
        data.setBlock(x, stepY, z, Material.OAK_PLANKS);

        // Place stair block on top for walkability
        BlockFace facing = getSpiralFacing(sx, sz);
        if (facing != null) {
            Stairs stairData = (Stairs) Material.OAK_STAIRS.createBlockData();
            stairData.setFacing(facing);
            data.setBlock(x, stepY + 1, z, stairData);
        }

        // Fill solid below the step (so player doesn't fall through)
        // Only fill down to airMin (or solidMin if punched through)
        int fillMin = (baseY > 0) ? baseY : baseY + REL_AIR_MIN;
        for (int y = fillMin; y < stepY; y++) {
            data.setBlock(x, y, z, Material.OAK_PLANKS);
        }
    }

    /**
     * Maps stairwell coordinates (0-5, 0-5) to a spiral step index (0-31).
     * Returns -1 for center shaft positions (2-3, 2-3) and pilaster corners.
     *
     * The 2-wide ring has 4 sides × 8 positions = 32 steps.
     */
    private int getSpiralStep(int sx, int sz) {
        // Center shaft (2-3, 2-3) — open air
        if (sx >= 2 && sx <= 3 && sz >= 2 && sz <= 3) return -1;

        // Pilaster corners
        if ((sx == 0 || sx == 5) && (sz == 0 || sz == 5)) return -1;

        // South side: sz=4-5, sx=0-5 (left to right), ascending
        // inner row (sz=4): sx 1,2,3,4 → steps 1,3,5,7 (skip corners)
        // outer row (sz=5): sx 1,2,3,4 → steps 0,2,4,6
        if (sz >= 4) {
            if (sz == 5) return (sx - 1) * 2;       // outer: 0,2,4,6
            else return (sx - 1) * 2 + 1;           // inner: 1,3,5,7
        }
        // East side: sx=4-5, sz=3-0 (top to bottom), ascending
        if (sx >= 4) {
            int progress = 3 - sz; // sz=3→0, sz=2→1, sz=1→2, sz=0→3
            if (sx == 5) return 8 + progress * 2;    // outer: 8,10,12,14
            else return 8 + progress * 2 + 1;        // inner: 9,11,13,15
        }
        // North side: sz=0-1, sx=4-1 (right to left), ascending
        if (sz <= 1) {
            int progress = 4 - sx; // sx=4→0, sx=3→1, sx=2→2, sx=1→3
            if (sz == 0) return 16 + progress * 2;   // outer: 16,18,20,22
            else return 16 + progress * 2 + 1;       // inner: 17,19,21,23
        }
        // West side: sx=0-1, sz=0-3 (bottom to top), ascending
        if (sx <= 1) {
            int progress = sz - 2; // sz=2→0, sz=3→1
            if (sx == 0) return 24 + progress * 2;   // outer: 24,26
            else return 24 + progress * 2 + 1;       // inner: 25,27
        }

        return -1;
    }

    /**
     * Returns the facing direction for stair blocks on the spiral.
     * Stairs face the direction of ascending travel.
     */
    private BlockFace getSpiralFacing(int sx, int sz) {
        if (sz >= 4) return BlockFace.EAST;   // south side, ascending left→right
        if (sx >= 4) return BlockFace.NORTH;  // east side, ascending south→north
        if (sz <= 1) return BlockFace.WEST;   // north side, ascending right→left
        if (sx <= 1) return BlockFace.SOUTH;  // west side, ascending bottom→top
        return null;
    }

    // ── Shared building methods ─────────────────────────────────────────

    private void placeShelfColumn(ChunkData data, int x, int z,
                                   int airMin, int shelfMin, int shelfMax, int airMax) {
        data.setBlock(x, airMin, z, Material.OAK_PLANKS);       // baseboard
        for (int y = shelfMin; y <= shelfMax; y++) {
            data.setBlock(x, y, z, Material.BOOKSHELF);
        }
        data.setBlock(x, airMax - 1, z, Material.OAK_PLANKS);  // crown
        // Clear remaining air between baseboard+1 area if needed
        // (shelf covers shelfMin to shelfMax, air above shelfMax+1 to airMax-2)
        // shelfMin = airMin+1, shelfMax = airMax-2, crown = airMax-1 → fully covered
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
