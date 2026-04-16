package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level64637ChunkGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level64637ChunkGenerator.BookConfig;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Populates chiseled bookshelves with books in the Library level (64637).
 * Only center-pillar bookshelves (positions 6-9) become chiseled — these are
 * far enough from chunk boundaries to avoid the updateNeighbourForOutputSignal
 * deadlock that occurs when setItem() triggers a 2-deep neighbor check into
 * an unloaded chunk.
 */
public class LibraryBookshelfListener implements Listener {

    private static final int CELL_SIZE = 16;
    private static final double CHISELED_CHANCE = 0.12;

    private static final String CONSONANTS = "bcdfghjklmnprstvwz";
    private static final String VOWELS = "aeiou";

    private final JavaPlugin plugin;
    private final BookConfig config;

    public LibraryBookshelfListener(JavaPlugin plugin, BookConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        if (!event.getWorld().getName().equals("bkrms_64637")) return;

        Chunk chunk = event.getChunk();
        long worldSeed = event.getWorld().getSeed();
        int cellX = Math.floorDiv(chunk.getX() * 16, CELL_SIZE);
        int cellZ = Math.floorDiv(chunk.getZ() * 16, CELL_SIZE);

        Random rng = new Random(chunk.getChunkKey());

        List<ShelfCandidate> candidates = new ArrayList<>();

        for (int layer = 0; layer < Level64637ChunkGenerator.NUM_LAYERS; layer++) {
            // Only pillar rooms on this layer have chiseled bookshelves
            if (!Level64637ChunkGenerator.hasCenterPillar(worldSeed, layer, cellX, cellZ)) continue;

            int shelfMinY = Level64637ChunkGenerator.BASE_Y + layer * Level64637ChunkGenerator.FLOOR_SPACING + 4;
            int shelfMaxY = Level64637ChunkGenerator.BASE_Y + layer * Level64637ChunkGenerator.FLOOR_SPACING + 8;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = shelfMinY; y <= shelfMaxY; y++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.getType() != Material.BOOKSHELF) continue;
                        if (rng.nextDouble() >= CHISELED_CHANCE) continue;

                        BlockFace facing = computeFacing(block.getX(), block.getZ());
                        if (facing == null) continue;

                        candidates.add(new ShelfCandidate(block.getLocation(), facing, rng.nextLong()));
                    }
                }
            }
        }

        if (candidates.isEmpty()) return;

        // Defer block modifications to next tick to avoid issues during chunk loading
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ShelfCandidate candidate : candidates) {
                Block block = candidate.location.getBlock();
                if (block.getType() != Material.BOOKSHELF) continue;

                Directional data = (Directional) Material.CHISELED_BOOKSHELF.createBlockData();
                data.setFacing(candidate.facing);
                block.setBlockData(data, false);

                if (block.getState() instanceof ChiseledBookshelf shelf) {
                    Random bookRng = new Random(candidate.seed);
                    fillShelf(shelf, bookRng);
                }
            }
        });
    }

    /**
     * Computes the facing direction for a pillar-face bookshelf.
     * Returns null for non-pillar positions, corners, and interior blocks.
     * All returned positions are in [6,9], safely far from chunk boundaries.
     */
    private BlockFace computeFacing(int worldX, int worldZ) {
        int localX = Math.floorMod(worldX, CELL_SIZE);
        int localZ = Math.floorMod(worldZ, CELL_SIZE);

        boolean inPillarX = localX >= Level64637ChunkGenerator.PILLAR_MIN
                && localX <= Level64637ChunkGenerator.PILLAR_MAX;
        boolean inPillarZ = localZ >= Level64637ChunkGenerator.PILLAR_MIN
                && localZ <= Level64637ChunkGenerator.PILLAR_MAX;

        if (!inPillarX || !inPillarZ) return null;

        boolean onEdgeX = localX == Level64637ChunkGenerator.PILLAR_MIN
                || localX == Level64637ChunkGenerator.PILLAR_MAX;
        boolean onEdgeZ = localZ == Level64637ChunkGenerator.PILLAR_MIN
                || localZ == Level64637ChunkGenerator.PILLAR_MAX;

        if (onEdgeX && onEdgeZ) return null; // corner pilaster

        if (onEdgeX) {
            return localX == Level64637ChunkGenerator.PILLAR_MIN ? BlockFace.WEST : BlockFace.EAST;
        }
        if (onEdgeZ) {
            return localZ == Level64637ChunkGenerator.PILLAR_MIN ? BlockFace.NORTH : BlockFace.SOUTH;
        }

        return null; // interior
    }

    private record ShelfCandidate(Location location, BlockFace facing, long seed) {}

    private void fillShelf(ChiseledBookshelf shelf, Random rng) {
        int bookCount = 1 + rng.nextInt(6);
        int maxSlots = shelf.getInventory().getSize();

        for (int i = 0; i < Math.min(bookCount, maxSlots); i++) {
            int slot = rng.nextInt(maxSlots);
            if (shelf.getInventory().getItem(slot) != null) continue;
            shelf.getInventory().setItem(slot, createBook(rng));
        }
    }

    private ItemStack createBook(Random rng) {
        // Rare pre-written books
        if (!config.preWrittenBooks().isEmpty()
                && rng.nextDouble() < config.preWrittenChance()) {
            BookConfig.PreWrittenBook pw =
                    config.preWrittenBooks().get(rng.nextInt(config.preWrittenBooks().size()));
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();
            meta.setTitle(pw.title());
            meta.setAuthor(pw.author());
            for (String page : pw.pages()) meta.addPage(page);
            book.setItemMeta(meta);
            return book;
        }

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        boolean isCursed = rng.nextDouble() < config.cursedChance()
                && !config.cursedSnippets().isEmpty();

        // Pick generation mode: 70% char gibberish, 20% syllabic, 8% word salad
        double roll = rng.nextDouble();
        int mode;
        if (config.wordPool().isEmpty() || roll < 0.714) {
            mode = 0; // char gibberish
        } else if (roll < 0.918) {
            mode = 1; // syllabic
        } else {
            mode = 2; // word salad
        }

        // Title
        if (isCursed && rng.nextDouble() < 0.3 && !config.cursedTitles().isEmpty()) {
            meta.setTitle(config.cursedTitles().get(rng.nextInt(config.cursedTitles().size())));
        } else {
            meta.setTitle(switch (mode) {
                case 1 -> generateSyllabicWord(rng, 2 + rng.nextInt(3));
                case 2 -> generateWordSalad(rng, 2 + rng.nextInt(3));
                default -> generateGibberishTitle(rng);
            });
        }

        // Author
        if (isCursed && rng.nextDouble() < 0.3 && !config.cursedAuthors().isEmpty()) {
            meta.setAuthor(config.cursedAuthors().get(rng.nextInt(config.cursedAuthors().size())));
        } else {
            meta.setAuthor(switch (mode) {
                case 1 -> generateSyllabicWord(rng, 1 + rng.nextInt(2))
                        + " " + generateSyllabicWord(rng, 1 + rng.nextInt(3));
                case 2 -> config.wordPool().get(rng.nextInt(config.wordPool().size()))
                        + " " + config.wordPool().get(rng.nextInt(config.wordPool().size()));
                default -> generateGibberish(rng, 4 + rng.nextInt(8));
            });
        }

        // Pages
        int pageCount = 1 + rng.nextInt(3);
        for (int p = 0; p < pageCount; p++) {
            String pageText;
            if (isCursed && p == rng.nextInt(pageCount)) {
                String snippet = config.cursedSnippets().get(
                        rng.nextInt(config.cursedSnippets().size()));
                String before = generateContent(rng, mode, 3 + rng.nextInt(5));
                String after = generateContent(rng, mode, 3 + rng.nextInt(5));
                pageText = before + " " + snippet + " " + after;
            } else {
                pageText = generateContent(rng, mode, 12 + rng.nextInt(18));
            }
            meta.addPage(pageText);
        }

        book.setItemMeta(meta);
        return book;
    }

    /** Dispatches to the right generator based on mode. wordCount is approximate word count. */
    private String generateContent(Random rng, int mode, int wordCount) {
        return switch (mode) {
            case 1 -> generateSyllabicText(rng, wordCount);
            case 2 -> generateWordSalad(rng, wordCount);
            default -> generateGibberish(rng, wordCount * 6);
        };
    }

    // ── Mode 0: Character gibberish (existing) ─────────────────────────

    private String generateGibberishTitle(Random rng) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append((char) ('A' + rng.nextInt(26)));
        sb.append('.');
        for (int i = 0; i < 4; i++) sb.append((char) ('A' + rng.nextInt(26)));
        sb.append('.');
        for (int i = 0; i < 3; i++) sb.append((char) ('0' + rng.nextInt(10)));
        return sb.toString();
    }

    private String generateGibberish(Random rng, int length) {
        String chars = config.gibberishChars();
        if (chars.isEmpty()) chars = "abcdefghijklmnopqrstuvwxyz ";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ── Mode 1: Syllabic nonsense ──────────────────────────────────────

    /** Generates a single pronounceable nonsense word with the given number of syllables. */
    private String generateSyllabicWord(Random rng, int syllables) {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < syllables; s++) {
            sb.append(CONSONANTS.charAt(rng.nextInt(CONSONANTS.length())));
            sb.append(VOWELS.charAt(rng.nextInt(VOWELS.length())));
            // Occasionally add a trailing consonant
            if (rng.nextInt(3) == 0) {
                sb.append(CONSONANTS.charAt(rng.nextInt(CONSONANTS.length())));
            }
        }
        return sb.toString();
    }

    /** Generates a passage of syllabic nonsense words with punctuation. */
    private String generateSyllabicText(Random rng, int wordCount) {
        StringBuilder sb = new StringBuilder();
        int untilPeriod = 5 + rng.nextInt(8);
        boolean capitalize = true;
        for (int w = 0; w < wordCount; w++) {
            String word = generateSyllabicWord(rng, 2 + rng.nextInt(3));
            if (capitalize) {
                word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
                capitalize = false;
            }
            sb.append(word);
            untilPeriod--;
            if (untilPeriod <= 0) {
                sb.append(". ");
                untilPeriod = 5 + rng.nextInt(8);
                capitalize = true;
            } else if (rng.nextInt(8) == 0) {
                sb.append(", ");
            } else {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    // ── Mode 2: Word salad ─────────────────────────────────────────────

    /** Generates a passage of random real words strung into pseudo-sentences. */
    private String generateWordSalad(Random rng, int wordCount) {
        List<String> pool = config.wordPool();
        if (pool.isEmpty()) return generateGibberish(rng, wordCount * 6);

        StringBuilder sb = new StringBuilder();
        int untilPeriod = 5 + rng.nextInt(8);
        boolean capitalize = true;
        for (int w = 0; w < wordCount; w++) {
            String word = pool.get(rng.nextInt(pool.size()));
            if (capitalize) {
                word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
                capitalize = false;
            }
            sb.append(word);
            untilPeriod--;
            if (untilPeriod <= 0) {
                sb.append(". ");
                untilPeriod = 5 + rng.nextInt(8);
                capitalize = true;
            } else {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }
}
