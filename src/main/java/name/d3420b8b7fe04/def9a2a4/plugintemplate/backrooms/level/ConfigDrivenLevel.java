package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.BackroomsPlugin;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.ExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.GeneratorRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

import java.util.List;

/**
 * A level whose metadata is entirely config-driven.
 * The only code dependency is the generator ID, which maps to a registered ChunkGenerator.
 */
public class ConfigDrivenLevel extends AbstractLevel {

    private final String id;
    private final String generatorId;
    private final GeneratorRegistry generatorRegistry;

    private String displayName;
    private World.Environment environment;
    private List<String> eventIds;
    private List<String> entityIds;
    private String enterMessage;
    private long fixedTime;

    public ConfigDrivenLevel(BackroomsPlugin plugin, String id, String generatorId,
                             GeneratorRegistry generatorRegistry) {
        super(plugin);
        this.id = id;
        this.generatorId = generatorId;
        this.generatorRegistry = generatorRegistry;

        // Defaults
        this.displayName = id;
        this.environment = World.Environment.THE_END;
        this.eventIds = List.of();
        this.entityIds = List.of();
        this.enterMessage = null;
        this.fixedTime = -1;
    }

    /**
     * Load level properties from a config section.
     * Expected structure:
     * <pre>
     *   level_0:
     *     display_name: "The Lobby"
     *     environment: THE_END
     *     fixed_time: 6000
     *     enter_message: "You have entered the Backrooms."
     *     events:
     *       - ambient_sound
     *       - light_blink
     *     entities:
     *       - herobrine
     *     event_config:
     *       ambient_sound:
     *         enabled: true
     *         chance: 0.3
     *         ...
     * </pre>
     */
    public void loadFromConfig(ConfigurationSection section) {
        if (section == null) return;

        this.displayName = section.getString("display_name", id);

        String envStr = section.getString("environment", "THE_END");
        try {
            this.environment = World.Environment.valueOf(envStr);
        } catch (IllegalArgumentException e) {
            this.environment = World.Environment.THE_END;
        }

        this.eventIds = section.getStringList("events");
        this.entityIds = section.getStringList("entities");
        this.enterMessage = section.getString("enter_message", null);
        this.fixedTime = section.getLong("fixed_time", -1);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public ChunkGenerator createChunkGenerator() {
        ChunkGenerator gen = generatorRegistry.create(generatorId);
        if (gen == null) {
            throw new IllegalStateException("No generator registered for id: " + generatorId
                    + " (level: " + id + ")");
        }
        return gen;
    }

    @Override
    public World.Environment getEnvironment() {
        return environment;
    }

    @Override
    public void configureWorld(World world) {
        super.configureWorld(world);
        if (fixedTime >= 0) {
            world.setTime(fixedTime);
        }
    }

    @Override
    public List<String> getEventIds() {
        return eventIds;
    }

    @Override
    public List<String> getEntityIds() {
        return entityIds;
    }

    @Override
    public List<ExitTrigger> getExitTriggers() {
        return List.of();
    }

    @Override
    public void onPlayerEnter(Player player, BackroomsPlayerState state) {
        if (enterMessage != null && !enterMessage.isEmpty()) {
            player.sendMessage(enterMessage);
        }
    }

    public String getGeneratorId() {
        return generatorId;
    }
}
