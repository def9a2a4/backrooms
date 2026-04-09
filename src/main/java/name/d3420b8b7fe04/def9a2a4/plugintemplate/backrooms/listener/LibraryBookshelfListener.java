package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level64637ChunkGenerator.BookConfig;
import org.bukkit.Chunk;
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

import java.util.Random;

/**
 * Populates chiseled bookshelves with books in the Library level (64637).
 * Uses ChunkLoadEvent instead of BlockPopulator because LimitedRegion
 * cannot reliably modify tile entity inventories during chunk generation.
 */
public class LibraryBookshelfListener implements Listener {

    private static final int SHELF_MIN_Y = 11;
    private static final int SHELF_MAX_Y = 15;
    private static final double CHISELED_CHANCE = 0.025;

    private final BookConfig config;

    public LibraryBookshelfListener(BookConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        if (!event.getWorld().getName().equals("level_64637")) return;

        Chunk chunk = event.getChunk();
        Random rng = new Random(chunk.getChunkKey());

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = SHELF_MIN_Y; y <= SHELF_MAX_Y; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.BOOKSHELF) continue;
                    if (rng.nextDouble() >= CHISELED_CHANCE) continue;

                    // Determine which side faces the room (air)
                    BlockFace facing = findAirFace(block);
                    if (facing == null) continue;

                    // Place chiseled bookshelf with correct facing
                    Directional data = (Directional) Material.CHISELED_BOOKSHELF.createBlockData();
                    data.setFacing(facing);
                    block.setBlockData(data, false);

                    // Fill with books — block is live in the world, tile entity works
                    if (block.getState() instanceof ChiseledBookshelf shelf) {
                        fillShelf(shelf, rng);
                        shelf.update(true, false);
                    }
                }
            }
        }
    }

    private BlockFace findAirFace(Block block) {
        if (block.getRelative(BlockFace.WEST).getType() == Material.AIR) return BlockFace.WEST;
        if (block.getRelative(BlockFace.EAST).getType() == Material.AIR) return BlockFace.EAST;
        if (block.getRelative(BlockFace.NORTH).getType() == Material.AIR) return BlockFace.NORTH;
        if (block.getRelative(BlockFace.SOUTH).getType() == Material.AIR) return BlockFace.SOUTH;
        return null;
    }

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
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        boolean isCursed = rng.nextDouble() < config.cursedChance()
                && !config.cursedSnippets().isEmpty();

        // Title
        if (isCursed && rng.nextDouble() < 0.3 && !config.cursedTitles().isEmpty()) {
            meta.setTitle(config.cursedTitles().get(rng.nextInt(config.cursedTitles().size())));
        } else {
            meta.setTitle(generateGibberishTitle(rng));
        }

        // Author
        if (isCursed && rng.nextDouble() < 0.3 && !config.cursedAuthors().isEmpty()) {
            meta.setAuthor(config.cursedAuthors().get(rng.nextInt(config.cursedAuthors().size())));
        } else {
            meta.setAuthor(generateGibberish(rng, 4 + rng.nextInt(8)));
        }

        // Pages
        int pageCount = 1 + rng.nextInt(3);
        for (int p = 0; p < pageCount; p++) {
            if (isCursed && p == rng.nextInt(pageCount)) {
                String snippet = config.cursedSnippets().get(
                        rng.nextInt(config.cursedSnippets().size()));
                String before = generateGibberish(rng, 20 + rng.nextInt(40));
                String after = generateGibberish(rng, 20 + rng.nextInt(40));
                meta.addPage(before + " " + snippet + " " + after);
            } else {
                meta.addPage(generateGibberish(rng, 80 + rng.nextInt(120)));
            }
        }

        book.setItemMeta(meta);
        return book;
    }

    private String generateGibberishTitle(Random rng) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append((char) ('A' + rng.nextInt(26)));
        sb.append('.');
        for (int i = 0; i < 4; i++) sb.append((char) ('A' + rng.nextInt(26)));
        sb.append('.');
        for (int i = 0; i < 3; i++) sb.append((char) ('0' + rng.nextInt(10)));
        return sb.toString();
    }

    private String generateGibberish(Random rng, int length) {
        String chars = config.gibberishChars();
        if (chars.isEmpty()) chars = "abcdefghijklmnopqrstuvwxyz ";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
