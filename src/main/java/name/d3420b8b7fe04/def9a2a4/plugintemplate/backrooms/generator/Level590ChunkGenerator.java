package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Level 590 — "The Sponge". A Menger sponge of sponge blocks, tiled forever in x and z.
 *
 * <p>Each tile is 3^order blocks on a side (243 at the default order 5) and spans
 * y in [base_y, base_y + size). A block is removed when, at any base-3 digit position, at least two
 * of its local x/y/z digits are 1 — the standard Menger construction, one block per unit cell.
 * Tiles share faces, so the big tunnels of neighbouring tiles join into endless corridors.
 *
 * <p>The tiling is shifted so world (0, 0) sits deep in solid sponge, on the floor of a 9-wide
 * tunnel running along z, away from the big open voids. In base-3 digits (most significant first,
 * order 5): local x = 2 2 1 1 1 (the tunnel's center column), feet y = 2 0 1 0 0 (the tunnel's
 * floor level), z = 0. The x and y digits are both 1 only at the 9-block position, so the tunnel
 * is a hole there and nothing larger is; the floor (y = 2 0 0 2 2) has no paired 1s, so it is solid.
 */
public class Level590ChunkGenerator extends BackroomsChunkGenerator {

    private int order = 5;
    private int baseY = 6;
    private int size = 243;
    /** Local y of the spawn tunnel's floor-level air block; see the class comment. */
    private int spawnLy = 171;
    /** World x/z of a tile's local origin; chosen so world (0, 0) is the spawn column. */
    private int originX = -229;
    private int originZ = 0;

    public Level590ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config != null) {
            // order >= 3 so there is a 9-wide tunnel to spawn in
            order = Math.max(3, Math.min(5, config.getInt("order", order)));
            baseY = config.getInt("base_y", baseY);
        }
        int s = 1;
        for (int i = 0; i < order; i++) s *= 3;
        size = s;
        // x digits: 2 above the 9-block position, then 1 1 1. y: a leading 2, then 0 1 0 0.
        int lx = 13;
        for (int k = 3, p = 27; k < order; k++, p *= 3) lx += 2 * p;
        spawnLy = 9 + (order > 3 ? 2 * (size / 3) : 0);
        originX = -lx;
        originZ = 0;
    }

    /** Deterministic ~1/2000 roll for a wet-sponge defect at a world position. */
    private static boolean isWetDefect(long seed, int worldX, int y, int worldZ) {
        long h = seed
                ^ ((long) worldX * 341873128712L)
                ^ ((long) y * 132897987541L)
                ^ ((long) worldZ * 914494133L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return Math.floorMod(h, 2000) == 0;
    }

    /** True if local cube coordinate (lx, ly, lz), each in [0, size), is solid. */
    private boolean isSponge(int lx, int ly, int lz) {
        for (int i = 0; i < order; i++) {
            int ones = (lx % 3 == 1 ? 1 : 0) + (ly % 3 == 1 ? 1 : 0) + (lz % 3 == 1 ? 1 : 0);
            if (ones >= 2) return false;
            lx /= 3;
            ly /= 3;
            lz /= 3;
        }
        return true;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        int yMin = Math.max(baseY, chunkData.getMinHeight());
        int yMax = Math.min(baseY + size, chunkData.getMaxHeight());

        for (int x = 0; x < 16; x++) {
            int lx = Math.floorMod(baseX + x - originX, size);
            for (int z = 0; z < 16; z++) {
                int lz = Math.floorMod(baseZ + z - originZ, size);
                for (int y = yMin; y < yMax; y++) {
                    if (isSponge(lx, y - baseY, lz)) {
                        // ~1 in 2000 blocks is a defect: wet. Breaking it is the way
                        // out (break_block exit in level_590.yml). Hashed on world
                        // coordinates so defects don't repeat with the 243-block tile.
                        chunkData.setBlock(x, y, z,
                                isWetDefect(worldInfo.getSeed(), baseX + x, y, baseZ + z)
                                        ? Material.WET_SPONGE : Material.SPONGE);
                    }
                }
            }
        }
    }

    @Override
    public int getSpawnY() { return baseY + spawnLy; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, getSpawnY(), 0.5);
    }
}
