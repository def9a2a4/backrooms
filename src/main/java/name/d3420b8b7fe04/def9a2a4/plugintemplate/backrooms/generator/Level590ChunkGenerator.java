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
 * Level 590 — "The Sponge". A single Menger sponge of sponge blocks floating in the void.
 *
 * <p>The cube is 3^order blocks on a side (243 at the default order 5), with its minimum corner at
 * (origin_x, base_y, origin_z). A block is removed when, at any base-3 digit position, at least two
 * of its local x/y/z digits are 1 — the standard Menger construction, one block per unit cell.
 *
 * <p>The corner column at (origin_x, origin_z) is always solid (all digits 0), so spawn sits there
 * on the top face.
 */
public class Level590ChunkGenerator extends BackroomsChunkGenerator {

    private int order = 5;
    private int baseY = 6;
    private int originX = 0;
    private int originZ = 0;
    private int size = 243;

    public Level590ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config != null) {
            order = Math.max(1, Math.min(5, config.getInt("order", order)));
            baseY = config.getInt("base_y", baseY);
            originX = config.getInt("origin_x", originX);
            originZ = config.getInt("origin_z", originZ);
        }
        int s = 1;
        for (int i = 0; i < order; i++) s *= 3;
        size = s;
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
        if (baseX + 16 <= originX || baseX >= originX + size
                || baseZ + 16 <= originZ || baseZ >= originZ + size) {
            return;
        }

        int yMin = Math.max(baseY, chunkData.getMinHeight());
        int yMax = Math.min(baseY + size, chunkData.getMaxHeight());

        for (int x = 0; x < 16; x++) {
            int lx = baseX + x - originX;
            if (lx < 0 || lx >= size) continue;
            for (int z = 0; z < 16; z++) {
                int lz = baseZ + z - originZ;
                if (lz < 0 || lz >= size) continue;
                for (int y = yMin; y < yMax; y++) {
                    if (isSponge(lx, y - baseY, lz)) {
                        chunkData.setBlock(x, y, z, Material.SPONGE);
                    }
                }
            }
        }
    }

    @Override
    public int getSpawnY() { return baseY + size - 4; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, originX + 0.5, baseY + size, originZ + 0.5);
    }
}
