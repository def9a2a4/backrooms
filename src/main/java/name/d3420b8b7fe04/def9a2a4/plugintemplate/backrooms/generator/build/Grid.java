package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A building's blocks, laid out once in its own frame and then copied into whichever chunks it
 * crosses: the way IronSource derives a whole site and each cell keeps only what lies inside it.
 *
 * <p>The frame: x across the front, z from the front (z = 0) to the back, y up from the first
 * block above the ground. The front faces north (-z) until it is placed; {@link #place} turns it
 * by quarter turns about the origin, block states and all.
 *
 * <p>Blocks are kept as state strings ("minecraft:oak_stairs[facing=north,half=bottom]"), so a
 * grid can be built and inspected without a server; they are resolved to BlockData (and turned)
 * once per distinct string. Cells never set are left alone when placed; air written on purpose
 * carves.
 */
public final class Grid {

    public final int minX, minY, minZ, sizeX, sizeY, sizeZ;
    private final short[] cells;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Short> index = new HashMap<>();

    public Grid(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cells = new short[sizeX * sizeY * sizeZ];
        palette.add(null);
    }

    public int maxX() { return minX + sizeX - 1; }
    public int maxY() { return minY + sizeY - 1; }
    public int maxZ() { return minZ + sizeZ - 1; }

    private int at(int x, int y, int z) {
        x -= minX;
        y -= minY;
        z -= minZ;
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) return -1;
        return (y * sizeZ + z) * sizeX + x;
    }

    public void set(int x, int y, int z, String state) {
        int i = at(x, y, z);
        if (i < 0) return;
        cells[i] = index.computeIfAbsent(state, s -> {
            palette.add(s);
            return (short) (palette.size() - 1);
        });
    }

    /** Sets only where nothing has been set yet. */
    public void add(int x, int y, int z, String state) {
        int i = at(x, y, z);
        if (i >= 0 && cells[i] == 0) set(x, y, z, state);
    }

    /** True where nothing but air is (set or not). */
    public boolean free(int x, int y, int z) {
        String s = get(x, y, z);
        return s == null || s.equals(Blocks.AIR);
    }

    /** Sets only where it is free; false if something was in the way. */
    public boolean put(int x, int y, int z, String state) {
        if (at(x, y, z) < 0 || !free(x, y, z)) return false;
        set(x, y, z, state);
        return true;
    }

    public void clear(int x, int y, int z) {
        int i = at(x, y, z);
        if (i >= 0) cells[i] = 0;
    }

    public @Nullable String get(int x, int y, int z) {
        int i = at(x, y, z);
        return i < 0 ? null : palette.get(cells[i]);
    }

    /** True where a solid-ish block (not air, not unset) is. */
    public boolean solid(int x, int y, int z) {
        String s = get(x, y, z);
        return s != null && !s.equals(Blocks.AIR);
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, String state) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) set(x, y, z, state);
    }

    // ── Placement ────────────────────────────────────────────────────────

    private static final StructureRotation[] ROTATIONS = {
            StructureRotation.NONE, StructureRotation.CLOCKWISE_90,
            StructureRotation.CLOCKWISE_180, StructureRotation.COUNTERCLOCKWISE_90
    };
    private static final Map<String, BlockData[]> RESOLVED = new ConcurrentHashMap<>();

    private static BlockData resolve(String state, int rot) {
        BlockData[] turned = RESOLVED.computeIfAbsent(state, s -> {
            BlockData base = Bukkit.createBlockData(s);
            BlockData[] out = new BlockData[4];
            out[0] = base;
            for (int r = 1; r < 4; r++) {
                BlockData b = base.clone();
                b.rotate(ROTATIONS[r]);
                out[r] = b;
            }
            return out;
        });
        return turned[rot];
    }

    /** World x of frame point (x, z) placed at (ox, oz) turned {@code rot} quarter turns clockwise. */
    public static int worldX(int ox, int rot, int x, int z) {
        return ox + switch (rot & 3) { case 0 -> x; case 1 -> -z; case 2 -> -x; default -> z; };
    }

    public static int worldZ(int oz, int rot, int x, int z) {
        return oz + switch (rot & 3) { case 0 -> z; case 1 -> x; case 2 -> -z; default -> -x; };
    }

    /** The world box this grid covers when placed: {minX, minZ, maxX, maxZ}. */
    public int[] worldBounds(int ox, int oz, int rot) {
        int ax = worldX(ox, rot, minX, minZ), bx = worldX(ox, rot, maxX(), maxZ());
        int az = worldZ(oz, rot, minX, minZ), bz = worldZ(oz, rot, maxX(), maxZ());
        return new int[]{ Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz) };
    }

    /**
     * Copies the part of this grid inside chunk (chunkX, chunkZ), its frame origin at world
     * (ox, oy, oz) and turned {@code rot} quarter turns clockwise (seen from above).
     */
    public void place(ChunkData data, int chunkX, int chunkZ, int ox, int oy, int oz, int rot) {
        int[] b = worldBounds(ox, oz, rot);
        int x0 = Math.max(b[0], chunkX * 16), x1 = Math.min(b[2], chunkX * 16 + 15);
        int z0 = Math.max(b[1], chunkZ * 16), z1 = Math.min(b[3], chunkZ * 16 + 15);
        if (x0 > x1 || z0 > z1) return;
        int lo = data.getMinHeight(), hi = data.getMaxHeight();
        for (int wx = x0; wx <= x1; wx++) {
            for (int wz = z0; wz <= z1; wz++) {
                int dx = wx - ox, dz = wz - oz;
                // the inverse turn
                int x, z;
                switch (rot & 3) {
                    case 0 -> { x = dx; z = dz; }
                    case 1 -> { x = dz; z = -dx; }
                    case 2 -> { x = -dx; z = -dz; }
                    default -> { x = -dz; z = dx; }
                }
                for (int y = minY; y <= maxY(); y++) {
                    int i = at(x, y, z);
                    if (i < 0 || cells[i] == 0) continue;
                    int wy = oy + y;
                    if (wy < lo || wy >= hi) continue;
                    data.setBlock(wx - chunkX * 16, wy, wz - chunkZ * 16, resolve(palette.get(cells[i]), rot & 3));
                }
            }
        }
    }
}
