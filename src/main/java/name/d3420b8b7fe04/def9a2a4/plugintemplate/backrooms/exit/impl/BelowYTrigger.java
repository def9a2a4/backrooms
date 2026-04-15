package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Triggers when player Y coordinate falls below a threshold.
 * Used for: L37→L3 (pool drain void fall), L7→overworld (void fall).
 *
 * Config:
 *   type: below_y
 *   y_threshold: 0
 *   target_level: overworld
 */
public class BelowYTrigger extends AbstractExitTrigger {

    private final int yThreshold;

    public BelowYTrigger(ConfigurationSection config) {
        super(config);
        this.yThreshold = config.getInt("y_threshold", 0);
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        return player.getLocation().getY() < yThreshold;
    }
}
