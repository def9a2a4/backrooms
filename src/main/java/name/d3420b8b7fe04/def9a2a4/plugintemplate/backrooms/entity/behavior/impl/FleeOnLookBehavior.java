package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehavior;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class FleeOnLookBehavior implements EntityBehavior {

    private final double maxAngleDegrees;
    private final double maxDistance;

    public FleeOnLookBehavior(double maxAngleDegrees, double maxDistance) {
        this.maxAngleDegrees = maxAngleDegrees;
        this.maxDistance = maxDistance;
    }

    @Override
    public void tick(EntityHandle handle, Player nearestPlayer) {
        if (handle.bukkitEntities().isEmpty()) return;

        Entity primary = handle.bukkitEntities().get(0);
        if (primary.isDead()) return;

        Location entityLoc = primary.getLocation();
        Location playerLoc = nearestPlayer.getEyeLocation();
        double distance = entityLoc.distance(playerLoc);

        if (distance > maxDistance) return;

        Vector toEntity = entityLoc.toVector().subtract(playerLoc.toVector()).normalize();
        Vector lookDir = playerLoc.getDirection().normalize();
        double angle = Math.toDegrees(lookDir.angle(toEntity));

        if (angle < maxAngleDegrees) {
            // Player is looking at us - despawn
            for (Entity e : handle.bukkitEntities()) {
                if (!e.isDead()) e.remove();
            }
        }
    }
}
