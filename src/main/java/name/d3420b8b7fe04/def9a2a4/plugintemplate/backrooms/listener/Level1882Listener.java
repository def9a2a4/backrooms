package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.book.ShelfBooks;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level1882ChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Fills in Level 1882's museum labels, lectern books, pot sherds and paintings when their chunk is
 * first generated: ChunkData can't hold block-entity data or entities, so the generator places the
 * blocks and this writes the rest on the next tick (as Level142Listener does).
 */
public class Level1882Listener implements Listener {

    private final JavaPlugin plugin;

    public Level1882Listener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !(event.getWorld().getGenerator() instanceof Level1882ChunkGenerator gen)) return;
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) gen.applyBlockEntities(chunk);
        });
    }

    /** Before a book comes off a library shelf, the shelf's stand-ins become real books. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onShelfUse(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.useInteractedBlock() != org.bukkit.event.Event.Result.DENY) {
            ShelfBooks.shelve(event.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShelfBreak(BlockBreakEvent event) {
        ShelfBooks.shelve(event.getBlock());
    }
}
