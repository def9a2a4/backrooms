package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Material;

/**
 * Picks a material per world column in large irregular patches: Voronoi cells
 * on a jittered grid, each cell hashed to a weighted material, with a simplex
 * domain warp so patch edges wander instead of running straight. Stateless and
 * deterministic in (seed, x, z), so patches continue seamlessly across chunks.
 */
final class PatchPalette {

    record Entry(Material material, int weight) {}

    static Entry entry(Material material, int weight) {
        return new Entry(material, weight);
    }

    // Warp relative to the cell size: low frequency and wide, so patch edges
    // bend in long curves instead of fraying back and forth.
    private static final double WARP_WAVELENGTH = 0.3;
    private static final double WARP_AMPLITUDE = 0.12;

    private final long salt;
    private final int cellSize;
    private final double warpScale;
    private final double warpAmplitude;
    private final Material[] materials;
    private final int[] cumulative;
    private final int totalWeight;

    PatchPalette(long salt, int cellSize, Entry... entries) {
        this.salt = salt;
        this.cellSize = cellSize;
        this.warpScale = 1.0 / (WARP_WAVELENGTH * cellSize);
        this.warpAmplitude = WARP_AMPLITUDE * cellSize;
        this.materials = new Material[entries.length];
        this.cumulative = new int[entries.length];
        int sum = 0;
        for (int i = 0; i < entries.length; i++) {
            materials[i] = entries[i].material();
            sum += entries[i].weight();
            cumulative[i] = sum;
        }
        this.totalWeight = sum;
    }

    Material pick(long seed, int worldX, int worldZ) {
        double wx = worldX * warpScale, wz = worldZ * warpScale;
        double fx = worldX + warpAmplitude * SimplexNoise.noise2(seed + salt, wx, wz);
        double fz = worldZ + warpAmplitude * SimplexNoise.noise2(seed + salt + 1, wx, wz);
        int cellX = Math.floorDiv((int) Math.floor(fx), cellSize);
        int cellZ = Math.floorDiv((int) Math.floor(fz), cellSize);

        double bestDistSq = Double.MAX_VALUE;
        long bestHash = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = hash(seed, cellX + dx, cellZ + dz);
                double px = (cellX + dx + (h & 0xFFFF) / 65536.0) * cellSize;
                double pz = (cellZ + dz + ((h >>> 16) & 0xFFFF) / 65536.0) * cellSize;
                double distSq = (px - fx) * (px - fx) + (pz - fz) * (pz - fz);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestHash = h;
                }
            }
        }

        int roll = (int) Math.floorMod(bestHash >>> 32, (long) totalWeight);
        for (int i = 0; i < cumulative.length; i++) {
            if (roll < cumulative[i]) return materials[i];
        }
        return materials[materials.length - 1];
    }

    private long hash(long seed, int cellX, int cellZ) {
        long h = seed ^ salt * 0x9E3779B97F4A7C15L;
        h ^= (long) cellX * 0x517CC1B727220A95L;
        h ^= (long) cellZ * 0x6C62272E07BB0142L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }
}
