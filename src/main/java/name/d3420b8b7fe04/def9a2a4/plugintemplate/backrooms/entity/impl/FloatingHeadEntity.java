package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.AppearanceBuilder;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.BackroomsEntity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityUtil;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehavior;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehaviorRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FloatingHeadEntity implements BackroomsEntity {

    private final String instanceId;
    private final EntityBehaviorRegistry behaviorRegistry;

    private String skin = "";
    private float scale = 1.0f;
    private double spawnChance = 0.15;
    private int minEscalationLevel = 0;
    private EntityBehavior behavior;

    public FloatingHeadEntity(String instanceId, EntityBehaviorRegistry behaviorRegistry) {
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
        scale = (float) config.getDouble("scale", scale);
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
        ItemStack head = buildHead(target);

        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class, id -> {
            id.setItemStack(head);
            id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            id.setGravity(false);
            id.setSilent(true);
            id.setInvulnerable(true);
            if (scale != 1.0f) {
                id.setTransformation(new Transformation(
                        new Vector3f(), new AxisAngle4f(),
                        new Vector3f(scale, scale, scale), new AxisAngle4f()));
            }
        });

        EntityHandle handle = new EntityHandle(
                getId(), UUID.randomUUID(), List.of(display),
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

        // Face the player
        EntityUtil.facePlayer(primary, target);

        // Delegate to behavior
        if (behavior != null) {
            behavior.tick(handle, target);
            if (behavior.shouldDespawn(handle)) {
                despawn(handle);
            }
        }
    }

    private ItemStack buildHead(Player target) {
        if ("player".equalsIgnoreCase(skin)) {
            return buildHeadFromPlayer(target);
        }
        return AppearanceBuilder.createPlayerHead(skin);
    }

    @SuppressWarnings("deprecation")
    private ItemStack buildHeadFromPlayer(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwnerProfile(player.getPlayerProfile());
            head.setItemMeta(meta);
        }
        return head;
    }
}
