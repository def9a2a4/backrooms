package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.EventScheduler;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class LightBlinkEvent extends AbstractTimedEvent {

    private int radius = 20;
    private int cycles = 4;
    private int toggleDelayTicks = 5;

    private JavaPlugin plugin;
    private EventScheduler eventScheduler;

    // These constants match Level 0 generator; override via subclass or config for other levels
    private int lightY = 24;
    private int lightSpacing = 4;
    private Material lightMaterial = Material.OCHRE_FROGLIGHT;

    public LightBlinkEvent() {
        this.chance = 0.15;
        this.checkIntervalTicks = 30 * 20;
    }

    @Override
    public String getId() {
        return "light_blink";
    }

    @Override
    public void init(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setEventScheduler(EventScheduler scheduler) {
        this.eventScheduler = scheduler;
    }

    public void setLightParameters(int lightY, int lightSpacing, Material lightMaterial) {
        this.lightY = lightY;
        this.lightSpacing = lightSpacing;
        this.lightMaterial = lightMaterial;
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        if (config == null) return;
        radius = config.getInt("radius", 20);
        cycles = config.getInt("cycles", 4);
        toggleDelayTicks = config.getInt("toggle_delay_ticks", 5);
    }

    @Override
    public boolean canTrigger(Player player, BackroomsPlayerState state) {
        if (eventScheduler != null && eventScheduler.isPlayerInEvent(player.getUniqueId())) {
            return false;
        }
        return super.canTrigger(player, state);
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        if (plugin == null) return;
        UUID id = player.getUniqueId();

        if (eventScheduler != null) {
            eventScheduler.markPlayerInEvent(id);
        }

        Location center = player.getLocation().clone();

        for (int i = 0; i < cycles * 2; i++) {
            boolean lightsOff = (i % 2 == 0);
            long delay = (long) i * toggleDelayTicks;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (lightsOff) {
                    setLightsInRadius(center, radius, Material.AIR);
                } else {
                    setLightsInRadius(center, radius, lightMaterial);
                }
            }, delay);
        }

        long finalDelay = (long) cycles * 2 * toggleDelayTicks;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            setLightsInRadius(center, radius, lightMaterial);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                setLightsInRadius(p.getLocation(), radius, lightMaterial);
            }
            if (eventScheduler != null) {
                eventScheduler.unmarkPlayerInEvent(id);
            }
        }, finalDelay);
    }

    @Override
    public void shutdown() {
        // Light restoration is handled by the level's cleanup
    }

    private void setLightsInRadius(Location center, int r, Material material) {
        World world = center.getWorld();
        if (world == null) return;

        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        for (int x = cx - r; x <= cx + r; x++) {
            if (x % lightSpacing != 0) continue;
            for (int z = cz - r; z <= cz + r; z++) {
                if (z % lightSpacing != 0) continue;
                Block block = world.getBlockAt(x, lightY, z);
                if (block.getChunk().isLoaded()) {
                    block.setType(material, false);
                }
            }
        }
    }

    public int getRadius() {
        return radius;
    }
}
