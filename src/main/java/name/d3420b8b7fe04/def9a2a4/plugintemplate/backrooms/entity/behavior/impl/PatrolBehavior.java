package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehavior;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class PatrolBehavior implements EntityBehavior {

    private final double speed;
    private final int directionChangeTicks;
    private Vector currentDirection;
    private long lastDirectionChange = 0;

    public PatrolBehavior(double speed, int directionChangeTicks) {
        this.speed = speed;
        this.directionChangeTicks = directionChangeTicks;
        randomizeDirection();
    }

    @Override
    public void tick(EntityHandle handle, Player nearestPlayer) {
        long currentTick = org.bukkit.Bukkit.getCurrentTick();
        if (currentTick - lastDirectionChange > directionChangeTicks) {
            randomizeDirection();
            lastDirectionChange = currentTick;
        }

        if (handle.bukkitEntities().isEmpty()) return;

        Vector movement = currentDirection.clone().multiply(speed);
        for (Entity e : handle.bukkitEntities()) {
            if (!e.isDead()) {
                Location loc = e.getLocation().add(movement);
                float yaw = (float) Math.toDegrees(Math.atan2(-currentDirection.getX(), currentDirection.getZ()));
                loc.setYaw(yaw);
                e.teleport(loc);
            }
        }
    }

    private void randomizeDirection() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double angle = rng.nextDouble() * 2 * Math.PI;
        currentDirection = new Vector(Math.cos(angle), 0, Math.sin(angle));
    }
}
