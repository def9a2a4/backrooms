package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public class DatapackInstaller {

    private static final String DATAPACK_PREFIX = "datapack/backrooms_datapack/";
    private static final String[] DATAPACK_FILES = {
            "pack.mcmeta",
            "data/minecraft/dimension_type/the_nether.json"
    };

    private final JavaPlugin plugin;
    private final Logger logger;

    public DatapackInstaller(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Install the backrooms datapack to the main world's datapacks folder.
     * This overrides minecraft:the_nether as a fallback for dark levels
     * when NMS dimension type swapping is unavailable.
     */
    public void installToMainWorld() {
        World mainWorld = Bukkit.getWorlds().get(0);
        if (mainWorld == null) {
            logger.warning("Main world not available — cannot install datapack");
            return;
        }
        File datapackDir = new File(mainWorld.getWorldFolder(), "datapacks/backrooms_datapack");

        for (String resource : DATAPACK_FILES) {
            File target = new File(datapackDir, resource);
            target.getParentFile().mkdirs();
            try (InputStream in = plugin.getResource(DATAPACK_PREFIX + resource)) {
                if (in == null) {
                    logger.warning("Missing datapack resource: " + resource);
                    continue;
                }
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.warning("Failed to install datapack resource " + resource + ": " + e.getMessage());
            }
        }
        logger.info("Installed backrooms datapack to " + datapackDir.getAbsolutePath());
    }
}
