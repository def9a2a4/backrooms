package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level64637ChunkGenerator;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Wraps player Y position in the Library level (64637) to create a vertical loop.
 * Layers 0 and 6 are identical. Descending past Y=5 teleports up by 60;
 * ascending past Y=66 teleports down by 60. Works everywhere — rooms are
 * identical so the teleport is invisible.
 */
public class LibraryWrapListener implements Listener {

    private static final double UPPER_THRESHOLD = Level64637ChunkGenerator.BASE_Y + 66.0;
    private static final double LOWER_THRESHOLD = Level64637ChunkGenerator.BASE_Y + 5.0;

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.getTo().getWorld().getName().equals("bkrms_64637")) return;

        double y = event.getTo().getY();
        if (y > LOWER_THRESHOLD && y < UPPER_THRESHOLD) return;

        Location dest = event.getTo().clone();
        if (y >= UPPER_THRESHOLD) {
            dest.setY(y - Level64637ChunkGenerator.WRAP_OFFSET);
        } else {
            dest.setY(y + Level64637ChunkGenerator.WRAP_OFFSET);
        }
        event.setTo(dest);
    }
}
