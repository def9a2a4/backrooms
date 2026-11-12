package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.EventScheduler;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public class LightBlinkEvent extends AbstractTimedEvent {

    private int radius = 20;
    private int cycles = 4;
    private int toggleDelayTicks = 5;

    private JavaPlugin plugin;
    private EventScheduler eventScheduler;

    // These constants match Level 0 generator; override via subclass or config for other levels
    private int lightY = 24;
    private int lightYMin = 24;   // scan down to here for pendant lights
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
        this.lightYMin = lightY;
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
        lightY = config.getInt("light_y", lightY);
        lightYMin = Math.min(lightY, config.getInt("light_y_min", lightY));
        lightSpacing = config.getInt("light_spacing", lightSpacing);
        String matName = config.getString("light_material", null);
        if (matName != null) {
            Material mat = Material.matchMaterial(matName);
            if (mat != null) lightMaterial = mat;
        }
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

        // Toggle only the blocks that are lights right now, so restoring never
        // writes a light where there wasn't one (e.g. the top of a pendant chain).
        List<Block> lights = LightGrid.find(player.getLocation(), radius, lightSpacing,
                lightY, lightYMin, lightMaterial);

        for (int i = 0; i < cycles * 2; i++) {
            boolean lightsOff = (i % 2 == 0);
            long delay = (long) i * toggleDelayTicks;

            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    LightGrid.set(lights, lightsOff ? Material.AIR : lightMaterial), delay);
        }

        long finalDelay = (long) cycles * 2 * toggleDelayTicks;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            LightGrid.set(lights, lightMaterial);
            if (eventScheduler != null) {
                eventScheduler.unmarkPlayerInEvent(id);
            }
        }, finalDelay);
    }

    @Override
    public void shutdown() {
        // Light restoration is handled by the level's cleanup
    }


    public int getRadius() {
        return radius;
    }
}
