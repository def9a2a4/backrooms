package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.BackroomsPlugin;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.ExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level0ChunkGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.AbstractLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.util.List;

public class Level0 extends AbstractLevel {

    public Level0(BackroomsPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getId() {
        return "level_0";
    }

    @Override
    public String getDisplayName() {
        return "The Lobby";
    }

    @Override
    public ChunkGenerator createChunkGenerator() {
        return new Level0ChunkGenerator();
    }

    @Override
    public World.Environment getEnvironment() {
        return World.Environment.THE_END;
    }

    @Override
    public List<String> getEventIds() {
        return List.of("ambient_sound", "light_blink", "blackout", "footstep_echo");
    }

    @Override
    public List<String> getEntityIds() {
        return List.of();
    }

    @Override
    public List<ExitTrigger> getExitTriggers() {
        return List.of();
    }

    @Override
    public void onPlayerEnter(Player player, BackroomsPlayerState state) {
        player.sendMessage("You have entered the Backrooms.");
    }
}
