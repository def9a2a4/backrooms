package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Grid;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where Alpha's grammar-built houses ({@link AlphaHouse}) stand. The plain is cut into square
 * cells; a cell holds a house by chance, placed and turned from the seed and the cell alone,
 * always inside the cell with room to spare. A house crosses chunk borders freely: each chunk
 * asks its own cell for the house and copies the part of it that falls inside.
 *
 * <p>Built houses are kept (a few hundred, least recently used dropped) since every house is
 * asked for by each of the chunks it touches.
 */
final class AlphaHouses {

    static final int CELL = 64;
    private static final int MARGIN = 4;
    private static final int CACHE = 256;

    /** A house in a cell: its grid, placed at (x, z) turned {@code rot} quarter turns. */
    record Placed(Grid grid, int x, int z, int rot, int minX, int minZ, int maxX, int maxZ) {
        boolean touches(int chunkX, int chunkZ, int pad) {
            int cx0 = chunkX * 16, cz0 = chunkZ * 16;
            return maxX + pad >= cx0 && minX - pad <= cx0 + 15 && maxZ + pad >= cz0 && minZ - pad <= cz0 + 15;
        }
    }

    /** Whether ground is already taken (by a megastructure): world box minX, minZ, maxX, maxZ. */
    @FunctionalInterface
    interface Taken {
        boolean test(long seed, int minX, int minZ, int maxX, int maxZ);
    }

    private final double chance;
    private final int spawnExclusion;
    private final Taken taken;
    private final Map<Long, Placed> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Placed> e) {
            return size() > CACHE;
        }
    };
    private static final Placed NONE = new Placed(null, 0, 0, 0, 0, 0, 0, 0);

    AlphaHouses(double chancePercent, int spawnExclusion, Taken taken) {
        this.chance = chancePercent / 100.0;
        this.spawnExclusion = spawnExclusion;
        this.taken = taken;
    }

    /** The house whose footprint (plus {@code pad}) touches this chunk, if any. */
    @Nullable Placed near(long seed, int chunkX, int chunkZ, int pad) {
        Placed p = at(seed, Math.floorDiv(chunkX * 16, CELL), Math.floorDiv(chunkZ * 16, CELL));
        return p != null && p.touches(chunkX, chunkZ, pad) ? p : null;
    }

    void place(ChunkData data, int chunkX, int chunkZ, Placed p) {
        p.grid().place(data, chunkX, chunkZ, p.x(), Level101ChunkGenerator.GROUND_Y + 1, p.z(), p.rot());
    }

    /** The house in this cell, if it has one. */
    @Nullable Placed at(long seed, int cellX, int cellZ) {
        if (chance <= 0) return null;
        long key = (long) cellX << 32 ^ (cellZ & 0xFFFFFFFFL);
        synchronized (cache) {
            Placed hit = cache.get(key);
            if (hit != null) return hit == NONE ? null : hit;
        }
        Placed p = build(seed, cellX, cellZ);
        synchronized (cache) {
            cache.put(key, p == null ? NONE : p);
        }
        return p;
    }

    private @Nullable Placed build(long seed, int cellX, int cellZ) {
        long h = AlphaMegastructures.hash(cellX, cellZ, seed, 0x4053);
        if (Math.floorMod(h, 10_000) >= Math.round(chance * 10_000)) return null;
        double cx = cellX * CELL + CELL / 2.0, cz = cellZ * CELL + CELL / 2.0;
        if (cx * cx + cz * cz < (double) spawnExclusion * spawnExclusion) return null;
        Grid g = AlphaHouse.build(h ^ 0x51ED270B27C3E2F1L);
        int rot = (int) ((h >>> 20) & 3);
        // Fit the turned footprint inside the cell: place at the origin, then shift into a random spot.
        int[] b = g.worldBounds(0, 0, rot);
        int w = b[2] - b[0] + 1, d = b[3] - b[1] + 1;
        int roomX = CELL - 2 * MARGIN - w, roomZ = CELL - 2 * MARGIN - d;
        if (roomX < 0 || roomZ < 0) return null;
        int ox = cellX * CELL + MARGIN + (int) Math.floorMod(h >>> 24, (long) roomX + 1) - b[0];
        int oz = cellZ * CELL + MARGIN + (int) Math.floorMod(h >>> 40, (long) roomZ + 1) - b[1];
        int[] wb = g.worldBounds(ox, oz, rot);
        if (taken.test(seed, wb[0] - 8, wb[1] - 8, wb[2] + 8, wb[3] + 8)) return null;
        return new Placed(g, ox, oz, rot, wb[0], wb[1], wb[2], wb[3]);
    }
}
