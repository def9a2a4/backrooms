package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Triggers when player has collected enough of a specific item in their inventory.
 * Used for: L0→L1 (collect 7 written books), L64637→overworld (collect 16 books).
 *
 * The count is the live inventory total, not cumulative pickups — dropping items
 * below the threshold keeps the player where they are. An optional warning fires
 * when the count enters [warning_threshold, threshold) so the mechanic teaches itself.
 *
 * Config:
 *   type: collect_items
 *   item: WRITTEN_BOOK
 *   threshold: 16
 *   warning_threshold: 12          # optional, 0 = no warning
 *   warning_message: "..."         # sent at most once a minute per player
 *   target_level: level_1
 */
public class CollectItemsTrigger extends AbstractExitTrigger {

    private static final long WARN_COOLDOWN_MS = 60_000L;

    private final Material material;
    private final int threshold;
    private final int warningThreshold;
    private final String warningMessage;

    private final Map<UUID, Long> lastWarned = new HashMap<>();

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
        this.warningThreshold = config.getInt("warning_threshold", 0);
        this.warningMessage = config.getString("warning_message");
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        if (count >= threshold) return true;

        if (warningThreshold > 0 && count >= warningThreshold && warningMessage != null) {
            long now = System.currentTimeMillis();
            Long last = lastWarned.get(player.getUniqueId());
            if (last == null || now - last > WARN_COOLDOWN_MS) {
                lastWarned.put(player.getUniqueId(), now);
                player.sendMessage(warningMessage);
            }
        }
        return false;
    }
}
