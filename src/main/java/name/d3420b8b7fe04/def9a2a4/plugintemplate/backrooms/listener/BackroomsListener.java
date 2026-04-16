package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class BackroomsListener implements Listener {

    private final LevelRegistry levelRegistry;
    private final PlayerStateManager playerStateManager;

    public BackroomsListener(LevelRegistry levelRegistry, PlayerStateManager playerStateManager) {
        this.levelRegistry = levelRegistry;
        this.playerStateManager = playerStateManager;
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
            // Clear trigger-specific state so it doesn't carry over to future re-entries
            BackroomsPlayerState state = playerStateManager.get(event.getPlayer());
            if (state != null) {
                state.getCustomData().clear();
            }
        }
    }
}
