package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Lantern;

import static org.bukkit.Material.*;

/**
 * Level 1882's architecture, one palette per district (as the Poolrooms pick one per region):
 * walls, their trim, carving and bands, the arch voussoirs, two floor tiles and their border, the
 * display-case wood, signs, lanterns and the shape of the arches.
 */
final class MuseumPalette {

    enum Arch { ROUND, POINTED, SQUARE }

    final String name;
    final BlockData wall, trim, carved, band, accent, tileA, tileB, edge, wood, roof, dais, daisTop, lantern;
    final Material sign, stairs;
    final Arch arch;
    final int weight;

    private MuseumPalette(String name, int weight, Arch arch, Material wall, Material trim, Material carved,
                          Material band, Material accent, Material tileA, Material tileB, Material edge,
                          Material wood, Material sign, Material dais, Material daisTop, boolean soul) {
        this.name = name;
        this.weight = weight;
        this.arch = arch;
        this.wall = wall.createBlockData();
        this.trim = trim.createBlockData();
        this.carved = carved.createBlockData();
        this.band = band.createBlockData();
        this.accent = accent.createBlockData();
        this.tileA = tileA.createBlockData();
        this.tileB = tileB.createBlockData();
        this.edge = edge.createBlockData();
        this.wood = wood.createBlockData();
        this.sign = sign;
        this.stairs = switch (name) {
            case "marble" -> QUARTZ_STAIRS;
            case "brick" -> STONE_BRICK_STAIRS;
            case "gothic" -> POLISHED_DEEPSLATE_STAIRS;
            case "mudbrick" -> MUD_BRICK_STAIRS;
            case "red sandstone" -> SMOOTH_RED_SANDSTONE_STAIRS;
            case "calcite" -> POLISHED_DIORITE_STAIRS;
            case "prismarine" -> PRISMARINE_BRICK_STAIRS;
            case "white" -> SMOOTH_QUARTZ_STAIRS;
            case "blackstone" -> POLISHED_BLACKSTONE_BRICK_STAIRS;
            case "end stone" -> END_STONE_BRICK_STAIRS;
            case "library" -> DARK_OAK_STAIRS;
            case "copper" -> WAXED_CUT_COPPER_STAIRS;
            default -> SMOOTH_SANDSTONE_STAIRS;
        };
        this.roof = DEEPSLATE_TILES.createBlockData();
        this.dais = dais.createBlockData();
        this.daisTop = daisTop.createBlockData();
        Lantern l = (Lantern) (soul ? SOUL_LANTERN : LANTERN).createBlockData();
        l.setHanging(true);
        this.lantern = l;
    }

