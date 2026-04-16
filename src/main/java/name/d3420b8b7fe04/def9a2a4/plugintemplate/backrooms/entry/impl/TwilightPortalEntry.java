package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryTrigger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TwilightPortalEntry implements EntryTrigger {

    private static final Set<Material> FLOWERS = Set.of(
            Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID,
            Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP,
            Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP,
            Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY,
            Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY,
            Material.TORCHFLOWER, Material.PINK_PETALS, Material.WITHER_ROSE
    );

    private boolean enabled = true;
    private String targetLevel = "level_84";
    private int blindnessDuration = 60;
    private int delayTicks = 50;
    private Set<String> enabledWorlds = new HashSet<>();
    private final JavaPlugin plugin;

    private Location pendingLightningLocation;

    public TwilightPortalEntry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "twilight_portal";
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(PlayerDropItemEvent.class);
    }

    @Override
    @Nullable
    public String evaluate(Event event, Player player) {
        if (!enabled) return null;
        if (!(event instanceof PlayerDropItemEvent dropEvent)) return null;

        Item droppedItem = dropEvent.getItemDrop();
        if (droppedItem.getItemStack().getType() != Material.DIAMOND) return null;

        // Search for a valid 2x2 water pool near the player
        Block playerBlock = player.getLocation().getBlock();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    Block candidate = playerBlock.getRelative(dx, dy, dz);
                    if (isValidPool(candidate)) {
                        // Consume the diamond
                        droppedItem.remove();

                        // Lightning strike at center of the pool
                        pendingLightningLocation = candidate.getLocation().add(1, 0, 1);

                        return targetLevel;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Validates the Twilight Forest portal structure:
     * - 2x2 water pool (candidate is the min-X, min-Z corner)
     * - Ring of grass blocks surrounding the pool (at the same Y level)
     * - At least one flower on top of each grass block
     */
    private boolean isValidPool(Block corner) {
        // Check the 2x2 water pool
        Block[][] pool = {
                {corner, corner.getRelative(BlockFace.EAST)},
                {corner.getRelative(BlockFace.SOUTH), corner.getRelative(BlockFace.SOUTH).getRelative(BlockFace.EAST)}
        };

        for (Block[] row : pool) {
            for (Block b : row) {
                if (b.getType() != Material.WATER) return false;
            }
        }

        // Check the ring of grass blocks around the pool and flowers on top
        // The ring is a 4x4 area minus the 2x2 interior
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                // Skip the interior pool blocks
                if (dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1) continue;

                Block ring = corner.getRelative(dx, 0, dz);
                if (ring.getType() != Material.GRASS_BLOCK) return false;

                Block above = ring.getRelative(BlockFace.UP);
                if (!FLOWERS.contains(above.getType())) return false;
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
        // Strike lightning at the pool center
        if (pendingLightningLocation != null) {
            Location loc = pendingLightningLocation;
            pendingLightningLocation = null;
            // Cosmetic lightning — no damage
            player.getWorld().strikeLightningEffect(loc);
        }

        if (blindnessDuration > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, 1, false, false));
        player.sendMessage("\u00a72\u00a7oThe forest calls to you...");
        Bukkit.getScheduler().runTaskLater(plugin, onComplete, delayTicks);
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        enabled = config.getBoolean("enabled", true);
        targetLevel = config.getString("target_level", "level_84");
        blindnessDuration = config.getInt("blindness_duration", blindnessDuration);
        delayTicks = config.getInt("delay_ticks", delayTicks);
    }

    @Override
    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    @Override
    public void setEnabledWorlds(Set<String> worlds) {
        this.enabledWorlds = worlds;
    }
}
