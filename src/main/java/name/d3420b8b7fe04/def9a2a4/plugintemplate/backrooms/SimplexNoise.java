package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms;

/**
 * OpenSimplex2S noise (smooth variant). Public domain.
 * Adapted from KdotJPG's OpenSimplex2 reference implementation.
 */
public final class SimplexNoise {

    private static final long PRIME_X = 0x5205402B9270C86FL;
    private static final long PRIME_Y = 0x598CD327003817B5L;
    private static final long HASH_SEED = 0x53A3F72DEEC546F5L;
    private static final int N_GRADS_2D = 128;
    private static final double SKEW_2D = 0.366025403784439;   // (sqrt(3) - 1) / 2
    private static final double UNSKEW_2D = -0.21132486540518713; // (1/sqrt(3) - 1) / 2

    private static final double ROOT2OVER2 = 0.7071067811865476;
    private static final double[] GRADIENTS_2D;

    static {
        GRADIENTS_2D = new double[N_GRADS_2D * 2];
        double[] grad2 = {
             0.38268343236509,   0.923879532511287,
             0.923879532511287,  0.38268343236509,
             0.923879532511287, -0.38268343236509,
             0.38268343236509,  -0.923879532511287,
            -0.38268343236509,  -0.923879532511287,
            -0.923879532511287, -0.38268343236509,
            -0.923879532511287,  0.38268343236509,
            -0.38268343236509,   0.923879532511287,
             ROOT2OVER2,         ROOT2OVER2,
             ROOT2OVER2,        -ROOT2OVER2,
            -ROOT2OVER2,        -ROOT2OVER2,
            -ROOT2OVER2,         ROOT2OVER2,
             1, 0, 0, 1, -1, 0, 0, -1
        };
        for (int i = 0; i < GRADIENTS_2D.length; i++) {
            GRADIENTS_2D[i] = grad2[i % grad2.length] / 0.01001634121365712;
        }
    }

    private SimplexNoise() {}

    public static double noise2(long seed, double x, double y) {
        double s = SKEW_2D * (x + y);
        double xs = x + s, ys = y + s;

        double value = 0;

        int xsb = fastFloor(xs), ysb = fastFloor(ys);
        double xsi = xs - xsb, ysi = ys - ysb;

        double a = 0.5 - xsi - ysi;
        if (a > 0) {
            double aa = a * a;
            value = aa * aa * grad(seed, xsb, ysb, xs, ys);
        }

        double c = (2.0 * (1.0 - 2.0 * SKEW_2D) * (1.0 / SKEW_2D + 2.0)) * (xsi + ysi) + ((-2.0 * (1.0 - 2.0 * SKEW_2D) * (1.0 - 2.0 * SKEW_2D)) + a);
        if (c > 0) {
            double xsi1 = xsi - (1 - 2 * SKEW_2D), ysi1 = ysi - (1 - 2 * SKEW_2D);
            double cc = c * c;
            value += cc * cc * grad(seed, xsb + 1, ysb + 1, xs - xsi1, ys - ysi1);
        }

        if (ysi > xsi) {
            double b = a + ysi - 0.5;
            if (b > 0) {
                double bb = b * b;
                value += bb * bb * grad(seed, xsb, ysb + 1, xs, ys - (ysi - UNSKEW_2D * 2 - 1));
            }
        } else {
            double b = a + xsi - 0.5;
            if (b > 0) {
                double bb = b * b;
                value += bb * bb * grad(seed, xsb + 1, ysb, xs - (xsi - UNSKEW_2D * 2 - 1), ys);
            }
        }

        return value;
    }

    private static double grad(long seed, int xsvp, int ysvp, double dx, double dy) {
        long hash = seed ^ (xsvp * PRIME_X) ^ (ysvp * PRIME_Y);
        hash *= HASH_SEED;
        hash ^= hash >> 25;
        int gi = (int) hash & ((N_GRADS_2D - 1) << 1);
        return GRADIENTS_2D[gi] * dx + GRADIENTS_2D[gi | 1] * dy;
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}
