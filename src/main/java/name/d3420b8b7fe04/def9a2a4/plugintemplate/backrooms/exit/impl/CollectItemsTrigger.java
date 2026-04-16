package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Triggers when player has collected enough of a specific item in their inventory.
 * Used for: L0→L1 (collect 7 written books), L64637→overworld (collect 16 books).
 *
 * Config:
 *   type: collect_items
 *   item: WRITTEN_BOOK
 *   threshold: 16
 *   target_level: level_1
 */
public class CollectItemsTrigger extends AbstractExitTrigger {

    private final Material material;
    private final int threshold;

    public CollectItemsTrigger(ConfigurationSection config) {
        super(config);
        String itemStr = config.getString("item", "WRITTEN_BOOK");
        Material parsed;
        try {
            parsed = Material.valueOf(itemStr);
        } catch (IllegalArgumentException e) {
            parsed = Material.WRITTEN_BOOK;
        }
        this.material = parsed;
        this.threshold = config.getInt("threshold", 16);
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count >= threshold;
    }
}
