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
 * bookshelves. Rare chiseled bookshelves contain written books — mostly gibberish,
 * occasionally with cursed snippets.
 *
 * Room layout (16x16 cell, 1 per chunk):
 *   Positions 0-1: wall zone (shared with adjacent room)
 *   Positions 2-15: interior
 *
 * Wall face pattern (positions 2-15, symmetric):
 *   P BBB P B DD B P BBB P
 *   where P=pilaster(oak), B=bookshelf, D=doorway
 *
 * Wall vertical structure (y=10-16):
 *   y=10: oak plank baseboard
 *   y=11-15: bookshelves (5 shelves, matching Borges)
 *   y=16: oak plank crown
 */
public class Level64637ChunkGenerator extends ChunkGenerator {

    // ── Geometry ─────────────────────────────────────────────────────────

    private static final int CELL_SIZE = 16;       // one room per chunk
    private static final int WALL_THICKNESS = 2;   // two-wide walls

    // Vertical layout
    private static final int SOLID_MIN_Y = 0;
    private static final int AIR_MIN_Y = 10;       // floor surface / baseboard
    private static final int SHELF_MIN_Y = 11;     // first bookshelf row
    private static final int SHELF_MAX_Y = 15;     // last bookshelf row (inclusive)
    private static final int AIR_MAX_Y = 17;       // ceiling (exclusive); crown at y=16
    private static final int SOLID_MAX_Y = 40;

    private static final int DOOR_HEIGHT = 3;      // y=10,11,12 are open

    // Wall face pattern — positions along the 14-block span (local 2-15)
    // Offsets from position 2:  0  1  2  3  4  5  6  7  8  9 10 11 12 13
    // Pattern:                  P  B  B  B  P  B  D  D  B  P  B  B  B  P
    private static final int FACE_START = WALL_THICKNESS; // local pos 2

    public Level64637ChunkGenerator() {
    }

    // ── Terrain generation ───────────────────────────────────────────────

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // Solid fill: floor mass and ceiling mass (oak planks)
        chunkData.setRegion(0, SOLID_MIN_Y, 0, 16, AIR_MIN_Y, 16, Material.OAK_PLANKS);
        chunkData.setRegion(0, AIR_MAX_Y, 0, 16, SOLID_MAX_Y, 16, Material.OAK_PLANKS);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);
                int cellX = Math.floorDiv(worldX, CELL_SIZE);
                int cellZ = Math.floorDiv(worldZ, CELL_SIZE);

                boolean inWallX = localX < WALL_THICKNESS;
                boolean inWallZ = localZ < WALL_THICKNESS;

                if (inWallX && inWallZ) {
                    // Corner post — solid oak planks
                    fillColumn(chunkData, x, z, Material.OAK_PLANKS);
                } else if (inWallX) {
                    // X-wall face: runs along Z axis, position determined by localZ
                    placeWallColumn(chunkData, x, z, localZ, seed, cellX, cellZ, true, chunkRng);
                } else if (inWallZ) {
                    // Z-wall face: runs along X axis, position determined by localX
                    placeWallColumn(chunkData, x, z, localX, seed, cellX, cellZ, false, chunkRng);
                } else {
                    // Interior — air
                    for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                }
            }
        }

        // Four lanterns per room in a symmetric pattern
        placeLantern(chunkData, 5, 5);
        placeLantern(chunkData, 5, 11);
        placeLantern(chunkData, 11, 5);
        placeLantern(chunkData, 11, 11);
    }

    /**
     * Places a wall column at the given position along a wall face.
     * The face spans local positions 2-15 (14 blocks).
     * Pattern: P BBB P B DD B P BBB P (perfectly symmetric)
     */
    private void placeWallColumn(ChunkData data, int x, int z, int localPos,
                                 long seed, int cellX, int cellZ, boolean xWall, Random rng) {
        int offset = localPos - FACE_START;
        if (offset < 0 || offset >= CELL_SIZE - WALL_THICKNESS) {
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
        return noise > -0.4; // ~70% of walls get a door
    }

    /**
     * Fills a column with oak plank baseboard, regular bookshelves, and oak plank crown.
     * Chiseled bookshelves are placed later by the BlockPopulator with correct facing.
     */
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
        data.setBlock(x, AIR_MAX_Y - 1, z, Material.LANTERN);
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
