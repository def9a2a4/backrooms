package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.AppearanceBuilder;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.BackroomsEntity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityUtil;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class HerobrineEntity implements BackroomsEntity {

    // White-eyed Steve skin (classic Herobrine texture)
    private static final String HEROBRINE_SKIN_URL =
            "https://textures.minecraft.net/texture/de507cfcfbd14d9fe9970f553a934f643c2a393dbc6b7d8ccee75909dbc4ebfa"; // herobrine (from skins.yml)

    private int minEscalationLevel = 1;
    private double spawnChance = 0.15;
    private double approachSpeed = 0.08;
    private double fleeAngle = 25.0;
    private double fleeDistance = 40.0;
    private double damageDistance = 5.0;
    private double damage = 4.0;

    @Override
    public String getId() {
        return "herobrine";
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        minEscalationLevel = config.getInt("min_escalation_level", minEscalationLevel);
        spawnChance = config.getDouble("spawn_chance", spawnChance);
        approachSpeed = config.getDouble("approach_speed", approachSpeed);
        fleeAngle = config.getDouble("flee_angle", fleeAngle);
        fleeDistance = config.getDouble("flee_distance", fleeDistance);
        damageDistance = config.getDouble("damage_distance", damageDistance);
        damage = config.getDouble("damage", damage);
    }

    @Override
    public boolean shouldSpawn(Player player, BackroomsPlayerState state) {
        if (state.getEscalationLevel() < minEscalationLevel) return false;
        return ThreadLocalRandom.current().nextDouble() < spawnChance;
    }

    @Override
    public EntityHandle spawn(Location location, Player target) {
        List<Entity> entities = new AppearanceBuilder()
                .armorStand()
                .invisible(true)
                .gravity(false)
                .playerHead(HEROBRINE_SKIN_URL)
                .build(location);

        EntityHandle handle = new EntityHandle(
                getId(),
                UUID.randomUUID(),
                entities,
                target.getUniqueId(),
                location,
                target.getWorld().getFullTime()
        );

        // Play a subtle ambient sound on spawn
        target.playSound(target.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.3f, 0.5f);

        return handle;
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

        Entity primary = handle.bukkitEntities().isEmpty() ? null : handle.bukkitEntities().get(0);
        if (primary == null || primary.isDead()) return;

        if (!primary.getWorld().equals(target.getWorld())) return;

        double distance = primary.getLocation().distance(target.getLocation());

        // If player is looking at Herobrine, vanish
        if (isPlayerLookingAt(target, primary.getLocation(), fleeAngle) && distance < fleeDistance) {
            despawn(handle);
            return;
        }

        // If very close and not looking, deal damage then vanish
        if (distance < damageDistance) {
            target.damage(damage);
            target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 0.6f, 0.7f);
            despawn(handle);
            return;
        }

        // Approach when not being observed
        if (!isPlayerLookingAt(target, primary.getLocation(), 30.0)) {
            Location entityLoc = primary.getLocation();
            Location targetLoc = target.getLocation();
            double dx = targetLoc.getX() - entityLoc.getX();
            double dz = targetLoc.getZ() - entityLoc.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.1) {
                dx /= len;
                dz /= len;
                Location newLoc = entityLoc.add(dx * approachSpeed, 0, dz * approachSpeed);
                // Face the player
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                newLoc.setYaw(yaw);
                for (Entity e : handle.bukkitEntities()) {
                    if (e != null && !e.isDead()) e.teleport(newLoc);
                }
            }
        } else {
            // Stare at the player while being observed (but not directly enough to flee)
            Location entityLoc = primary.getLocation();
            Location targetLoc = target.getLocation();
            double dx = targetLoc.getX() - entityLoc.getX();
            double dz = targetLoc.getZ() - entityLoc.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            Location rotated = entityLoc.clone();
            rotated.setYaw(yaw);
            for (Entity e : handle.bukkitEntities()) {
                if (e != null && !e.isDead()) e.teleport(rotated);
            }
        }
    }

    private boolean isPlayerLookingAt(Player player, Location target, double maxAngle) {
        return EntityUtil.isPlayerLookingAt(player, target, maxAngle);
    }
}
