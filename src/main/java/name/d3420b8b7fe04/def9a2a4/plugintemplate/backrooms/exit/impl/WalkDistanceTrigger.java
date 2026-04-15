package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Triggers when player walks far enough from their entry point (world spawn).
 * Used for: L4→L3 (walk ~1000 blocks in Far Lands).
 *
 * Config:
 *   type: walk_distance
 *   distance_blocks: 1000
 *   target_level: level_3
 */
public class WalkDistanceTrigger extends AbstractExitTrigger {

    private final int distanceBlocks;

    public WalkDistanceTrigger(ConfigurationSection config) {
        super(config);
        this.distanceBlocks = config.getInt("distance_blocks", 1000);
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        Location spawn = player.getWorld().getSpawnLocation();
        double dx = player.getLocation().getX() - spawn.getX();
        double dz = player.getLocation().getZ() - spawn.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        return horizontalDistance >= distanceBlocks;
    }
}
