package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public interface BackroomsEvent {

    String getId();

    boolean canTrigger(Player player, BackroomsPlayerState state);

    void trigger(Player player, BackroomsPlayerState state);

    void loadConfig(ConfigurationSection config);

    int getCheckIntervalTicks();

    default void init(JavaPlugin plugin) {}

    default void shutdown() {}
}
