package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.zip.GZIPInputStream;

/**
 * The shapes of Level 418's test scene, voxelized on demand into cached solid grids.
 *
 * <p>Every shape is defined at unit height around the center (0, 0.5, 0). Mesh shapes (teapot,
 * Suzanne, the Stanford models) are meshes built by scripts/testscene/build_meshes.py; primitives
 * are analytic inside-tests. A shape is placed at a size (blocks per unit) and an orientation:
 * orientations below {@link #UPRIGHT} stand upright and turn about Y, the rest are fixed random
 * rotations in 3D, the same in every world. The rotated shape rests with its lowest block on y=0.
 *
 * <p>Meshes are voxelized by casting rays through block centers along all three axes; a block is
 * solid when at least two of the three agree it is inside. That keeps small holes in the scanned
 * meshes from leaking into pillars, which a single vertical ray (as in {@link VoxelMesh}) would.
 *
 * <p>Shell grids keep only the closed surface, one block thick: a hollow teapot still has its bottom.
 *
 * <p>Grids are keyed by (shape, size, orientation, shell) and kept in a small LRU bounded by total
 * size. Immutable after {@link #load}; every public method is safe to call from any thread.
 */
public final class ShapeLibrary {

    /** Every shape the test scene can place. Mesh shapes name a downloaded mesh (meshes.json). */
    public enum Shape {
        TEAPOT("teapot"), SUZANNE("suzanne"), BUNNY("bunny"), DRAGON("dragon"), LUCY("lucy"),
        CUBE(null), PYRAMID(null), CONE(null), SPHERE(null), TORUS(null),
        /** Cornell box contents: half as wide as it is tall. Not in the random pools. */
        TALL_BOX(null);

        final String mesh;

        Shape(String mesh) {
            this.mesh = mesh;
        }
    }

    /** Orientations [0, UPRIGHT) stand upright, turned by o / UPRIGHT of a full turn about Y. */
    public static final int UPRIGHT = 16;
    /** Orientations [UPRIGHT, ORIENTATIONS) are fixed random rotations in 3D. */
    public static final int ORIENTATIONS = 64;

    private static final long CACHE_BITS = 32L * 8 * 1024 * 1024;   // ~32 MB of grids
    private static final double[][] ROTATIONS = rotations();
    private static final int PRIMITIVE_SAMPLES = 24;                  // per axis, for primitive bounds

    /** A unit-height mesh: base at y=0, centered in XZ. */
    private record Mesh(double[] v, int[] tris) {}

    /**
     * A voxelized shape. Block (x, y, z) of the grid sits at (pivot + ox + x, base + y, pivot + oz + z)
     * in the world, where pivot is the shape's XZ center and base its lowest block.
     */
    public static final class VoxelGrid {
        public final int sx, sy, sz, ox, oz;
        private final long[] bits;

        VoxelGrid(int sx, int sy, int sz, int ox, int oz) {
            this.sx = sx; this.sy = sy; this.sz = sz; this.ox = ox; this.oz = oz;
            this.bits = new long[(int) (((long) sx * sy * sz + 63) >>> 6)];
        }

        int index(int x, int y, int z) {
            return (x * sz + z) * sy + y;
        }

        public boolean get(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return false;
            int i = index(x, y, z);
            return (bits[i >>> 6] & (1L << i)) != 0;
        }

        void set(int x, int y, int z) {
            int i = index(x, y, z);
            bits[i >>> 6] |= 1L << i;
        }

        void clear(int x, int y, int z) {
            int i = index(x, y, z);
            bits[i >>> 6] &= ~(1L << i);
        }

        VoxelGrid copy() {
            VoxelGrid c = new VoxelGrid(sx, sy, sz, ox, oz);
            System.arraycopy(bits, 0, c.bits, 0, bits.length);
            return c;
        }

        long size() {
            return (long) bits.length * 64;
        }
    }

    private record Key(Shape shape, int size, int orient, boolean shell) {}

    private final Map<Shape, Mesh> meshes;
    /** Per shape and orientation, at unit size: [0] horizontal radius around the pivot, [1] height. */
    private final Map<Shape, double[][]> bounds;
    private final Map<Key, VoxelGrid> cache = new LinkedHashMap<>(64, 0.75f, true);
    private long cachedBits;

