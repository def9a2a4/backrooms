package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.skin;

import org.bukkit.Material;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nearest alpha-era block for a skin pixel. Wool covers the saturated colours;
 * the earthy blocks (planks, dirt, sand, bricks, clay) cover skin tones, hair
 * and leather far better than wool alone. Colours are each block's average
 * texture colour. Matching is nearest in CIELAB.
 */
public final class SkinPalette {

    private record Entry(Material material, int r, int g, int b) {}

    private static final Entry[] PALETTE = {
            new Entry(Material.WHITE_WOOL, 234, 236, 237),
            new Entry(Material.ORANGE_WOOL, 241, 118, 20),
            new Entry(Material.MAGENTA_WOOL, 190, 69, 180),
            new Entry(Material.LIGHT_BLUE_WOOL, 58, 175, 217),
            new Entry(Material.YELLOW_WOOL, 249, 198, 40),
            new Entry(Material.LIME_WOOL, 112, 185, 26),
            new Entry(Material.PINK_WOOL, 238, 141, 172),
            new Entry(Material.GRAY_WOOL, 63, 68, 72),
            new Entry(Material.LIGHT_GRAY_WOOL, 142, 142, 135),
            new Entry(Material.CYAN_WOOL, 21, 138, 145),
            new Entry(Material.PURPLE_WOOL, 122, 42, 173),
            new Entry(Material.BLUE_WOOL, 53, 57, 157),
            new Entry(Material.BROWN_WOOL, 114, 72, 41),
            new Entry(Material.GREEN_WOOL, 85, 110, 28),
            new Entry(Material.RED_WOOL, 161, 39, 35),
            new Entry(Material.BLACK_WOOL, 21, 21, 26),
            new Entry(Material.OAK_PLANKS, 162, 131, 79),
            new Entry(Material.DIRT, 134, 96, 67),
            new Entry(Material.SAND, 219, 207, 163),
            new Entry(Material.GRAVEL, 132, 127, 127),
            new Entry(Material.STONE, 126, 126, 126),
            new Entry(Material.BRICKS, 151, 98, 83),
            new Entry(Material.CLAY, 161, 166, 179),
            new Entry(Material.NETHERRACK, 98, 38, 38),
            new Entry(Material.SOUL_SAND, 81, 62, 51),
            new Entry(Material.GOLD_BLOCK, 246, 208, 62),
            new Entry(Material.IRON_BLOCK, 220, 220, 220),
            new Entry(Material.DIAMOND_BLOCK, 98, 237, 228),
            new Entry(Material.OBSIDIAN, 15, 11, 25),
            new Entry(Material.SNOW_BLOCK, 249, 254, 254),
    };

    private static final Map<Integer, Material> CACHE = new ConcurrentHashMap<>();

    private SkinPalette() {}

    public static Material blockFor(int argb) {
        return CACHE.computeIfAbsent(argb & 0xFFFFFF, rgb -> {
            double[] lab = lab((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            Material best = Material.WHITE_WOOL;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < PALETTE.length; i++) {
                double[] p = PALETTE_LAB[i];
                double dl = lab[0] - p[0], da = lab[1] - p[1], db = lab[2] - p[2];
                double dist = dl * dl + da * da + db * db;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = PALETTE[i].material;
                }
            }
            return best;
        });
    }

    private static final double[][] PALETTE_LAB = new double[PALETTE.length][];
    static {
        for (int i = 0; i < PALETTE.length; i++) PALETTE_LAB[i] = lab(PALETTE[i].r, PALETTE[i].g, PALETTE[i].b);
    }

    /** sRGB → CIELAB (D65). Distances in Lab track what the eye sees; in RGB, warm skin can lose to a neutral grey. */
    private static double[] lab(int r, int g, int b) {
        double lr = linear(r), lg = linear(g), lb = linear(b);
        double x = (0.4124 * lr + 0.3576 * lg + 0.1805 * lb) / 0.95047;
        double y = 0.2126 * lr + 0.7152 * lg + 0.0722 * lb;
        double z = (0.0193 * lr + 0.1192 * lg + 0.9505 * lb) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{ 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz) };
    }

    private static double linear(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }
}
