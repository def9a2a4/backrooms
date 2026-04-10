package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.command;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level37ChunkGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.BackroomsEntity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntitySpawner;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryTriggerRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.BackroomsEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.EventRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.TransitionManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.GeneratorRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.BackroomsLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BackroomsCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final LevelRegistry levelRegistry;
    private final PlayerStateManager playerStateManager;
    private final TransitionManager transitionManager;
    private final EventRegistry eventRegistry;
    private final EntityRegistry entityRegistry;
    private final EntryTriggerRegistry entryTriggerRegistry;
    private final EntitySpawner entitySpawner;
    private final GeneratorRegistry generatorRegistry;

    private final Set<UUID> debug37Players = new HashSet<>();
    private BukkitTask debug37Task;

    public BackroomsCommand(JavaPlugin plugin, LevelRegistry levelRegistry, PlayerStateManager playerStateManager,
                            TransitionManager transitionManager, EventRegistry eventRegistry,
                            EntityRegistry entityRegistry, EntryTriggerRegistry entryTriggerRegistry,
                            EntitySpawner entitySpawner, GeneratorRegistry generatorRegistry) {
        this.plugin = plugin;
        this.levelRegistry = levelRegistry;
        this.playerStateManager = playerStateManager;
        this.transitionManager = transitionManager;
        this.eventRegistry = eventRegistry;
        this.entityRegistry = entityRegistry;
        this.entryTriggerRegistry = entryTriggerRegistry;
        this.entitySpawner = entitySpawner;
        this.generatorRegistry = generatorRegistry;
        startDebug37Task();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
            return handleEnter(player, "level_0");
        }

        return switch (args[0].toLowerCase()) {
            case "regenerate" -> handleRegenerate(sender, args.length > 1 ? args[1] : null);
            case "list" -> handleList(sender, args.length > 1 ? args[1] : null);
            case "leave", "goto", "status", "event", "spawn", "despawn", "enter", "escalation", "reset", "debug37" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This command can only be used by players.");
                    yield true;
                }
                yield switch (args[0].toLowerCase()) {
                    case "leave" -> handleLeave(player);
                    case "goto" -> {
                        if (!player.hasPermission("backrooms.admin") && !player.isOp()) {
                            player.sendMessage("You don't have permission to use this command.");
                            yield true;
                        }
                        if (args.length < 2) {
                            player.sendMessage("Usage: /backrooms goto <level_id>");
                            yield true;
                        }
                        yield handleEnter(player, args[1]);
                    }
                    case "status" -> handleStatus(player);
                    case "event" -> {
                        if (args.length < 2) {
                            player.sendMessage("Usage: /backrooms event <event_id>");
                            yield true;
                        }
                        yield handleEvent(player, args[1]);
                    }
                    case "spawn" -> {
                        if (args.length < 2) {
                            player.sendMessage("Usage: /backrooms spawn <entity_id>");
                            yield true;
                        }
                        yield handleSpawn(player, args[1]);
                    }
                    case "despawn" -> handleDespawn(player);
                    case "enter" -> {
                        if (args.length < 2) {
                            player.sendMessage("Usage: /backrooms enter <trigger_id>");
                            yield true;
                        }
                        yield handleTriggerEntry(player, args[1]);
                    }
                    case "escalation" -> {
                        if (args.length < 2) {
                            player.sendMessage("Usage: /backrooms escalation <level>");
                            yield true;
                        }
                        yield handleEscalation(player, args[1]);
                    }
                    case "reset" -> handleReset(player);
                    case "debug37" -> handleDebug37(player);
                    default -> true;
                };
            }
            default -> {
                sender.sendMessage("Unknown subcommand. Use: leave, goto, regenerate, status, event, spawn, despawn, enter, escalation, reset, list, debug37");
                yield true;
            }
        };
    }

    private boolean handleEnter(Player player, String levelId) {
        if (levelRegistry.isBackroomsWorld(player.getWorld())) {
            if (!player.hasPermission("backrooms.admin") && !player.isOp()) {
                player.sendMessage("You are already in the Backrooms.");
                return true;
            }
            BackroomsLevel targetLevel = levelRegistry.get(levelId);
            if (targetLevel == null) {
                player.sendMessage("Unknown level: " + levelId);
                return true;
            }
            World targetWorld = levelRegistry.getWorld(targetLevel);
            if (targetWorld == null) {
                player.sendMessage("Level world not loaded: " + levelId);
                return true;
            }
            BackroomsPlayerState state = playerStateManager.getOrCreate(player);
            player.teleport(targetWorld.getSpawnLocation());
            state.setCurrentLevelId(levelId);
            player.sendMessage("Teleported to " + targetLevel.getDisplayName() + ".");
            return true;
        }

        BackroomsLevel level = levelRegistry.get(levelId);
        if (level == null) {
            player.sendMessage("Unknown level: " + levelId);
            return true;
        }

        World targetWorld = levelRegistry.getWorld(level);
        if (targetWorld == null) {
            player.sendMessage("The Backrooms world is not available.");
            return true;
        }

        BackroomsPlayerState state = playerStateManager.getOrCreate(player);
        state.setReturnLocation(player.getLocation());
        transitionManager.enterBackrooms(player, state, levelId);
        return true;
    }

    private boolean handleLeave(Player player) {
        if (!levelRegistry.isBackroomsWorld(player.getWorld())) {
            player.sendMessage("You are not in the Backrooms.");
            return true;
        }

        BackroomsPlayerState state = playerStateManager.getOrCreate(player);
        BackroomsLevel currentLevel = levelRegistry.getByWorld(player.getWorld());
        transitionManager.returnToOverworld(player, state, currentLevel);
        player.sendMessage("You have left the Backrooms.");
        return true;
    }

    private boolean handleRegenerate(CommandSender sender, String levelId) {
        if (!sender.hasPermission("backrooms.regenerate") && !sender.isOp()) {
            sender.sendMessage("You don't have permission to regenerate the Backrooms.");
            return true;
        }

        if (levelId != null && !"all".equalsIgnoreCase(levelId)) {
            if (levelRegistry.get(levelId) == null) {
                sender.sendMessage("Unknown level: " + levelId);
                return true;
            }
            sender.sendMessage("Regenerating level: " + levelId + "...");
            levelRegistry.regenerateLevel(levelId);
            sender.sendMessage("Level " + levelId + " has been regenerated.");
        } else {
            sender.sendMessage("Regenerating all Backrooms levels...");
            levelRegistry.regenerateAll();
            sender.sendMessage("All Backrooms levels have been regenerated.");
        }
        return true;
    }

    private boolean handleStatus(Player player) {
        BackroomsPlayerState state = playerStateManager.get(player);
        if (state == null || !levelRegistry.isBackroomsWorld(player.getWorld())) {
            player.sendMessage("You are not in the Backrooms.");
            return true;
        }

        BackroomsLevel level = levelRegistry.getByWorld(player.getWorld());
        String levelName = level != null ? level.getDisplayName() : "Unknown";
        long minutes = state.getTotalTicksInBackrooms() / (20 * 60);
        int escalation = state.getEscalationLevel();

        player.sendMessage("--- Backrooms Status ---");
        player.sendMessage("Level: " + levelName);
        player.sendMessage("Time: " + minutes + " minutes");
        player.sendMessage("Escalation: " + escalation);
        player.sendMessage("Levels visited: " + state.getLevelsVisited());
        return true;
    }

    // --- Admin/debug subcommands ---

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("backrooms.admin") && !sender.isOp()) {
            sender.sendMessage("You don't have permission to use this command.");
            return false;
        }
        return true;
    }

    private boolean handleEvent(Player player, String eventId) {
        if (!requireAdmin(player)) return true;

        if (!levelRegistry.isBackroomsWorld(player.getWorld())) {
            player.sendMessage("You must be in the Backrooms to trigger an event.");
            return true;
        }

        BackroomsEvent event = eventRegistry.get(eventId);
        if (event == null) {
            player.sendMessage("Unknown event: " + eventId);
            return true;
        }

        BackroomsPlayerState state = playerStateManager.getOrCreate(player);
        event.trigger(player, state);
        player.sendMessage("Triggered event: " + eventId);
        return true;
    }

    private boolean handleSpawn(Player player, String entityId) {
        if (!requireAdmin(player)) return true;

        if (!levelRegistry.isBackroomsWorld(player.getWorld())) {
            player.sendMessage("You must be in the Backrooms to spawn an entity.");
            return true;
        }

        BackroomsEntity entity = entityRegistry.get(entityId);
        if (entity == null) {
            player.sendMessage("Unknown entity: " + entityId);
            return true;
        }

        EntityHandle handle = entitySpawner.spawnFor(player, entity);
        if (handle == null) {
            player.sendMessage("Failed to spawn entity: " + entityId);
            return true;
        }

        player.sendMessage("Spawned entity: " + entityId);
        return true;
    }

    private boolean handleDespawn(Player player) {
        if (!requireAdmin(player)) return true;

        int count = 0;
        Iterator<EntityHandle> iter = entitySpawner.getActiveEntities().iterator();
        while (iter.hasNext()) {
            EntityHandle handle = iter.next();
            if (handle.targetPlayerUuid().equals(player.getUniqueId())) {
                BackroomsEntity type = entityRegistry.get(handle.entityId());
                if (type != null) type.despawn(handle);
                iter.remove();
                count++;
            }
        }
        player.sendMessage("Despawned " + count + " entity/entities.");
        return true;
    }

    private boolean handleTriggerEntry(Player player, String triggerId) {
        if (!requireAdmin(player)) return true;

        if (levelRegistry.isBackroomsWorld(player.getWorld())) {
            player.sendMessage("You are already in the Backrooms. Use /backrooms goto <level> instead.");
            return true;
        }

        EntryTrigger trigger = entryTriggerRegistry.get(triggerId);
        if (trigger == null) {
            player.sendMessage("Unknown entry trigger: " + triggerId);
            return true;
        }

        BackroomsPlayerState state = playerStateManager.getOrCreate(player);
        state.setReturnLocation(player.getLocation());

        String targetLevel = trigger.getTargetLevel();
        trigger.playEntrySequence(player, () ->
                transitionManager.enterBackrooms(player, state, targetLevel));
        player.sendMessage("Playing entry sequence: " + triggerId);
        return true;
    }

    private boolean handleEscalation(Player player, String levelStr) {
        if (!requireAdmin(player)) return true;

        int targetLevel;
        try {
            targetLevel = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            player.sendMessage("Escalation level must be a number.");
            return true;
        }

        if (targetLevel < 0) {
            player.sendMessage("Escalation level must be >= 0.");
            return true;
        }

        BackroomsPlayerState state = playerStateManager.getOrCreate(player);

        if (targetLevel == 0) {
            state.setTotalTicksInBackrooms(0);
        } else {
            int[] thresholds = BackroomsPlayerState.getEscalationThresholds();
            if (targetLevel > thresholds.length) {
                player.sendMessage("Max escalation level is " + thresholds.length + ".");
                return true;
            }
            long ticks = (long) thresholds[targetLevel - 1] * 20 * 60;
            state.setTotalTicksInBackrooms(ticks);
        }

        player.sendMessage("Escalation set to " + state.getEscalationLevel() + ".");
        return true;
    }

    private boolean handleReset(Player player) {
        if (!requireAdmin(player)) return true;

        BackroomsPlayerState state = playerStateManager.getOrCreate(player);

        if (levelRegistry.isBackroomsWorld(player.getWorld())) {
            BackroomsLevel currentLevel = levelRegistry.getByWorld(player.getWorld());
            transitionManager.returnToOverworld(player, state, currentLevel);
        }

        state.clear();
        player.sendMessage("Player state has been reset.");
        return true;
    }

    private boolean handleDebug37(Player player) {
        if (!requireAdmin(player)) return true;

        BackroomsLevel level = levelRegistry.getByWorld(player.getWorld());
        if (level == null || !"level_37".equals(level.getId())) {
            player.sendMessage("You must be in Level 37 (The Poolrooms) to use this command.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (debug37Players.remove(uuid)) {
            player.sendMessage("§7Debug overlay disabled.");
        } else {
            debug37Players.add(uuid);
            player.sendMessage("§7Debug overlay enabled.");
        }
        return true;
    }

    private void startDebug37Task() {
        debug37Task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<UUID> it = debug37Players.iterator();
            while (it.hasNext()) {
                Player player = Bukkit.getPlayer(it.next());
                if (player == null || !player.isOnline()) {
                    it.remove();
                    continue;
                }
                BackroomsLevel level = levelRegistry.getByWorld(player.getWorld());
                if (level == null || !"level_37".equals(level.getId())) {
                    it.remove();
                    continue;
                }
                Location loc = player.getLocation();
                String info = Level37ChunkGenerator.getDebugInfo(
                        loc.getBlockX(), loc.getBlockZ(), player.getWorld().getSeed());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(info));
            }
        }, 0L, 10L);
    }

    public void shutdown() {
        if (debug37Task != null) {
            debug37Task.cancel();
        }
    }

    private boolean handleList(CommandSender sender, String category) {
        if (!requireAdmin(sender)) return true;

        if (category == null) {
            sender.sendMessage("--- Backrooms Registries ---");
            sender.sendMessage("Levels: " + levelRegistry.getAll().size());
            sender.sendMessage("Events: " + eventRegistry.getAll().size());
            sender.sendMessage("Entities: " + entityRegistry.getAll().size());
            sender.sendMessage("Entry triggers: " + entryTriggerRegistry.getAll().size());
            sender.sendMessage("Generators: " + generatorRegistry.getIds().size());
            sender.sendMessage("Use /backrooms list <category> for details.");
            return true;
        }

        switch (category.toLowerCase()) {
            case "levels" -> {
                sender.sendMessage("--- Levels ---");
                for (BackroomsLevel level : levelRegistry.getAll()) {
                    sender.sendMessage("  " + level.getId() + " - " + level.getDisplayName());
                }
            }
            case "events" -> {
                sender.sendMessage("--- Events ---");
                for (BackroomsEvent event : eventRegistry.getAll()) {
                    sender.sendMessage("  " + event.getId());
                }
            }
            case "entities" -> {
                sender.sendMessage("--- Entities ---");
                for (BackroomsEntity entity : entityRegistry.getAll()) {
                    sender.sendMessage("  " + entity.getId());
                }
            }
            case "triggers" -> {
                sender.sendMessage("--- Entry Triggers ---");
                for (EntryTrigger trigger : entryTriggerRegistry.getAll()) {
                    sender.sendMessage("  " + trigger.getId());
                }
            }
            case "generators" -> {
                sender.sendMessage("--- Generators ---");
                for (String id : generatorRegistry.getIds()) {
                    sender.sendMessage("  " + id);
                }
            }
            default -> sender.sendMessage("Unknown category. Use: levels, events, entities, triggers, generators");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("leave", "status"));
            if (sender.hasPermission("backrooms.admin") || sender.isOp()) {
                subs.addAll(List.of("goto", "event", "spawn", "despawn", "enter", "escalation", "reset", "list", "debug37"));
            }
            if (sender.hasPermission("backrooms.regenerate") || sender.isOp()) {
                subs.add("regenerate");
            }
            return filterStartsWith(subs, args[0]);
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "goto" -> filterStartsWith(levelIds(), args[1]);
                case "regenerate" -> {
                    List<String> opts = new ArrayList<>();
                    opts.add("all");
                    opts.addAll(levelIds());
                    yield filterStartsWith(opts, args[1]);
                }
                case "event" -> filterStartsWith(eventIds(), args[1]);
                case "spawn" -> filterStartsWith(entityIds(), args[1]);
                case "enter" -> filterStartsWith(triggerIds(), args[1]);
                case "escalation" -> filterStartsWith(List.of("0", "1", "2", "3", "4", "5"), args[1]);
                case "list" -> filterStartsWith(
                        List.of("levels", "events", "entities", "triggers", "generators"), args[1]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> levelIds() {
        List<String> ids = new ArrayList<>();
        for (BackroomsLevel level : levelRegistry.getAll()) {
            ids.add(level.getId());
        }
        return ids;
    }

    private List<String> eventIds() {
        List<String> ids = new ArrayList<>();
        for (BackroomsEvent event : eventRegistry.getAll()) {
            ids.add(event.getId());
        }
        return ids;
    }

    private List<String> entityIds() {
        List<String> ids = new ArrayList<>();
        for (BackroomsEntity entity : entityRegistry.getAll()) {
            ids.add(entity.getId());
        }
        return ids;
    }

    private List<String> triggerIds() {
        List<String> ids = new ArrayList<>();
        for (EntryTrigger trigger : entryTriggerRegistry.getAll()) {
            ids.add(trigger.getId());
        }
        return ids;
    }

    private static List<String> filterStartsWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                filtered.add(option);
            }
        }
        return filtered;
    }
}
