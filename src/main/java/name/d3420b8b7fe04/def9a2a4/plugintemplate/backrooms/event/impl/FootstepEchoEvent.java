package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public class FootstepEchoEvent extends AbstractTimedEvent {

    private int delayTicks = 8;
    private int offsetBlocks = 5;
    private JavaPlugin plugin;

    public FootstepEchoEvent() {
        this.chance = 0.4;
        this.checkIntervalTicks = 3 * 20;
    }

    @Override
    public String getId() {
        return "footstep_echo";
    }

    @Override
    public void init(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        if (config == null) return;
        delayTicks = config.getInt("delay_ticks", 8);
        offsetBlocks = config.getInt("offset_blocks", 5);
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        if (plugin == null) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location echoLoc = player.getLocation().add(
                rng.nextDouble(-offsetBlocks, offsetBlocks),
                0,
                rng.nextDouble(-offsetBlocks, offsetBlocks)
        );

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(echoLoc, Sound.BLOCK_STONE_STEP, SoundCategory.BLOCKS, 0.6f, 0.8f);
            }
        }, delayTicks);
    }
}
