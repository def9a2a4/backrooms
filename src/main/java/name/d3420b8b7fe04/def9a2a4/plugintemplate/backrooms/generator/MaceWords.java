package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mace's word lists (words/*.txt in the Mace pack, see scripts/mace/extract_schem.py) and the sign
 * text it makes from them: "[ChurchAdj]" on a template sign is a random line of churchadj.txt,
 * "[house]" a house name, "[nb]" a notice-board note. Ports of Mace's SourceWorld.SignText and
 * NoticeBoard. Lines keep their spaces: Mace joins city names as prefix + suffix line, and the
 * noun lists start with one.
 *
 * <p>Immutable after {@link #load}; the per-city state lives in the {@link #forCity} resolver.
 */
final class MaceWords {

    private static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final int NOTICE_KINDS = 15;
    private static final int TRIES = 100;

    private final Map<String, List<String>> lists;

    private MaceWords(Map<String, List<String>> lists) {
        this.lists = lists;
    }

    /** Reads words/&lt;name&gt;.txt from the pack; names are lowercase. */
    static MaceWords load(ZipFile zip) throws IOException {
        Map<String, List<String>> lists = new HashMap<>();
        for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
            ZipEntry e = en.nextElement();
            String n = e.getName();
            if (!n.startsWith("words/") || !n.endsWith(".txt")) continue;
            String text;
            try (InputStream in = zip.getInputStream(e)) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (text.startsWith("﻿")) text = text.substring(1);
            List<String> lines = new ArrayList<>();
            for (String line : text.split("\r?\n")) {
                if (!line.isBlank() && !line.startsWith("//")) lines.add(line);
            }
            if (!lines.isEmpty()) lists.put(n.substring(6, n.length() - 4).toLowerCase(Locale.ROOT), List.copyOf(lines));
        }
        return new MaceWords(lists);
    }

    /** A random line of the list, or null if there's no such list. */
    @Nullable
    String line(String list, Random rng) {
        List<String> l = lists.get(list.toLowerCase(Locale.ROOT));
        return l == null ? null : l.get(rng.nextInt(l.size()));
    }

    /**
     * Mace's city name: "City of " + a prefix line + a suffix line, different words, at most 14
     * letters together.
     */
    String cityName(String prefixList, String suffixList, Random rng) {
        String start = "", end = "";
        for (int i = 0; i < TRIES; i++) {
            start = orEmpty(line(prefixList, rng));
            end = orEmpty(line(suffixList, rng));
            if (!start.trim().equalsIgnoreCase(end.trim()) && (start + end).length() <= 14) break;
        }
        return "City of " + start + end;
    }

    /**
     * Sign tokens for one city: house numbers count up and most notice kinds appear once, as in
     * Mace. Not thread-safe; use it for one city's fill at a time.
     */
    Function<String, @Nullable String> forCity(Random rng) {
        boolean[] noticeUsed = new boolean[NOTICE_KINDS];
        int[] houseNumber = {0};
        return token -> switch (token) {
            case "nb", "nb2" -> fitting(() -> notice(rng, noticeUsed));
            case "nb1" -> "Created by~Mace~by Robson.~Have fun :)";
            case "house" -> {
                houseNumber[0]++;
                yield fitting(() -> {
                    String noun = orEmpty(line("nouns", rng)).trim();
                    if (!noun.isEmpty()) noun = noun.substring(0, 1).toUpperCase(Locale.ROOT) + noun.substring(1);
                    return orEmpty(line("housetypes", rng)) + " of~" + orEmpty(line("titles", rng)) + " " + noun
                            + "~- " + houseNumber[0] + " -";
                });
            }
            default -> lists.containsKey(token) ? fitting(() -> line(token, rng)) : null;
        };
    }

    /** The first text that fits on a sign (Mace retries forever; this gives up with the last). */
    @Nullable
    private static String fitting(java.util.function.Supplier<@Nullable String> make) {
        String text = null;
        for (int i = 0; i < TRIES; i++) {
            text = make.get();
            if (text == null || MaceBuilding.fits(text)) break;
        }
        return text;
    }

    /** NoticeBoard.RandomSign: a note for the city's notice board; kinds 5 and up once per city. */
    private String notice(Random rng, boolean[] used) {
        int kind;
        int tries = 0;
        do {
            kind = rng.nextInt(NOTICE_KINDS);
        } while (kind >= 5 && used[kind] && ++tries < TRIES);
        used[kind] = true;
        return switch (kind) {
            case 0, 1 -> pick(rng, "I will", "I can", "Will", "Can", "Can you") + " "
                    + pick(rng, "trade", "swap", "sell") + " "
                    + pick(rng, "gold", "iron", "dirt", "tools", "glass", "flowers", "cake", "mushrooms") + " for "
                    + pick(rng, "obsidian", "wood", "sand", "bricks", "coal", "stone", "cookies", "diamonds")
                    + "~-" + pick(rng, "See", "Talk to") + " " + letter(rng) + "." + letter(rng) + ".";
            case 2 -> pick(rng, "Church", "Order") + " of the holy " + orEmpty(line("churchnoun", rng))
                    + " are meeting this " + pick(rng, DAYS);
            case 3 -> pick(rng, "Mrs", "Miss") + " " + orEmpty(line("adjectives", rng)) + " has lost her "
                    + pick(rng, "cat", "dog", "glasses", "marbles", "knitting") + ". "
                    + pick(rng, "Reward offered", "Please help");
            case 4 -> pick(rng, "Armour", "Property", "House", "Weapons", "Gold", "Bodyguard", "Pet wolf", "Books", "Tools")
                    + " for sale~-" + pick(rng, "See", "Talk to") + " " + letter(rng) + "." + letter(rng) + ".";
            case 5 -> "Lost pet creeper. Last seen near the mini crater";
            case 6 -> "Israphel~~Wanted dead~or alive";
            case 7 -> "Lost Jaffa Cakes. Please return to Honeydew";
            case 8 -> "Read note " + (500 + rng.nextInt(499));
            case 9 -> "Buy one get one free on gravestones!";
            case 10 -> "Archery practice this " + pick(rng, DAYS) + " afternoon";
            case 11 -> "Seen a crime? Tell the nearest city guard";
            case 12 -> "New city law: No minors can be miners";
            case 13 -> "Council meeting this " + pick(rng, DAYS);
            default -> "Numbers for lovers:~" + pick(rng, "220 284", "1184 1210", "2620 2924", "5020 5564", "6232 6368");
        };
    }

    private static String pick(Random rng, String... options) {
        return options[rng.nextInt(options.length)];
    }

    private static char letter(Random rng) {
        return (char) ('A' + rng.nextInt(26));
    }

    private static String orEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }
}
