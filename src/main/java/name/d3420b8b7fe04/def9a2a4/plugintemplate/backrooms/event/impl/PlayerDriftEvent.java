package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class PlayerDriftEvent extends AbstractTimedEvent {

    private double maxDrift = 0.8;

    public PlayerDriftEvent() {
        this.chance = 0.3;
        this.checkIntervalTicks = 10 * 20;
    }

    @Override
    public String getId() {
        return "player_drift";
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        if (config != null) {
            maxDrift = config.getDouble("max_drift", maxDrift);
        }
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double dx = rng.nextDouble(-maxDrift, maxDrift);
        double dz = rng.nextDouble(-maxDrift, maxDrift);
        Location loc = player.getLocation().add(dx, 0, dz);
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(player.getLocation().getPitch());
        player.teleport(loc);
    }
}
