package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.book;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

/**
 * Procedural books from a grammar of genres ({@code books/grammar.yml}):
 * journals, memos, rules, letters, logs, reports, agendas, catalogue cards and
 * more, each written in one or more voices ("lobby", "office", "library").
 * A book binds its writer, day, room and so on once (a genre's {@code setup}),
 * runs to an opening page, a few body pages and a closing page, and rots as it
 * goes ({@link Decay}): some books stay clean, some fall apart by the end.
 */
public final class BookGenerator {

    public record Book(String title, String author, List<String> pages) {}

    private record Genre(String name, int weight, List<String> voices, double decay, String setup,
                         List<String> titles, List<String> authors, List<String> opening,
                         List<String> body, int bodyMin, int bodyMax, List<String> closing) {}

    /** Minecraft limits: titles 32 characters; a page shows about 14 lines of ~19 characters. */
    private static final int TITLE_MAX = 32;
    private static final int PAGE_CHARS = 230;
    private static final int PAGE_LINES = 13;
    private static final int LINE_WIDTH = 19;
    private static final int MAX_PAGES = 12;

    private final Grammar grammar;
    private final List<Genre> genres;

    private BookGenerator(Grammar grammar, List<Genre> genres) {
        this.grammar = grammar;
        this.genres = genres;
    }

