package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.book;

import java.util.Random;

/**
 * Rot for a page of text, by a level from 0 (clean) to 1 (barely legible):
 * typos, stutters, redactions, scrambled words (obfuscated), words struck
 * through, a line shouted, and at the worst a line that will not stop
 * repeating. Formatting codes are legacy section signs; words that already
 * carry one are left alone.
 */
public final class Decay {

    private static final String BLOCK = "█";

    private Decay() {}

    public static String apply(String page, double level, Random rng) {
        if (level <= 0.02) return page;
        String[] lines = page.split("\n", -1);
        StringBuilder out = new StringBuilder(page.length() + 32);
        String lastWords = null;
        for (int l = 0; l < lines.length; l++) {
            String line = lines[l];
            if (l > 0) out.append('\n');
            if (!line.isEmpty() && !line.contains("§") && rng.nextDouble() < 0.08 * level) {
                line = line.toUpperCase();
            }
            String[] words = line.split(" ", -1);
            for (int w = 0; w < words.length; w++) {
                if (w > 0) out.append(' ');
                out.append(word(words[w], level, rng));
            }
            if (words.length >= 3) lastWords = words[words.length - 3] + " " + words[words.length - 2] + " " + words[words.length - 1];
        }
        // At the worst, the page gives way to one line, over and over.
        if (lastWords != null && level > 0.6 && rng.nextDouble() < (level - 0.6) * 1.5) {
            String echo = lastWords.replaceAll("[.,;:!?]", "").toLowerCase().trim();
            int times = 3 + rng.nextInt(5);
            for (int i = 0; i < times; i++) out.append('\n').append(i == times - 1 ? "§8" : "").append(echo);
        }
        return out.toString();
    }

    private static String word(String w, double level, Random rng) {
        if (w.length() < 2 || w.contains("§")) return w;
        double r = rng.nextDouble();
        double p = level;
        if ((r -= 0.06 * p) < 0) return typo(w, rng);
        if ((r -= 0.035 * p) < 0) return w + " " + w.toLowerCase() + (rng.nextBoolean() ? " " + w.toLowerCase() : "");
        if ((r -= 0.04 * p) < 0) {
            // Redact the word but keep its punctuation, so sentences still end.
            int end = w.length();
            while (end > 1 && ".,;:!?'\")".indexOf(w.charAt(end - 1)) >= 0) end--;
            return BLOCK.repeat(Math.min(8, Math.max(2, end))) + w.substring(end);
        }
        if ((r -= 0.025 * p) < 0) return "§k" + w + "§r";
        if ((r -= 0.02 * p) < 0) return "§m" + w + "§r";
        return w;
    }

    private static String typo(String w, Random rng) {
        char[] c = w.toCharArray();
        int i = rng.nextInt(c.length - 1);
        if (!Character.isLetter(c[i]) || !Character.isLetter(c[i + 1])) return w;
        if (rng.nextBoolean()) {
            char t = c[i];
            c[i] = c[i + 1];
            c[i + 1] = t;
            return new String(c);
        }
        return w.substring(0, i) + w.substring(i + 1);
    }
}
