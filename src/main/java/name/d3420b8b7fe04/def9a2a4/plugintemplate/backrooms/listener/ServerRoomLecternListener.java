package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level3ChunkGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level3ChunkGenerator.CommandBlockKind;
import org.bukkit.Bukkit;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Populates lecterns sitting on top of command blocks in Level 3 (The Server Room)
 * with written books containing cryptic hints. The generator places SMOOTH_STONE_SLAB
 * as a placeholder; this listener converts it to LECTERN (type change forces tile entity
 * creation) and populates the book.
 */
public class ServerRoomLecternListener implements Listener {

    private static final int FLOOR_HEIGHT = 4;
    private static final Material PLACEHOLDER = Material.SMOOTH_STONE_SLAB;

    private final JavaPlugin plugin;

    public ServerRoomLecternListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private record LecternCandidate(int x, int z, int kindIndex, long posHash) {}

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        if (!event.getWorld().getName().startsWith("bkrms_3")) return;

        ChunkGenerator gen = event.getWorld().getGenerator();
        if (!(gen instanceof Level3ChunkGenerator l3gen)) return;

        List<CommandBlockKind> kinds = l3gen.getCommandBlockKinds();
        if (kinds.isEmpty()) return;

        Chunk chunk = event.getChunk();
        long worldSeed = event.getWorld().getSeed();

        // Collect candidates during event
        List<LecternCandidate> candidates = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Block placeholder = chunk.getBlock(x, FLOOR_HEIGHT + 2, z);
                if (placeholder.getType() != PLACEHOLDER) continue;

                Block cmdBlock = chunk.getBlock(x, FLOOR_HEIGHT + 1, z);
                if (cmdBlock.getType() != Material.COMMAND_BLOCK) continue;

                int worldX = chunk.getX() * 16 + x;
                int worldZ = chunk.getZ() * 16 + z;
                long posHash = worldSeed ^ ((long) worldX * 198491317L + (long) worldZ * 6542989L + 12345L);
                long kindHash = posHash ^ 0xC0D_B10CL;
                int kindIndex = l3gen.pickKind(kindHash >> 16);

                candidates.add(new LecternCandidate(x, z, kindIndex, posHash));
            }
        }
        if (candidates.isEmpty()) return;

        // Deferred: convert placeholder → LECTERN (type change forces tile entity)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!chunk.isLoaded()) return;
            for (LecternCandidate c : candidates) {
                Block block = chunk.getBlock(c.x, FLOOR_HEIGHT + 2, c.z);
                if (block.getType() != PLACEHOLDER) continue;

                block.setBlockData(Material.LECTERN.createBlockData(), false);

                CommandBlockKind kind = kinds.get(c.kindIndex);
                if (kind.messages().isEmpty()) continue;

                if (block.getState() instanceof Lectern lectern) {
                    Random rng = new Random(c.posHash);
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
                }
            }
        });
    }
}
