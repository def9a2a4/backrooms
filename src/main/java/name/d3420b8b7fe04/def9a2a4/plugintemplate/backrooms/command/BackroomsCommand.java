package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.command;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.TransitionManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.BackroomsLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class BackroomsCommand implements CommandExecutor, TabCompleter {

    private final LevelRegistry levelRegistry;
    private final PlayerStateManager playerStateManager;
    private final TransitionManager transitionManager;

    public BackroomsCommand(LevelRegistry levelRegistry, PlayerStateManager playerStateManager,
                            TransitionManager transitionManager) {
        this.levelRegistry = levelRegistry;
        this.playerStateManager = playerStateManager;
        this.transitionManager = transitionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            return handleEnter(player, "level_0");
        }

        return switch (args[0].toLowerCase()) {
            case "leave" -> handleLeave(player);
            case "goto" -> {
                if (args.length < 2) {
                    player.sendMessage("Usage: /backrooms goto <level_id>");
                    yield true;
                }
                yield handleEnter(player, args[1]);
            }
            case "regenerate" -> handleRegenerate(player, args.length > 1 ? args[1] : null);
            case "status" -> handleStatus(player);
            default -> {
                player.sendMessage("Unknown subcommand. Use: leave, goto, regenerate, status");
                yield true;
            }
        };
    }

    private boolean handleEnter(Player player, String levelId) {
        if (levelRegistry.isBackroomsWorld(player.getWorld())) {
            // Already in backrooms - goto a specific level
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

    private boolean handleRegenerate(Player player, String levelId) {
        if (!player.hasPermission("backrooms.regenerate") && !player.isOp()) {
            player.sendMessage("You don't have permission to regenerate the Backrooms.");
            return true;
        }

        if (levelId != null && !"all".equalsIgnoreCase(levelId)) {
            if (levelRegistry.get(levelId) == null) {
                player.sendMessage("Unknown level: " + levelId);
                return true;
            }
            player.sendMessage("Regenerating level: " + levelId + "...");
            levelRegistry.regenerateLevel(levelId);
            player.sendMessage("Level " + levelId + " has been regenerated.");
        } else {
            player.sendMessage("Regenerating all Backrooms levels...");
            levelRegistry.regenerateAll();
            player.sendMessage("All Backrooms levels have been regenerated.");
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("leave", "goto", "regenerate", "status");
        }
        if (args.length == 2 && ("goto".equalsIgnoreCase(args[0]) || "regenerate".equalsIgnoreCase(args[0]))) {
            List<String> levels = new ArrayList<>();
            if ("regenerate".equalsIgnoreCase(args[0])) {
                levels.add("all");
            }
            for (BackroomsLevel level : levelRegistry.getAll()) {
                levels.add(level.getId());
            }
            return levels;
        }
        return List.of();
    }
}
