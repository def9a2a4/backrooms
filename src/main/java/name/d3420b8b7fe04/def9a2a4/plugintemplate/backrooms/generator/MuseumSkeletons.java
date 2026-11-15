package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

import java.util.function.DoubleUnaryOperator;

/**
 * Level 1882's skeletons, drawn from bone blocks along a body: real animals (whale, sauropod,
 * theropod, mammoth, pterosaur, ichthyosaur, plesiosaur, turtle, trilobite) and Minecraft's mobs
 * scaled up (creeper, skeleton, zombie, enderman, piglin, villager, wither, spider, ghast, ender
 * dragon, pig, cow, horse, wolf, camel, sniffer, hoglin, goat, chicken, strider, squid). Hanging
 * skeletons are hung from the ceiling by chains that start on a bone and run up to the first solid
 * block that isn't bone.
 */
final class MuseumSkeletons {

    private MuseumSkeletons() {}

    static final BlockData[] BONE = new BlockData[3];   // axis x, y, z
    static final BlockData IVORY = Material.CALCITE.createBlockData();
    static final BlockData CHAIN = Material.IRON_CHAIN.createBlockData();

    static {
        Axis[] axes = {Axis.X, Axis.Y, Axis.Z};
        for (int i = 0; i < 3; i++) {
            Orientable b = (Orientable) Material.BONE_BLOCK.createBlockData();
            b.setAxis(axes[i]);
            BONE[i] = b;
        }
    }

    /** What can stand in a room ({@code len} ≤ ~22, height ≤ ~14) or hang in one. */
    enum Kind {
        THEROPOD, MAMMOTH, SAUROPOD, PTEROSAUR(true), ICHTHYOSAUR(true), PLESIOSAUR(true), TURTLE, TRILOBITE,
        CREEPER, SKELETON, ZOMBIE, ENDERMAN, PIGLIN, VILLAGER, WITHER, SPIDER, GHAST(true), DRAGON(true),
        PIG, COW, HORSE, WOLF, CAMEL, SNIFFER, HOGLIN, GOAT, CHICKEN, STRIDER, SQUID(true), WHALE(true), BAT(true), PHANTOM(true);

        final boolean hangs;
        Kind() { this(false); }
        Kind(boolean hangs) { this.hangs = hangs; }

        String label() {
            return switch (this) {
                case THEROPOD -> "Theropod";
                case MAMMOTH -> "Mammoth";
                case SAUROPOD -> "Sauropod, juvenile";
                case PTEROSAUR -> "Pterosaur";
                case ICHTHYOSAUR -> "Ichthyosaur";
                case PLESIOSAUR -> "Plesiosaur";
                case TURTLE -> "Turtle, giant";
                case TRILOBITE -> "Trilobite, giant";
                case WHALE -> "Whale, juvenile";
                case DRAGON -> "Ender Dragon, juvenile";
                case BAT -> "Bat, giant";
                default -> cap(name());
            };
        }

        private static String cap(String n) {
            return n.charAt(0) + n.substring(1).toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Draws bones along a body: a = along from the head end, l = to the left, v = up from hy. */
    record Body(MuseumCanvas c, double hx, double hy, double hz, int ux, int uz) {
        double x(double a, double l) { return hx + ux * a - uz * l; }
        double z(double a, double l) { return hz + uz * a + ux * l; }

        void put(double a, double l, double v, BlockData b) {
            c.set((int) Math.floor(x(a, l)), (int) Math.floor(hy + v), (int) Math.floor(z(a, l)), b);
        }

        void bone(double a0, double l0, double v0, double a1, double l1, double v1, double r) {
            bone(a0, l0, v0, a1, l1, v1, r, null);
        }

        void bone(double a0, double l0, double v0, double a1, double l1, double v1, double r, BlockData mat) {
            double x0 = x(a0, l0), z0 = z(a0, l0), x1 = x(a1, l1), z1 = z(a1, l1);
            double dx = x1 - x0, dy = v1 - v0, dz = z1 - z0;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            BlockData b = mat != null ? mat
                    : BONE[Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz) ? 0 : Math.abs(dy) >= Math.abs(dz) ? 1 : 2];
            int n = Math.max(1, (int) Math.ceil(len * 2));
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n;
                ball(x0 + dx * t, hy + v0 + dy * t, z0 + dz * t, r, b);
            }
        }

