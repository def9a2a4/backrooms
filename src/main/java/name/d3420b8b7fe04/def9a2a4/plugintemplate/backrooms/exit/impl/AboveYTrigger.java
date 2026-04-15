package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Triggers when player Y coordinate rises above a threshold.
 * Used for: L1→Skyblock (climb vines through ceiling hole).
 *
 * Config:
 *   type: above_y
 *   y_threshold: 30
 *   target_level: level_skyblock
 */
public class AboveYTrigger extends AbstractExitTrigger {

    private final int yThreshold;

    public AboveYTrigger(ConfigurationSection config) {
        super(config);
        this.yThreshold = config.getInt("y_threshold", 256);
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        return player.getLocation().getY() > yThreshold;
    }
}
