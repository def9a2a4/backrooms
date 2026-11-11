package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Writes text onto the blank signs Level101ChunkGenerator places in the Alpha
 * level (ChunkData can't write tile-entity text, so the generator leaves signs
 * blank and this listener fills them when the chunk first loads — the same
 * two-phase pattern as ServerRoomLecternListener).
 *
 * Each sign is classified by its surroundings:
 *   - sign on top of a chest        -> chest label ("ores", "stuff", "tomorrow")
 *   - wall sign (house exteriors)   -> nameplate ("Steve's house")
 *   - standing sign beside a dirt mound -> gravestone (name, b. / d. years)
 *   - anything else (signpost clusters, the open grave) stays blank
 *
 * Text is derived deterministically from the world seed and sign position, so
 * a regenerated world gets the same names. Signs are waxed — the old world's
 * words are not editable.
 */
public class Level101SignListener implements Listener {

    private static final String WORLD_NAME = "bkrms_101";
    private static final int SCAN_MIN_Y = 58;   // dungeon-breach chest labels sit at y60
    private static final int SCAN_MAX_Y = 78;

    private static final String[] NAMES = {
            "Steve", "Alex", "Dave", "Sarah", "Mark", "Emily", "Kevin", "Anna",
            "Josh", "Rachel", "Ben", "Laura", "Mike", "Tom", "Greg", "Pete",
            "builder2010", "xX_miner_Xx"
    };

    private static final String[] HOUSE_WEIRD = {
            "home", "my house", "your house", "nobody's house"
    };

    private static final String[] CHEST_MUNDANE = {
            "ores", "stuff", "tools", "wood", "food", "junk",
            "cobble", "important", "do not open", "empty"
    };

    private static final String[] CHEST_WEIRD = {
            "tomorrow", "yesterday", "what's left", "the rest", "winter", "mine"
    };

    private final JavaPlugin plugin;

    public Level101SignListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        if (!event.getWorld().getName().equals(WORLD_NAME)) return;

        Chunk chunk = event.getChunk();
        List<int[]> signs = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = SCAN_MIN_Y; y <= SCAN_MAX_Y; y++) {
                    Material type = chunk.getBlock(x, y, z).getType();
                    if (type == Material.OAK_SIGN || type == Material.OAK_WALL_SIGN) {
                        signs.add(new int[]{ x, y, z });
                    }
                }
            }
        }
        if (signs.isEmpty()) return;

        long worldSeed = event.getWorld().getSeed();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!chunk.isLoaded()) return;
            for (int[] pos : signs) {
                writeSign(chunk, pos[0], pos[1], pos[2], worldSeed);
            }
        });
    }

    private void writeSign(Chunk chunk, int x, int y, int z, long worldSeed) {
        Block block = chunk.getBlock(x, y, z);
        Material type = block.getType();
        if (type != Material.OAK_SIGN && type != Material.OAK_WALL_SIGN) return;
        if (!(block.getState() instanceof Sign sign)) return;

        int worldX = chunk.getX() * 16 + x;
        int worldZ = chunk.getZ() * 16 + z;
        Random rng = new Random(worldSeed
                ^ ((long) worldX * 198491317L + (long) y * 6542989L + (long) worldZ * 357916421L));

        String[] lines;
        if (block.getRelative(BlockFace.DOWN).getType() == Material.CHEST) {
            lines = chestLabel(rng);
        } else if (type == Material.OAK_WALL_SIGN) {
            lines = houseNameplate(rng);
        } else if (besideDirtMound(block)) {
            lines = gravestone(rng);
        } else {
            return; // signpost clusters say nothing
        }

        for (int i = 0; i < 4; i++) {
            sign.getSide(Side.FRONT).line(i, Component.text(i < lines.length ? lines[i] : ""));
        }
        sign.setWaxed(true);
        sign.update();
    }

    /** Headstones stand with the grave's dirt mound directly beside them. */
    private static boolean besideDirtMound(Block block) {
        for (BlockFace face : new BlockFace[]{ BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST }) {
            if (block.getRelative(face).getType() == Material.DIRT) return true;
        }
        return false;
    }

    private static String[] gravestone(Random rng) {
        String name = NAMES[rng.nextInt(NAMES.length)];
        int a = rng.nextInt(10000);
        int b = rng.nextInt(10000);
        // Usually the dates are in order. Sometimes they are not, and nothing explains it.
        if (rng.nextInt(100) < 85 && a > b) {
            int t = a; a = b; b = t;
        }
        return new String[]{ "", name, "b. " + a, "d. " + b };
    }

    private static String[] houseNameplate(Random rng) {
        int roll = rng.nextInt(100);
        if (roll < 70) {
            return new String[]{ "", NAMES[rng.nextInt(NAMES.length)] + "'s", "house" };
        } else if (roll < 85) {
            return new String[]{ "", NAMES[rng.nextInt(NAMES.length)] + "'s", "place" };
        }
        return new String[]{ "", HOUSE_WEIRD[rng.nextInt(HOUSE_WEIRD.length)] };
    }

    private static String[] chestLabel(Random rng) {
        String text = rng.nextInt(100) < 80
                ? CHEST_MUNDANE[rng.nextInt(CHEST_MUNDANE.length)]
                : CHEST_WEIRD[rng.nextInt(CHEST_WEIRD.length)];
        return new String[]{ "", text };
    }
}
