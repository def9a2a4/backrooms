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
    public enum Topic {
        SPECIMEN, SKELETON, MONOLITH, POTS, PAINTING, ROOM, GUIDE, MURAL, TANK,
        LIBRARY,    // a scholarly book in a library
        SHELFMARK,  // the label at the end of a bookcase
        LOG,        // an internal record, in staff-only rooms
        STAFF,      // the sign on a staff-only door
        CRATE,      // a label on something in a storeroom
        CHEST       // a storeroom chest's contents
    }

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
            boolean small = b.length() > 0 && (w.equals("of") || w.equals("the") || w.equals("and") || w.equals("on"));
            b.append(small ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
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

    /** A year from 9999 BCE to 9999 CE (there's no year 0). */
    static int year(Random r) {
        int y = r.nextInt(19999) - 9999;
        return y == 0 ? 1 : y;
    }

    static String yr(int y) {
        return y < 0 ? -y + " BCE" : y < 1000 ? y + " CE" : String.valueOf(y);
    }

    static String yr(Random r) {
        return yr(year(r));
    }

    /** Two years, in order, except one time in ten. */
    static int[] span(Random r) {
        int a = year(r), b = year(r);
        if (a > b && r.nextInt(10) != 0) {
            int t = a;
            a = b;
            b = t;
        }
        return new int[]{a, b};
    }

    static String regNo(Random r) {
        return switch (r.nextInt(4)) {
            case 0 -> (1800 + r.nextInt(220)) + "." + (1 + r.nextInt(12)) + "." + (1 + r.nextInt(400));
            case 1 -> "NH " + (100 + r.nextInt(9900));
            case 2 -> (char) ('A' + r.nextInt(8)) + "." + (1000 + r.nextInt(9000));
            default -> "M" + (10000 + r.nextInt(90000));
        };
    }

    static int gallery(long hash) {
        return 1 + (int) Math.floorMod(hash, 240L);
    }

    // ── Signs ───────────────────────────────────────────────────────────

    private static final int LINE = 16;

    /** Four lines of a label for {@code s}. */
    static String[] sign(MuseumCanvas.Subject s, Random r) {
        String name = s.name();
        String rarity = s.rarity().label;
        return switch (s.topic()) {
            case ROOM -> {
                String title = !name.isEmpty() ? name : s.theme().titles[r.nextInt(s.theme().titles.length)];
                String[] t = wrap(title);
                yield r.nextInt(4) == 0
                        ? new String[]{"Gallery " + s.gallery(), "", t[0], t[1]}
                        : new String[]{"Gallery " + s.gallery(), t[0], t[1], ""};
            }
            case GUIDE -> new String[]{"", "Please do not", "touch the", "exhibits"};
            case SHELFMARK -> {
                String title = !name.isEmpty() ? name : s.theme().titles[r.nextInt(s.theme().titles.length)];
                String cls = "" + (char) ('A' + r.nextInt(26)) + (char) ('A' + r.nextInt(26));
                int n0 = 1 + r.nextInt(900), n1 = n0 + 1 + r.nextInt(400);
                yield switch (r.nextInt(6)) {
                    case 0 -> lines(cls + " " + n0, "to " + cls + " " + n1, "", title.length() <= LINE ? title : "");
                    case 1 -> lines(title, cls + " " + n0 + "-" + n1, "", "");
                    case 2 -> new String[]{cls + " " + n0 + "-" + n1, "Folios", "Ask at the desk", ""};
                    case 3 -> lines(title, "Reference only", "", cls + " " + n0 + "-" + n1);
                    case 4 -> new String[]{"Shelves " + (1 + r.nextInt(99)), "", "Do not reshelve", ""};
                    default -> lines(title, "Periodicals", yr(r) + "-", "");
                };
            }
            case STAFF -> switch (r.nextInt(5)) {
                case 0 -> new String[]{"", "STAFF ONLY", "", ""};
                case 1 -> new String[]{"Store " + (1 + r.nextInt(60)), "", "Staff only", ""};
                case 2 -> new String[]{"PRIVATE", "", "No admittance", ""};
                case 3 -> new String[]{"Store Room", String.valueOf(1 + r.nextInt(60)), "Keep this door", "closed"};
                default -> new String[]{"Department of", short15(s.theme().titles[0]), "Staff only", ""};
            };
            case CRATE -> switch (r.nextInt(7)) {
                case 0 -> new String[]{"Crate " + (1 + r.nextInt(400)), "Unsorted", "", ""};
                case 1 -> lines(short15(place(r)), yr(r), "To be", "catalogued");
                case 2 -> new String[]{"Duplicates", "", "Return to", "Gallery " + (1 + r.nextInt(240))};
                case 3 -> new String[]{"DO NOT", "OPEN", "", ""};
                case 4 -> lines(name.isEmpty() ? "Misc." : name, "Box " + (1 + r.nextInt(90)) + " of " + (90 + r.nextInt(30)), "", "");
                case 5 -> new String[]{"Spare labels", "", "", ""};
                default -> new String[]{"Recd. " + yr(r), short15("from " + collector(r)), "", ""};
            };
            case SKELETON -> switch (r.nextInt(5)) {
                case 0 -> lines(name, "(cast)", "Original in", "the reserve");
                case 1 -> lines(name, short15(latin(r)), "Mounted " + yr(r), "");
                case 2 -> lines(name, "From " + short15(place(r)), "Coll. " + yr(r), short15(collector(r)));
                case 3 -> lines(name, "", "Do not climb", "on the bones");
                default -> lines(name, "Reg. " + regNo(r), "Purchased", yr(r));
            };
            case MONOLITH -> switch (r.nextInt(4)) {
                case 0 -> lines(name, "From " + short15(place(r)), "Removed " + yr(r), "");
                case 1 -> lines(name, "Inscription", "not yet", "translated");
                case 2 -> lines(name, "Cast, plaster", "Original " + (r.nextBoolean() ? "lost" : "in situ"), "");
                default -> lines(name, "Gift of", short15(collector(r)), "Reg. " + regNo(r));
            };
            case POTS -> switch (r.nextInt(5)) {
                case 0 -> lines(name, "From " + short15(place(r)), "Excavated", yr(r));
                case 1 -> lines(name, "Restored from", (8 + r.nextInt(300)) + " fragments", "");
                case 2 -> lines(name, "Capacity", (2 + r.nextInt(400)) + " litres", "Reg. " + regNo(r));
                case 3 -> lines(name, "Kiln unknown", "Made " + yr(r), "");
                default -> lines(name, "Lent by", short15(collector(r)), "");
            };
            case PAINTING -> switch (r.nextInt(4)) {
                case 0 -> lines(paintingTitle(r), "Oil on canvas", yr(r), "");
                case 1 -> lines(paintingTitle(r), "Attributed to", short15(collector(r)), "");
                case 2 -> lines(paintingTitle(r), "", "Bequeathed " + yr(r), "");
                default -> lines("Untitled", "Artist unknown", "", "");
            };
            case MURAL -> lines(paintingTitle(r), "Block on wall", "Commissioned", yr(r));
            case TANK -> lines(name, "Living", "collection", "Do not tap glass");
            default -> switch (r.nextInt(9)) {
                case 0, 1 -> lines(name, "Collected " + yr(r), "by " + short15(collector(r)), rarity);
                case 2 -> lines(name, "From " + short15(place(r)), yr(r), "");
                case 3 -> lines(name, "Gift of", short15(collector(r)), "Reg. " + regNo(r));
                case 4 -> lines(name, rarity, "", "");
                case 5 -> lines("", name, "", "");
                case 6 -> lines(name, "On loan from", short15(collector(r)), "");
                case 7 -> lines(name, short15(place(r)), rarity, "Reg. " + regNo(r));
                default -> lines(name, "Coll. " + short15(collector(r)), yr(r), "");
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

    /**
     * A sign line that fits: drop a leading "From", "by" or "Coll.", then a collector's title,
     * then whole words from the end.
     */
    private static String short15(String s) {
        for (String pre : new String[]{"From ", "by ", "Coll. ", "Reg. ", "Collected ", "Mounted ", "Removed ",
                "Bequeathed ", "Purchased "}) {
            if (s.length() > LINE && s.startsWith(pre)) s = s.substring(pre.length());
        }
        for (String t : TITLES) {
            if (s.length() > LINE && !t.isEmpty() && s.startsWith(t)) s = s.substring(t.length());
        }
        for (String art : new String[]{"the ", "a ", "an "}) {
            if (s.length() > LINE && s.startsWith(art)) s = s.substring(art.length());
        }
        while (s.length() > LINE && s.lastIndexOf(' ') > 0) s = s.substring(0, s.lastIndexOf(' '));
        return s.length() > LINE ? s.substring(0, LINE) : s;
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

    /**
     * A book for a lectern: in a gallery a catalogue, a guide or the visitors' book; in a library a
     * learned treatise; in a staff room an internal record.
     */
    static Book book(MuseumCanvas.Subject s, Random r) {
        if (s.topic() == Topic.LIBRARY) return treatise(s, r);
        if (s.topic() == Topic.LOG || s.topic() == Topic.CHEST) return record(s, r);
        Theme t = s.theme();
        String subject = t.titles[r.nextInt(t.titles.length)];
        List<String> pages = new ArrayList<>();
        switch (r.nextInt(4)) {
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
                            case 1 -> "Presented by " + collector(r) + ", " + yr(r) + ".";
                            case 2 -> "Purchased " + yr(r) + ". " + sp.rarity().label + ".";
                            default -> "Good condition.";
                        });
                        b.append("\n\n");
                    }
                    pages.add(b.toString().trim());
                }
                String vol = ", Vol. " + ROMAN[r.nextInt(ROMAN.length)];
                String title = "Catalogue of " + subject;
                if (title.length() + vol.length() > 32) title = subject;
                if (title.length() + vol.length() > 32) title = "Catalogue";
                return new Book(title + vol, collector(r), pages);
            }
            case 2 -> {
                // a gallery guide
                pages.add("Gallery " + s.gallery() + " holds part of the collections of " + t.titles[0]
                        + " (" + subject + "). The cases are arranged by place of origin, then by date of collection.");
                int[] sp = span(r);
                pages.add("The specimens in the wall cases were collected between " + yr(sp[0]) + " and "
                        + yr(sp[1]) + ", chiefly from " + place(r) + " and " + place(r) + ".");
                pages.add("Visitors are asked not to lean on the cases. Further specimens may be seen by appointment"
                        + " with the Keeper.");
                if (r.nextBoolean()) pages.add("The gallery is closed on Mondays and on the first Tuesday of each month"
                        + " for cleaning.");
                return new Book("Guide to Gallery " + s.gallery(), "The Trustees", pages);
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

    /** An internal record, for staff-only rooms. */
    static Book record(MuseumCanvas.Subject s, Random r) {
        Theme t = s.theme();
        String subject = t.titles[r.nextInt(t.titles.length)];
        List<String> pages = new ArrayList<>();
        switch (r.nextInt(6)) {
            case 0 -> {
                // an annual report
                int y = year(r);
                pages.add("Report of the Trustees for the year " + yr(y) + ".\n\nThe number of visitors was "
                        + (40000 + r.nextInt(600000)) + ", an " + (r.nextBoolean() ? "increase" : "decrease")
                        + " on the previous year.");
                pages.add((50 + r.nextInt(4000)) + " specimens were added to the collections, chiefly of "
                        + subject.toLowerCase(Locale.ROOT) + ". The largest donation was from " + collector(r) + ".");
                pages.add("The roof over Gallery " + gallery(r.nextLong()) + " was repaired. The heating in the Central"
                        + " Hall was improved. Two new cases were made for Gallery " + s.gallery() + ".");
                pages.add("The Trustees record their thanks to the staff of the department, and to "
                        + collector(r) + ", who retired after " + (10 + r.nextInt(35)) + " years of service.");
                return new Book("Annual Report, " + yr(y), "The Trustees", pages);
            }
            case 1 -> {
                // notes on the arrangement
                String[] order = {"by kind rather than by donor", "by place of collection", "in order of acquisition",
                        "by size, the largest at the back", "by colour, at the request of the Keeper"};
                pages.add("Notes on the Arrangement of " + subject + ".\n\nThe collection was rearranged in "
                        + yr(r) + ". Specimens are now shown " + order[r.nextInt(order.length)] + ".");
                String[] notes = {
                        "Labels in black are original. Labels in red were added when a specimen was identified again.",
                        "Some labels have not yet been replaced. Where two labels disagree, the newer is to be preferred.",
                        "Specimens marked Reserve are stored in the basement and may be seen by students on written application.",
                        "Duplicates have been exchanged with other museums. A list is kept in the Keeper's office.",
                        "The cases were relined in green baize in " + yr(r) + ". The old lining is in store.",
                        "Measurements are given in blocks. Weights were not recorded before " + yr(r) + ".",
                        "Casts are marked with a white spot. Original material is unmarked."};
                for (int k = 0, n = 2 + r.nextInt(3); k < n; k++) pages.add(notes[r.nextInt(notes.length)]);
                return new Book("Notes on " + cut(subject, 22), collector(r), pages);
            }
            case 2 -> {
                // the accessions register
                int[] sp = span(r);
                int no = 1 + r.nextInt(9000);
                for (int p = 0, n = 3 + r.nextInt(5); p < n; p++) {
                    StringBuilder b = new StringBuilder();
                    for (int k = 0; k < 3; k++) {
                        Specimen x = pool(t)[r.nextInt(pool(t).length)];
                        b.append(no++).append(". ").append(name(x)).append(", ").append(1 + r.nextInt(12)).append(". ")
                                .append(r.nextInt(3) == 0 ? "Recd. from " + collector(r) : "From " + place(r)).append(".\n\n");
                    }
                    pages.add(b.toString().trim());
                }
                return new Book("Register " + yr(sp[0]) + "-" + yr(sp[1]), "Accessions", pages);
            }
            case 3 -> {
                // a day book
                String[] days = {"Mon.", "Tues.", "Wed.", "Thurs.", "Fri.", "Sat."};
                String[] entries = {
                        "Lights in Gallery %g left on overnight again.", "Received %n crates from %p. Unpacked two.",
                        "Ladder in the store needs repair. Reported.", "%c called about the loan. Not in.",
                        "Requisition: %n labels, two pots of glue, string.", "Case in Gallery %g relined.",
                        "Cleaned the skeleton in the Central Hall. Took all day.", "Ammonite returned from conservation.",
                        "Inventory of Store %g begun. Not finished.", "Rain. Bucket under the skylight in Gallery %g.",
                        "%n visitors. Quiet.", "Moved the %s to the reserve.", "Crate %n opened. Contents as listed.",
                        "Keeper away. %c in charge.", "Found the key to Store %g in a drawer."};
                for (int p = 0, n = 3 + r.nextInt(5); p < n; p++) {
                    StringBuilder b = new StringBuilder();
                    for (int k = 0; k < 3; k++) {
                        Specimen x = pool(t)[r.nextInt(pool(t).length)];
                        String e = entries[r.nextInt(entries.length)].replace("%g", String.valueOf(1 + r.nextInt(240)))
                                .replace("%n", String.valueOf(2 + r.nextInt(40))).replace("%p", place(r))
                                .replace("%c", collector(r)).replace("%s", name(x).toLowerCase(Locale.ROOT));
                        b.append(days[r.nextInt(days.length)]).append(' ').append(e).append("\n\n");
                    }
                    pages.add(b.toString().trim());
                }
                return new Book("Day Book, " + yr(r), collector(r), pages);
            }
            case 4 -> {
                // a night porter's log
                for (int p = 0, n = 3 + r.nextInt(4); p < n; p++) {
                    StringBuilder b = new StringBuilder();
                    for (int k = 0; k < 4; k++) {
                        int h = 22 + r.nextInt(8);
                        b.append(h % 24 < 10 ? "0" : "").append(h % 24).append(r.nextBoolean() ? ":00" : ":30").append(' ')
                                .append(switch (r.nextInt(6)) {
                                    case 0 -> "Rounds. All in order.";
                                    case 1 -> "Gallery " + (1 + r.nextInt(240)) + " locked.";
                                    case 2 -> "Central Hall. All in order.";
                                    case 3 -> "Checked the stores.";
                                    case 4 -> "Tea.";
                                    default -> "Window in Gallery " + (1 + r.nextInt(240)) + " shut.";
                                }).append('\n');
                    }
                    pages.add(b.toString().trim());
                }
                return new Book("Night Porter's Log", collector(r), pages);
            }
            default -> {
                // memoranda
                String[] memos = {
                        "Staff are reminded that specimens are not to be taken home for study.",
                        "The tea urn in the basement is out of order.",
                        "From Monday the stores will be locked at five.",
                        "All labels are to be typed. Handwritten labels will be replaced.",
                        "Please return the long ladder to Store %g after use.",
                        "Overtime for the move of the %s will be paid at the usual rate.",
                        "The Keeper asks that crates not be left in the corridors.",
                        "Visitors are not to be admitted to the stores without an escort."};
                for (int p = 0, n = 2 + r.nextInt(4); p < n; p++) {
                    Specimen x = pool(t)[r.nextInt(pool(t).length)];
                    pages.add("MEMORANDUM\n\nTo all staff, " + subject + ".\n\n" + memos[r.nextInt(memos.length)]
                            .replace("%g", String.valueOf(1 + r.nextInt(60))).replace("%s", name(x).toLowerCase(Locale.ROOT))
                            + "\n\n" + collector(r));
                }
                return new Book("Memoranda", "The Keeper", pages);
            }
        }
    }

    /** A learned book, for a library. */
    static Book treatise(MuseumCanvas.Subject s, Random r) {
        Theme t = s.theme();
        String subject = t.titles[r.nextInt(t.titles.length)];
        String[] forms = {"A Treatise on ", "Observations on ", "On the Classification of ", "Remarks upon ",
                "A Handbook of ", "Studies in ", "An Introduction to ", "Proceedings: "};
        String title = forms[r.nextInt(forms.length)] + subject;
        if (title.length() > 32) title = subject;
        List<String> pages = new ArrayList<>();
        Specimen a = pool(t)[r.nextInt(pool(t).length)], b = pool(t)[r.nextInt(pool(t).length)];
        String[] prose = {
                "It has long been held that the " + name(a).toLowerCase(Locale.ROOT) + " and the " + name(b).toLowerCase(Locale.ROOT)
                        + " are of one kind. The present author cannot agree.",
                "Specimens from " + place(r) + " differ from those of " + place(r) + " chiefly in colour.",
                "The earliest account is that of " + collector(r) + ", written in " + yr(r) + ", which is in most respects sound.",
                "No complete example is known. The best is in the collection of this Museum (Reg. " + regNo(r) + ").",
                "The question of age must be left open. The evidence of the strata points to " + yr(r) + ".",
                "For the figures the author is indebted to " + collector(r) + ".",
                "The reader is referred to the plates at the end of the volume.",
                "Of the " + (3 + r.nextInt(40)) + " specimens examined, all but " + (1 + r.nextInt(3)) + " agreed in form."};
        pages.add(title + ".\n\nBy " + collector(r) + ".\n\n" + (r.nextBoolean() ? "Second edition, " + yr(r) + "." : "Printed for the Trustees."));
        for (int p = 0, n = 3 + r.nextInt(4); p < n; p++) pages.add(prose[r.nextInt(prose.length)]);
        return new Book(title, collector(r), pages);
    }

    /** Odds and ends for a storeroom chest. */
    static Material[] junk(Random r) {
        Material[] common = {PAPER, PAPER, BOOK, BONE, BONE_MEAL, STRING, GLASS_BOTTLE, FLOWER_POT, CANDLE, NAME_TAG,
                INK_SAC, FEATHER, BRUSH, COMPASS, CLOCK, MAP, LEAD, BUCKET, SHEARS, BOWL, STICK, CLAY_BALL, FLINT,
                GOLD_NUGGET, IRON_NUGGET, BRICK, AMETHYST_SHARD, PRISMARINE_SHARD, SPYGLASS, WRITABLE_BOOK};
        Theme t = Theme.values()[r.nextInt(Theme.values().length)];
        Material[] out = new Material[3 + r.nextInt(8)];
        for (int i = 0; i < out.length; i++) {
            out[i] = r.nextInt(3) == 0 ? pool(t)[r.nextInt(pool(t).length)].block() : common[r.nextInt(common.length)];
        }
        return out;
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
