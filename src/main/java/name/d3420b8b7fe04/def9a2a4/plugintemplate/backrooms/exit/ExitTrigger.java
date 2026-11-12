package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;

public interface ExitTrigger {

    String getId();

    String getTargetLevelId();

    /** Where to go instead when the target level isn't available (disabled or still loading); null: nowhere. */
    default String getFallbackLevelId() {
        return null;
    }

    /** Arrive at the same coordinates (and facing) in the target level instead of its spawn. */
    default boolean keepsPosition() {
        return false;
    }

    boolean check(Player player, BackroomsPlayerState state);

    void playTransitionSequence(Player player, Runnable onComplete);

    default List<Class<? extends Event>> getListenedEvents() {
        return List.of();
    }

    default boolean checkEvent(Event event, Player player, BackroomsPlayerState state) {
        return false;
    }
}
