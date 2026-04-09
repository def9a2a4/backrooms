package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.command.BackroomsCommand;
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
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.impl.Level0;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener.BackroomsListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BackroomsPlugin {

    private final JavaPlugin plugin;
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

    public BackroomsPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
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
        // 1. Register built-in events
        AmbientSoundEvent ambientSound = new AmbientSoundEvent();
        LightBlinkEvent lightBlink = new LightBlinkEvent();
        BlackoutEvent blackout = new BlackoutEvent();
        FakeChatMessageEvent fakeChat = new FakeChatMessageEvent();
        FootstepEchoEvent footstepEcho = new FootstepEchoEvent();

        // Wire event scheduler into light events
        lightBlink.setEventScheduler(eventScheduler);
        blackout.setEventScheduler(eventScheduler);

        eventRegistry.register(ambientSound);
        eventRegistry.register(lightBlink);
        eventRegistry.register(blackout);
        eventRegistry.register(fakeChat);
        eventRegistry.register(footstepEcho);

        // 2. Register built-in entry triggers
        SuffocationEntry suffocation = new SuffocationEntry(plugin);
        VoidFallEntry voidFall = new VoidFallEntry(plugin);
        BedAnomalyEntry bedAnomaly = new BedAnomalyEntry(plugin);
        PortalMislinkEntry portalMislink = new PortalMislinkEntry(plugin);

        entryTriggerRegistry.register(suffocation);
        entryTriggerRegistry.register(voidFall);
        entryTriggerRegistry.register(bedAnomaly);
        entryTriggerRegistry.register(portalMislink);

        // 3. Register levels
        levelRegistry.register(new Level0(this));

        // 4. Load config
        loadConfig();

        // 5. Init events
        for (BackroomsEvent event : eventRegistry.getAll()) {
            event.init(plugin);
        }

        // 6. Create worlds
        levelRegistry.loadWorlds();

        // 7. Start schedulers
        eventScheduler.start();
        entitySpawner.start();
        transitionManager.start();

        // 8. Register Bukkit listeners
        Bukkit.getPluginManager().registerEvents(playerStateManager, plugin);
        Bukkit.getPluginManager().registerEvents(entryManager, plugin);
        Bukkit.getPluginManager().registerEvents(new BackroomsListener(levelRegistry), plugin);

        // 9. Register commands
        BackroomsCommand command = new BackroomsCommand(levelRegistry, playerStateManager, transitionManager);
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
        eventScheduler.stop();
        entitySpawner.stop();
        transitionManager.stop();

        // Restore lights before unloading
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

        // Load entry trigger config
        ConfigurationSection entryCfg = cfg.getConfigurationSection("entry");
        Set<String> enabledWorlds = new HashSet<>();
        if (entryCfg != null) {
            enabledWorlds.addAll(entryCfg.getStringList("enabled_worlds"));

            for (var trigger : entryTriggerRegistry.getAll()) {
                ConfigurationSection triggerCfg = entryCfg.getConfigurationSection(trigger.getId());
                trigger.loadConfig(triggerCfg);
                if (trigger instanceof SuffocationEntry s) s.setEnabledWorlds(enabledWorlds);
                if (trigger instanceof VoidFallEntry v) v.setEnabledWorlds(enabledWorlds);
                if (trigger instanceof BedAnomalyEntry b) b.setEnabledWorlds(enabledWorlds);
                if (trigger instanceof PortalMislinkEntry p) p.setEnabledWorlds(enabledWorlds);
            }
        }

        // Load per-level event config
        for (var level : levelRegistry.getAll()) {
            ConfigurationSection levelCfg = cfg.getConfigurationSection(level.getId());
            if (levelCfg == null) continue;

            ConfigurationSection eventsCfg = levelCfg.getConfigurationSection("events");
            if (eventsCfg != null) {
                for (String eventId : level.getEventIds()) {
                    BackroomsEvent event = eventRegistry.get(eventId);
                    if (event != null) {
                        event.loadConfig(eventsCfg.getConfigurationSection(eventId));
                    }
                }
            }
        }
    }

    // Accessors for other components
    public JavaPlugin getJavaPlugin() { return plugin; }
    public LevelRegistry getLevelRegistry() { return levelRegistry; }
    public EventRegistry getEventRegistry() { return eventRegistry; }
    public EntryTriggerRegistry getEntryTriggerRegistry() { return entryTriggerRegistry; }
    public EntityRegistry getEntityRegistry() { return entityRegistry; }
    public PlayerStateManager getPlayerStateManager() { return playerStateManager; }
    public EventScheduler getEventScheduler() { return eventScheduler; }
    public EntitySpawner getEntitySpawner() { return entitySpawner; }
    public TransitionManager getTransitionManager() { return transitionManager; }
}
