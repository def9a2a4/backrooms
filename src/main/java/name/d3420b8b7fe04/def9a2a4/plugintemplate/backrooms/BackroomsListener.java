package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class BackroomsListener implements Listener {

    private final BackroomsManager manager;

    public BackroomsListener(BackroomsManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity().getWorld().equals(manager.getWorld())) {
            if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.getPlayer().getWorld().equals(manager.getWorld())) {
            event.setRespawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }
}
