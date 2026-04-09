package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.BackroomsEntity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wrong Endermen for the Far Lands (Level 4).
 * They don't teleport. They don't move. They just stare at you
 * regardless of eye contact. That's what makes them wrong.
 */
public class WrongEndermanEntity implements BackroomsEntity {

    private double spawnChance = 0.3;
    private int stareGlowDuration = 40;
    private double screamDistance = 15.0;
    private double screamChance = 0.05;

    @Override
    public String getId() {
        return "wrong_enderman";
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        spawnChance = config.getDouble("spawn_chance", spawnChance);
        stareGlowDuration = config.getInt("stare_glow_duration", stareGlowDuration);
        screamDistance = config.getDouble("scream_distance", screamDistance);
        screamChance = config.getDouble("scream_chance", screamChance);
    }

    @Override
    public boolean shouldSpawn(Player player, BackroomsPlayerState state) {
        return ThreadLocalRandom.current().nextDouble() < spawnChance;
    }

    @Override
    public EntityHandle spawn(Location location, Player target) {
        Enderman enderman = location.getWorld().spawn(location, Enderman.class, e -> {
            e.setAI(false);
            e.setGravity(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            // Prevent teleportation
            e.setTarget(null);
        });

        return new EntityHandle(
                getId(),
                UUID.randomUUID(),
                List.of(enderman),
                target.getUniqueId(),
                location,
                target.getWorld().getFullTime()
        );
    }

    @Override
    public void despawn(EntityHandle handle) {
        for (Entity e : handle.bukkitEntities()) {
            if (e != null && !e.isDead()) e.remove();
        }
    }

    @Override
    public void tick(EntityHandle handle) {
        Player target = Bukkit.getPlayer(handle.targetPlayerUuid());
        if (target == null || !target.isOnline()) return;

        for (Entity e : handle.bukkitEntities()) {
            if (e == null || e.isDead()) continue;
            if (!(e instanceof Enderman enderman)) continue;

            // Always face the player — the wrong part
            Location entityLoc = enderman.getLocation();
            Location playerLoc = target.getLocation();
            double dx = playerLoc.getX() - entityLoc.getX();
            double dz = playerLoc.getZ() - entityLoc.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            entityLoc.setYaw(yaw);
            enderman.teleport(entityLoc);

            double distance = entityLoc.distance(playerLoc);

            // Apply GLOWING to the player when they're being stared at within range
            if (distance < screamDistance) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.GLOWING, stareGlowDuration, 0, true, false, false));
            }

            // Occasional full-volume scream from nowhere
            if (distance < screamDistance * 2 && ThreadLocalRandom.current().nextDouble() < screamChance) {
                target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM,
                        SoundCategory.HOSTILE, 1.0f, 0.6f);
            }
        }
    }
}
