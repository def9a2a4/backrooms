package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Finds a safe random spawn location within a radius around (0, spawnY, 0).
 * Falls back to the fixed spawn location if no safe spot is found.
 */
public class SpawnFinder {

    private static final int MAX_ATTEMPTS = 10;

    public static Location findRandomSpawn(World world, int radius, int spawnY, Location fallback) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = rng.nextInt(-radius, radius + 1);
            int z = rng.nextInt(-radius, radius + 1);
            for (int dy = 0; dy <= 3; dy++) {
                if (isSafe(world, x, spawnY + dy, z)) {
                    return new Location(world, x + 0.5, spawnY + dy, z + 0.5);
                }
                if (dy > 0 && isSafe(world, x, spawnY - dy, z)) {
                    return new Location(world, x + 0.5, spawnY - dy, z + 0.5);
                }
            }
        }
        return fallback;
    }

    public static Location findSurfaceSpawn(World world, int x, int z, int startY) {
        int maxY = world.getMaxHeight() - 1;
        for (int y = startY; y < maxY; y++) {
            if (isSafe(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return new Location(world, x + 0.5, startY, z + 0.5);
    }

    public static void clearFallDamage(Player player) {
        player.setFallDistance(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20, 3));
    }

    private static boolean isSafe(World world, int x, int y, int z) {
        return world.getBlockAt(x, y - 1, z).getType().isSolid()
                && world.getBlockAt(x, y, z).getType().isAir()
                && world.getBlockAt(x, y + 1, z).getType().isAir();
    }
}
