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

public class HerobrineShrineEntry implements EntryTrigger {

    private boolean enabled = true;
    private String targetLevel = "level_0";
    private int blindnessDuration = 60;
    private int delayTicks = 40;
    private String entryMessage = "\u00a74\u00a7oYou feel a presence watching you...";
    private Set<String> enabledWorlds = new HashSet<>();
    private final JavaPlugin plugin;
    private Block pendingCleanupBlock;

    public HerobrineShrineEntry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "herobrine_shrine";
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
        if (interact.getItem() == null || interact.getItem().getType() != Material.FLINT_AND_STEEL) return null;

        Block clickedBlock = interact.getClickedBlock();
        if (clickedBlock == null) return null;
        if (clickedBlock.getType() != Material.NETHERRACK) return null;

        if (!isShrineValid(clickedBlock)) return null;

        // Let the fire light — we'll clean it up during the entry sequence
        pendingCleanupBlock = clickedBlock.getRelative(BlockFace.UP);

        return targetLevel;
    }

    /**
     * Validates the Herobrine shrine structure:
     * - 3x3 gold block base below the netherrack
     * - Netherrack at center on top of the gold
     * - 4 redstone torches on the gold at cardinal directions around the netherrack
     */
    private boolean isShrineValid(Block netherrack) {
        Block below = netherrack.getRelative(BlockFace.DOWN);

        // Check 3x3 gold base under the netherrack
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (below.getRelative(dx, 0, dz).getType() != Material.GOLD_BLOCK) return false;
            }
        }

        // Check 4 redstone torches at cardinal positions (same Y as netherrack, on the gold)
        if (netherrack.getRelative(BlockFace.NORTH).getType() != Material.REDSTONE_TORCH) return false;
        if (netherrack.getRelative(BlockFace.SOUTH).getType() != Material.REDSTONE_TORCH) return false;
        if (netherrack.getRelative(BlockFace.EAST).getType() != Material.REDSTONE_TORCH) return false;
        if (netherrack.getRelative(BlockFace.WEST).getType() != Material.REDSTONE_TORCH) return false;

        return true;
    }

    @Override
    public String getTargetLevel() {
        return targetLevel;
    }

    @Override
    public void playEntrySequence(Player player, Runnable onComplete) {
        if (blindnessDuration > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, 1, false, false));
        if (blindnessDuration > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, blindnessDuration, 0, false, false));
        player.sendMessage(entryMessage);

        // Extinguish the fire partway through the sequence
        if (pendingCleanupBlock != null) {
            Block cleanup = pendingCleanupBlock;
            pendingCleanupBlock = null;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (cleanup.getType() == Material.FIRE) {
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
        targetLevel = config.getString("target_level", "level_0");
        blindnessDuration = config.getInt("blindness_duration", blindnessDuration);
        delayTicks = config.getInt("delay_ticks", delayTicks);
        entryMessage = config.getString("message", entryMessage);
    }

    @Override
    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public void setEnabledWorlds(Set<String> worlds) {
        this.enabledWorlds = worlds;
    }
}
