package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.book.BookGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level64637ChunkGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level64637ChunkGenerator.BookConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.data.Directional;
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
 * Populates chiseled bookshelves with books in the Library level (64637).
 * Only center-pillar bookshelves (positions 6-9) become chiseled — these are
 * far enough from chunk boundaries to avoid the updateNeighbourForOutputSignal
 * deadlock that occurs when setItem() triggers a 2-deep neighbor check into
 * an unloaded chunk.
 */
public class LibraryBookshelfListener implements Listener {

    private static final int CELL_SIZE = 16;
    private static final double CHISELED_CHANCE = 0.12;

    private final JavaPlugin plugin;
    /** Of the ordinary (not pre-written, not entry) books, how many come from the grammar. */
    private static final double GRAMMAR_CHANCE = 0.5;

    private final BookConfig config;
    private final ProceduralBooks books;
    private final BookGenerator generator;

    public LibraryBookshelfListener(JavaPlugin plugin, BookConfig config, BookGenerator generator) {
        this.plugin = plugin;
        this.config = config;
        this.books = new ProceduralBooks(config);
        this.generator = generator;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        if (!event.getWorld().getName().equals("bkrms_64637")) return;

        Chunk chunk = event.getChunk();
        long worldSeed = event.getWorld().getSeed();
        int cellX = Math.floorDiv(chunk.getX() * 16, CELL_SIZE);
        int cellZ = Math.floorDiv(chunk.getZ() * 16, CELL_SIZE);

        Random rng = new Random(chunk.getChunkKey());

        List<ShelfCandidate> candidates = new ArrayList<>();

        for (int layer = 0; layer < Level64637ChunkGenerator.NUM_LAYERS; layer++) {
            // Only pillar rooms on this layer have chiseled bookshelves
            if (!Level64637ChunkGenerator.hasCenterPillar(worldSeed, layer, cellX, cellZ)) continue;

            int shelfMinY = Level64637ChunkGenerator.BASE_Y + layer * Level64637ChunkGenerator.FLOOR_SPACING + 4;
            int shelfMaxY = Level64637ChunkGenerator.BASE_Y + layer * Level64637ChunkGenerator.FLOOR_SPACING + 8;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = shelfMinY; y <= shelfMaxY; y++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.getType() != Material.BOOKSHELF) continue;
                        if (rng.nextDouble() >= CHISELED_CHANCE) continue;

                        BlockFace facing = computeFacing(block.getX(), block.getZ());
                        if (facing == null) continue;

                        candidates.add(new ShelfCandidate(block.getLocation(), facing, rng.nextLong()));
                    }
                }
            }
        }

        if (candidates.isEmpty()) return;

        // Defer block modifications to next tick to avoid issues during chunk loading
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ShelfCandidate candidate : candidates) {
                Block block = candidate.location.getBlock();
                if (block.getType() != Material.BOOKSHELF) continue;

                Directional data = (Directional) Material.CHISELED_BOOKSHELF.createBlockData();
                data.setFacing(candidate.facing);
                block.setBlockData(data, false);

                if (block.getState() instanceof ChiseledBookshelf shelf) {
                    Random bookRng = new Random(candidate.seed);
                    fillShelf(shelf, bookRng);
                }
            }
        });
    }

    /**
     * Computes the facing direction for a pillar-face bookshelf.
     * Returns null for non-pillar positions, corners, and interior blocks.
     * All returned positions are in [6,9], safely far from chunk boundaries.
     */
    private BlockFace computeFacing(int worldX, int worldZ) {
        int localX = Math.floorMod(worldX, CELL_SIZE);
        int localZ = Math.floorMod(worldZ, CELL_SIZE);

        boolean inPillarX = localX >= Level64637ChunkGenerator.PILLAR_MIN
                && localX <= Level64637ChunkGenerator.PILLAR_MAX;
        boolean inPillarZ = localZ >= Level64637ChunkGenerator.PILLAR_MIN
                && localZ <= Level64637ChunkGenerator.PILLAR_MAX;

        if (!inPillarX || !inPillarZ) return null;

        boolean onEdgeX = localX == Level64637ChunkGenerator.PILLAR_MIN
                || localX == Level64637ChunkGenerator.PILLAR_MAX;
        boolean onEdgeZ = localZ == Level64637ChunkGenerator.PILLAR_MIN
                || localZ == Level64637ChunkGenerator.PILLAR_MAX;

        if (onEdgeX && onEdgeZ) return null; // corner pilaster

        if (onEdgeX) {
            return localX == Level64637ChunkGenerator.PILLAR_MIN ? BlockFace.WEST : BlockFace.EAST;
        }
        if (onEdgeZ) {
            return localZ == Level64637ChunkGenerator.PILLAR_MIN ? BlockFace.NORTH : BlockFace.SOUTH;
        }

        return null; // interior
    }

    private record ShelfCandidate(Location location, BlockFace facing, long seed) {}

    private void fillShelf(ChiseledBookshelf shelf, Random rng) {
        int bookCount = 1 + rng.nextInt(6);
        int maxSlots = shelf.getInventory().getSize();

        for (int i = 0; i < Math.min(bookCount, maxSlots); i++) {
            int slot = rng.nextInt(maxSlots);
            if (shelf.getInventory().getItem(slot) != null) continue;
            shelf.getInventory().setItem(slot, createBook(rng));
        }
    }

    private ItemStack createBook(Random rng) {
        // Entry books (the way into other levels) — rolled before the pre-written pool
        // so adding them doesn't dilute level.dat and friends.
        if (!config.entryBooks().isEmpty()
                && rng.nextDouble() < config.entryBookChance()) {
            return buildFixedBook(config.entryBooks().get(rng.nextInt(config.entryBooks().size())));
        }

        // Rare pre-written books
        if (!config.preWrittenBooks().isEmpty()
                && rng.nextDouble() < config.preWrittenChance()) {
            return buildFixedBook(
                    config.preWrittenBooks().get(rng.nextInt(config.preWrittenBooks().size())));
        }
        if (generator != null && rng.nextDouble() < GRAMMAR_CHANCE) {
            BookGenerator.Book b = generator.generate("library", rng);
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();
            meta.setTitle(b.title());
            meta.setAuthor(b.author());
            for (String page : b.pages()) meta.addPage(page);
            book.setItemMeta(meta);
            return book;
        }
        return books.create(rng);
    }

    private static ItemStack buildFixedBook(BookConfig.PreWrittenBook pw) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(pw.title());
        meta.setAuthor(pw.author());
        if (pw.exitKey() == null || pw.pages().isEmpty()) {
            for (String page : pw.pages()) meta.addPage(page);
        } else {
            // Exit books: you must read to the end. The last page carries a
            // clickEvent — pressing the closing question is the transition
            // (page turns on hand-held books are client-side; see BookEndTrigger).
            List<Component> pages = new ArrayList<>();
            for (String page : pw.pages()) {
                pages.add(LegacyComponentSerializer.legacySection().deserialize(page));
            }
            int last = pages.size() - 1;
            pages.set(last, pages.get(last)
                    .clickEvent(ClickEvent.runCommand("/backrooms read " + pw.exitKey())));
            meta.pages(pages);
        }
        book.setItemMeta(meta);
        return book;
    }
}
