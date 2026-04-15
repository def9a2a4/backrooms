package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.advancement.AdvancementManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.TransitionManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class EntryManager implements Listener {

    private final JavaPlugin plugin;
    private final EntryTriggerRegistry triggerRegistry;
    private final LevelRegistry levelRegistry;
    private final PlayerStateManager playerStateManager;
    private final TransitionManager transitionManager;
    private final AdvancementManager advancementManager;

    public EntryManager(JavaPlugin plugin, EntryTriggerRegistry triggerRegistry,
                        LevelRegistry levelRegistry, PlayerStateManager playerStateManager,
                        TransitionManager transitionManager,
                        AdvancementManager advancementManager) {
        this.plugin = plugin;
        this.triggerRegistry = triggerRegistry;
        this.levelRegistry = levelRegistry;
        this.playerStateManager = playerStateManager;
        this.transitionManager = transitionManager;
        this.advancementManager = advancementManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            route(event, player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        route(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBedEnter(PlayerBedEnterEvent event) {
        route(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        route(event, event.getPlayer());
    }

    private void route(Event event, Player player) {
        // Don't trigger if already in backrooms
        if (levelRegistry.isBackroomsWorld(player.getWorld())) return;

        for (EntryTrigger trigger : triggerRegistry.getAll()) {
            if (!trigger.getEnabledWorlds().contains(player.getWorld().getName())) continue;
            if (!trigger.getListenedEvents().contains(event.getClass())) continue;

            String targetLevel = trigger.evaluate(event, player);
            if (targetLevel != null) {
                BackroomsPlayerState state = playerStateManager.getOrCreate(player);
                state.setReturnLocation(player.getLocation());
                trigger.playEntrySequence(player, () -> {
                    transitionManager.enterBackrooms(player, state, targetLevel);
                    // Delay advancements by 1 tick so the client finishes processing the dimension change
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            advancementManager.grantEntry(player, trigger.getId());
                            advancementManager.grantLevelDiscovery(player, targetLevel);
                        }
                    }, 1L);
                });
                break;
            }
        }
    }
}
