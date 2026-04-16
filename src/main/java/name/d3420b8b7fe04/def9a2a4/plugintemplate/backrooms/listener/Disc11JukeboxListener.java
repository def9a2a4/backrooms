package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.advancement.AdvancementManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Level 5 (Disc 11) — clicking jukeboxes causes them to explode.
 * L5 is a dead end; the only way out is death (respawn at L0).
 */
public class Disc11JukeboxListener implements Listener {

    private final AdvancementManager advancementManager;

    public Disc11JukeboxListener(AdvancementManager advancementManager) {
        this.advancementManager = advancementManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        if (block.getType() != Material.JUKEBOX) return;
        if (!block.getWorld().getName().startsWith("bkrms_5")) return;

        Player player = event.getPlayer();
        if (player.isDead()) return;

        event.setCancelled(true);
        block.setType(Material.AIR);
        block.getWorld().createExplosion(block.getLocation(), 6.0f, false, true);
        advancementManager.grantDisc11Hint(player);
        // Guaranteed kill regardless of armor/health
        player.setHealth(0.0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!event.getEntity().getWorld().getName().startsWith("bkrms_5")) return;
        event.setDeathMessage(event.getEntity().getName() + " heard something");
    }
}
