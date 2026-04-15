package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityUtil;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehavior;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FleeBehavior implements EntityBehavior {

    private double triggerDistance = 20.0;
    private double lookAngle = 30.0;
    private double fleeSpeed = 0.15;
    private double maxDistance = 60.0;

    private final Map<UUID, Boolean> despawnFlags = new HashMap<>();

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        triggerDistance = config.getDouble("trigger_distance", triggerDistance);
        lookAngle = config.getDouble("look_angle", lookAngle);
        fleeSpeed = config.getDouble("flee_speed", fleeSpeed);
        maxDistance = config.getDouble("max_distance", maxDistance);
    }

    @Override
    public void tick(EntityHandle handle, Player player) {
        if (handle.bukkitEntities().isEmpty()) return;
        var primary = handle.bukkitEntities().get(0);
        if (primary.isDead()) return;

        double distance = EntityUtil.distanceTo(handle, player);

        // Only flee when player is close AND looking
        if (distance < triggerDistance
                && EntityUtil.isPlayerLookingAt(player, primary.getLocation(), lookAngle)) {
            EntityUtil.moveAway(handle, player, fleeSpeed);
        }

        // Despawn when far enough
        if (distance > maxDistance) {
            despawnFlags.put(handle.instanceId(), true);
        }
    }

    @Override
    public boolean shouldDespawn(EntityHandle handle) {
        return Boolean.TRUE.equals(despawnFlags.get(handle.instanceId()));
    }

    @Override
    public void onDespawn(EntityHandle handle) {
        despawnFlags.remove(handle.instanceId());
    }
}
