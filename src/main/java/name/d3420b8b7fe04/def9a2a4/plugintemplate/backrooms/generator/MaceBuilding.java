package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A building from Mace's template world, as a Sponge v3 schematic made by
 * scripts/mace/extract_schem.py, and the placeholders Mace fills in when it places one.
 *
 * <p>The templates mark the parts that vary per city with stand-in blocks and signs:
 * <ul>
 *   <li>lapis ore: wool, one random colour per building;
 *   <li>redstone ore / gold ore on the ground layer (source y63): the city's ground / path block;
 *   <li>city entrances: iron ore is wall material; without walkway torches, glowstone is wall
 *       material too and torches are removed;
 *   <li>a chest whose first slot holds 3 paper: house loot; 4 paper: treasure;
 *   <li>signs: "cityname" gets the city's name; [word] tokens are looked up (Mace's word lists,
 *       e.g. [ChurchAdj], [Drink], [house], [nb] for notice boards), with "~" forcing a line break.
 *       A leading 0/1/2 asks for text unique per building (1) or per city (2). "entity",
 *       "spawner" and "commentsign" signs are Mace commands and author notes; they are removed.
 * </ul>
 * Spawners and entities (paintings) are not placed.
 *
 * <p>{@link #fill} resolves all of that from a {@link Fill}, an RNG and a rotation into a
 * {@link Filled} building, which is placed in two steps like the Level 101 signs: blocks into
 * ChunkData during generation ({@link Filled#place}), then sign text and chest contents once the
 * chunk exists ({@link Filled#applyBlockEntities}). Fill again with the same seed to get the same
 * building. Rotation is vanilla's (StructureTemplate): clockwise seen from above, about the
 * footprint, which stays at the origin.
 *
 * <p>Immutable after {@link #load}; safe to share between threads.
 */
final class MaceBuilding {

    /** City-wide choices. {@code words} maps a lowercase token ("churchadj", "house", "nb") to its text. */
    record Fill(BlockData ground, BlockData path, BlockData wall, boolean walkwayTorches,
                @Nullable String cityName, Function<String, @Nullable String> words, boolean loot,
                @Nullable Set<String> citySigns) {}

    private static final int GROUND_Y = 63;  // source y of the ground layer the ore markers sit on
    private static final int SIGN_WIDTH = 14;
    private static final Pattern TOKEN = Pattern.compile("\\[(.*?)]");

    private static final BlockData AIR = Material.AIR.createBlockData();
    private static final BlockData[] WOOL = java.util.stream.Stream.of(
        Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
        Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
        Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
        Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL
    ).map(Material::createBlockData).toArray(BlockData[]::new);
    private static final Material[] HOUSE_LOOT = {
        Material.APPLE, Material.OAK_BOAT, Material.BOOK, Material.BONE, Material.BOW, Material.BOWL,
        Material.BREAD, Material.BUCKET, Material.COOKIE, Material.EGG, Material.FEATHER,
        Material.FISHING_ROD, Material.FLINT_AND_STEEL, Material.GLASS_BOTTLE, Material.PAPER,
        Material.SHEARS, Material.STICK, Material.STRING, Material.TORCH, Material.WHEAT,
        Material.WOODEN_AXE, Material.WOODEN_HOE, Material.WOODEN_SHOVEL, Material.WOODEN_SWORD,
    };
    private static final Material[] TREASURE_LOOT = {
        Material.GOLDEN_APPLE, Material.GOLD_INGOT, Material.DIAMOND, Material.MUSIC_DISC_13,
        Material.MUSIC_DISC_CAT, Material.IRON_INGOT, Material.SADDLE, Material.COMPASS,
        Material.CLOCK, Material.CAKE, Material.COOKIE,
    };

    final String name, type;
    final int width, height, length;
    /** Source y of layer 0 in Mace's world; the ground layer is at {@code GROUND_Y - sourceY}. */
    final int sourceY;
    /** The rest of the building's XML entry (Metadata.Mace): frequency, files, pattern, ... */
    private final Map<String, String> mace = new HashMap<>();
    private final BlockData[] palette;
    private final int[] blocks;                         // palette index, x + z*width + y*width*length
    private final Map<Integer, String[]> signs = new HashMap<>();
    private final Map<Integer, List<Item>> containers = new HashMap<>();

    private record Item(int slot, Material material, int count) {}

    private MaceBuilding(Map<String, Object> root) {
        Map<String, Object> s = root.containsKey("Schematic") ? Nbt.compound(root, "Schematic") : root;
        Map<String, Object> meta = Nbt.compound(s, "Metadata");
        Map<String, Object> mace = Nbt.compound(meta, "Mace");
        name = Nbt.string(meta, "Name", "?");
        type = Nbt.string(mace, "type", "city");
        sourceY = Nbt.integer(mace, "source_y", GROUND_Y);
        width = Nbt.integer(s, "Width", 0) & 0xFFFF;
        height = Nbt.integer(s, "Height", 0) & 0xFFFF;
        length = Nbt.integer(s, "Length", 0) & 0xFFFF;
        for (Map.Entry<String, Object> e : mace.entrySet()) this.mace.put(e.getKey(), String.valueOf(e.getValue()));

        Map<String, Object> b = Nbt.compound(s, "Blocks");
        Map<String, Object> pal = Nbt.compound(b, "Palette");
        palette = new BlockData[pal.size()];
        for (Map.Entry<String, Object> e : pal.entrySet()) {
            BlockData data;
            try {
                data = Bukkit.createBlockData(e.getKey());
            } catch (IllegalArgumentException ex) {
                data = Material.AIR.createBlockData();
            }
            palette[((Number) e.getValue()).intValue()] = data;
        }

        byte[] raw = (byte[]) b.get("Data");
        blocks = new int[width * height * length];
        for (int i = 0, p = 0; i < blocks.length; i++) {
            int v = 0, shift = 0, x;
            do {
                x = raw[p++];
                v |= (x & 0x7F) << shift;
                shift += 7;
            } while ((x & 0x80) != 0);
            blocks[i] = v;
        }

        for (Object o : Nbt.list(b, "BlockEntities")) {
            @SuppressWarnings("unchecked") Map<String, Object> be = (Map<String, Object>) o;
            int[] pos = (int[]) be.get("Pos");
            int i = index(pos[0], pos[1], pos[2]);
            Map<String, Object> data = Nbt.compound(be, "Data");
            if (Nbt.string(be, "Id", "").endsWith("sign")) {
                List<Object> msgs = Nbt.list(Nbt.compound(data, "front_text"), "messages");
                String[] lines = new String[4];
                for (int l = 0; l < 4; l++) lines[l] = l < msgs.size() && msgs.get(l) instanceof String t ? t : "";
                signs.put(i, lines);
            } else if (data.containsKey("Items")) {
                List<Item> items = new ArrayList<>();
                for (Object io : Nbt.list(data, "Items")) {
                    @SuppressWarnings("unchecked") Map<String, Object> it = (Map<String, Object>) io;
                    Material m = Material.matchMaterial(Nbt.string(it, "id", ""));
                    if (m != null) items.add(new Item(Nbt.integer(it, "Slot", 0), m, Nbt.integer(it, "count", 1)));
                }
                containers.put(i, items);
            }
        }
    }

    /**
     * A building made at runtime rather than read from a pack (see {@link CityHouses}): block
     * states in schematic order (x + z*width + y*width*length), layer 0 at world y {@code sourceY}.
     * Signs carry template text like a pack's ("[house]"); {@code lootChests} get house loot.
     */
    MaceBuilding(String name, String type, int width, int height, int length, int sourceY, Map<String, String> meta,
                 String[] states, Map<Integer, String[]> signs, Set<Integer> lootChests) {
        this.name = name;
        this.type = type;
        this.width = width;
        this.height = height;
        this.length = length;
        this.sourceY = sourceY;
        this.mace.putAll(meta);
        Map<String, Integer> index = new HashMap<>();
        List<BlockData> pal = new ArrayList<>();
        blocks = new int[states.length];
        for (int i = 0; i < states.length; i++) {
            blocks[i] = index.computeIfAbsent(states[i], st -> {
                BlockData data;
                try {
                    data = Bukkit.createBlockData(st);
                } catch (IllegalArgumentException ex) {
                    data = Material.AIR.createBlockData();
                }
                pal.add(data);
                return pal.size() - 1;
            });
        }
        palette = pal.toArray(BlockData[]::new);
        this.signs.putAll(signs);
        for (int i : lootChests) this.containers.put(i, List.of(new Item(0, Material.PAPER, 3)));
    }

    /** Reads a gzipped Sponge v3 schematic. */
    static MaceBuilding load(InputStream in) throws IOException {
        return new MaceBuilding(Nbt.readGzipped(in));
    }

    private int index(int x, int y, int z) {
        return x + z * width + y * width * length;
    }

    /** A field of the building's Mace XML entry ("frequency", "files", "pattern", ...), or null. */
    @Nullable
    String meta(String key) {
        return mace.get(key);
    }

    // ── Filling ───────────────────────────────────────────────────────────

    /** {@link #fill(Fill, Random, StructureRotation)} unrotated. */
    Filled fill(Fill fill, Random rng) {
        return fill(fill, rng, StructureRotation.NONE);
    }

    /**
     * Resolves every placeholder and rotates the building. Deterministic for a given fill, RNG
     * seed, rotation and citySigns state.
     */
    Filled fill(Fill fill, Random rng, StructureRotation rotation) {
        BlockData wool = WOOL[rng.nextInt(WOOL.length)];
        boolean entrance = type.equals("city_entrance");

        BlockData[] cells = new BlockData[blocks.length];
        int layer = width * length;
        for (int i = 0; i < blocks.length; i++) {
            BlockData b = palette[blocks[i]];
            boolean ground = i / layer + sourceY == GROUND_Y;
            cells[i] = switch (b.getMaterial()) {
                case LAPIS_ORE -> wool;
                case REDSTONE_ORE -> ground ? fill.ground() : b;
                case GOLD_ORE -> ground ? fill.path() : b;
                case IRON_ORE -> entrance ? fill.wall() : b;
                case GLOWSTONE -> entrance && !fill.walkwayTorches() ? fill.wall() : b;
                case TORCH, WALL_TORCH -> entrance && !fill.walkwayTorches() ? AIR : b;
                case SPAWNER -> AIR;
                default -> b;
            };
        }

        Map<Integer, String[]> filledSigns = new HashMap<>();
        Set<String> instanceSigns = new HashSet<>();
        for (Map.Entry<Integer, String[]> e : signs.entrySet()) {
            String[] text = signText(e.getValue(), fill, rng, instanceSigns);
            if (text == null) cells[e.getKey()] = AIR;
            else filledSigns.put(e.getKey(), text);
        }

        Map<Integer, ItemStack[]> filledContainers = new HashMap<>();
        for (Map.Entry<Integer, List<Item>> e : containers.entrySet()) {
            filledContainers.put(e.getKey(), contents(e.getValue(), fill.loot(), rng));
        }

        if (rotation != StructureRotation.NONE) {
            Map<BlockData, BlockData> rotated = new IdentityHashMap<>();
            for (int i = 0; i < cells.length; i++) {
                cells[i] = rotated.computeIfAbsent(cells[i], b -> {
                    BlockData r = b.clone();
                    r.rotate(rotation);
                    return r;
                });
            }
        }
        return new Filled(cells, filledSigns, filledContainers, rotation);
    }

    /** The sign's four lines, or null to remove it. */
    private static String @Nullable [] signText(String[] lines, Fill fill, Random rng, Set<String> instanceSigns) {
        String text = String.join(" ", lines).trim();
        String first = lines[0].trim().toLowerCase(Locale.ROOT);
        if (first.equals("entity") || first.equals("spawner") || first.equals("gspawner")
                || first.equals("commentsign") || first.equals("comment sign")) {
            return null;
        }
        if (first.equals("cityname")) {
            String city = fill.cityName();
            return city == null ? null : wrap(city.replace("City of ", "City of~"));
        }

        int unique = 0;
        boolean digit = !text.isEmpty() && text.charAt(0) >= '0' && text.charAt(0) <= '2';
        if (digit) {
            unique = text.charAt(0) - '0';
            text = text.substring(1);
        }
        if (!text.contains("[")) {  // plain text: keep the author's lines, minus the uniqueness digit
            String[] kept = lines.clone();
            for (int l = 0; digit && l < 4; l++) {
                if (kept[l].isBlank()) continue;
                kept[l] = kept[l].trim().substring(1);
                break;
            }
            return kept;
        }

        Set<String> citySigns = fill.citySigns();
        String result = text;
        for (int attempt = 0; attempt < 100; attempt++) {
            result = resolve(text, fill.words(), rng);
            boolean duplicate = switch (unique) {
                case 1 -> instanceSigns.contains(result);
                case 2 -> citySigns != null && citySigns.contains(result);
                default -> false;
            };
            if (!duplicate) break;
        }
        instanceSigns.add(result);
        if (citySigns != null) citySigns.add(result);
        return wrap(result);
    }

    private static String resolve(String text, Function<String, @Nullable String> words, Random rng) {
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String token = m.group(1).toLowerCase(Locale.ROOT);
            String word = words.apply(token);
            m.appendReplacement(out, Matcher.quoteReplacement(word != null ? word : "?" + token + "?"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Mace's IsValidSign: whether {@link #wrap} fits the whole text on the sign. */
    static boolean fits(String text) {
        return wrap(text, true) != null;
    }

    /** Mace's ConvertStringToSignText: word-wrap to 14 columns, "~" breaks the line, short text moves down a line. */
    static String[] wrap(String text) {
        return java.util.Objects.requireNonNull(wrap(text, false));
    }

    /** Text that doesn't fit is cut off, or with {@code strict} gives null. */
    private static String @Nullable [] wrap(String text, boolean strict) {
        String[] lines = {"", "", "", ""};
        String[] words = text.replace("~", "~ ").replace("  ", " ").trim().split(" ");
        int line = 0;
        for (String word : words) {
            if (line > 3) {  // a "~" ended the last line and there's more
                if (strict) return null;
                break;
            }
            if (lines[line].length() + word.replace("~", "").length() + 1 > SIGN_WIDTH && ++line > 3) {
                if (strict) return null;
                break;
            }
            lines[line] = (lines[line] + " " + word).trim();
            if (lines[line].endsWith("~")) {
                lines[line] = lines[line].substring(0, lines[line].length() - 1);
                line++;
            }
        }
        if (lines[2].isEmpty()) {
            lines[2] = lines[1];
            lines[1] = lines[0];
            lines[0] = "";
        }
        return lines;
    }

    private static ItemStack[] contents(List<Item> items, boolean loot, Random rng) {
        ItemStack[] out = new ItemStack[27];
        if (!loot) return out;
        Item first = items.stream().filter(it -> it.slot() == 0).findFirst().orElse(null);
        if (first != null && first.material() == Material.PAPER && first.count() == 3) {
            for (int slot = 2 + rng.nextInt(3); slot >= 0; slot--) {
                out[slot] = new ItemStack(HOUSE_LOOT[rng.nextInt(HOUSE_LOOT.length)]);
            }
        } else if (first != null && first.material() == Material.PAPER && first.count() == 4) {
            for (int slot = 5 + rng.nextInt(2); slot >= 2; slot--) {
                out[slot] = new ItemStack(TREASURE_LOOT[rng.nextInt(TREASURE_LOOT.length)]);
            }
            out[0] = new ItemStack(HOUSE_LOOT[rng.nextInt(HOUSE_LOOT.length)]);
            out[1] = new ItemStack(HOUSE_LOOT[rng.nextInt(HOUSE_LOOT.length)]);
        } else {
            for (Item it : items) {
                if (it.slot() >= 0 && it.slot() < out.length) out[it.slot()] = new ItemStack(it.material(), it.count());
            }
        }
        return out;
    }

    // ── Placing ───────────────────────────────────────────────────────────

    /** Receives a building's blocks, by position in its (rotated) box. */
    interface BlockSink {
        void accept(int x, int y, int z, BlockData block);
    }

    /**
     * A building with its placeholders resolved, rotated. Origin (x0, y0, z0) is the minimum
     * corner of its rotated footprint, {@link #sizeX} by {@link #sizeZ}.
     */
    final class Filled {
        private final BlockData[] cells;                // source order, already rotated
        private final Map<Integer, String[]> signs;
        private final Map<Integer, ItemStack[]> containers;
        private final StructureRotation rotation;
        private final int sizeX, sizeZ;

        private Filled(BlockData[] cells, Map<Integer, String[]> signs, Map<Integer, ItemStack[]> containers,
                       StructureRotation rotation) {
            this.cells = cells;
            this.signs = signs;
            this.containers = containers;
            this.rotation = rotation;
            boolean quarter = rotation == StructureRotation.CLOCKWISE_90
                    || rotation == StructureRotation.COUNTERCLOCKWISE_90;
            this.sizeX = quarter ? length : width;
            this.sizeZ = quarter ? width : length;
        }

        MaceBuilding building() {
            return MaceBuilding.this;
        }

        int sizeX() {
            return sizeX;
        }

        int sizeZ() {
            return sizeZ;
        }

        /** The block at (x, y, z) of the rotated building. */
        BlockData block(int x, int y, int z) {
            return cells[source(x, y, z)];
        }

        /** Every block of the rotated building, air included, by its position in the rotated box. */
        void forEachBlock(BlockSink sink) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++) sink.accept(x, y, z, cells[source(x, y, z)]);
                }
            }
        }

        /** The signs' text by position in the rotated box ({x, y, z} -> four lines). */
        Map<int[], String[]> signs() {
            return located(signs);
        }

        /** The containers' contents by position in the rotated box. */
        Map<int[], ItemStack[]> containers() {
            return located(containers);
        }

        private <T> Map<int[], T> located(Map<Integer, T> entries) {
            Map<int[], T> out = new java.util.LinkedHashMap<>();
            int layer = width * length;
            for (Map.Entry<Integer, T> e : entries.entrySet()) {
                int[] xz = rotated(e.getKey());
                out.put(new int[]{xz[0], e.getKey() / layer, xz[1]}, e.getValue());
            }
            return out;
        }

        /** Source index of rotated (x, y, z): the inverse of vanilla's StructureTemplate transform. */
        private int source(int x, int y, int z) {
            return switch (rotation) {
                case NONE -> index(x, y, z);
                case CLOCKWISE_90 -> index(z, y, length - 1 - x);
                case CLOCKWISE_180 -> index(width - 1 - x, y, length - 1 - z);
                case COUNTERCLOCKWISE_90 -> index(width - 1 - z, y, x);
            };
        }

        /** Rotated {x, z} of source index i. */
        private int[] rotated(int i) {
            int sx = i % width, sz = (i / width) % length;
            return switch (rotation) {
                case NONE -> new int[]{sx, sz};
                case CLOCKWISE_90 -> new int[]{length - 1 - sz, sx};
                case CLOCKWISE_180 -> new int[]{width - 1 - sx, length - 1 - sz};
                case COUNTERCLOCKWISE_90 -> new int[]{sz, width - 1 - sx};
            };
        }

        /**
         * Writes this chunk's slice of the building, air included, into ChunkData.
         * Blocks outside the chunk or the world's height are skipped.
         */
        void place(ChunkData data, int chunkX, int chunkZ, int x0, int y0, int z0) {
            int ax = Math.max(0, chunkX * 16 - x0), bx = Math.min(sizeX, chunkX * 16 + 16 - x0);
            int az = Math.max(0, chunkZ * 16 - z0), bz = Math.min(sizeZ, chunkZ * 16 + 16 - z0);
            int ay = Math.max(0, data.getMinHeight() - y0), by = Math.min(height, data.getMaxHeight() - y0);
            for (int y = ay; y < by; y++) {
                for (int z = az; z < bz; z++) {
                    for (int x = ax; x < bx; x++) {
                        data.setBlock(x0 + x - chunkX * 16, y0 + y, z0 + z - chunkZ * 16, cells[source(x, y, z)]);
                    }
                }
            }
        }

        /** Writes sign text and container contents for the part of the building in this (loaded) chunk. */
        void applyBlockEntities(Chunk chunk, int x0, int y0, int z0) {
            applyAll(chunk, x0, y0, z0, signs, (state, lines) -> {
                if (!(state instanceof Sign sign)) return false;
                for (int i = 0; i < 4; i++) sign.getSide(Side.FRONT).line(i, Component.text(lines[i]));
                sign.setWaxed(true);
                return true;
            });
            applyAll(chunk, x0, y0, z0, containers, (state, items) -> {
                if (!(state instanceof Container container)) return false;
                Inventory inv = container.getSnapshotInventory();
                inv.clear();
                for (int i = 0; i < items.length && i < inv.getSize(); i++) {
                    if (items[i] != null) inv.setItem(i, items[i].clone());
                }
                return true;
            });
        }

        private <T> void applyAll(Chunk chunk, int x0, int y0, int z0, Map<Integer, T> entries,
                                  java.util.function.BiPredicate<BlockState, T> apply) {
            int layer = width * length;
            for (Map.Entry<Integer, T> e : entries.entrySet()) {
                int i = e.getKey();
                int[] xz = rotated(i);
                int wx = x0 + xz[0], wy = y0 + i / layer, wz = z0 + xz[1];
                if (wx >> 4 != chunk.getX() || wz >> 4 != chunk.getZ()) continue;
                Block block = chunk.getWorld().getBlockAt(wx, wy, wz);
                BlockState state = block.getState();
                if (apply.test(state, e.getValue())) state.update(true, false);
            }
        }
    }
}
