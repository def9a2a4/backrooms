package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit;

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
    private final Set<UUID> transitioning = new HashSet<>();
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
                if (transitioning.contains(player.getUniqueId())) continue;

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

        String targetId = exit.getTargetLevelId();

        if ("overworld".equals(targetId)) {
            exit.playTransitionSequence(player, () -> {
                try {
                    returnToOverworld(player, state, currentLevel);
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
                state.setCurrentLevelId(targetId);
                targetLevel.onPlayerEnter(player, state);
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

        Location spawn = findSpawnForLevel(targetLevel, targetWorld);
        player.teleport(spawn);
        state.setCurrentLevelId(targetLevelId);
        targetLevel.onPlayerEnter(player, state);
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
        playerStateManager.remove(player);
    }
}
