package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * Populates chiseled bookshelves in Level 0 (The Lobby) with written books
 * when chunks are first generated. Players collect 16 books to trigger the exit.
 */
public class LobbyBookshelfListener implements Listener {

    private static final int AIR_MIN_Y = 20;

    private static final String[] BOOK_TITLES = {
            "Note #7", "I found this under the carpet", "Don't read this",
            "Memo: RE: Lights", "Employee Handbook (Rev. 14)",
            "Notice of Termination", "Why are the walls yellow?",
            "Meeting Notes (Undated)", "Incident Report #???",
            "To Whoever Finds This", "My Last Entry",
            "The Hum Won't Stop", "Floor Plan (Outdated)",
            "List of Names", "Instructions (Illegible)",
            "Someone Was Here Before"
    };

    private static final String[] BOOK_PAGES = {
            "The fluorescent lights buzz overhead. I can't remember how long I've been walking.",
            "I thought I found a staircase today. It just led back to the same hallway.",
            "If you are reading this, do not trust the walls. They move when you aren't looking.",
            "Day ???: The carpet is damp. It shouldn't be damp. There's nothing above us.",
            "I counted 847 identical rooms before I stopped counting.",
            "The lights flicker every 23 seconds. I timed it. Then they changed to 17.",
            "There's a smell like old almonds and mildew. It's getting stronger.",
            "I found footprints in the dust. They were my own.",
            "Someone left a note: 'GO DOWN.' But there is no down.",
            "The hum is a C#. It never changes. I think it's coming from the floor.",
            "I woke up at my desk. I don't have a desk. I don't work here.",
            "This book was already open when I found it. It was open to this page.",
    };

    private final JavaPlugin plugin;

    public LobbyBookshelfListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        if (!event.getWorld().getName().startsWith("bkrms_0")) return;

        Chunk chunk = event.getChunk();
        // Run on next tick to avoid tile entity issues during generation
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> populateChunk(chunk), 1L);
    }

    private void populateChunk(Chunk chunk) {
        if (!chunk.isLoaded()) return;

        Random rng = new Random(chunk.getChunkKey() ^ 0xB00C5L);

        // Scan for chiseled bookshelves at desk Y level
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Block block = chunk.getBlock(x, AIR_MIN_Y, z);
                if (block.getType() != Material.CHISELED_BOOKSHELF) continue;

                // Force tile entity creation on freshly-generated blocks
                block.setBlockData(block.getBlockData(), false);

                if (block.getState() instanceof ChiseledBookshelf shelf) {
                    // Fill 1-3 random slots with written books
                    int bookCount = 1 + rng.nextInt(3);
                    for (int i = 0; i < bookCount; i++) {
                        int slot = rng.nextInt(6);
                        if (shelf.getInventory().getItem(slot) != null) continue;

                        ItemStack book = createLoreBook(rng);
                        shelf.getInventory().setItem(slot, book);
                    }
                    shelf.update();
                }
            }
        }
    }

    private ItemStack createLoreBook(Random rng) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle(BOOK_TITLES[rng.nextInt(BOOK_TITLES.length)]);
            meta.setAuthor("Unknown");
            meta.addPage(BOOK_PAGES[rng.nextInt(BOOK_PAGES.length)]);
            book.setItemMeta(meta);
        }
        return book;
    }
}
