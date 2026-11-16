package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.book;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.mesh.MeshStore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 * Chapters of public-domain books for the chiseled shelves in Level 1882's libraries. Built by
 * scripts/books/gutenberg.py and downloaded like the level meshes ({@link MeshStore}, "shelf_books"),
 * not shipped in the jar; until the file is here, shelves keep their plain books.
 *
 * <p>A shelf is generated holding plain books and tagged with its gallery's theme; the first time
 * anyone takes a book from it or breaks it, {@link #shelve} swaps the plain books for chapters on
 * that theme. Written books are kilobytes each, so filling every shelf up front would bloat each
 * library's chunks by megabytes.
 */
public final class ShelfBooks {

    /** On a chiseled shelf still holding stand-ins: the theme its books are on. */
    public static final NamespacedKey SHELVED = new NamespacedKey("backrooms", "shelved");
    /** The theme of a shelf that takes any book. */
    public static final String ANY = "ANY";
    /** The download's name in meshes.json. */
    public static final String FILE = "shelf_books";

    private record Work(String title, String author, List<String> themes, List<List<String>> excerpts) {}

    private static List<Work> works;

    private ShelfBooks() {}

    /** The books, once the download is here; empty before that. */
    private static synchronized List<Work> works() {
        if (works != null) return works;
        Path file = MeshStore.file(FILE);
        if (file == null) return List.of();
        List<Work> out = new ArrayList<>();
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("works")) {
                JsonObject w = e.getAsJsonObject();
                List<List<String>> excerpts = new ArrayList<>();
                for (JsonElement x : w.getAsJsonArray("excerpts")) excerpts.add(strings(x.getAsJsonArray()));
                out.add(new Work(w.get("title").getAsString(), w.get("author").getAsString(),
                        strings(w.getAsJsonArray("themes")), excerpts));
            }
        } catch (Exception e) {
            Logger.getLogger("Backrooms").warning("Couldn't read the library's books: " + e);
        }
        works = out;
        return out;
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>(a.size());
        for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }

    /** A chapter from a book on {@code theme}, or one time in four from anywhere in the library. */
    public static ItemStack book(String theme, Random r) {
        List<Work> all = works();
        if (all.isEmpty()) return new ItemStack(Material.BOOK);
        List<Work> on = all.stream().filter(w -> w.themes().contains(theme)).toList();
        List<Work> pool = on.isEmpty() || r.nextInt(4) == 0 ? all : on;
        Work w = pool.get(r.nextInt(pool.size()));
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        meta.setTitle(w.title());
        meta.setAuthor(w.author());
        meta.setPages(w.excerpts().get(r.nextInt(w.excerpts().size())));
        meta.setGeneration(BookMeta.Generation.values()[r.nextInt(3)]);
        item.setItemMeta(meta);
        return item;
    }

    /** If {@code b} is a shelf still holding stand-ins, puts the real books on it (the same ones every time). */
    public static void shelve(Block b) {
        if (b.getType() != Material.CHISELED_BOOKSHELF || !(b.getState() instanceof ChiseledBookshelf shelf)) return;
        String theme = shelf.getPersistentDataContainer().get(SHELVED, PersistentDataType.STRING);
        if (theme == null || works().isEmpty()) return;
        Random r = new Random(b.getWorld().getSeed() ^ b.getX() * 0x9E3779B97F4A7C15L ^ b.getY() * 0xC2B2AE3D27D4EB4FL
                ^ b.getZ() * 0x165667B19E3779F9L);
        Inventory inv = shelf.getSnapshotInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            ItemStack real = book(theme, r);
            if (it != null && it.getType() == Material.BOOK) inv.setItem(i, real);
        }
        shelf.getPersistentDataContainer().remove(SHELVED);
        shelf.update(true, false);
    }
}
