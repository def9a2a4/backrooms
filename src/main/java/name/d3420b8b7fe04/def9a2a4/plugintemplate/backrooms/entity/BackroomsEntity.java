package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public interface BackroomsEntity {

    String getId();

    EntityHandle spawn(Location location, Player target);

    void despawn(EntityHandle handle);

    void tick(EntityHandle handle);

    boolean shouldSpawn(Player player, BackroomsPlayerState state);

    void loadConfig(ConfigurationSection config);
}
