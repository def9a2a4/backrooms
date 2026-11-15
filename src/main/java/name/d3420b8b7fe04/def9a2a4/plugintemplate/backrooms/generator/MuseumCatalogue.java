package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.bukkit.Material.*;

/**
 * Level 1882's collections and the dry text that goes with them: what each kind of gallery
 * holds, and the labels, gallery signs and books, written the way a museum catalogue would be.
 * Everything here is chosen from a Random, so a label's text follows from its seed.
 */
public final class MuseumCatalogue {

    private MuseumCatalogue() {}

    public enum Rarity {
        COMMON("Common"), UNCOMMON("Uncommon"), RARE("Rare"), EPIC("Epic");

        final String label;
        Rarity(String label) { this.label = label; }
    }

    /** What a label is about. */
    public enum Topic { SPECIMEN, SKELETON, MONOLITH, POTS, PAINTING, ROOM, GUIDE, MURAL, TANK }

    /** A gallery's subject: its titles, and what its cases hold. */
    public enum Theme {
        MINERALS("Minerals", "Minerals and Ores", "Mineralogy", "Gems and Crystals", "Economic Minerals"),
        FOSSILS("Fossils", "Fossil Reptiles", "Palaeontology", "Fossil Mammals", "Life in Deep Time"),
        MOBS("Comparative Osteology", "Hostile Mobs", "Skeletons", "Anatomy of the Overworld", "Mob Bones"),
        BOTANY("Botany", "Flowering Plants", "Fungi", "Seeds and Saplings", "The Herbarium"),
        MARINE("Marine Life", "The Oceans", "Corals and Sponges", "Life of the Reef", "Marine Invertebrates"),
        ARCHAEOLOGY("Archaeology", "Antiquities", "Ancient Tools", "Trail Ruins", "Pottery and Tile"),
        NETHER("The Nether", "Nether Geology", "Fauna of the Nether", "Fortress Finds", "Basalt and Soul"),
        END("The End", "Outer Islands", "End Fauna", "Purpur and Chorus", "The Void"),
        CURIOSITIES("Curiosities", "Instruments", "Mechanisms", "The Cabinet", "Miscellanea"),
        ART("Paintings", "Fine Art", "Landscapes", "Portraits and Still Life", "Gifts and Bequests"),
        ZOOLOGY("Zoology", "Eggs and Nests", "Domestic Animals", "Insects", "Wool and Hide"),
        GEOLOGY("Geology", "Rocks of the Overworld", "Meteorites", "Caves and Karst", "Volcanoes");

        final String[] titles;
        Theme(String... titles) { this.titles = titles; }
    }

    /** One kind of thing in a case: the block, what the label calls it, how rare it is. */
    record Specimen(Material block, String name, Rarity rarity) {}

    private static Specimen s(Material m) {
        return new Specimen(m, null, Rarity.COMMON);
    }

    private static Specimen s(Material m, Rarity r) {
        return new Specimen(m, null, r);
    }

    private static Specimen s(Material m, String name) {
        return new Specimen(m, name, Rarity.COMMON);
    }

    private static Specimen s(Material m, String name, Rarity r) {
        return new Specimen(m, name, r);
    }

    private static final Rarity U = Rarity.UNCOMMON, R = Rarity.RARE, E = Rarity.EPIC;

