package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class VanillaAdvancementBlocker implements Listener {

    private final JavaPlugin plugin;
    private final LevelRegistry levelRegistry;

    public VanillaAdvancementBlocker(JavaPlugin plugin, LevelRegistry levelRegistry) {
        this.plugin = plugin;
        this.levelRegistry = levelRegistry;
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        Advancement advancement = event.getAdvancement();

        if (!advancement.getKey().getNamespace().equals("minecraft")) return;
        if (!levelRegistry.isBackroomsWorld(player.getWorld())) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criterion : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criterion);
            }
        }, 1L);
    }
}
