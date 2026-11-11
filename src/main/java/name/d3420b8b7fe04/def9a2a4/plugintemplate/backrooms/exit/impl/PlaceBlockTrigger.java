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
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Triggers when the player places one of the listed blocks. Entries are material names or block
 * tags prefixed with '#'. Tags are checked when the block is placed, not when the config loads, so
 * they always reflect the server's loaded datapacks. Used for: L1967→L1973 (put a light down in
 * the grey undercroft).
 *
 * Config:
 *   type: place_block
 *   materials: [torch, lantern, "#minecraft:candles"]
 *   target_level: level_1973
 */
public class PlaceBlockTrigger extends AbstractExitTrigger {

    private final Set<Material> materials = EnumSet.noneOf(Material.class);
    private final List<Tag<Material>> tags = new ArrayList<>();

    public PlaceBlockTrigger(ConfigurationSection config) {
        super(config);
        for (String entry : config.getStringList("materials")) {
            if (entry.startsWith("#")) {
                NamespacedKey key = NamespacedKey.fromString(entry.substring(1));
                Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
                if (tag != null) {
                    tags.add(tag);
                    continue;
                }
            } else {
                Material mat = Material.matchMaterial(entry);
                if (mat != null) {
                    materials.add(mat);
                    continue;
                }
            }
            Logger.getLogger("Backrooms").warning("place_block: unknown material or tag " + entry);
        }
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        return false; // event-based only
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(BlockPlaceEvent.class);
    }

    @Override
    public boolean checkEvent(Event event, Player player, BackroomsPlayerState state) {
        if (!(event instanceof BlockPlaceEvent e)) return false;
        Material type = e.getBlockPlaced().getType();
        if (materials.contains(type)) return true;
        for (Tag<Material> tag : tags) {
            if (tag.isTagged(type)) return true;
        }
        return false;
    }
}
