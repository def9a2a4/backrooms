package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.BackroomsLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Routes Bukkit events to event-based ExitTriggers.
 * Only fires for players currently in backrooms worlds.
 */
public class ExitEventListener implements Listener {

    private final LevelRegistry levelRegistry;
    private final PlayerStateManager playerStateManager;
    private final TransitionManager transitionManager;

    public ExitEventListener(LevelRegistry levelRegistry,
                             PlayerStateManager playerStateManager,
                             TransitionManager transitionManager) {
        this.levelRegistry = levelRegistry;
        this.playerStateManager = playerStateManager;
        this.transitionManager = transitionManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        route(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            route(event, player);
        }
    }

    private void route(Event event, Player player) {
        if (!levelRegistry.isBackroomsWorld(player.getWorld())) return;

        World world = player.getWorld();
        BackroomsLevel level = levelRegistry.getByWorld(world);
        if (level == null) return;

        BackroomsPlayerState state = playerStateManager.getOrCreate(player);

        for (ExitTrigger exit : level.getExitTriggers()) {
            if (exit.getListenedEvents().isEmpty()) continue;
            if (!exit.getListenedEvents().contains(event.getClass())) continue;

            if (exit.checkEvent(event, player, state)) {
                transitionManager.performTransition(player, state, level, exit);
                break;
            }
        }
    }
}
