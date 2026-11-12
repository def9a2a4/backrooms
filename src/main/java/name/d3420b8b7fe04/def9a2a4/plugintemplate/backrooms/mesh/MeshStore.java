package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.mesh;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.DebugLog;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.VoxelMesh;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Every level mesh (Stonehenge, the test scene's models, the scans) is kept out of the jar to keep
 * it light: they're downloaded once from {@code meshes.base_url} (this repo's meshes/ folder on
 * raw.githubusercontent.com) into {@code plugins/Backrooms/meshes/}, checked against the SHA-256
 * in the bundled manifest (meshes.json, written by scripts/scans/publish.py), and decoded off the
 * main thread. A level lists the meshes it needs ({@code meshes:} in its yml) and its world is
 * created once they're in.
 *
 * <p>With {@code meshes.download: false} nothing is fetched, and levels whose meshes aren't
 * already on disk are disabled. Files and decoded meshes are kept by name for the generators.
 */
public final class MeshStore {

    /** One manifest entry: the published file name (content-addressed) and its checksum. */
    public record Entry(String file, String sha256, long bytes) {}

    public static final String DEFAULT_BASE_URL = "https://raw.githubusercontent.com/def9a2a4/backrooms/main/meshes/";

    private static final Map<String, VoxelMesh> LOADED = new ConcurrentHashMap<>();
    private static final Map<String, Path> FILES = new ConcurrentHashMap<>();

    private final Logger log;
    private final Path dir;
    private final boolean download;
    private final String baseUrl;
    private final Map<String, Entry> manifest = new LinkedHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Backrooms-meshes");
        t.setDaemon(true);
        return t;
    });

    public MeshStore(JavaPlugin plugin, @Nullable ConfigurationSection config) {
        this.log = plugin.getLogger();
        this.dir = plugin.getDataFolder().toPath().resolve("meshes");
        this.download = config == null || config.getBoolean("download", true);
        String base = config == null ? null : config.getString("base_url");
        if (base == null || base.isBlank()) base = DEFAULT_BASE_URL;
        this.baseUrl = base.endsWith("/") ? base : base + "/";
        try (InputStream in = plugin.getResource("meshes.json")) {
            if (in == null) throw new IOException("missing meshes.json");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.getAsJsonObject("meshes").entrySet()) {
                JsonObject m = e.getValue().getAsJsonObject();
                manifest.put(e.getKey(), new Entry(m.get("file").getAsString(), m.get("sha256").getAsString(),
                        m.get("bytes").getAsLong()));
            }
        } catch (IOException | RuntimeException e) {
            log.severe("Meshes: could not read the mesh manifest: " + e.getMessage());
        }
    }

    /** A decoded mesh; null until {@link #fetch} decoded it. */
    @Nullable
    public static VoxelMesh get(String name) {
        return LOADED.get(name);
    }

    /** A verified mesh file on disk; null until {@link #fetch} got it. */
    @Nullable
    public static Path file(String name) {
        return FILES.get(name);
    }

    public boolean downloadsEnabled() {
        return download;
    }

    public boolean known(String name) {
        return manifest.containsKey(name);
    }

    /** Whether the mesh is on disk at the expected size (the checksum is verified when loading). */
    public boolean onDisk(String name) {
        Entry e = manifest.get(name);
        if (e == null) return false;
        Path p = dir.resolve(e.file());
        try {
            return Files.isRegularFile(p) && Files.size(p) == e.bytes();
        } catch (IOException ex) {
            return false;
        }
    }

    /** A level using this mesh can run: the file is here, or may be downloaded. */
    public boolean usable(String name) {
        return known(name) && (download || onDisk(name));
    }

    /**
     * Log what the scan levels will cost on disk before anything is fetched: the downloads
     * themselves, and the worlds, which are giant and grow as players explore them.
     */
    public void warnDiskSpace(Iterable<String> names) {
        long missing = 0;
        int count = 0;
        for (String n : names) {
            if (known(n) && !onDisk(n)) {
                missing += manifest.get(n).bytes();
                count++;
            }
        }
        if (count == 0 || !download) return;
        long free;
        try {
            Files.createDirectories(dir);
            free = Files.getFileStore(dir).getUsableSpace();
        } catch (IOException e) {
            free = -1;
        }
        log.warning(String.format(
                "Meshes: downloading %d level mesh%s (%.1f MB) from %s into %s. The scan levels built from them "
                        + "(1881, 1967, 1973, 9500) are 16x-scale and can grow to several hundred MB each as players explore. "
                        + "To skip the downloads and disable the levels that need them, set meshes.download: false in config.yml.%s",
                count, count == 1 ? "" : "es", missing / 1048576.0, baseUrl, dir,
                free >= 0 ? String.format(" (%.1f GB free)", free / 1073741824.0) : ""));
        if (free >= 0 && free < 2L * 1024 * 1024 * 1024) {
            log.warning("Meshes: less than 2 GB of disk space left; the scan worlds may fill it.");
        }
    }

    /**
     * Off the main thread: fetch (if needed) and verify every named mesh, then decode
     * {@code decode} (if not null) with {@code options}, for generators that voxelize it.
     */
    public CompletableFuture<Void> fetch(List<String> names, @Nullable String decode, VoxelMesh.Options options) {
        return CompletableFuture.runAsync(() -> {
            try {
                for (String name : names) {
                    if (!FILES.containsKey(name)) FILES.put(name, ensure(name));
                }
                if (decode != null && !LOADED.containsKey(decode)) {
                    long t0 = System.nanoTime();
                    try (InputStream in = Files.newInputStream(FILES.get(decode))) {
                        LOADED.put(decode, VoxelMesh.load(in, options));
                    }
                    DebugLog.log(log, String.format("Mesh %s decoded in %.0f ms", decode, (System.nanoTime() - t0) / 1e6));
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, pool);
    }

    private Path ensure(String name) throws IOException {
        Entry e = manifest.get(name);
        if (e == null) throw new IOException("no mesh '" + name + "' in the manifest");
        Path file = dir.resolve(e.file());
        if (Files.isRegularFile(file) && Files.size(file) == e.bytes() && sha256(file).equals(e.sha256())) {
            return file;
        }
        if (!download) throw new IOException(file + " is missing or damaged and meshes.download is false");
        Files.createDirectories(dir);
        Path tmp = dir.resolve(e.file() + ".part");
        URI uri = URI.create(baseUrl + e.file());
        log.info(String.format("Meshes: downloading %s (%.1f MB)...", uri, e.bytes() / 1048576.0));
        long t0 = System.nanoTime();
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(10)).GET().build();
        try {
            HttpResponse<Path> res = client.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + " for " + uri);
            String sum = sha256(tmp);
            if (!sum.equals(e.sha256())) throw new IOException("checksum mismatch for " + uri + " (got " + sum + ")");
        } catch (InterruptedException ex) {
            Files.deleteIfExists(tmp);
            Thread.currentThread().interrupt();
            throw new IOException("interrupted downloading " + uri);
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(tmp);   // never leave an error page or a partial file behind
            throw ex;
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log.info(String.format("Meshes: %s downloaded in %.1f s", e.file(), (System.nanoTime() - t0) / 1e9));
        return file;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
