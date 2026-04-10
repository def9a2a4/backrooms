package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.ExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.generator.ChunkGenerator;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.List;

public interface BackroomsLevel {

    String getId();

    String getDisplayName();

    ChunkGenerator createChunkGenerator();

    World.Environment getEnvironment();

    void configureWorld(World world);

    List<String> getEventIds();

    default ConfigurationSection getEventConfig(String eventId) {
        return null;
    }

    List<String> getEntityIds();

    List<ExitTrigger> getExitTriggers();

    void tick(World world, Collection<Player> players);

    void onPlayerEnter(Player player, BackroomsPlayerState state);

    void onPlayerLeave(Player player, BackroomsPlayerState state);

    default boolean handleEvent(Event event, Player player) {
        return false;
    }
}
