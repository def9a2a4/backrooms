package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.ScanChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Keeps the scan levels' props whole, so the exits hanging off them work for every visitor: a
 * display case, offering stone or single-block prop that's broken or built into is rebuilt
 * {@link #DELAY_TICKS} after the first change (after any exit it triggered has played out).
 */
public class ScanPropListener implements Listener {

    private static final long DELAY_TICKS = 100;

    private final JavaPlugin plugin;
    private final Set<Object> pending = Collections.newSetFromMap(new IdentityHashMap<>());

    public ScanPropListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        touched(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        touched(event.getBlockPlaced());
    }

    private void touched(Block block) {
        World world = block.getWorld();
        if (!(world.getGenerator() instanceof ScanChunkGenerator gen)) return;
        Object prop = gen.propAt(world, block.getX(), block.getY(), block.getZ());
        if (prop == null || !pending.add(prop)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(prop);
            gen.restore(world, prop);
        }, DELAY_TICKS);
    }
}
