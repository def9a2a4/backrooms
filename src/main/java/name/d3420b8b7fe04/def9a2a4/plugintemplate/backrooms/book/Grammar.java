package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.book;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

/**
 * A small Tracery-style text grammar.
 *
 * <ul>
 *   <li>{@code #rule#} expands a random alternative of a rule (or a bound
 *   variable of that name); modifiers follow dots: {@code #obj.a#},
 *   {@code #name.caps#}. Modifiers: {@code cap}, {@code caps}, {@code lower},
 *   {@code title}, {@code a} (with its article), {@code s} (plural),
 *   {@code initial} (first letter).</li>
 *   <li>{@code #~name#} asks the external source (gibberish, tallies, ...).</li>
 *   <li>{@code [var:template]} expands the template once and binds it, so a
 *   writer or a day stays the same across a whole book. It prints nothing.</li>
 *   <li>{@code {a|b|c}} picks inline; {@code {n:3-90}} is a number;
 *   {@code {?30:text}} includes the text 30% of the time; {@code {+name}}
 *   counts 1, 2, 3... through the book.</li>
 * </ul>
 *
 * A {@code #} that does not open a valid symbol is printed as is, so
 * "Report #347" needs no escaping.
 */
public final class Grammar {

    private static final int MAX_DEPTH = 24;

    private final Map<String, List<String>> rules;
    private final BiFunction<String, Random, String> external;

    public Grammar(Map<String, List<String>> rules, BiFunction<String, Random, String> external) {
        this.rules = rules;
        this.external = external;
    }

    public boolean has(String rule) {
        return rules.containsKey(rule);
    }

    public String expand(String text, Random rng, Map<String, String> vars) {
        return expand(text, rng, vars, 0);
    }

    private String expand(String text, Random rng, Map<String, String> vars, int depth) {
        if (text == null) return "";
        if (depth > MAX_DEPTH) return text;
        StringBuilder out = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == '[') {
                int end = matching(text, i, '[', ']');
                int colon = end > 0 ? text.indexOf(':', i) : -1;
                if (end > 0 && colon > i + 1 && colon < end && isName(text.substring(i + 1, colon))) {
                    vars.put(text.substring(i + 1, colon), expand(text.substring(colon + 1, end), rng, vars, depth + 1));
                    i = end + 1;
                    continue;
                }
            } else if (ch == '{') {
                int end = matching(text, i, '{', '}');
                if (end > 0) {
                    out.append(inline(text.substring(i + 1, end), rng, vars, depth));
                    i = end + 1;
                    continue;
                }
            } else if (ch == '#') {
                int end = text.indexOf('#', i + 1);
                if (end > 0 && isSymbol(text.substring(i + 1, end))) {
                    out.append(symbol(text.substring(i + 1, end), rng, vars, depth));
                    i = end + 1;
                    continue;
                }
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private String inline(String body, Random rng, Map<String, String> vars, int depth) {
        if (body.startsWith("n:")) {
            String[] range = body.substring(2).split("-");
            try {
                int lo = Integer.parseInt(range[0].trim()), hi = Integer.parseInt(range[1].trim());
                return Integer.toString(lo + rng.nextInt(Math.max(1, hi - lo + 1)));
            } catch (RuntimeException e) {
                return "{" + body + "}";
            }
        }
        if (body.startsWith("+") && isName(body.substring(1))) {
            // A counter: numbered lists keep counting across pages.
            String key = "+" + body.substring(1);
            int next = Integer.parseInt(vars.getOrDefault(key, "0")) + 1;
            vars.put(key, Integer.toString(next));
            return Integer.toString(next);
        }
        if (body.startsWith("?")) {
            int colon = body.indexOf(':');
            if (colon > 1) {
                try {
                    int pct = Integer.parseInt(body.substring(1, colon));
                    return rng.nextInt(100) < pct ? expand(body.substring(colon + 1), rng, vars, depth + 1) : "";
                } catch (NumberFormatException ignored) {
                    // fall through to a plain choice
                }
            }
        }
        List<String> options = splitTop(body);
        return expand(options.get(rng.nextInt(options.size())), rng, vars, depth + 1);
    }

    private String symbol(String spec, Random rng, Map<String, String> vars, int depth) {
        String[] parts = spec.split("\\.");
        String name = parts[0];
        String value;
        if (vars.containsKey(name)) {
            value = vars.get(name);
        } else if (name.startsWith("~")) {
            value = external.apply(name.substring(1), rng);
        } else {
            List<String> alts = rules.get(name);
            value = alts == null || alts.isEmpty()
                    ? "((" + name + "))"
                    : expand(alts.get(rng.nextInt(alts.size())), rng, vars, depth + 1);
        }
        for (int m = 1; m < parts.length; m++) value = modify(value, parts[m]);
        return value;
    }

    private static String modify(String v, String mod) {
        if (v.isEmpty()) return v;
        return switch (mod) {
            case "cap" -> Character.toUpperCase(v.charAt(0)) + v.substring(1);
            case "caps" -> v.toUpperCase();
            case "lower" -> v.toLowerCase();
            case "title" -> {
                StringBuilder sb = new StringBuilder();
                for (String w : v.split(" ")) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
                }
                yield sb.toString();
            }
            case "a" -> ("aeiouAEIOU".indexOf(v.charAt(0)) >= 0 ? "an " : "a ") + v;
            case "s" -> v.endsWith("s") || v.endsWith("x") || v.endsWith("ch") || v.endsWith("sh") ? v + "es"
                    : v.endsWith("y") && v.length() > 1 && "aeiou".indexOf(v.charAt(v.length() - 2)) < 0
                    ? v.substring(0, v.length() - 1) + "ies" : v + "s";
            case "initial" -> v.substring(0, 1);
            default -> v;
        };
    }

    private static boolean isSymbol(String s) {
        if (s.isEmpty()) return false;
        String name = s.split("\\.")[0];
        if (name.startsWith("~")) name = name.substring(1);
        if (!isName(name)) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '~')) return false;
        }
        return true;
    }

    private static boolean isName(String s) {
        if (s.isEmpty() || !(Character.isLetter(s.charAt(0)) || s.charAt(0) == '_')) return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return false;
        }
        return true;
    }

    private static int matching(String text, int open, char o, char c) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == o) depth++;
            else if (ch == c && --depth == 0) return i;
        }
        return -1;
    }

    /** Splits on '|' outside nested braces and brackets. */
    private static List<String> splitTop(String body) {
        List<String> out = new java.util.ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '{' || ch == '[') depth++;
            else if (ch == '}' || ch == ']') depth--;
            else if (ch == '|' && depth == 0) {
                out.add(body.substring(start, i));
                start = i + 1;
            }
        }
        out.add(body.substring(start));
        return out;
    }
}
