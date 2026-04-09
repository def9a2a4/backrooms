package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStateManager implements Listener {

    private final Map<UUID, BackroomsPlayerState> states = new HashMap<>();
    private final NamespacedKey stateKey;
    private final Gson gson;

    public PlayerStateManager(JavaPlugin plugin) {
        this.stateKey = new NamespacedKey(plugin, "backrooms_state");
        this.gson = new GsonBuilder().create();
    }

    public BackroomsPlayerState getOrCreate(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), uuid -> {
            BackroomsPlayerState loaded = loadFromPDC(player);
            return loaded != null ? loaded : new BackroomsPlayerState(uuid);
        });
    }

    @Nullable
    public BackroomsPlayerState get(Player player) {
        return states.get(player.getUniqueId());
    }

    public void remove(Player player) {
        BackroomsPlayerState state = states.remove(player.getUniqueId());
        if (state != null) {
            // Clear PDC on removal
            player.getPersistentDataContainer().remove(stateKey);
        }
    }

    public void saveToPDC(Player player) {
        BackroomsPlayerState state = states.get(player.getUniqueId());
        if (state == null) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(stateKey, PersistentDataType.STRING, gson.toJson(state));
    }

    @Nullable
    private BackroomsPlayerState loadFromPDC(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String json = pdc.get(stateKey, PersistentDataType.STRING);
        if (json == null) return null;
        try {
            return gson.fromJson(json, BackroomsPlayerState.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, BackroomsPlayerState> entry : states.entrySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                saveToPDC(player);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        BackroomsPlayerState loaded = loadFromPDC(event.getPlayer());
        if (loaded != null) {
            states.put(event.getPlayer().getUniqueId(), loaded);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        saveToPDC(event.getPlayer());
        states.remove(event.getPlayer().getUniqueId());
    }
}
