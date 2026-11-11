package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.bukkit.Material.*;

/**
 * Block palettes for Level 974's planets. Each palette maps a position inside a sphere to a block:
 * mostly concentric layers (shell → core), with optional surface tops, per-block mixes, y-banding
 * for gas giants, and hollow centers.
 *
 * <p>Everything here is immutable after class init, so it is safe to use from chunk worker threads.
 */
final class PlanetPalettes {

    private PlanetPalettes() {}

    /**
     * Picks the block at one position of a planet, or null for air.
     *
     * @param f   distance from the center as a fraction of the radius, 0 (center) .. 1 (surface)
     * @param top true if the block above is outside the planet
     * @param h   per-block hash, for random mixes
     * @param ph  per-planet hash, for per-planet choices and noise seeds
     */
    @FunctionalInterface
    interface Picker {
        @Nullable BlockData pick(double f, boolean top, long h, int wx, int wy, int wz, long ph);
    }

    record Palette(String name, int weight, int rMin, int rMax, Picker picker) {}

    /** A weighted block mix, sampled by hash. */
    static final class Mix {
        private final BlockData[] table;

        private Mix(BlockData[] table) { this.table = table; }

        /** Alternating (Material or BlockData, Integer weight) pairs; a bare trailing block has weight 1. */
        static Mix of(Object... parts) {
            List<BlockData> t = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) {
                BlockData b = parts[i] instanceof Material m ? data(m) : (BlockData) parts[i];
                int w = (i + 1 < parts.length && parts[i + 1] instanceof Integer n) ? n : 1;
                if (i + 1 < parts.length && parts[i + 1] instanceof Integer) i++;
                for (int k = 0; k < w; k++) t.add(b);
            }
            return new Mix(t.toArray(new BlockData[0]));
        }

