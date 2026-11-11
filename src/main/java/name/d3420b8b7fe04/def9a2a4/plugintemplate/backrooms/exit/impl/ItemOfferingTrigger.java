package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Triggers when an item the player dropped comes to rest on a specific block. One item from the
 * dropped stack is consumed. Used for: L56→L3 (lay a porkchop on the lodestone altar).
 *
 * Config:
 *   type: item_offering
 *   block: lodestone
 *   items: [porkchop, cooked_porkchop]
 *   radius: 8                 # how far from the player the dropped item may be
 *   target_level: level_3
 */
public class ItemOfferingTrigger extends AbstractExitTrigger {

    private final Material block;
    private final Set<Material> items = EnumSet.noneOf(Material.class);
    private final double radius;

    public ItemOfferingTrigger(ConfigurationSection config) {
        super(config);
        Material b = Material.matchMaterial(config.getString("block", "lodestone"));
        this.block = b != null ? b : Material.LODESTONE;
        for (String id : config.getStringList("items")) {
            Material m = Material.matchMaterial(id.toLowerCase(Locale.ROOT));
            if (m != null) items.add(m);
        }
        this.radius = config.getDouble("radius", 8.0);
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Item item) || !item.isOnGround()) continue;
            if (!player.getUniqueId().equals(item.getThrower())) continue;
            ItemStack stack = item.getItemStack();
            if (!items.isEmpty() && !items.contains(stack.getType())) continue;
            if (item.getLocation().subtract(0, 0.2, 0).getBlock().getType() != block) continue;
            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
                item.setItemStack(stack);
            } else {
                item.remove();
            }
            return true;
        }
        return false;
    }
}
