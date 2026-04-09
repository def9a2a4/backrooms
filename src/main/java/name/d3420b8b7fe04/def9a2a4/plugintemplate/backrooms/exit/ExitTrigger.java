package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;

public interface ExitTrigger {

    String getId();

    String getTargetLevelId();

    boolean check(Player player, BackroomsPlayerState state);

    void playTransitionSequence(Player player, Runnable onComplete);

    default List<Class<? extends Event>> getListenedEvents() {
        return List.of();
    }

    default boolean checkEvent(Event event, Player player) {
        return false;
    }
}
