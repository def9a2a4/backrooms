package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface EntryTrigger {

    String getId();

    List<Class<? extends Event>> getListenedEvents();

    @Nullable
    String evaluate(Event event, Player player);

    void playEntrySequence(Player player, Runnable onComplete);

    void loadConfig(ConfigurationSection config);

    Set<String> getEnabledWorlds();
}
