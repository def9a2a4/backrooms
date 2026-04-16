package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.advancement.AdvancementManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level1ChunkGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Bukkit;
import org.bukkit.World;
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

    private static final int NAUSEA_DURATION = 100; // 5 seconds
    private static final int CHECK_INTERVAL = 40; // 2 seconds

    private final JavaPlugin plugin;
    private final AdvancementManager advancementManager;
    private BukkitTask task;

    public Level1GardenEffectListener(JavaPlugin plugin, AdvancementManager advancementManager) {
        this.plugin = plugin;
        this.advancementManager = advancementManager;
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
            if (!(world.getGenerator() instanceof Level1ChunkGenerator gen)) continue;
            long seed = world.getSeed();

            for (Player player : world.getPlayers()) {
                int worldX = player.getLocation().getBlockX();
                int worldZ = player.getLocation().getBlockZ();

                if (isGardenZone(gen, seed, worldX, worldZ)) {
                    advancementManager.grantGardenDiscovery(player);
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.NAUSEA, NAUSEA_DURATION, 0, true, false, false));
                }
            }
        }
    }

    private boolean isGardenZone(Level1ChunkGenerator gen, long seed, int worldX, int worldZ) {
        double minDist = gen.getMinGardenDistance();
        double distSq = (double) worldX * worldX + (double) worldZ * worldZ;
        if (distSq < minDist * minDist) return false;

        double scale = gen.getGardenZoneScale();
        double gardenNoise = SimplexNoise.noise2(seed + 50,
                worldX * scale, worldZ * scale);
        return gardenNoise > gen.getGardenThreshold();
    }
}
