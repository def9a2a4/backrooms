package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity;

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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EntitySpawner {

    private static final int MAX_ENTITIES_PER_PLAYER = 3;
    private static final int SPAWN_CHECK_INTERVAL_TICKS = 100;

    private final JavaPlugin plugin;
    private final LevelRegistry levelRegistry;
    private final EntityRegistry entityRegistry;
    private final PlayerStateManager playerStateManager;

    private final List<EntityHandle> activeEntities = new ArrayList<>();
    private BukkitTask tickTask;

    public EntitySpawner(JavaPlugin plugin, LevelRegistry levelRegistry,
                         EntityRegistry entityRegistry, PlayerStateManager playerStateManager) {
        this.plugin = plugin;
        this.levelRegistry = levelRegistry;
        this.entityRegistry = entityRegistry;
        this.playerStateManager = playerStateManager;
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, SPAWN_CHECK_INTERVAL_TICKS, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        despawnAll();
    }

    public void despawnAll() {
        for (EntityHandle handle : activeEntities) {
            BackroomsEntity type = entityRegistry.get(handle.entityId());
            if (type != null) {
                type.despawn(handle);
            }
        }
        activeEntities.clear();
    }

    private void tick() {
        // Clean up stale entities
        Iterator<EntityHandle> iter = activeEntities.iterator();
        while (iter.hasNext()) {
            EntityHandle handle = iter.next();
            Player target = Bukkit.getPlayer(handle.targetPlayerUuid());
            if (target == null || !target.isOnline()) {
                BackroomsEntity type = entityRegistry.get(handle.entityId());
                if (type != null) type.despawn(handle);
                iter.remove();
                continue;
            }

            // Check if target left the backrooms
            if (!levelRegistry.isBackroomsWorld(target.getWorld())) {
                BackroomsEntity type = entityRegistry.get(handle.entityId());
                if (type != null) type.despawn(handle);
                iter.remove();
                continue;
            }

            // Tick the entity AI
            BackroomsEntity type = entityRegistry.get(handle.entityId());
            if (type != null) {
                type.tick(handle);
            }
        }

        // Try to spawn new entities
        for (BackroomsLevel level : levelRegistry.getAll()) {
            World world = levelRegistry.getWorld(level);
            if (world == null) continue;

            for (Player player : world.getPlayers()) {
                int activeCount = countActiveFor(player.getUniqueId());
                if (activeCount >= MAX_ENTITIES_PER_PLAYER) continue;

                BackroomsPlayerState state = playerStateManager.getOrCreate(player);

                for (String entityId : level.getEntityIds()) {
                    if (activeCount >= MAX_ENTITIES_PER_PLAYER) break;
                    BackroomsEntity entity = entityRegistry.get(entityId);
                    if (entity == null) continue;
                    if (!entity.shouldSpawn(player, state)) continue;

                    Location spawnLoc = findSpawnLocation(player);
                    if (spawnLoc == null) continue;

                    EntityHandle handle = entity.spawn(spawnLoc, player);
                    if (handle != null) {
                        activeEntities.add(handle);
                        activeCount++;
                    }
                }
            }
        }
    }

    private int countActiveFor(UUID playerId) {
        int count = 0;
        for (EntityHandle handle : activeEntities) {
            if (handle.targetPlayerUuid().equals(playerId)) count++;
        }
        return count;
    }

    private Location findSpawnLocation(Player player) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location base = player.getLocation();
        double angle = rng.nextDouble() * 2 * Math.PI;
        double distance = 30 + rng.nextDouble() * 30; // 30-60 blocks away
        double x = base.getX() + Math.cos(angle) * distance;
        double z = base.getZ() + Math.sin(angle) * distance;
        return new Location(base.getWorld(), x, base.getY(), z);
    }

    public EntityHandle spawnFor(Player player, BackroomsEntity entity) {
        Location spawnLoc = findSpawnLocation(player);
        if (spawnLoc == null) return null;
        EntityHandle handle = entity.spawn(spawnLoc, player);
        if (handle != null) activeEntities.add(handle);
        return handle;
    }

    public List<EntityHandle> getActiveEntities() {
        return activeEntities;
    }
}
