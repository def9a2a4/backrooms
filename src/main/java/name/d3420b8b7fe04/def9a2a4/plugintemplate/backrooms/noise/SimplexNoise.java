package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise;

/**
 * OpenSimplex2S noise (smooth variant). Public domain.
 * Adapted from KdotJPG's OpenSimplex2 reference implementation.
 */
public final class SimplexNoise {

    private static final long PRIME_X = 0x5205402B9270C86FL;
    private static final long PRIME_Y = 0x598CD327003817B5L;
    private static final long PRIME_Z = 0x5BCC226E9FA0BACBL;
    private static final long HASH_SEED = 0x53A3F72DEEC546F5L;
    private static final int N_GRADS_2D = 128;
    private static final int N_GRADS_3D = 16;
    private static final double SKEW_2D = 0.366025403784439;   // (sqrt(3) - 1) / 2
    private static final double UNSKEW_2D = -0.21132486540518713; // (1/sqrt(3) - 1) / 2
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;

    private static final double ROOT2OVER2 = 0.7071067811865476;
    private static final double[] GRADIENTS_2D;
    private static final double[] GRADIENTS_3D;

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

        GRADIENTS_3D = new double[N_GRADS_3D * 3];
        double[] grad3 = {
             1, 1, 0,  -1, 1, 0,   1,-1, 0,  -1,-1, 0,
             1, 0, 1,  -1, 0, 1,   1, 0,-1,  -1, 0,-1,
             0, 1, 1,   0,-1, 1,   0, 1,-1,   0,-1,-1,
             1, 1, 0,  -1, 1, 0,   0,-1, 1,   0, 1,-1
        };
        for (int i = 0; i < GRADIENTS_3D.length; i++) {
            GRADIENTS_3D[i] = grad3[i % grad3.length];
        }
    }

    private SimplexNoise() {}

    public static double noise2(long seed, double x, double y) {
        double s = SKEW_2D * (x + y);
        double xs = x + s, ys = y + s;

        double value = 0;

        int xsb = fastFloor(xs), ysb = fastFloor(ys);
        double xsi = xs - xsb, ysi = ys - ysb;

        // Compute proper unskewed-space displacements for each lattice vertex
        // Vertex (xsb, ysb)
        double t0 = UNSKEW_2D * (xsb + ysb);
        double dx0 = x - xsb - t0;
        double dy0 = y - ysb - t0;

        double a = 0.5 - xsi - ysi;
        if (a > 0) {
            double aa = a * a;
            value = aa * aa * grad(seed, xsb, ysb, dx0, dy0);
        }

        double c = (2.0 * (1.0 - 2.0 * SKEW_2D) * (1.0 / SKEW_2D + 2.0)) * (xsi + ysi) + ((-2.0 * (1.0 - 2.0 * SKEW_2D) * (1.0 - 2.0 * SKEW_2D)) + a);
        if (c > 0) {
            // Vertex (xsb+1, ysb+1)
            double t1 = UNSKEW_2D * (xsb + 1 + ysb + 1);
            double dx1 = x - (xsb + 1) - t1;
            double dy1 = y - (ysb + 1) - t1;
            double cc = c * c;
            value += cc * cc * grad(seed, xsb + 1, ysb + 1, dx1, dy1);
        }

        if (ysi > xsi) {
            double b = a + ysi - 0.5;
            if (b > 0) {
                // Vertex (xsb, ysb+1)
                double t2 = UNSKEW_2D * (xsb + ysb + 1);
                double dx2 = x - xsb - t2;
                double dy2 = y - (ysb + 1) - t2;
                double bb = b * b;
                value += bb * bb * grad(seed, xsb, ysb + 1, dx2, dy2);
            }
        } else {
            double b = a + xsi - 0.5;
            if (b > 0) {
                // Vertex (xsb+1, ysb)
                double t2 = UNSKEW_2D * (xsb + 1 + ysb);
                double dx2 = x - (xsb + 1) - t2;
                double dy2 = y - ysb - t2;
                double bb = b * b;
                value += bb * bb * grad(seed, xsb + 1, ysb, dx2, dy2);
            }
        }

        return value;
    }

    public static double noise3(long seed, double x, double y, double z) {
        double s = F3 * (x + y + z);
        int i = fastFloor(x + s), j = fastFloor(y + s), k = fastFloor(z + s);

        double t = (i + j + k) * G3;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        double z0 = z - (k - t);

        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0)      { i1=1;j1=0;k1=0; i2=1;j2=1;k2=0; }
            else if (x0 >= z0) { i1=1;j1=0;k1=0; i2=1;j2=0;k2=1; }
            else               { i1=0;j1=0;k1=1; i2=1;j2=0;k2=1; }
        } else {
            if (y0 < z0)       { i1=0;j1=0;k1=1; i2=0;j2=1;k2=1; }
            else if (x0 < z0)  { i1=0;j1=1;k1=0; i2=0;j2=1;k2=1; }
            else               { i1=0;j1=1;k1=0; i2=1;j2=1;k2=0; }
        }

        double x1 = x0 - i1 + G3;
        double y1 = y0 - j1 + G3;
        double z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3;
        double y2 = y0 - j2 + 2.0 * G3;
        double z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3;
        double y3 = y0 - 1.0 + 3.0 * G3;
        double z3 = z0 - 1.0 + 3.0 * G3;

        double value = 0;

        double t0 = 0.6 - x0*x0 - y0*y0 - z0*z0;
        if (t0 > 0) { t0 *= t0; value += t0 * t0 * grad3(seed, i, j, k, x0, y0, z0); }

        double t1 = 0.6 - x1*x1 - y1*y1 - z1*z1;
        if (t1 > 0) { t1 *= t1; value += t1 * t1 * grad3(seed, i+i1, j+j1, k+k1, x1, y1, z1); }

        double t2 = 0.6 - x2*x2 - y2*y2 - z2*z2;
        if (t2 > 0) { t2 *= t2; value += t2 * t2 * grad3(seed, i+i2, j+j2, k+k2, x2, y2, z2); }

        double t3 = 0.6 - x3*x3 - y3*y3 - z3*z3;
        if (t3 > 0) { t3 *= t3; value += t3 * t3 * grad3(seed, i+1, j+1, k+1, x3, y3, z3); }

        return 32.0 * value;
    }

    private static double grad3(long seed, int xsvp, int ysvp, int zsvp,
                                 double dx, double dy, double dz) {
        long hash = seed ^ ((long) xsvp * PRIME_X) ^ ((long) ysvp * PRIME_Y) ^ ((long) zsvp * PRIME_Z);
        hash *= HASH_SEED;
        hash ^= hash >> 25;
        int gi = ((int) hash & (N_GRADS_3D - 1)) * 3;
        return GRADIENTS_3D[gi] * dx + GRADIENTS_3D[gi + 1] * dy + GRADIENTS_3D[gi + 2] * dz;
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
