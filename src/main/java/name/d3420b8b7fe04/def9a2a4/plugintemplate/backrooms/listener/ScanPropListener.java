package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.ScanChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the scan levels' props whole, so the exits hanging off them work for every visitor:
 * <ul>
 *   <li>a display case, offering stone, lectern or single-block prop that's broken or built into
 *   is rebuilt {@link #DELAY_TICKS} after the first change (after any exit it triggered);</li>
 *   <li>lectern props ("placards") get their book from the level's placard file
 *   ({@code generator_config.placards}, in levels/) whenever their chunk loads or they're rebuilt,
 *   and the book can't be taken.</li>
 * </ul>
 */
public class ScanPropListener implements Listener {

    private static final long DELAY_TICKS = 100;

    private final JavaPlugin plugin;
    private final Set<Object> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, ConfigurationSection> placardFiles = new HashMap<>();

    public ScanPropListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        touched(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        touched(event.getBlockPlaced());
    }

    private void touched(Block block) {
        World world = block.getWorld();
        if (!(world.getGenerator() instanceof ScanChunkGenerator gen)) return;
        Object prop = gen.propAt(world, block.getX(), block.getY(), block.getZ());
        if (prop == null || !pending.add(prop)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(prop);
            gen.restore(world, prop);
            stockPlacards(world, gen, block.getX() >> 4, block.getZ() >> 4);
        }, DELAY_TICKS);
    }

    // ── placards ─────────────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (!(world.getGenerator() instanceof ScanChunkGenerator gen) || gen.placardFile() == null) return;
        Chunk chunk = event.getChunk();
        if (gen.placards(world, chunk.getX(), chunk.getZ()).isEmpty()) return;
        // block entities can't be touched during the load event itself
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) stockPlacards(world, gen, chunk.getX(), chunk.getZ());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onTakeBook(PlayerTakeLecternBookEvent event) {
        Block b = event.getLectern().getBlock();
        World world = b.getWorld();
        if (!(world.getGenerator() instanceof ScanChunkGenerator gen)) return;
        for (ScanChunkGenerator.Placard p : gen.placards(world, b.getX() >> 4, b.getZ() >> 4)) {
            if (p.x() == b.getX() && p.y() == b.getY() && p.z() == b.getZ()) {
                event.setCancelled(true);   // placards stay on their stands
                return;
            }
        }
    }

    private void stockPlacards(World world, ScanChunkGenerator gen, int chunkX, int chunkZ) {
        ConfigurationSection books = books(gen.placardFile());
        if (books == null) return;
        for (ScanChunkGenerator.Placard p : gen.placards(world, chunkX, chunkZ)) {
            Block block = world.getBlockAt(p.x(), p.y(), p.z());
            if (block.getType() != Material.LECTERN || !(block.getState() instanceof Lectern lectern)) continue;
            ItemStack current = lectern.getInventory().getItem(0);
            if (current != null && current.getType() == Material.WRITTEN_BOOK) continue;
            ConfigurationSection def = books.getConfigurationSection(p.book());
            if (def == null) {
                plugin.getLogger().warning("Placard book '" + p.book() + "' missing from " + gen.placardFile());
                continue;
            }
            lectern.getInventory().setItem(0, book(def));
        }
    }

    private static ItemStack book(ConfigurationSection def) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(def.getString("title", ""));
        meta.setAuthor(def.getString("author", ""));
        List<String> pages = def.getStringList("pages");
        meta.setPages(pages.isEmpty() ? List.of("") : pages);
        book.setItemMeta(meta);
        return book;
    }

    private ConfigurationSection books(String file) {
        return placardFiles.computeIfAbsent(file, f -> {
            File path = new File(plugin.getDataFolder(), "levels/" + f);
            if (!path.exists()) {
                plugin.getLogger().warning("Placard file levels/" + f + " not found");
                return null;
            }
            return YamlConfiguration.loadConfiguration(path).getConfigurationSection("books");
        });
    }
}
