package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityUtil;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehavior;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StalkerBehavior implements EntityBehavior {

    private double approachSpeed = 0.06;
    private double despawnDistance = 5.0;

    private final Map<UUID, Boolean> despawnFlags = new HashMap<>();

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        approachSpeed = config.getDouble("approach_speed", approachSpeed);
        despawnDistance = config.getDouble("despawn_distance", despawnDistance);
    }

    @Override
    public void tick(EntityHandle handle, Player player) {
        EntityUtil.moveToward(handle, player, approachSpeed);

        double distance = EntityUtil.distanceTo(handle, player);
        if (distance < despawnDistance) {
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
