package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Triggers physics updates on water source blocks placed during Level 1 chunk
 * generation so they flow down from the ceiling.
 */
public class Level1WaterDripListener implements Listener {

    private static final int CEILING_Y = 18; // Level1ChunkGenerator.CEILING_MIN_Y

    private final JavaPlugin plugin;

    public Level1WaterDripListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        if (!event.getWorld().getName().equals("bkrms_1")) return;

        Chunk chunk = event.getChunk();
        List<int[]> waterPositions = new ArrayList<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (chunk.getBlock(x, CEILING_Y, z).getType() == Material.WATER) {
                    waterPositions.add(new int[]{x, z});
                }
            }
        }

        if (waterPositions.isEmpty()) return;

        // Defer to next tick so the chunk is fully loaded before triggering physics.
        // Must clear to AIR first — setType to the same material is a no-op in Bukkit.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int[] pos : waterPositions) {
                Block block = chunk.getBlock(pos[0], CEILING_Y, pos[1]);
                if (block.getType() == Material.WATER) {
                    block.setType(Material.AIR, false);
                    block.setType(Material.WATER, true);
                }
            }
        });
    }
}
