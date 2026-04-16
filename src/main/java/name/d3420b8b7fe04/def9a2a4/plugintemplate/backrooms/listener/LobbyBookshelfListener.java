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

import java.util.List;
import java.util.Random;

/**
 * Populates chiseled bookshelves in Level 0 (The Lobby) with written books
 * when chunks are first generated. Players collect 16 books to trigger the exit.
 */
public class LobbyBookshelfListener implements Listener {

    private static final int AIR_MIN_Y = 20;

    private final JavaPlugin plugin;
    private final LobbyBookConfig config;

    public LobbyBookshelfListener(JavaPlugin plugin, LobbyBookConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public record LobbyBookConfig(
            List<String> titles,
            List<String> authors,
            List<String> pages,
            double multiPageChance,
            List<MultiPageBook> multiPageBooks
    ) {
        public record MultiPageBook(String title, String author, List<String> pages) {}

        public static final LobbyBookConfig DEFAULT = new LobbyBookConfig(
                List.of("Note #7", "I found this under the carpet", "Don't read this",
                        "Memo: RE: Lights", "Employee Handbook (Rev. 14)",
                        "Notice of Termination", "Why are the walls yellow?",
                        "Meeting Notes (Undated)", "Incident Report #???",
                        "To Whoever Finds This", "My Last Entry",
                        "The Hum Won't Stop", "Floor Plan (Outdated)",
                        "List of Names", "Instructions (Illegible)",
                        "Someone Was Here Before"),
                List.of("Unknown"),
                List.of("The fluorescent lights buzz overhead. I can't remember how long I've been walking.",
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
                        "This book was already open when I found it. It was open to this page."),
                0.0,
                List.of()
        );
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

                // Force fresh tile entity: destroy and recreate the block
                // (re-applying same data doesn't create a new tile entity,
                //  but cycling through AIR does — same reason Library listener works)
                org.bukkit.block.data.BlockData savedData = block.getBlockData();
                block.setType(Material.AIR, false);
                block.setBlockData(savedData, false);

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
        // Chance to use a curated multi-page book template
        if (!config.multiPageBooks().isEmpty()
                && rng.nextDouble() < config.multiPageChance()) {
            LobbyBookConfig.MultiPageBook template =
                    config.multiPageBooks().get(rng.nextInt(config.multiPageBooks().size()));
            return createMultiPageBook(template);
        }

        // Default: random title + author + 1-3 random pages
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle(config.titles().get(rng.nextInt(config.titles().size())));
            meta.setAuthor(config.authors().get(rng.nextInt(config.authors().size())));
            int pageCount = 1 + rng.nextInt(3);
            for (int i = 0; i < pageCount; i++) {
                meta.addPage(config.pages().get(rng.nextInt(config.pages().size())));
            }
            book.setItemMeta(meta);
        }
        return book;
    }

    private ItemStack createMultiPageBook(LobbyBookConfig.MultiPageBook template) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle(template.title());
            meta.setAuthor(template.author());
            for (String page : template.pages()) {
                meta.addPage(page);
            }
            book.setItemMeta(meta);
        }
        return book;
    }
}