    static Specimen[] pool(Theme t) {
        return switch (t) {
            case MINERALS -> new Specimen[]{
                    s(AMETHYST_CLUSTER), s(LARGE_AMETHYST_BUD), s(BUDDING_AMETHYST, U), s(RAW_IRON_BLOCK), s(RAW_COPPER_BLOCK),
                    s(RAW_GOLD_BLOCK, U), s(IRON_ORE), s(COPPER_ORE), s(GOLD_ORE, U), s(DIAMOND_ORE, R), s(EMERALD_ORE, R),
                    s(LAPIS_ORE), s(REDSTONE_ORE), s(COAL_ORE), s(DEEPSLATE_DIAMOND_ORE, R), s(DEEPSLATE_GOLD_ORE, U),
                    s(DEEPSLATE_COPPER_ORE), s(NETHER_QUARTZ_ORE), s(ANCIENT_DEBRIS, E), s(DIAMOND_BLOCK, "Diamond, cut", E),
                    s(EMERALD_BLOCK, "Emerald, cut", R), s(LAPIS_BLOCK, U), s(AMETHYST_BLOCK), s(QUARTZ_BLOCK, "Quartz, dressed"),
                    s(COAL_BLOCK), s(GOLD_BLOCK, "Gold, refined", R), s(COPPER_BLOCK, "Native copper"),
                    s(OXIDIZED_COPPER, "Copper, weathered"), s(RAW_IRON_BLOCK, "Iron, raw"), s(GLOWSTONE, U)};
            case FOSSILS -> new Specimen[]{
                    s(BONE_BLOCK, "Vertebra"), s(BONE_BLOCK, "Femur, fragment"), s(SKELETON_SKULL, "Skull"),
                    s(WITHER_SKELETON_SKULL, "Skull, blackened", R), s(SNIFFER_EGG, "Egg, Sniffer", R), s(TURTLE_EGG, "Eggs, Turtle"),
                    s(DEAD_BRAIN_CORAL_BLOCK, "Coral, fossil"), s(DEAD_HORN_CORAL_BLOCK, "Coral, fossil"),
                    s(HONEYCOMB_BLOCK, "Amber", U), s(PACKED_MUD, "Trackway, cast"), s(SUSPICIOUS_SAND, "Matrix, unprepared"),
                    s(DIORITE, "Limestone with shells"), s(MUD, "Mud, Mangrove Swamp"), s(CLAY, "Clay with fossils"),
                    s(CALCITE, "Calcite, from a skeleton"), s(DRIPSTONE_BLOCK, "Cave deposit")};
            case MOBS -> new Specimen[]{
                    s(SKELETON_SKULL, "Skeleton, skull"), s(WITHER_SKELETON_SKULL, "Wither Skeleton, skull", R),
                    s(ZOMBIE_HEAD, "Zombie, head"), s(CREEPER_HEAD, "Creeper, head", U), s(PIGLIN_HEAD, "Piglin, head", U),
                    s(DRAGON_HEAD, "Ender Dragon, head", E), s(BONE_BLOCK, "Bones, assorted"), s(SLIME_BLOCK, "Slime, preserved"),
                    s(COBWEB, "Spider silk"), s(MAGMA_BLOCK, "Magma Cube, section", U), s(PURPLE_SHULKER_BOX, "Shulker, shell", R),
                    s(SPONGE, "Sponge, dried"), s(HONEY_BLOCK, "Honey, Bee"), s(WHITE_CARPET, "Phantom, membrane", U)};
            case BOTANY -> new Specimen[]{
                    s(POTTED_FERN), s(POTTED_CACTUS), s(POTTED_DEAD_BUSH), s(POTTED_AZALEA_BUSH), s(POTTED_FLOWERING_AZALEA_BUSH),
                    s(POTTED_BAMBOO), s(POTTED_RED_MUSHROOM), s(POTTED_BROWN_MUSHROOM), s(POTTED_OAK_SAPLING),
                    s(POTTED_SPRUCE_SAPLING), s(POTTED_BIRCH_SAPLING), s(POTTED_JUNGLE_SAPLING), s(POTTED_ACACIA_SAPLING),
                    s(POTTED_DARK_OAK_SAPLING), s(POTTED_MANGROVE_PROPAGULE), s(POTTED_CHERRY_SAPLING, U),
                    s(POTTED_TORCHFLOWER, R), s(POTTED_BLUE_ORCHID), s(POTTED_ALLIUM), s(POTTED_LILY_OF_THE_VALLEY),
                    s(POTTED_WITHER_ROSE, R), s(POTTED_POPPY), s(POTTED_CORNFLOWER), s(POTTED_OXEYE_DAISY),
                    s(MOSS_BLOCK), s(MUSHROOM_STEM), s(RED_MUSHROOM_BLOCK), s(BROWN_MUSHROOM_BLOCK), s(PUMPKIN), s(MELON),
                    s(HAY_BLOCK, "Wheat, sheaf"), s(DRIED_KELP_BLOCK, "Kelp, dried")};
            case MARINE -> new Specimen[]{
                    s(DEAD_TUBE_CORAL_BLOCK, "Coral, Tube"), s(DEAD_BRAIN_CORAL_BLOCK, "Coral, Brain"),
                    s(DEAD_BUBBLE_CORAL_BLOCK, "Coral, Bubble"), s(DEAD_FIRE_CORAL_BLOCK, "Coral, Fire"),
                    s(DEAD_HORN_CORAL_BLOCK, "Coral, Horn"), s(SPONGE, "Sponge"), s(WET_SPONGE, "Sponge, in spirit"),
                    s(PRISMARINE, "Prismarine, rough"), s(PRISMARINE_BRICKS, "Prismarine, worked", U), s(SEA_LANTERN, U),
                    s(CONDUIT, R), s(TURTLE_EGG, "Eggs, Turtle"), s(DRIED_KELP_BLOCK, "Kelp, dried"), s(SEA_PICKLE),
                    s(SAND, "Sand, Warm Ocean"), s(GRAVEL, "Sea floor sample")};
            case ARCHAEOLOGY -> new Specimen[]{
                    s(DECORATED_POT, "Pot"), s(CHISELED_STONE_BRICKS, "Carved block"), s(CHISELED_SANDSTONE, "Carved block, desert"),
                    s(CHISELED_RED_SANDSTONE, "Carved block, Badlands"), s(CHISELED_DEEPSLATE, "Carved block, ancient city", U),
                    s(CHISELED_TUFF, "Carved block, trial chamber"), s(CHISELED_TUFF_BRICKS, "Lintel, trial chamber"),
                    s(CHISELED_QUARTZ_BLOCK, "Column drum"), s(CHISELED_POLISHED_BLACKSTONE, "Carved block, bastion", U),
                    s(MOSSY_STONE_BRICKS, "Wall fragment"), s(CRACKED_STONE_BRICKS, "Wall fragment"), s(GOLD_BLOCK, "Hoard", R),
                    s(BELL, "Bell, village"), s(CHIPPED_ANVIL, "Anvil"), s(DAMAGED_ANVIL, "Anvil, worn"), s(LODESTONE, U),
                    s(LOOM), s(GRINDSTONE), s(SMITHING_TABLE), s(FLETCHING_TABLE), s(CARTOGRAPHY_TABLE), s(COMPOSTER),
                    s(BARREL), s(WHITE_GLAZED_TERRACOTTA, "Tile, glazed"), s(BLUE_GLAZED_TERRACOTTA, "Tile, glazed"),
                    s(ORANGE_GLAZED_TERRACOTTA, "Tile, glazed"), s(CYAN_GLAZED_TERRACOTTA, "Tile, glazed"),
                    s(HEAVY_CORE, "Core, trial chamber", E)};
            case NETHER -> new Specimen[]{
                    s(NETHERRACK), s(NETHER_WART_BLOCK), s(WARPED_WART_BLOCK), s(SHROOMLIGHT), s(CRIMSON_NYLIUM),
                    s(WARPED_NYLIUM), s(SOUL_SAND), s(SOUL_SOIL), s(MAGMA_BLOCK), s(GLOWSTONE), s(ANCIENT_DEBRIS, E),
                    s(NETHERITE_BLOCK, "Netherite, smelted", E), s(GILDED_BLACKSTONE, U), s(CRYING_OBSIDIAN, U),
                    s(RESPAWN_ANCHOR, R), s(NETHER_GOLD_ORE), s(NETHER_QUARTZ_ORE), s(PIGLIN_HEAD, "Piglin, head", U),
                    s(WITHER_SKELETON_SKULL, "Wither Skeleton, skull", R), s(BASALT), s(BLACKSTONE), s(CRIMSON_STEM),
                    s(WARPED_STEM), s(POTTED_CRIMSON_ROOTS), s(POTTED_WARPED_ROOTS), s(POTTED_CRIMSON_FUNGUS),
                    s(POTTED_WARPED_FUNGUS)};
            case END -> new Specimen[]{
                    s(END_STONE), s(END_STONE_BRICKS), s(PURPUR_BLOCK), s(PURPUR_PILLAR), s(CHORUS_FLOWER, U),
                    s(CHORUS_PLANT), s(PURPLE_SHULKER_BOX, "Shulker, shell", R), s(DRAGON_HEAD, "Ender Dragon, head", E),
                    s(END_ROD), s(OBSIDIAN), s(ENDER_CHEST, U), s(END_PORTAL_FRAME, R), s(CRYING_OBSIDIAN, U)};
            case CURIOSITIES -> new Specimen[]{
                    s(CONDUIT, R), s(BEACON, E), s(ENCHANTING_TABLE, R), s(BREWING_STAND, U), s(JUKEBOX), s(NOTE_BLOCK),
                    s(BELL), s(LODESTONE, U), s(RESPAWN_ANCHOR, R), s(END_PORTAL_FRAME, R), s(HEAVY_CORE, E), s(CRAFTER),
                    s(OBSERVER), s(PISTON), s(STICKY_PISTON), s(TARGET), s(DAYLIGHT_DETECTOR), s(TNT, "TNT, inert"),
                    s(SCULK_CATALYST, R), s(SCULK_SENSOR, U), s(COPPER_BULB), s(LIGHTNING_ROD), s(CHISELED_BOOKSHELF),
                    s(CAKE, "Cake, wedding (1902)"), s(CAULDRON), s(HOPPER), s(DISPENSER), s(COMPARATOR)};
            case ART -> new Specimen[]{
                    s(WHITE_GLAZED_TERRACOTTA, "Tile"), s(MAGENTA_GLAZED_TERRACOTTA, "Tile"), s(LIGHT_BLUE_GLAZED_TERRACOTTA, "Tile"),
                    s(YELLOW_GLAZED_TERRACOTTA, "Tile"), s(PURPLE_GLAZED_TERRACOTTA, "Tile"), s(DECORATED_POT, "Vase"),
                    s(CHISELED_QUARTZ_BLOCK, "Bust, unfinished"), s(CARVED_PUMPKIN, "Carving"), s(FLOWER_POT, "Vessel")};
            case ZOOLOGY -> new Specimen[]{
                    s(TURTLE_EGG, "Eggs, Turtle"), s(SNIFFER_EGG, "Egg, Sniffer", R), s(HONEYCOMB_BLOCK, "Honeycomb"),
                    s(BEE_NEST, "Nest, Bee"), s(BEEHIVE, "Hive, Bee"), s(SLIME_BLOCK, "Slime"), s(HONEY_BLOCK, "Honey"),
                    s(COBWEB, "Web, Spider"), s(WHITE_WOOL, "Fleece, Sheep"), s(BROWN_WOOL, "Fleece, Sheep, brown"),
                    s(BLACK_WOOL, "Fleece, Sheep, black"), s(PINK_WOOL, "Fleece, Sheep, pink", R), s(HAY_BLOCK, "Fodder"),
                    s(OCHRE_FROGLIGHT, "Froglight, ochre", U), s(VERDANT_FROGLIGHT, "Froglight, verdant", U),
                    s(PEARLESCENT_FROGLIGHT, "Froglight, pearlescent", U), s(SKELETON_SKULL, "Skull"), s(ZOMBIE_HEAD, "Head")};
            case GEOLOGY -> new Specimen[]{
                    s(GRANITE), s(DIORITE), s(ANDESITE), s(TUFF), s(CALCITE), s(DEEPSLATE), s(SMOOTH_BASALT), s(BASALT),
                    s(BLACKSTONE), s(OBSIDIAN), s(CRYING_OBSIDIAN, U), s(MAGMA_BLOCK), s(DRIPSTONE_BLOCK),
                    s(POINTED_DRIPSTONE, "Stalagmite"), s(AMETHYST_BLOCK), s(MUD), s(CLAY), s(TERRACOTTA, "Badlands clay"),
                    s(RED_SAND), s(SAND), s(GRAVEL), s(PACKED_ICE), s(BLUE_ICE, U), s(SNOW_BLOCK), s(MOSS_BLOCK), s(COBBLED_DEEPSLATE),
                    s(RAW_IRON_BLOCK, "Meteorite, iron", R)};
        };
    }