        void ball(double x, double y, double z, double r, BlockData b) {
            if (r < 0.75) {
                c.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), b);
                return;
            }
            int R = (int) Math.ceil(r);
            for (int dx = -R; dx <= R; dx++) {
                for (int dy = -R; dy <= R; dy++) {
                    for (int dz = -R; dz <= R; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= r * r) {
                            c.set((int) Math.floor(x) + dx, (int) Math.floor(y) + dy, (int) Math.floor(z) + dz, b);
                        }
                    }
                }
            }
        }

        void ballAt(double a, double l, double v, double r) {
            ball(x(a, l), hy + v, z(a, l), r, BONE[1]);
        }

        /** A pair of ribs hanging from (a, v): out {@code w}, down {@code h}, swept back by {@code sweep}. */
        void ribs(double a, double v, double w, double h, double sweep) {
            int n = (int) Math.ceil((w + h) * 2.5);
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i <= n; i++) {
                    double th = 0.93 * Math.PI * i / n;
                    double l = side * w * Math.sin(th), dv = -h * (1 - Math.cos(th)) / 2;
                    double da = sweep * (1 - Math.cos(th)) / 2;
                    ball(x(a + da, l), hy + v + dv, z(a + da, l), 0.5, BONE[1]);
                }
            }
        }

        /** A horizontal ring round an upright spine (creepers, humanoids): radii rl across, ra along. */
        void ring(double a, double v, double rl, double ra) {
            int n = (int) Math.ceil((rl + ra) * 4);
            for (int i = 0; i < n; i++) {
                double th = 2 * Math.PI * i / n;
                put(a + ra * Math.sin(th), rl * Math.cos(th), v, BONE[1]);
            }
        }

        /**
         * A boxy skull: a hollow box from a0 (its face) back {@code depth}, {@code w} wide, from v0 up
         * {@code h}; the face carries {@code face} (8 rows of 8, '.' a hole), the sides eye holes if
         * {@code sideEyes}.
         */
        void skull(double a0, double depth, double w, double v0, double h, String[] face, boolean sideEyes) {
            int na = (int) Math.round(depth), nl = (int) Math.round(w), nv = (int) Math.round(h);
            for (int i = 0; i < na; i++) {
                for (int j = 0; j < nl; j++) {
                    for (int k = 0; k < nv; k++) {
                        boolean shell = i == 0 || i == na - 1 || j == 0 || j == nl - 1 || k == 0 || k == nv - 1;
                        if (!shell) continue;
                        if (i == 0 && face != null) {
                            int col = Math.min(7, j * 8 / nl), row = Math.min(7, (nv - 1 - k) * 8 / nv);
                            if (face[row].charAt(col) == '.') continue;
                        }
                        if (sideEyes && (j == 0 || j == nl - 1) && i == Math.max(1, na / 3) && k == nv * 2 / 3) continue;
                        put(a0 + i, -w / 2 + j + 0.5, v0 + k, BONE[1]);
                    }
                }
            }
        }

        /** A chain from the bone at (a, l, v) up through any bone above it to the ceiling. */
        void hang(double a, double l, double v) {
            int x = (int) Math.floor(x(a, l)), z = (int) Math.floor(z(a, l));
            int y = (int) Math.floor(hy + v);
            if (c.type(x, y, z) == null) return;    // not this chunk's column (or only labels)
            for (; y < c.maxY; y++) {
                Material m = c.type(x, y, z);
                if (m == Material.BONE_BLOCK || m == Material.CALCITE || m == Material.IRON_CHAIN) continue;
                if (!c.air(x, y, z)) return;
                c.set(x, y, z, CHAIN);
            }
        }
    }

    static final String[] FACE_CREEPER = {"########", "########", "#..##..#", "#..##..#", "###..###", "##....##", "##....##", "##.##.##"};
    static final String[] FACE_SKELETON = {"########", "########", "#..##..#", "#..##..#", "###..###", "########", "#.#.#.##", "########"};
    static final String[] FACE_GHAST = {"########", "########", "#..##..#", "########", "########", "###..###", "###..###", "########"};
    static final String[] FACE_ENDERMAN = {"########", "########", "########", "########", "#...#..#", "########", "########", "########"};
    static final String[] FACE_SPIDER = {"########", "########", "##.##.##", "#.#..#.#", "########", "#.####.#", "########", "########"};
    static final String[] FACE_SNOUT = {"########", "########", "########", "########", "##.##.##", "##.##.##", "########", "########"};

    // ── Drawing a kind into a room ───────────────────────────────────────

    /**
     * A room-sized skeleton of {@code kind}, centered on (cx, cz), facing along (ux, uz), standing
     * on y0 or (hangers) hanging with its spine at {@code hangY}.
     */
    static void draw(MuseumCanvas c, Kind kind, double cx, double y0, double cz, int ux, int uz, double hangY) {
        // head end: half the drawn length ahead of the center, back toward -u
        double len = switch (kind) {
            case THEROPOD, SAUROPOD, DRAGON, ICHTHYOSAUR, PLESIOSAUR, WHALE -> 20;
            case MAMMOTH -> 16;
            case PTEROSAUR, BAT, PHANTOM -> 10;
            case TURTLE, TRILOBITE -> 14;
            case SPIDER -> 14;
            case PIG, WOLF -> 13;
            case COW, GOAT, HOGLIN -> 15;
            case HORSE, CAMEL -> 16;
            case SNIFFER -> 18;
            case CHICKEN, STRIDER -> 8;
            default -> 8;
        };
        Body b = new Body(c, cx - ux * len / 2, kind.hangs ? hangY : y0, cz - uz * len / 2, ux, uz);
        Body mid = new Body(c, cx, kind.hangs ? hangY : y0, cz, ux, uz);
        switch (kind) {
            case THEROPOD -> theropod(b, len);
            case MAMMOTH -> mammoth(b, len);
            case SAUROPOD -> sauropod(b, len);
            case PTEROSAUR -> flyer(new Body(c, cx - ux * 3, hangY, cz - uz * 3, ux, uz), 8, 11, 0.12, 0.05, 2.5, true);
            case BAT -> flyer(new Body(c, cx - ux * 2, hangY, cz - uz * 2, ux, uz), 5, 9, 0.05, 0.1, 1.0, false);
            case PHANTOM -> flyer(new Body(c, cx - ux * 4, hangY, cz - uz * 4, ux, uz), 9, 10, 0.0, 0.35, 1.5, false);
            case DRAGON -> flyer(b, len, 12, 0.3, 0.4, 2.2, false);
            case ICHTHYOSAUR -> swimmer(b, len, 0.0, 3.5, true);
            case PLESIOSAUR -> swimmer(b, len, 0.4, 1.2, false);
            case WHALE -> whale(b, len, 9);
            case TURTLE -> turtle(mid, 1.2);
            case TRILOBITE -> trilobite(b, len);
            case CREEPER -> creeper(mid, 0.45, 0.75);
            case SKELETON -> humanoid(mid, 0.38, 0.65, 0);
            case ZOMBIE -> humanoid(mid, 0.38, 0.65, 1);
            case ENDERMAN -> humanoid(mid, 0.27, 0.6, 2);
            case PIGLIN -> humanoid(mid, 0.38, 0.65, 3);
            case VILLAGER -> humanoid(mid, 0.38, 0.65, 4);
            case WITHER -> wither(mid, 0.42, 0.65);
            case SPIDER -> spider(b, 1.1);
            case GHAST -> ghast(mid, 0.45);
            case CHICKEN -> chicken(mid, 0.9);
            case STRIDER -> strider(mid, 0.6);
            case SQUID -> squid(mid, 0.6);
            default -> quadruped(b, quad(kind));
        }
    }

    // ── Real animals ─────────────────────────────────────────────────────

    /** Spine points for hanging: chains go up from these. */
    private static void spine(Body b, double from, double to, DoubleUnaryOperator v, DoubleUnaryOperator l,
                              DoubleUnaryOperator r) {
        double pa = from, pl = l.applyAsDouble(from), pv = v.applyAsDouble(from);
        for (double a = from + 1; a <= to + 1e-9; a += 1) {
            double nl = l.applyAsDouble(a), nv = v.applyAsDouble(a);
            b.bone(pa, pl, pv, a, nl, nv, r.applyAsDouble(a));
            pa = a;
            pl = nl;
            pv = nv;
        }
    }

    /** A blue whale, head first; {@code half}: room to spread its flippers. */
    static void whale(Body b, double len, int half) {
        double skull = 0.2 * len;
        DoubleUnaryOperator v = a -> { double s = a / len; return 3 * Math.sin(Math.PI * s) - 1.5 * s; };
        DoubleUnaryOperator l = a -> { double s = a / len; return 2.5 * Math.sin(2 * Math.PI * s) * s; };
        double k = len / 80.0;
        // rostrum: a broad plate, wider toward the back
        for (double a = 0; a <= skull; a += 0.5) {
            double q = a / skull, w = (1.2 + 4.5 * q) * Math.max(0.5, k * 1.2), vv = 1 - (1 - q) * 1.5 * k;
            for (double ll = -w; ll <= w; ll += 0.5) b.put(a, ll, vv, BONE[b.ux() != 0 ? 0 : 2]);
            if (q > 0.5) for (double ll = -w * 0.4; ll <= w * 0.4; ll += 0.5) b.put(a, ll, vv + 1, BONE[1]);
        }
        double jw = 5.9 * Math.max(0.5, k * 1.2);
        for (int side = -1; side <= 1; side += 2) {
            b.bone(skull, side * jw, -2.5 * k, skull / 2, side * jw * 0.85, -3.2 * k, 0.6);
            b.bone(skull / 2, side * jw * 0.85, -3.2 * k, 0, side * 1.0, -1.5 * k, 0.6);
        }
        spine(b, skull, len, v, l, a -> (1.3 * (1 - a / len) + 0.5) * Math.max(0.6, k));
        for (double a = skull; a <= len; a += 1) {
            double s = a / len;
            if (s < 0.7 && Math.round(a) % 2 == 0) b.bone(a, l.applyAsDouble(a), v.applyAsDouble(a), a + 0.5,
                    l.applyAsDouble(a), v.applyAsDouble(a) + (1.5 + 2 * (1 - s)) * Math.max(0.5, k), 0.5);
            if (s >= 0.24 && s <= 0.5 && Math.round(a) % 2 == 0) {
                double q = (s - 0.24) / 0.26, size = Math.sin(Math.PI * (0.25 + 0.75 * q));
                b.ribs(a, v.applyAsDouble(a), (6.5 * size + 1) * Math.max(0.4, k), (8 * size + 1) * Math.max(0.4, k), 1.5 * k);
            }
        }
        double fa = 0.27 * len;
        for (int side = -1; side <= 1; side += 2) {
            b.bone(fa, side * 4 * k, -2 * k, fa + 2 * k, side * 8 * k, -6 * k, 0.8 * Math.max(0.7, k));
            b.bone(fa + 2 * k, side * 8 * k, -6 * k, fa + 5 * k, side * Math.min(11 * k, half - 2), -10 * k, 0.6);
        }
        for (double s : new double[]{0.22, 0.4, 0.6, 0.85}) {
            double a = Math.round(s * len);
            b.hang(a, l.applyAsDouble(a), v.applyAsDouble(a));
        }
    }

    /** A long-necked sauropod standing at hy, head end at a = 0. */
    static void sauropod(Body b, double len) {
        double k = len / 70.0, sh = 15 * k;
        DoubleUnaryOperator v = a -> {
            double s = a / len;
            if (s < 0.28) return sh + 16 * k * Math.pow((0.28 - s) / 0.28, 1.2);
            if (s < 0.55) return sh + 1.5 * k * Math.sin(Math.PI * (s - 0.28) / 0.27);
            return sh - (sh - 2.5 * k) * Math.pow((s - 0.55) / 0.45, 0.8);
        };
        DoubleUnaryOperator l = a -> { double s = a / len; return s > 0.55 ? 4 * k * Math.sin(Math.PI * (s - 0.55) / 0.45) : 0; };
        spine(b, 0, len, v, l, a -> {
            double s = a / len;
            return s < 0.28 ? (0.6 + 0.4 * s / 0.28) * Math.max(1, k * 1.2) : s < 0.55 ? 1.5 * Math.max(0.6, k) : (1.3 - s) * Math.max(0.6, k);
        });
        int step = Math.max(2, (int) Math.round(2 * k));
        for (int a = (int) (0.3 * len); a < 0.53 * len; a += step) b.ribs(a, v.applyAsDouble(a), 4.5 * k + 0.8, 8 * k + 0.8, 0);
        double hv = v.applyAsDouble(0);
        b.bone(0, 0, hv, -2.5 * k - 1, 0, hv - 0.8, Math.max(0.6, 1.2 * k));
        b.bone(-0.5, 0, hv - 1.2 * k - 0.5, -2.5 * k - 1, 0, hv - 1.5 * k - 0.5, 0.5);
        for (double s : new double[]{0.31, 0.53}) {
            double a = s * len, vv = v.applyAsDouble(a);
            double fwd = s < 0.4 ? 1 : -1;
            for (int sd = -1; sd <= 1; sd += 2) {
                b.bone(a, sd * 3 * k, vv - 1, a + fwd * k, sd * 3.5 * k, 7 * k, Math.max(0.6, 1.1 * k));
                b.bone(a + fwd * k, sd * 3.5 * k, 7 * k, a, sd * 3.5 * k, 0, Math.max(0.6, k));
            }
        }
    }

    /** A two-legged predator, jaws open. */
    static void theropod(Body b, double len) {
        double k = len / 22.0, hh = 8 * k;
        b.bone(0, 0, hh + 2 * k, 4 * k, 0, hh + 3 * k, 1.2 * Math.max(0.7, k));
        b.bone(0.3, 0, hh + 2.5 * k, 3.5 * k, 0, hh + 3.8 * k, 0.5);
        b.bone(4 * k, 0, hh + 1.5 * k, 0.5, 0, hh - 0.5 * k, 0.6);
        b.bone(4.5 * k, 0, hh + 2.5 * k, 6 * k, 0, hh + 1 * k, 0.9 * Math.max(0.7, k));
        DoubleUnaryOperator v = a -> {
            double s = a / len;
            return s < 0.48 ? hh + k - 0.5 * k * (s - 0.27) / 0.21 : hh + 0.5 * k - 2.5 * k * (s - 0.48) / 0.52;
        };
        DoubleUnaryOperator l = a -> { double s = a / len; return s > 0.48 ? 1.5 * k * Math.sin(Math.PI * (s - 0.48) / 0.52) : 0; };
        spine(b, 6 * k, len, v, l, a -> a / len < 0.5 ? 0.9 * Math.max(0.7, k) : 0.6);
        for (double a = 0.27 * len; a < 0.46 * len; a += 1) b.ribs(a, v.applyAsDouble(a), 2.2 * k, 3.5 * k, 0.5);
        double la = 0.45 * len;
        for (int sd = -1; sd <= 1; sd += 2) {
            b.bone(la, sd * 1.8 * k, hh, la - 2 * k, sd * 2.2 * k, hh - 4 * k, 0.9 * Math.max(0.7, k));
            b.bone(la - 2 * k, sd * 2.2 * k, hh - 4 * k, la + 1.5 * k, sd * 2.2 * k, 1.5 * k, 0.7 * Math.max(0.8, k));
            b.bone(la + 1.5 * k, sd * 2.2 * k, 1.5 * k, la - 1.5 * k, sd * 2.2 * k, 0, 0.5);
            double aa = 0.28 * len;
            b.bone(aa, sd * 1.5 * k, hh, aa - 1.2 * k, sd * 1.8 * k, hh - 1.5 * k, 0.5);
        }
    }

    /** A mammoth with curling ivory tusks. */
    static void mammoth(Body b, double len) {
        double k = len / 18.0, sh = 9 * k, h0 = 5.5 * k;
        for (int sd = -1; sd <= 1; sd += 2) {
            b.bone(h0 + k, sd * 1.3 * k, sh - k, h0 - 3 * k, sd * 2.5 * k, 3 * k, 0.6, IVORY);
            b.bone(h0 - 3 * k, sd * 2.5 * k, 3 * k, h0 - 5.5 * k, sd * 1.5 * k, 6 * k, 0.6, IVORY);
            b.bone(h0 - 5.5 * k, sd * 1.5 * k, 6 * k, h0 - 5 * k, sd * 0.5 * k, 8 * k, 0.5, IVORY);
        }
        b.ballAt(h0 + 1.5 * k, 0, sh + 1, 1.8 * k);
        DoubleUnaryOperator v = a -> {
            double s = (a - h0) / (len - h0);
            return s < 0.8 ? sh + 1.5 * k * Math.sin(Math.PI * s / 0.8) + k * (1 - s) : sh - 4 * k * (s - 0.8) / 0.2;
        };
        spine(b, h0 + 3 * k, len, v, a -> 0, a -> (a - h0) / (len - h0) < 0.8 ? 1.0 : 0.5);
        for (double a = h0 + 3 * k; a <= len; a += 2) {
            double s = (a - h0) / (len - h0);
            if (s > 0.1 && s < 0.7) b.ribs(a, v.applyAsDouble(a), 3 * k, 5 * k, 0);
        }
        for (double s : new double[]{0.15, 0.7}) {
            double a = h0 + s * (len - h0);
            for (int sd = -1; sd <= 1; sd += 2) b.bone(a, sd * 2 * k, sh - 1, a, sd * 2.2 * k, 0, 0.9 * Math.max(0.8, k));
        }
    }

    /**
     * Wings: a pterosaur, a bat, or a dragon. {@code span} is one wing's reach; {@code neck} and
     * {@code tail} are shares of {@code len}; {@code beak} the skull's length.
     */
    static void flyer(Body b, double len, double span, double neck, double tail, double beak, boolean crest) {
        double head = beak, n0 = head, n1 = head + neck * len, t0 = len - tail * len;
        DoubleUnaryOperator v = a -> a < n1 ? 1.5 * Math.sin(Math.PI * (a - n0) / Math.max(1, n1 - n0)) : -0.5 * (a - n1) / len * 4;
        DoubleUnaryOperator l = a -> a > t0 ? 1.5 * Math.sin(Math.PI * (a - t0) / Math.max(1, len - t0)) : 0;
        // skull: a long beak (pterosaur) or a boxy head with a jaw (dragon)
        b.bone(0, 0, 0.3, head, 0, v.applyAsDouble(n0), 0.6);
        b.bone(0.5, 0, -0.6, head, 0, v.applyAsDouble(n0) - 1, 0.5);
        if (crest) b.bone(head, 0, 0.8, head + 2, 0, 2.5, 0.5);
        spine(b, n0, len, v, l, a -> a < t0 ? 0.6 : 0.5);
        double sh = (n1 + t0) / 2 - (t0 - n1) / 4;
        for (double a = n1; a < t0; a += 1.5) b.ribs(a, v.applyAsDouble(a), 1.5, 2.5, 0.3);
        for (double a = t0; a < len && tail > 0.2; a += 3) b.bone(a, l.applyAsDouble(a), v.applyAsDouble(a), a + 0.5, l.applyAsDouble(a), v.applyAsDouble(a) + 1.2, 0.5);
        double vs = v.applyAsDouble(sh);
        for (int sd = -1; sd <= 1; sd += 2) {
            double ea = sh + 1, el = sd * span * 0.35, ev = vs + span * 0.18;
            double wa = sh + 2, wl = sd * span * 0.62, wv = vs + span * 0.08;
            b.bone(sh, sd, vs, ea, el, ev, 0.6);
            b.bone(ea, el, ev, wa, wl, wv, 0.5);
            for (int f = 0; f < 4; f++) {
                double ta = wa + 2 + f * len * 0.1, tl = sd * span * (1 - 0.13 * f), tv = wv - 0.6 * f;
                b.bone(wa, wl, wv, ta, tl, tv, 0.5);
            }
            // legs dangling
            b.bone(t0 - 1, sd, v.applyAsDouble(t0 - 1), t0, sd * 1.5, v.applyAsDouble(t0) - 3, 0.5);
            b.hang(wa, wl, wv);
        }
        b.hang(Math.round(n0 + 1), 0, v.applyAsDouble(Math.round(n0 + 1)));
        b.hang(Math.round(sh), 0, v.applyAsDouble(Math.round(sh)));
        if (tail > 0.2) b.hang(Math.round((t0 + len) / 2), l.applyAsDouble(Math.round((t0 + len) / 2)), v.applyAsDouble(Math.round((t0 + len) / 2)));
    }

    /** An ichthyosaur (long snout, upright tail) or a plesiosaur (long neck, four paddles). */
    static void swimmer(Body b, double len, double neck, double snout, boolean fluke) {
        double n1 = snout + neck * len, body1 = n1 + 0.35 * len;
        DoubleUnaryOperator v = a -> 0.8 * Math.sin(Math.PI * a / len);
        DoubleUnaryOperator l = a -> a > body1 ? 1.2 * Math.sin(Math.PI * (a - body1) / (len - body1)) : 0;
        b.bone(0, 0, 0.2, snout, 0, v.applyAsDouble(snout), 0.6);
        b.ballAt(snout, 0, v.applyAsDouble(snout), 1.0);
        spine(b, snout, len, v, l, a -> a < body1 ? 0.7 : 0.5);
        for (double a = n1; a < body1; a += 1.5) b.ribs(a, v.applyAsDouble(a), 2.2, 2.8, 0.3);
        for (double fa : new double[]{n1 + 1, body1 - 1}) {
            for (int sd = -1; sd <= 1; sd += 2) {
                b.bone(fa, sd * 1.5, v.applyAsDouble(fa) - 1, fa + 2.5, sd * 5, v.applyAsDouble(fa) - 2.5, 0.5);
            }
        }
        if (fluke) {
            b.bone(len - 1, l.applyAsDouble(len - 1), v.applyAsDouble(len - 1), len + 1, 0, 3, 0.5);
            b.bone(len - 1, l.applyAsDouble(len - 1), v.applyAsDouble(len - 1), len + 1, 0, -2.5, 0.5);
            b.bone(n1 + 2, 0, v.applyAsDouble(n1 + 2), n1 + 4, 0, v.applyAsDouble(n1 + 4) + 2.5, 0.5);
        }
        for (double a : new double[]{Math.round(n1), Math.round(body1), Math.round((body1 + len) / 2)}) {
            b.hang(a, l.applyAsDouble(a), v.applyAsDouble(a));
        }
    }

    /** A giant turtle: a latticed shell on four flippers. */
    static void turtle(Body b, double k) {
        double ra = 6 * k, rl = 5 * k, rv = 4 * k;
        for (double th = 0; th < Math.PI / 2; th += 0.08) {
            for (double ph = 0; ph < 2 * Math.PI; ph += 0.06) {
                double a = ra * Math.cos(th) * Math.cos(ph), l = rl * Math.cos(th) * Math.sin(ph), v = rv * Math.sin(th);
                boolean plate = ((int) Math.floor(ph * 3) + (int) Math.floor(th * 5)) % 2 == 0;
                boolean seam = Math.abs(Math.sin(ph * 3)) < 0.12 || Math.abs(Math.sin(th * 5)) < 0.15;
                if (seam || plate) b.put(a, l, 1.5 + v, seam ? IVORY : BONE[1]);
            }
        }
        b.bone(-ra, 0, 2.5, -ra - 2.5 * k, 0, 3, 0.8);
        for (int sd = -1; sd <= 1; sd += 2) {
            b.bone(-ra * 0.5, sd * rl * 0.8, 1.5, -ra * 0.9, sd * (rl + 3 * k), 0.5, 0.6);
            b.bone(ra * 0.5, sd * rl * 0.8, 1.5, ra * 0.7, sd * (rl + 2 * k), 0.5, 0.6);
        }
    }

    /** A giant trilobite lying flat: three lobes in segments. */
    static void trilobite(Body b, double len) {
        BlockData dark = Material.POLISHED_DEEPSLATE.createBlockData();
        for (double a = 0; a < len; a += 1) {
            double s = a / len, w = (s < 0.2 ? 0.6 + 2 * s : 1.0 - 0.6 * Math.max(0, s - 0.6) / 0.4) * len * 0.32;
            for (double l = -w; l <= w; l += 0.5) {
                boolean axial = Math.abs(l) < w * 0.28;
                BlockData m = ((int) a % 2 == 0) == axial ? BONE[1] : dark;
                b.put(a, l, axial ? 1 : 0, m);
            }
        }
        b.put(1.5, -2.5, 1, IVORY);
        b.put(1.5, 2.5, 1, IVORY);
    }

    // ── Minecraft's mobs ─────────────────────────────────────────────────

    /**
     * A creeper: four short legs, an upright spine in rings, a cube skull with the face. Heights
     * are {@code k} blocks per model pixel, widths and the head {@code w} (wider, so the bones
     * stay apart at room scale).
     */
    static void creeper(Body b, double k, double w) {
        for (int fa = -1; fa <= 1; fa += 2) {
            for (int sd = -1; sd <= 1; sd += 2) {
                b.bone(fa * 3 * w, sd * 2 * w, 0, fa * 2 * w, sd * 2 * w, 6 * k, 0.8);
            }
        }
        b.bone(-2 * w, -2 * w, 6 * k, 2 * w, 2 * w, 6 * k, 0.5);
        b.bone(-2 * w, 2 * w, 6 * k, 2 * w, -2 * w, 6 * k, 0.5);
        b.bone(0, 0, 6 * k, 0, 0, 18 * k, 0.8);
        for (double v = 8 * k; v <= 17 * k; v += 2) b.ring(0, v, 3.5 * w, 1.8 * w);
        b.skull(-4 * w, 8 * w, 8 * w, 18 * k, 8 * w, FACE_CREEPER, false);
    }

    /**
     * A biped mob's skeleton in Minecraft's proportions: 0 skeleton, 1 zombie (arms out), 2
     * enderman (long and thin), 3 piglin (snout and tusks), 4 villager (nose, arms folded).
     * Heights are {@code k} blocks per model pixel, widths and the head {@code w}.
     */
    static void humanoid(Body b, double k, double w, int type) {
        double legs = (type == 2 ? 30 : 12) * k, torso = 12 * k, arm = (type == 2 ? 30 : 12) * k, head = 8 * w;
        double r = type == 2 ? 0.5 : 0.6;
        for (int sd = -1; sd <= 1; sd += 2) {
            b.bone(0, sd * 2 * w, 0, 0, sd * 2 * w, legs, r);
            b.bone(0, sd * 2 * w, 0, -1.5, sd * 2 * w, 0, 0.5);   // feet
        }
        b.bone(0, -2.5 * w, legs, 0, 2.5 * w, legs, 0.6);
        b.bone(0, 0, legs, 0, 0, legs + torso, 0.6);
        for (double v = legs + 2; v < legs + torso - 1; v += 1.5) b.ring(0, v, 3.2 * w, 1.6 * w);
        double sh = legs + torso - 0.5;
        b.bone(0, -6 * w, sh, 0, 6 * w, sh, 0.5);
        for (int sd = -1; sd <= 1; sd += 2) {
            if (type == 1) b.bone(0, sd * 6 * w, sh, -arm, sd * 6 * w, sh, r);                 // zombie: arms out in front
            else if (type == 4) b.bone(0, sd * 6 * w, sh, -2.5 * w, -sd * 3 * w, sh - 4, r);    // villager: folded
            else b.bone(0, sd * 6 * w, sh, 0, sd * 6.5 * w, sh - arm, r);
        }
        double hv = legs + torso + 0.5;
        String[] face = type == 2 ? FACE_ENDERMAN : type == 3 ? FACE_SNOUT : FACE_SKELETON;
        double hw = type == 3 ? 10 * w : head;
        b.skull(-head / 2, head, hw, hv, head, face, false);
        if (type == 3) {   // piglin snout and tusks
            b.skull(-head / 2 - 2, 2, 4 * w, hv + 1, 3 * w, null, false);
            for (int sd = -1; sd <= 1; sd += 2) b.put(-head / 2 - 1, sd * 2.5 * w, hv, IVORY);
        }
        if (type == 4) b.skull(-head / 2 - 2, 2, 2 * w, hv + 1, 4 * w, null, false);   // nose
    }

    /** The wither: three skulls on a shoulder beam over a spine and ribs, no legs. */
    static void wither(Body b, double k, double w) {
        b.bone(0, 0, 0, 0, 0, 3 * k, 0.6);
        b.bone(0, 0, 3 * k, 1.5 * w, 0, 10 * k, 0.6);
        b.bone(1.5 * w, 0, 10 * k, 0, 0, 22 * k, 0.7);
        for (double v = 13 * k; v < 21 * k; v += 1.5) b.ring(0, v, 3.5 * w, 1.8 * w);
        double sh = 22 * k;
        b.bone(0, -10 * w, sh, 0, 10 * w, sh, 0.6);
        b.skull(-4 * w, 8 * w, 8 * w, sh + 1, 8 * w, FACE_SKELETON, false);
        // side skulls, out along the beam
        for (int sd = -1; sd <= 1; sd += 2) {
            Body side = new Body(b.c(), b.x(0, sd * 7.5 * w), b.hy(), b.z(0, sd * 7.5 * w), b.ux(), b.uz());
            side.skull(-3 * w, 6 * w, 6 * w, sh, 6 * w, FACE_SKELETON, false);
        }
    }

    /** A spider: a ribbed abdomen and thorax, an eight-eyed skull, eight jointed legs. */
    static void spider(Body b, double k) {
        double bh = 4 * k;
        b.skull(0, 4 * k, 6 * k, bh - 2 * k, 5 * k, FACE_SPIDER, false);
        b.bone(4 * k, 0, bh, 14 * k, 0, bh + k, 0.6);
        for (double a = 4 * k; a < 7 * k; a += 1) b.ribs(a, bh + k, 2.5 * k, 3 * k, 0);
        for (double a = 7 * k; a < 14 * k; a += 1.2) {
            double s = (a - 7 * k) / (7 * k);
            double w = 4.5 * k * Math.sin(Math.PI * (0.15 + 0.85 * s)) + 0.5;
            b.ring(a, bh + k, w, 0.01);
        }
        for (int i = 0; i < 4; i++) {
            double a0 = 4.5 * k + i * 0.8 * k, spread = (i - 1.5) * 3 * k;
            for (int sd = -1; sd <= 1; sd += 2) {
                b.bone(a0, sd * 2.5 * k, bh, a0 + spread * 0.4, sd * 6 * k, bh + 3 * k, 0.5);
                b.bone(a0 + spread * 0.4, sd * 6 * k, bh + 3 * k, a0 + spread, sd * 10 * k, 0, 0.5);
            }
        }
    }

    /** A ghast: a hollow cube skull with nine tentacle bones hanging under it. */
    static void ghast(Body b, double k) {
        double s = 16 * k;
        b.skull(-s / 2, s, s, 0, s, FACE_GHAST, false);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                double a = i * s / 3, l = j * s / 3, len = (6 + Math.floorMod(i * 7 + j * 3, 5) * 2) * k;
                b.bone(a, l, 0, a + 0.4 * i, l + 0.4 * j, -len, 0.5);
            }
        }
        for (int[] cnr : new int[][]{{-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) {
            b.hang(cnr[0] * (s / 2 - 1), cnr[1] * (s / 2 - 1.5), s - 1);
        }
    }

    /** A chicken: two thin legs, ribs, folded wings, a small skull with a beak. */
    static void chicken(Body b, double k) {
        double hip = 5 * k;
        for (int sd = -1; sd <= 1; sd += 2) {
            b.bone(0.5, sd * 1.2 * k, hip, 0, sd * 1.2 * k, 0, 0.5);
            b.bone(0, sd * 1.2 * k, 0, -1.5 * k, sd * 1.2 * k, 0, 0.5);
            b.bone(-1, sd * 2.5 * k, hip + 2.5 * k, 3 * k, sd * 2.8 * k, hip + 1.5 * k, 0.5);
        }
        b.bone(-2 * k, 0, hip + 3 * k, 3.5 * k, 0, hip + 2 * k, 0.6);
        for (double a = -1.5 * k; a < 3 * k; a += 1) b.ribs(a, hip + 3 * k, 2 * k, 3 * k, 0);
        b.bone(-2 * k, 0, hip + 3 * k, -3 * k, 0, hip + 6 * k, 0.5);
        b.skull(-4.5 * k, 3 * k, 3 * k, hip + 5.5 * k, 3 * k, null, true);
        b.bone(-4.5 * k, 0, hip + 6.5 * k, -6 * k, 0, hip + 6 * k, 0.5);
        b.bone(3.5 * k, 0, hip + 2 * k, 4.5 * k, 0, hip + 4 * k, 0.5);
    }

    /** A strider: a boxy ribcage on two long bent legs. */
    static void strider(Body b, double k) {
        double hip = 16 * k;
        for (int sd = -1; sd <= 1; sd += 2) {
            b.bone(0, sd * 4 * k, hip, -2 * k, sd * 5 * k, hip / 2, 0.6);
            b.bone(-2 * k, sd * 5 * k, hip / 2, 0, sd * 4 * k, 0, 0.6);
        }
        for (double v = hip; v < hip + 14 * k; v += 2) b.ring(0, v, 8 * k, 8 * k);
        b.bone(0, 0, hip, 0, 0, hip + 14 * k, 0.6);
        for (double a = -6 * k; a <= 6 * k; a += 3 * k) b.bone(a, 0, hip + 14 * k, a, 0, hip + 17 * k, 0.5);
    }

    /** A squid's pen and arms, hanging: a long mantle and eight tentacles under it. */
    static void squid(Body b, double k) {
        for (double v = 0; v < 12 * k; v += 1.5) b.ring(0, v, 5 * k * Math.sin(Math.PI * (0.2 + 0.6 * v / (12 * k))), 5 * k * Math.sin(Math.PI * (0.2 + 0.6 * v / (12 * k))));
        b.bone(0, 0, 0, 0, 0, 12 * k, 0.5);
        for (int i = 0; i < 8; i++) {
            double th = i * Math.PI / 4, a = 3 * k * Math.cos(th), l = 3 * k * Math.sin(th);
            b.bone(a, l, 0, a * 1.4, l * 1.4, -9 * k - (i % 3), 0.5);
        }
        b.hang(0, 0, 12 * k - 1);
    }

    /** Proportions of a four-legged mob, in blocks. */
    record Quad(double body, double legH, double ribW, double ribH, double neck, double neckRise, double headLen,
                double headH, double headW, double snout, double tail, double tailRise, int extras) {}

    static final int HORNS = 1, TUSKS = 2, HUMP = 4, NOSE = 8;

    static Quad quad(Kind k) {
        return switch (k) {
            case PIG -> new Quad(7, 4, 3, 3.5, 0.5, 0, 4, 4, 4, 1.5, 0.5, 0, 0);
            case COW -> new Quad(8, 6, 3.2, 4.5, 1, 0.5, 4, 4, 4, 1, 1, -2, HORNS);
            case HORSE -> new Quad(8, 8, 2.5, 4, 4, 5, 5, 3, 3, 0, 4, -4, 0);
            case WOLF -> new Quad(6, 4.5, 2, 3, 1.5, 1, 3.5, 3, 3, 1.5, 4, 2, 0);
            case CAMEL -> new Quad(8, 9, 3, 4, 5, 4, 5, 3, 3, 0, 1.5, -2, HUMP);
            case SNIFFER -> new Quad(11, 3.5, 4.5, 5, 0.5, 0, 6, 5, 5, 2, 0.5, 0, NOSE);
            case HOGLIN -> new Quad(9, 5, 3.5, 4.5, 0.5, -0.5, 6, 5, 6, 0, 1, 0, TUSKS);
            case GOAT -> new Quad(7, 5.5, 2.5, 3.5, 1, 1.5, 3.5, 3.5, 3, 0, 1, 1, HORNS);
            default -> new Quad(7, 4, 3, 3.5, 0.5, 0, 4, 4, 4, 1, 1, 0, 0);
        };
    }

    /** A four-legged mob: boxy skull at the front, neck, ribs, four legs, a tail. */
    static void quadruped(Body b, Quad q) {
        double a0 = q.headLen() + q.snout(), as = a0 + q.neck(), ah = as + q.body();
        double back = q.legH() + q.ribH() * 0.7;
        DoubleUnaryOperator v = a -> back + ((q.extras() & HUMP) != 0 ? 2 * Math.max(0, Math.sin(Math.PI * (a - as) / q.body())) : 0);
        double hv = back + q.neckRise();
        b.skull(q.snout(), q.headLen(), q.headW(), hv - q.headH() / 2, q.headH(), FACE_SNOUT, true);
        if (q.snout() > 0) b.skull(0, q.snout() + 1, q.headW() * 0.6, hv - q.headH() / 2, q.headH() * 0.5, null, false);
        if ((q.extras() & HORNS) != 0) {
            for (int sd = -1; sd <= 1; sd += 2) b.bone(q.snout() + q.headLen() - 1, sd * q.headW() / 2, hv + q.headH() / 2, q.snout() + q.headLen() + 1, sd * (q.headW() / 2 + 1.5), hv + q.headH() / 2 + 2, 0.5, IVORY);
        }
        if ((q.extras() & TUSKS) != 0) {
            for (int sd = -1; sd <= 1; sd += 2) b.bone(q.snout() + 1, sd * q.headW() / 2, hv - q.headH() / 2, -1, sd * (q.headW() / 2 + 1), hv, 0.5, IVORY);
        }
        if ((q.extras() & NOSE) != 0) b.skull(-1, 2, q.headW() * 0.8, hv - q.headH() / 2, 2, null, false);
        b.bone(a0, 0, hv, as, 0, v.applyAsDouble(as), 0.6);
        spine(b, as, ah, v, a -> 0, a -> 0.7);
        for (double a = as + 1; a < ah - 0.5; a += 1.5) b.ribs(a, v.applyAsDouble(a), q.ribW(), q.ribH(), 0.3);
        for (double a : new double[]{as + 1, ah - 1}) {
            double knee = a < (as + ah) / 2 ? -0.6 : 0.6;
            for (int sd = -1; sd <= 1; sd += 2) {
                b.bone(a, sd * q.ribW() * 0.7, v.applyAsDouble(a), a + knee, sd * q.ribW() * 0.7, q.legH() * 0.5, 0.6);
                b.bone(a + knee, sd * q.ribW() * 0.7, q.legH() * 0.5, a, sd * q.ribW() * 0.7, 0, 0.6);
            }
        }
        if (q.tail() > 0) b.bone(ah, 0, v.applyAsDouble(ah), ah + q.tail(), 0, v.applyAsDouble(ah) + q.tailRise(), 0.5);
    }
}
