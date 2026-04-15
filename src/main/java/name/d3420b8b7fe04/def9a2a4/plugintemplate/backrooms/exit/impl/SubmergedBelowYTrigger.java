package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Triggers when player is swimming/submerged in water below a Y threshold.
 * Used for: L1→L2 (tainted water sinkhole).
 *
 * Config:
 *   type: submerged_below_y
 *   y_threshold: 5
 *   target_level: level_2
 */
public class SubmergedBelowYTrigger extends AbstractExitTrigger {

    private final int yThreshold;

    public SubmergedBelowYTrigger(ConfigurationSection config) {
        super(config);
        this.yThreshold = config.getInt("y_threshold", 0);
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        return player.isInWater() && player.getLocation().getY() < yThreshold;
    }
}