    // ── Names ───────────────────────────────────────────────────────────

    static String name(Specimen s) {
        return s.name() != null ? s.name() : pretty(s.block());
    }

    static String pretty(Material m) {
        String n = m.name().replace("POTTED_", "").replace("WAXED_", "");
        StringBuilder b = new StringBuilder();
        for (String w : n.toLowerCase(Locale.ROOT).split("_")) {
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        String out = b.toString();
        if (m.name().startsWith("POTTED_")) out += ", potted";
        return out;
    }

    private static final String[] SURNAMES = {
            "Ashby", "Pemberton", "Hollis", "Thackeray", "Wren", "Marlowe", "Fairbairn", "Haddow", "Cresswell",
            "Ingram", "Lusk", "Okafor", "Varga", "Nakamura", "Ferreira", "Lindqvist", "Ostrowski", "Abara", "Quill",
            "Penhallow", "Dunmore", "Garrow", "Moss", "Kettering", "Larch", "Trent", "Bramble", "Sowerby", "Eckhart",
            "Mbeki", "Rao", "Castellan", "Whitlock", "Fenwick", "Tolley", "Haverford", "Ng", "Iqbal", "Delacroix",
    };
    private static final String[] TITLES = {"", "", "", "", "Dr. ", "Rev. ", "Capt. ", "Prof. ", "Miss ", "Mrs. ", "Sir "};
    private static final String[] PLACES = {
            "the Badlands", "the Deep Dark", "the Lush Caves", "Dripstone Caves", "the Mushroom Fields",
            "a Warm Ocean", "the Frozen Peaks", "a Mangrove Swamp", "a Cherry Grove", "the Basalt Deltas",
            "Soul Sand Valley", "a Crimson Forest", "the End Highlands", "a Stony Shore", "a Savanna Plateau",
            "Old Growth Taiga", "the Jagged Peaks", "the Desert", "the Ice Spikes", "a Dark Forest", "a Jungle",
            "a Meadow", "a Swamp", "a Birch Forest", "the Warped Forest", "an Ocean Monument", "a Trail Ruin",
            "a Desert Temple", "an Ancient City", "a Woodland Mansion", "a Bastion", "a Nether Fortress",
            "an End City", "a Mineshaft", "a Stronghold", "a Pillager Outpost",
    };
    private static final String[] GENUS_A = {"Mega", "Ptero", "Crypto", "Litho", "Ferro", "Cubo", "Brachio", "Tyranno",
            "Myco", "Osteo", "Quadro", "Ortho", "Pseudo", "Para", "Hypo", "Proto", "Cerato", "Dino", "Iguano", "Plesio"};
    private static final String[] GENUS_B = {"saurus", "therium", "don", "cetus", "ceras", "odon", "ops", "nyx",
            "gnathus", "raptor", "mimus", "lophus", "pteryx", "suchus", "ichthys", "cephalus"};
    private static final String[] SPECIES = {"vulgaris", "minor", "major", "occidentalis", "quadratus",
            "borealis", "cubicus", "profundus", "giganteus", "nocturnus", "hollisi", "ashbyi", "fenwicki",
            "australis", "obscurus", "lentus", "ferox", "elegans", "simplex", "insularis"};

    static String collector(Random r) {
        char i = (char) ('A' + r.nextInt(26));
        String t = TITLES[r.nextInt(TITLES.length)];
        String sn = SURNAMES[r.nextInt(SURNAMES.length)];
        return t.isEmpty() || t.equals("Dr. ") || t.equals("Prof. ") ? t + i + ". " + sn : t + sn;
    }

    static String place(Random r) {
        return PLACES[r.nextInt(PLACES.length)];
    }

    static String latin(Random r) {
        return GENUS_A[r.nextInt(GENUS_A.length)] + GENUS_B[r.nextInt(GENUS_B.length)] + " " + SPECIES[r.nextInt(SPECIES.length)];
    }

    static int year(Random r) {
        return 1824 + r.nextInt(150);
    }

    static String regNo(Random r) {
        return switch (r.nextInt(4)) {
            case 0 -> year(r) + "." + (1 + r.nextInt(12)) + "." + (1 + r.nextInt(400));
            case 1 -> "NH " + (100 + r.nextInt(9900));
            case 2 -> (char) ('A' + r.nextInt(8)) + "." + (1000 + r.nextInt(9000));
            default -> "M" + (10000 + r.nextInt(90000));
        };
    }

    static int gallery(long hash) {
        return 1 + (int) Math.floorMod(hash, 240L);
    }

    // ── Signs ───────────────────────────────────────────────────────────

    private static final int LINE = 15;

    /** Four lines of a label for {@code s}. */
    static String[] sign(MuseumCanvas.Subject s, Random r) {
        String name = s.name();
        String rarity = s.rarity().label;
        return switch (s.topic()) {
            case ROOM -> {
                String title = s.theme().titles[r.nextInt(s.theme().titles.length)];
                String[] t = wrap(title);
                yield r.nextInt(4) == 0
                        ? new String[]{"Gallery " + s.gallery(), "", t[0], t[1]}
                        : new String[]{"Gallery " + s.gallery(), t[0], t[1], ""};
            }
            case GUIDE -> new String[]{"", "Please do not", "touch the", "exhibits"};
            case SKELETON -> switch (r.nextInt(5)) {
                case 0 -> lines(name, "(cast)", "Original in", "the reserve");
                case 1 -> lines(name, short15(latin(r)), "Mounted " + year(r), "");
                case 2 -> lines(name, "From " + short15(place(r)), "Coll. " + year(r), short15(collector(r)));
                case 3 -> lines(name, "", "Do not climb", "on the bones");
                default -> lines(name, "Reg. " + regNo(r), "Purchased", String.valueOf(year(r)));
            };
            case MONOLITH -> switch (r.nextInt(4)) {
                case 0 -> lines(name, "From " + short15(place(r)), "Removed " + year(r), "");
                case 1 -> lines(name, "Inscription", "not yet", "translated");
                case 2 -> lines(name, "Cast, plaster", "Original " + (r.nextBoolean() ? "lost" : "in situ"), "");
                default -> lines(name, "Reg. " + regNo(r), "Gift of", short15(collector(r)));
            };
            case POTS -> lines(name, "From " + short15(place(r)), "Excavated", String.valueOf(year(r)));
            case PAINTING -> switch (r.nextInt(4)) {
                case 0 -> lines(paintingTitle(r), "Oil on canvas", String.valueOf(year(r)), "");
                case 1 -> lines(paintingTitle(r), "Attributed to", short15(collector(r)), "");
                case 2 -> lines(paintingTitle(r), "", "Bequeathed " + year(r), "");
                default -> lines("Untitled", "Artist unknown", "", "");
            };
            case MURAL -> lines(paintingTitle(r), "Block on wall", "Commissioned", String.valueOf(year(r)));
            case TANK -> lines(name, "Living", "collection", "Do not tap glass");
            default -> switch (r.nextInt(9)) {
                case 0, 1 -> lines(name, "Collected " + year(r), "by " + short15(collector(r)), rarity);
                case 2 -> lines(name, "From " + short15(place(r)), String.valueOf(year(r)), "");
                case 3 -> lines(name, "Reg. " + regNo(r), "Gift of", short15(collector(r)));
                case 4 -> lines(name, rarity, "", "");
                case 5 -> lines("", name, "", "");
                case 6 -> lines(name, "On loan from", short15(collector(r)), "");
                case 7 -> lines(name, short15(place(r)), rarity, "Reg. " + regNo(r));
                default -> lines(name, "Coll. " + short15(collector(r)), String.valueOf(year(r)), "");
            };
        };
    }

    /** The name over up to two lines, then the rest, cut to four. */
    private static String[] lines(String name, String... rest) {
        List<String> out = new ArrayList<>();
        String[] n = wrap(name);
        out.add(n[0]);
        if (!n[1].isEmpty()) out.add(n[1]);
        for (String x : rest) out.add(x);
        while (out.size() > 4 && out.contains("")) out.remove("");
        String[] a = new String[4];
        for (int i = 0; i < 4; i++) a[i] = i < out.size() ? short15(out.get(i)) : "";
        return a;
    }

    private static String[] wrap(String s) {
        if (s.length() <= LINE) return new String[]{s, ""};
        int cut = s.lastIndexOf(' ', LINE);
        if (cut <= 0) return new String[]{s.substring(0, LINE), short15(s.substring(LINE))};
        return new String[]{s.substring(0, cut).replaceAll(",$", ""), short15(s.substring(cut + 1))};
    }

    private static String short15(String s) {
        return s.length() <= LINE ? s : s.substring(0, LINE - 1) + ".";
    }

    private static final String[] PAINT_A = {"View of", "Study of", "Evening,", "Morning,", "Storm over",
            "Portrait of", "Still Life with", "Sketch of", "Memory of", "Winter,"};
    private static final String[] PAINT_B = {"a Village", "the Peaks", "a Mooshroom", "Two Pigs", "the Sea",
            "a Lady", "Melons", "a Birch Wood", "the Fortress", "a Shipwreck", "the Builder", "Sheep", "a Lantern"};

    static String paintingTitle(Random r) {
        return PAINT_A[r.nextInt(PAINT_A.length)] + " " + PAINT_B[r.nextInt(PAINT_B.length)];
    }

    // ── Books ───────────────────────────────────────────────────────────

    record Book(String title, String author, List<String> pages) {}

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};

