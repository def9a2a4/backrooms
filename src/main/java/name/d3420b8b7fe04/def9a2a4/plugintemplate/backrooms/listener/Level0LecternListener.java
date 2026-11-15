package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Puts a book on every lectern in Level 0's office buildings (conference
 * rooms, the control room's back row, auditorium stages) when their chunk is
 * first generated: mostly an agenda that has gone wrong, followed by speaker
 * notes in the Library's procedural styles; now and then a Library book
 * outright.
 */
public class Level0LecternListener implements Listener {

    private static final String WORLD = "bkrms_0";
    private static final int MIN_Y = 40, MAX_Y = 100;   // Level0Building's storeys

    private static final List<String> TITLES = List.of(
            "Agenda", "Agenda (Revised)", "Agenda (Revised) (2)", "Minutes", "Minutes of the Next Meeting",
            "Speaker Notes", "Quarterly Review", "Orientation, Day 1", "Orientation, Day 1 (again)",
            "Fire Drill Procedure", "Projections", "All-Hands", "Welcome Back", "Do Not Read Aloud",
            "Presentation FINAL final", "Notes for Whoever Is Next", "Item 7", "Q5 Results");
    private static final List<String> AUTHORS = List.of(
            "The Speaker", "Facilities", "Management", "Whoever Was Here", "HR", "You", "Room 3");
    private static final List<String> WRONG_ITEMS = List.of(
            "Item 4 has been removed", "Do not look at the screens", "Count the doors",
            "Introduce yourself (again)", "Lunch (there is no lunch)", "The lights",
            "A minute of silence for Room 3", "Who let you in", "Return to your desk",
            "Questions (there are no answers)", "Find the stairs that go up",
            "Agree with the speaker", "Read this page again", "Leave the way you came");

    private final JavaPlugin plugin;
    private final ProceduralBooks books;

    public Level0LecternListener(JavaPlugin plugin, ProceduralBooks books) {
        this.plugin = plugin;
        this.books = books;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        if (!event.getWorld().getName().equals(WORLD)) return;

        Chunk chunk = event.getChunk();
        ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);
        List<int[]> found = new ArrayList<>();
        for (int y = MIN_Y; y <= MAX_Y; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (snap.getBlockType(x, y, z) == Material.LECTERN) found.add(new int[]{ x, y, z });
                }
            }
        }
        if (found.isEmpty()) return;

        long seed = event.getWorld().getSeed();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!chunk.isLoaded()) return;
            for (int[] p : found) {
                Block block = chunk.getBlock(p[0], p[1], p[2]);
                if (block.getType() != Material.LECTERN) continue;
                if (!(block.getState() instanceof Lectern)) {
                    // Generated without its block entity: set it again to make one.
                    BlockData data = block.getBlockData();
                    block.setType(Material.AIR, false);
                    block.setBlockData(data, false);
                }
                if (!(block.getState() instanceof Lectern lectern)) continue;
                long h = seed ^ (block.getX() * 341873128712L + block.getY() * 2654435761L + block.getZ() * 132897987541L);
                lectern.getInventory().setItem(0, book(new Random(h)));
            }
        });
    }

    private ItemStack book(Random rng) {
        if (rng.nextInt(100) < 25) return books.create(rng);

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(TITLES.get(rng.nextInt(TITLES.size())));
        meta.setAuthor(rng.nextInt(3) == 0
                ? books.generateSyllabicWord(rng, 2) + " " + books.generateSyllabicWord(rng, 3)
                : AUTHORS.get(rng.nextInt(AUTHORS.size())));

        // An agenda: numbered items of word salad, one of them wrong, the numbers not quite right.
        StringBuilder agenda = new StringBuilder("AGENDA\n\n");
        int items = 4 + rng.nextInt(3);
        int wrong = rng.nextInt(items);
        int number = 1;
        for (int i = 0; i < items; i++) {
            String item = i == wrong
                    ? WRONG_ITEMS.get(rng.nextInt(WRONG_ITEMS.size()))
                    : books.generateWordSalad(rng, 2 + rng.nextInt(3));
            agenda.append(number).append(". ").append(item).append('\n');
            if (rng.nextInt(6) != 0) number++;   // now and then a number comes round twice
        }
        meta.addPage(agenda.toString().trim());

        // Speaker notes.
        int notes = 1 + rng.nextInt(2);
        for (int p = 0; p < notes; p++) {
            ProceduralBooks.GenerationMode mode = books.pickMode(rng);
            String text = books.generateContent(rng, mode, 10 + rng.nextInt(16));
            List<String> cursed = books.config().cursedSnippets();
            if (!cursed.isEmpty() && rng.nextInt(3) == 0) {
                text = text + " " + cursed.get(rng.nextInt(cursed.size()));
            }
            meta.addPage(text);
        }
        book.setItemMeta(meta);
        return book;
    }
}
