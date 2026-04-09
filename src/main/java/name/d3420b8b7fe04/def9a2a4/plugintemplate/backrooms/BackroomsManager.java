package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class BackroomsManager {

    private World backroomsWorld;

    public void loadOrCreateWorld() {
        WorldCreator creator = new WorldCreator("backrooms");
        creator.generator(new BackroomsChunkGenerator());
        creator.environment(World.Environment.THE_END);
        creator.generateStructures(false);
        backroomsWorld = creator.createWorld();

        if (backroomsWorld != null) {
            backroomsWorld.setGameRule(GameRules.SPAWN_MOBS, false);
            backroomsWorld.setGameRule(GameRules.ADVANCE_TIME, false);
            backroomsWorld.setGameRule(GameRules.MOB_GRIEFING, false);
        }
    }

    public void unloadWorld() {
        if (backroomsWorld != null) {
            Bukkit.unloadWorld(backroomsWorld, true);
        }
    }

    public void regenerateWorld() {
        // Evacuate all players from the backrooms
        if (backroomsWorld != null) {
            World overworld = Bukkit.getWorlds().get(0);
            for (var player : backroomsWorld.getPlayers()) {
                player.teleport(overworld.getSpawnLocation());
                player.sendMessage("The Backrooms are being regenerated...");
            }
            Bukkit.unloadWorld(backroomsWorld, false);
        }

        // Delete the world folder
        File worldFolder = new File(Bukkit.getWorldContainer(), "backrooms");
        if (worldFolder.exists()) {
            deleteDirectory(worldFolder.toPath());
        }

        backroomsWorld = null;

        // Recreate
        loadOrCreateWorld();
    }

    private void deleteDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public World getWorld() {
        return backroomsWorld;
    }
}