    /** A book for a lectern in a gallery of {@code s.theme()}. */
    static Book book(MuseumCanvas.Subject s, Random r) {
        Theme t = s.theme();
        String subject = t.titles[r.nextInt(t.titles.length)];
        List<String> pages = new ArrayList<>();
        switch (r.nextInt(6)) {
            case 0, 1 -> {
                // a catalogue: numbered entries
                int no = 1 + r.nextInt(3000);
                int n = 3 + r.nextInt(5);
                for (int p = 0; p < n; p++) {
                    StringBuilder b = new StringBuilder();
                    for (int k = 0; k < 3; k++) {
                        Specimen sp = pool(t)[r.nextInt(pool(t).length)];
                        b.append("No. ").append(no++).append(". ").append(name(sp)).append(". ");
                        b.append(switch (r.nextInt(4)) {
                            case 0 -> "From " + place(r) + ".";
                            case 1 -> "Presented by " + collector(r) + ", " + year(r) + ".";
                            case 2 -> "Purchased " + year(r) + ". " + sp.rarity().label + ".";
                            default -> "Good condition.";
                        });
                        b.append("\n\n");
                    }
                    pages.add(b.toString().trim());
                }
                return new Book("Catalogue of " + cut(subject, 18) + ", Vol. " + ROMAN[r.nextInt(ROMAN.length)],
                        collector(r), pages);
            }
            case 2 -> {
                // a gallery guide
                pages.add("Gallery " + s.gallery() + " contains part of the collection of " + subject.toLowerCase(Locale.ROOT)
                        + ". The cases are arranged by place of origin, then by date of collection.");
                pages.add("The specimens in the wall cases were collected between " + (1830 + r.nextInt(40)) + " and "
                        + (1890 + r.nextInt(60)) + ", chiefly from " + place(r) + " and " + place(r) + ".");
                pages.add("Visitors are asked not to lean on the cases. Further specimens may be seen by appointment"
                        + " with the Keeper.");
                if (r.nextBoolean()) pages.add("The gallery is closed on Mondays and on the first Tuesday of each month"
                        + " for cleaning.");
                return new Book("Guide to Gallery " + s.gallery(), "The Trustees", pages);
            }
            case 3 -> {
                // an annual report
                int y = year(r);
                pages.add("Report of the Trustees for the year " + y + ".\n\nThe number of visitors was "
                        + (40000 + r.nextInt(600000)) + ", an " + (r.nextBoolean() ? "increase" : "decrease")
                        + " on the previous year.");
                pages.add((50 + r.nextInt(4000)) + " specimens were added to the collections, chiefly of "
                        + subject.toLowerCase(Locale.ROOT) + ". The largest donation was from " + collector(r) + ".");
                pages.add("The roof over Gallery " + gallery(r.nextLong()) + " was repaired. The heating in the Central"
                        + " Hall was improved. Two new cases were made for Gallery " + s.gallery() + ".");
                pages.add("The Trustees record their thanks to the staff of the department, and to "
                        + collector(r) + ", who retired after " + (10 + r.nextInt(35)) + " years of service.");
                return new Book("Annual Report, " + y, "The Trustees", pages);
            }
            case 4 -> {
                // notes on the arrangement
                pages.add("Notes on the Arrangement of " + subject + ".\n\nThe collection was rearranged in "
                        + year(r) + ". Specimens are now shown by kind rather than by donor.");
                pages.add("Labels in black are original. Labels in red were added when a specimen was identified"
                        + " again. Some labels have not yet been replaced.");
                pages.add("Specimens marked Reserve are stored in the basement and may be seen by students on"
                        + " written application.");
                return new Book("Notes on " + cut(subject, 22), collector(r), pages);
            }
            default -> {
                // a visitors' book
                String[] remarks = {"Very interesting.", "Lovely skeleton.", "Came for the minerals.",
                        "Excellent, as always.", "The children enjoyed the eggs.", "Could not find the cafe.",
                        "Most instructive.", "Will come again.", "Too many stairs.", "Good labels.",
                        "The big one is my favourite.", "Saw everything.", "Thank you."};
                int n = 2 + r.nextInt(4);
                for (int p = 0; p < n; p++) {
                    StringBuilder b = new StringBuilder();
                    for (int k = 0; k < 4; k++) {
                        b.append(remarks[r.nextInt(remarks.length)]).append(" - ").append(collector(r)).append("\n\n");
                    }
                    pages.add(b.toString().trim());
                }
                return new Book("Visitors' Book", "Gallery " + s.gallery(), pages);
            }
        }
    }