    static final MuseumPalette[] ALL = {
            // Waterhouse's buff and blue-grey terracotta (Hintze Hall itself)
            new MuseumPalette("terracotta", 5, Arch.ROUND, SMOOTH_SANDSTONE, CUT_SANDSTONE, CHISELED_SANDSTONE,
                    LIGHT_BLUE_TERRACOTTA, LIGHT_BLUE_TERRACOTTA, SMOOTH_SANDSTONE, WHITE_TERRACOTTA, TERRACOTTA,
                    DARK_OAK_PLANKS, DARK_OAK_WALL_SIGN, POLISHED_DEEPSLATE, SMOOTH_STONE, false),
            // a neoclassical marble gallery
            new MuseumPalette("marble", 3, Arch.ROUND, QUARTZ_BLOCK, SMOOTH_QUARTZ, CHISELED_QUARTZ_BLOCK,
                    POLISHED_ANDESITE, QUARTZ_BRICKS, POLISHED_DIORITE, POLISHED_ANDESITE, POLISHED_BLACKSTONE,
                    DARK_OAK_PLANKS, DARK_OAK_WALL_SIGN, POLISHED_BLACKSTONE, POLISHED_DIORITE, false),
            // red brick with stone dressings
            new MuseumPalette("brick", 3, Arch.ROUND, BRICKS, STONE_BRICKS, CHISELED_STONE_BRICKS,
                    POLISHED_ANDESITE, STONE_BRICKS, SMOOTH_STONE, POLISHED_ANDESITE, BRICKS,
                    SPRUCE_PLANKS, SPRUCE_WALL_SIGN, POLISHED_ANDESITE, SMOOTH_STONE, false),
            // dark Gothic stone
            new MuseumPalette("gothic", 2, Arch.POINTED, DEEPSLATE_BRICKS, POLISHED_DEEPSLATE, CHISELED_DEEPSLATE,
                    TUFF_BRICKS, POLISHED_TUFF, POLISHED_TUFF, TUFF_BRICKS, POLISHED_DEEPSLATE,
                    DARK_OAK_PLANKS, DARK_OAK_WALL_SIGN, POLISHED_DEEPSLATE, POLISHED_TUFF, false),
            // the Egyptian wing
            new MuseumPalette("mudbrick", 2, Arch.SQUARE, MUD_BRICKS, SMOOTH_SANDSTONE, CHISELED_SANDSTONE,
                    ORANGE_TERRACOTTA, PACKED_MUD, SMOOTH_SANDSTONE, YELLOW_TERRACOTTA, BROWN_TERRACOTTA,
                    JUNGLE_PLANKS, JUNGLE_WALL_SIGN, BROWN_TERRACOTTA, SMOOTH_SANDSTONE, false),
            // red sandstone inlaid with white
            new MuseumPalette("red sandstone", 2, Arch.POINTED, SMOOTH_RED_SANDSTONE, CUT_RED_SANDSTONE,
                    CHISELED_RED_SANDSTONE, SMOOTH_QUARTZ, SMOOTH_QUARTZ, SMOOTH_QUARTZ, SMOOTH_RED_SANDSTONE,
                    RED_TERRACOTTA, ACACIA_PLANKS, ACACIA_WALL_SIGN, CUT_RED_SANDSTONE, SMOOTH_QUARTZ, false),
            // the geology wing: calcite and dripstone
            new MuseumPalette("calcite", 2, Arch.ROUND, CALCITE, DRIPSTONE_BLOCK, CHISELED_TUFF,
                    SMOOTH_BASALT, DRIPSTONE_BLOCK, SMOOTH_STONE, CALCITE, SMOOTH_BASALT,
                    SPRUCE_PLANKS, SPRUCE_WALL_SIGN, SMOOTH_BASALT, CALCITE, false),
            // the marine wing
            new MuseumPalette("prismarine", 2, Arch.ROUND, PRISMARINE_BRICKS, DARK_PRISMARINE, PRISMARINE,
                    DARK_PRISMARINE, SEA_LANTERN, POLISHED_DIORITE, CYAN_TERRACOTTA, DARK_PRISMARINE,
                    DARK_OAK_PLANKS, DARK_OAK_WALL_SIGN, DARK_PRISMARINE, PRISMARINE_BRICKS, true),
            // a modern white gallery
            new MuseumPalette("white", 2, Arch.SQUARE, WHITE_CONCRETE, SMOOTH_QUARTZ, QUARTZ_PILLAR,
                    LIGHT_GRAY_CONCRETE, WHITE_CONCRETE, SMOOTH_STONE, POLISHED_ANDESITE, POLISHED_ANDESITE,
                    BIRCH_PLANKS, BIRCH_WALL_SIGN, WHITE_CONCRETE, SMOOTH_QUARTZ, false),
            // the Nether wing
            new MuseumPalette("blackstone", 1, Arch.POINTED, POLISHED_BLACKSTONE_BRICKS, POLISHED_BLACKSTONE,
                    CHISELED_POLISHED_BLACKSTONE, NETHER_BRICKS, RED_NETHER_BRICKS, SMOOTH_BASALT,
                    POLISHED_BLACKSTONE, NETHER_BRICKS, CRIMSON_PLANKS, CRIMSON_WALL_SIGN, BLACKSTONE,
                    POLISHED_BLACKSTONE, true),
            // the End wing
            new MuseumPalette("end stone", 1, Arch.SQUARE, END_STONE_BRICKS, PURPUR_BLOCK, PURPUR_PILLAR,
                    PURPUR_BLOCK, PURPUR_BLOCK, END_STONE_BRICKS, PURPUR_BLOCK, OBSIDIAN,
                    BIRCH_PLANKS, BIRCH_WALL_SIGN, OBSIDIAN, END_STONE_BRICKS, false),
            // a wood-panelled library wing, bookshelves for bands
            new MuseumPalette("library", 2, Arch.ROUND, DARK_OAK_PLANKS, STRIPPED_DARK_OAK_WOOD, CHISELED_BOOKSHELF,
                    BOOKSHELF, SPRUCE_PLANKS, SPRUCE_PLANKS, DARK_OAK_PLANKS, STRIPPED_SPRUCE_WOOD,
                    SPRUCE_PLANKS, SPRUCE_WALL_SIGN, STRIPPED_DARK_OAK_WOOD, SPRUCE_PLANKS, false),
            // an Art Deco wing in waxed copper
            new MuseumPalette("copper", 1, Arch.SQUARE, WAXED_CUT_COPPER, WAXED_COPPER_BLOCK, WAXED_CHISELED_COPPER,
                    WAXED_OXIDIZED_CUT_COPPER, WAXED_OXIDIZED_COPPER, SMOOTH_STONE, POLISHED_TUFF,
                    WAXED_EXPOSED_CUT_COPPER, DARK_OAK_PLANKS, DARK_OAK_WALL_SIGN, POLISHED_TUFF, SMOOTH_STONE,
                    false),
    };

    private static final int TOTAL;
    static {
        int t = 0;
        for (MuseumPalette p : ALL) t += p.weight;
        TOTAL = t;
    }

    static MuseumPalette pick(long hash) {
        int roll = (int) Math.floorMod(hash, (long) TOTAL);
        for (MuseumPalette p : ALL) {
            if (roll < p.weight) return p;
            roll -= p.weight;
        }
        return ALL[0];
    }
}
