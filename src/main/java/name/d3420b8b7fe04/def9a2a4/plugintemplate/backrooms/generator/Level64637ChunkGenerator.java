package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;
import java.util.Random;

/**
 * Level 64637 — "The Library"
 * Inspired by Borges' Library of Babel. Endless square rooms with floor-to-ceiling
 * bookshelves. Rare chiseled bookshelves contain written books — mostly gibberish,
 * occasionally with cursed snippets.
 */
public class Level64637ChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_MIN_Y = 0;
    private static final int FLOOR_Y = 10;
    private static final int AIR_MIN_Y = 10;
    private static final int AIR_MAX_Y = 16; // 6 blocks tall — tall library room
    private static final int CEILING_Y = 16;
    private static final int CEILING_MAX_Y = 40;

    // Room grid: 8-block cells (6 interior + 1 wall on each side, shared with neighbors)
    private static final int CELL_SIZE = 8;
    private static final int CELLS_PER_AXIS = 16 / CELL_SIZE; // 2 cells per chunk axis
    private static final int WALL_THICKNESS = 1;
    private static final int ROOM_INTERIOR = CELL_SIZE - WALL_THICKNESS; // 7 interior blocks

    private static final double DOOR_SCALE = 1.0 / 16.0;
    private static final double PILLAR_SCALE = 1.0 / 48.0;

    private static final double CHISELED_CHANCE = 0.025; // ~2.5% of bookshelf blocks

    private final BookConfig bookConfig;

    public Level64637ChunkGenerator(BookConfig bookConfig) {
        this.bookConfig = bookConfig;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // Fill solid below floor and above ceiling
        chunkData.setRegion(0, FLOOR_MIN_Y, 0, 16, FLOOR_Y, 16, Material.DARK_OAK_PLANKS);
        chunkData.setRegion(0, CEILING_Y, 0, 16, CEILING_MAX_Y, 16, Material.DARK_OAK_PLANKS);

        // For each block column, determine if it's wall, interior, or doorway
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Determine cell position in the global grid
                int cellX = Math.floorDiv(worldX, CELL_SIZE);
                int cellZ = Math.floorDiv(worldZ, CELL_SIZE);
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);

                boolean isWallX = localX == 0;
                boolean isWallZ = localZ == 0;

                if (isWallX || isWallZ) {
                    // This is a wall position — check if it should be a doorway
                    boolean isDoor = false;

                    if (isWallX && !isWallZ) {
                        // Wall along X axis (between cellX-1 and cellX)
                        isDoor = isDoorway(seed, cellX, cellZ, localZ, true);
                    } else if (isWallZ && !isWallX) {
                        // Wall along Z axis (between cellZ-1 and cellZ)
                        isDoor = isDoorway(seed, cellX, cellZ, localX, false);
                    }
                    // Corner pillars (isWallX && isWallZ) are always solid

                    if (isDoor) {
                        // Doorway: air with dark oak floor
                        for (int y = AIR_MIN_Y; y < AIR_MIN_Y + 3; y++) {
                            chunkData.setBlock(x, y, z, Material.AIR);
                        }
                        // Keep upper wall as bookshelves above doorway
                        for (int y = AIR_MIN_Y + 3; y < AIR_MAX_Y; y++) {
                            chunkData.setBlock(x, y, z, pickBookshelf(chunkRng));
                        }
                    } else {
                        // Solid bookshelf wall floor-to-ceiling
                        for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
                            chunkData.setBlock(x, y, z, pickBookshelf(chunkRng));
                        }
                    }
                } else {
                    // Interior — air
                    for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }

                    // Interior bookshelf pillars (noise-driven, rare)
                    double pillarNoise = SimplexNoise.noise2(seed + 50, worldX * PILLAR_SCALE, worldZ * PILLAR_SCALE);
                    if (pillarNoise > 0.65 && localX == 4 && localZ == 4) {
                        for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
                            chunkData.setBlock(x, y, z, pickBookshelf(chunkRng));
                        }
                    }
                }
            }
        }

        // Sparse lighting — soul lanterns on the ceiling
        for (int cx = 0; cx < CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < CELLS_PER_AXIS; cz++) {
                // One light per room, offset position within room
                int lightX = cx * CELL_SIZE + 4;
                int lightZ = cz * CELL_SIZE + 4;
                if (lightX < 16 && lightZ < 16) {
                    chunkData.setBlock(lightX, AIR_MAX_Y - 1, lightZ, Material.SOUL_LANTERN);
                }

                // Occasional extra candles on floor
                if (chunkRng.nextDouble() < 0.3) {
                    int candleX = cx * CELL_SIZE + 1 + chunkRng.nextInt(ROOM_INTERIOR);
                    int candleZ = cz * CELL_SIZE + 1 + chunkRng.nextInt(ROOM_INTERIOR);
                    if (candleX < 16 && candleZ < 16
                            && chunkData.getType(candleX, AIR_MIN_Y, candleZ) == Material.AIR) {
                        chunkData.setBlock(candleX, AIR_MIN_Y, candleZ, Material.CANDLE);
                    }
                }
            }
        }
    }

    /**
     * Determines if a wall segment at a given position should be a doorway.
     * Uses noise so doors are consistent across chunk boundaries.
     */
    private boolean isDoorway(long seed, int cellX, int cellZ, int posAlongWall, boolean xWall) {
        // Doors only in the middle portion of the wall (positions 3-4 of 1-7)
        if (posAlongWall < 3 || posAlongWall > 4) return false;

        // Use noise based on cell coordinates to decide if this wall has a door
        long noiseSeed = xWall ? seed + 30 : seed + 31;
        double noise = SimplexNoise.noise2(noiseSeed, cellX * DOOR_SCALE, cellZ * DOOR_SCALE);

        // ~70% of walls get a door for good connectivity
        return noise > -0.4;
    }

    /**
     * Picks BOOKSHELF or rarely CHISELED_BOOKSHELF.
     */
    private Material pickBookshelf(Random rng) {
        if (rng.nextDouble() < CHISELED_CHANCE) {
            return Material.CHISELED_BOOKSHELF;
        }
        return Material.BOOKSHELF;
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new LibraryBookPopulator(bookConfig));
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
        return new Location(world, 4.5, AIR_MIN_Y, 4.5);
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

    // ── Book Config Record ──────────────────────────────────────────────

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

    // ── Block Populator — fills chiseled bookshelves with written books ──

    private static class LibraryBookPopulator extends BlockPopulator {

        private final BookConfig config;

        LibraryBookPopulator(BookConfig config) {
            this.config = config;
        }

        @Override
        public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            int startX = chunkX * 16;
            int startZ = chunkZ * 16;

            for (int x = startX; x < startX + 16; x++) {
                for (int z = startZ; z < startZ + 16; z++) {
                    for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
                        if (!region.isInRegion(x, y, z)) continue;
                        if (region.getType(x, y, z) != Material.CHISELED_BOOKSHELF) continue;

                        BlockState state = region.getBlockState(x, y, z);
                        if (!(state instanceof ChiseledBookshelf shelf)) continue;

                        fillShelf(shelf, random);
                        state.update(true, false);
                    }
                }
            }
        }

        private void fillShelf(ChiseledBookshelf shelf, Random rng) {
            int bookCount = 1 + rng.nextInt(6); // 1-6 books
            int maxSlots = shelf.getInventory().getSize();

            for (int i = 0; i < Math.min(bookCount, maxSlots); i++) {
                // Pick a random empty slot
                int slot = rng.nextInt(maxSlots);
                if (shelf.getInventory().getItem(slot) != null) continue;

                shelf.getInventory().setItem(slot, createBook(rng));
            }
        }

        private ItemStack createBook(Random rng) {
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();

            boolean isCursed = rng.nextDouble() < config.cursedChance
                    && !config.cursedSnippets.isEmpty();

            // Title
            if (isCursed && rng.nextDouble() < 0.3 && !config.cursedTitles.isEmpty()) {
                meta.setTitle(config.cursedTitles.get(rng.nextInt(config.cursedTitles.size())));
            } else {
                meta.setTitle(generateGibberishTitle(rng));
            }

            // Author
            if (isCursed && rng.nextDouble() < 0.3 && !config.cursedAuthors.isEmpty()) {
                meta.setAuthor(config.cursedAuthors.get(rng.nextInt(config.cursedAuthors.size())));
            } else {
                meta.setAuthor(generateGibberish(rng, 4 + rng.nextInt(8)));
            }

            // Pages
            int pageCount = 1 + rng.nextInt(3);
            for (int p = 0; p < pageCount; p++) {
                if (isCursed && p == rng.nextInt(pageCount)) {
                    // Embed cursed snippet in a page of gibberish
                    String snippet = config.cursedSnippets.get(
                            rng.nextInt(config.cursedSnippets.size()));
                    String before = generateGibberish(rng, 20 + rng.nextInt(40));
                    String after = generateGibberish(rng, 20 + rng.nextInt(40));
                    meta.addPage(before + " " + snippet + " " + after);
                } else {
                    meta.addPage(generateGibberish(rng, 80 + rng.nextInt(120)));
                }
            }

            book.setItemMeta(meta);
            return book;
        }

        private String generateGibberishTitle(Random rng) {
            // Format: "XXXX.XXXX.NNN"
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append((char) ('A' + rng.nextInt(26)));
            sb.append('.');
            for (int i = 0; i < 4; i++) sb.append((char) ('A' + rng.nextInt(26)));
            sb.append('.');
            for (int i = 0; i < 3; i++) sb.append((char) ('0' + rng.nextInt(10)));
            return sb.toString();
        }

        private String generateGibberish(Random rng, int length) {
            String chars = config.gibberishChars;
            if (chars.isEmpty()) chars = "abcdefghijklmnopqrstuvwxyz ";
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt(rng.nextInt(chars.length())));
            }
            return sb.toString();
        }
    }
}
