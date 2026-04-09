package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehavior;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class StationaryStareBehavior implements EntityBehavior {

    private final int rotateIntervalTicks;
    private long lastRotateTick = 0;

    public StationaryStareBehavior(int rotateIntervalTicks) {
        this.rotateIntervalTicks = rotateIntervalTicks;
    }

    @Override
    public void tick(EntityHandle handle, Player nearestPlayer) {
        long currentTick = org.bukkit.Bukkit.getCurrentTick();
        if (currentTick - lastRotateTick < rotateIntervalTicks) return;
        lastRotateTick = currentTick;

        for (Entity entity : handle.bukkitEntities()) {
            if (entity instanceof ArmorStand stand && !stand.isDead()) {
                Location standLoc = stand.getLocation();
                Location playerLoc = nearestPlayer.getLocation();
                double dx = playerLoc.getX() - standLoc.getX();
                double dz = playerLoc.getZ() - standLoc.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                standLoc.setYaw(yaw);
                stand.teleport(standLoc);
            }
        }
    }
}
