package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Level 84 "The Hedge Maze" — punishes block break/place and prevents bed use.
 * Punishment parameters are configurable via level_config in the level YAML.
 */
public class Level84Listener implements Listener {

    private static final String WORLD_NAME = "bkrms_84";

    // Punishment config
    private final double damage;
    private final int poisonDurationTicks;
    private final int poisonAmplifier;
    private final boolean disableBeds;

    public Level84Listener(double damage, int poisonDurationTicks,
                           int poisonAmplifier, boolean disableBeds) {
        this.damage = damage;
        this.poisonDurationTicks = poisonDurationTicks;
        this.poisonAmplifier = poisonAmplifier;
        this.disableBeds = disableBeds;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.getBlock().getWorld().getName().equals(WORLD_NAME)) return;
        event.setCancelled(true);
        punish(event.getPlayer());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.getBlock().getWorld().getName().equals(WORLD_NAME)) return;
        event.setCancelled(true);
        punish(event.getPlayer());
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!disableBeds) return;
        if (!event.getPlayer().getWorld().getName().equals(WORLD_NAME)) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("\u00a77The bed refuses to accept you here.");
    }

    private void punish(Player player) {
        player.damage(damage);
        if (poisonDurationTicks > 0) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.POISON, poisonDurationTicks,
                    poisonAmplifier, false, true));
        }
    }
}
