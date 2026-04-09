package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class BackroomsListener implements Listener {

    private final LevelRegistry levelRegistry;

    public BackroomsListener(LevelRegistry levelRegistry) {
        this.levelRegistry = levelRegistry;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (levelRegistry.isBackroomsWorld(event.getEntity().getWorld())) {
            if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (levelRegistry.isBackroomsWorld(event.getPlayer().getWorld())) {
            // Respawn in overworld instead of backrooms
            event.setRespawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }
}
