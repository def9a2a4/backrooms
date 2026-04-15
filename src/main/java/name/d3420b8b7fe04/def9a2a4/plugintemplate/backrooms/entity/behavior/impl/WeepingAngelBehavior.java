package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityUtil;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehavior;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WeepingAngelBehavior implements EntityBehavior {

    private double lookAngle = 30.0;
    private int approachIntervalTicks = 5;
    private double approachDistance = 2.5;
    private double closeDistance = 8.0;

    private final Map<UUID, State> states = new HashMap<>();

    private static class State {
        int cooldown = 0;
        boolean despawnRequested = false;
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        lookAngle = config.getDouble("look_angle", lookAngle);
        approachIntervalTicks = config.getInt("approach_interval_ticks", approachIntervalTicks);
        approachDistance = config.getDouble("approach_distance", approachDistance);
        closeDistance = config.getDouble("close_distance", closeDistance);
    }

    @Override
    public void tick(EntityHandle handle, Player player) {
        State state = states.computeIfAbsent(handle.instanceId(), k -> new State());

        if (handle.bukkitEntities().isEmpty()) return;
        var primary = handle.bukkitEntities().get(0);
        if (primary.isDead()) return;

        // Freeze when player is looking
        if (EntityUtil.isPlayerLookingAt(player, primary.getLocation(), lookAngle)) {
            return;
        }

        // Approach when not observed
        state.cooldown--;
        if (state.cooldown <= 0) {
            EntityUtil.moveToward(handle, player, approachDistance);
            state.cooldown = approachIntervalTicks;
        }

        // Despawn when close
        double distance = EntityUtil.distanceTo(handle, player);
        if (distance < closeDistance) {
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.HOSTILE, 0.4f, 1.0f);
            state.despawnRequested = true;
        }
    }

    @Override
    public boolean shouldDespawn(EntityHandle handle) {
        State state = states.get(handle.instanceId());
        return state != null && state.despawnRequested;
    }

    @Override
    public void onDespawn(EntityHandle handle) {
        states.remove(handle.instanceId());
    }
}
