package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.command.BackroomsCommand;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.BackroomsEntity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntitySpawner;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryTriggerRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl.BedAnomalyEntry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl.PortalMislinkEntry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl.SuffocationEntry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl.VoidFallEntry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.BackroomsEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.EventRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.EventScheduler;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl.*;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.TransitionManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.GeneratorRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.ConfigDrivenLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.DimensionTypeHelper;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.BackroomsListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.Level1WaterDripListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.LibraryBookshelfListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.Bukkit;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level64637ChunkGenerator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BackroomsPlugin {

    private final JavaPlugin plugin;
    private final GeneratorRegistry generatorRegistry;
    private final LevelRegistry levelRegistry;
    private final EventRegistry eventRegistry;
    private final EntryTriggerRegistry entryTriggerRegistry;
    private final EntityRegistry entityRegistry;
    private final PlayerStateManager playerStateManager;
    private final EventScheduler eventScheduler;
    private final EntitySpawner entitySpawner;
    private final TransitionManager transitionManager;
    private final EntryManager entryManager;

    private BukkitTask autoSaveTask;
    private BackroomsCommand command;

    public BackroomsPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
        this.generatorRegistry = new GeneratorRegistry();
        this.levelRegistry = new LevelRegistry(plugin.getLogger());
        this.eventRegistry = new EventRegistry();
        this.entryTriggerRegistry = new EntryTriggerRegistry();
        this.entityRegistry = new EntityRegistry();
        this.playerStateManager = new PlayerStateManager(plugin);
        this.eventScheduler = new EventScheduler(plugin, levelRegistry, eventRegistry, playerStateManager);
        this.entitySpawner = new EntitySpawner(plugin, levelRegistry, entityRegistry, playerStateManager);
        this.transitionManager = new TransitionManager(plugin, levelRegistry, playerStateManager);
        this.entryManager = new EntryManager(plugin, entryTriggerRegistry, levelRegistry,
                playerStateManager, transitionManager);
    }

    public void enable() {
        // 1. Register generators (code — these define terrain)
        generatorRegistry.registerDefaults();
        generatorRegistry.register("level_64637", Level64637ChunkGenerator::new);

        // 2. Register built-in events
        AmbientSoundEvent ambientSound = new AmbientSoundEvent();
        LightBlinkEvent lightBlink = new LightBlinkEvent();
        BlackoutEvent blackout = new BlackoutEvent();
        FakeChatMessageEvent fakeChat = new FakeChatMessageEvent();
        FootstepEchoEvent footstepEcho = new FootstepEchoEvent();

        lightBlink.setEventScheduler(eventScheduler);
        blackout.setEventScheduler(eventScheduler);

        eventRegistry.register(ambientSound);
        eventRegistry.register(lightBlink);
        eventRegistry.register(blackout);
        eventRegistry.register(fakeChat);
        eventRegistry.register(footstepEcho);
        eventRegistry.register(new FakeCoordinatesEvent());
        eventRegistry.register(new PlayerDriftEvent());
        eventRegistry.register(new RandomPotionEvent());
        eventRegistry.register(new BlockCorruptionEvent());
        eventRegistry.register(new InventoryGlitchEvent());
        eventRegistry.register(new TorchDecayEvent());

        // 3. Register built-in entities
        entityRegistry.register(new name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl.HerobrineEntity());
        entityRegistry.register(new name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl.PursuerEntity());
        entityRegistry.register(new name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl.WrongEndermanEntity());

        // 4. Register built-in entry triggers
        SuffocationEntry suffocation = new SuffocationEntry(plugin);
        VoidFallEntry voidFall = new VoidFallEntry(plugin);
        BedAnomalyEntry bedAnomaly = new BedAnomalyEntry(plugin);
        PortalMislinkEntry portalMislink = new PortalMislinkEntry(plugin);

        entryTriggerRegistry.register(suffocation);
        entryTriggerRegistry.register(voidFall);
        entryTriggerRegistry.register(bedAnomaly);
        entryTriggerRegistry.register(portalMislink);

        // 4. Load config — creates levels from config using generator registry
        loadConfig();

        // 5. Init events
        for (BackroomsEvent event : eventRegistry.getAll()) {
            event.init(plugin);
        }

        // 6. Apply NMS dimension types and create worlds
        levelRegistry.setDimensionTypeHelper(new DimensionTypeHelper(plugin.getLogger()));
        levelRegistry.loadWorlds();

        // 7. Start schedulers
        eventScheduler.start();
        entitySpawner.start();
        transitionManager.start();

        // 8. Register Bukkit listeners
        Bukkit.getPluginManager().registerEvents(playerStateManager, plugin);
        Bukkit.getPluginManager().registerEvents(entryManager, plugin);
        Bukkit.getPluginManager().registerEvents(new BackroomsListener(levelRegistry), plugin);
        Bukkit.getPluginManager().registerEvents(new LibraryBookshelfListener(plugin, loadLibraryBookConfig()), plugin);
        Bukkit.getPluginManager().registerEvents(new Level1WaterDripListener(plugin), plugin);

        // 9. Register commands
        command = new BackroomsCommand(plugin, levelRegistry, playerStateManager, transitionManager,
                eventRegistry, entityRegistry, entryTriggerRegistry, entitySpawner, generatorRegistry);
        plugin.getCommand("backrooms").setExecutor(command);
        plugin.getCommand("backrooms").setTabCompleter(command);

        // 10. Auto-save player state every 5 minutes
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin,
                playerStateManager::saveAll, 6000L, 6000L);

        plugin.getLogger().info("Backrooms framework enabled with " +
                levelRegistry.getAll().size() + " level(s).");
    }

    public void disable() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        if (command != null) {
            command.shutdown();
        }
        eventScheduler.stop();
        entitySpawner.stop();
        transitionManager.stop();

        for (BackroomsEvent event : eventRegistry.getAll()) {
            event.shutdown();
        }

        playerStateManager.saveAll();
        levelRegistry.unloadWorlds();
    }

    private void loadConfig() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("backrooms");
        if (cfg == null) return;

        // Load escalation thresholds
        if (cfg.contains("player.escalation_thresholds_minutes")) {
            List<Integer> thresholds = cfg.getIntegerList("player.escalation_thresholds_minutes");
            if (!thresholds.isEmpty()) {
                BackroomsPlayerState.setEscalationThresholds(thresholds.stream().mapToInt(i -> i).toArray());
            }
        }

        // Load entity spawner config
        entitySpawner.loadConfig(cfg.getConfigurationSection("entity_spawner"));

        // Load entry trigger config
        ConfigurationSection entryCfg = cfg.getConfigurationSection("entry");
        Set<String> enabledWorlds = new HashSet<>();
        if (entryCfg != null) {
            enabledWorlds.addAll(entryCfg.getStringList("enabled_worlds"));

            for (var trigger : entryTriggerRegistry.getAll()) {
                ConfigurationSection triggerCfg = entryCfg.getConfigurationSection(trigger.getId());
                trigger.loadConfig(triggerCfg);
                trigger.setEnabledWorlds(enabledWorlds);
            }
        }

        // Extract default levels from jar if needed, then load
        extractDefaultLevels();
        loadLevels();
    }

    private Level64637ChunkGenerator.BookConfig loadLibraryBookConfig() {
        // Try data folder first, then fall back to jar resource
        File bookFile = new File(plugin.getDataFolder(), "levels/level_64637_books.yml");
        YamlConfiguration yaml;
        if (bookFile.exists()) {
            yaml = YamlConfiguration.loadConfiguration(bookFile);
        } else {
            InputStream resource = plugin.getResource("levels/level_64637_books.yml");
            if (resource != null) {
                yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(resource, StandardCharsets.UTF_8));
            } else {
                return Level64637ChunkGenerator.BookConfig.DEFAULT;
            }
        }
        return new Level64637ChunkGenerator.BookConfig(
                yaml.getString("gibberish_chars", "abcdefghijklmnopqrstuvwxyz .,;:'-"),
                yaml.getDouble("cursed_chance", 0.15),
                yaml.getStringList("cursed_snippets"),
                yaml.getStringList("cursed_titles"),
                yaml.getStringList("cursed_authors")
        );
    }

    private void extractDefaultLevels() {
        File levelsDir = new File(plugin.getDataFolder(), "levels");
        if (levelsDir.exists()) return;
        levelsDir.mkdirs();

        // Scan the plugin jar for all files under levels/
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(
                plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath())) {
            jar.stream()
                    .filter(e -> e.getName().startsWith("levels/") && !e.isDirectory())
                    .forEach(e -> {
                        try {
                            plugin.saveResource(e.getName(), false);
                        } catch (Exception ex) {
                            plugin.getLogger().warning("Failed to extract " + e.getName() + ": " + ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            plugin.getLogger().warning("Could not scan jar for level files: " + e.getMessage());
        }
    }

    private void loadLevels() {
        File levelsDir = new File(plugin.getDataFolder(), "levels");
        if (!levelsDir.exists() || !levelsDir.isDirectory()) {
            plugin.getLogger().warning("No levels/ directory found — no levels loaded.");
            return;
        }

        File[] levelFiles = levelsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (levelFiles == null || levelFiles.length == 0) {
            plugin.getLogger().warning("No .yml files found in levels/ — no levels loaded.");
            return;
        }

        // Sort by filename for deterministic load order
        Arrays.sort(levelFiles);

        for (File levelFile : levelFiles) {
            YamlConfiguration levelCfg = YamlConfiguration.loadConfiguration(levelFile);

            String levelId = levelCfg.getString("id");
            if (levelId == null || levelId.isEmpty()) {
                plugin.getLogger().warning("Level file " + levelFile.getName() + " missing 'id' — skipping.");
                continue;
            }

            String generatorId = levelCfg.getString("generator", levelId);
            if (!generatorRegistry.has(generatorId)) {
                plugin.getLogger().warning("No generator '" + generatorId
                        + "' for level " + levelId + " (" + levelFile.getName() + ") — skipping.");
                continue;
            }

            ConfigDrivenLevel level = new ConfigDrivenLevel(this, levelId, generatorId, generatorRegistry);
            level.loadFromConfig(levelCfg);
            levelRegistry.register(level);

            // Event config is now stored in ConfigDrivenLevel and applied per-tick
            // by EventScheduler, so each level gets the right config at trigger time.

            // Load per-level entity config
            ConfigurationSection entitiesCfg = levelCfg.getConfigurationSection("entity_config");
            if (entitiesCfg != null) {
                for (String entityId : level.getEntityIds()) {
                    BackroomsEntity entity = entityRegistry.get(entityId);
                    if (entity != null) {
                        entity.loadConfig(entitiesCfg.getConfigurationSection(entityId));
                    }
                }
            }

            plugin.getLogger().info("Loaded level: " + levelId + " (" + level.getDisplayName()
                    + ") from " + levelFile.getName());
        }
    }

    // Accessors
    public JavaPlugin getJavaPlugin() { return plugin; }
    public GeneratorRegistry getGeneratorRegistry() { return generatorRegistry; }
    public LevelRegistry getLevelRegistry() { return levelRegistry; }
    public EventRegistry getEventRegistry() { return eventRegistry; }
    public EntryTriggerRegistry getEntryTriggerRegistry() { return entryTriggerRegistry; }
    public EntityRegistry getEntityRegistry() { return entityRegistry; }
    public PlayerStateManager getPlayerStateManager() { return playerStateManager; }
    public EventScheduler getEventScheduler() { return eventScheduler; }
    public EntitySpawner getEntitySpawner() { return entitySpawner; }
    public TransitionManager getTransitionManager() { return transitionManager; }
}
