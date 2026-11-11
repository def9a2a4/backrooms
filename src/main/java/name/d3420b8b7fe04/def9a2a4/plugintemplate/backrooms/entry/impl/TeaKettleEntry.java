package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryTrigger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Campfire;
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

/**
 * Brewing tea: throw leaves at a water cauldron sitting over a fire (a lit campfire, soul
 * campfire, or fire). The water boils, the kettle whistles, and the player arrives in the
 * voxel engine, where the server answers 418.
 */
public class TeaKettleEntry implements EntryTrigger {

    private boolean enabled = true;
    private String targetLevel = "level_418";
    private int blindnessDuration = 60;
    private int delayTicks = 50;
    private Set<String> enabledWorlds = new HashSet<>();
    private final JavaPlugin plugin;

    private Location pendingKettle;

    public TeaKettleEntry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "tea_kettle";
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
        if (!Tag.LEAVES.isTagged(droppedItem.getItemStack().getType())) return null;

        Block playerBlock = player.getLocation().getBlock();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    Block candidate = playerBlock.getRelative(dx, dy, dz);
                    if (isKettle(candidate)) {
                        droppedItem.remove();
                        pendingKettle = candidate.getLocation().add(0.5, 1.0, 0.5);
                        return targetLevel;
                    }
                }
            }
        }
        return null;
    }

    /** A water cauldron with a lit campfire or fire directly below it. */
    private static boolean isKettle(Block block) {
        if (block.getType() != Material.WATER_CAULDRON) return false;
        Block below = block.getRelative(BlockFace.DOWN);
        return switch (below.getType()) {
            case FIRE, SOUL_FIRE -> true;
            case CAMPFIRE, SOUL_CAMPFIRE -> below.getBlockData() instanceof Campfire c && c.isLit();
            default -> false;
        };
    }

    @Override
    public String getTargetLevel() {
        return targetLevel;
    }

    @Override
    public void playEntrySequence(Player player, Runnable onComplete) {
        if (pendingKettle != null) {
            Location loc = pendingKettle;
            pendingKettle = null;
            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 40, 0.3, 0.4, 0.3, 0.02);
            loc.getWorld().playSound(loc, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.2f);
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_FLUTE, 1.0f, 2.0f), 20);
        }

        if (blindnessDuration > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, 1, false, false));
        player.sendMessage("§7§oThe kettle whistles. Something refuses to brew coffee.");
        Bukkit.getScheduler().runTaskLater(plugin, onComplete, delayTicks);
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        enabled = config.getBoolean("enabled", true);
        targetLevel = config.getString("target_level", "level_418");
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
