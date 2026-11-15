package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.skin;

import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;

/**
 * A player skin as a 64x64 ARGB grid in the modern layout, cleaned up the way
 * the vanilla client does it: legacy 64x32 skins are converted (the left arm
 * and leg are mirrored copies of the right), the base layer is made opaque,
 * and a legacy skin whose whole hat area is opaque (Notch's, and many old
 * skins) has its hat dropped, as the game draws them.
 */
public record Skin(String name, int[] argb, boolean slim) {

    public static final int SIZE = 64;

    public int pixel(int x, int y) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return 0;
        return argb[y * SIZE + x];
    }

    public static boolean opaque(int argb) {
        return (argb >>> 24) >= 128;
    }

    static @Nullable Skin fromImage(String name, BufferedImage image, boolean slim) {
        int w = image.getWidth(), h = image.getHeight();
        if (w != SIZE || (h != SIZE && h != SIZE / 2)) return null;
        int[] px = new int[SIZE * SIZE];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * SIZE + x] = image.getRGB(x, y);
            }
        }
        boolean legacy = h == SIZE / 2;
        if (legacy) convertLegacy(px);
        setNoAlpha(px, 0, 0, 32, 16);
        if (legacy) notchTransparencyHack(px, 32, 0, 64, 32);
        setNoAlpha(px, 0, 16, 64, 32);
        setNoAlpha(px, 16, 48, 48, 64);
        return new Skin(name, px, slim);
    }

    /** Vanilla's legacy-skin upgrade: copy each right-limb face into the left-limb slot, mirrored. */
    private static void convertLegacy(int[] px) {
        copyRect(px, 4, 16, 16, 32, 4, 4);
        copyRect(px, 8, 16, 16, 32, 4, 4);
        copyRect(px, 0, 20, 24, 32, 4, 12);
        copyRect(px, 4, 20, 16, 32, 4, 12);
        copyRect(px, 8, 20, 8, 32, 4, 12);
        copyRect(px, 12, 20, 16, 32, 4, 12);
        copyRect(px, 44, 16, -8, 32, 4, 4);
        copyRect(px, 48, 16, -8, 32, 4, 4);
        copyRect(px, 40, 20, 0, 32, 4, 12);
        copyRect(px, 44, 20, -8, 32, 4, 12);
        copyRect(px, 48, 20, -16, 32, 4, 12);
        copyRect(px, 52, 20, -8, 32, 4, 12);
    }

    /** The base layer is drawn opaque whatever its alpha. */
    private static void setNoAlpha(int[] px, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) px[y * SIZE + x] |= 0xFF000000;
    }

    /** An overlay region with no transparent pixel at all was never meant as an overlay: clear it. */
    private static void notchTransparencyHack(int[] px, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if ((px[y * SIZE + x] >>> 24) < 128) return;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) px[y * SIZE + x] = 0;
    }

    private static void copyRect(int[] px, int x0, int y0, int dx, int dy, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int src = (y0 + y) * SIZE + (x0 + x);
                int dst = (y0 + dy + y) * SIZE + (x0 + dx + (w - 1 - x));
                px[dst] = px[src];
            }
        }
    }
}
