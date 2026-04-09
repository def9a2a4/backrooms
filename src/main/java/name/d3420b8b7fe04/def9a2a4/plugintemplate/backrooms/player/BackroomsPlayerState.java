package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BackroomsPlayerState {

    private UUID playerId;
    private String currentLevelId;
    private long entryTimestamp;
    private long totalTicksInBackrooms;
    private Set<String> levelsVisited = new HashSet<>();
    private Set<String> itemsCollected = new HashSet<>();
    private Map<String, String> customData = new HashMap<>();

    // Return location (stored separately since Location isn't trivially serializable)
    private String returnWorldName;
    private double returnX, returnY, returnZ;
    private float returnYaw, returnPitch;

    // Escalation thresholds in minutes (configurable)
    private static int[] escalationThresholds = {5, 15, 30, 60, 120};

    public BackroomsPlayerState() {}

    public BackroomsPlayerState(UUID playerId) {
        this.playerId = playerId;
        this.entryTimestamp = System.currentTimeMillis();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getCurrentLevelId() {
        return currentLevelId;
    }

    public void setCurrentLevelId(String currentLevelId) {
        this.currentLevelId = currentLevelId;
        if (currentLevelId != null) {
            levelsVisited.add(currentLevelId);
        }
    }

    public long getEntryTimestamp() {
        return entryTimestamp;
    }

    public void setEntryTimestamp(long entryTimestamp) {
        this.entryTimestamp = entryTimestamp;
    }

    public long getTotalTicksInBackrooms() {
        return totalTicksInBackrooms;
    }

    public void addTicks(long ticks) {
        this.totalTicksInBackrooms += ticks;
    }

    public Set<String> getLevelsVisited() {
        return levelsVisited;
    }

    public Set<String> getItemsCollected() {
        return itemsCollected;
    }

    public void collectItem(String itemId) {
        itemsCollected.add(itemId);
    }

    public Map<String, String> getCustomData() {
        return customData;
    }

    public void setCustomData(String key, String value) {
        customData.put(key, value);
    }

    public String getCustomData(String key) {
        return customData.get(key);
    }

    public void setReturnLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        this.returnWorldName = loc.getWorld().getName();
        this.returnX = loc.getX();
        this.returnY = loc.getY();
        this.returnZ = loc.getZ();
        this.returnYaw = loc.getYaw();
        this.returnPitch = loc.getPitch();
    }

    public Location getReturnLocation() {
        if (returnWorldName == null) return null;
        World world = Bukkit.getWorld(returnWorldName);
        if (world == null) return null;
        return new Location(world, returnX, returnY, returnZ, returnYaw, returnPitch);
    }

    public int getEscalationLevel() {
        long minutesIn = totalTicksInBackrooms / (20 * 60);
        for (int i = escalationThresholds.length - 1; i >= 0; i--) {
            if (minutesIn >= escalationThresholds[i]) return i + 1;
        }
        return 0;
    }

    public static void setEscalationThresholds(int[] thresholds) {
        escalationThresholds = thresholds;
    }

    public void clear() {
        currentLevelId = null;
        entryTimestamp = 0;
        totalTicksInBackrooms = 0;
        levelsVisited.clear();
        itemsCollected.clear();
        customData.clear();
        returnWorldName = null;
    }
}
