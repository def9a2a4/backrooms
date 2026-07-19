package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerBedEnterEvent;

import java.util.List;

/**
 * Triggers when the player tries to sleep in any bed in the level.
 * Fires regardless of BedEnterResult — levels with fixed daytime
 * (like L101's permanent noon) report NOT_POSSIBLE_NOW, and the
 * transition must still happen. Used for: L101→L0 (the cottage bed
 * evicts you from the memory).
 *
 * Config:
 *   type: bed_sleep
 *   target_level: level_0
 */
public class BedSleepTrigger extends AbstractExitTrigger {

    public BedSleepTrigger(ConfigurationSection config) {
        super(config);
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        return false; // event-based only
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(PlayerBedEnterEvent.class);
    }

    @Override
    public boolean checkEvent(Event event, Player player, BackroomsPlayerState state) {
        if (!(event instanceof PlayerBedEnterEvent bedEnter)) return false;
        bedEnter.setCancelled(true);
        return true;
    }
}
