package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.advancement;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;

public class AdvancementManager {

    private static final String NAMESPACE = "backrooms";
    private static final String CRITERION = "impossible";

    private final JavaPlugin plugin;

    private final NamespacedKey rootKey = key("root");
    private final NamespacedKey escapeKey = key("escape/overworld");
    private final NamespacedKey allLevelsKey = key("escape/all_levels");
    private final NamespacedKey gardenKey = key("level/level_1_garden");

    private static final Set<String> ALL_LEVEL_IDS = Set.of(
            "level_0", "level_1", "level_2", "level_3", "level_4",
            "level_5", "level_7", "level_37", "level_64637", "level_94", "level_84"
    );

    private final Map<String, NamespacedKey> entryKeys = Map.of(
            "suffocation_noclip", key("entry/suffocation"),
            "void_fall", key("entry/void"),
            "bed_anomaly", key("entry/bed"),
            "aether_portal", key("entry/aether"),
            "herobrine_shrine", key("entry/shrine"),
            "twilight_portal", key("entry/twilight")
    );

    private final Map<String, NamespacedKey> levelKeys = Map.ofEntries(
            Map.entry("level_0", key("level/level_0")),
            Map.entry("level_1", key("level/level_1")),
            Map.entry("level_2", key("level/level_2")),
            Map.entry("level_3", key("level/level_3")),
            Map.entry("level_4", key("level/level_4")),
            Map.entry("level_5", key("level/level_5")),
            Map.entry("level_7", key("level/level_7")),
            Map.entry("level_37", key("level/level_37")),
            Map.entry("level_64637", key("level/level_64637")),
            Map.entry("level_94", key("level/level_94")),
            Map.entry("level_84", key("level/level_84"))
    );

    // Exit hints: keyed by "currentLevel:targetLevel" or "currentLevel:exitType" for ambiguous cases
    private final Map<String, NamespacedKey> exitHintKeys = Map.ofEntries(
            Map.entry("level_0:level_1", key("hint/hint_0")),
            Map.entry("level_1:level_2", key("hint/hint_1_down")),
            Map.entry("level_1:level_94", key("hint/hint_1_up")),
            Map.entry("level_2:level_37", key("hint/hint_2")),
            Map.entry("level_3:level_4", key("hint/hint_3")),
            Map.entry("level_3:level_5", key("hint/hint_3")),
            Map.entry("level_3:level_7", key("hint/hint_3")),
            Map.entry("level_3:level_64637", key("hint/hint_3")),
            Map.entry("level_4:level_3", key("hint/hint_4")),
            Map.entry("level_7:overworld", key("hint/hint_7")),
            Map.entry("level_37:level_3", key("hint/hint_37")),
            Map.entry("level_64637:overworld", key("hint/hint_64637_fall")),
            Map.entry("level_84:level_4", key("hint/hint_84_down")),
            Map.entry("level_84:level_3", key("hint/hint_84_up"))
    );

    // Direct hint keys for special triggers (L5 jukebox, L94 cascade)
    private final NamespacedKey hint5Key = key("hint/hint_5");
    private final NamespacedKey hint94Key = key("hint/hint_94");

    public AdvancementManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void grantEntry(Player player, String triggerId) {
        grant(player, rootKey);
        NamespacedKey entryKey = entryKeys.get(triggerId);
        if (entryKey != null) {
            grant(player, entryKey);
        }
    }

    public void grantLevelDiscovery(Player player, String levelId) {
        // Grant the full structural chain so the entire tree is visible
        grantStructuralChain(player);
        NamespacedKey discoveryKey = levelKeys.get(levelId);
        if (discoveryKey != null) {
            grant(player, discoveryKey);
        }
        checkAllLevelsVisited(player);
    }

    /**
     * Grants root + all entries + all level discoveries + garden so that
     * the entire hint/escape layer is visible in the advancement screen.
     * These are structural — their JSONs have show_toast:false.
     */
    private void grantStructuralChain(Player player) {
        grant(player, rootKey);
        for (NamespacedKey entryKey : entryKeys.values()) {
            grant(player, entryKey);
        }
        for (NamespacedKey levelKey : levelKeys.values()) {
            grant(player, levelKey);
        }
        grant(player, gardenKey);
    }

    /**
     * Grants the hint advancement for the exit used.
     * The hint is determined by currentLevel + targetLevel.
     */
    public void grantExitHint(Player player, String currentLevelId, String targetLevelId,
                              String exitTriggerType) {
        NamespacedKey hintKey = exitHintKeys.get(currentLevelId + ":" + targetLevelId);
        if (hintKey != null) {
            grant(player, hintKey);
        }
    }

    /** Grant hint_5 — L5 jukebox death (called from Disc11JukeboxListener). */
    public void grantDisc11Hint(Player player) {
        grant(player, hint5Key);
    }

    /** Grant hint_94 — L94 barrier cascade (called from Level94Listener). */
    public void grantSkyblockHint(Player player) {
        grant(player, hint94Key);
    }

    /** Grant the garden discovery advancement (called from Level1GardenEffectListener). */
    public void grantGardenDiscovery(Player player) {
        grant(player, gardenKey);
    }

    public void grantEscape(Player player) {
        grant(player, escapeKey);
    }

    private void checkAllLevelsVisited(Player player) {
        for (String levelId : ALL_LEVEL_IDS) {
            NamespacedKey levelKey = levelKeys.get(levelId);
            if (levelKey == null) return;
            Advancement adv = Bukkit.getAdvancement(levelKey);
            if (adv == null) return;
            if (!player.getAdvancementProgress(adv).isDone()) return;
        }
        grant(player, allLevelsKey);
    }

    private void grant(Player player, NamespacedKey key) {
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            plugin.getLogger().warning("Advancement not found: " + key
                    + " — is the backrooms datapack loaded?");
            return;
        }
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (!progress.isDone()) {
            progress.awardCriteria(CRITERION);
        }
    }

    private static NamespacedKey key(String path) {
        return new NamespacedKey(NAMESPACE, path);
    }
}
