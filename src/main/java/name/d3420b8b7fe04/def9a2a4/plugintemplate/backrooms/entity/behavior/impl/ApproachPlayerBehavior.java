package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehavior;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ApproachPlayerBehavior implements EntityBehavior {

    private final double speed;
    private final double maxAngleToBeObserved;

    public ApproachPlayerBehavior(double speed, double maxAngleToBeObserved) {
        this.speed = speed;
        this.maxAngleToBeObserved = maxAngleToBeObserved;
    }

    public ApproachPlayerBehavior(double speed) {
        this(speed, 30.0);
    }

    @Override
    public void tick(EntityHandle handle, Player nearestPlayer) {
        if (handle.bukkitEntities().isEmpty()) return;

        Entity primary = handle.bukkitEntities().get(0);
        if (primary.isDead()) return;

        // Don't move if player is looking at us
        Location entityLoc = primary.getLocation();
        Location playerEye = nearestPlayer.getEyeLocation();
        Vector toEntity = entityLoc.toVector().subtract(playerEye.toVector()).normalize();
        Vector lookDir = playerEye.getDirection().normalize();
        double angle = Math.toDegrees(lookDir.angle(toEntity));

        if (angle < maxAngleToBeObserved) return;

        // Move toward player
        Vector direction = nearestPlayer.getLocation().toVector()
                .subtract(entityLoc.toVector()).normalize().multiply(speed);
        Location newLoc = entityLoc.add(direction);
        newLoc.setYaw(entityLoc.getYaw());

        for (Entity e : handle.bukkitEntities()) {
            if (!e.isDead()) {
                e.teleport(e.getLocation().add(direction));
            }
        }
    }
}
