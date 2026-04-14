package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level64637ChunkGenerator;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Wraps player Y position in the Library level (64637) to create a vertical loop.
 * Layers 0 and 6 are identical. Ascending past the ceiling cap teleports to layer 0;
 * descending past the base teleports to layer 6.
 */
public class LibraryWrapListener implements Listener {

    // Teleport triggers — mid-point of the boundary solids
    private static final double UPPER_THRESHOLD = Level64637ChunkGenerator.NUM_LAYERS
            * Level64637ChunkGenerator.FLOOR_SPACING + 1; // Y=71, mid ceiling cap
    private static final double LOWER_THRESHOLD = 1.0;     // Y=1, mid base solid

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.getTo().getWorld().getName().equals("backrooms_level_64637")) return;

        double y = event.getTo().getY();

        if (y > UPPER_THRESHOLD) {
            Location to = event.getTo().clone();
            to.setY(y - Level64637ChunkGenerator.WRAP_OFFSET);
            event.setTo(to);
        } else if (y < LOWER_THRESHOLD) {
            Location to = event.getTo().clone();
            to.setY(y + Level64637ChunkGenerator.WRAP_OFFSET);
            event.setTo(to);
        }
    }
}