    /**
     * Loads the grammar. {@code external} answers {@code #~name#} symbols
     * (gibberish, syllables, word salad); the generator adds {@code ~tally}.
     */
    public static BookGenerator load(Reader yaml, BiFunction<String, Random, String> external) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(yaml);
        Map<String, List<String>> rules = new HashMap<>();
        ConfigurationSection rs = cfg.getConfigurationSection("rules");
        if (rs != null) {
            for (String key : rs.getKeys(false)) rules.put(key, rs.getStringList(key));
        }
        List<Genre> genres = new ArrayList<>();
        ConfigurationSection gs = cfg.getConfigurationSection("genres");
        if (gs != null) {
            for (String key : gs.getKeys(false)) {
                ConfigurationSection g = gs.getConfigurationSection(key);
                if (g == null) continue;
                List<Integer> bodyPages = g.getIntegerList("body_pages");
                genres.add(new Genre(key, g.getInt("weight", 1), g.getStringList("voices"),
                        g.getDouble("decay", 0.4), g.getString("setup", ""),
                        g.getStringList("title"), g.getStringList("author"), g.getStringList("opening"),
                        g.getStringList("body"),
                        bodyPages.size() > 0 ? bodyPages.get(0) : 0, bodyPages.size() > 1 ? bodyPages.get(1) : 0,
                        g.getStringList("closing")));
            }
        }
        BiFunction<String, Random, String> ext = (name, rng) -> switch (name) {
            case "tally" -> tally(rng);
            default -> external != null ? external.apply(name, rng) : "";
        };
        return new BookGenerator(new Grammar(rules, ext), genres);
    }

    public boolean hasVoice(String voice) {
        return genres.stream().anyMatch(g -> g.voices().contains(voice));
    }

    public Book generate(String voice, Random rng) {
        List<Genre> pool = genres.stream().filter(g -> g.voices().contains(voice)).toList();
        if (pool.isEmpty()) return new Book("Untitled", "Unknown", List.of(""));
        int total = pool.stream().mapToInt(Genre::weight).sum();
        int roll = rng.nextInt(Math.max(1, total));
        Genre genre = pool.get(pool.size() - 1);
        for (Genre g : pool) {
            if ((roll -= g.weight()) < 0) {
                genre = g;
                break;
            }
        }
        return write(genre, voice, rng);
    }

    private Book write(Genre g, String voice, Random rng) {
        Map<String, String> vars = new HashMap<>();
        vars.put("voice", voice);
        if (grammar.has("setup")) grammar.expand("#setup#", rng, vars);
        grammar.expand(g.setup(), rng, vars);

        String title = clip(clean(pick(g.titles(), rng, vars, "Untitled")), TITLE_MAX);
        String author = clip(clean(pick(g.authors(), rng, vars, "Unknown")), TITLE_MAX);

        List<String> raw = new ArrayList<>();
        if (!g.opening().isEmpty()) raw.add(pick(g.opening(), rng, vars, ""));
        int bodies = g.bodyMin() + rng.nextInt(Math.max(1, g.bodyMax() - g.bodyMin() + 1));
        for (int i = 0; i < bodies && !g.body().isEmpty(); i++) raw.add(pick(g.body(), rng, vars, ""));
        if (!g.closing().isEmpty()) raw.add(pick(g.closing(), rng, vars, ""));
        if (raw.isEmpty()) raw.add("");

        // Rot: most books a little, some a lot, worsening toward the end.
        double base = g.decay() * Math.pow(rng.nextDouble(), 1.6);
        boolean failing = rng.nextDouble() < g.decay() * 0.25;
        List<String> pages = new ArrayList<>();
        for (int i = 0; i < raw.size() && pages.size() < MAX_PAGES; i++) {
            double t = raw.size() == 1 ? 1.0 : i / (double) (raw.size() - 1);
            double level = Math.min(1.0, base * (0.35 + 0.65 * t) + (failing ? 0.7 * t * t : 0));
            for (String page : paginate(clean(raw.get(i)))) {
                if (pages.size() >= MAX_PAGES) break;
                pages.add(Decay.apply(page, level, rng));
            }
        }
        return new Book(title, author, pages);
    }

    private String pick(List<String> options, Random rng, Map<String, String> vars, String fallback) {
        if (options.isEmpty()) return fallback;
        return grammar.expand(options.get(rng.nextInt(options.size())), rng, vars);
    }

    /** Tidies what expansion leaves behind: doubled spaces, spaces before punctuation, blank-line runs. */
    private static String clean(String s) {
        return s.replaceAll("[ \\t]+", " ")
                .replaceAll(" ([.,;:!?])", "$1")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{4,}", "\n\n\n")
                .trim();
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max).trim();
    }

    /** Splits text into Minecraft pages at word boundaries; a form feed forces a break. */
    static List<String> paginate(String text) {
        List<String> pages = new ArrayList<>();
        for (String chunk : text.split("\f")) {
            StringBuilder page = new StringBuilder();
            int lines = 1, col = 0;
            for (String line : chunk.split("\n", -1)) {
                if (page.length() > 0) {
                    if (lines + 1 > PAGE_LINES) {
                        pages.add(page.toString().trim());
                        page.setLength(0);
                        lines = 1;
                    } else {
                        page.append('\n');
                        lines++;
                    }
                    col = 0;
                }
                for (String word : line.split(" ")) {
                    int len = visible(word);
                    if (col > 0 && col + 1 + len > LINE_WIDTH) {
                        lines++;
                        col = 0;
                    }
                    if (lines > PAGE_LINES || page.length() + word.length() + 1 > PAGE_CHARS) {
                        pages.add(page.toString().trim());
                        page.setLength(0);
                        lines = 1;
                        col = 0;
                    }
                    if (col > 0) page.append(' ');
                    else if (page.length() > 0 && page.charAt(page.length() - 1) != '\n') page.append(' ');
                    page.append(word);
                    col += (col > 0 ? 1 : 0) + len;
                    while (col > LINE_WIDTH) {
                        col -= LINE_WIDTH;
                        lines++;
                    }
                }
            }
            if (page.length() > 0 || pages.isEmpty()) pages.add(page.toString().trim());
        }
        return pages;
    }

    private static int visible(String word) {
        return word.replaceAll("§.", "").length();
    }

    private static String tally(Random rng) {
        int n = 3 + rng.nextInt(40);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            sb.append(i % 5 == 0 ? "/" : "|");
            if (i % 5 == 0) sb.append(' ');
            if (i % 20 == 0) sb.append('\n');
        }
        return sb.toString().trim();
    }
}
