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
 * Level 64637 — "The Library"
 * Inspired by Borges' Library of Babel. Endless square rooms with floor-to-ceiling
 * bookshelves. Two room variants: empty rooms and rooms with a central 4x4 pillar
 * whose faces hold chiseled bookshelves with written books.
 *
 * Room layout (16x16 cell, 1 per chunk):
 *   Positions 0 and 15: wall (1-block each side, 2-thick effective between rooms)
 *   Positions 1-14: interior with face pattern
 *
 * Wall face pattern (positions 1-14, 14 elements, symmetric):
 *   P BBB P B DD B P BBB P
 *   where P=pilaster(oak), B=bookshelf, D=doorway
 *
 * Center pillar (variant B rooms, positions 6-9 x 6-9):
 *   P B B P
 *   B O O B    P=pilaster, B=bookshelf, O=solid oak interior
 *   B O O B
 *   P B B P
 */
public class Level64637ChunkGenerator extends ChunkGenerator {

    // ── Geometry ─────────────────────────────────────────────────────────

    private static final int CELL_SIZE = 16;       // one room per chunk
    private static final int WALL_THICKNESS = 1;   // one-block walls on each edge

    // Vertical layout
    private static final int SOLID_MIN_Y = 0;
    private static final int AIR_MIN_Y = 10;       // floor surface / baseboard
    private static final int SHELF_MIN_Y = 11;     // first bookshelf row
    private static final int SHELF_MAX_Y = 15;     // last bookshelf row (inclusive)
    private static final int AIR_MAX_Y = 17;       // ceiling (exclusive); crown at y=16
    private static final int SOLID_MAX_Y = 40;

    private static final int DOOR_HEIGHT = 3;      // y=10,11,12 are open

    // Wall face pattern — positions along the 14-block span (local 1-14)
    // Offsets from position 1:  0  1  2  3  4  5  6  7  8  9 10 11 12 13
    // Pattern:                  P  B  B  B  P  B  D  D  B  P  B  B  B  P
    private static final int FACE_START = WALL_THICKNESS; // local pos 1
    private static final int FACE_LENGTH = CELL_SIZE - 2 * WALL_THICKNESS; // 14

    // Center pillar bounds (inclusive)
    public static final int PILLAR_MIN = 6;
    public static final int PILLAR_MAX = 9;

    public Level64637ChunkGenerator() {
    }

