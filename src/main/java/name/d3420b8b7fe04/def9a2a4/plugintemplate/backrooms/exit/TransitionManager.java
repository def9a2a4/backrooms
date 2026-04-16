package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.advancement.AdvancementManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.BackroomsChunkGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.BackroomsLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.ConfigDrivenLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.SpawnFinder;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TransitionManager {

    private final JavaPlugin plugin;
    private final LevelRegistry levelRegistry;
    private final PlayerStateManager playerStateManager;
    private final AdvancementManager advancementManager;
    private final Set<UUID> transitioning = new HashSet<>();
    private BukkitTask tickTask;

    public TransitionManager(JavaPlugin plugin, LevelRegistry levelRegistry,
                             PlayerStateManager playerStateManager,
                             AdvancementManager advancementManager) {
        this.plugin = plugin;
        this.levelRegistry = levelRegistry;
        this.playerStateManager = playerStateManager;
        this.advancementManager = advancementManager;
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
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
                if (transitioning.contains(player.getUniqueId())) continue;
                if (player.getGameMode() == GameMode.SPECTATOR) continue;

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
        if (!transitioning.add(player.getUniqueId())) return; // Already transitioning

        // Reset all trigger counters (e.g. fall distance cumulative) immediately
        // so nothing accumulates further during the transition sequence.
        state.getCustomData().clear();

        String targetId = exit.getTargetLevelId();

        if ("overworld".equals(targetId)) {
            exit.playTransitionSequence(player, () -> {
                try {
                    String currentLevelId = currentLevel.getId();
                    String exitType = exit.getId();
                    returnToOverworld(player, state, currentLevel);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            advancementManager.grantExitHint(player, currentLevelId, "overworld", exitType);
                            advancementManager.grantEscape(player);
                        }
                    }, 1L);
                } finally {
                    transitioning.remove(player.getUniqueId());
                }
            });
            return;
        }

        BackroomsLevel targetLevel = levelRegistry.get(targetId);
        if (targetLevel == null) {
            plugin.getLogger().warning("Exit trigger references unknown level: " + targetId);
            transitioning.remove(player.getUniqueId());
            return;
        }

        World targetWorld = levelRegistry.getWorld(targetLevel);
        if (targetWorld == null) {
            plugin.getLogger().warning("Target level world not loaded: " + targetId);
            transitioning.remove(player.getUniqueId());
            return;
        }

        exit.playTransitionSequence(player, () -> {
            try {
                currentLevel.onPlayerLeave(player, state);
                Location spawn = findSpawnForLevel(targetLevel, targetWorld);
                player.teleport(spawn);
                SpawnFinder.clearFallDamage(player);
                state.setCurrentLevelId(targetId);
                targetLevel.onPlayerEnter(player, state);
                String fromLevelId = currentLevel.getId();
                String exitType = exit.getId();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        advancementManager.grantExitHint(player, fromLevelId, targetId, exitType);
                        advancementManager.grantLevelDiscovery(player, targetId);
                    }
                }, 1L);
            } finally {
                transitioning.remove(player.getUniqueId());
            }
        });
    }

    public void enterBackrooms(Player player, BackroomsPlayerState state, String targetLevelId) {
        BackroomsLevel targetLevel = levelRegistry.get(targetLevelId);
        if (targetLevel == null) return;

        World targetWorld = levelRegistry.getWorld(targetLevel);
        if (targetWorld == null) return;

        // Clear stale trigger state from any prior visit.
        state.getCustomData().clear();

        Location spawn = findSpawnForLevel(targetLevel, targetWorld);
        player.teleport(spawn);
        SpawnFinder.clearFallDamage(player);
        state.setCurrentLevelId(targetLevelId);
        targetLevel.onPlayerEnter(player, state);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                advancementManager.grantLevelDiscovery(player, targetLevelId);
            }
        }, 1L);
    }

    private Location findSpawnForLevel(BackroomsLevel level, World targetWorld) {
        Location fixedSpawn = targetWorld.getSpawnLocation();
        int radius = 300;
        int spawnY = fixedSpawn.getBlockY();

        if (level instanceof ConfigDrivenLevel cdl) {
            radius = cdl.getSpawnRadius();
        }

        ChunkGenerator gen = targetWorld.getGenerator();
        if (gen instanceof BackroomsChunkGenerator bGen) {
            spawnY = bGen.getSpawnY();
        }

        if (radius == 0) {
            return SpawnFinder.findSurfaceSpawn(targetWorld, 0, 0, spawnY);
        }
        return SpawnFinder.findRandomSpawn(targetWorld, radius, spawnY, fixedSpawn);
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
        SpawnFinder.clearFallDamage(player);
        playerStateManager.remove(player);
    }
}