    private ShapeLibrary(Map<Shape, Mesh> meshes) {
        this.meshes = meshes;
        this.bounds = new EnumMap<>(Shape.class);
        for (Shape s : Shape.values()) {
            double[] pts = s.mesh != null ? meshes.get(s).v : primitivePoints(s);
            double[][] b = new double[ORIENTATIONS][];
            for (int o = 0; o < ORIENTATIONS; o++) b[o] = measure(pts, ROTATIONS[o]);
            bounds.put(s, b);
        }
    }

    /** Load every mesh shape from the downloaded mesh files (MeshStore, names as in Shape). */
    public static ShapeLibrary load() {
        Map<Shape, Mesh> meshes = new EnumMap<>(Shape.class);
        for (Shape s : Shape.values()) {
            if (s.mesh == null) continue;
            java.nio.file.Path file = name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.mesh.MeshStore.file(s.mesh);
            try (InputStream in = file == null ? null : java.nio.file.Files.newInputStream(file)) {
                if (in == null) throw new IOException("mesh '" + s.mesh + "' isn't downloaded");
                meshes.put(s, readMesh(in));
            } catch (IOException e) {
                throw new UncheckedIOException("Level 418: failed to load " + s.mesh, e);
            }
        }
        return new ShapeLibrary(meshes);
    }

    private static Mesh readMesh(InputStream in) throws IOException {
        try (DataInputStream d = new DataInputStream(new GZIPInputStream(in))) {
            byte[] magic = new byte[4];
            d.readFully(magic);
            if (!"VXM1".equals(new String(magic, StandardCharsets.US_ASCII))) {
                throw new IOException("not a VXM1 mesh");
            }
            double[] lo = {VoxelMesh.f32(d), VoxelMesh.f32(d), VoxelMesh.f32(d)};
            double[] hi = {VoxelMesh.f32(d), VoxelMesh.f32(d), VoxelMesh.f32(d)};
            int nv = VoxelMesh.varint(d);
            double[] v = new double[nv * 3];
            long[] q = new long[3];
            for (int i = 0; i < nv; i++) {
                for (int c = 0; c < 3; c++) {
                    q[c] += VoxelMesh.zigzag(VoxelMesh.varint(d));
                    v[i * 3 + c] = lo[c] + (q[c] / 65535.0) * (hi[c] - lo[c]);
                }
            }
            int nt = VoxelMesh.varint(d);
            int[] tris = new int[nt * 3];
            int a = 0;
            for (int i = 0; i < nt; i++) {
                a += VoxelMesh.zigzag(VoxelMesh.varint(d));
                tris[i * 3] = a;
                tris[i * 3 + 1] = a + VoxelMesh.zigzag(VoxelMesh.varint(d));
                tris[i * 3 + 2] = a + VoxelMesh.zigzag(VoxelMesh.varint(d));
            }
            return new Mesh(v, tris);
        }
    }

    /** Horizontal radius around the pivot, per block of size, in this orientation. */
    public double footprint(Shape shape, int orient) {
        return bounds.get(shape)[orient][0];
    }

    /** Height per block of size, in this orientation. */
    public double height(Shape shape, int orient) {
        return bounds.get(shape)[orient][1];
    }

    /** The shape at the given size (blocks per unit) and orientation. */
    public VoxelGrid grid(Shape shape, int size, int orient, boolean shell) {
        Key key = new Key(shape, size, Math.floorMod(orient, ORIENTATIONS), shell);
        synchronized (cache) {
            VoxelGrid g = cache.get(key);
            if (g != null) return g;
        }
        // Built outside the lock; two threads may build the same grid once, which is harmless.
        VoxelGrid g = shape.mesh != null ? voxelizeMesh(meshes.get(shape), key) : voxelizePrimitive(key);
        synchronized (cache) {
            if (cache.putIfAbsent(key, g) == null) {
                cachedBits += g.size();
                var it = cache.entrySet().iterator();
                while (cachedBits > CACHE_BITS && it.hasNext()) {
                    var e = it.next();
                    if (e.getKey().equals(key)) continue;
                    cachedBits -= e.getValue().size();
                    it.remove();
                }
            }
        }
        return g;
    }

