package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * The shapes of Level 418's test scene, voxelized on demand into cached solid grids.
 *
 * <p>Mesh shapes (teapot, Suzanne, the Stanford models) are unit-height meshes built by
 * scripts/testscene/build_meshes.py. They are voxelized by casting a vertical ray through each
 * block column and walking the hits top-down: an up-facing hit starts solid, a down-facing hit
 * ends it, and a span still open at the bottom runs down to the base. This is the same rule as
 * {@link VoxelMesh}, and it copes with meshes that aren't watertight.
 * Primitives are analytic inside-tests per block.
 *
 * <p>A grid is keyed by (shape, height, yaw step, upside-down, shell). Shell grids keep only the
 * surface, and open the bottom layer inside the rim, so a hollow teapot can be walked into from
 * below. Grids are kept in a small LRU bounded by total size.
 *
 * <p>Immutable after {@link #load}; {@link #grid} is safe to call from any thread.
 */
public final class ShapeLibrary {

    /** Every shape the test scene can place. Mesh shapes name a resource under /structures/testscene/. */
    public enum Shape {
        TEAPOT("teapot"), SUZANNE("suzanne"), BUNNY("bunny"), DRAGON("dragon"),
        ARMADILLO("armadillo"), LUCY("lucy"),
        CUBE(null), PYRAMID(null), CONE(null), SPHERE(null), TORUS(null),
        /** Cornell box contents: half as wide as it is tall. Not in the random pools. */
        TALL_BOX(null);

        final String mesh;

        Shape(String mesh) {
            this.mesh = mesh;
        }
    }

    public static final int YAW_STEPS = 16;
    private static final long CACHE_BITS = 32L * 8 * 1024 * 1024;   // ~32 MB of grids

    /** A unit-height mesh: base at y=0, centered in XZ. */
    private record Mesh(double[] v, int[] tris) {}

    /**
     * A voxelized shape. Block (x, y, z) of the grid sits at (pivot + ox + x, base + y, pivot + oz + z)
     * in the world, where pivot is the shape's XZ center.
     */
    public static final class VoxelGrid {
        public final int sx, sy, sz, ox, oz;
        private final long[] bits;

        VoxelGrid(int sx, int sy, int sz, int ox, int oz) {
            this.sx = sx; this.sy = sy; this.sz = sz; this.ox = ox; this.oz = oz;
            this.bits = new long[(int) (((long) sx * sy * sz + 63) >>> 6)];
        }

        private int index(int x, int y, int z) {
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

    private record Key(Shape shape, int height, int yaw, boolean flip, boolean shell) {}

    private final Map<Shape, Mesh> meshes;
    private final Map<Key, VoxelGrid> cache = new LinkedHashMap<>(64, 0.75f, true);
    private long cachedBits;

    private ShapeLibrary(Map<Shape, Mesh> meshes) {
        this.meshes = meshes;
    }

    /** Load every mesh shape from the plugin jar. */
    public static ShapeLibrary load() {
        Map<Shape, Mesh> meshes = new EnumMap<>(Shape.class);
        for (Shape s : Shape.values()) {
            if (s.mesh == null) continue;
            String res = "/structures/testscene/" + s.mesh + ".mesh.gz";
            try (InputStream in = ShapeLibrary.class.getResourceAsStream(res)) {
                if (in == null) throw new IOException("missing resource " + res);
                meshes.put(s, readMesh(in));
            } catch (IOException e) {
                throw new UncheckedIOException("Level 418: failed to load " + res, e);
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

    /**
     * Horizontal radius of the shape at height 1, around its pivot, for any yaw. Layout uses it to
     * keep objects inside their cells.
     */
    public double radius(Shape shape) {
        Mesh m = meshes.get(shape);
        if (m == null) {
            return switch (shape) {
                case CUBE, PYRAMID -> Math.sqrt(0.5);
                case TALL_BOX -> Math.sqrt(0.125);
                default -> 0.5;
            };
        }
        double r2 = 0;
        for (int i = 0; i < m.v.length; i += 3) {
            r2 = Math.max(r2, m.v[i] * m.v[i] + m.v[i + 2] * m.v[i + 2]);
        }
        return Math.sqrt(r2);
    }

    /** The shape at the given height in blocks, turned by yaw / YAW_STEPS of a full turn. */
    public VoxelGrid grid(Shape shape, int height, int yaw, boolean flip, boolean shell) {
        Key key = new Key(shape, height, Math.floorMod(yaw, YAW_STEPS), flip, shell);
        synchronized (cache) {
            VoxelGrid g = cache.get(key);
            if (g != null) return g;
        }
        // Built outside the lock; two threads may build the same grid once, which is harmless.
        VoxelGrid g = shape.mesh != null ? voxelizeMesh(meshes.get(shape), key) : voxelizePrimitive(key);
        if (shell) hollow(g);
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

    // ── meshes ───────────────────────────────────────────────────────────────────────────────────

    private static VoxelGrid voxelizeMesh(Mesh m, Key key) {
        double h = key.height;
        double t = 2 * Math.PI * key.yaw / YAW_STEPS, cos = Math.cos(t), sin = Math.sin(t);
        int n = m.v.length / 3;
        double[] w = new double[m.v.length];
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double x = m.v[i * 3] * h, y = m.v[i * 3 + 1] * h, z = m.v[i * 3 + 2] * h;
            if (key.flip) {   // half a turn about X: a proper rotation, so winding stays outward
                y = h - y;
                z = -z;
            }
            double rx = x * cos - z * sin, rz = x * sin + z * cos;
            w[i * 3] = rx;
            w[i * 3 + 1] = y;
            w[i * 3 + 2] = rz;
            minX = Math.min(minX, rx); maxX = Math.max(maxX, rx);
            minZ = Math.min(minZ, rz); maxZ = Math.max(maxZ, rz);
        }
        int ox = (int) Math.floor(minX), oz = (int) Math.floor(minZ);
        VoxelGrid g = new VoxelGrid((int) Math.ceil(maxX) - ox + 1, key.height + 1,
                (int) Math.ceil(maxZ) - oz + 1, ox, oz);

        // hits per column as {y, up ? 1 : 0}
        @SuppressWarnings("unchecked")
        List<double[]>[] hits = new List[g.sx * g.sz];
        int[] tris = m.tris;
        for (int t3 = 0; t3 < tris.length; t3 += 3) {
            int a = tris[t3], b = tris[t3 + 1], c = tris[t3 + 2];
            double ax = w[a * 3] - ox, ay = w[a * 3 + 1], az = w[a * 3 + 2] - oz;
            double bx = w[b * 3] - ox, by = w[b * 3 + 1], bz = w[b * 3 + 2] - oz;
            double cx = w[c * 3] - ox, cy = w[c * 3 + 1], cz = w[c * 3 + 2] - oz;
            double ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            if (ny * ny <= 1e-8 * (nx * nx + ny * ny + nz * nz)) continue;
            double det = (bx - ax) * (cz - az) - (cx - ax) * (bz - az);
            int x0 = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx)) - 0.5));
            int x1 = Math.min(g.sx - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx)) - 0.5));
            int z0 = Math.max(0, (int) Math.floor(Math.min(az, Math.min(bz, cz)) - 0.5));
            int z1 = Math.min(g.sz - 1, (int) Math.ceil(Math.max(az, Math.max(bz, cz)) - 0.5));
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    double px = x + 0.5, pz = z + 0.5;
                    double w1 = ((px - ax) * (cz - az) - (cx - ax) * (pz - az)) / det;
                    double w2 = ((bx - ax) * (pz - az) - (px - ax) * (bz - az)) / det;
                    double w0 = 1 - w1 - w2;
                    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                    int idx = x * g.sz + z;
                    if (hits[idx] == null) hits[idx] = new ArrayList<>(4);
                    hits[idx].add(new double[]{w0 * ay + w1 * by + w2 * cy, ny > 0 ? 1 : 0});
                }
            }
        }

        for (int x = 0; x < g.sx; x++) {
            for (int z = 0; z < g.sz; z++) {
                List<double[]> list = hits[x * g.sz + z];
                if (list == null) continue;
                list.sort((p, q) -> Double.compare(q[0], p[0]));
                double top = Double.NaN;
                for (double[] hit : list) {
                    if (hit[1] == 1) {
                        if (Double.isNaN(top)) top = hit[0];
                    } else if (!Double.isNaN(top)) {
                        fill(g, x, z, (int) Math.floor(top - 0.5), (int) Math.ceil(hit[0] - 0.5));
                        top = Double.NaN;
                    }
                }
                if (!Double.isNaN(top)) fill(g, x, z, (int) Math.floor(top - 0.5), 0);
            }
        }
        return g;
    }

    private static void fill(VoxelGrid g, int x, int z, int top, int bottom) {
        for (int y = Math.max(0, bottom); y <= Math.min(g.sy - 1, top); y++) g.set(x, y, z);
    }

    // ── primitives ───────────────────────────────────────────────────────────────────────────────

    private VoxelGrid voxelizePrimitive(Key key) {
        int h = key.height;
        int r = (int) Math.ceil(radius(key.shape) * h) + 1;
        VoxelGrid g = new VoxelGrid(2 * r + 1, h, 2 * r + 1, -r, -r);
        double t = 2 * Math.PI * key.yaw / YAW_STEPS, cos = Math.cos(t), sin = Math.sin(t);
        for (int x = 0; x < g.sx; x++) {
            for (int z = 0; z < g.sz; z++) {
                double wx = x - r + 0.5, wz = z - r + 0.5;
                // inverse yaw: world → shape-local, in units of the height
                double lx = (wx * cos + wz * sin) / h, lz = (-wx * sin + wz * cos) / h;
                if (key.flip) lz = -lz;   // half a turn about X, as for meshes
                for (int y = 0; y < h; y++) {
                    double ly = (y + 0.5) / h;
                    if (inside(key.shape, lx, key.flip ? 1 - ly : ly, lz)) g.set(x, y, z);
                }
            }
        }
        return g;
    }

    /** Unit primitives: base on y=0, height 1, centered on the y axis. */
    private static boolean inside(Shape s, double x, double y, double z) {
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

    // ── shells ───────────────────────────────────────────────────────────────────────────────────

    /** Keep only blocks with an empty face neighbor; open the bottom layer inside the rim. */
    private static void hollow(VoxelGrid g) {
        VoxelGrid src = g.copy();
        for (int x = 0; x < g.sx; x++) {
            for (int z = 0; z < g.sz; z++) {
                for (int y = 0; y < g.sy; y++) {
                    if (!src.get(x, y, z)) continue;
                    boolean side = src.get(x - 1, y, z) && src.get(x + 1, y, z)
                            && src.get(x, y, z - 1) && src.get(x, y, z + 1);
                    // bottom layer: open wherever all four sides are solid (inside the rim)
                    if (y == 0 ? side : side && src.get(x, y - 1, z) && src.get(x, y + 1, z)) g.clear(x, y, z);
                }
            }
        }
    }
}
