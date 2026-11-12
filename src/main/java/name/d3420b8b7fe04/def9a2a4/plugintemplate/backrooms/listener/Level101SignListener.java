package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Text pools and chances come from levels/level_101_signs.yml (loaded in
 * BackroomsPlugin); the small built-in pools below are only a fallback for a
 * missing config. Text is derived deterministically from the world seed and
 * sign position, so a regenerated world gets the same names. Signs are waxed —
 * the old world's words are not editable.
 */
public class Level101SignListener implements Listener {

    // Sign scan band per world, {min, max}. Level 101: dungeon-breach chest labels
    // sit at y60. Level 0: its sunken rooms hold Level 101 houses on floors as
    // low as y36.
    private static final Map<String, int[]> SCAN_BANDS = Map.of(
            "bkrms_101", new int[]{ 58, 78 },
            "bkrms_0", new int[]{ 28, 72 });

    private List<String> names = List.of("Steve", "Alex", "Sarah", "Mark");
    private List<String> graveWeirdNames = List.of("unknown", "???", "nobody");
    private List<String> epitaphs = List.of("gone mining", "be right back");
    private List<String> weirdDeathLines = List.of("d. ????", "d. soon");
    private List<String> houseKinds = List.of("house", "house", "house", "place");
    private List<String> houseWeird = List.of("home", "my house", "nobody's house");
    private List<String> chestMundane = List.of("ores", "stuff", "tools", "empty");
    private List<String> chestWeird = List.of("tomorrow", "what's left");

    private double graveWeirdNameChance = 0.12;
    private double ripChance = 0.4;
    private double epitaphChance = 0.15;
    private double orderedDatesChance = 0.8;
    private double weirdDeathChance = 0.08;
    private double noDeathChance = 0.06;
    private double houseWeirdChance = 0.2;
    private double herobrineChance = 0.04;
    private double chestWeirdChance = 0.25;

    private final JavaPlugin plugin;

    public Level101SignListener(JavaPlugin plugin, @Nullable ConfigurationSection config) {
        this.plugin = plugin;
        if (config == null) return;

        names = listOrDefault(config, "names", names);

        ConfigurationSection grave = config.getConfigurationSection("grave");
        if (grave != null) {
            graveWeirdNames = listOrDefault(grave, "weird_names", graveWeirdNames);
            epitaphs = listOrDefault(grave, "epitaphs", epitaphs);
            weirdDeathLines = listOrDefault(grave, "weird_death_lines", weirdDeathLines);
            graveWeirdNameChance = grave.getDouble("weird_name_chance", graveWeirdNameChance);
            ripChance = grave.getDouble("rip_chance", ripChance);
            epitaphChance = grave.getDouble("epitaph_chance", epitaphChance);
            orderedDatesChance = grave.getDouble("ordered_dates_chance", orderedDatesChance);
            weirdDeathChance = grave.getDouble("weird_death_chance", weirdDeathChance);
            noDeathChance = grave.getDouble("no_death_chance", noDeathChance);
        }

        ConfigurationSection house = config.getConfigurationSection("house");
        if (house != null) {
            houseWeird = listOrDefault(house, "weird", houseWeird);
            houseWeirdChance = house.getDouble("weird_chance", houseWeirdChance);
            herobrineChance = house.getDouble("herobrine_chance", herobrineChance);
            ConfigurationSection kinds = house.getConfigurationSection("kinds");
            if (kinds != null) {
                List<String> expanded = new ArrayList<>();
                for (String kind : kinds.getKeys(false)) {
                    int weight = kinds.getInt(kind, 1);
                    for (int i = 0; i < weight; i++) expanded.add(kind);
                }
                if (!expanded.isEmpty()) houseKinds = expanded;
            }
        }

        ConfigurationSection chest = config.getConfigurationSection("chest");
        if (chest != null) {
            chestMundane = listOrDefault(chest, "mundane", chestMundane);
            chestWeird = listOrDefault(chest, "weird", chestWeird);
            chestWeirdChance = chest.getDouble("weird_chance", chestWeirdChance);
        }
    }

    private static List<String> listOrDefault(ConfigurationSection config, String key, List<String> fallback) {
        List<String> list = config.getStringList(key);
        return list.isEmpty() ? fallback : list;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        int[] band = SCAN_BANDS.get(event.getWorld().getName());
        if (band == null) return;

        Chunk chunk = event.getChunk();
        List<int[]> signs = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = band[0]; y <= band[1]; y++) {
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

    private static String pick(List<String> pool, Random rng) {
        return pool.get(rng.nextInt(pool.size()));
    }

    private String[] gravestone(Random rng) {
        String name = rng.nextDouble() < graveWeirdNameChance
                ? pick(graveWeirdNames, rng)
                : pick(names, rng);
        String header = rng.nextDouble() < ripChance ? "RIP" : "";

        // Some graves carry an epitaph instead of dates
        if (rng.nextDouble() < epitaphChance) {
            return new String[]{ header, name, pick(epitaphs, rng), "" };
        }

        int a = rng.nextInt(10000);
        int b = rng.nextInt(10000);
        // Usually the dates are in order. Sometimes they are not, and nothing explains it.
        if (rng.nextDouble() < orderedDatesChance && a > b) {
            int t = a; a = b; b = t;
        }
        String deathLine = "d. " + b;
        double quirk = rng.nextDouble();
        if (quirk < weirdDeathChance) {
            deathLine = pick(weirdDeathLines, rng);
        } else if (quirk < weirdDeathChance + noDeathChance) {
            deathLine = ""; // no death date. nobody added one.
        }
        return new String[]{ header, name, "b. " + a, deathLine };
    }

    private String[] houseNameplate(Random rng) {
        if (rng.nextDouble() >= houseWeirdChance) {
            String name = rng.nextDouble() < herobrineChance
                    ? "Herobrine"
                    : pick(names, rng);
            return new String[]{ "", name + "'s", pick(houseKinds, rng) };
        }
        return new String[]{ "", pick(houseWeird, rng) };
    }

    private String[] chestLabel(Random rng) {
        String text = rng.nextDouble() < chestWeirdChance
                ? pick(chestWeird, rng)
                : pick(chestMundane, rng);
        return new String[]{ "", text };
    }
}
