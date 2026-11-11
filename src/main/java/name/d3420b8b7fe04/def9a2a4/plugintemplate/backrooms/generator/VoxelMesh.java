package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * A triangle mesh voxelized on the fly, one chunk at a time. Used by Level 56 to build a giant
 * Stonehenge from a ~50 KB mesh, and by {@link ScanChunkGenerator} for the photogrammetry scans.
 *
 * <p>The mesh files come from scripts/stonehenge/build_mesh.py and scripts/scans/build_scan.py.
 * The browser preview's scripts/voxel-preview/voxelize.js is the reference implementation of
 * this class: same decode, same column-ray and shell rules, same hash and noise, so the preview
 * shows what the server generates. Change one, change the other.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>column</b> (terrain): per block column, cast a vertical ray at a noise-warped sample
 *   point, collect every triangle hit, then walk the hits top-down. An up-facing hit starts solid,
 *   a down-facing hit ends it. Columns outside the island circle are void; below the lowest
 *   surface the island's underside is a ragged lens.</li>
 *   <li><b>shell</b> (interiors): scans of rooms are single-sided sheets with unreliable winding, so
 *   every block whose center lies within the shell thickness (± noise) of a triangle is solid. The
 *   first triangle (in file order) to claim a block decides its material.</li>
 * </ul>
 * Materials come from the optional surface grid (a 3D texture: one palette index per small cube
 * the original surface passed through), else from per-vertex palette indices, mixed with
 * per-material "variants".
 *
 * <p>Immutable after {@link #load}; {@link #generateChunk} is safe to call from any thread.
 */
public final class VoxelMesh {

    // ── tuning (mirror voxelize.js) ──────────────────────────────────────────────────────────────
    private static final int DIRT_DEPTH = 3;
    private static final int CELL = 8;
    private static final int PICK_MARGIN = 4;
    private static final double WARP_AMP = 2.5;
    private static final double WARP_SCALE = 20;
    private static final double BUMP_GROUND = 1.2;
    private static final double BUMP_STONE = 2.0;
    private static final double BUMP_SCALE = 14;
    private static final double MAT_WARP = 3.0;
    private static final double MAT_WARP_SCALE = 9;
    private static final int MAT_LATTICE = 2;
    private static final double CLUMP_SCALE = 5;
    private static final double ISLAND_DEPTH = 6.25;
    private static final double ISLAND_EDGE = 4;
    private static final double ISLAND_RAGGED = 0.18;
    private static final double GRID_JITTER = 0.25;
    private static final int GRID_REACH = 2;
    private static final int INTERIOR_DEPTH = 6;   // below this, blocks far from any surface copy the one above
    private static final double SHELL_BUMP = 0.6;
    private static final double SHELL_BUMP_SCALE = 6;
    private static final int FILL = 2;

    private static final int SALT_WARP_X = 11, SALT_WARP_Z = 13, SALT_BUMP = 17, SALT_MAT = 23,
            SALT_CLUMP = 31, SALT_UNDER = 41, SALT_SHELL = 53;

    /**
     * How to place and voxelize a mesh.
     *
     * @param scale       blocks per mesh meter
     * @param groundY     world Y of the mesh's ground reference (column) or lowest point (shell)
     * @param originX     world X of the mesh origin
     * @param originZ     world Z of the mesh origin
     * @param shell       shell mode instead of column mode
     * @param thickness   shell half-thickness in blocks
     * @param islandDepth column mode: island underside depth at the center, blocks per unit scale
     * @param useGrid     use the surface grid when the file has one
     */
    public record Options(double scale, int groundY, int originX, int originZ, boolean shell,
                          double thickness, double islandDepth, boolean useGrid) {
        public static Options column(double scale, int groundY, int originX, int originZ) {
            return new Options(scale, groundY, originX, originZ, false, 1.5, ISLAND_DEPTH, true);
        }
    }

    /**
     * TEX1 surface grid: sorted linear cell indices ((x * dimZ + z) * dimY + y) and materials, plus
     * a coarse occupancy bitset (one bit per BRICK³ cells) so lookups far from any surface (the
     * insides of a column-mode island) fail without probing the neighborhood cell by cell.
     */
    private record Grid(double cell, double[] origin, int[] dims, long[] keys, byte[] mats,
                        java.util.BitSet bricks, int[] bdims) {
        static final int BRICK = 4;

        static Grid of(double cell, double[] origin, int[] dims, long[] keys, byte[] mats) {
            int[] bd = {dims[0] / BRICK + 1, dims[1] / BRICK + 1, dims[2] / BRICK + 1};
            java.util.BitSet bits = new java.util.BitSet();
            for (long k : keys) {
                int y = (int) (k % dims[1]), z = (int) ((k / dims[1]) % dims[2]), x = (int) (k / ((long) dims[1] * dims[2]));
                bits.set(((x / BRICK) * bd[2] + z / BRICK) * bd[1] + y / BRICK);
            }
            return new Grid(cell, origin, dims, keys, mats, bits, bd);
        }

        /** {@link #near} for a mesh-space point in meters. */
        boolean nearPoint(double x, double y, double z) {
            return near((int) Math.floor((x - origin[0]) / cell), (int) Math.floor((y - origin[1]) / cell),
                    (int) Math.floor((z - origin[2]) / cell));
        }

        /** Whether any cell within reach of (cx, cy, cz) can be filled. */
        boolean near(int cx, int cy, int cz) {
            int x0 = Math.max(0, Math.floorDiv(cx - GRID_REACH, BRICK)), x1 = Math.min(bdims[0] - 1, Math.floorDiv(cx + GRID_REACH, BRICK));
            int y0 = Math.max(0, Math.floorDiv(cy - GRID_REACH, BRICK)), y1 = Math.min(bdims[1] - 1, Math.floorDiv(cy + GRID_REACH, BRICK));
            int z0 = Math.max(0, Math.floorDiv(cz - GRID_REACH, BRICK)), z1 = Math.min(bdims[2] - 1, Math.floorDiv(cz + GRID_REACH, BRICK));
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        if (bricks.get((x * bdims[2] + z) * bdims[1] + y)) return true;
                    }
                }
            }
            return false;
        }

        int at(int ix, int iy, int iz) {
            if (ix < 0 || iy < 0 || iz < 0 || ix >= dims[0] || iy >= dims[1] || iz >= dims[2]) return -1;
            int i = java.util.Arrays.binarySearch(keys, ((long) ix * dims[2] + iz) * dims[1] + iy);
            return i >= 0 ? mats[i] & 0xff : -1;
        }

        /** Material at mesh-space point (meters), else the nearest filled cell within reach; -1 if none. */
        int material(double x, double y, double z) {
            double fx = (x - origin[0]) / cell, fy = (y - origin[1]) / cell, fz = (z - origin[2]) / cell;
            int cx = (int) Math.floor(fx), cy = (int) Math.floor(fy), cz = (int) Math.floor(fz);
            if (!near(cx, cy, cz)) return -1;
            int hit = at(cx, cy, cz);
            if (hit >= 0) return hit;
            int best = -1;
            double bd = Double.POSITIVE_INFINITY;
            for (int r = 1; r <= GRID_REACH; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) continue;
                            int m = at(cx + dx, cy + dy, cz + dz);
                            if (m < 0) continue;
                            double ex = cx + dx + 0.5 - fx, ey = cy + dy + 0.5 - fy, ez = cz + dz + 0.5 - fz;
                            double d = ex * ex + ey * ey + ez * ez;
                            if (d < bd) {
                                bd = d;
                                best = m;
                            }
                        }
                    }
                }
                if (best >= 0) return best;
            }
            return -1;
        }
    }

    /** A palette entry: block id (no namespace), family (0 ground, 1 stone) and weighted variants. */
    public record PaletteEntry(String id, int family, int[] variants, int[] cumulative, int total) {}

    /** Receives one block; x/z are chunk-local (0-15), value is a palette index. */
    @FunctionalInterface
    public interface BlockSink {
        void set(int x, int y, int z, int paletteIndex);
    }

    private final PaletteEntry[] palette;
    private final Options opt;
    private final Grid grid;            // null: per-vertex materials only
    private final double meshBaseY;     // mesh-space Y that lands on groundY
    private final byte[] vmat;
    private final double[] w;          // vertices in local block space: x, y (world), z
    private final int[] tris;
    private final int originX, originZ;
    private final int groundY;
    private final double islandX, islandZ, islandR, maxDepth;
    private final int pickFloor;
    private final int dirt, stone;
    private final Map<Long, int[]> stoneGrid;
    private final Map<Long, int[]> chunkTris;   // world chunk key -> triangle indices, ascending

    private VoxelMesh(PaletteEntry[] palette, byte[] vmat, double[] w, int[] tris, Options opt,
                      Grid surface, double meshBaseY, double islandX, double islandZ, double islandR) {
        this.palette = palette;
        this.opt = opt;
        this.grid = opt.useGrid ? surface : null;
        this.meshBaseY = meshBaseY;
        int originX = opt.originX, originZ = opt.originZ;
        double maxDepth = opt.islandDepth * opt.scale;
        this.vmat = vmat;
        this.w = w;
        this.tris = tris;
        this.originX = originX;
        this.originZ = originZ;
        this.groundY = opt.groundY;
        this.islandX = islandX;
        this.islandZ = islandZ;
        this.islandR = islandR;
        this.maxDepth = maxDepth;
        // fill blocks: scan palettes may lack stone or dirt
        int st = indexOf("stone");
        if (st < 0) st = indexOf("tuff");
        if (st < 0) st = indexOf("andesite");
        this.stone = Math.max(st, 0);
        int dt = indexOf("dirt");
        this.dirt = dt >= 0 ? dt : this.stone;

        int nv = vmat.length;
        double minY = Double.POSITIVE_INFINITY;
        for (int i = 0; i < nv; i++) minY = Math.min(minY, w[i * 3 + 1]);
        this.pickFloor = (int) Math.floor(minY) - PICK_MARGIN;

        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < nv; i++) {
            if (family(i) != 1) continue;
            long k = cellKey(floorDiv(w[i * 3], CELL), floorDiv(w[i * 3 + 1], CELL), floorDiv(w[i * 3 + 2], CELL));
            grid.computeIfAbsent(k, key -> new ArrayList<>()).add(i);
        }
        this.stoneGrid = freeze(grid);

        // Triangles per chunk they can touch: column rays sample within WARP_AMP of a column, shell
        // blocks within the shell reach. Two passes over primitive arrays (scans have ~2M tris).
        double margin = (opt.shell ? opt.thickness + SHELL_BUMP : WARP_AMP) + 0.5;
        int nt = tris.length / 3;
        int[] range = new int[nt * 4];
        Map<Long, int[]> counts = new HashMap<>();
        for (int t = 0; t < nt; t++) {
            int a = tris[t * 3], b = tris[t * 3 + 1], c = tris[t * 3 + 2];
            double minX = Math.min(w[a * 3], Math.min(w[b * 3], w[c * 3])) - margin + originX;
            double maxX = Math.max(w[a * 3], Math.max(w[b * 3], w[c * 3])) + margin + originX;
            double minZ = Math.min(w[a * 3 + 2], Math.min(w[b * 3 + 2], w[c * 3 + 2])) - margin + originZ;
            double maxZ = Math.max(w[a * 3 + 2], Math.max(w[b * 3 + 2], w[c * 3 + 2])) + margin + originZ;
            int cx0 = Math.floorDiv((int) Math.floor(minX), 16), cx1 = Math.floorDiv((int) Math.ceil(maxX), 16);
            int cz0 = Math.floorDiv((int) Math.floor(minZ), 16), cz1 = Math.floorDiv((int) Math.ceil(maxZ), 16);
            range[t * 4] = cx0; range[t * 4 + 1] = cx1; range[t * 4 + 2] = cz0; range[t * 4 + 3] = cz1;
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    counts.computeIfAbsent(chunkKey(cx, cz), k -> new int[1])[0]++;
                }
            }
        }
        Map<Long, int[]> buckets = new HashMap<>(counts.size() * 2);
        for (Map.Entry<Long, int[]> e : counts.entrySet()) {
            buckets.put(e.getKey(), new int[e.getValue()[0]]);
            e.getValue()[0] = 0;
        }
        for (int t = 0; t < nt; t++) {
            for (int cx = range[t * 4]; cx <= range[t * 4 + 1]; cx++) {
                for (int cz = range[t * 4 + 2]; cz <= range[t * 4 + 3]; cz++) {
                    long k = chunkKey(cx, cz);
                    buckets.get(k)[counts.get(k)[0]++] = t * 3;   // ascending: first-wins order
                }
            }
        }
        this.chunkTris = buckets;
    }

    // ── loading ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Decode a mesh file (see build_mesh.py write_mesh() for the format).
     *
     * @param scale   blocks per mesh meter
     * @param groundY world Y of the mesh's ground reference
     * @param originX world X of the mesh origin (the monument center)
     * @param originZ world Z of the mesh origin
     */
    public static VoxelMesh load(InputStream in, double scale, int groundY, int originX, int originZ)
            throws IOException {
        return load(in, Options.column(scale, groundY, originX, originZ));
    }

    /** Decode a mesh file and place it per {@code opt}. */
    public static VoxelMesh load(InputStream in, Options opt) throws IOException {
        double scale = opt.scale;
        int groundY = opt.groundY;
        try (DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(in), 1 << 16))) {
            byte[] magic = new byte[4];
            d.readFully(magic);
            if (!"SHM2".equals(new String(magic, StandardCharsets.US_ASCII))) {
                throw new IOException("not a SHM2 mesh");
            }
            double[] lo = {f32(d), f32(d), f32(d)};
            double[] hi = {f32(d), f32(d), f32(d)};
            double groundRef = f32(d);
            double ix = f32(d), iz = f32(d), ir = f32(d);

            int np = d.readUnsignedByte();
            PaletteEntry[] palette = new PaletteEntry[np];
            for (int i = 0; i < np; i++) {
                int family = d.readUnsignedByte();
                byte[] name = new byte[d.readUnsignedByte()];
                d.readFully(name);
                int nvar = d.readUnsignedByte();
                int[] variants = new int[nvar], cumulative = new int[nvar];
                int total = 0;
                for (int j = 0; j < nvar; j++) {
                    variants[j] = d.readUnsignedByte();
                    total += d.readUnsignedByte();
                    cumulative[j] = total;
                }
                palette[i] = new PaletteEntry(new String(name, StandardCharsets.US_ASCII), family,
                        variants, cumulative, total);
            }

            int nv = varint(d);
            float[] raw = new float[nv * 3];
            long[] q = new long[3];
            float minY = Float.POSITIVE_INFINITY;
            for (int i = 0; i < nv; i++) {
                for (int c = 0; c < 3; c++) {
                    q[c] += zigzag(varint(d));
                    // decoded in double, stored as float: matches the preview's Float32Array
                    raw[i * 3 + c] = (float) (lo[c] + (q[c] / 65535.0) * (hi[c] - lo[c]));
                }
                minY = Math.min(minY, raw[i * 3 + 1]);
            }
            // column: groundRef lands on groundY; shell: the lowest point does
            double baseY = opt.shell ? minY : groundRef;
            double[] w = new double[nv * 3];
            for (int i = 0; i < nv; i++) {
                w[i * 3] = raw[i * 3] * scale;
                w[i * 3 + 1] = (raw[i * 3 + 1] - baseY) * scale + groundY;
                w[i * 3 + 2] = raw[i * 3 + 2] * scale;
            }
            byte[] vmat = new byte[nv];
            d.readFully(vmat);

            int nt = varint(d);
            int[] tris = new int[nt * 3];
            int a = 0;
            for (int i = 0; i < nt; i++) {
                a += zigzag(varint(d));
                tris[i * 3] = a;
                tris[i * 3 + 1] = a + zigzag(varint(d));
                tris[i * 3 + 2] = a + zigzag(varint(d));
            }
            Grid grid = readGrid(d);
            return new VoxelMesh(palette, vmat, w, tris, opt, grid, baseY,
                    ix * scale, iz * scale, ir * scale);
        }
    }

    /** The optional TEX1 section after the triangles (build_scan.py write_grid), or null. */
    private static Grid readGrid(DataInputStream d) throws IOException {
        byte[] magic = new byte[4];
        int n = d.readNBytes(magic, 0, 4);
        if (n < 4 || !"TEX1".equals(new String(magic, StandardCharsets.US_ASCII))) return null;
        double cell = f32(d);
        double[] origin = {f32(d), f32(d), f32(d)};
        int[] dims = {u16(d), u16(d), u16(d)};
        int count = varint(d);
        long[] keys = new long[count];
        long k = 0;
        for (int i = 0; i < count; i++) keys[i] = k += varlong(d);
        byte[] mats = new byte[count];
        d.readFully(mats);
        return Grid.of(cell, origin, dims, keys, mats);
    }

    public PaletteEntry[] palette() {
        return palette.clone();
    }

    // ── generation ───────────────────────────────────────────────────────────────────────────────

    private record Hit(double y, boolean up, int fam, int mat) {}

    /** Voxelize one chunk. Only blocks inside [minY, maxY) are written. */
    public void generateChunk(int chunkX, int chunkZ, int minY, int maxY, BlockSink sink) {
        int[] bucket = chunkTris.get(chunkKey(chunkX, chunkZ));
        if (bucket == null) return;
        if (opt.shell) {
            shellChunk(chunkX, chunkZ, minY, maxY, bucket, sink);
            return;
        }

        int bx = chunkX * 16 - originX, bz = chunkZ * 16 - originZ;   // local coords of column (0,0)
        double[] wx = new double[256], wz = new double[256];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int x = bx + i, z = bz + j;
                wx[i * 16 + j] = x + 0.5 + (vnoise(x / WARP_SCALE, 0, z / WARP_SCALE, SALT_WARP_X) * 2 - 1) * WARP_AMP;
                wz[i * 16 + j] = z + 0.5 + (vnoise(x / WARP_SCALE, 0, z / WARP_SCALE, SALT_WARP_Z) * 2 - 1) * WARP_AMP;
            }
        }

        @SuppressWarnings("unchecked")
        List<Hit>[] hits = new List[256];
        for (int t : bucket) {
            int a = tris[t], b = tris[t + 1], c = tris[t + 2];
            double ax = w[a * 3], ay = w[a * 3 + 1], az = w[a * 3 + 2];
            double bxv = w[b * 3], by = w[b * 3 + 1], bzv = w[b * 3 + 2];
            double cx = w[c * 3], cy = w[c * 3 + 1], cz = w[c * 3 + 2];
            double det = (bxv - ax) * (cz - az) - (cx - ax) * (bzv - az);
            double ux = bxv - ax, uy = by - ay, uz = bzv - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
            double nxn = uy * vz - uz * vy, nyn = uz * vx - ux * vz, nzn = ux * vy - uy * vx;
            if (nyn * nyn <= 1e-8 * (nxn * nxn + nyn * nyn + nzn * nzn)) continue;
            boolean up = nyn > 0;
            int x0 = Math.max(bx, (int) Math.floor(Math.min(ax, Math.min(bxv, cx)) - WARP_AMP - 0.5));
            int x1 = Math.min(bx + 15, (int) Math.ceil(Math.max(ax, Math.max(bxv, cx)) + WARP_AMP - 0.5));
            int z0 = Math.max(bz, (int) Math.floor(Math.min(az, Math.min(bzv, cz)) - WARP_AMP - 0.5));
            int z1 = Math.min(bz + 15, (int) Math.ceil(Math.max(az, Math.max(bzv, cz)) + WARP_AMP - 0.5));
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    int idx = (x - bx) * 16 + (z - bz);
                    double px = wx[idx], pz = wz[idx];
                    double w1 = ((px - ax) * (cz - az) - (cx - ax) * (pz - az)) / det;
                    double w2 = ((bxv - ax) * (pz - az) - (px - ax) * (bzv - az)) / det;
                    double w0 = 1 - w1 - w2;
                    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                    int dom = a;
                    if (w1 > w0 && w1 >= w2) dom = b;
                    else if (w2 > w0 && w2 > w1) dom = c;
                    int fam = family(dom);
                    double y = w0 * ay + w1 * by + w2 * cy
                            + fbm2(x, z, BUMP_SCALE, SALT_BUMP) * (fam == 1 ? BUMP_STONE : BUMP_GROUND);
                    double r = unit(hash(x, (int) Math.floor(y), z));
                    int pick = r < w0 ? a : r < w0 + w1 ? b : c;
                    if (hits[idx] == null) hits[idx] = new ArrayList<>(4);
                    hits[idx].add(new Hit(y, up, fam, vmat[pick] & 0xff));
                }
            }
        }

        Map<Long, Integer> latticeCache = new HashMap<>();
        double r2 = islandR * islandR;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                List<Hit> list = hits[i * 16 + j];
                int x = bx + i, z = bz + j;
                double ex = x + 0.5 - islandX, ez = z + 0.5 - islandZ;
                if (list == null || ex * ex + ez * ez > r2) continue;   // off the island: void
                Column col = new Column(i, j, x, z, minY, maxY, sink, latticeCache);
                int bottom = islandBottom(x, z);
                list.sort((p, q) -> {
                    double d = q.y - p.y;
                    return d > 0 ? 1 : d < 0 ? -1 : 0;
                });
                Hit top = null;
                int lowest = Integer.MAX_VALUE;
                for (Hit h : list) {
                    if (h.up) {
                        if (top == null) top = h;
                    } else if (top != null) {
                        int yt = (int) Math.floor(top.y - 0.5), yb = (int) Math.ceil(h.y - 0.5);
                        if (yt >= yb) {
                            col.fill(yt, yb, top.fam, top.mat);
                            lowest = Math.min(lowest, yb);
                        }
                        top = null;
                    }
                }
                if (top != null) {
                    col.fill((int) Math.floor(top.y - 0.5), bottom, top.fam, top.mat);
                } else if (lowest != Integer.MAX_VALUE) {
                    col.fill(lowest - 1, bottom, FILL, stone);
                }
            }
        }
    }

    /**
     * Y of the top block of a world column (the highest up-facing hit), or {@code Integer.MIN_VALUE}
     * if the column misses the mesh. Same sampling as {@link #generateChunk}, for one column.
     */
    public int surfaceY(int worldX, int worldZ) {
        int[] bucket = chunkTris.get(chunkKey(worldX >> 4, worldZ >> 4));
        if (bucket == null) return Integer.MIN_VALUE;
        int x = worldX - originX, z = worldZ - originZ;
        double px = x + 0.5 + (vnoise(x / WARP_SCALE, 0, z / WARP_SCALE, SALT_WARP_X) * 2 - 1) * WARP_AMP;
        double pz = z + 0.5 + (vnoise(x / WARP_SCALE, 0, z / WARP_SCALE, SALT_WARP_Z) * 2 - 1) * WARP_AMP;
        double best = Double.NEGATIVE_INFINITY;
        for (int t : bucket) {
            int a = tris[t], b = tris[t + 1], c = tris[t + 2];
            double ax = w[a * 3], ay = w[a * 3 + 1], az = w[a * 3 + 2];
            double bxv = w[b * 3], by = w[b * 3 + 1], bzv = w[b * 3 + 2];
            double cx = w[c * 3], cy = w[c * 3 + 1], cz = w[c * 3 + 2];
            double det = (bxv - ax) * (cz - az) - (cx - ax) * (bzv - az);
            double ux = bxv - ax, uy = by - ay, uz = bzv - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
            double nxn = uy * vz - uz * vy, nyn = uz * vx - ux * vz, nzn = ux * vy - uy * vx;
            if (nyn <= 0 || nyn * nyn <= 1e-8 * (nxn * nxn + nyn * nyn + nzn * nzn)) continue;
            double w1 = ((px - ax) * (cz - az) - (cx - ax) * (pz - az)) / det;
            double w2 = ((bxv - ax) * (pz - az) - (px - ax) * (bzv - az)) / det;
            double w0 = 1 - w1 - w2;
            if (w0 < 0 || w1 < 0 || w2 < 0) continue;
            int dom = a;
            if (w1 > w0 && w1 >= w2) dom = b;
            else if (w2 > w0 && w2 > w1) dom = c;
            double y = w0 * ay + w1 * by + w2 * cy
                    + fbm2(x, z, BUMP_SCALE, SALT_BUMP) * (family(dom) == 1 ? BUMP_STONE : BUMP_GROUND);
            best = Math.max(best, y);
        }
        return best == Double.NEGATIVE_INFINITY ? Integer.MIN_VALUE : (int) Math.floor(best - 0.5);
    }

    /** One column's fill rules (mirrors fill() in voxelize.js). */
    private final class Column {
        final int i, j, x, z, minY, maxY;
        final BlockSink sink;
        final Map<Long, Integer> latticeCache;

        Column(int i, int j, int x, int z, int minY, int maxY, BlockSink sink, Map<Long, Integer> latticeCache) {
            this.i = i; this.j = j; this.x = x; this.z = z;
            this.minY = minY; this.maxY = maxY;
            this.sink = sink;
            this.latticeCache = latticeCache;
        }

        void set(int y, int m) {
            if (y >= minY && y < maxY) sink.set(i, y, j, variant(m, x, y, z));
        }

        void fill(int yTop, int yBot, int fam, int mat) {
            int last = stone;
            for (int y = Math.min(yTop, maxY - 1); y >= yBot; y--) {
                if (y < pickFloor) {
                    for (int k = Math.max(yBot, minY); k <= y; k++) sink.set(i, k, j, stone);
                    return;
                }
                if (fam == FILL) {
                    set(y, stone);
                } else if (fam == 0) {
                    int depth = yTop - y;
                    set(y, depth == 0 ? mat : depth <= DIRT_DEPTH ? dirt : stone);
                } else {
                    // deep inside, away from every surface: nothing to look up, continue the column
                    if (grid != null && yTop - y > INTERIOR_DEPTH && !grid.nearPoint((x + 0.5) / opt.scale,
                            (y + 0.5 - groundY) / opt.scale + meshBaseY, (z + 0.5) / opt.scale)) {
                        set(y, last);
                        continue;
                    }
                    double sx = x / MAT_WARP_SCALE, sy = y / MAT_WARP_SCALE, sz = z / MAT_WARP_SCALE;
                    double jx = (vnoise(sx, sy, sz, SALT_MAT) * 2 - 1) * MAT_WARP;
                    double jy = (vnoise(sx, sy, sz, SALT_MAT + 1) * 2 - 1) * MAT_WARP;
                    double jz = (vnoise(sx, sy, sz, SALT_MAT + 2) * 2 - 1) * MAT_WARP;
                    // surface grid near the surface (light jitter only: it carries real detail),
                    // nearest-vertex lattice deeper in
                    int m = -1;
                    if (grid != null) {
                        m = grid.material((x + 0.5 + jx * GRID_JITTER) / opt.scale,
                                (y + 0.5 + jy * GRID_JITTER - groundY) / opt.scale + meshBaseY,
                                (z + 0.5 + jz * GRID_JITTER) / opt.scale);
                    }
                    last = m >= 0 ? m : stoneMat(x + 0.5 + jx, y + 0.5 + jy, z + 0.5 + jz, latticeCache);
                    set(y, last);
                }
            }
        }
    }

    // ── shell mode (mirrors voxelizeShell() in voxelize.js) ───────────────────────────────────────

    private void shellChunk(int chunkX, int chunkZ, int minY, int maxY, int[] bucket, BlockSink sink) {
        int bx = chunkX * 16 - originX, bz = chunkZ * 16 - originZ;   // local coords of column (0,0)
        int height = maxY - minY;
        boolean[] taken = new boolean[16 * 16 * height];
        double thickness = opt.thickness, reach = thickness + SHELL_BUMP, r2max = reach * reach;
        int[] min = {bx, minY, bz}, max = {bx + 15, maxY - 1, bz + 15};
        double[] A = new double[3], B = new double[3], C = new double[3], bary = new double[3];
        int[] p = new int[3];
        for (int t : bucket) {
            int a = tris[t], b = tris[t + 1], c = tris[t + 2];
            for (int k = 0; k < 3; k++) {
                A[k] = w[a * 3 + k];
                B[k] = w[b * 3 + k];
                C[k] = w[c * 3 + k];
            }
            double ux = B[0] - A[0], uy = B[1] - A[1], uz = B[2] - A[2];
            double vx = C[0] - A[0], vy = C[1] - A[1], vz = C[2] - A[2];
            double[] n = {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
            double len = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            if (len < 1e-12) continue;
            double dPlane = n[0] * A[0] + n[1] * A[1] + n[2] * A[2];
            int d = 0;
            if (Math.abs(n[1]) > Math.abs(n[d])) d = 1;
            if (Math.abs(n[2]) > Math.abs(n[d])) d = 2;
            int u = (d + 1) % 3, wa = (d + 2) % 3;
            double span = reach * len / Math.abs(n[d]);
            int u0 = Math.max(min[u], (int) Math.floor(Math.min(A[u], Math.min(B[u], C[u])) - reach - 0.5));
            int u1 = Math.min(max[u], (int) Math.ceil(Math.max(A[u], Math.max(B[u], C[u])) + reach - 0.5));
            int w0 = Math.max(min[wa], (int) Math.floor(Math.min(A[wa], Math.min(B[wa], C[wa])) - reach - 0.5));
            int w1 = Math.min(max[wa], (int) Math.ceil(Math.max(A[wa], Math.max(B[wa], C[wa])) + reach - 0.5));
            for (int iu = u0; iu <= u1; iu++) {
                for (int iw = w0; iw <= w1; iw++) {
                    double cd = (dPlane - n[u] * (iu + 0.5) - n[wa] * (iw + 0.5)) / n[d];
                    int d0 = Math.max(min[d], (int) Math.floor(cd - span - 0.5));
                    int d1 = Math.min(max[d], (int) Math.ceil(cd + span - 0.5));
                    for (int id = d0; id <= d1; id++) {
                        p[d] = id;
                        p[u] = iu;
                        p[wa] = iw;
                        int slot = ((p[0] - bx) * 16 + (p[2] - bz)) * height + (p[1] - minY);
                        if (taken[slot]) continue;
                        double px = p[0] + 0.5, py = p[1] + 0.5, pz = p[2] + 0.5;
                        closestBary(px, py, pz, A, B, C, bary);
                        double qx = bary[0] * A[0] + bary[1] * B[0] + bary[2] * C[0] - px;
                        double qy = bary[0] * A[1] + bary[1] * B[1] + bary[2] * C[1] - py;
                        double qz = bary[0] * A[2] + bary[1] * B[2] + bary[2] * C[2] - pz;
                        double dist2 = qx * qx + qy * qy + qz * qz;
                        if (dist2 > r2max) continue;
                        double th = thickness + (vnoise(px / SHELL_BUMP_SCALE, py / SHELL_BUMP_SCALE,
                                pz / SHELL_BUMP_SCALE, SALT_SHELL) * 2 - 1) * SHELL_BUMP;
                        if (dist2 > th * th) continue;
                        int m = -1;
                        if (grid != null) {
                            m = grid.material(px / opt.scale, (py - groundY) / opt.scale + meshBaseY, pz / opt.scale);
                        }
                        if (m < 0) {
                            double r = unit(hash(p[0], p[1], p[2]));
                            m = vmat[r < bary[0] ? a : r < bary[0] + bary[1] ? b : c] & 0xff;
                        }
                        taken[slot] = true;
                        sink.set(p[0] - bx, p[1], p[2] - bz, variant(m, p[0], p[1], p[2]));
                    }
                }
            }
        }
    }

    /** Closest point on triangle abc to p (Ericson, RTCD 5.1.5), as barycentrics into {@code out}. */
    private static void closestBary(double px, double py, double pz, double[] a, double[] b, double[] c, double[] out) {
        double abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
        double acx = c[0] - a[0], acy = c[1] - a[1], acz = c[2] - a[2];
        double apx = px - a[0], apy = py - a[1], apz = pz - a[2];
        double d1 = abx * apx + aby * apy + abz * apz, d2 = acx * apx + acy * apy + acz * apz;
        if (d1 <= 0 && d2 <= 0) { out[0] = 1; out[1] = 0; out[2] = 0; return; }
        double bpx = px - b[0], bpy = py - b[1], bpz = pz - b[2];
        double d3 = abx * bpx + aby * bpy + abz * bpz, d4 = acx * bpx + acy * bpy + acz * bpz;
        if (d3 >= 0 && d4 <= d3) { out[0] = 0; out[1] = 1; out[2] = 0; return; }
        double vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0) {
            double v = d1 / (d1 - d3);
            out[0] = 1 - v; out[1] = v; out[2] = 0;
            return;
        }
        double cpx = px - c[0], cpy = py - c[1], cpz = pz - c[2];
        double d5 = abx * cpx + aby * cpy + abz * cpz, d6 = acx * cpx + acy * cpy + acz * cpz;
        if (d6 >= 0 && d5 <= d6) { out[0] = 0; out[1] = 0; out[2] = 1; return; }
        double vb = d5 * d2 - d1 * d6;
        if (vb <= 0 && d2 >= 0 && d6 <= 0) {
            double ww = d2 / (d2 - d6);
            out[0] = 1 - ww; out[1] = 0; out[2] = ww;
            return;
        }
        double va = d3 * d6 - d5 * d4;
        if (va <= 0 && d4 - d3 >= 0 && d5 - d6 >= 0) {
            double ww = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            out[0] = 0; out[1] = 1 - ww; out[2] = ww;
            return;
        }
        double denom = 1 / (va + vb + vc);
        double v = vb * denom, ww = vc * denom;
        out[0] = 1 - v - ww; out[1] = v; out[2] = ww;
    }

    private int islandBottom(int x, int z) {
        double dx = x + 0.5 - islandX, dz = z + 0.5 - islandZ;
        double d = Math.sqrt(dx * dx + dz * dz) / islandR;
        double depth = ISLAND_EDGE + (maxDepth - ISLAND_EDGE) * Math.sqrt(Math.max(0, 1 - d * d));
        return (int) Math.floor(groundY - depth * (1 + ISLAND_RAGGED * fbm2(x, z, 24, SALT_UNDER)));
    }

    private int stoneMat(double x, double y, double z, Map<Long, Integer> cache) {
        int lx = floorDiv(x, MAT_LATTICE), ly = floorDiv(y, MAT_LATTICE), lz = floorDiv(z, MAT_LATTICE);
        return cache.computeIfAbsent(cellKey(lx, ly, lz), k -> {
            int v = nearestStone((lx + 0.5) * MAT_LATTICE, (ly + 0.5) * MAT_LATTICE, (lz + 0.5) * MAT_LATTICE);
            return v >= 0 ? vmat[v] & 0xff : stone;
        });
    }

    private int nearestStone(double x, double y, double z) {
        int cx = floorDiv(x, CELL), cy = floorDiv(y, CELL), cz = floorDiv(z, CELL);
        int best = -1;
        double bd = Double.POSITIVE_INFINITY;
        for (int r = 1; r <= 3 && best < 0; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        int[] list = stoneGrid.get(cellKey(cx + dx, cy + dy, cz + dz));
                        if (list == null) continue;
                        for (int v : list) {
                            double ex = w[v * 3] - x, ey = w[v * 3 + 1] - y, ez = w[v * 3 + 2] - z;
                            double d = ex * ex + ey * ey + ez * ez;
                            if (d < bd) {
                                bd = d;
                                best = v;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private int variant(int m, int x, int y, int z) {
        PaletteEntry e = palette[m];
        if (e.variants.length == 1) return e.variants[0];
        double clump = vnoise(x / CLUMP_SCALE, y / CLUMP_SCALE, z / CLUMP_SCALE, SALT_CLUMP + m);
        double r = Math.min(0.999999, Math.max(0, (clump - 0.5) * 1.6 + 0.5) * 0.7 + unit(hash(x, y, z)) * 0.3);
        double t = r * e.total;
        for (int k = 0; k < e.variants.length; k++) {
            if (t < e.cumulative[k]) return e.variants[k];
        }
        return e.variants[e.variants.length - 1];
    }

    private int family(int v) {
        return palette[vmat[v] & 0xff].family;
    }

    private int indexOf(String id) {
        for (int i = 0; i < palette.length; i++) {
            if (palette[i].id.equals(id)) return i;
        }
        return -1;
    }

    // ── hash and value noise (mirror voxelize.js) ────────────────────────────────────────────────

    static int hash(int x, int y, int z) {
        int h = (x * 0x27d4eb2d) ^ (y * 0x165667b1) ^ (z * 0x9e3779b1);
        h = (h ^ (h >>> 15)) * 0x85ebca6b;
        h = (h ^ (h >>> 13)) * 0xc2b2ae35;
        return h ^ (h >>> 16);
    }

    private static double unit(int h) {
        return (h & 0xffffffffL) / 4294967296.0;
    }

    private static double lattice(int x, int y, int z, int salt) {
        return unit(hash(x, y, z ^ (salt * 0x632be5ab)));
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double vnoise(double x, double y, double z, int salt) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double tx = smooth(x - xi), ty = smooth(y - yi), tz = smooth(z - zi);
        return lerp(
                lerp(lerp(lattice(xi, yi, zi, salt), lattice(xi + 1, yi, zi, salt), tx),
                        lerp(lattice(xi, yi + 1, zi, salt), lattice(xi + 1, yi + 1, zi, salt), tx), ty),
                lerp(lerp(lattice(xi, yi, zi + 1, salt), lattice(xi + 1, yi, zi + 1, salt), tx),
                        lerp(lattice(xi, yi + 1, zi + 1, salt), lattice(xi + 1, yi + 1, zi + 1, salt), tx), ty),
                tz);
    }

    private static double fbm2(int x, int z, double scale, int salt) {
        return (vnoise(x / scale, 0, z / scale, salt) * 0.65
                + vnoise(x / (scale * 0.37), 0, z / (scale * 0.37), salt + 1) * 0.35) * 2 - 1;
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private static int floorDiv(double v, int cell) {
        return (int) Math.floor(v / cell);
    }

    private static long cellKey(int x, int y, int z) {
        return (((long) x + 1024) * 2048 + (y + 1024)) * 2048 + (z + 1024);
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private static Map<Long, int[]> freeze(Map<Long, List<Integer>> in) {
        Map<Long, int[]> out = new HashMap<>(in.size() * 2);
        for (Map.Entry<Long, List<Integer>> e : in.entrySet()) {
            out.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        }
        return out;
    }

    static double f32(DataInputStream d) throws IOException {
        return Float.intBitsToFloat(Integer.reverseBytes(d.readInt()));
    }

    static int varint(DataInputStream d) throws IOException {
        int v = 0, shift = 0, b;
        do {
            b = d.readUnsignedByte();
            v |= (b & 0x7f) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return v;
    }

    static long varlong(DataInputStream d) throws IOException {
        long v = 0;
        int shift = 0, b;
        do {
            b = d.readUnsignedByte();
            v |= (long) (b & 0x7f) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return v;
    }

    static int u16(DataInputStream d) throws IOException {
        return Short.reverseBytes(d.readShort()) & 0xffff;
    }

    static int zigzag(int v) {
        return (v >>> 1) ^ -(v & 1);
    }
}
