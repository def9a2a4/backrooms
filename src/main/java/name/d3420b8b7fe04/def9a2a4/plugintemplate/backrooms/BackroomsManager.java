package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;

public class BackroomsManager {

    private World backroomsWorld;

    public void loadOrCreateWorld() {
        WorldCreator creator = new WorldCreator("backrooms");
        creator.generator(new BackroomsChunkGenerator());
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);
        backroomsWorld = creator.createWorld();

        if (backroomsWorld != null) {
            backroomsWorld.setGameRule(GameRules.SPAWN_MOBS, false);
            backroomsWorld.setGameRule(GameRules.ADVANCE_TIME, false);
            backroomsWorld.setGameRule(GameRules.ADVANCE_WEATHER, false);
            backroomsWorld.setGameRule(GameRules.MOB_GRIEFING, false);
            backroomsWorld.setTime(6000);
        }
    }

    public void unloadWorld() {
        if (backroomsWorld != null) {
            Bukkit.unloadWorld(backroomsWorld, true);
        }
    }

    public World getWorld() {
        return backroomsWorld;
    }
}
