package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class BackroomsAmbianceManager {

    private final JavaPlugin plugin;
    private final BackroomsManager backroomsManager;

    // Ambient sounds config
    private boolean ambientSoundsEnabled;
    private double ambientSoundChance;
    private List<Sound> ambientSounds;

    // Light blink config
    private boolean blinkEnabled;
    private double blinkChance;
    private int blinkRadius;
    private int blinkCycles;
    private int blinkToggleDelayTicks;

    // Blackout config
    private boolean blackoutEnabled;
    private double blackoutChance;
    private int blackoutRadius;
    private int blackoutDurationTicks;

    // State
    private final Set<UUID> playersInEvent = new HashSet<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    public BackroomsAmbianceManager(JavaPlugin plugin, BackroomsManager backroomsManager) {
        this.plugin = plugin;
        this.backroomsManager = backroomsManager;
        loadConfig();
    }

    private void loadConfig() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("backrooms");

        // Ambient sounds
        ConfigurationSection soundsCfg = cfg != null ? cfg.getConfigurationSection("ambient_sounds") : null;
        ambientSoundsEnabled = soundsCfg != null && soundsCfg.getBoolean("enabled", true);
        ambientSoundChance = soundsCfg != null ? soundsCfg.getDouble("chance", 0.3) : 0.3;

        ambientSounds = new ArrayList<>();
        if (soundsCfg != null && soundsCfg.contains("sounds")) {
            for (String name : soundsCfg.getStringList("sounds")) {
                String key = name.toLowerCase(java.util.Locale.ROOT).replace('_', '.');
                Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(key));
                if (sound != null) {
                    ambientSounds.add(sound);
                } else {
                    plugin.getLogger().warning("Unknown sound in config: " + name);
                }
            }
        }
        if (ambientSounds.isEmpty()) {
            ambientSounds.add(Sound.AMBIENT_CAVE);
        }

        // Light blink
        ConfigurationSection blinkCfg = cfg != null ? cfg.getConfigurationSection("light_blink") : null;
        blinkEnabled = blinkCfg != null && blinkCfg.getBoolean("enabled", true);
        blinkChance = blinkCfg != null ? blinkCfg.getDouble("chance", 0.15) : 0.15;
        blinkRadius = blinkCfg != null ? blinkCfg.getInt("radius", 20) : 20;
        blinkCycles = blinkCfg != null ? blinkCfg.getInt("cycles", 4) : 4;
        blinkToggleDelayTicks = blinkCfg != null ? blinkCfg.getInt("toggle_delay_ticks", 5) : 5;

        // Blackout
        ConfigurationSection blackoutCfg = cfg != null ? cfg.getConfigurationSection("blackout") : null;
        blackoutEnabled = blackoutCfg != null && blackoutCfg.getBoolean("enabled", true);
        blackoutChance = blackoutCfg != null ? blackoutCfg.getDouble("chance", 0.05) : 0.05;
        blackoutRadius = blackoutCfg != null ? blackoutCfg.getInt("radius", 30) : 30;
        blackoutDurationTicks = (blackoutCfg != null ? blackoutCfg.getInt("duration_seconds", 15) : 15) * 20;
    }

    public void start() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("backrooms");

        // Ambient sounds timer
        if (ambientSoundsEnabled) {
            int intervalTicks = 5 * 20; // default 5 seconds
            if (cfg != null && cfg.getConfigurationSection("ambient_sounds") != null) {
                intervalTicks = cfg.getConfigurationSection("ambient_sounds").getInt("check_interval_seconds", 5) * 20;
            }
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tickAmbientSounds, intervalTicks, intervalTicks));
        }

        // Light blink timer
        if (blinkEnabled) {
            int intervalTicks = 30 * 20;
            if (cfg != null && cfg.getConfigurationSection("light_blink") != null) {
                intervalTicks = cfg.getConfigurationSection("light_blink").getInt("check_interval_seconds", 30) * 20;
            }
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tickBlink, intervalTicks, intervalTicks));
        }

        // Blackout timer
        if (blackoutEnabled) {
            int intervalTicks = 120 * 20;
            if (cfg != null && cfg.getConfigurationSection("blackout") != null) {
                intervalTicks = cfg.getConfigurationSection("blackout").getInt("check_interval_seconds", 120) * 20;
            }
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tickBlackout, intervalTicks, intervalTicks));
        }
    }

    public void stop() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();

        // Restore all lights in loaded chunks
        World world = backroomsManager.getWorld();
        if (world != null) {
            for (Player player : world.getPlayers()) {
                restoreLightsInRadius(player.getLocation(), Math.max(blinkRadius, blackoutRadius));
            }
        }
        playersInEvent.clear();
    }

    private void tickAmbientSounds() {
        World world = backroomsManager.getWorld();
        if (world == null) return;

        for (Player player : world.getPlayers()) {
            if (ThreadLocalRandom.current().nextDouble() < ambientSoundChance) {
                playAmbientSound(player);
            }
        }
    }

    private void tickBlink() {
        World world = backroomsManager.getWorld();
        if (world == null) return;

        for (Player player : world.getPlayers()) {
            if (playersInEvent.contains(player.getUniqueId())) continue;
            if (ThreadLocalRandom.current().nextDouble() < blinkChance) {
                startBlinkEvent(player);
            }
        }
    }

    private void tickBlackout() {
        World world = backroomsManager.getWorld();
        if (world == null) return;

        for (Player player : world.getPlayers()) {
            if (playersInEvent.contains(player.getUniqueId())) continue;
            if (ThreadLocalRandom.current().nextDouble() < blackoutChance) {
                startBlackoutEvent(player);
            }
        }
    }

    private void playAmbientSound(Player player) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Sound sound = ambientSounds.get(rng.nextInt(ambientSounds.size()));
        Location loc = player.getLocation().add(
                rng.nextDouble(-15, 15),
                rng.nextDouble(-3, 3),
                rng.nextDouble(-15, 15)
        );
        float pitch = 0.7f + rng.nextFloat() * 0.6f;
        player.playSound(loc, sound, SoundCategory.AMBIENT, 0.8f, pitch);
    }

    private void startBlinkEvent(Player player) {
        UUID id = player.getUniqueId();
        playersInEvent.add(id);
        Location center = player.getLocation().clone();

        for (int i = 0; i < blinkCycles * 2; i++) {
            boolean lightsOff = (i % 2 == 0);
            long delay = (long) i * blinkToggleDelayTicks;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (lightsOff) {
                    setLightsInRadius(center, blinkRadius, Material.AIR);
                } else {
                    restoreLightsInRadius(center, blinkRadius);
                }
            }, delay);
        }

        // Final restore
        long finalDelay = (long) blinkCycles * 2 * blinkToggleDelayTicks;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            restoreLightsInRadius(center, blinkRadius);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                restoreLightsInRadius(p.getLocation(), blinkRadius);
            }
            playersInEvent.remove(id);
        }, finalDelay);
    }

    private void startBlackoutEvent(Player player) {
        UUID id = player.getUniqueId();
        playersInEvent.add(id);
        Location center = player.getLocation().clone();

        setLightsInRadius(center, blackoutRadius, Material.AIR);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            restoreLightsInRadius(center, blackoutRadius);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                restoreLightsInRadius(p.getLocation(), blackoutRadius);
            }
            playersInEvent.remove(id);
        }, blackoutDurationTicks);
    }

    private void setLightsInRadius(Location center, int radius, Material material) {
        World world = center.getWorld();
        if (world == null) return;

        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int lightY = BackroomsChunkGenerator.CEILING_MIN_Y;
        int spacing = BackroomsChunkGenerator.LIGHT_SPACING;

        for (int x = cx - radius; x <= cx + radius; x++) {
            if (x % spacing != 0) continue;
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (z % spacing != 0) continue;
                Block block = world.getBlockAt(x, lightY, z);
                if (block.getChunk().isLoaded()) {
                    block.setType(material, false);
                }
            }
        }
    }

    private void restoreLightsInRadius(Location center, int radius) {
        setLightsInRadius(center, radius, Material.OCHRE_FROGLIGHT);
    }
}