    // ── Terrain generation ───────────────────────────────────────────────

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));
        int cellX = Math.floorDiv(chunkX * 16, CELL_SIZE);
        int cellZ = Math.floorDiv(chunkZ * 16, CELL_SIZE);

        // Solid fill: floor mass and ceiling mass (oak planks)
        chunkData.setRegion(0, SOLID_MIN_Y, 0, 16, AIR_MIN_Y, 16, Material.OAK_PLANKS);
        chunkData.setRegion(0, AIR_MAX_Y, 0, 16, SOLID_MAX_Y, 16, Material.OAK_PLANKS);

        boolean pillarRoom = hasCenterPillar(seed, cellX, cellZ);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);

                boolean isWallX = localX < WALL_THICKNESS || localX >= CELL_SIZE - WALL_THICKNESS;
                boolean isWallZ = localZ < WALL_THICKNESS || localZ >= CELL_SIZE - WALL_THICKNESS;

                if (isWallX && isWallZ) {
                    // Corner post — solid oak planks
                    fillColumn(chunkData, x, z, Material.OAK_PLANKS);
                } else if (isWallX) {
                    // X-wall face: runs along Z axis
                    placeWallColumn(chunkData, x, z, localZ, seed, cellX, cellZ, true, chunkRng);
                } else if (isWallZ) {
                    // Z-wall face: runs along X axis
                    placeWallColumn(chunkData, x, z, localX, seed, cellX, cellZ, false, chunkRng);
                } else if (pillarRoom && localX >= PILLAR_MIN && localX <= PILLAR_MAX
                        && localZ >= PILLAR_MIN && localZ <= PILLAR_MAX) {
                    placePillarColumn(chunkData, x, z, localX, localZ);
                } else {
                    // Interior — air
                    for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                }
            }
        }

        // Lanterns — positioned to work for both variants
        if (pillarRoom) {
            // Four lanterns in the gaps between pillar and walls
            placeLantern(chunkData, 3, 3);
            placeLantern(chunkData, 3, 12);
            placeLantern(chunkData, 12, 3);
            placeLantern(chunkData, 12, 12);
        } else {
            placeLantern(chunkData, 5, 5);
            placeLantern(chunkData, 5, 10);
            placeLantern(chunkData, 10, 5);
            placeLantern(chunkData, 10, 10);
        }
    }

    /**
     * Places a wall column at the given position along a wall face.
     * The face spans local positions 1-14 (14 blocks).
     * Pattern: P BBB P B DD B P BBB P (perfectly symmetric)
     */
    private void placeWallColumn(ChunkData data, int x, int z, int localPos,
                                 long seed, int cellX, int cellZ, boolean xWall, Random rng) {
        int offset = localPos - FACE_START;
        if (offset < 0 || offset >= FACE_LENGTH) {
            fillColumn(data, x, z, Material.OAK_PLANKS);
            return;
        }

        WallElement element = getWallElement(offset);

        if (element == WallElement.PILASTER) {
            fillColumn(data, x, z, Material.OAK_PLANKS);
        } else if (element == WallElement.DOOR && hasDoor(seed, cellX, cellZ, xWall)) {
            placeDoorColumn(data, x, z);
        } else {
            placeShelfColumn(data, x, z);
        }
    }

    /**
     * Determines the element type at a given offset (0-13) along the wall face.
     * Pattern: P BBB P B DD B P BBB P
     */
    private WallElement getWallElement(int offset) {
        return switch (offset) {
            case 0, 4, 9, 13 -> WallElement.PILASTER;
            case 6, 7 -> WallElement.DOOR;
            default -> WallElement.BOOKSHELF;
        };
    }

    private enum WallElement { PILASTER, BOOKSHELF, DOOR }

    /**
     * Determines if a wall face has a doorway, using noise for cross-chunk consistency.
     */
    private boolean hasDoor(long seed, int cellX, int cellZ, boolean xWall) {
        long noiseSeed = seed + (xWall ? 30L : 31L);
        double noise = SimplexNoise.noise2(noiseSeed, cellX * 0.7, cellZ * 0.7);
        return noise > -0.7; // ~85% of walls get a door
    }

    /**
     * Determines if this room has a center pillar, using noise for consistency.
     */
    public static boolean hasCenterPillar(long seed, int cellX, int cellZ) {
        double noise = SimplexNoise.noise2(seed + 32L, cellX * 0.5, cellZ * 0.5);
        return noise > 0.0; // ~50% of rooms get a pillar
    }

    /**
     * Places a column within the 4x4 center pillar.
     * Corners are pilasters, outer faces are bookshelves, interior is solid oak.
     */
    private void placePillarColumn(ChunkData data, int x, int z, int localX, int localZ) {
        boolean edgeX = localX == PILLAR_MIN || localX == PILLAR_MAX;
        boolean edgeZ = localZ == PILLAR_MIN || localZ == PILLAR_MAX;

        if (edgeX && edgeZ) {
            fillColumn(data, x, z, Material.OAK_PLANKS);
        } else if (edgeX || edgeZ) {
            placeShelfColumn(data, x, z);
        } else {
            fillColumn(data, x, z, Material.OAK_PLANKS);
        }
    }

    private void placeShelfColumn(ChunkData data, int x, int z) {
        data.setBlock(x, AIR_MIN_Y, z, Material.OAK_PLANKS);       // baseboard
        for (int y = SHELF_MIN_Y; y <= SHELF_MAX_Y; y++) {
            data.setBlock(x, y, z, Material.BOOKSHELF);
        }
        data.setBlock(x, AIR_MAX_Y - 1, z, Material.OAK_PLANKS);  // crown
    }

    private void placeDoorColumn(ChunkData data, int x, int z) {
        for (int y = AIR_MIN_Y; y < AIR_MIN_Y + DOOR_HEIGHT; y++) {
            data.setBlock(x, y, z, Material.AIR);
        }
        data.setBlock(x, AIR_MIN_Y + DOOR_HEIGHT, z, Material.OAK_PLANKS);  // lintel
        for (int y = AIR_MIN_Y + DOOR_HEIGHT + 1; y <= SHELF_MAX_Y; y++) {
            data.setBlock(x, y, z, Material.BOOKSHELF);
        }
        data.setBlock(x, AIR_MAX_Y - 1, z, Material.OAK_PLANKS);  // crown
    }

    private void fillColumn(ChunkData data, int x, int z, Material material) {
        for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
            data.setBlock(x, y, z, material);
        }
    }

    private void placeLantern(ChunkData data, int x, int z) {
        org.bukkit.block.data.type.Lantern lantern =
                (org.bukkit.block.data.type.Lantern) Material.LANTERN.createBlockData();
        lantern.setHanging(true);
        data.setBlock(x, AIR_MAX_Y - 1, z, lantern);
    }

    // ── Overrides ────────────────────────────────────────────────────────

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
