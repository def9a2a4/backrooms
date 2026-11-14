package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level142ChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Fills in the sign text and chest loot of Level 142's Mace buildings when their chunk is first
 * generated: ChunkData can't hold tile-entity data, so the generator places the blocks and this
 * writes the rest on the next tick (the same two-phase pattern as Level101SignListener).
 */
public class Level142Listener implements Listener {

    private final JavaPlugin plugin;

    public Level142Listener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !(event.getWorld().getGenerator() instanceof Level142ChunkGenerator gen)) return;
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) gen.applyBlockEntities(chunk);
        });
    }
}
