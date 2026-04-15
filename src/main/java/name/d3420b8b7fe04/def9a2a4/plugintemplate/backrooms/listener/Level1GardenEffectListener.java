package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Applies nausea to players standing in garden zones in Level 1.
 * Uses the same noise function as Level1ChunkGenerator to determine garden zones.
 */
public class Level1GardenEffectListener {

    private static final double GARDEN_ZONE_SCALE = 0.004;
    private static final double MIN_GARDEN_DISTANCE = 300.0;
    private static final int NAUSEA_DURATION = 100; // 5 seconds
    private static final int CHECK_INTERVAL = 40; // 2 seconds

    private final JavaPlugin plugin;
    private BukkitTask task;

    public Level1GardenEffectListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (var world : Bukkit.getWorlds()) {
            if (!world.getName().startsWith("bkrms_1")) continue;
            long seed = world.getSeed();

            for (Player player : world.getPlayers()) {
                int worldX = player.getLocation().getBlockX();
                int worldZ = player.getLocation().getBlockZ();

                if (isGardenZone(seed, worldX, worldZ)) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.NAUSEA, NAUSEA_DURATION, 0, true, false, false));
                }
            }
        }
    }

    private boolean isGardenZone(long seed, int worldX, int worldZ) {
        double distSq = (double) worldX * worldX + (double) worldZ * worldZ;
        if (distSq < MIN_GARDEN_DISTANCE * MIN_GARDEN_DISTANCE) return false;

        double gardenNoise = SimplexNoise.noise2(seed + 50,
                worldX * GARDEN_ZONE_SCALE, worldZ * GARDEN_ZONE_SCALE);
        return gardenNoise > 0.73;
    }
}
