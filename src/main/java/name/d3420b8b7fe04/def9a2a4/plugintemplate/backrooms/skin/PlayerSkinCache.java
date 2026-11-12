package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.skin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skins of the players who have been on this server, for Alpha's portraits
 * (giant faces, heads and statues built from a real player's skin).
 *
 * Skins are downloaded when a player joins and kept on disk, so portraits can
 * be painted while nobody is online. Chunk generation runs off the main thread
 * and paints one slice of a portrait per chunk, so each canvas is pinned to one
 * skin the first time any of its chunks generates ({@link #skinForCanvas}); the
 * pin is persisted so the remaining chunks — generated later, maybe after a
 * restart — paint the same person. Canvases that generate while no skin is
 * known stay blank: white, faceless.
 */
public final class PlayerSkinCache implements Listener {

    private static final String BLANK = "blank";
    private static volatile PlayerSkinCache instance;

    private final JavaPlugin plugin;
    private final File skinDir;
    private final File assignmentFile;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<UUID, Skin> skins = new ConcurrentHashMap<>();
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final Map<String, String> assignments = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public PlayerSkinCache(JavaPlugin plugin) {
        this.plugin = plugin;
        this.skinDir = new File(plugin.getDataFolder(), "skins");
        this.assignmentFile = new File(plugin.getDataFolder(), "alpha_portraits.yml");
    }

    /** The active cache, or null before the plugin has enabled it. Safe from any thread. */
    public static @Nullable PlayerSkinCache get() {
        return instance;
    }

    public void enable() {
        load();
        instance = this;
        for (Player p : Bukkit.getOnlinePlayers()) {
            online.add(p.getUniqueId());
            fetch(p);
        }
    }

    public void disable() {
        save();
        instance = null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        online.add(event.getPlayer().getUniqueId());
        fetch(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        online.remove(event.getPlayer().getUniqueId());
    }

    /**
     * The skin painted on canvas {@code canvasKey}, pinning one on first use:
     * an online player's skin when any is known, else any known skin, else
     * blank (null). Stable for the life of the canvas.
     */
    public @Nullable Skin skinForCanvas(String canvasKey) {
        String pinned = assignments.computeIfAbsent(canvasKey, key -> {
            dirty = true;
            List<UUID> pool = new ArrayList<>();
            for (UUID id : online) if (skins.containsKey(id)) pool.add(id);
            if (pool.isEmpty()) pool.addAll(skins.keySet());
            if (pool.isEmpty()) return BLANK;
            pool.sort(null);
            return pool.get(Math.floorMod(key.hashCode() * 0x9E3779B9, pool.size())).toString();
        });
        if (BLANK.equals(pinned)) return null;
        try {
            return skins.get(UUID.fromString(pinned));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Download ─────────────────────────────────────────────────────────

    private void fetch(Player player) {
        PlayerTextures textures = player.getPlayerProfile().getTextures();
        URL url = textures.getSkin();
        if (url == null) return; // offline-mode server or no skin set
        boolean slim = textures.getSkinModel() == PlayerTextures.SkinModel.SLIM;
        UUID id = player.getUniqueId();
        String name = player.getName();

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(20)).GET().build();
        } catch (IllegalArgumentException e) {
            return;
        }
        http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(response -> {
            if (response.statusCode() != 200) return;
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                if (image == null) return;
                Skin skin = Skin.fromImage(name, image, slim);
                if (skin == null) return;
                skins.put(id, skin);
                writeSkin(id, skin, image);
            } catch (IOException e) {
                plugin.getLogger().warning("Couldn't read skin for " + name + ": " + e.getMessage());
            }
        }).exceptionally(err -> {
            plugin.getLogger().fine("Skin download failed for " + name + ": " + err.getMessage());
            return null;
        });
    }

    // ── Persistence ──────────────────────────────────────────────────────

    private void writeSkin(UUID id, Skin skin, BufferedImage image) throws IOException {
        if (!skinDir.isDirectory() && !skinDir.mkdirs()) return;
        ImageIO.write(image, "png", new File(skinDir, id + ".png"));
        YamlConfiguration meta = new YamlConfiguration();
        meta.set("name", skin.name());
        meta.set("slim", skin.slim());
        meta.save(new File(skinDir, id + ".yml"));
    }

    private void load() {
        File[] pngs = skinDir.listFiles((dir, n) -> n.endsWith(".png"));
        if (pngs != null) {
            for (File png : pngs) {
                String base = png.getName().substring(0, png.getName().length() - 4);
                try {
                    UUID id = UUID.fromString(base);
                    YamlConfiguration meta = YamlConfiguration.loadConfiguration(new File(skinDir, base + ".yml"));
                    BufferedImage image = ImageIO.read(png);
                    Skin skin = image == null ? null
                            : Skin.fromImage(meta.getString("name", base), image, meta.getBoolean("slim"));
                    if (skin != null) skins.put(id, skin);
                } catch (IllegalArgumentException | IOException ignored) {
                    // not a skin file of ours
                }
            }
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(assignmentFile);
        for (String key : yaml.getKeys(false)) {
            String value = yaml.getString(key);
            if (value != null) assignments.put(key, value);
        }
    }

    public void save() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        assignments.forEach(yaml::set);
        try {
            yaml.save(assignmentFile);
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().warning("Couldn't save " + assignmentFile.getName() + ": " + e.getMessage());
        }
    }
}
