package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

public class InventoryGlitchEvent extends AbstractTimedEvent {

    private int swapCount = 2;
    private int restoreDelayTicks = 40;
    private JavaPlugin plugin;

    public InventoryGlitchEvent() {
        this.chance = 0.1;
        this.checkIntervalTicks = 60 * 20;
    }

    @Override
    public String getId() {
        return "inventory_glitch";
    }

    @Override
    public void init(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        if (config != null) {
            swapCount = config.getInt("swap_count", swapCount);
            restoreDelayTicks = config.getInt("restore_delay_ticks", restoreDelayTicks);
        }
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        PlayerInventory inv = player.getInventory();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int[][] swaps = new int[swapCount][2];
        ItemStack[][] saved = new ItemStack[swapCount][2];

        for (int i = 0; i < swapCount; i++) {
            int slotA = rng.nextInt(36);
            int slotB = rng.nextInt(36);
            swaps[i] = new int[]{slotA, slotB};
            saved[i] = new ItemStack[]{
                    inv.getItem(slotA) != null ? inv.getItem(slotA).clone() : null,
                    inv.getItem(slotB) != null ? inv.getItem(slotB).clone() : null
            };

            ItemStack temp = inv.getItem(slotA);
            inv.setItem(slotA, inv.getItem(slotB));
            inv.setItem(slotB, temp);
        }

        if (plugin != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    for (int i = swapCount - 1; i >= 0; i--) {
                        inv.setItem(swaps[i][0], saved[i][0]);
                        inv.setItem(swaps[i][1], saved[i][1]);
                    }
                }
            }, restoreDelayTicks);
        }
    }
}
