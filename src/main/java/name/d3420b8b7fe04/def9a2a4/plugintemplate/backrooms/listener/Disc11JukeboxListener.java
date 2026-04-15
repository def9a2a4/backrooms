package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Level 5 (Disc 11) — clicking jukeboxes causes them to explode.
 * L5 is a dead end; the only way out is death (respawn at L0).
 */
public class Disc11JukeboxListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        if (block.getType() != Material.JUKEBOX) return;
        if (!block.getWorld().getName().startsWith("bkrms_5")) return;

        event.setCancelled(true);
        block.setType(Material.AIR);
        block.getWorld().createExplosion(block.getLocation(), 6.0f, false, true);
        // Guaranteed kill regardless of armor/health
        event.getPlayer().setHealth(0.0);
    }
}
