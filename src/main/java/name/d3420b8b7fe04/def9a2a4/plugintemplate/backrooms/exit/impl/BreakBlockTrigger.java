package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Triggers when the player breaks one of the listed blocks. Entries are material names or block
 * tags prefixed with '#'. Used for: L418→L94 (break the only wood in the voxel engine).
 *
 * Config:
 *   type: break_block
 *   materials: ["#minecraft:logs"]
 *   target_level: level_94
 */
public class BreakBlockTrigger extends AbstractExitTrigger {

    private final Set<Material> materials = EnumSet.noneOf(Material.class);

    public BreakBlockTrigger(ConfigurationSection config) {
        super(config);
        for (String entry : config.getStringList("materials")) {
            if (entry.startsWith("#")) {
                NamespacedKey key = NamespacedKey.fromString(entry.substring(1));
                Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
                if (tag != null) {
                    materials.addAll(tag.getValues());
                    continue;
                }
            } else {
                Material mat = Material.matchMaterial(entry);
                if (mat != null) {
                    materials.add(mat);
                    continue;
                }
            }
            Logger.getLogger("Backrooms").warning("break_block: unknown material or tag " + entry);
        }
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        return false; // event-based only
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(BlockBreakEvent.class);
    }

    @Override
    public boolean checkEvent(Event event, Player player, BackroomsPlayerState state) {
        return event instanceof BlockBreakEvent e && materials.contains(e.getBlock().getType());
    }
}
