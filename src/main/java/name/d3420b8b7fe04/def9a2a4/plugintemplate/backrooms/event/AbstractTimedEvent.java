package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractTimedEvent implements BackroomsEvent {

    protected boolean enabled = true;
    protected double chance = 0.1;
    protected int checkIntervalTicks = 100;

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        this.enabled = config.getBoolean("enabled", true);
        this.chance = config.getDouble("chance", this.chance);
        this.checkIntervalTicks = config.getInt("check_interval_seconds", this.checkIntervalTicks / 20) * 20;
    }

    @Override
    public boolean canTrigger(Player player, BackroomsPlayerState state) {
        return enabled && ThreadLocalRandom.current().nextDouble() < chance;
    }

    @Override
    public int getCheckIntervalTicks() {
        return checkIntervalTicks;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