    private static String cut(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static final Material[] SHERDS = {
            ANGLER_POTTERY_SHERD, ARCHER_POTTERY_SHERD, ARMS_UP_POTTERY_SHERD, BLADE_POTTERY_SHERD, BREWER_POTTERY_SHERD,
            BURN_POTTERY_SHERD, DANGER_POTTERY_SHERD, EXPLORER_POTTERY_SHERD, FRIEND_POTTERY_SHERD, HEART_POTTERY_SHERD,
            HEARTBREAK_POTTERY_SHERD, HOWL_POTTERY_SHERD, MINER_POTTERY_SHERD, MOURNER_POTTERY_SHERD, PLENTY_POTTERY_SHERD,
            PRIZE_POTTERY_SHERD, SHEAF_POTTERY_SHERD, SHELTER_POTTERY_SHERD, SKULL_POTTERY_SHERD, SNORT_POTTERY_SHERD,
            FLOW_POTTERY_SHERD, GUSTER_POTTERY_SHERD, SCRAPE_POTTERY_SHERD,
    };

    /** Four sherds (or bricks) for a pot. */
    static Material[] sherds(Random r) {
        Material[] out = new Material[4];
        for (int i = 0; i < 4; i++) out[i] = r.nextInt(4) == 0 ? BRICK : SHERDS[r.nextInt(SHERDS.length)];
        return out;
    }
}
