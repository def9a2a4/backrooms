package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class FakeCoordinatesEvent extends AbstractTimedEvent {

    private long baseX = 12_550_821L;
    private long baseZ = -8_371_022L;
    private int driftRange = 500;

    public FakeCoordinatesEvent() {
        this.chance = 1.0;
        this.checkIntervalTicks = 20;
    }

    @Override
    public String getId() {
        return "fake_coordinates";
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        if (config != null) {
            baseX = config.getLong("base_x", baseX);
            baseZ = config.getLong("base_z", baseZ);
            driftRange = config.getInt("drift_range", driftRange);
        }
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        long x = baseX + rng.nextInt(-driftRange, driftRange);
        int y = (int) player.getLocation().getY();
        long z = baseZ + rng.nextInt(-driftRange, driftRange);

        String text = "X: " + x + " Y: " + y + " Z: " + z;
        player.sendActionBar(Component.text(text, NamedTextColor.GRAY));
    }
}
