package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public final class EntityUtil {

    private EntityUtil() {}

    public static boolean isPlayerLookingAt(Player player, Location target, double maxAngle) {
        Location eyeLoc = player.getEyeLocation();
        Vector toTarget = target.toVector().subtract(eyeLoc.toVector());
        if (toTarget.lengthSquared() < 0.001) return true;
        toTarget.normalize();
        Vector lookDir = eyeLoc.getDirection().normalize();
        double angle = Math.toDegrees(lookDir.angle(toTarget));
        return angle < maxAngle;
    }

    public static void facePlayer(Entity entity, Player player) {
        Location entityLoc = entity.getLocation();
        Location playerLoc = player.getLocation();
        double dx = playerLoc.getX() - entityLoc.getX();
        double dz = playerLoc.getZ() - entityLoc.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        entityLoc.setYaw(yaw);
        entity.teleport(entityLoc);
    }

    public static void moveToward(EntityHandle handle, Player player, double speed) {
        if (handle.bukkitEntities().isEmpty()) return;
        Entity primary = handle.bukkitEntities().get(0);
        if (primary.isDead()) return;

        Location entityLoc = primary.getLocation();
        Location targetLoc = player.getLocation();
        double dx = targetLoc.getX() - entityLoc.getX();
        double dz = targetLoc.getZ() - entityLoc.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.1) return;

        Vector direction = new Vector(dx / len * speed, 0, dz / len * speed);
        for (Entity e : handle.bukkitEntities()) {
            if (!e.isDead()) {
                e.teleport(e.getLocation().add(direction));
            }
        }
    }

    public static void moveAway(EntityHandle handle, Player player, double speed) {
        if (handle.bukkitEntities().isEmpty()) return;
        Entity primary = handle.bukkitEntities().get(0);
        if (primary.isDead()) return;

        Location entityLoc = primary.getLocation();
        Location targetLoc = player.getLocation();
        double dx = entityLoc.getX() - targetLoc.getX();
        double dz = entityLoc.getZ() - targetLoc.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.1) return;

        Vector direction = new Vector(dx / len * speed, 0, dz / len * speed);
        for (Entity e : handle.bukkitEntities()) {
            if (!e.isDead()) {
                e.teleport(e.getLocation().add(direction));
            }
        }
    }

    public static double distanceTo(EntityHandle handle, Player player) {
        if (handle.bukkitEntities().isEmpty()) return Double.MAX_VALUE;
        Entity primary = handle.bukkitEntities().get(0);
        if (primary.isDead()) return Double.MAX_VALUE;
        if (!primary.getWorld().equals(player.getWorld())) return Double.MAX_VALUE;
        return primary.getLocation().distance(player.getLocation());
    }

    public static PlayerProfile buildProfileFromUrl(String textureUrl) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}";
        String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
        profile.getProperties().add(new ProfileProperty("textures", base64));
        return profile;
    }
}
