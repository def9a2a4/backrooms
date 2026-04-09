package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.BackroomsEntity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Pursuer — an invisible entity in the Disc 11 tunnels.
 * Represented only by sound: footsteps behind the player.
 * When the player stops moving, the footsteps continue a few steps then stop.
 * If the player stands still too long, it closes distance and deals damage.
 */
public class PursuerEntity implements BackroomsEntity {

    private double spawnChance = 0.2;
    private double followDistance = 30.0;
    private double approachSpeed = 0.15;
    private double damageDistance = 5.0;
    private double damage = 1.0;
    private int standStillTicksBeforeApproach = 100; // 5 seconds
    private int footstepSoundInterval = 15;

    // Per-entity tracking (stored as custom data key in player state)
    private long lastFootstepTick = 0;

    @Override
    public String getId() {
        return "pursuer";
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        spawnChance = config.getDouble("spawn_chance", spawnChance);
        followDistance = config.getDouble("follow_distance", followDistance);
        approachSpeed = config.getDouble("approach_speed", approachSpeed);
        damageDistance = config.getDouble("damage_distance", damageDistance);
        damage = config.getDouble("damage", damage);
        standStillTicksBeforeApproach = config.getInt("stand_still_ticks", standStillTicksBeforeApproach);
        footstepSoundInterval = config.getInt("footstep_interval_ticks", footstepSoundInterval);
    }

    @Override
    public boolean shouldSpawn(Player player, BackroomsPlayerState state) {
        return ThreadLocalRandom.current().nextDouble() < spawnChance;
    }

    @Override
    public EntityHandle spawn(Location location, Player target) {
        // No visible entity — the pursuer is purely sound-based.
        // We use the EntityHandle with empty entity list to track position.
        Location behindPlayer = getBehindPlayer(target, followDistance);

        return new EntityHandle(
                getId(),
                UUID.randomUUID(),
                Collections.emptyList(),
                target.getUniqueId(),
                behindPlayer,
                target.getWorld().getFullTime()
        );
    }

    @Override
    public void despawn(EntityHandle handle) {
        // No entities to remove
    }

    @Override
    public void tick(EntityHandle handle) {
        Player target = Bukkit.getPlayer(handle.targetPlayerUuid());
        if (target == null || !target.isOnline()) return;

        long currentTick = target.getWorld().getFullTime();

        // Calculate pursuer position: always behind the player
        Location playerLoc = target.getLocation();
        Location pursuerLoc = handle.spawnLocation();

        // Move pursuer to maintain position behind player
        Location idealPos = getBehindPlayer(target, followDistance);
        double dx = idealPos.getX() - pursuerLoc.getX();
        double dz = idealPos.getZ() - pursuerLoc.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist > 1.0) {
            double speed = Math.min(approachSpeed, dist);
            pursuerLoc.add(dx / dist * speed, 0, dz / dist * speed);
        }

        // Check if player is standing still
        boolean playerMoving = target.getVelocity().lengthSquared() > 0.01;

        if (!playerMoving) {
            // Player stopped — pursuer closes in
            long ticksSinceSpawn = currentTick - handle.spawnTick();
            if (ticksSinceSpawn > standStillTicksBeforeApproach) {
                // Close distance
                double toPx = playerLoc.getX() - pursuerLoc.getX();
                double toPz = playerLoc.getZ() - pursuerLoc.getZ();
                double toPdist = Math.sqrt(toPx * toPx + toPz * toPz);
                if (toPdist > damageDistance) {
                    pursuerLoc.add(toPx / toPdist * approachSpeed * 2, 0, toPz / toPdist * approachSpeed * 2);
                } else {
                    // Close enough — deal damage
                    target.damage(damage);
                }
            }
        }

        // Play footstep sounds at intervals
        if (currentTick - lastFootstepTick >= footstepSoundInterval) {
            lastFootstepTick = currentTick;
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            float pitch = 0.6f + rng.nextFloat() * 0.3f;
            target.playSound(pursuerLoc, Sound.BLOCK_STONE_STEP, SoundCategory.HOSTILE, 0.7f, pitch);

            // Occasional heavier footstep
            if (rng.nextDouble() < 0.2) {
                target.playSound(pursuerLoc, Sound.BLOCK_DEEPSLATE_STEP, SoundCategory.HOSTILE, 0.9f, 0.5f);
            }
        }

        // Update spawn location to track position (EntityHandle is a record so we track via the mutable Location)
        handle.spawnLocation().setX(pursuerLoc.getX());
        handle.spawnLocation().setY(pursuerLoc.getY());
        handle.spawnLocation().setZ(pursuerLoc.getZ());
    }

    private Location getBehindPlayer(Player player, double distance) {
        Location loc = player.getLocation();
        // Behind = opposite of where they're looking
        float yaw = loc.getYaw();
        double rad = Math.toRadians(yaw);
        // Player looks in -sin(yaw), cos(yaw) direction, so behind is +sin(yaw), -cos(yaw)
        double behindX = loc.getX() + Math.sin(rad) * distance;
        double behindZ = loc.getZ() - Math.cos(rad) * distance;
        return new Location(loc.getWorld(), behindX, loc.getY(), behindZ);
    }
}
