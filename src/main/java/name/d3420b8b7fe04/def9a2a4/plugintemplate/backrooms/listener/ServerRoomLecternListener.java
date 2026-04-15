package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level3ChunkGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level3ChunkGenerator.CommandBlockKind;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;

/**
 * Populates lecterns sitting on top of command blocks in Level 3 (The Server Room)
 * with written books containing cryptic hints. The book content is determined by
 * which "kind" of command block terminal it is (based on position hash + configured weights).
 */
public class ServerRoomLecternListener implements Listener {

    private static final int FLOOR_HEIGHT = 4;

    private final JavaPlugin plugin;

    public ServerRoomLecternListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        if (!event.getWorld().getName().startsWith("bkrms_3")) return;

        ChunkGenerator gen = event.getWorld().getGenerator();
        if (!(gen instanceof Level3ChunkGenerator l3gen)) return;

        List<CommandBlockKind> kinds = l3gen.getCommandBlockKinds();
        if (kinds.isEmpty()) return;

        Chunk chunk = event.getChunk();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> populateChunk(chunk, l3gen, kinds), 1L);
    }

    private void populateChunk(Chunk chunk, Level3ChunkGenerator l3gen,
                               List<CommandBlockKind> kinds) {
        if (!chunk.isLoaded()) return;

        long worldSeed = chunk.getWorld().getSeed();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Check for lectern at FLOOR_HEIGHT+2 on top of command block at FLOOR_HEIGHT+1
                Block lecternBlock = chunk.getBlock(x, FLOOR_HEIGHT + 2, z);
                if (lecternBlock.getType() != Material.LECTERN) continue;

                Block cmdBlock = chunk.getBlock(x, FLOOR_HEIGHT + 1, z);
                if (cmdBlock.getType() != Material.COMMAND_BLOCK) continue;

                // Determine which kind using the same hash as the generator
                int worldX = chunk.getX() * 16 + x;
                int worldZ = chunk.getZ() * 16 + z;
                long posHash = worldSeed ^ ((long) worldX * 198491317L + (long) worldZ * 6542989L + 12345L);
                long kindHash = posHash ^ 0xC0D_B10CL;
                int kindIndex = l3gen.pickKind(kindHash >> 16);

                CommandBlockKind kind = kinds.get(kindIndex);
                if (kind.messages().isEmpty()) continue;

                // Force tile entity, then place the book
                lecternBlock.setBlockData(lecternBlock.getBlockData(), false);
                if (lecternBlock.getState() instanceof Lectern lectern) {
                    Random rng = new Random(posHash);
                    String message = kind.messages().get(rng.nextInt(kind.messages().size()));

                    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                    BookMeta meta = (BookMeta) book.getItemMeta();
                    if (meta != null) {
                        meta.setTitle("§k" + kind.target());
                        meta.setAuthor("SYSTEM");
                        meta.addPage(message);
                        book.setItemMeta(meta);
                    }
                    lectern.getInventory().setItem(0, book);
                    lectern.update();
                }
            }
        }
    }
}
