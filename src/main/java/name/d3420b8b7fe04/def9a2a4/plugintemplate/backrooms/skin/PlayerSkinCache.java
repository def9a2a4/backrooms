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
 * Skins are downloaded when a player joins (on offline-mode servers, by looking
 * the username up with Mojang) and kept on disk, so portraits can
 * be painted while nobody is online. Chunk generation runs off the main thread
 * and paints one slice of a portrait per chunk, so each canvas is pinned to one
 * skin the first time any of its chunks generates ({@link #skinForCanvas}); the
 * pin is persisted so the remaining chunks — generated later, maybe after a
 * restart — paint the same person. Canvases that generate while no skin is
 * known stay blank: white, faceless.
 *
 * <p>A server with few players would paint the same few faces everywhere, so
 * while fewer than {@link #POOL} players are known the cache is topped up with
 * the skins of well-known accounts: Mojang staff and the YouTubers of the old
 * days, picked at random and looked up by name.
 */
public final class PlayerSkinCache implements Listener {

    private static final String BLANK = "blank";
    /** Below this many known players, strangers' skins fill the pool up to it. */
    private static final int POOL = 10;
    /** Accounts whose skins fill the pool: Mojang staff, and players and YouTubers mostly from before 2015. */
    private static final List<String> FAMOUS = List.of(
            "Notch", "jeb_", "Dinnerbone", "Grumm", "Searge", "EvilSeph", "C418", "carlmanneh",
            "kurtjmac", "CaptainSparklez", "SkyDoesMinecraft", "AntVenom", "SethBling", "Etho", "BdoubleO100",
            "Docm77", "Paulsoaresjr", "xisumavoid", "Vechs", "Guude", "VintageBeef", "Zisteau", "Generikb",
            "Nebris", "Pauseunpause", "Hypixel", "iJevin", "BajanCanadian", "JeromeASF", "Vikkstar123",
            "DanTDM", "stampylonghead", "deadmau5", "Mumbo", "Xephos", "Honeydew", "Sjin",
            "Nilesy", "Rythian", "Zoeya", "Sips_", "TheBajanCanadian");
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
    /** Skins fetched to fill the pool, not of anyone who has played here. */
    private final Set<UUID> strangers = ConcurrentHashMap.newKeySet();
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
        fillPool();
    }

    /** While fewer than POOL players are known, fetch strangers' skins (in random order) up to POOL in all. */
    private void fillPool() {
        int players = skins.size() - strangers.size();
        int wanted = POOL - skins.size();
        if (players >= POOL || wanted <= 0) return;
        List<String> names = new ArrayList<>(FAMOUS);
        java.util.Collections.shuffle(names);
        Set<String> known = new java.util.HashSet<>();
        for (Skin k : skins.values()) known.add(k.name().toLowerCase(java.util.Locale.ROOT));
        names.removeIf(n -> known.contains(n.toLowerCase(java.util.Locale.ROOT)));
        fetchStrangers(names, wanted);
    }

    /**
     * One name at a time, so a name that no longer resolves just moves on to the next. Mojang
     * limits lookups; when it says to slow down, the same name is tried again a minute later.
     */
    private void fetchStrangers(List<String> names, int wanted) {
        if (wanted <= 0 || names.isEmpty() || instance != this) return;
        String name = names.remove(0);
        lookupByName(null, name).whenComplete((ok, err) -> {
            if (ok == null && err == null) {
                names.add(0, name);
                if (instance == this && plugin.isEnabled()) {
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> fetchStrangers(names, wanted), 20L * 60);
                }
                return;
            }
            fetchStrangers(names, Boolean.TRUE.equals(ok) ? wanted - 1 : wanted);
        });
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
     * half the time an online player's skin (when any is known), else any
     * known skin, strangers' included; blank (null) if none is known. Stable
     * for the life of the canvas.
     */
    public @Nullable Skin skinForCanvas(String canvasKey) {
        String pinned = assignments.computeIfAbsent(canvasKey, key -> {
            dirty = true;
            int h = key.hashCode() * 0x9E3779B9;
            List<UUID> pool = new ArrayList<>();
            if ((h & 0x100) == 0) for (UUID id : online) if (skins.containsKey(id)) pool.add(id);
            if (pool.isEmpty()) pool.addAll(skins.keySet());
            if (pool.isEmpty()) return BLANK;
            pool.sort(null);
            return pool.get(Math.floorMod(h >>> 9, pool.size())).toString();
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
        UUID id = player.getUniqueId();
        String name = player.getName();
        if (url != null) {
            download(id, name, url.toString(), textures.getSkinModel() == PlayerTextures.SkinModel.SLIM, false);
        } else {
            // Offline-mode servers hand out profiles without textures: ask Mojang
            // for the skin of the account with this name instead.
            lookupByName(id, name);
        }
    }

    /**
     * Looks a name up with Mojang and downloads that account's skin. {@code id}: whose skin it is
     * here (a player on an offline-mode server); null for a stranger, kept under the account's own
     * id. Completes true once the skin is in, false if there is none, null if Mojang asked us to
     * slow down.
     */
    private java.util.concurrent.CompletableFuture<Boolean> lookupByName(@Nullable UUID id, String name) {
        return get("https://api.mojang.com/users/profiles/minecraft/" + name, HttpResponse.BodyHandlers.ofString())
                .thenCompose(r -> {
                    if (r.statusCode() == 429) throw new RateLimited();
                    if (r.statusCode() != 200) return java.util.concurrent.CompletableFuture.completedFuture(null);
                    String mojangId = com.google.gson.JsonParser.parseString(r.body()).getAsJsonObject().get("id").getAsString();
                    return get("https://sessionserver.mojang.com/session/minecraft/profile/" + mojangId,
                            HttpResponse.BodyHandlers.ofString());
                })
                .thenCompose(r -> {
                    if (r != null && r.statusCode() == 429) throw new RateLimited();
                    if (r == null || r.statusCode() != 200) return java.util.concurrent.CompletableFuture.completedFuture(false);
                    var profile = com.google.gson.JsonParser.parseString(r.body()).getAsJsonObject();
                    UUID owner = id != null ? id : fromMojang(profile.get("id").getAsString());
                    String shown = id != null ? name : profile.get("name").getAsString();
                    for (var p : profile.getAsJsonArray("properties")) {
                        var prop = p.getAsJsonObject();
                        if (!"textures".equals(prop.get("name").getAsString())) continue;
                        String json = new String(java.util.Base64.getDecoder().decode(prop.get("value").getAsString()),
                                java.nio.charset.StandardCharsets.UTF_8);
                        var skin = com.google.gson.JsonParser.parseString(json).getAsJsonObject()
                                .getAsJsonObject("textures").getAsJsonObject("SKIN");
                        if (skin == null) return java.util.concurrent.CompletableFuture.completedFuture(false);
                        var meta = skin.getAsJsonObject("metadata");
                        boolean slim = meta != null && meta.has("model") && "slim".equals(meta.get("model").getAsString());
                        return download(owner, shown, skin.get("url").getAsString(), slim, id == null);
                    }
                    return java.util.concurrent.CompletableFuture.completedFuture(false);
                })
                .exceptionally(err -> {
                    if (err instanceof RateLimited || err.getCause() instanceof RateLimited) return null;
                    plugin.getLogger().fine("Skin lookup failed for " + name + ": " + err.getMessage());
                    return false;
                });
    }

    /** Mojang's "429 Too Many Requests". */
    private static final class RateLimited extends RuntimeException {
        RateLimited() {
            super("rate limited", null, false, false);
        }
    }

    private static UUID fromMojang(String hex) {
        return UUID.fromString(hex.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
    }

    private <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> get(String url, HttpResponse.BodyHandler<T> handler) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
        return http.sendAsync(request, handler);
    }

    private java.util.concurrent.CompletableFuture<Boolean> download(UUID id, String name, String url, boolean slim, boolean stranger) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20)).GET().build();
        } catch (IllegalArgumentException e) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() != 200) return false;
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                if (image == null) return false;
                Skin skin = Skin.fromImage(name, image, slim);
                if (skin == null) return false;
                skins.put(id, skin);
                if (stranger) strangers.add(id);
                else strangers.remove(id);
                writeSkin(id, skin, image, stranger);
                return true;
            } catch (IOException e) {
                plugin.getLogger().warning("Couldn't read skin for " + name + ": " + e.getMessage());
                return false;
            }
        }).exceptionally(err -> {
            plugin.getLogger().fine("Skin download failed for " + name + ": " + err.getMessage());
            return false;
        });
    }

    // ── Persistence ──────────────────────────────────────────────────────

    private void writeSkin(UUID id, Skin skin, BufferedImage image, boolean stranger) throws IOException {
        if (!skinDir.isDirectory() && !skinDir.mkdirs()) return;
        ImageIO.write(image, "png", new File(skinDir, id + ".png"));
        YamlConfiguration meta = new YamlConfiguration();
        meta.set("name", skin.name());
        meta.set("slim", skin.slim());
        if (stranger) meta.set("stranger", true);
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
                    if (skin != null) {
                        skins.put(id, skin);
                        if (meta.getBoolean("stranger")) strangers.add(id);
                    }
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
