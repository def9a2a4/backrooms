package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.TransitionManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.BackroomsLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Triggers when a command block receives enough redstone pulses within a time window
 * while a player is nearby.
 *
 * Listens for BlockRedstoneEvent directly (registered as its own Bukkit Listener).
 * Tracks pulse timestamps per block location. When threshold is reached and a player
 * is within radius, fires the transition for that player.
 *
 * Config:
 *   type: powered_command_block
 *   power_count: 16        # pulses needed within window
 *   window_ticks: 80       # time window (4 seconds at 20 TPS)
 *   radius: 20             # max player distance
 *   target_level: level_4
 */
public class PoweredCommandBlockTrigger extends AbstractExitTrigger implements Listener {

    private final int powerCount;
    private final int windowTicks;
    private final int radius;

    // Pulse timestamps per block location (keyed by packed block coords)
    private final Map<Long, List<Long>> pulseLog = new HashMap<>();

    // Set after registration by BackroomsPlugin
    private LevelRegistry levelRegistry;
    private PlayerStateManager playerStateManager;
    private TransitionManager transitionManager;

    public PoweredCommandBlockTrigger(ConfigurationSection config) {
        super(config);
        this.powerCount = config.getInt("power_count", 16);
        this.windowTicks = config.getInt("window_ticks", 80);
        this.radius = config.getInt("radius", 20);
    }

    /** Called by BackroomsPlugin after construction to provide manager references. */
    public void init(LevelRegistry levelRegistry, PlayerStateManager playerStateManager,
                     TransitionManager transitionManager) {
        this.levelRegistry = levelRegistry;
        this.playerStateManager = playerStateManager;
        this.transitionManager = transitionManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        // Only count rising edges (off → on)
        if (event.getOldCurrent() > 0 || event.getNewCurrent() == 0) return;

        Block block = event.getBlock();
        if (block.getType() != Material.COMMAND_BLOCK) return;

        // Must be in a backrooms world
        if (levelRegistry == null || !levelRegistry.isBackroomsWorld(block.getWorld())) return;

        long now = block.getWorld().getFullTime();
        long blockKey = packBlockPos(block);

        // Record this pulse
        pulseLog.computeIfAbsent(blockKey, k -> new ArrayList<>()).add(now);

        // Prune old pulses outside the window
        List<Long> pulses = pulseLog.get(blockKey);
        long cutoff = now - windowTicks;
        pulses.removeIf(t -> t < cutoff);

        // Check if threshold reached
        if (pulses.size() < powerCount) return;

        // Find nearest player within radius
        Player nearestPlayer = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player player : block.getWorld().getPlayers()) {
            double dist = player.getLocation().distance(block.getLocation());
            if (dist <= radius && dist < nearestDist) {
                nearestDist = dist;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer == null) return;

        // Clear pulse log for this block to prevent re-triggering
        pulses.clear();

        // Fire transition
        BackroomsLevel level = levelRegistry.getByWorld(block.getWorld());
        if (level == null) return;

        BackroomsPlayerState state = playerStateManager.getOrCreate(nearestPlayer);
        transitionManager.performTransition(nearestPlayer, state, level, this);
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        // Not tick-based — handled entirely by the event listener above
        return false;
    }

    private static long packBlockPos(Block block) {
        return ((long) block.getX() & 0x3FFFFFFL) << 38
             | ((long) block.getY() & 0xFFFL) << 26
             | ((long) block.getZ() & 0x3FFFFFFL);
    }
}
