package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.BackroomsLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class TransitionManager {

    private final JavaPlugin plugin;
    private final LevelRegistry levelRegistry;
    private final PlayerStateManager playerStateManager;
    private BukkitTask tickTask;

    public TransitionManager(JavaPlugin plugin, LevelRegistry levelRegistry,
                             PlayerStateManager playerStateManager) {
        this.plugin = plugin;
        this.levelRegistry = levelRegistry;
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
    }

    private void tick() {
        for (BackroomsLevel level : levelRegistry.getAll()) {
            World world = levelRegistry.getWorld(level);
            if (world == null) continue;

            for (Player player : world.getPlayers()) {
                BackroomsPlayerState state = playerStateManager.getOrCreate(player);

                for (ExitTrigger exit : level.getExitTriggers()) {
                    if (exit.check(player, state)) {
                        performTransition(player, state, level, exit);
                        break;
                    }
                }
            }
        }
    }

    public void performTransition(Player player, BackroomsPlayerState state,
                                  BackroomsLevel currentLevel, ExitTrigger exit) {
        String targetId = exit.getTargetLevelId();

        if ("overworld".equals(targetId)) {
            exit.playTransitionSequence(player, () -> returnToOverworld(player, state, currentLevel));
            return;
        }

        BackroomsLevel targetLevel = levelRegistry.get(targetId);
        if (targetLevel == null) {
            plugin.getLogger().warning("Exit trigger references unknown level: " + targetId);
            return;
        }

        World targetWorld = levelRegistry.getWorld(targetLevel);
        if (targetWorld == null) {
            plugin.getLogger().warning("Target level world not loaded: " + targetId);
            return;
        }

        exit.playTransitionSequence(player, () -> {
            currentLevel.onPlayerLeave(player, state);
            Location spawn = targetWorld.getSpawnLocation();
            player.teleport(spawn);
            state.setCurrentLevelId(targetId);
            targetLevel.onPlayerEnter(player, state);
        });
    }

    public void enterBackrooms(Player player, BackroomsPlayerState state, String targetLevelId) {
        BackroomsLevel targetLevel = levelRegistry.get(targetLevelId);
        if (targetLevel == null) return;

        World targetWorld = levelRegistry.getWorld(targetLevel);
        if (targetWorld == null) return;

        Location spawn = targetWorld.getSpawnLocation();
        player.teleport(spawn);
        state.setCurrentLevelId(targetLevelId);
        targetLevel.onPlayerEnter(player, state);
    }

    public void returnToOverworld(Player player, BackroomsPlayerState state,
                                  BackroomsLevel currentLevel) {
        if (currentLevel != null) {
            currentLevel.onPlayerLeave(player, state);
        }

        Location returnLoc = state.getReturnLocation();
        if (returnLoc == null) {
            returnLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
        }

        player.teleport(returnLoc);
        playerStateManager.remove(player);
    }
}
