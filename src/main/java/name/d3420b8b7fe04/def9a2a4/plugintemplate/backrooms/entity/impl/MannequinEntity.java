package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.BackroomsEntity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityUtil;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehavior;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehaviorRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class MannequinEntity implements BackroomsEntity {

    private final String instanceId;
    private final EntityBehaviorRegistry behaviorRegistry;

    private String skin = "";
    private double scale = 1.0;
    private double spawnChance = 0.12;
    private int minEscalationLevel = 0;
    private EntityBehavior behavior;

    public MannequinEntity(String instanceId, EntityBehaviorRegistry behaviorRegistry) {
        this.instanceId = instanceId;
        this.behaviorRegistry = behaviorRegistry;
    }

    @Override
    public String getId() {
        return instanceId;
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        skin = config.getString("skin", skin);
        scale = config.getDouble("scale", scale);
        spawnChance = config.getDouble("spawn_chance", spawnChance);
        minEscalationLevel = config.getInt("min_escalation", minEscalationLevel);

        String behaviorName = config.getString("behavior", "stationary_stare");
        behavior = behaviorRegistry.create(behaviorName);
        if (behavior != null) {
            behavior.loadConfig(config.getConfigurationSection(behaviorName));
        }
    }

    @Override
    public boolean shouldSpawn(Player player, BackroomsPlayerState state) {
        if (state.getEscalationLevel() < minEscalationLevel) return false;
        return ThreadLocalRandom.current().nextDouble() < spawnChance;
    }

    @Override
    public EntityHandle spawn(Location location, Player target) {
        ResolvableProfile profile = buildProfile(target);

        Mannequin mannequin = location.getWorld().spawn(location, Mannequin.class, m -> {
            m.setProfile(profile);
            m.setImmovable(true);
            m.setSilent(true);
            m.setInvulnerable(true);
            m.setAI(false);
            m.setGravity(false);
            m.setCollidable(false);
            m.setCustomNameVisible(false);
            m.setDescription(null);
            m.setPose(Pose.STANDING);
        });

        if (scale != 1.0) {
            var scaleAttr = mannequin.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scale);
            }
        }

        EntityHandle handle = new EntityHandle(
                getId(), UUID.randomUUID(), List.of(mannequin),
                target.getUniqueId(), location, target.getWorld().getFullTime());

        if (behavior != null) behavior.onSpawn(handle);
        return handle;
    }

    @Override
    public void despawn(EntityHandle handle) {
        if (behavior != null) behavior.onDespawn(handle);
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

        // Delegate to behavior
        if (behavior != null) {
            behavior.tick(handle, target);
            if (behavior.shouldDespawn(handle)) {
                despawn(handle);
                return;
            }
        }

        // Always face the player after behavior movement
        EntityUtil.facePlayer(primary, target);
    }

    private ResolvableProfile buildProfile(Player target) {
        if ("player".equalsIgnoreCase(skin)) {
            PlayerProfile pp = target.getPlayerProfile();
            return ResolvableProfile.resolvableProfile(pp);
        }
        PlayerProfile pp = EntityUtil.buildProfileFromUrl(skin);
        return ResolvableProfile.resolvableProfile(pp);
    }
}
