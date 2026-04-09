package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.BackroomsPlugin;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;

public abstract class AbstractLevel implements BackroomsLevel {

    protected final BackroomsPlugin plugin;
    protected World world;

    protected AbstractLevel(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void configureWorld(World world) {
        this.world = world;
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
    }

    @Override
    public void tick(World world, Collection<Player> players) {
    }

    @Override
    public void onPlayerEnter(Player player, BackroomsPlayerState state) {
    }

    @Override
    public void onPlayerLeave(Player player, BackroomsPlayerState state) {
    }

    public World getWorld() {
        return world;
    }
}
