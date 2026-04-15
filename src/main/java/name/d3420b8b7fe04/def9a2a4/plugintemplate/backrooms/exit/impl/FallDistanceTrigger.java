package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Triggers when a player has fallen a cumulative distance downward.
 * Tracks descent across vertical wraps by storing last-known Y and
 * accumulated fall in player custom data. Ascending resets the counter.
 * Used for: L64637→overworld (fall 200 blocks down infinite staircase).
 *
 * Config:
 *   type: fall_distance
 *   fall_distance: 200
 *   target_level: overworld
 */
public class FallDistanceTrigger extends AbstractExitTrigger {

    private static final String KEY_LAST_Y = "fall_trigger_last_y";
    private static final String KEY_CUMULATIVE = "fall_trigger_cumulative";

    /** Ignore Y deltas larger than this — they come from wrap teleports, not real movement. */
    private static final double WRAP_THRESHOLD = 10.0;

    private final int fallDistance;

    public FallDistanceTrigger(ConfigurationSection config) {
        super(config);
        this.fallDistance = config.getInt("fall_distance", 200);
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        double currentY = player.getLocation().getY();

        String lastYStr = state.getCustomData(KEY_LAST_Y);
        if (lastYStr == null) {
            state.setCustomData(KEY_LAST_Y, String.valueOf(currentY));
            state.setCustomData(KEY_CUMULATIVE, "0");
            return false;
        }

        double lastY = Double.parseDouble(lastYStr);
        double delta = lastY - currentY; // positive when falling

        state.setCustomData(KEY_LAST_Y, String.valueOf(currentY));

        if (delta > 0) {
            if (delta < WRAP_THRESHOLD) {
                // Normal descent — accumulate
                double cumulative = Double.parseDouble(state.getCustomData(KEY_CUMULATIVE));
                cumulative += delta;
                state.setCustomData(KEY_CUMULATIVE, String.valueOf(cumulative));
                return cumulative >= fallDistance;
            }
            // Large jump from wrap teleport — skip this tick
            return false;
        } else {
            // Ascended or stayed level — reset
            state.setCustomData(KEY_CUMULATIVE, "0");
            return false;
        }
    }
}
