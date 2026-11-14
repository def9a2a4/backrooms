package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.mesh.MeshStore;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Everything in a building pack, downloaded by MeshStore: the buildings, the word lists, the wall
 * emblems and the tower flags. Level 142's is the Mace pack (scripts/mace/extract_schem.py --pack,
 * "mace"), Level 143's its own (scripts/level143/build_pack.py, "level143"); both have the same
 * layout. Each is loaded once, then shared by every city; immutable.
 */
final class MaceLibrary {

    static final String PACK = "mace";

    /** Mace's frequency classes, most common first (SourceWorld.SelectRandomBuilding). */
    static final String[] FREQUENCIES = {"very common", "common", "average", "rare", "very rare"};

    final List<MaceBuilding> buildings = new ArrayList<>();
    final MaceWords words;
    /** Emblem grids, rows top to bottom, cells as block states or "-1" for the precious block. */
    final Map<String, List<String[]>> emblems = new HashMap<>();
    /** Flag patterns, rows top to bottom, each cell a wool colour slot digit. */
    final List<List<String>> flags = new ArrayList<>();

    private static final Map<String, MaceLibrary> LOADED = new java.util.concurrent.ConcurrentHashMap<>();

    /** The Mace pack. */
    static MaceLibrary get() {
        return get(PACK);
    }

    /** A pack's library, read from the downloaded pack on first use. */
    static MaceLibrary get(String pack) {
        MaceLibrary lib = LOADED.get(pack);
        if (lib != null) return lib;
        synchronized (LOADED) {
            return LOADED.computeIfAbsent(pack, p -> {
                Path file = MeshStore.file(p);
                if (file == null) throw new IllegalStateException("the " + p + " pack isn't downloaded");
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    return new MaceLibrary(zip);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private MaceLibrary(ZipFile zip) throws IOException {
        words = MaceWords.load(zip);
        List<String> names = new ArrayList<>();
        for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements(); ) names.add(en.nextElement().getName());
        Collections.sort(names);   // a fixed order, so a seed always gives the same city
        for (String n : names) {
            if (n.startsWith("schem/") && n.endsWith(".schem")) {
                try (InputStream in = zip.getInputStream(zip.getEntry(n))) {
                    buildings.add(MaceBuilding.load(in));
                }
            } else if (n.startsWith("emblems/") && n.endsWith(".txt")) {
                List<String[]> rows = new ArrayList<>();
                for (String line : text(zip, n).split("\n")) if (!line.isEmpty()) rows.add(line.split("\t"));
                emblems.put(n.substring(8, n.length() - 4), rows);
            } else if (n.startsWith("flags/") && n.endsWith(".txt")) {
                List<String> rows = new ArrayList<>();
                for (String line : text(zip, n).split("\n")) if (!line.isBlank()) rows.add(line.trim());
                flags.add(rows);
            }
        }
    }

    private static String text(ZipFile zip, String name) throws IOException {
        try (InputStream in = zip.getInputStream(zip.getEntry(name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Every building of a type, whatever its theme. */
    List<MaceBuilding> ofType(String type) {
        List<MaceBuilding> out = new ArrayList<>();
        for (MaceBuilding b : buildings) if (b.type.equals(type)) out.add(b);
        return out;
    }

    /** The buildings of a type ("city", "farm", "city_entrance", ...) listed in a theme's file. */
    List<MaceBuilding> of(String themeFile, String type) {
        List<MaceBuilding> out = new ArrayList<>();
        for (MaceBuilding b : buildings) {
            if (!b.type.equals(type)) continue;
            String files = b.meta("files");
            if (files != null && List.of(files.split(",")).contains(themeFile)) out.add(b);
        }
        return out;
    }

    /**
     * Mace's SelectRandomBuilding: a frequency class by weight, then any building of that class
     * ({@code frequencyKey} is "frequency", or "frequencyN" for mineshaft level N). Null if no
     * building of the pool is in any weighted class.
     */
    @Nullable
    static MaceBuilding select(List<MaceBuilding> pool, int[] weights, String frequencyKey, Random rng) {
        List<List<MaceBuilding>> byClass = new ArrayList<>();
        for (String f : FREQUENCIES) {
            List<MaceBuilding> l = new ArrayList<>();
            for (MaceBuilding b : pool) if (f.equalsIgnoreCase(String.valueOf(b.meta(frequencyKey)).trim())) l.add(b);
            byClass.add(l);
        }
        int total = 0;
        for (int i = 0; i < weights.length; i++) if (!byClass.get(i).isEmpty()) total += weights[i];
        if (total <= 0) return null;
        int roll = rng.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            if (byClass.get(i).isEmpty()) continue;
            roll -= weights[i];
            if (roll < 0) return byClass.get(i).get(rng.nextInt(byClass.get(i).size()));
        }
        return null;
    }

    static boolean unique(MaceBuilding b) {
        String u = b.meta("unique");
        return u != null && (u.equalsIgnoreCase("yes") || u.equalsIgnoreCase("true"));
    }

    static String frequency(MaceBuilding b) {
        String f = b.meta("frequency");
        return f == null ? "" : f.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
