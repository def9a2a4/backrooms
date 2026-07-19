package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

/**
 * Triggers when the player opens (right-clicks with) a written book whose
 * title matches the configured one. Used for: L64637→L101 (the level.dat
 * save-file book hidden in the Library's shelves).
 *
 * Config:
 *   type: open_book
 *   book_title: "level.dat"
 *   target_level: level_101
 */
public class OpenBookTrigger extends AbstractExitTrigger {

    private final String bookTitle;

    public OpenBookTrigger(ConfigurationSection config) {
        super(config);
        this.bookTitle = config.getString("book_title", "level.dat");
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        return false; // event-based only
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(PlayerInteractEvent.class);
    }

    @Override
    public boolean checkEvent(Event event, Player player, BackroomsPlayerState state) {
        if (!(event instanceof PlayerInteractEvent interact)) return false;
        Action action = interact.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return false;

        ItemStack item = interact.getItem();
        if (item == null || item.getType() != Material.WRITTEN_BOOK) return false;
        if (!(item.getItemMeta() instanceof BookMeta meta)) return false;

        String title = meta.getTitle();
        return title != null && title.equals(bookTitle);
    }
}
