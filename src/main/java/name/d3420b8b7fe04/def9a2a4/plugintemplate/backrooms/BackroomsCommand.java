package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BackroomsCommand implements CommandExecutor, TabCompleter {

    private final BackroomsManager manager;
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public BackroomsCommand(BackroomsManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
            return handleLeave(player);
        }

        return handleEnter(player);
    }

    private boolean handleEnter(Player player) {
        World backrooms = manager.getWorld();
        if (backrooms == null) {
            player.sendMessage("The Backrooms world is not available.");
            return true;
        }

        if (player.getWorld().equals(backrooms)) {
            player.sendMessage("You are already in the Backrooms.");
            return true;
        }

        returnLocations.put(player.getUniqueId(), player.getLocation());
        Location spawn = new Location(backrooms, 8.5, 20, 8.5);
        player.teleport(spawn);
        player.sendMessage("You have entered the Backrooms.");
        return true;
    }

    private boolean handleLeave(Player player) {
        World backrooms = manager.getWorld();
        if (backrooms == null || !player.getWorld().equals(backrooms)) {
            player.sendMessage("You are not in the Backrooms.");
            return true;
        }

        Location returnLoc = returnLocations.remove(player.getUniqueId());
        if (returnLoc == null) {
            World overworld = Bukkit.getWorlds().get(0);
            returnLoc = overworld.getSpawnLocation();
        }

        player.teleport(returnLoc);
        player.sendMessage("You have left the Backrooms.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("leave");
        }
        return List.of();
    }
}
