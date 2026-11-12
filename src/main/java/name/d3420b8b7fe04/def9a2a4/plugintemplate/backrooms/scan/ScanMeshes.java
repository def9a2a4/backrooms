package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.scan;

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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * The meshes of the scan levels (1881, 1967, 1973, 9500): too big to ship in the jar, so they're
 * downloaded once from {@code scan_levels.base_url} (def9a2a4.github.io) into
 * {@code plugins/Backrooms/meshes/}, checked against the SHA-256 in the bundled manifest
 * (scans/meshes.json, written by scripts/scans/publish.py), and loaded off the main thread.
 *
 * <p>With {@code scan_levels.download: false} nothing is fetched, and scan levels whose mesh isn't
 * already on disk are disabled. Loaded meshes are kept by name for {@code ScanChunkGenerator}.
 */
public final class ScanMeshes {

    /** One manifest entry: the published file name (content-addressed) and its checksum. */
    public record Entry(String file, String sha256, long bytes) {}

    private static final Map<String, VoxelMesh> LOADED = new ConcurrentHashMap<>();

    private final Logger log;
    private final Path dir;
    private final boolean download;
    private final String baseUrl;
    private final Map<String, Entry> manifest = new LinkedHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Backrooms-scan-meshes");
        t.setDaemon(true);
        return t;
    });

    public ScanMeshes(JavaPlugin plugin, @Nullable ConfigurationSection config) {
        this.log = plugin.getLogger();
        this.dir = plugin.getDataFolder().toPath().resolve("meshes");
        this.download = config == null || config.getBoolean("download", true);
        String base = config == null ? null : config.getString("base_url");
        if (base == null || base.isBlank()) base = "https://def9a2a4.github.io/backrooms/meshes/";
        this.baseUrl = base.endsWith("/") ? base : base + "/";
        try (InputStream in = plugin.getResource("scans/meshes.json")) {
            if (in == null) throw new IOException("missing scans/meshes.json");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.getAsJsonObject("meshes").entrySet()) {
                JsonObject m = e.getValue().getAsJsonObject();
                manifest.put(e.getKey(), new Entry(m.get("file").getAsString(), m.get("sha256").getAsString(),
                        m.get("bytes").getAsLong()));
            }
        } catch (IOException | RuntimeException e) {
            log.severe("Scan levels: could not read the mesh manifest: " + e.getMessage());
        }
    }

    /** The mesh a level's generator gets; null until {@link #load} finished. */
    @Nullable
    public static VoxelMesh get(String name) {
        return LOADED.get(name);
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
                "Scan levels: downloading %d mesh%s (%.1f MB) from %s into %s. The worlds built from them are "
                        + "16x-scale scans and can grow to several hundred MB each as players explore. "
                        + "To skip the downloads and disable these levels, set scan_levels.download: false in config.yml.%s",
                count, count == 1 ? "" : "es", missing / 1048576.0, baseUrl, dir,
                free >= 0 ? String.format(" (%.1f GB free)", free / 1073741824.0) : ""));
        if (free >= 0 && free < 2L * 1024 * 1024 * 1024) {
            log.warning("Scan levels: less than 2 GB of disk space left; the scan worlds may fill it.");
        }
    }

    /** Fetch if needed, verify, and decode the mesh off the main thread. */
    public CompletableFuture<VoxelMesh> load(String name, VoxelMesh.Options options) {
        VoxelMesh done = LOADED.get(name);
        if (done != null) return CompletableFuture.completedFuture(done);
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path file = ensure(name);
                long t0 = System.nanoTime();
                VoxelMesh mesh;
                try (InputStream in = Files.newInputStream(file)) {
                    mesh = VoxelMesh.load(in, options);
                }
                DebugLog.log(log, String.format("Scan mesh %s loaded in %.0f ms", name, (System.nanoTime() - t0) / 1e6));
                LOADED.put(name, mesh);
                return mesh;
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
        if (!download) throw new IOException(file + " is missing or damaged and scan_levels.download is false");
        Files.createDirectories(dir);
        Path tmp = dir.resolve(e.file() + ".part");
        URI uri = URI.create(baseUrl + e.file());
        log.info(String.format("Scan levels: downloading %s (%.1f MB)...", uri, e.bytes() / 1048576.0));
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
        log.info(String.format("Scan levels: %s downloaded in %.1f s", e.file(), (System.nanoTime() - t0) / 1e9));
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
