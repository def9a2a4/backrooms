package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds a level's grid lights so light events toggle exactly the blocks that
 * are lights. Each grid column is scanned from {@code lightY} down to
 * {@code lightYMin}, which also catches pendant lights hung below a ceiling.
 */
final class LightGrid {

    private LightGrid() {}

    static List<Block> find(Location center, int radius, int spacing,
                            int lightY, int lightYMin, Material lightMaterial) {
        List<Block> lights = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return lights;

        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        for (int x = cx - radius; x <= cx + radius; x++) {
            if (x % spacing != 0) continue;
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (z % spacing != 0) continue;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = lightY; y >= lightYMin; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == lightMaterial) lights.add(block);
                }
            }
        }
        return lights;
    }

    static void set(List<Block> lights, Material material) {
        for (Block block : lights) {
            if (block.getChunk().isLoaded()) {
                block.setType(material, false);
            }
        }
    }
}