    // ── orientations ─────────────────────────────────────────────────────────────────────────────

    /** Row-major 3×3 rotations: upright yaws, then uniform random rotations from a fixed seed. */
    private static double[][] rotations() {
        double[][] out = new double[ORIENTATIONS][];
        for (int o = 0; o < UPRIGHT; o++) {
            double t = 2 * Math.PI * o / UPRIGHT, c = Math.cos(t), s = Math.sin(t);
            out[o] = new double[]{c, 0, -s, 0, 1, 0, s, 0, c};
        }
        SplittableRandom r = new SplittableRandom(0x418);
        for (int o = UPRIGHT; o < ORIENTATIONS; o++) {
            // Shoemake's uniform random unit quaternion
            double u1 = r.nextDouble(), u2 = 2 * Math.PI * r.nextDouble(), u3 = 2 * Math.PI * r.nextDouble();
            double a = Math.sqrt(1 - u1), b = Math.sqrt(u1);
            double x = a * Math.sin(u2), y = a * Math.cos(u2), z = b * Math.sin(u3), w = b * Math.cos(u3);
            out[o] = new double[]{
                    1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
                    2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
                    2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)};
        }
        return out;
    }

    /** {horizontal radius, height} of unit-size points (around center (0, 0.5, 0)) after rotation. */
    private static double[] measure(double[] pts, double[] m) {
        double r2 = 0, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = 0; i < pts.length; i += 3) {
            double x = pts[i], y = pts[i + 1] - 0.5, z = pts[i + 2];
            double rx = m[0] * x + m[1] * y + m[2] * z;
            double ry = m[3] * x + m[4] * y + m[5] * z;
            double rz = m[6] * x + m[7] * y + m[8] * z;
            r2 = Math.max(r2, rx * rx + rz * rz);
            lo = Math.min(lo, ry);
            hi = Math.max(hi, ry);
        }
        return new double[]{Math.sqrt(r2), hi - lo};
    }

    // ── meshes ───────────────────────────────────────────────────────────────────────────────────

    private static VoxelGrid voxelizeMesh(Mesh m, Key key) {
        double size = key.size;
        double[] rot = ROTATIONS[key.orient];
        int n = m.v.length / 3;
        double[] w = new double[m.v.length];
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE,
                minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double x = m.v[i * 3] * size, y = (m.v[i * 3 + 1] - 0.5) * size, z = m.v[i * 3 + 2] * size;
            double rx = rot[0] * x + rot[1] * y + rot[2] * z;
            double ry = rot[3] * x + rot[4] * y + rot[5] * z;
            double rz = rot[6] * x + rot[7] * y + rot[8] * z;
            w[i * 3] = rx;
            w[i * 3 + 1] = ry;
            w[i * 3 + 2] = rz;
            minX = Math.min(minX, rx); maxX = Math.max(maxX, rx);
            minY = Math.min(minY, ry); maxY = Math.max(maxY, ry);
            minZ = Math.min(minZ, rz); maxZ = Math.max(maxZ, rz);
        }
        int ox = (int) Math.floor(minX), oz = (int) Math.floor(minZ);
        VoxelGrid g = new VoxelGrid((int) Math.ceil(maxX) - ox + 1, (int) Math.ceil(maxY - minY) + 1,
                (int) Math.ceil(maxZ) - oz + 1, ox, oz);

        // vertices in grid coordinates: block (i, j, k) has its center at (i + 0.5, j + 0.5, k + 0.5)
        double[] p = new double[w.length];
        for (int i = 0; i < n; i++) {
            p[i * 3] = w[i * 3] - ox;
            p[i * 3 + 1] = w[i * 3 + 1] - minY;
            p[i * 3 + 2] = w[i * 3 + 2] - oz;
        }
        byte[] votes = new byte[g.sx * g.sy * g.sz];
        for (int axis = 0; axis < 3; axis++) castAxis(p, m.tris, axis, g, votes);
        for (int x = 0; x < g.sx; x++) {
            for (int z = 0; z < g.sz; z++) {
                for (int y = 0; y < g.sy; y++) {
                    if (votes[g.index(x, y, z)] >= 2) g.set(x, y, z);
                }
            }
        }

        if (key.shell) hollow(g);
        return g;
    }

    /**
     * Cast rays through block centers along one axis and vote for every block inside the mesh.
     * Walking along +axis, a surface facing -axis enters the solid and one facing +axis leaves it.
     * A span that never closes (a hole in the mesh) gets no votes, so a leak along one axis is
     * outvoted by the other two instead of becoming a pillar.
     */
    private static void castAxis(double[] p, int[] tris, int a, VoxelGrid g, byte[] votes) {
        int b = (a + 1) % 3, c = (a + 2) % 3;
        int[] dim = {g.sx, g.sy, g.sz};
        int nb = dim[b], nc = dim[c], na = dim[a];
        @SuppressWarnings("unchecked")
        List<double[]>[] hits = new List[nb * nc];   // per line: {t, leaving ? 1 : 0}
        for (int t3 = 0; t3 < tris.length; t3 += 3) {
            int i0 = tris[t3] * 3, i1 = tris[t3 + 1] * 3, i2 = tris[t3 + 2] * 3;
            double[] u = {p[i1] - p[i0], p[i1 + 1] - p[i0 + 1], p[i1 + 2] - p[i0 + 2]};
            double[] v = {p[i2] - p[i0], p[i2 + 1] - p[i0 + 1], p[i2 + 2] - p[i0 + 2]};
            double[] nrm = {u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
            double na2 = nrm[a] * nrm[a];
            if (na2 <= 1e-8 * (nrm[0] * nrm[0] + nrm[1] * nrm[1] + nrm[2] * nrm[2])) continue;
            double b0 = p[i0 + b], b1 = p[i1 + b], b2 = p[i2 + b];
            double c0 = p[i0 + c], c1 = p[i1 + c], c2 = p[i2 + c];
            double det = (b1 - b0) * (c2 - c0) - (b2 - b0) * (c1 - c0);
            int lo1 = Math.max(0, (int) Math.floor(Math.min(b0, Math.min(b1, b2)) - 0.5));
            int hi1 = Math.min(nb - 1, (int) Math.ceil(Math.max(b0, Math.max(b1, b2)) - 0.5));
            int lo2 = Math.max(0, (int) Math.floor(Math.min(c0, Math.min(c1, c2)) - 0.5));
            int hi2 = Math.min(nc - 1, (int) Math.ceil(Math.max(c0, Math.max(c1, c2)) - 0.5));
            for (int i = lo1; i <= hi1; i++) {
                for (int j = lo2; j <= hi2; j++) {
                    double pb = i + 0.5, pc = j + 0.5;
                    double w1 = ((pb - b0) * (c2 - c0) - (b2 - b0) * (pc - c0)) / det;
                    double w2 = ((b1 - b0) * (pc - c0) - (pb - b0) * (c1 - c0)) / det;
                    double w0 = 1 - w1 - w2;
                    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                    int line = i * nc + j;
                    if (hits[line] == null) hits[line] = new ArrayList<>(4);
                    hits[line].add(new double[]{w0 * p[i0 + a] + w1 * p[i1 + a] + w2 * p[i2 + a], nrm[a] > 0 ? 1 : 0});
                }
            }
        }

        int[] pos = new int[3];
        for (int i = 0; i < nb; i++) {
            for (int j = 0; j < nc; j++) {
                List<double[]> list = hits[i * nc + j];
                if (list == null) continue;
                list.sort((x, y) -> Double.compare(x[0], y[0]));
                double open = Double.NaN;
                pos[b] = i;
                pos[c] = j;
                for (double[] hit : list) {
                    if (hit[1] == 0) {
                        if (Double.isNaN(open)) open = hit[0];
                    } else if (!Double.isNaN(open)) {
                        int k0 = Math.max(0, (int) Math.ceil(open - 0.5)), k1 = Math.min(na - 1, (int) Math.floor(hit[0] - 0.5));
                        for (int k = k0; k <= k1; k++) {
                            pos[a] = k;
                            votes[g.index(pos[0], pos[1], pos[2])]++;
                        }
                        open = Double.NaN;
                    }
                }
            }
        }
    }

    // ── primitives ───────────────────────────────────────────────────────────────────────────────

    private VoxelGrid voxelizePrimitive(Key key) {
        double size = key.size;
        double[] rot = ROTATIONS[key.orient];
        int r = (int) Math.ceil(footprint(key.shape, key.orient) * size) + 1;
        int h = (int) Math.ceil(height(key.shape, key.orient) * size) + 2;
        // evaluate a box around the center, then drop the empty layers at the bottom
        VoxelGrid tmp = new VoxelGrid(2 * r + 1, h + 1, 2 * r + 1, -r, -r);
        int half = h / 2;
        int lowest = Integer.MAX_VALUE, highest = -1;
        for (int x = 0; x < tmp.sx; x++) {
            for (int z = 0; z < tmp.sz; z++) {
                for (int y = 0; y < tmp.sy; y++) {
                    double px = x - r + 0.5, py = y - half + 0.5, pz = z - r + 0.5;
                    double lx = (rot[0] * px + rot[3] * py + rot[6] * pz) / size;
                    double ly = (rot[1] * px + rot[4] * py + rot[7] * pz) / size + 0.5;
                    double lz = (rot[2] * px + rot[5] * py + rot[8] * pz) / size;
                    if (inside(key.shape, lx, ly, lz)) {
                        tmp.set(x, y, z);
                        lowest = Math.min(lowest, y);
                        highest = Math.max(highest, y);
                    }
                }
            }
        }
        if (highest < 0) return new VoxelGrid(1, 1, 1, 0, 0);
        VoxelGrid g = new VoxelGrid(tmp.sx, highest - lowest + 1, tmp.sz, tmp.ox, tmp.oz);
        for (int x = 0; x < g.sx; x++) {
            for (int z = 0; z < g.sz; z++) {
                for (int y = 0; y < g.sy; y++) {
                    if (tmp.get(x, y + lowest, z)) g.set(x, y, z);
                }
            }
        }
        if (key.shell) hollow(g);
        return g;
    }

    /** Unit primitives: base on y=0, height 1, centered on the y axis. */
    private static boolean inside(Shape s, double x, double y, double z) {
        if (y < 0 || y > 1) return false;
        double ax = Math.abs(x), az = Math.abs(z);
        return switch (s) {
            case CUBE -> ax <= 0.5 && az <= 0.5;
            case TALL_BOX -> ax <= 0.25 && az <= 0.25;
            case PYRAMID -> Math.max(ax, az) <= 0.5 * (1 - y);
            case CONE -> Math.hypot(x, z) <= 0.5 * (1 - y);
            case SPHERE -> x * x + (y - 0.5) * (y - 0.5) + z * z <= 0.25;
            case TORUS -> {   // standing on its rim like an arch, axis along x
                double ring = Math.hypot(y - 0.5, z) - 0.35;
                yield ring * ring + x * x <= 0.15 * 0.15;
            }
            default -> false;
        };
    }

    /** Points inside a unit primitive, for measuring its bounds per orientation. */
    private static double[] primitivePoints(Shape s) {
        int n = PRIMITIVE_SAMPLES;
        double[] pts = new double[(n + 1) * (n + 1) * (n + 1) * 3];
        int k = 0;
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                for (int l = 0; l <= n; l++) {
                    double x = i / (double) n - 0.5, y = j / (double) n, z = l / (double) n - 0.5;
                    if (!inside(s, x, y, z)) continue;
                    pts[k++] = x;
                    pts[k++] = y;
                    pts[k++] = z;
                }
            }
        }
        return Arrays.copyOf(pts, k);
    }

    // ── shells ───────────────────────────────────────────────────────────────────────────────────

    /** Keep only blocks with an empty face neighbor. */
    private static void hollow(VoxelGrid g) {
        VoxelGrid src = g.copy();
        for (int x = 0; x < g.sx; x++) {
            for (int z = 0; z < g.sz; z++) {
                for (int y = 0; y < g.sy; y++) {
                    if (!src.get(x, y, z)) continue;
                    boolean inner = src.get(x - 1, y, z) && src.get(x + 1, y, z)
                            && src.get(x, y, z - 1) && src.get(x, y, z + 1)
                            && src.get(x, y - 1, z) && src.get(x, y + 1, z);
                    if (inner) g.clear(x, y, z);
                }
            }
        }
    }
}
