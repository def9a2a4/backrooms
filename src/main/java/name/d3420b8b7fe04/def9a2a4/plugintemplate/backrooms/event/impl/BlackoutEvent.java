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

public class BlackoutEvent extends AbstractTimedEvent {

    private int radius = 30;
    private int durationTicks = 15 * 20;

    private JavaPlugin plugin;
    private EventScheduler eventScheduler;

    private int lightY = 24;
    private int lightSpacing = 4;
    private Material lightMaterial = Material.OCHRE_FROGLIGHT;

    public BlackoutEvent() {
        this.chance = 0.05;
        this.checkIntervalTicks = 120 * 20;
    }

    @Override
    public String getId() {
        return "blackout";
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
        radius = config.getInt("radius", 30);
        durationTicks = config.getInt("duration_seconds", 15) * 20;
        lightY = config.getInt("light_y", lightY);
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

        Location center = player.getLocation().clone();
        setLightsInRadius(center, radius, Material.AIR);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            setLightsInRadius(center, radius, lightMaterial);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                setLightsInRadius(p.getLocation(), radius, lightMaterial);
            }
            if (eventScheduler != null) {
                eventScheduler.unmarkPlayerInEvent(id);
            }
        }, durationTicks);
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
