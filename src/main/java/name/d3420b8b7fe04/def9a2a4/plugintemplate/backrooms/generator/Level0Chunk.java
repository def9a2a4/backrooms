package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/**
 * Per-chunk working state shared by the Level 0 generator passes: what each
 * column's surfaces are made of, where its floor is, and where its grid light
 * hangs. Chunk-local x/z throughout; world coordinates via {@link #wx}/{@link #wz}.
 */
final class Level0Chunk {

    static final int NO_LIGHT = Integer.MIN_VALUE;

    final ChunkData data;
    final long seed;
    final int chunkX, chunkZ;

    final boolean[][] classic = new boolean[16][16];
    final Material[][] floorMats = new Material[16][16];
    final Material[][] wallMats = new Material[16][16];
    final int[][] floorTop = new int[16][16];       // y of the floor surface block
    final boolean[][] noCarpet = new boolean[16][16];
    final int[][] lightY = new int[16][16];         // grid light height, NO_LIGHT off-grid
    boolean anyClassic;

    Level0Chunk(ChunkData data, long seed, int chunkX, int chunkZ) {
        this.data = data;
        this.seed = seed;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    int wx(int x) { return chunkX * 16 + x; }
    int wz(int z) { return chunkZ * 16 + z; }

    /** Chunk-local x of world x, or -1 when it falls outside this chunk. */
    int lx(int worldX) {
        int x = worldX - chunkX * 16;
        return x >= 0 && x < 16 ? x : -1;
    }

    int lz(int worldZ) {
        int z = worldZ - chunkZ * 16;
        return z >= 0 && z < 16 ? z : -1;
    }

    /** Sets a block by world x/z; ignored outside this chunk. */
    void setWorld(int worldX, int y, int worldZ, Material material) {
        int x = lx(worldX), z = lz(worldZ);
        if (x >= 0 && z >= 0) data.setBlock(x, y, z, material);
    }

    void setWorld(int worldX, int y, int worldZ, org.bukkit.block.data.BlockData blockData) {
        int x = lx(worldX), z = lz(worldZ);
        if (x >= 0 && z >= 0) data.setBlock(x, y, z, blockData);
    }

    /** Clears the room band (y65-68) of a column to air. */
    void clearRoom(int x, int z) {
        for (int y = Level0ChunkGenerator.AIR_MIN_Y; y < Level0ChunkGenerator.CEILING_MIN_Y; y++) {
            data.setBlock(x, y, z, Material.AIR);
        }
    }

    static Stairs stairs(Material material, BlockFace facing, Stairs.Half half) {
        Stairs stairs = (Stairs) material.createBlockData();
        stairs.setFacing(facing);
        stairs.setHalf(half);
        return stairs;
    }

    static Slab slab(Material material, Slab.Type type) {
        Slab slab = (Slab) material.createBlockData();
        slab.setType(type);
        return slab;
    }

    static long hash(long seed, int a, int b, long salt) {
        long h = seed ^ salt * 0x9E3779B97F4A7C15L;
        h ^= (long) a * 0x517CC1B727220A95L;
        h ^= (long) b * 0x6C62272E07BB0142L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    /** Uniform int in [0, bound) from bits of a hash. */
    static int pick(long hash, int shift, int bound) {
        return (int) Math.floorMod(hash >>> shift, (long) bound);
    }
}
