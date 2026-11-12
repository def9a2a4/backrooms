package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import io.papermc.paper.event.player.PlayerLecternPageChangeEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

/**
 * Triggers when the player turns a lectern's book to its last page and the book's title matches.
 * Used for: L1881 (Hintze Hall's placards: read about a place to the end and you're there).
 *
 * Config:
 *   type: lectern_last_page
 *   book_title: "Stonehenge"
 *   target_level: level_56
 */
public class LecternLastPageTrigger extends AbstractExitTrigger {

    private final String bookTitle;

    public LecternLastPageTrigger(ConfigurationSection config) {
        super(config);
        this.bookTitle = config.getString("book_title", "");
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        return false; // event-based only
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(PlayerLecternPageChangeEvent.class);
    }

    @Override
    public boolean checkEvent(Event event, Player player, BackroomsPlayerState state) {
        if (!(event instanceof PlayerLecternPageChangeEvent e)) return false;
        ItemStack book = e.getBook();
        if (!(book.getItemMeta() instanceof BookMeta meta) || !bookTitle.equals(meta.getTitle())) return false;
        return e.getNewPage() >= meta.getPageCount() - 1;
    }
}
