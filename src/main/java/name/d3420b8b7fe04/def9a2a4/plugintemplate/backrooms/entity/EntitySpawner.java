package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.BackroomsLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EntitySpawner {

    private int maxEntitiesPerPlayer = 3;
    private int spawnCheckIntervalTicks = 100;
    // TODO: make spawn distances configurable per-level and per-entity-instance
    private int spawnDistanceMin = 30;
    private int spawnDistanceMax = 60;

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

    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        maxEntitiesPerPlayer = config.getInt("max_entities_per_player", maxEntitiesPerPlayer);
        spawnCheckIntervalTicks = config.getInt("spawn_check_interval_ticks", spawnCheckIntervalTicks);
        spawnDistanceMin = config.getInt("spawn_distance_min", spawnDistanceMin);
        spawnDistanceMax = config.getInt("spawn_distance_max", spawnDistanceMax);
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, spawnCheckIntervalTicks, 20L);
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

            // Check if target moved to a different world than the entity
            Entity primaryEntity = handle.bukkitEntities().isEmpty() ? null : handle.bukkitEntities().get(0);
            if (primaryEntity != null && !primaryEntity.isDead()
                    && !primaryEntity.getWorld().equals(target.getWorld())) {
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
                if (activeCount >= maxEntitiesPerPlayer) continue;

                BackroomsPlayerState state = playerStateManager.getOrCreate(player);

                for (String entityId : level.getEntityIds()) {
                    if (activeCount >= maxEntitiesPerPlayer) break;
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
        double distance = spawnDistanceMin + rng.nextDouble() * (spawnDistanceMax - spawnDistanceMin);
        double x = base.getX() + Math.cos(angle) * distance;
        double z = base.getZ() + Math.sin(angle) * distance;
        int playerY = base.getBlockY();
        World world = base.getWorld();

        // Scan for an air block near player Y level
        for (int dy = 0; dy <= 10; dy++) {
            for (int sign : new int[]{1, -1}) {
                int y = playerY + dy * sign;
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                if (world.getBlockAt((int) x, y, (int) z).getType().isAir()) {
                    return new Location(world, x, y, z);
                }
            }
        }
        return null;
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