        BlockData pick(long h) {
            return table[(int) Long.remainderUnsigned(h >>> 7, table.length)];
        }
    }

    // ---- builders ----

    private static BlockData data(Material m) {
        return m.createBlockData();
    }

    private static BlockData leaf(Material m) {
        return Bukkit.createBlockData(m, "[persistent=true]");
    }

    private static Mix one(Material m) { return Mix.of(m); }

    /**
     * Concentric layers from the outside in: alternating (Double outer fraction, Mix-or-null).
     * The innermost layer whose fraction is at least f wins; a null mix is hollow (air).
     */
    private static Picker layers(Object... args) {
        int n = args.length / 2;
        double[] fr = new double[n];
        Mix[] mx = new Mix[n];
        for (int i = 0; i < n; i++) {
            fr[i] = (Double) args[2 * i];
            Object m = args[2 * i + 1];
            mx[i] = m == null ? null : m instanceof Material mat ? one(mat) : (Mix) m;
        }
        return (f, top, h, wx, wy, wz, ph) -> {
            for (int i = n - 1; i >= 0; i--) {
                if (f <= fr[i]) return mx[i] == null ? null : mx[i].pick(h);
            }
            return mx[0] == null ? null : mx[0].pick(h);
        };
    }

    /** Uses {@code top} for surface blocks with open space above, {@code base} elsewhere. */
    private static Picker withTop(Mix top, Picker base) {
        return (f, t, h, wx, wy, wz, ph) -> t && f > 0.6 ? top.pick(h) : base.pick(f, t, h, wx, wy, wz, ph);
    }

    private static Picker withTop(Material top, Picker base) {
        return withTop(one(top), base);
    }

    /** Horizontal bands by y, wobbled by noise — gas giants and mesas. */
    private static Picker bands(double width, double wobble, Material... colours) {
        BlockData[] c = new BlockData[colours.length];
        for (int i = 0; i < c.length; i++) c[i] = data(colours[i]);
        return (f, top, h, wx, wy, wz, ph) -> {
            double n = SimplexNoise.noise3(ph, wx * 0.08, wy * 0.15, wz * 0.08);
            int i = (int) Math.floor((wy + wobble * n) / width);
            return c[Math.floorMod(i + (int) (ph & 0xF), c.length)];
        };
    }

    /** Shell of one colour, core of another, both chosen per planet. */
    private static Picker perPlanet(Material[] shells, Material[] cores, double coreFrac) {
        BlockData[] s = new BlockData[shells.length];
        BlockData[] c = new BlockData[cores.length];
        for (int i = 0; i < s.length; i++) s[i] = data(shells[i]);
        for (int i = 0; i < c.length; i++) c[i] = data(cores[i]);
        return (f, top, h, wx, wy, wz, ph) -> f <= coreFrac
                ? c[(int) Long.remainderUnsigned(ph >>> 20, c.length)]
                : s[(int) Long.remainderUnsigned(ph >>> 4, s.length)];
    }

    // ---- palettes ----

    private static final Material[] STAINED_GLASS = {
            WHITE_STAINED_GLASS, ORANGE_STAINED_GLASS, MAGENTA_STAINED_GLASS, LIGHT_BLUE_STAINED_GLASS,
            YELLOW_STAINED_GLASS, LIME_STAINED_GLASS, PINK_STAINED_GLASS, GRAY_STAINED_GLASS,
            LIGHT_GRAY_STAINED_GLASS, CYAN_STAINED_GLASS, PURPLE_STAINED_GLASS, BLUE_STAINED_GLASS,
            BROWN_STAINED_GLASS, GREEN_STAINED_GLASS, RED_STAINED_GLASS, BLACK_STAINED_GLASS,
    };

    private static final Material[] CONCRETE = {
            WHITE_CONCRETE, ORANGE_CONCRETE, MAGENTA_CONCRETE, LIGHT_BLUE_CONCRETE,
            YELLOW_CONCRETE, LIME_CONCRETE, PINK_CONCRETE, GRAY_CONCRETE,
            LIGHT_GRAY_CONCRETE, CYAN_CONCRETE, PURPLE_CONCRETE, BLUE_CONCRETE,
            BROWN_CONCRETE, GREEN_CONCRETE, RED_CONCRETE, BLACK_CONCRETE,
    };

    private static final Mix STONE_ORES = Mix.of(
            STONE, 60, COAL_ORE, 12, IRON_ORE, 10, COPPER_ORE, 6, GOLD_ORE, 4,
            REDSTONE_ORE, 3, LAPIS_ORE, 3, DIAMOND_ORE, 2);

    private static final Mix DEEPSLATE_ORES = Mix.of(
            DEEPSLATE, 55, DEEPSLATE_COAL_ORE, 8, DEEPSLATE_IRON_ORE, 10, DEEPSLATE_COPPER_ORE, 5,
            DEEPSLATE_GOLD_ORE, 6, DEEPSLATE_REDSTONE_ORE, 7, DEEPSLATE_LAPIS_ORE, 5, DEEPSLATE_DIAMOND_ORE, 4);

    private static Palette wood(String name, Material leaves, Material log) {
        return new Palette(name, 2, 6, 14, layers(1.0, Mix.of(leaf(leaves)), 0.5, log));
    }

    private static Palette star(String name, int weight, Material m) {
        return new Palette(name, weight, 3, 6, layers(1.0, m));
    }

    /** Grass on dirt around water — also the spawn planet. */
    static final Palette GRASS = new Palette("grass", 5, 7, 14,
            withTop(GRASS_BLOCK, layers(1.0, DIRT, 0.35, WATER)));

    static final List<Palette> ALL = List.of(
            // trees
            wood("oak", OAK_LEAVES, OAK_LOG),
            wood("spruce", SPRUCE_LEAVES, SPRUCE_LOG),
            wood("birch", BIRCH_LEAVES, BIRCH_LOG),
            wood("jungle", JUNGLE_LEAVES, JUNGLE_LOG),
            wood("acacia", ACACIA_LEAVES, ACACIA_LOG),
            wood("dark_oak", DARK_OAK_LEAVES, DARK_OAK_LOG),
            wood("cherry", CHERRY_LEAVES, CHERRY_LOG),
            wood("pale_oak", PALE_OAK_LEAVES, PALE_OAK_LOG),
            new Palette("mangrove", 2, 6, 13, layers(
                    1.0, Mix.of(leaf(MANGROVE_LEAVES)),
                    0.55, Mix.of(MANGROVE_ROOTS, 2, MUDDY_MANGROVE_ROOTS, 1),
                    0.35, MANGROVE_LOG)),
            new Palette("azalea", 2, 6, 12, layers(
                    1.0, Mix.of(leaf(AZALEA_LEAVES), 3, leaf(FLOWERING_AZALEA_LEAVES), 1),
                    0.5, ROOTED_DIRT,
                    0.25, OAK_LOG)),

            // fungi
            new Palette("red_mushroom", 2, 6, 12, layers(1.0, RED_MUSHROOM_BLOCK, 0.45, MUSHROOM_STEM)),
            new Palette("brown_mushroom", 2, 6, 12, layers(1.0, BROWN_MUSHROOM_BLOCK, 0.45, MUSHROOM_STEM)),
            new Palette("crimson", 2, 6, 13, layers(
                    1.0, Mix.of(NETHER_WART_BLOCK, 16, SHROOMLIGHT, 1), 0.45, CRIMSON_STEM)),
            new Palette("warped", 2, 6, 13, layers(
                    1.0, Mix.of(WARPED_WART_BLOCK, 16, SHROOMLIGHT, 1), 0.45, WARPED_STEM)),

            // soils
            GRASS,
            new Palette("grass_stone", 3, 8, 15, withTop(GRASS_BLOCK, layers(1.0, DIRT, 0.75, STONE_ORES))),
            new Palette("mycelium", 1, 7, 13, withTop(MYCELIUM, layers(1.0, DIRT, 0.4, MUSHROOM_STEM))),
            new Palette("taiga", 1, 7, 14, withTop(Mix.of(PODZOL, 3, COARSE_DIRT, 1),
                    layers(1.0, Mix.of(DIRT, 3, COARSE_DIRT, 1), 0.6, Mix.of(STONE, 3, MOSSY_COBBLESTONE, 1)))),
            new Palette("moss", 2, 6, 13, layers(1.0, MOSS_BLOCK, 0.6, Mix.of(COBBLESTONE, 2, MOSSY_COBBLESTONE, 3))),
            new Palette("pale_moss", 1, 6, 12, layers(1.0, PALE_MOSS_BLOCK, 0.45, PALE_OAK_LOG)),
            new Palette("swamp", 2, 7, 14, withTop(GRASS_BLOCK, layers(1.0, MUD, 0.5, CLAY))),
            new Palette("desert", 3, 7, 15, withTop(SAND, layers(1.0, Mix.of(SANDSTONE, 3, SMOOTH_SANDSTONE, 1), 0.55, SAND))),
            new Palette("red_desert", 2, 7, 14, withTop(RED_SAND, layers(1.0, RED_SANDSTONE, 0.5, TERRACOTTA))),
            new Palette("mesa", 2, 9, 15, withTop(RED_SAND, bands(2.0, 2.5,
                    TERRACOTTA, ORANGE_TERRACOTTA, YELLOW_TERRACOTTA, TERRACOTTA, WHITE_TERRACOTTA,
                    LIGHT_GRAY_TERRACOTTA, BROWN_TERRACOTTA, RED_TERRACOTTA))),
            new Palette("clay", 1, 6, 11, layers(1.0, CLAY, 0.5, TERRACOTTA)),
            new Palette("dripstone", 1, 6, 12, layers(1.0, DRIPSTONE_BLOCK, 0.5, CALCITE, 0.2, RAW_COPPER_BLOCK)),

            // rock and ore
            new Palette("stone", 6, 7, 15, layers(1.0, STONE, 0.5, STONE_ORES)),
            new Palette("deepslate", 3, 7, 15, layers(
                    1.0, Mix.of(DEEPSLATE, 8, TUFF, 1), 0.85, DEEPSLATE, 0.5, DEEPSLATE_ORES)),
            new Palette("marble", 2, 8, 15, layers(
                    1.0, Mix.of(GRANITE, 2, DIORITE, 2, ANDESITE, 2, CALCITE, 1), 0.3, RAW_IRON_BLOCK)),
            new Palette("copper", 2, 6, 12, layers(
                    1.0, Mix.of(COPPER_BLOCK, 1, EXPOSED_COPPER, 1, WEATHERED_COPPER, 1, OXIDIZED_COPPER, 2),
                    0.35, RAW_COPPER_BLOCK)),
            new Palette("gilded", 1, 6, 11, layers(
                    1.0, Mix.of(BLACKSTONE, 6, GILDED_BLACKSTONE, 1), 0.3, RAW_GOLD_BLOCK)),
            new Palette("geode", 2, 7, 13, layers(
                    1.0, SMOOTH_BASALT, 0.85, CALCITE, 0.72, Mix.of(AMETHYST_BLOCK, 8, BUDDING_AMETHYST, 1), 0.58, null)),
            new Palette("sculk", 2, 7, 14, layers(1.0, SCULK, 0.8, DEEPSLATE, 0.25, REINFORCED_DEEPSLATE)),

            // ice and water
            new Palette("ice", 3, 6, 14, layers(1.0, PACKED_ICE, 0.5, BLUE_ICE)),
            new Palette("snowball", 2, 6, 12, layers(1.0, SNOW_BLOCK, 0.5, POWDER_SNOW)),
            new Palette("aquarium", 2, 6, 13, layers(1.0, GLASS, 0.8, WATER)),
            new Palette("prismarine", 2, 7, 14, layers(
                    1.0, Mix.of(PRISMARINE, 2, PRISMARINE_BRICKS, 1, DARK_PRISMARINE, 1), 0.8, WATER, 0.3, SEA_LANTERN)),

            // nether and end
            new Palette("nether", 3, 7, 15, layers(
                    1.0, Mix.of(NETHERRACK, 20, NETHER_QUARTZ_ORE, 2, NETHER_GOLD_ORE, 1), 0.5, MAGMA_BLOCK, 0.3, LAVA)),
            new Palette("soul", 2, 7, 13, layers(1.0, Mix.of(SOUL_SOIL, 2, SOUL_SAND, 1), 0.4, BONE_BLOCK)),
            new Palette("basalt", 2, 7, 14, layers(1.0, Mix.of(BASALT, 3, POLISHED_BASALT, 1), 0.4, MAGMA_BLOCK, 0.2, LAVA)),
            new Palette("netherite", 1, 5, 8, layers(1.0, NETHERRACK, 0.35, ANCIENT_DEBRIS)),
            new Palette("obsidian", 2, 6, 12, layers(1.0, Mix.of(OBSIDIAN, 9, CRYING_OBSIDIAN, 1), 0.4, LAVA)),
            new Palette("end", 2, 7, 14, layers(1.0, END_STONE, 0.5, PURPUR_BLOCK, 0.2, OBSIDIAN)),

            // gas giants
            new Palette("jupiter", 2, 13, 17, bands(2.0, 3.0,
                    WHITE_TERRACOTTA, ORANGE_TERRACOTTA, BROWN_TERRACOTTA, YELLOW_TERRACOTTA,
                    WHITE_TERRACOTTA, LIGHT_GRAY_TERRACOTTA, ORANGE_TERRACOTTA, RED_TERRACOTTA)),
            new Palette("neptune", 2, 13, 17, bands(2.5, 2.0,
                    BLUE_CONCRETE, LIGHT_BLUE_CONCRETE, CYAN_CONCRETE, BLUE_CONCRETE, LIGHT_BLUE_CONCRETE, WHITE_CONCRETE)),
            new Palette("saturn", 1, 13, 17, bands(1.5, 1.5,
                    YELLOW_TERRACOTTA, WHITE_TERRACOTTA, SMOOTH_SANDSTONE, YELLOW_TERRACOTTA, BIRCH_PLANKS)),

            // oddities
            new Palette("honey", 2, 6, 12, layers(1.0, HONEYCOMB_BLOCK, 0.6, HONEY_BLOCK)),
            new Palette("hay", 1, 5, 10, layers(1.0, HAY_BLOCK)),
            new Palette("pumpkin", 1, 5, 10, layers(1.0, PUMPKIN, 0.35, JACK_O_LANTERN)),
            new Palette("melon", 1, 5, 10, layers(1.0, MELON)),
            new Palette("fossil", 1, 6, 11, layers(1.0, BONE_BLOCK, 0.3, COAL_BLOCK)),
            new Palette("library", 1, 6, 11, layers(1.0, BOOKSHELF, 0.7, OAK_PLANKS)),
            new Palette("slime", 1, 5, 10, layers(1.0, SLIME_BLOCK, 0.5, MOSS_BLOCK)),
            new Palette("resin", 1, 6, 11, layers(1.0, Mix.of(RESIN_BLOCK, 3, RESIN_BRICKS, 1), 0.4, PALE_OAK_LOG)),
            new Palette("tnt", 1, 5, 9, layers(1.0, TNT, 0.4, STONE)),
            new Palette("confetti", 1, 6, 11, layers(1.0, Mix.of((Object[]) CONCRETE))),
            new Palette("marble_glass", 2, 6, 12, perPlanet(STAINED_GLASS, CONCRETE, 0.55)),

            // stars
            star("glowstone", 2, GLOWSTONE),
            star("sea_lantern", 1, SEA_LANTERN),
            star("shroomlight", 1, SHROOMLIGHT),
            star("ochre", 1, OCHRE_FROGLIGHT),
            star("verdant", 1, VERDANT_FROGLIGHT),
            star("pearlescent", 1, PEARLESCENT_FROGLIGHT)
    );

    private static final int TOTAL_WEIGHT = ALL.stream().mapToInt(Palette::weight).sum();

    static Palette pick(Random rng) {
        int roll = rng.nextInt(TOTAL_WEIGHT);
        for (Palette p : ALL) {
            roll -= p.weight();
            if (roll < 0) return p;
        }
        return GRASS;
    }
}
