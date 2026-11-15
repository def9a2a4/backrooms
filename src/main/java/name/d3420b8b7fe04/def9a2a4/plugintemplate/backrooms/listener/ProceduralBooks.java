package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level64637ChunkGenerator.BookConfig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;
import java.util.Random;

/**
 * The Library's procedural books: character gibberish, syllabic nonsense and
 * word salad (from {@link BookConfig#wordPool()}), now and then with a cursed
 * snippet, title or author. Shared by the Library's shelves and Level 0's
 * lecterns.
 */
public final class ProceduralBooks {

    private static final String CONSONANTS = "bcdfghjklmnprstvwz";
    private static final String VOWELS = "aeiou";

    private final BookConfig config;

    public ProceduralBooks(BookConfig config) {
        this.config = config;
    }

    public BookConfig config() {
        return config;
    }

    /** Generated text for the book grammar's {@code #~name#} symbols. */
    public String external(String name, Random rng) {
        return switch (name) {
            case "word" -> generateSyllabicWord(rng, 1 + rng.nextInt(3));
            case "syllabic" -> generateContent(rng, GenerationMode.SYLLABIC, 6 + rng.nextInt(12));
            case "salad" -> generateWordSalad(rng, 5 + rng.nextInt(10));
            case "gibberish" -> generateContent(rng, GenerationMode.GIBBERISH, 3 + rng.nextInt(4));
            default -> "";
        };
    }

    /** Text generation style for procedural books. Ratio is 5:2:1 (GIBBERISH:SYLLABIC:WORD_SALAD). */
    public enum GenerationMode {
        GIBBERISH, SYLLABIC, WORD_SALAD
    }

    public GenerationMode pickMode(Random rng) {
        if (config.wordPool().isEmpty()) return GenerationMode.GIBBERISH;
        int roll = rng.nextInt(8); // 5:2:1 ratio
        if (roll < 5) return GenerationMode.GIBBERISH;
        if (roll < 7) return GenerationMode.SYLLABIC;
        return GenerationMode.WORD_SALAD;
    }

    /** A whole procedural book: title, author, one to three pages, now and then cursed. */
    public ItemStack create(Random rng) {

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        boolean isCursed = rng.nextDouble() < config.cursedChance()
                && !config.cursedSnippets().isEmpty();
        GenerationMode mode = pickMode(rng);

        // Title
        if (isCursed && rng.nextDouble() < 0.3 && !config.cursedTitles().isEmpty()) {
            meta.setTitle(config.cursedTitles().get(rng.nextInt(config.cursedTitles().size())));
        } else {
            meta.setTitle(generateTitle(rng, mode));
        }

        // Author
        if (isCursed && rng.nextDouble() < 0.3 && !config.cursedAuthors().isEmpty()) {
            meta.setAuthor(config.cursedAuthors().get(rng.nextInt(config.cursedAuthors().size())));
        } else {
            meta.setAuthor(generateAuthor(rng, mode));
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

    private String generateTitle(Random rng, GenerationMode mode) {
        return switch (mode) {
            case SYLLABIC   -> generateSyllabicWord(rng, 2 + rng.nextInt(3));
            case WORD_SALAD -> generateWordSalad(rng, 2 + rng.nextInt(3));
            case GIBBERISH  -> generateGibberishTitle(rng);
        };
    }

    private String generateAuthor(Random rng, GenerationMode mode) {
        return switch (mode) {
            case SYLLABIC   -> generateSyllabicWord(rng, 1 + rng.nextInt(2))
                    + " " + generateSyllabicWord(rng, 1 + rng.nextInt(3));
            case WORD_SALAD -> config.wordPool().get(rng.nextInt(config.wordPool().size()))
                    + " " + config.wordPool().get(rng.nextInt(config.wordPool().size()));
            case GIBBERISH  -> generateGibberish(rng, 4 + rng.nextInt(8));
        };
    }

    /** A passage of about {@code wordCount} words in the given style. */
    public String generateContent(Random rng, GenerationMode mode, int wordCount) {
        return switch (mode) {
            case SYLLABIC   -> generateSyllabicText(rng, wordCount);
            case WORD_SALAD -> generateWordSalad(rng, wordCount);
            case GIBBERISH  -> generateGibberish(rng, wordCount * 6);
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
    public String generateSyllabicWord(Random rng, int syllables) {
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
    public String generateWordSalad(Random rng, int wordCount) {
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
