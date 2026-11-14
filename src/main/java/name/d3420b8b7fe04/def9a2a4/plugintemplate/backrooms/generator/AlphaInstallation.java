package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Grid;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Rng;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * An installation on Alpha's plain, rare and far out: a control centre ({@link ControlCentre})
 * with its radars and hangars round it ({@link Radars}), gravel roads from its door to each.
 * Nobody is there. The consoles are still on their desks.
 *
 * <p>Everything is laid out from the seed inside a square area, then each chunk copies what
 * falls inside it. Building one takes a moment, so the last few are kept.
 */
final class AlphaInstallation {

    /** One building or antenna: its grid placed at (x, z) turned {@code rot}, and its world box. */
    record Part(Grid grid, int x, int z, int rot, int minX, int minZ, int maxX, int maxZ) {
        boolean touches(int chunkX, int chunkZ) {
            int cx0 = chunkX * 16, cz0 = chunkZ * 16;
            return maxX >= cx0 && minX <= cx0 + 15 && maxZ >= cz0 && minZ <= cz0 + 15;
        }
    }

    private static final int GAP = 12;
    private static final int CACHE = 4;
    private static final Map<Long, CompletableFuture<AlphaInstallation>> BUILT = new LinkedHashMap<>(8, 0.75f, true);

    final List<Part> parts = new ArrayList<>();

    /** The installation in the square from (x0, z0), {@code size} across: built, or taken from the cache. */
    static AlphaInstallation get(long seed, int x0, int z0, int size) {
        long key = Rng.mix(seed ^ Rng.mix(x0 * 0x9E3779B97F4A7C15L ^ z0 * 0xC2B2AE3D27D4EB4FL) ^ size);
        CompletableFuture<AlphaInstallation> f;
        boolean mine = false;
        synchronized (BUILT) {
            f = BUILT.get(key);
            if (f == null) {
                f = new CompletableFuture<>();
                BUILT.put(key, f);
                mine = true;
                while (BUILT.size() > CACHE) BUILT.remove(BUILT.keySet().iterator().next());
            }
        }
        if (mine) {
            try {
                f.complete(new AlphaInstallation(key, x0, z0, size));
            } catch (RuntimeException e) {
                f.completeExceptionally(e);
                synchronized (BUILT) { BUILT.remove(key); }
                throw e;
            }
        }
        return f.join();
    }

    void place(ChunkData data, int chunkX, int chunkZ) {
        for (Part p : parts) {
            if (p.touches(chunkX, chunkZ)) p.grid().place(data, chunkX, chunkZ, p.x(), Level101ChunkGenerator.GROUND_Y + 1, p.z(), p.rot());
        }
    }

    private AlphaInstallation(long seed, int x0, int z0, int size) {
        Rng r = new Rng(seed);
        Grid roads = new Grid(x0, -1, z0, size, 1, size);
        parts.add(new Part(roads, 0, 0, 0, x0, z0, x0 + size - 1, z0 + size - 1));

        // The control centre, near the middle.
        ControlCentre cc = new ControlCentre(r.sub("centre").nextLong());
        int rot = r.range(0, 3);
        int[] b = cc.g.worldBounds(0, 0, rot);
        int w = b[2] - b[0] + 1, d = b[3] - b[1] + 1;
        int slackX = Math.max(0, size - w - 2 * GAP), slackZ = Math.max(0, size - d - 2 * GAP);
        int ox = x0 + GAP + slackX / 2 + r.range(-slackX / 4, slackX / 4) - b[0];
        int oz = z0 + GAP + slackZ / 2 + r.range(-slackZ / 4, slackZ / 4) - b[1];
        Part centre = add(cc.g, ox, oz, rot);
        // A ring road round it, and the way in from the ring to its door.
        int[] ring = { centre.minX() - 7, centre.minZ() - 7, centre.maxX() + 7, centre.maxZ() + 7 };
        for (int x = ring[0]; x <= ring[2]; x++) { gravel(roads, x, ring[1]); gravel(roads, x, ring[3]); }
        for (int z = ring[1]; z <= ring[3]; z++) { gravel(roads, ring[0], z); gravel(roads, ring[2], z); }
        for (int t = -4; t >= -9; t--) gravel(roads, Grid.worldX(ox, rot, cc.entranceX, t), Grid.worldZ(oz, rot, cc.entranceX, t));

        // Radars and hangars round it: a big dish at most, two hangars at most.
        int count = r.range(3, 6), bowls = 0, hangars = 0;
        Rng kr = r.sub("kinds");
        for (int i = 0; i < count; i++) {
            Rng pr = kr.sub(i);
            Radars.Kind kind = Radars.Kind.values()[pr.pick(3, 3, 2, 1, bowls == 0 ? 1.5 : 0, hangars < 2 ? 2 : 0)];
            if (kind == Radars.Kind.BOWL) bowls++;
            if (kind == Radars.Kind.HANGAR) hangars++;
            Grid g = Radars.build(kind, pr.sub("build"));
            int prot = pr.range(0, 3);
            for (int attempt = 0; attempt < 40; attempt++) {
                int[] gb = g.worldBounds(0, 0, prot);
                int gw = gb[2] - gb[0] + 1, gd = gb[3] - gb[1] + 1;
                if (gw > size - 2 || gd > size - 2) break;
                int px = x0 + 1 + pr.range(0, size - 2 - gw) - gb[0];
                int pz = z0 + 1 + pr.range(0, size - 2 - gd) - gb[1];
                int[] wb = g.worldBounds(px, pz, prot);
                if (clear(wb)) {
                    Part p = add(g, px, pz, prot);
                    spur(roads, (p.minX() + p.maxX()) / 2, (p.minZ() + p.maxZ()) / 2, ring);
                    break;
                }
            }
        }
    }

    private Part add(Grid g, int x, int z, int rot) {
        int[] wb = g.worldBounds(x, z, rot);
        Part p = new Part(g, x, z, rot, wb[0], wb[1], wb[2], wb[3]);
        parts.add(p);
        return p;
    }

    /** True if the box, with a gap round it, meets nothing placed yet (the roads aside). */
    private boolean clear(int[] wb) {
        for (int i = 1; i < parts.size(); i++) {
            Part p = parts.get(i);
            if (wb[2] + GAP >= p.minX() && wb[0] - GAP <= p.maxX() && wb[3] + GAP >= p.minZ() && wb[1] - GAP <= p.maxZ()) return false;
        }
        return true;
    }

    /** A gravel road three wide from (x, z) to the nearest point of the ring road: along x, then z. */
    private static void spur(Grid roads, int x, int z, int[] ring) {
        int tx = Math.max(ring[0], Math.min(ring[2], x)), tz = Math.max(ring[1], Math.min(ring[3], z));
        if (tx > ring[0] && tx < ring[2] && tz > ring[1] && tz < ring[3]) return;   // inside the ring
        for (int xx = Math.min(x, tx); xx <= Math.max(x, tx); xx++) gravel(roads, xx, z);
        for (int zz = Math.min(z, tz); zz <= Math.max(z, tz); zz++) gravel(roads, tx, zz);
    }

    private static void gravel(Grid roads, int x, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) roads.set(x + dx, -1, z + dz, "minecraft:gravel");
    }
}
