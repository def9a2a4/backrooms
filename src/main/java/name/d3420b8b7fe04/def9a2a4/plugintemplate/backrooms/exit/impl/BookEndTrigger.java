package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Fired when a player finishes reading a book. Hand-held book page turns are
 * client-side and invisible to the server, so "reaching the end" is detected
 * by a click: the last page of qualifying books carries a closing line with a
 * clickEvent that runs "/backrooms read &lt;key&gt;". BackroomsCommand looks up
 * the BookEndTrigger with that key on the player's current level and performs
 * the transition (the player must be holding a written book).
 *
 * Config:
 *   type: book_end
 *   key: "leveldat"        # must match the book's click command
 *   target_level: level_101
 */
public class BookEndTrigger extends AbstractExitTrigger {

    private final String key;

    public BookEndTrigger(ConfigurationSection config) {
        super(config);
        this.key = config.getString("key", "");
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        return false; // fired only via /backrooms read, never by tick polling
    }
}
