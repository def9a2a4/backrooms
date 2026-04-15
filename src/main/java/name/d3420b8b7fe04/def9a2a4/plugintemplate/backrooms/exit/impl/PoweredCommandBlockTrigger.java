package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;

/**
 * Triggers when a player flips a lever/button near a command block enough times.
 * Used for: L3→{L4, L5, L7, L64637} (Server Room hub).
 *
 * Detects PlayerInteractEvent on levers/buttons, checks if a command block
 * is within radius, and counts activations in player customData.
 *
 * Config:
 *   type: powered_command_block
 *   power_count: 5
 *   radius: 8
 *   target_level: level_4
 */
public class PoweredCommandBlockTrigger extends AbstractExitTrigger {

    private final int powerCount;
    private final int radius;
    private final String stateKey;

    public PoweredCommandBlockTrigger(ConfigurationSection config) {
        super(config);
        this.powerCount = config.getInt("power_count", 5);
        this.radius = config.getInt("radius", 8);
        // Unique state key per target so multiple command block triggers don't share counts
        this.stateKey = "cmd_power_" + targetLevelId;
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(PlayerInteractEvent.class);
    }

    @Override
    public boolean checkEvent(Event event, Player player, BackroomsPlayerState state) {
        if (!(event instanceof PlayerInteractEvent interact)) return false;
        if (interact.getClickedBlock() == null) return false;

        Block clicked = interact.getClickedBlock();
        Material type = clicked.getType();

        // Only count lever/button interactions
        if (type != Material.LEVER && !type.name().endsWith("_BUTTON")) return false;

        // Check if there's a command block within radius
        if (!hasCommandBlockNearby(clicked, radius)) return false;

        // Increment count
        String countStr = state.getCustomData(stateKey);
        int count = (countStr != null ? Integer.parseInt(countStr) : 0) + 1;
        state.setCustomData(stateKey, String.valueOf(count));

        return count >= powerCount;
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        // Event-driven only
        return false;
    }

    private boolean hasCommandBlockNearby(Block origin, int searchRadius) {
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    Block b = origin.getRelative(dx, dy, dz);
                    if (b.getType() == Material.COMMAND_BLOCK) return true;
                }
            }
        }
        return false;
    }
}
