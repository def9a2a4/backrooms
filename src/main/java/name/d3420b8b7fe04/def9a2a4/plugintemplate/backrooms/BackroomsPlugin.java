package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.command.BackroomsCommand;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.BackroomsEntity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntitySpawner;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityTypeRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.EntityBehaviorRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior.impl.*;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl.FloatingHeadEntity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl.MannequinEntity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryTriggerRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl.BedAnomalyEntry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl.AetherPortalEntry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl.HerobrineShrineEntry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl.SuffocationEntry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl.VoidFallEntry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.BackroomsEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.EventRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.EventScheduler;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl.*;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.ExitEventListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.ExitTriggerRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.TransitionManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl.*;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.GeneratorRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.ConfigDrivenLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.DimensionTypeHelper;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.BackroomsListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.Level1WaterDripListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.LibraryBookshelfListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.Disc11JukeboxListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.Level1GardenEffectListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.LobbyBookshelfListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.ServerRoomLecternListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.LibraryWrapListener;
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
    private final ExitTriggerRegistry exitTriggerRegistry;
    private final EntityTypeRegistry entityTypeRegistry = new EntityTypeRegistry();
    private final EntityBehaviorRegistry behaviorRegistry = new EntityBehaviorRegistry();
    private final PlayerStateManager playerStateManager;
    private final EventScheduler eventScheduler;
    private final EntitySpawner entitySpawner;
    private final TransitionManager transitionManager;
    private final EntryManager entryManager;

    private BukkitTask autoSaveTask;
    private BackroomsCommand command;
    private Level1GardenEffectListener gardenEffectListener;

    public BackroomsPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
        this.generatorRegistry = new GeneratorRegistry();
        this.levelRegistry = new LevelRegistry(plugin.getLogger());
        this.eventRegistry = new EventRegistry();
        this.entryTriggerRegistry = new EntryTriggerRegistry();
        this.exitTriggerRegistry = new ExitTriggerRegistry();
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

        eventRegistry.register(new WallShiftEvent());
        eventRegistry.register(new SignPlacementEvent());

        // 3. Register built-in entities
        entityRegistry.register(new name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl.HerobrineEntity());
        entityRegistry.register(new name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl.PursuerEntity());
        entityRegistry.register(new name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.impl.WrongEndermanEntity());

        // 3b. Register entity behaviors and type factories (for data-driven entity_instances)
        behaviorRegistry.register("weeping_angel", WeepingAngelBehavior::new);
        behaviorRegistry.register("stalk", StalkerBehavior::new);
        behaviorRegistry.register("flee", FleeBehavior::new);
        behaviorRegistry.register("attack", AttackBehavior::new);
        behaviorRegistry.register("stationary_stare", StationaryStareBehavior::new);
        behaviorRegistry.register("patrol", () -> new PatrolBehavior(0.04, 100));

        entityTypeRegistry.register("floating_head", (instanceId, cfg) -> {
            FloatingHeadEntity e = new FloatingHeadEntity(instanceId, behaviorRegistry);
            e.loadConfig(cfg);
            return e;
        });
        entityTypeRegistry.register("mannequin", (instanceId, cfg) -> {
            MannequinEntity e = new MannequinEntity(instanceId, behaviorRegistry);
            e.loadConfig(cfg);
            return e;
        });

        // 4a. Register exit trigger types
        exitTriggerRegistry.register("below_y", BelowYTrigger::new);
        exitTriggerRegistry.register("above_y", AboveYTrigger::new);
        exitTriggerRegistry.register("submerged_below_y", SubmergedBelowYTrigger::new);
        exitTriggerRegistry.register("collect_items", CollectItemsTrigger::new);
        exitTriggerRegistry.register("walk_distance", WalkDistanceTrigger::new);
        exitTriggerRegistry.register("powered_command_block", PoweredCommandBlockTrigger::new);
        exitTriggerRegistry.register("lever_pipe", LeverPipeTrigger::new);
        exitTriggerRegistry.register("fall_distance", FallDistanceTrigger::new);

        // 4b. Register built-in entry triggers
        SuffocationEntry suffocation = new SuffocationEntry(plugin);
        VoidFallEntry voidFall = new VoidFallEntry(plugin);
        BedAnomalyEntry bedAnomaly = new BedAnomalyEntry(plugin);
        AetherPortalEntry aetherPortal = new AetherPortalEntry(plugin);
        HerobrineShrineEntry herobrineShrine = new HerobrineShrineEntry(plugin);

        entryTriggerRegistry.register(suffocation);
        entryTriggerRegistry.register(voidFall);
        entryTriggerRegistry.register(bedAnomaly);
        entryTriggerRegistry.register(aetherPortal);
        entryTriggerRegistry.register(herobrineShrine);

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
        gardenEffectListener = new Level1GardenEffectListener(plugin);
        gardenEffectListener.start();

        // 8. Register Bukkit listeners
        Bukkit.getPluginManager().registerEvents(playerStateManager, plugin);
        Bukkit.getPluginManager().registerEvents(entryManager, plugin);
        Bukkit.getPluginManager().registerEvents(
                new ExitEventListener(levelRegistry, playerStateManager, transitionManager), plugin);
        Bukkit.getPluginManager().registerEvents(new BackroomsListener(levelRegistry), plugin);
        Bukkit.getPluginManager().registerEvents(new LobbyBookshelfListener(plugin), plugin);
        Bukkit.getPluginManager().registerEvents(new Disc11JukeboxListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new ServerRoomLecternListener(plugin), plugin);
        Bukkit.getPluginManager().registerEvents(new LibraryBookshelfListener(plugin, loadLibraryBookConfig()), plugin);
        Bukkit.getPluginManager().registerEvents(new LibraryWrapListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new Level1WaterDripListener(plugin), plugin);
        Bukkit.getPluginManager().registerEvents(
                new name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.Level94Listener(
                        plugin, levelRegistry, playerStateManager), plugin);

        // 9. Register commands (Paper plugins don't support YAML-based commands)
        command = new BackroomsCommand(plugin, levelRegistry, playerStateManager, transitionManager,
                eventRegistry, entityRegistry, entryTriggerRegistry, entitySpawner, generatorRegistry);
        org.bukkit.command.Command backroomsCmd = new org.bukkit.command.Command(
                "backrooms", "Backrooms dimension commands",
                "/backrooms [leave|goto|regenerate|status|event|spawn|despawn|enter|escalation|reset|list]",
                java.util.List.of()) {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                return command.onCommand(sender, this, label, args);
            }
            @Override
            public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
                java.util.List<String> result = command.onTabComplete(sender, this, alias, args);
                return result != null ? result : java.util.List.of();
            }
        };
        backroomsCmd.setPermission("backrooms.use");
        Bukkit.getCommandMap().register("backrooms", backroomsCmd);

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
        if (gardenEffectListener != null) gardenEffectListener.stop();

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
        if (!levelsDir.exists()) levelsDir.mkdirs();

        // Scan the plugin jar for all files under levels/
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(
                plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath())) {
            jar.stream()
                    .filter(e -> e.getName().startsWith("levels/") && !e.isDirectory())
                    .forEach(e -> {
                        try {
                            plugin.saveResource(e.getName(), true);
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

        File[] levelFiles = levelsDir.listFiles((dir, name) -> name.endsWith(".yml") && !name.endsWith("_books.yml"));
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

            ConfigDrivenLevel level = new ConfigDrivenLevel(this, levelId, generatorId, generatorRegistry, exitTriggerRegistry);
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

            // Load data-driven entity instances
            ConfigurationSection instancesCfg = levelCfg.getConfigurationSection("entity_instances");
            if (instancesCfg != null) {
                for (String instanceId : instancesCfg.getKeys(false)) {
                    ConfigurationSection instanceCfg = instancesCfg.getConfigurationSection(instanceId);
                    if (instanceCfg == null) continue;
                    String typeId = instanceCfg.getString("type");
                    if (typeId == null) {
                        plugin.getLogger().warning("Entity instance '" + instanceId
                                + "' in " + levelFile.getName() + " missing 'type' — skipping.");
                        continue;
                    }
                    BackroomsEntity entity = entityTypeRegistry.create(typeId, instanceId, instanceCfg);
                    if (entity == null) {
                        plugin.getLogger().warning("Unknown entity type '" + typeId
                                + "' for instance '" + instanceId + "' in " + levelFile.getName());
                        continue;
                    }
                    entityRegistry.register(entity);
                    level.addEntityId(instanceId);
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
