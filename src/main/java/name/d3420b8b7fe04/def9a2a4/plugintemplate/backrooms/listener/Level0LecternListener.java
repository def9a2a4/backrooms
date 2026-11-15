package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.book.BookGenerator;
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
 * Puts a book on every lectern in Level 0 (conference rooms, control rooms'
 * back rows and auditorium stages, in the office buildings and in the
 * lobby's office districts) when their chunk is
 * first generated: mostly office papers from the book grammar ({@link
 * BookGenerator}'s "office" voice: agendas, minutes, speaker notes, memos,
 * reviews), now and then a Library book outright.
 */
public class Level0LecternListener implements Listener {

    private static final String WORLD = "bkrms_0";
    private static final int MIN_Y = 40, MAX_Y = 100;   // Level0Building's storeys

    private final JavaPlugin plugin;
    private final ProceduralBooks books;
    private final BookGenerator generator;

    public Level0LecternListener(JavaPlugin plugin, ProceduralBooks books, BookGenerator generator) {
        this.plugin = plugin;
        this.books = books;
        this.generator = generator;
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
        if (generator == null || rng.nextInt(100) < 20) return books.create(rng);
        BookGenerator.Book b = generator.generate("office", rng);
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(b.title());
        meta.setAuthor(b.author());
        for (String page : b.pages()) meta.addPage(page);
        book.setItemMeta(meta);
        return book;
    }
}
