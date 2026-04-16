package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryTrigger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AetherPortalEntry implements EntryTrigger {

    private boolean enabled = true;
    private String targetLevel = "level_94";
    private int blindnessDuration = 60;
    private int delayTicks = 40;
    private Set<String> enabledWorlds = new HashSet<>();
    private final JavaPlugin plugin;
    private Block pendingCleanupBlock;

    public AetherPortalEntry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "aether_portal";
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(PlayerInteractEvent.class);
    }

    @Override
    @Nullable
    public String evaluate(Event event, Player player) {
        if (!enabled) return null;
        if (!(event instanceof PlayerInteractEvent interact)) return null;
        if (interact.getAction() != Action.RIGHT_CLICK_BLOCK) return null;
        if (interact.getItem() == null || interact.getItem().getType() != Material.WATER_BUCKET) return null;

        Block clickedBlock = interact.getClickedBlock();
        if (clickedBlock == null) return null;

        // The water will be placed on the face the player clicked
        Block waterBlock = clickedBlock.getRelative(interact.getBlockFace());

        if (!isInsideGlowstonePortalFrame(waterBlock)) return null;

        // Let the water place — we'll clean it up during the entry sequence
        pendingCleanupBlock = waterBlock;

        return targetLevel;
    }

    /**
     * Checks if the given block is inside a nether-portal-shaped frame made of glowstone.
     * Scans both X-axis and Z-axis orientations.
     * Minimum portal: 4 wide x 5 tall frame (2x3 interior).
     */
    private boolean isInsideGlowstonePortalFrame(Block block) {
        return checkFrameAxis(block, BlockFace.EAST, BlockFace.WEST)
                || checkFrameAxis(block, BlockFace.NORTH, BlockFace.SOUTH);
    }

    private boolean checkFrameAxis(Block block, BlockFace positive, BlockFace negative) {
        // Find left and right frame edges from the water placement block
        int left = 0;
        for (int i = 1; i <= 21; i++) {
            if (block.getRelative(negative, i).getType() == Material.GLOWSTONE) { left = i; break; }
            if (block.getRelative(negative, i).getType() != Material.AIR
                    && block.getRelative(negative, i).getType() != Material.WATER) break;
        }
        if (left == 0) return false;

        int right = 0;
        for (int i = 1; i <= 21; i++) {
            if (block.getRelative(positive, i).getType() == Material.GLOWSTONE) { right = i; break; }
            if (block.getRelative(positive, i).getType() != Material.AIR
                    && block.getRelative(positive, i).getType() != Material.WATER) break;
        }
        if (right == 0) return false;

        int interiorWidth = left + right - 1;
        if (interiorWidth < 2) return false;

        // Find top and bottom frame edges
        int down = 0;
        for (int i = 1; i <= 21; i++) {
            if (block.getRelative(BlockFace.DOWN, i).getType() == Material.GLOWSTONE) { down = i; break; }
            if (block.getRelative(BlockFace.DOWN, i).getType() != Material.AIR
                    && block.getRelative(BlockFace.DOWN, i).getType() != Material.WATER) break;
        }
        if (down == 0) return false;

        int up = 0;
        for (int i = 1; i <= 21; i++) {
            if (block.getRelative(BlockFace.UP, i).getType() == Material.GLOWSTONE) { up = i; break; }
            if (block.getRelative(BlockFace.UP, i).getType() != Material.AIR
                    && block.getRelative(BlockFace.UP, i).getType() != Material.WATER) break;
        }
        if (up == 0) return false;

        int interiorHeight = down + up - 1;
        if (interiorHeight < 3) return false;

        // Validate full frame: bottom row, top row, and side columns must all be glowstone
        Block bottomLeft = block.getRelative(BlockFace.DOWN, down).getRelative(negative, left);

        // Check bottom row
        for (int x = 0; x < interiorWidth + 2; x++) {
            if (bottomLeft.getRelative(positive, x).getType() != Material.GLOWSTONE) return false;
        }
        // Check top row
        Block topLeft = bottomLeft.getRelative(BlockFace.UP, interiorHeight + 1);
        for (int x = 0; x < interiorWidth + 2; x++) {
            if (topLeft.getRelative(positive, x).getType() != Material.GLOWSTONE) return false;
        }
        // Check left column
        for (int y = 1; y <= interiorHeight; y++) {
            if (bottomLeft.getRelative(BlockFace.UP, y).getType() != Material.GLOWSTONE) return false;
        }
        // Check right column
        Block bottomRight = bottomLeft.getRelative(positive, interiorWidth + 1);
        for (int y = 1; y <= interiorHeight; y++) {
            if (bottomRight.getRelative(BlockFace.UP, y).getType() != Material.GLOWSTONE) return false;
        }

        // Check interior is all air/water
        for (int y = 1; y <= interiorHeight; y++) {
            for (int x = 1; x <= interiorWidth; x++) {
                Material mat = bottomLeft.getRelative(positive, x).getRelative(BlockFace.UP, y).getType();
                if (mat != Material.AIR && mat != Material.WATER) return false;
            }
        }

        return true;
    }

    @Override
    public String getTargetLevel() {
        return targetLevel;
    }

    @Override
    public void playEntrySequence(Player player, Runnable onComplete) {
        if (blindnessDuration > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, 1, false, false));
        player.sendMessage("\u00a7b\u00a7oThe light engulfs you...");

        // Remove the water partway through the sequence
        if (pendingCleanupBlock != null) {
            Block cleanup = pendingCleanupBlock;
            pendingCleanupBlock = null;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (cleanup.getType() == Material.WATER) {
                    cleanup.setType(Material.AIR);
                }
            }, Math.max(1, delayTicks - 5));
        }

        Bukkit.getScheduler().runTaskLater(plugin, onComplete, delayTicks);
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        enabled = config.getBoolean("enabled", true);
        targetLevel = config.getString("target_level", "level_94");
        blindnessDuration = config.getInt("blindness_duration", blindnessDuration);
        delayTicks = config.getInt("delay_ticks", delayTicks);
    }

    @Override
    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public void setEnabledWorlds(Set<String> worlds) {
        this.enabledWorlds = worlds;
    }
}
