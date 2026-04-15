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

public class AttackBehavior implements EntityBehavior {

    private double chargeSpeed = 0.8;
    private double attackDistance = 2.0;
    private double damage = 10.0;

    private final Map<UUID, Boolean> despawnFlags = new HashMap<>();

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        chargeSpeed = config.getDouble("charge_speed", chargeSpeed);
        attackDistance = config.getDouble("attack_distance", attackDistance);
        damage = config.getDouble("damage", damage);
    }

    @Override
    public void tick(EntityHandle handle, Player player) {
        EntityUtil.moveToward(handle, player, chargeSpeed);

        double distance = EntityUtil.distanceTo(handle, player);
        if (distance < attackDistance) {
            player.damage(damage);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG,
                    SoundCategory.HOSTILE, 1.0f, 0.8f);
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
