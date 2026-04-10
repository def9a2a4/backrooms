package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.BackroomsLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EventScheduler {

    private final JavaPlugin plugin;
    private final LevelRegistry levelRegistry;
    private final EventRegistry eventRegistry;
    private final PlayerStateManager playerStateManager;

    private BukkitTask tickTask;

    // Per-player per-event last check tick
    private final Map<UUID, Map<String, Long>> lastCheckTicks = new HashMap<>();

    // Players currently in an active event (global cooldown)
    private final Set<UUID> playersInActiveEvent = new HashSet<>();

    public EventScheduler(JavaPlugin plugin, LevelRegistry levelRegistry,
                          EventRegistry eventRegistry, PlayerStateManager playerStateManager) {
        this.plugin = plugin;
        this.levelRegistry = levelRegistry;
        this.eventRegistry = eventRegistry;
        this.playerStateManager = playerStateManager;
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        lastCheckTicks.clear();
        playersInActiveEvent.clear();
    }

    public boolean isPlayerInEvent(UUID playerId) {
        return playersInActiveEvent.contains(playerId);
    }

    public void markPlayerInEvent(UUID playerId) {
        playersInActiveEvent.add(playerId);
    }

    public void unmarkPlayerInEvent(UUID playerId) {
        playersInActiveEvent.remove(playerId);
    }

    private void tick() {
        long currentTick = Bukkit.getCurrentTick();

        for (BackroomsLevel level : levelRegistry.getAll()) {
            World world = levelRegistry.getWorld(level);
            if (world == null) continue;

            var players = world.getPlayers();
            if (players.isEmpty()) continue;

            // Tick the level itself
            level.tick(world, players);

            // Process events for each player
            for (Player player : players) {
                BackroomsPlayerState state = playerStateManager.getOrCreate(player);
                state.addTicks(20); // 1 second worth of ticks

                for (String eventId : level.getEventIds()) {
                    BackroomsEvent event = eventRegistry.get(eventId);
                    if (event == null) continue;

                    // Apply this level's config to the shared event instance
                    event.loadConfig(level.getEventConfig(eventId));

                    // Check interval
                    long lastCheck = getLastCheck(player.getUniqueId(), eventId);
                    if (currentTick - lastCheck < event.getCheckIntervalTicks()) continue;
                    setLastCheck(player.getUniqueId(), eventId, currentTick);

                    if (event.canTrigger(player, state)) {
                        event.trigger(player, state);
                    }
                }
            }
        }
    }

    private long getLastCheck(UUID playerId, String eventId) {
        Map<String, Long> playerChecks = lastCheckTicks.get(playerId);
        if (playerChecks == null) return 0;
        return playerChecks.getOrDefault(eventId, 0L);
    }

    private void setLastCheck(UUID playerId, String eventId, long tick) {
        lastCheckTicks.computeIfAbsent(playerId, k -> new HashMap<>()).put(eventId, tick);
    }

    public void clearPlayer(UUID playerId) {
        lastCheckTicks.remove(playerId);
        playersInActiveEvent.remove(playerId);
    }
}
