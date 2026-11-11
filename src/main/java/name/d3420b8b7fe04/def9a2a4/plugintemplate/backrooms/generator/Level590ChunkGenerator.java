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
 * <p>The tiling is shifted so world (0, 0) sits inside the sponge: on the floor of the largest
 * tunnel running along z (local x = t + t/2, y = t, z = 0 with t = size / 3). There, the local x
 * and y top digits are 1 (a hole the whole tunnel height) and the floor below has all y digits
 * 2 and all z digits 0, so it is always solid.
 */
public class Level590ChunkGenerator extends BackroomsChunkGenerator {

    private int order = 5;
    private int baseY = 6;
    private int size = 243;
    /** World x/z of a tile's local origin; chosen so world (0, 0) is the spawn column. */
    private int originX = -(81 + 40);
    private int originZ = 0;

    public Level590ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config != null) {
            // order >= 2 so the spawn tunnel is at least 3 blocks tall
            order = Math.max(2, Math.min(5, config.getInt("order", order)));
            baseY = config.getInt("base_y", baseY);
        }
        int s = 1;
        for (int i = 0; i < order; i++) s *= 3;
        size = s;
        int t = size / 3;
        originX = -(t + t / 2);
        originZ = 0;
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
                        chunkData.setBlock(x, y, z, Material.SPONGE);
                    }
                }
            }
        }
    }

    @Override
    public int getSpawnY() { return baseY + size / 3; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, getSpawnY(), 0.5);
    }
}
