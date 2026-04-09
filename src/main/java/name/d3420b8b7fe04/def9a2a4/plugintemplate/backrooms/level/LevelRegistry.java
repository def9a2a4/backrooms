package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.EnderDragon;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Logger;

public class LevelRegistry {

    private final Map<String, BackroomsLevel> levels = new LinkedHashMap<>();
    private final Map<World, BackroomsLevel> worldToLevel = new HashMap<>();
    private final Map<String, World> levelToWorld = new HashMap<>();
    private final Logger logger;

    public LevelRegistry(Logger logger) {
        this.logger = logger;
    }

    public void register(BackroomsLevel level) {
        levels.put(level.getId(), level);
    }

    @Nullable
    public BackroomsLevel get(String id) {
        return levels.get(id);
    }

    @Nullable
    public BackroomsLevel getByWorld(World world) {
        return worldToLevel.get(world);
    }

    @Nullable
    public World getWorld(BackroomsLevel level) {
        return levelToWorld.get(level.getId());
    }

    @Nullable
    public World getWorld(String levelId) {
        return levelToWorld.get(levelId);
    }

    public Collection<BackroomsLevel> getAll() {
        return Collections.unmodifiableCollection(levels.values());
    }

    public boolean isBackroomsWorld(World world) {
        return worldToLevel.containsKey(world);
    }

    public void loadWorlds() {
        for (BackroomsLevel level : levels.values()) {
            String worldName = worldNameFor(level);
            WorldCreator creator = new WorldCreator(worldName);
            creator.generator(level.createChunkGenerator());
            creator.environment(level.getEnvironment());
            creator.generateStructures(false);

            World world = creator.createWorld();
            if (world != null) {
                level.configureWorld(world);
                cleanEndFeatures(world);
                worldToLevel.put(world, level);
                levelToWorld.put(level.getId(), world);
                logger.info("Loaded backrooms level: " + level.getId() + " (world: " + worldName + ")");
            } else {
                logger.severe("Failed to create world for level: " + level.getId());
            }
        }
    }

    public void unloadWorlds() {
        for (Map.Entry<String, World> entry : levelToWorld.entrySet()) {
            World world = entry.getValue();
            if (world != null) {
                Bukkit.unloadWorld(world, true);
            }
        }
        worldToLevel.clear();
        levelToWorld.clear();
    }

    public void regenerateLevel(String levelId) {
        BackroomsLevel level = levels.get(levelId);
        if (level == null) return;

        World world = levelToWorld.get(levelId);
        if (world != null) {
            World overworld = Bukkit.getWorlds().get(0);
            for (var player : world.getPlayers()) {
                player.teleport(overworld.getSpawnLocation());
                player.sendMessage("The Backrooms are being regenerated...");
            }
            worldToLevel.remove(world);
            levelToWorld.remove(levelId);
            Bukkit.unloadWorld(world, false);
        }

        String worldName = worldNameFor(level);
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            deleteDirectory(worldFolder.toPath());
        }

        // Recreate
        WorldCreator creator = new WorldCreator(worldName);
        creator.generator(level.createChunkGenerator());
        creator.environment(level.getEnvironment());
        creator.generateStructures(false);

        World newWorld = creator.createWorld();
        if (newWorld != null) {
            level.configureWorld(newWorld);
            cleanEndFeatures(newWorld);
            worldToLevel.put(newWorld, level);
            levelToWorld.put(levelId, newWorld);
        }
    }

    public void regenerateAll() {
        for (String levelId : levels.keySet()) {
            regenerateLevel(levelId);
        }
    }

    private void cleanEndFeatures(World world) {
        if (world.getEnvironment() != World.Environment.THE_END) return;
        // Remove all ender dragons
        for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
            dragon.remove();
        }
    }

    private String worldNameFor(BackroomsLevel level) {
        // Level 0 keeps "backrooms" for backward compatibility
        if ("level_0".equals(level.getId())) {
            return "backrooms";
        }
        return "backrooms_" + level.getId();
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
            logger.severe("Failed to delete directory: " + dir + " - " + e.getMessage());
        }
    }
}
