package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumCanvas.Subject;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumCatalogue.Rarity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumCatalogue.Specimen;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumCatalogue.Theme;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumCatalogue.Topic;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumSkeletons.Kind;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Stairs;

import java.util.Random;

import static org.bukkit.Material.*;

/**
 * What stands in Level 1882's galleries: wall cases and the room's centerpiece, chosen by the
 * gallery's theme. A room is 32 blocks a side with walls on its edges; its inside runs from 1 to
 * 30 and its middle is between 15 and 16. Labels go on signs at the foot of each exhibit.
 */
final class MuseumExhibits {

    private MuseumExhibits() {}

    /** One room on one floor, as the exhibits see it. */
    record Room(MuseumCanvas c, Random r, int ox, int oz, int F, MuseumPalette pal, Theme theme, int gallery,
                byte[] e, boolean upper, long seed) {
        Subject subject(String name, Topic topic, Rarity rarity) {
            return new Subject(name, topic, rarity, gallery, theme, 0);
        }
    }

    static final BlockFace[] INTO_ROOM = {BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.NORTH};

    /** Room-local (side, depth from the wall, t along it) to world x/z. Sides: 0 W, 1 E, 2 N, 3 S. */
    static int sx(int ox, int side, int d, int t) {
        return ox + switch (side) {
            case 0 -> d;
            case 1 -> Level1882ChunkGenerator.M - 1 - d;
            default -> t;
        };
    }

    static int sz(int oz, int side, int d, int t) {
        return oz + switch (side) {
            case 2 -> d;
            case 3 -> Level1882ChunkGenerator.M - 1 - d;
            default -> t;
        };
    }

    // ── Choosing ─────────────────────────────────────────────────────────

    enum Center {
        SKELETON, HANGING, CASES, PEDESTALS, GEODE, CRYSTALS, METEORITE, PLANTERS, TREE, MUSHROOM, AQUARIUM,
        MONOLITH, POTS, PAINTINGS, AMMONITE, BENCHES
    }

    private static Center pick(Random r, Object... weighted) {
        int total = 0;
        for (int i = 1; i < weighted.length; i += 2) total += (Integer) weighted[i];
        int roll = r.nextInt(total);
        for (int i = 0; i < weighted.length; i += 2) {
            roll -= (Integer) weighted[i + 1];
            if (roll < 0) return (Center) weighted[i];
        }
        return Center.BENCHES;
    }

    static Center center(Theme t, Random r) {
        return switch (t) {
            case MINERALS -> pick(r, Center.CASES, 3, Center.PEDESTALS, 3, Center.GEODE, 2, Center.CRYSTALS, 2, Center.METEORITE, 1, Center.MONOLITH, 1);
            case FOSSILS -> pick(r, Center.SKELETON, 6, Center.HANGING, 3, Center.AMMONITE, 2, Center.CASES, 1);
            case MOBS -> pick(r, Center.SKELETON, 7, Center.HANGING, 2, Center.PEDESTALS, 2);
            case BOTANY -> pick(r, Center.PLANTERS, 3, Center.TREE, 3, Center.MUSHROOM, 2, Center.CASES, 1);
            case MARINE -> pick(r, Center.AQUARIUM, 3, Center.HANGING, 3, Center.AMMONITE, 1, Center.CASES, 1, Center.SKELETON, 1);
            case ARCHAEOLOGY -> pick(r, Center.MONOLITH, 5, Center.POTS, 5, Center.PEDESTALS, 2, Center.CASES, 1);
            case NETHER -> pick(r, Center.SKELETON, 3, Center.HANGING, 2, Center.PEDESTALS, 2, Center.METEORITE, 1, Center.CASES, 1);
            case END -> pick(r, Center.SKELETON, 1, Center.HANGING, 2, Center.PEDESTALS, 3, Center.MONOLITH, 3, Center.CRYSTALS, 2, Center.CASES, 2);
            case CURIOSITIES -> pick(r, Center.PEDESTALS, 4, Center.CASES, 3, Center.POTS, 1, Center.MONOLITH, 1, Center.BENCHES, 1);
            case ART -> pick(r, Center.PAINTINGS, 6, Center.POTS, 3, Center.MONOLITH, 2);
            case ZOOLOGY -> pick(r, Center.SKELETON, 4, Center.CASES, 2, Center.PEDESTALS, 1, Center.HANGING, 1);
            case GEOLOGY -> pick(r, Center.METEORITE, 2, Center.GEODE, 2, Center.CRYSTALS, 1, Center.MONOLITH, 2, Center.PEDESTALS, 2, Center.CASES, 1);
        };
    }

    static Kind skeleton(Theme t, Random r, boolean hanging) {
        Kind[] k = hanging ? switch (t) {
            case FOSSILS -> new Kind[]{Kind.PTEROSAUR, Kind.ICHTHYOSAUR, Kind.PLESIOSAUR};
            case MARINE -> new Kind[]{Kind.WHALE, Kind.ICHTHYOSAUR, Kind.SQUID, Kind.PLESIOSAUR};
            case NETHER -> new Kind[]{Kind.GHAST};
            case END -> new Kind[]{Kind.DRAGON, Kind.PHANTOM};
            case ZOOLOGY -> new Kind[]{Kind.BAT, Kind.PTEROSAUR, Kind.PHANTOM};
            default -> new Kind[]{Kind.GHAST, Kind.BAT, Kind.DRAGON};
        } : switch (t) {
            case FOSSILS -> new Kind[]{Kind.THEROPOD, Kind.MAMMOTH, Kind.SAUROPOD, Kind.TRILOBITE, Kind.TURTLE};
            case MARINE -> new Kind[]{Kind.TURTLE};
            case NETHER -> new Kind[]{Kind.HOGLIN, Kind.STRIDER, Kind.PIGLIN, Kind.WITHER};
            case END -> new Kind[]{Kind.ENDERMAN, Kind.WITHER, Kind.SPIDER};
            case ZOOLOGY -> new Kind[]{Kind.PIG, Kind.COW, Kind.HORSE, Kind.WOLF, Kind.CAMEL, Kind.SNIFFER, Kind.GOAT, Kind.CHICKEN};
            default -> new Kind[]{Kind.CREEPER, Kind.SKELETON, Kind.ZOMBIE, Kind.ENDERMAN, Kind.SPIDER, Kind.VILLAGER,
                    Kind.PIGLIN, Kind.WITHER, Kind.HOGLIN, Kind.SNIFFER, Kind.HORSE, Kind.CHICKEN};
        };
        return k[r.nextInt(k.length)];
    }

    /** Whether the centerpiece leaves the room's corners free for columns. */
    static boolean compact(Center c) {
        return switch (c) {
            case PEDESTALS, GEODE, CRYSTALS, METEORITE, TREE, MUSHROOM, MONOLITH, POTS, BENCHES, HANGING -> true;
            default -> false;
        };
    }

    // ── Wall cases ───────────────────────────────────────────────────────

    /**
     * Cases along a wall from t0 to t1: 0 glazed case, 1 open shelves, 2 a tall two-tier cabinet.
     * Each specimen gets a label on the case front.
     */
    static void wallCase(Room m, int side, int t0, int t1, int style) {
        MuseumCanvas c = m.c();
        BlockData glass = Material.GLASS.createBlockData();
        Specimen[] pool = MuseumCatalogue.pool(m.theme());
        int top = style == 2 ? 8 : 5;
        for (int t = t0; t <= t1; t++) {
            for (int d = 1; d <= 2; d++) {
                int x = sx(m.ox(), side, d, t), z = sz(m.oz(), side, d, t);
                c.set(x, m.F() + 1, z, m.pal().wood);
                if (style != 1) c.set(x, m.F() + top, z, m.pal().wood);
                boolean end = t == t0 || t == t1;
                for (int y = m.F() + 2; y < m.F() + top; y++) {
                    if (style == 1) {
                        if (end) c.set(x, y, z, m.pal().wood);
                    } else if (d == 2 || end) {
                        c.set(x, y, z, glass);
                    }
                }
                if (style == 2 && !end) c.set(x, m.F() + 4, z, m.pal().wood);   // middle shelf
                if (style == 1 && !end) c.set(x, m.F() + 4, z, m.pal().wood);
            }
            if (t == t0 || t == t1 || (t - t0) % 3 != 1) continue;
            for (int tier : style == 0 ? new int[]{2} : new int[]{2, 5}) {
                Specimen s = m.r().nextInt(100) < potChance(m.theme()) ? pot(m.r()) : pool[m.r().nextInt(pool.length)];
                put(c, sx(m.ox(), side, 1, t), m.F() + tier, sz(m.oz(), side, 1, t), s, INTO_ROOM[side], label(m, s));
                if (tier == 2) c.sign(sx(m.ox(), side, 3, t), m.F() + 1, sz(m.oz(), side, 3, t), INTO_ROOM[side], label(m, s));
            }
        }
    }

    static BlockData specimen(Specimen s, BlockFace facing) {
        BlockData b = s.block().createBlockData();
        // a skull's rotation is the way the back of its head points
        if (b instanceof Rotatable rot) rot.setRotation(facing.getOppositeFace());
        else if (b instanceof Directional dir && dir.getFaces().contains(facing)) dir.setFacing(facing);
        return b;
    }

    // ── Centerpieces ─────────────────────────────────────────────────────

    /** World x/z of a point {@code a} along the room's axis and {@code b} across it. */
    private static int X(Room m, boolean alongX, int a, int b) {
        return m.ox() + (alongX ? a : b);
    }

    private static int Z(Room m, boolean alongX, int a, int b) {
        return m.oz() + (alongX ? b : a);
    }

    private static BlockFace facing(boolean alongX, boolean plus) {
        return alongX ? (plus ? BlockFace.SOUTH : BlockFace.NORTH) : (plus ? BlockFace.EAST : BlockFace.WEST);
    }

    /** A raised plinth around the middle: half-length {@code al} along the axis, {@code ac} across. */
    static void dais(Room m, boolean alongX, int al, int ac) {
        for (int a = 16 - al - 1; a <= 15 + al + 1; a++) {
            for (int b = 16 - ac - 1; b <= 15 + ac + 1; b++) {
                boolean rim = a == 16 - al - 1 || a == 15 + al + 1 || b == 16 - ac - 1 || b == 15 + ac + 1;
                m.c().set(X(m, alongX, a, b), m.F() + 1, Z(m, alongX, a, b), rim ? m.pal().dais : m.pal().daisTop);
            }
        }
    }

    /** A label on the front of the dais (the +across side), facing out. */
    static void daisSign(Room m, boolean alongX, int ac, Subject s) {
        m.c().sign(X(m, alongX, 15, 16 + ac + 1), m.F() + 1, Z(m, alongX, 15, 16 + ac + 1), facing(alongX, true), s);
    }

    static void skeleton(Room m, boolean alongX, Kind kind) {
        int al = switch (kind) {
            case PTEROSAUR, BAT, PHANTOM, CREEPER, SKELETON, ZOMBIE, ENDERMAN, PIGLIN, VILLAGER, WITHER, CHICKEN, STRIDER -> 4;
            case SPIDER, TURTLE, TRILOBITE, PIG, WOLF -> 8;
            default -> 10;
        };
        int ac = switch (kind) {
            case SPIDER -> 8;
            case CREEPER, SKELETON, ZOMBIE, PIGLIN, VILLAGER, CHICKEN, STRIDER -> 4;
            case WITHER -> 6;
            default -> 5;
        };
        dais(m, alongX, al, ac);
        int ux = alongX ? 1 : 0, uz = alongX ? 0 : 1;
        // humanoids face the label side
        if (al == 4) { ux = alongX ? 0 : -1; uz = alongX ? -1 : 0; }
        MuseumSkeletons.draw(m.c(), kind, m.ox() + 16, m.F() + 2, m.oz() + 16, ux, uz, 0);
        daisSign(m, alongX, ac, m.subject(kind.label(), Topic.SKELETON, Rarity.RARE));
    }

    static void hanging(Room m, boolean alongX, Kind kind) {
        int ux = alongX ? 1 : 0, uz = alongX ? 0 : 1;
        MuseumSkeletons.draw(m.c(), kind, m.ox() + 16, 0, m.oz() + 16, ux, uz, m.F() + 11);
        // a low rail round the spot underneath, and the label on it
        for (int a = 9; a <= 22; a++) {
            for (int b : new int[]{11, 20}) {
                m.c().set(X(m, alongX, a, b), m.F() + 1, Z(m, alongX, a, b), m.pal().trim);
            }
        }
        m.c().sign(X(m, alongX, 15, 21), m.F() + 1, Z(m, alongX, 15, 21), facing(alongX, true),
                m.subject(kind.label(), Topic.SKELETON, Rarity.RARE));
    }

    static void islandCases(Room m, boolean alongX) {
        BlockData glass = Material.GLASS.createBlockData();
        Specimen[] pool = MuseumCatalogue.pool(m.theme());
        int[] across = m.r().nextBoolean() ? new int[]{11, 20} : new int[]{9, 15, 22};
        int[] along = m.r().nextBoolean() ? new int[]{8, 15, 23} : new int[]{7, 12, 19, 24};
        for (int a : along) {
            for (int b : across) {
                int cx = X(m, alongX, a, b), cz = Z(m, alongX, a, b);
                for (int x = cx - 1; x <= cx + 1; x++) {
                    for (int z = cz - 1; z <= cz + 1; z++) {
                        m.c().set(x, m.F() + 1, z, m.pal().wood);
                        boolean mid = x == cx && z == cz;
                        for (int y = m.F() + 2; y <= m.F() + 4; y++) if (!mid || y == m.F() + 4) m.c().set(x, y, z, glass);
                    }
                }
                Specimen s = m.r().nextInt(100) < potChance(m.theme()) ? pot(m.r()) : pool[m.r().nextInt(pool.length)];
                BlockFace f = facing(alongX, true);
                put(m.c(), cx, m.F() + 2, cz, s, f, label(m, s));
                m.c().sign(cx + f.getModX() * 2, m.F() + 1, cz + f.getModZ() * 2, f, label(m, s));
            }
        }
    }

    /** Two rows of carved pedestals, one thing on each. */
    static void pedestals(Room m, boolean alongX) {
        Specimen[] pool = MuseumCatalogue.pool(m.theme());
        for (int a = 9; a <= 22; a += 4) {
            for (int b : new int[]{12, 19}) {
                int x = X(m, alongX, a, b), z = Z(m, alongX, a, b);
                m.c().set(x, m.F() + 1, z, m.pal().trim);
                m.c().set(x, m.F() + 2, z, m.pal().carved);
                Specimen s = m.r().nextInt(100) < potChance(m.theme()) + 10 ? pot(m.r()) : pool[m.r().nextInt(pool.length)];
                BlockFace f = facing(alongX, b > 16);
                put(m.c(), x, m.F() + 3, z, s, f, label(m, s));
                m.c().sign(x + f.getModX(), m.F() + 1, z + f.getModZ(), f, label(m, s));
            }
        }
    }

    /** A geode cut in half: basalt, calcite, amethyst, hollow. */
    static void geode(Room m, boolean alongX) {
        dais(m, alongX, 5, 5);
        int cx = m.ox() + 16, cy = m.F() + 7, cz = m.oz() + 16;
        BlockData basalt = SMOOTH_BASALT.createBlockData(), calcite = CALCITE.createBlockData(),
                amethyst = AMETHYST_BLOCK.createBlockData(), budding = BUDDING_AMETHYST.createBlockData();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.1 + dz * dz);
                    if (d > 5.2 || d < 2.6 || (alongX ? dx : dz) > 0) continue;
                    BlockData b = d > 4.4 ? basalt : d > 3.6 ? calcite : Math.floorMod(dx * 7 + dy * 3 + dz, 5) == 0 ? budding : amethyst;
                    m.c().set(cx + dx, cy + dy, cz + dz, b);
                }
            }
        }
        daisSign(m, alongX, 5, m.subject("Geode, sectioned", Topic.SPECIMEN, Rarity.UNCOMMON));
    }

    /** Crystals bursting from a rock: amethyst, quartz or end rods. */
    static void crystals(Room m, boolean alongX) {
        dais(m, alongX, 4, 4);
        int kind = m.r().nextInt(3);
        BlockData rock = (kind == 2 ? END_STONE : kind == 1 ? SMOOTH_BASALT : CALCITE).createBlockData();
        BlockData xtal = (kind == 2 ? PURPUR_PILLAR : kind == 1 ? QUARTZ_PILLAR : AMETHYST_BLOCK).createBlockData();
        int cx = m.ox() + 16, cz = m.oz() + 16, y0 = m.F() + 2;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (dx * dx + dz * dz <= 5) m.c().set(cx + dx, y0, cz + dz, rock);
        for (int i = 0; i < 7; i++) {
            double th = m.r().nextDouble() * Math.PI * 2, tilt = 0.2 + m.r().nextDouble() * 0.6, len = 3 + m.r().nextInt(7);
            double dx = Math.cos(th) * Math.sin(tilt), dz = Math.sin(th) * Math.sin(tilt), dy = Math.cos(tilt);
            for (double t = 0; t < len; t += 0.5) {
                m.c().set((int) Math.floor(cx + 0.5 + dx * t), (int) Math.floor(y0 + 1 + dy * t), (int) Math.floor(cz + 0.5 + dz * t), xtal);
            }
            BlockData tip = (kind == 2 ? END_ROD : AMETHYST_CLUSTER).createBlockData();
            m.c().set((int) Math.floor(cx + 0.5 + dx * len), (int) Math.floor(y0 + 1 + dy * len), (int) Math.floor(cz + 0.5 + dz * len), tip);
        }
        String name = kind == 2 ? "Chorus crystal" : kind == 1 ? "Quartz, cluster" : "Amethyst, cluster";
        daisSign(m, alongX, 4, m.subject(name, Topic.SPECIMEN, Rarity.UNCOMMON));
    }

    static void meteorite(Room m, boolean alongX) {
        dais(m, alongX, 5, 5);
        long s = m.r().nextLong();
        BlockData[] mats = {BLACKSTONE.createBlockData(), BASALT.createBlockData(), GILDED_BLACKSTONE.createBlockData(),
                RAW_IRON_BLOCK.createBlockData(), MAGMA_BLOCK.createBlockData()};
        int cx = m.ox() + 16, cy = m.F() + 6, cz = m.oz() + 16;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -4; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.3 + dz * dz);
                    double n = SimplexNoise.noise3(s, dx * 0.35, dy * 0.35, dz * 0.35);
                    if (d > 3.8 + 1.4 * n) continue;
                    double k = SimplexNoise.noise3(s + 1, dx * 0.6, dy * 0.6, dz * 0.6);
                    m.c().set(cx + dx, cy + dy, cz + dz, mats[k > 0.5 ? 2 : k < -0.55 ? 3 : k > 0.35 ? 4 : (dx + dy + dz & 1)]);
                }
            }
        }
        daisSign(m, alongX, 5, m.subject(m.r().nextBoolean() ? "Meteorite" : "Volcanic bomb", Topic.SPECIMEN, Rarity.RARE));
    }

    static void planters(Room m, boolean alongX) {
        Material[] plants = {AZALEA, FLOWERING_AZALEA, FERN, SHORT_GRASS, MOSS_CARPET, ALLIUM, BLUE_ORCHID, LILY_OF_THE_VALLEY,
                CORNFLOWER, OXEYE_DAISY, POPPY, DANDELION, PINK_TULIP, SWEET_BERRY_BUSH, RED_MUSHROOM, BROWN_MUSHROOM};
        for (int b0 : new int[]{8, 19}) {
            for (int a = 5; a <= 26; a++) {
                for (int b = b0; b < b0 + 5; b++) {
                    int x = X(m, alongX, a, b), z = Z(m, alongX, a, b);
                    boolean rim = a == 5 || a == 26 || b == b0 || b == b0 + 4;
                    m.c().set(x, m.F() + 1, z, rim ? m.pal().trim : MOSS_BLOCK.createBlockData());
                    if (!rim && m.r().nextInt(3) > 0) m.c().set(x, m.F() + 2, z, plants[m.r().nextInt(plants.length)].createBlockData());
                }
            }
            for (int a = 8; a <= 23; a += 5) {
                int b = b0 == 8 ? 7 : 24;
                m.c().sign(X(m, alongX, a, b), m.F() + 1, Z(m, alongX, a, b), facing(alongX, b > 16),
                        m.subject(MuseumCatalogue.pretty(plants[m.r().nextInt(plants.length)]), Topic.SPECIMEN, Rarity.COMMON));
            }
        }
    }

    /** A specimen tree in a big planter. */
    static void tree(Room m, boolean alongX) {
        Material[][] kinds = {{OAK_LOG, AZALEA_LEAVES}, {BIRCH_LOG, BIRCH_LEAVES}, {CHERRY_LOG, CHERRY_LEAVES},
                {SPRUCE_LOG, SPRUCE_LEAVES}, {JUNGLE_LOG, JUNGLE_LEAVES}, {DARK_OAK_LOG, DARK_OAK_LEAVES},
                {MANGROVE_LOG, MANGROVE_LEAVES}, {ACACIA_LOG, ACACIA_LEAVES}};
        Material[] k = kinds[m.r().nextInt(kinds.length)];
        int cx = m.ox() + 16, cz = m.oz() + 16, y0 = m.F() + 1;
        for (int dx = -3; dx <= 2; dx++) {
            for (int dz = -3; dz <= 2; dz++) {
                boolean rim = dx == -3 || dx == 2 || dz == -3 || dz == 2;
                m.c().set(cx + dx, y0, cz + dz, rim ? m.pal().trim : ROOTED_DIRT.createBlockData());
                if (rim) m.c().set(cx + dx, y0 + 1, cz + dz, m.pal().trim);
            }
        }
        int h = 6 + m.r().nextInt(4);
        for (int y = y0 + 1; y < y0 + h; y++) m.c().set(cx, y, cz, k[0].createBlockData());
        Leaves leaf = (Leaves) k[1].createBlockData();
        leaf.setPersistent(true);
        long s = m.r().nextLong();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 2.0 + dz * dz) + 0.8 * SimplexNoise.noise3(s, dx * 0.5, dy * 0.5, dz * 0.5);
                    if (d < 4.2 && !(dx == 0 && dz == 0 && dy < 1)) m.c().set(cx + dx, y0 + h + dy - 1, cz + dz, leaf);
                }
            }
        }
        m.c().sign(cx, y0, cz + 3, BlockFace.SOUTH, m.subject(MuseumCatalogue.pretty(k[0]).replace(" Log", ", living"),
                Topic.SPECIMEN, Rarity.UNCOMMON));
    }

    static void mushroom(Room m, boolean alongX) {
        dais(m, alongX, 4, 4);
        boolean red = m.r().nextBoolean();
        int cx = m.ox() + 16, cz = m.oz() + 16, y0 = m.F() + 2, h = 5 + m.r().nextInt(3);
        for (int y = y0; y < y0 + h; y++) m.c().set(cx, y, cz, MUSHROOM_STEM.createBlockData());
        BlockData cap = (red ? RED_MUSHROOM_BLOCK : BROWN_MUSHROOM_BLOCK).createBlockData();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                int edge = Math.max(Math.abs(dx), Math.abs(dz));
                m.c().set(cx + dx, y0 + h - (red && edge == 3 ? 1 : 0), cz + dz, cap);
                if (red && edge == 3) m.c().set(cx + dx, y0 + h - 2, cz + dz, cap);
            }
        }
        daisSign(m, alongX, 4, m.subject(red ? "Mushroom, red, model" : "Mushroom, brown, model", Topic.SPECIMEN, Rarity.COMMON));
    }

    /** A glass tank of sea water with coral, kelp and sea grass. */
    static void aquarium(Room m, boolean alongX) {
        BlockData glass = Material.GLASS.createBlockData(), water = WATER.createBlockData(), sand = SAND.createBlockData();
        Material[] life = {TUBE_CORAL, BRAIN_CORAL, BUBBLE_CORAL, FIRE_CORAL, HORN_CORAL, SEAGRASS, SEA_PICKLE,
                TUBE_CORAL_FAN, HORN_CORAL_FAN, SEAGRASS};
        int a0 = 8, a1 = 23, b0 = 12, b1 = 19, y0 = m.F() + 1, y1 = m.F() + 7;
        for (int a = a0; a <= a1; a++) {
            for (int b = b0; b <= b1; b++) {
                int x = X(m, alongX, a, b), z = Z(m, alongX, a, b);
                boolean side = a == a0 || a == a1 || b == b0 || b == b1;
                m.c().set(x, y0, z, m.pal().dais);
                for (int y = y0 + 1; y <= y1; y++) m.c().set(x, y, z, side || y == y1 ? glass : y == y0 + 1 ? sand : water);
                if (side || m.r().nextInt(3) != 0) continue;
                if (m.r().nextInt(4) == 0) {
                    int h = 2 + m.r().nextInt(3);
                    for (int y = y0 + 2; y < y0 + 2 + h && y < y1 - 1; y++) m.c().set(x, y, z, KELP_PLANT.createBlockData());
                    m.c().set(x, Math.min(y0 + 2 + h, y1 - 1), z, KELP.createBlockData());
                } else {
                    m.c().set(x, y0 + 2, z, life[m.r().nextInt(life.length)].createBlockData());
                }
            }
        }
        m.c().sign(X(m, alongX, 15, b1 + 1), y0, Z(m, alongX, 15, b1 + 1), facing(alongX, true),
                m.subject("Reef, living", Topic.TANK, Rarity.UNCOMMON));
    }

    /** Room point (a along the axis, b across) at height y. */
    private static void at(Room m, boolean alongX, int a, int y, int b, BlockData d) {
        m.c().set(X(m, alongX, a, b), y, Z(m, alongX, a, b), d);
    }

    private static boolean hit(int a, int y, int b, int salt, int mod) {
        return Math.floorMod(a * 73 + y * 151 + b * 37 + salt, mod) == 0;
    }

    /**
     * A monolith: obelisk, stela, menhir, a long-faced head, a stone circle, a totem pole, a T-pillar,
     * a trilithon, a dolmen, a stone sphere, a runestone, a pyramid or ziggurat, a colossal head, a
     * colossal foot, an inscribed decree, a black monolith (mostly in the End galleries) or a fallen
     * column. Labelled at the front (+across).
     */
    static void monolith(Room m, boolean alongX) {
        int cx = m.ox() + 16, cz = m.oz() + 16, y0 = m.F() + 1;
        Random r = m.r();
        String name;
        int type = m.theme() == Theme.END && r.nextBoolean() ? 15 : r.nextInt(17);
        switch (type) {
            case 0 -> {
                Material[][] sets = {{QUARTZ_BLOCK, CHISELED_QUARTZ_BLOCK}, {SMOOTH_SANDSTONE, CHISELED_SANDSTONE},
                        {POLISHED_BLACKSTONE, CHISELED_POLISHED_BLACKSTONE}, {SMOOTH_RED_SANDSTONE, CHISELED_RED_SANDSTONE},
                        {POLISHED_GRANITE, GRANITE}, {POLISHED_DEEPSLATE, CHISELED_DEEPSLATE}};
                Material[] set = sets[r.nextInt(sets.length)];
                BlockData[] st = {set[0].createBlockData(), set[1].createBlockData()};
                int h = 12 + r.nextInt(5);
                BlockData tip = (r.nextInt(3) == 0 ? GOLD_BLOCK : set[0]).createBlockData();
                for (int y = 0; y < h; y++) {
                    int half = y < 2 ? 2 : y < h - 2 ? 1 : 0;
                    for (int dx = -half; dx <= half; dx++) {
                        for (int dz = -half; dz <= half; dz++) {
                            m.c().set(cx + dx, y0 + y, cz + dz, y == h - 1 ? tip : y % 3 == 1 && y > 2 && half == 1 ? st[1] : st[0]);
                        }
                    }
                }
                name = "Obelisk";
            }
            case 1 -> {
                BlockData stone = (r.nextBoolean() ? POLISHED_BASALT : POLISHED_BLACKSTONE).createBlockData();
                BlockData glyph = (r.nextBoolean() ? CHISELED_DEEPSLATE : CHISELED_TUFF).createBlockData();
                dais(m, alongX, 4, 1);
                for (int a = 12; a <= 19; a++) {
                    for (int y = 1; y <= 10; y++) {
                        boolean edge = a == 12 || a == 19 || y == 10;
                        boolean line = !edge && y % 2 == 0 && y > 1 && Math.floorMod(a * 7 + y * 3, 5) != 0;
                        at(m, alongX, a, y0 + y, 16, line ? glyph : stone);
                        at(m, alongX, a, y0 + y, 15, stone);
                    }
                }
                name = r.nextBoolean() ? "Stela, inscribed" : "Boundary stone";
            }
            case 2 -> {
                long s = r.nextLong();
                Material[][] sets = {{STONE, MOSSY_COBBLESTONE, ANDESITE}, {GRANITE, STONE, DIORITE},
                        {TUFF, STONE, ANDESITE}, {SANDSTONE, SMOOTH_SANDSTONE, STONE}};
                Material[] set = sets[r.nextInt(sets.length)];
                for (int y = 0; y < 13; y++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            double w = 2.2 - y * 0.08 + 0.5 * SimplexNoise.noise3(s, dx * 0.4, y * 0.2, dz * 0.4);
                            if (Math.sqrt(dx * dx + dz * dz * 2.5) < w) {
                                m.c().set(cx + dx, y0 + y, cz + dz, set[Math.floorMod(dx + y + dz, 3)].createBlockData());
                            }
                        }
                    }
                }
                name = "Menhir";
            }
            case 3 -> {
                BlockData tuff = TUFF.createBlockData(), dark = POLISHED_TUFF.createBlockData();
                dais(m, alongX, 3, 3);
                // a long-faced head: brow, nose, chin; facing +across
                for (int y = 1; y <= 12; y++) {
                    for (int a = -2; a <= 2; a++) {
                        for (int b = -2; b <= 2; b++) {
                            if (y > 10 && Math.abs(a) > 1) continue;
                            at(m, alongX, 16 + a, y0 + y, 16 + b, y == 9 && b == 2 ? dark : tuff);
                            if (b == 2 && a == 0 && y >= 5 && y <= 8) at(m, alongX, 16, y0 + y, 19, tuff);
                        }
                    }
                }
                name = "Head, carved";
            }
            case 4 -> {
                BlockData st = (r.nextBoolean() ? STONE : SMOOTH_STONE).createBlockData();
                for (int i = 0; i < 6; i++) {
                    double th = i * Math.PI / 3;
                    int px = cx + (int) Math.round(6 * Math.cos(th)), pz = cz + (int) Math.round(6 * Math.sin(th));
                    for (int y = 0; y < 6; y++) m.c().set(px, y0 + y, pz, st);
                    if (i % 2 == 0) {
                        int qx = cx + (int) Math.round(6 * Math.cos(th + Math.PI / 3)), qz = cz + (int) Math.round(6 * Math.sin(th + Math.PI / 3));
                        for (double t = 0; t <= 1; t += 0.1) {
                            m.c().set((int) Math.round(px + (qx - px) * t), y0 + 6, (int) Math.round(pz + (qz - pz) * t), st);
                        }
                    }
                }
                // the altar stone in the middle
                for (int a = 15; a <= 16; a++) for (int b = 14; b <= 17; b++) at(m, alongX, a, y0, b, st);
                name = "Stone circle, model";
            }
            case 5 -> {
                Material[] bands = {RED_TERRACOTTA, BLACK_TERRACOTTA, WHITE_TERRACOTTA, CYAN_TERRACOTTA, YELLOW_TERRACOTTA};
                for (int y = 0; y < 14; y++) {
                    BlockData b = y % 3 == 2 ? bands[Math.floorMod(y * 7 + cx, bands.length)].createBlockData() : STRIPPED_SPRUCE_LOG.createBlockData();
                    m.c().set(cx, y0 + y, cz, b);
                    m.c().set(cx - 1, y0 + y, cz, b);
                    m.c().set(cx, y0 + y, cz - 1, b);
                    m.c().set(cx - 1, y0 + y, cz - 1, b);
                }
                for (int d = -4; d <= 3; d++) at(m, alongX, 16 + d, y0 + 11, 15, SPRUCE_PLANKS.createBlockData());
                name = "Totem pole";
            }
            case 6 -> {
                // a T-shaped pillar, its sides carved with animals in relief
                Material[] set = r.nextBoolean() ? new Material[]{SMOOTH_SANDSTONE, CHISELED_SANDSTONE} : new Material[]{CALCITE, CHISELED_TUFF};
                BlockData st = set[0].createBlockData(), relief = set[1].createBlockData();
                dais(m, alongX, 3, 4);
                for (int y = 1; y <= 12; y++) {
                    int b0 = y <= 9 ? 13 : 12, b1 = y <= 9 ? 18 : 20;
                    for (int a = 15; a <= 16; a++) for (int b = b0; b <= b1; b++) at(m, alongX, a, y0 + y, b, st);
                }
                String[] beast = {"...#", "####", "####", "#..#"};   // top row first: head, back, belly, legs
                for (int row = 0; row < beast.length; row++) {
                    for (int k = 0; k < 4; k++) {
                        if (beast[row].charAt(k) != '#') continue;
                        at(m, alongX, 14, y0 + 7 - row, 14 + k, relief);
                        at(m, alongX, 17, y0 + 7 - row, 17 - k, relief);
                    }
                }
                name = "T-pillar";
            }
            case 7 -> {
                BlockData[] st = {STONE.createBlockData(), ANDESITE.createBlockData()};
                for (int a = 10; a <= 21; a++) {
                    for (int b = 15; b <= 16; b++) {
                        boolean leg = a <= 12 || a >= 19;
                        for (int y = 0; y <= 10; y++) {
                            if (y <= 8 && !leg) continue;
                            if (y <= 8 && (a == 10 || a == 21)) continue;
                            at(m, alongX, a, y0 + y, b, st[hit(a, y, b, 0, 3) ? 1 : 0]);
                        }
                    }
                }
                name = "Trilithon, cast";
            }
            case 8 -> {
                BlockData[] st = {STONE.createBlockData(), MOSSY_COBBLESTONE.createBlockData(), COBBLESTONE.createBlockData()};
                long s = r.nextLong();
                for (int y = 0; y <= 4; y++) {
                    for (int k = 13; k <= 18; k++) {
                        at(m, alongX, 12, y0 + y, k, st[hit(12, y, k, 1, 3) ? 1 : 0]);
                        at(m, alongX, 19, y0 + y, k, st[hit(19, y, k, 2, 3) ? 1 : 0]);
                        at(m, alongX, k, y0 + y, 13, st[hit(k, y, 13, 3, 4) ? 2 : 0]);
                    }
                }
                for (int a = 9; a <= 22; a++) {
                    for (int b = 10; b <= 21; b++) {
                        double da = a + 0.5 - 16, db = b + 0.5 - 16;
                        double e = da * da / 36 + db * db / 25 + 0.25 * SimplexNoise.noise2(s, a * 0.3, b * 0.3);
                        if (e < 1) at(m, alongX, a, y0 + 5, b, st[hit(a, 5, b, 4, 4) ? 1 : 0]);
                        if (e < 0.55) at(m, alongX, a, y0 + 6, b, st[0]);
                    }
                }
                name = "Dolmen";
            }
            case 9 -> {
                Material[] mats = {POLISHED_GRANITE, POLISHED_ANDESITE, POLISHED_DIORITE, SMOOTH_BASALT};
                BlockData st = mats[r.nextInt(mats.length)].createBlockData();
                dais(m, alongX, 4, 4);
                double R = 3.3 + r.nextDouble() * 0.8, cy = 1 + R;
                for (int a = 10; a <= 21; a++) {
                    for (int b = 10; b <= 21; b++) {
                        for (int y = 1; y <= 10; y++) {
                            double da = a + 0.5 - 16, db = b + 0.5 - 16, dy = y + 0.5 - cy;
                            if (da * da + db * db + dy * dy <= R * R) at(m, alongX, a, y0 + y, b, st);
                        }
                    }
                }
                name = "Stone sphere";
            }
            case 10 -> {
                BlockData stone = (r.nextBoolean() ? STONE : GRANITE).createBlockData();
                BlockData band = (r.nextBoolean() ? RED_TERRACOTTA : CHISELED_STONE_BRICKS).createBlockData();
                for (int a = 12; a <= 19; a++) {
                    int h = 9 - (int) ((a - 15.5) * (a - 15.5) / 4);
                    for (int y = 0; y < h; y++) {
                        at(m, alongX, a, y0 + y, 15, stone);
                        boolean ring = (a == 13 || a == 18 || y == h - 2) && a > 12 && a < 19 && y < h - 1 && y > 0;
                        boolean cross = (a == 15 || a == 16) && y >= 3 && y <= 6 || (y == 5 && a >= 14 && a <= 17);
                        at(m, alongX, a, y0 + y, 16, ring || cross ? band : stone);
                    }
                }
                name = "Runestone";
            }
            case 11 -> {
                if (r.nextBoolean()) {
                    BlockData st = SMOOTH_SANDSTONE.createBlockData(), line = CUT_SANDSTONE.createBlockData(), top = GOLD_BLOCK.createBlockData();
                    for (int y = 0; y <= 6; y++) {
                        for (int a = 9 + y; a <= 22 - y; a++) {
                            for (int b = 9 + y; b <= 22 - y; b++) at(m, alongX, a, y0 + y, b, y == 6 ? top : y % 2 == 1 ? line : st);
                        }
                    }
                    name = "Pyramid, model";
                } else {
                    BlockData[] st = {MUD_BRICKS.createBlockData(), PACKED_MUD.createBlockData()};
                    for (int k = 0; k <= 3; k++) {
                        for (int y = 2 * k; y <= 2 * k + 1; y++) {
                            for (int a = 9 + 2 * k; a <= 22 - 2 * k; a++) {
                                for (int b = 9 + 2 * k; b <= 22 - 2 * k; b++) at(m, alongX, a, y0 + y, b, st[k & 1]);
                            }
                        }
                    }
                    // a stair up the front
                    Stairs step = (Stairs) MUD_BRICK_STAIRS.createBlockData();
                    step.setFacing(facing(alongX, false));
                    for (int k = 0; k < 7; k++) {
                        for (int a = 15; a <= 16; a++) {
                            at(m, alongX, a, y0 + k, 22 - k, step);
                            if (k < 6) at(m, alongX, a, y0 + k + 1, 22 - k, AIR.createBlockData());
                        }
                    }
                    name = "Ziggurat, model";
                }
            }
            case 12 -> {
                // a colossal head in a helmet, its face to the front
                Material[][] sets = {{SMOOTH_BASALT, POLISHED_BASALT, POLISHED_BLACKSTONE, BLACKSTONE},
                        {ANDESITE, POLISHED_ANDESITE, STONE, TUFF}};
                Material[] set = sets[r.nextInt(sets.length)];
                BlockData body = set[0].createBlockData(), helm = set[1].createBlockData(), eye = set[2].createBlockData(),
                        lip = set[3].createBlockData();
                dais(m, alongX, 5, 5);
                double cy = 5.5;
                int[] front = new int[32 * 16];
                for (int a = 10; a <= 21; a++) {
                    for (int y = 1; y <= 10; y++) {
                        for (int b = 10; b <= 21; b++) {
                            double da = (a + 0.5 - 16) / 4.3, db = (b + 0.5 - 16) / 3.7, dy = (y + 0.5 - cy) / 5;
                            if (da * da + db * db + dy * dy >= 1) continue;
                            double up = y + 0.5 - cy;
                            at(m, alongX, a, y0 + y, b, up > 2.5 ? helm : up > 1.5 ? eye : body);
                            front[a * 16 + y] = Math.max(front[a * 16 + y], b);
                        }
                    }
                }
                int ey = (int) cy + 1;
                for (int a : new int[]{13, 18}) at(m, alongX, a, y0 + ey - 1, front[a * 16 + ey - 1], eye);
                for (int a = 15; a <= 16; a++) {
                    for (int y = ey - 3; y <= ey - 2; y++) at(m, alongX, a, y0 + y, front[a * 16 + y] + 1, body);
                    at(m, alongX, a, y0 + ey - 5, front[a * 16 + ey - 5], lip);
                }
                for (int a : new int[]{14, 17}) at(m, alongX, a, y0 + ey - 5, front[a * 16 + ey - 5], lip);
                name = "Head, colossal";
            }
            case 13 -> {
                // a foot broken off a statue at the ankle, toes to the front
                Material[] set = r.nextBoolean() ? new Material[]{QUARTZ_BLOCK, SMOOTH_QUARTZ} : new Material[]{SMOOTH_SANDSTONE, CUT_SANDSTONE};
                BlockData st = set[0].createBlockData(), broken = set[1].createBlockData();
                dais(m, alongX, 5, 4);
                for (int a = 13; a <= 18; a++) {
                    for (int b = 12; b <= 20; b++) {
                        if ((a == 13 || a == 18) && b == 12) continue;
                        int h = b <= 14 ? 4 : b <= 17 ? 3 : 2;
                        if (b >= 19 && (a == 14 || a == 16)) h = b == 19 ? 1 : 0;
                        for (int y = 1; y <= h; y++) at(m, alongX, a, y0 + y, b, st);
                    }
                }
                for (int a = 14; a <= 17; a++) {
                    for (int b = 12; b <= 15; b++) {
                        int top = 7 + Math.floorMod(a * 5 + b * 3, 3);
                        for (int y = 5; y <= top; y++) at(m, alongX, a, y0 + y, b, y == top ? broken : st);
                    }
                }
                name = "Foot, colossal";
            }
            case 14 -> {
                Material[] set = r.nextBoolean()
                        ? new Material[]{POLISHED_BLACKSTONE, CHISELED_POLISHED_BLACKSTONE, POLISHED_BLACKSTONE_BRICKS, CRACKED_POLISHED_BLACKSTONE_BRICKS}
                        : new Material[]{POLISHED_DEEPSLATE, CHISELED_DEEPSLATE, DEEPSLATE_TILES, CRACKED_DEEPSLATE_TILES};
                BlockData stone = set[0].createBlockData();
                dais(m, alongX, 4, 1);
                for (int a = 12; a <= 19; a++) {
                    int top = 8 + Math.floorMod(a * 7 + 3, 3) - (a == 19 ? 2 : 0);
                    for (int y = 1; y <= top; y++) {
                        at(m, alongX, a, y0 + y, 15, stone);
                        boolean edge = a == 12 || a == 19 || y == top;
                        int script = y <= 3 ? 1 : y <= 6 ? 2 : 3;     // three scripts, one above another
                        boolean mark = !edge && y % 3 != 1 && Math.floorMod(a * 5 + y * 11, 4) != 0;
                        at(m, alongX, a, y0 + y, 16, mark ? set[script].createBlockData() : stone);
                    }
                }
                name = r.nextBoolean() ? "Decree, three scripts" : "Slab, inscribed";
            }
            case 15 -> {
                BlockData base = END_STONE_BRICKS.createBlockData(), ob = OBSIDIAN.createBlockData(),
                        cry = CRYING_OBSIDIAN.createBlockData();
                for (int a = 12; a <= 19; a++) for (int b = 12; b <= 19; b++) at(m, alongX, a, y0, b, base);
                for (int y = 1; y <= 15; y++) {
                    for (int a = 15; a <= 16; a++) for (int b = 15; b <= 16; b++) at(m, alongX, a, y0 + y, b, hit(a, y, b, 5, 7) ? cry : ob);
                }
                at(m, alongX, 15, y0 + 16, 15, END_ROD.createBlockData());
                name = "Monolith, black";
            }
            default -> {
                // a column, its shaft broken, its drums and capital fallen beside it
                BlockData shaft = QUARTZ_PILLAR.createBlockData(), base = SMOOTH_QUARTZ.createBlockData(),
                        cap = CHISELED_QUARTZ_BLOCK.createBlockData();
                org.bukkit.block.data.Orientable lying = (org.bukkit.block.data.Orientable) QUARTZ_PILLAR.createBlockData();
                lying.setAxis(alongX ? org.bukkit.Axis.X : org.bukkit.Axis.Z);
                dais(m, alongX, 6, 4);
                for (int a = 13; a <= 18; a++) {
                    for (int b = 13; b <= 18; b++) {
                        double da = a + 0.5 - 16, db = b + 0.5 - 16, d2 = da * da + db * db;
                        if (d2 <= 6.5) at(m, alongX, a, y0 + 1, b, base);
                        if (d2 > 2.6) continue;
                        int top = 6 + Math.floorMod(a * 3 + b * 7, 4);
                        for (int y = 2; y <= top; y++) at(m, alongX, a, y0 + y, b, shaft);
                    }
                }
                for (int a = 9; a <= 13; a++) for (int b = 18; b <= 19; b++) for (int y = 1; y <= 2; y++) at(m, alongX, a, y0 + y, b, lying);
                for (int a = 19; a <= 21; a++) for (int b = 11; b <= 13; b++) for (int y = 1; y <= 2; y++) at(m, alongX, a, y0 + y, b, cap);
                name = "Column, fallen";
            }
        }
        // the label, on a post of its own
        at(m, alongX, 15, y0, 23, m.pal().trim);
        m.c().sign(X(m, alongX, 15, 24), y0, Z(m, alongX, 15, 24), facing(alongX, true), m.subject(name, Topic.MONOLITH, Rarity.RARE));
    }

    // ── Pots ─────────────────────────────────────────────────────────────

    private static final String[] POT_NAMES = {"Pot, decorated", "Vase", "Jar", "Urn, funerary", "Storage jar",
            "Pot, sherd-built", "Cooking pot", "Flask", "Pot, ritual", "Vessel"};

    /** A decorated pot as a specimen. */
    static Specimen pot(Random r) {
        return new Specimen(DECORATED_POT, POT_NAMES[r.nextInt(POT_NAMES.length)], r.nextInt(5) == 0 ? Rarity.UNCOMMON : Rarity.COMMON);
    }

    /** How often a gallery puts a decorated pot where a specimen would go, in percent. */
    static int potChance(Theme t) {
        return switch (t) {
            case ARCHAEOLOGY, ART -> 40;
            case CURIOSITIES -> 20;
            default -> 6;
        };
    }

    /** A specimen in its place: a decorated pot gets its sherds when the chunk loads. */
    static void put(MuseumCanvas c, int x, int y, int z, Specimen s, BlockFace f, Subject sub) {
        if (s.block() == DECORATED_POT) c.pot(x, y, z, f, sub);
        else c.set(x, y, z, specimen(s, f));
    }

    /** A specimen's label; pots get the potter's kind of label. */
    static Subject label(Room m, Specimen s) {
        return m.subject(MuseumCatalogue.name(s), s.block() == DECORATED_POT ? Topic.POTS : Topic.SPECIMEN, s.rarity());
    }

    /** Big vessels' profiles: radius at each height, base first. */
    private static final double[][] VESSELS = {
            {1.2, 2.5, 3.6, 4.2, 4.4, 4.2, 3.8, 3.2, 2.4, 1.6, 1.3, 1.3, 1.8, 2.2},
            {2.2, 3.2, 4.0, 4.7, 5.2, 5.5, 5.6, 5.5, 5.3, 5.0, 4.6, 4.0, 3.4, 3.0, 3.0, 3.4},
            {1.8, 1.4, 1.4, 2.2, 3.4, 4.5, 5.2, 5.7, 6.0, 6.1, 6.2},
            {1.4, 1.9, 2.3, 2.6, 2.8, 2.9, 3.0, 3.0, 2.9, 2.7, 2.4, 2.0, 1.5, 1.1, 1.1, 1.1, 1.6, 2.0},
            {2.0, 3.0, 3.8, 4.3, 4.5, 4.5, 4.3, 3.9, 3.2, 2.6, 2.6, 2.9, 2.2, 1.0},
            {2.4, 1.6, 1.6, 2.6, 3.6, 4.2, 4.4, 4.3, 4.0, 3.4, 2.8, 3.2, 3.0, 2.0, 0.9},
    };
    private static final String[] VESSEL_NAMES = {"Amphora", "Storage jar", "Krater", "Lekythos", "Jar, lidded", "Urn"};
    private static final int[] LIDS = {0, 0, 0, 0, 3, 4};             // solid rows at the top
    private static final boolean[] HANDLES = {true, false, true, false, false, true};

    /** What a vessel is made of: its body, the painted figures, the bands; and what the label calls it. */
    private record Ware(String name, Material body, Material figure, Material band) {}

    private static final Ware[] WARES = {
            new Ware("", TERRACOTTA, BLACK_TERRACOTTA, BLACK_TERRACOTTA),
            new Ware("", TERRACOTTA, BROWN_TERRACOTTA, WHITE_TERRACOTTA),
            new Ware("black-figure", ORANGE_TERRACOTTA, BLACK_TERRACOTTA, BLACK_TERRACOTTA),
            new Ware("red-figure", BLACK_TERRACOTTA, ORANGE_TERRACOTTA, ORANGE_TERRACOTTA),
            new Ware("blue and white", WHITE_CONCRETE, BLUE_CONCRETE, BLUE_GLAZED_TERRACOTTA),
            new Ware("celadon", LIME_TERRACOTTA, GREEN_TERRACOTTA, WHITE_TERRACOTTA),
            new Ware("glazed", CYAN_TERRACOTTA, LIGHT_BLUE_TERRACOTTA, LIGHT_BLUE_GLAZED_TERRACOTTA),
            new Ware("glazed", BROWN_TERRACOTTA, YELLOW_TERRACOTTA, ORANGE_GLAZED_TERRACOTTA),
            new Ware("slipware", WHITE_TERRACOTTA, RED_TERRACOTTA, RED_TERRACOTTA),
            new Ware("unfired", BROWN_TERRACOTTA, PACKED_MUD, MUD_BRICKS),
            new Ware("bronze", WAXED_OXIDIZED_COPPER, WAXED_WEATHERED_COPPER, WAXED_OXIDIZED_CHISELED_COPPER),
            new Ware("gilt", GOLD_BLOCK, RAW_GOLD_BLOCK, RAW_GOLD_BLOCK),
    };

    private static Ware ware(Random r) {
        // the last is rare
        return WARES[r.nextInt(8) == 0 ? r.nextInt(WARES.length) : r.nextInt(WARES.length - 1)];
    }

    /**
     * A big vessel turned on a lathe around room point (ca, cb): its profile scaled by {@code sr}
     * across and {@code sy} up, a band below and above a painted frieze (motif 0 zigzag, 1 key, 2
     * figures, 3 stripes, 4 plain), handles if it has them. Returns its height.
     */
    private static int vessel(Room m, boolean alongX, double ca, double cb, int y0, int v, double sr, double sy, Ware w,
                              int motif, long seed) {
        double[] prof = VESSELS[v];
        int h = Math.max(4, (int) Math.round(prof.length * sy)), lid = LIDS[v] == 0 ? 0 : Math.max(2, (int) Math.round(LIDS[v] * sy));
        BlockData body = w.body().createBlockData(), fig = w.figure().createBlockData(), band = w.band().createBlockData();
        double[] rad = new double[h];
        double rmax = 0;
        for (int y = 0; y < h; y++) {
            double t = y * (prof.length - 1.0) / (h - 1);
            int i = (int) t;
            double f = t - i;
            rad[y] = sr * (i + 1 < prof.length ? prof[i] * (1 - f) + prof[i + 1] * f : prof[i]);
            rmax = Math.max(rmax, rad[y]);
        }
        int b1 = Math.max(1, (int) (h * 0.22)), b2 = (int) (h * 0.62);
        int span = (int) Math.ceil(rmax) + 1;
        for (int y = 0; y < h; y++) {
            boolean solid = y == 0 || y >= h - lid;
            int sectors = Math.max(8, (int) Math.round(2 * Math.PI * rad[y] / 1.5));
            for (int a = (int) Math.floor(ca) - span; a <= (int) Math.floor(ca) + span; a++) {
                for (int b = (int) Math.floor(cb) - span; b <= (int) Math.floor(cb) + span; b++) {
                    double da = a + 0.5 - ca, db = b + 0.5 - cb, d = Math.hypot(da, db);
                    if (d > rad[y] || (!solid && d <= rad[y] - 1.3)) continue;
                    BlockData bd = body;
                    if (y == b1 || y == b2 || (y == h - 1 && lid == 0)) bd = band;
                    else if (y > b1 && y < b2) {
                        double th = Math.atan2(db, da);
                        int sec = (int) Math.floor((th + Math.PI) / (2 * Math.PI) * sectors);
                        boolean on = switch (motif) {
                            case 0 -> Math.floorMod(sec + y, 4) == 0;
                            case 1 -> Math.floorMod(sec / 2 + (y - b1) / 2, 2) == 0 && Math.floorMod(sec, 2) == 0;
                            case 2 -> SimplexNoise.noise2(seed, Math.cos(th) * 2.5 + y * 0.1, Math.sin(th) * 2.5 + y * 0.4) > 0.3;
                            case 3 -> Math.floorMod(sec, 3) == 0;
                            default -> false;
                        };
                        if (on) bd = fig;
                    }
                    at(m, alongX, a, y0 + y, b, bd);
                }
            }
        }
        if (HANDLES[v]) {
            int hs = (int) (h * 0.5), he = (int) (h * 0.8);
            double reach = 0;
            for (int y = hs; y <= he; y++) reach = Math.max(reach, rad[y]);
            for (int side = -1; side <= 1; side += 2) {
                int out = (int) Math.floor(ca + side * (reach + 0.9));
                for (int b = (int) Math.floor(cb - 0.5); b <= (int) Math.floor(cb); b++) {
                    for (int y = hs; y <= he; y++) at(m, alongX, out, y0 + y, b, body);
                    int in = (int) Math.floor(ca + side * (rad[he] - 0.5));
                    for (int a = Math.min(in, out); a <= Math.max(in, out); a++) at(m, alongX, a, y0 + he, b, body);
                }
            }
        }
        return h;
    }

    /**
     * Pots: one great vessel on a dais with decorated pots at its corners; a magazine of storage
     * jars; a shelving unit full of decorated pots; or decorated pots on stepped plinths.
     */
    static void pots(Room m, boolean alongX) {
        Random r = m.r();
        int mode = r.nextInt(20);
        int F = m.F();
        if (mode < 8) {
            int v = r.nextInt(VESSELS.length);
            Ware w = ware(r);
            double pmax = 0;
            for (double x : VESSELS[v]) pmax = Math.max(pmax, x);
            double sr = Math.min(0.85 + r.nextDouble() * 0.4, 6.0 / pmax);
            double sy = Math.min(0.9 + r.nextDouble() * 0.4, 12.0 / VESSELS[v].length);
            int ac = Math.min(7, (int) Math.ceil(pmax * sr) + 1);
            dais(m, alongX, ac, ac);
            vessel(m, alongX, 16, 16, F + 2, v, sr, sy, w, r.nextInt(5), r.nextLong());
            for (int a : new int[]{16 - ac, 15 + ac}) {
                for (int b : new int[]{16 - ac, 15 + ac}) {
                    m.c().pot(X(m, alongX, a, b), F + 2, Z(m, alongX, a, b), facing(alongX, b > 16), m.subject("Pot", Topic.POTS, Rarity.COMMON));
                }
            }
            String name = VESSEL_NAMES[v] + (w.name().isEmpty() ? "" : ", " + w.name());
            daisSign(m, alongX, ac, m.subject(name, Topic.POTS, Rarity.RARE));
        } else if (mode < 11) {
            // storage jars in rows, as in a palace magazine
            Ware w = r.nextBoolean() ? WARES[0] : ware(r);
            double sy = 0.4 + r.nextDouble() * 0.15;
            for (double ca : new double[]{8, 13, 19, 24}) {
                for (double cb : new double[]{11, 21}) vessel(m, alongX, ca, cb, F + 1, 1, 0.42, sy, w, r.nextInt(5), r.nextLong());
            }
            at(m, alongX, 15, F + 1, 16, m.pal().trim);
            m.c().sign(X(m, alongX, 15, 17), F + 1, Z(m, alongX, 15, 17), facing(alongX, true),
                    m.subject("Storage jars" + (w.name().isEmpty() ? "" : ", " + w.name()), Topic.POTS, Rarity.UNCOMMON));
        } else if (mode < 15) {
            // a study collection: two tiers of decorated pots both sides of a long shelf, more on top
            BlockData wood = m.pal().wood;
            for (int a = 7; a <= 24; a++) {
                for (int b = 15; b <= 16; b++) {
                    boolean end = a == 7 || a == 24;
                    BlockFace f = facing(alongX, b == 16);
                    for (int y = 1; y <= 6; y++) {
                        if (y == 1 || y == 3 || y == 5 || (end && y < 6)) at(m, alongX, a, F + y, b, wood);
                        else if (r.nextInt(5) > 0 && (y < 6 || r.nextBoolean())) {
                            m.c().pot(X(m, alongX, a, b), F + y, Z(m, alongX, a, b), f, m.subject("Pot", Topic.POTS, Rarity.COMMON));
                        }
                    }
                }
            }
            m.c().sign(X(m, alongX, 15, 17), F + 1, Z(m, alongX, 15, 17), facing(alongX, true),
                    m.subject("Pots, study collection", Topic.POTS, Rarity.UNCOMMON));
            m.c().sign(X(m, alongX, 16, 14), F + 1, Z(m, alongX, 16, 14), facing(alongX, false),
                    m.subject("Pots, decorated", Topic.POTS, Rarity.UNCOMMON));
        } else {
            for (int a = 8; a <= 23; a++) {
                for (int b = 12; b <= 19; b++) {
                    int step = Math.min(Math.min(a - 8, 23 - a), Math.min(b - 12, 19 - b));
                    int h = Math.min(3, step + 1);
                    for (int y = 1; y <= h; y++) at(m, alongX, a, F + y, b, y == h ? m.pal().daisTop : m.pal().dais);
                    if ((a + b) % 2 == 0 && r.nextInt(5) > 0) {
                        m.c().pot(X(m, alongX, a, b), F + h + 1, Z(m, alongX, a, b), facing(alongX, b > 15),
                                m.subject("Pot", Topic.POTS, Rarity.COMMON));
                    }
                }
            }
            m.c().sign(X(m, alongX, 15, 20), F + 1, Z(m, alongX, 15, 20), facing(alongX, true),
                    m.subject("Pots, decorated", Topic.POTS, Rarity.UNCOMMON));
        }
    }

    /** Decorated pots on plinths either side of each doorway. */
    static void doorPots(Room m) {
        for (int s = 0; s < 4; s++) {
            if (m.e()[s] != Level1882ChunkGenerator.DOOR) continue;
            for (int t : new int[]{10, 21}) {
                int x = sx(m.ox(), s, 1, t), z = sz(m.oz(), s, 1, t);
                m.c().set(x, m.F() + 1, z, m.pal().carved);
                m.c().pot(x, m.F() + 2, z, INTO_ROOM[s], m.subject("Pot", Topic.POTS, Rarity.COMMON));
            }
        }
    }

    /** Paintings on the walls (filled in on load), a block mural on one, benches in the middle. */
    static void paintings(Room m) {
        int muralSide = -1;
        for (int s = 0; s < 4; s++) if (m.e()[s] == Level1882ChunkGenerator.CLOSED && muralSide < 0 && m.r().nextBoolean()) muralSide = s;
        for (int s = 0; s < 4; s++) {
            if (s == muralSide) {
                mural(m, s);
                continue;
            }
            byte e = m.e()[s];
            if (e == Level1882ChunkGenerator.ARCADE) continue;
            int[] slots = e == Level1882ChunkGenerator.DOOR ? new int[]{5, 26} : new int[]{6, 15, 25};
            for (int t : slots) {
                int x = sx(m.ox(), s, 1, t), z = sz(m.oz(), s, 1, t);
                m.c().painting(x, m.F() + 6, z, INTO_ROOM[s], m.subject("", Topic.PAINTING, Rarity.COMMON), 4, 4);
                m.c().sign(x, m.F() + 2, z, INTO_ROOM[s], m.subject("", Topic.PAINTING, Rarity.COMMON));
            }
        }
        benchRows(m, m.r().nextBoolean(), 15, 16, false);
    }

    /** A landscape in concrete and terracotta on a wall, in a dark frame. */
    static void mural(Room m, int side) {
        long s = m.r().nextLong();
        int t0 = 6, t1 = 25, y0 = 3, y1 = 13;
        BlockData frame = STRIPPED_DARK_OAK_WOOD.createBlockData();
        boolean sea = m.r().nextBoolean();
        for (int t = t0; t <= t1; t++) {
            double hill = 6 + 2.5 * SimplexNoise.noise2(s, t * 0.12, 0) + (sea ? -2 : 0);
            for (int y = y0; y <= y1; y++) {
                Material mt;
                if (t == t0 || t == t1 || y == y0 || y == y1) mt = null;
                else {
                    int h = y - y0;
                    if (h < hill - 2) mt = sea ? BLUE_CONCRETE : GREEN_TERRACOTTA;
                    else if (h < hill) mt = sea ? CYAN_CONCRETE : LIME_TERRACOTTA;
                    else if (h > 8 && Math.floorMod(t * 3 + h * 5, 11) == 0) mt = WHITE_CONCRETE;
                    else mt = h > 7 ? LIGHT_BLUE_CONCRETE : h > 5 ? LIGHT_BLUE_TERRACOTTA : PINK_TERRACOTTA;
                }
                m.c().set(sx(m.ox(), side, 0, t), m.F() + y, sz(m.oz(), side, 0, t), mt == null ? frame : mt.createBlockData());
            }
        }
        m.c().sign(sx(m.ox(), side, 1, 15), m.F() + 2, sz(m.oz(), side, 1, 15), INTO_ROOM[side],
                m.subject("", Topic.MURAL, Rarity.UNCOMMON));
    }

    /** A giant ammonite on a wall: a bone spiral with ribs. */
    static void ammonite(Room m, int side) {
        double ct = 15.5, cr = 9.5;
        for (double th = 0.6; th < 6 * Math.PI; th += 0.04) {
            double rad = 0.42 * th;
            int t = (int) Math.floor(ct + rad * Math.cos(th)), y = (int) Math.floor(cr + rad * Math.sin(th));
            m.c().set(sx(m.ox(), side, 1, t), m.F() + y, sz(m.oz(), side, 1, t), MuseumSkeletons.BONE[1]);
        }
        for (double th = 2 * Math.PI; th < 6 * Math.PI; th += Math.PI / 7) {
            for (double rad = 0.42 * (th - 2 * Math.PI); rad < 0.42 * th; rad += 0.4) {
                int t = (int) Math.floor(ct + rad * Math.cos(th)), y = (int) Math.floor(cr + rad * Math.sin(th));
                m.c().set(sx(m.ox(), side, 1, t), m.F() + y, sz(m.oz(), side, 1, t), MuseumSkeletons.IVORY);
            }
        }
        m.c().sign(sx(m.ox(), side, 2, 15), m.F() + 1, sz(m.oz(), side, 2, 15), INTO_ROOM[side],
                m.subject("Ammonite, giant", Topic.SPECIMEN, Rarity.RARE));
        m.c().set(sx(m.ox(), side, 1, 15), m.F() + 1, sz(m.oz(), side, 1, 15), m.pal().trim);
    }

    /** Two rows of benches along the axis at {@code b0} and {@code b1}: facing the middle, or back to back. */
    static void benchRows(Room m, boolean alongX, int b0, int b1, boolean faceMiddle) {
        Material wood = m.pal().wood.getMaterial() == BIRCH_PLANKS ? BIRCH_STAIRS
                : m.pal().wood.getMaterial() == JUNGLE_PLANKS ? JUNGLE_STAIRS
                : m.pal().wood.getMaterial() == ACACIA_PLANKS ? ACACIA_STAIRS
                : m.pal().wood.getMaterial() == CRIMSON_PLANKS ? CRIMSON_STAIRS : SPRUCE_STAIRS;
        for (int row : new int[]{b0, b1}) {
            BlockFace low = alongX ? BlockFace.NORTH : BlockFace.WEST;
            boolean looksUp = (row < 16) == faceMiddle;
            Stairs s = (Stairs) wood.createBlockData();
            s.setFacing(looksUp ? low : low.getOppositeFace());
            for (int a = 11; a <= 20; a++) {
                if (a == 15 || a == 16) continue;
                m.c().set(X(m, alongX, a, row), m.F() + 1, Z(m, alongX, a, row), s);
            }
        }
    }

    /** Freestanding columns in the room's four quarters. */
    static void columns(Room m, int top) {
        for (int a : new int[]{6, 24}) {
            for (int b : new int[]{6, 24}) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        for (int y = m.F() + 1; y < top; y++) {
                            int r = y - m.F();
                            BlockData bd = r == 1 || r == top - m.F() - 1 ? m.pal().carved : r % 5 == 3 ? m.pal().band : m.pal().trim;
                            m.c().set(m.ox() + a + dx, y, m.oz() + b + dz, bd);
                        }
                    }
                }
            }
        }
    }

    /** A lectern by a wall with a book about the gallery. */
    static void lectern(Room m, int side, int t) {
        // a lectern's facing is the side its reader stands on
        m.c().lectern(sx(m.ox(), side, 4, t), m.F() + 1, sz(m.oz(), side, 4, t), INTO_ROOM[side],
                new Subject("", Topic.GUIDE, Rarity.COMMON, m.gallery(), m.theme(), m.seed()));
    }

    // ── Libraries ────────────────────────────────────────────────────────

    /** A library's fittings, by what it collects: its wood, carpet and candles, and what stands on the stalls. */
    record Fittings(String wood, Material carpet, Material candle, Material... deco) {
        BlockData planks() { return Material.valueOf(wood + "_PLANKS").createBlockData(); }
        BlockData fence() { return Material.valueOf(wood + "_FENCE").createBlockData(); }
        Material stairs() { return Material.valueOf(wood + "_STAIRS"); }

        BlockData post() {
            return (wood.equals("CRIMSON") ? STRIPPED_CRIMSON_STEM : Material.valueOf("STRIPPED_" + wood + "_LOG")).createBlockData();
        }

        BlockData topSlab() {
            org.bukkit.block.data.type.Slab s = (org.bukkit.block.data.type.Slab) Material.valueOf(wood + "_SLAB").createBlockData();
            s.setType(org.bukkit.block.data.type.Slab.Type.TOP);
            return s;
        }

        BlockData litCandle(Random r) {
            org.bukkit.block.data.type.Candle candle = (org.bukkit.block.data.type.Candle) this.candle.createBlockData();
            candle.setLit(true);
            candle.setCandles(1 + r.nextInt(3));
            return candle;
        }
    }

    static Fittings fittings(Theme t) {
        return switch (t) {
            case MINERALS -> new Fittings("SPRUCE", GRAY_CARPET, WHITE_CANDLE, AMETHYST_CLUSTER, LARGE_AMETHYST_BUD, CALCITE, RAW_IRON_BLOCK);
            case FOSSILS -> new Fittings("DARK_OAK", BROWN_CARPET, BROWN_CANDLE, SKELETON_SKULL, BONE_BLOCK, SKELETON_SKULL);
            case MOBS -> new Fittings("SPRUCE", RED_CARPET, RED_CANDLE, SKELETON_SKULL, ZOMBIE_HEAD, CREEPER_HEAD, PIGLIN_HEAD);
            case BOTANY -> new Fittings("JUNGLE", GREEN_CARPET, GREEN_CANDLE, POTTED_FERN, POTTED_AZALEA_BUSH,
                    POTTED_FLOWERING_AZALEA_BUSH, MOSS_CARPET, POTTED_CACTUS);
            case MARINE -> new Fittings("DARK_OAK", CYAN_CARPET, CYAN_CANDLE, SEA_PICKLE, PRISMARINE, DEAD_BRAIN_CORAL_BLOCK,
                    DEAD_TUBE_CORAL_BLOCK);
            case ARCHAEOLOGY -> new Fittings("ACACIA", ORANGE_CARPET, ORANGE_CANDLE, DECORATED_POT, DECORATED_POT, DECORATED_POT,
                    CHISELED_SANDSTONE);
            case NETHER -> new Fittings("CRIMSON", BLACK_CARPET, RED_CANDLE, WITHER_SKELETON_SKULL, SHROOMLIGHT, MAGMA_BLOCK,
                    CHISELED_POLISHED_BLACKSTONE);
            case END -> new Fittings("BIRCH", PURPLE_CARPET, PURPLE_CANDLE, END_ROD, CHORUS_FLOWER, PURPUR_PILLAR, DRAGON_HEAD);
            case CURIOSITIES -> new Fittings("DARK_OAK", BLUE_CARPET, YELLOW_CANDLE, LODESTONE, BELL, LIGHTNING_ROD, DECORATED_POT);
            case ART -> new Fittings("CHERRY", MAGENTA_CARPET, PINK_CANDLE, DECORATED_POT, POTTED_POPPY, POTTED_ALLIUM, FLOWER_POT);
            case ZOOLOGY -> new Fittings("OAK", YELLOW_CARPET, WHITE_CANDLE, TURTLE_EGG, SNIFFER_EGG, SKELETON_SKULL, HAY_BLOCK);
            case GEOLOGY -> new Fittings("SPRUCE", LIGHT_GRAY_CARPET, GRAY_CANDLE, POINTED_DRIPSTONE, SMOOTH_BASALT, AMETHYST_CLUSTER,
                    MAGMA_BLOCK);
        };
    }

    /** An ornament on top of a bookcase, its face (if it has one) toward {@code f}. */
    private static void ornament(Room m, int x, int y, int z, Material mt, BlockFace f) {
        if (mt == DECORATED_POT) {
            m.c().pot(x, y, z, f, m.subject("Pot", Topic.POTS, Rarity.COMMON));
            return;
        }
        BlockData b = mt.createBlockData();
        if (b instanceof Rotatable rot) rot.setRotation(f.getOppositeFace());
        m.c().set(x, y, z, b);
    }

    /** A bookcase block: a chiseled shelf facing {@code f}, its slots filled with chance {@code full}; now and then a plain one. */
    static void books(MuseumCanvas c, Random r, int x, int y, int z, BlockFace f, double full, Subject s) {
        if (r.nextInt(10) == 0) c.set(x, y, z, BOOKSHELF);
        else c.shelf(x, y, z, f, full, r, s);
    }

    /** One library room being fitted out: its fittings, the subject its books are on, how full its shelves are. */
    private record Lib(Room m, boolean alongX, Fittings fit, Subject shelved, double full) {
        void books(int x, int y, int z, BlockFace f) {
            MuseumExhibits.books(m.c(), m.r(), x, y, z, f, full, shelved);
        }

        void at(int a, int y, int b, BlockData d) {
            MuseumExhibits.at(m, alongX, a, y, b, d);
        }

        Material deco() {
            return fit.deco()[m.r().nextInt(fit.deco().length)];
        }

        void lectern(int a, int b, BlockFace f) {
            m.c().lectern(X(m, alongX, a, b), m.F() + 1, Z(m, alongX, a, b), f,
                    new Subject("", Topic.LIBRARY, Rarity.COMMON, m.gallery(), m.theme(), m.seed() + a * 31L + b));
        }

        void shelfmark(int a, int y, int b, BlockFace f) {
            m.c().sign(X(m, alongX, a, b), y, Z(m, alongX, a, b), f, m.subject("", Topic.SHELFMARK, Rarity.COMMON));
        }
    }

    /**
     * A library, fitted out for its subject, in one of five arrangements: stalls after Duke
     * Humfrey's Library, a rotunda, the stacks, a reading room of long tables under two galleries,
     * or a map room. Returns what the sign over the door calls it.
     */
    static String library(Room m, boolean alongX) {
        Random r = m.r();
        Lib L = new Lib(m, alongX, fittings(m.theme()),
                new Subject("", Topic.LIBRARY, Rarity.COMMON, m.gallery(), m.theme(), 0), 0.55 + r.nextDouble() * 0.4);
        String[] t = m.theme().titles;
        int roll = r.nextInt(10);
        if (roll < 3) {
            stalls(L);
            return name(r, "Reading Room", "Library of " + t[0], t[r.nextInt(t.length)] + " Library");
        } else if (roll < 5) {
            rotunda(L);
            return name(r, "The Rotunda", "Round Reading Room", "Rotunda, " + t[0]);
        } else if (roll < 7) {
            stacks(L);
            return name(r, "The Stacks", "Book Stack " + (1 + r.nextInt(30)), "Reserve Collection", t[0] + " Stack");
        } else if (roll < 9) {
            tables(L);
            return name(r, "Reading Room", "Upper Reading Room", "Reading Room, " + t[0], "Students' Room");
        }
        mapRoom(L);
        return name(r, "Map Room", "Map Room, " + t[0], "Atlases and Charts");
    }

    private static String name(Random r, String... names) {
        return names[r.nextInt(names.length)];
    }

    /**
     * Bookcases two deep up the walls to {@code top}, plain behind and chiseled in front, with
     * posts every six blocks and a cornice; over the closed walls a gallery at each of
     * {@code galleries} (the first three deep, the rest two), railed, with ladders up.
     */
    private static void wallBooks(Lib L, int top, int... galleries) {
        Room m = L.m();
        MuseumCanvas c = m.c();
        int F = m.F();
        byte[] e = m.e();
        BlockData post = L.fit().post(), planks = L.fit().planks(), rail = L.fit().fence();
        for (int side = 0; side < 4; side++) {
            byte k = e[side];
            if (k == Level1882ChunkGenerator.ARCADE) continue;
            boolean closed = k == Level1882ChunkGenerator.CLOSED;
            int[][] runs = closed ? new int[][]{{2, 29}} : new int[][]{{2, 10}, {21, 29}};
            for (int[] run : runs) {
                for (int t = run[0]; t <= run[1]; t++) {
                    int x1 = sx(m.ox(), side, 1, t), z1 = sz(m.oz(), side, 1, t);
                    int x2 = sx(m.ox(), side, 2, t), z2 = sz(m.oz(), side, 2, t);
                    boolean pilaster = (t - 2) % 6 == 3;
                    for (int y = F + 1; y <= top; y++) {
                        if (y == top) {
                            c.set(x1, y, z1, m.pal().trim);
                            c.set(x2, y, z2, m.pal().trim);
                            continue;
                        }
                        c.set(x1, y, z1, BOOKSHELF);
                        boolean hidden = t == 2 || t == 29;     // shared with the next wall
                        // behind a gallery's floor, or the end of the next wall's gallery
                        for (int g : galleries) hidden |= closed && y == g || (t <= 5 || t >= 26) && (y == g || y == g + 1);
                        if (pilaster) c.set(x2, y, z2, post);
                        else if (hidden) c.set(x2, y, z2, BOOKSHELF);
                        else L.books(x2, y, z2, INTO_ROOM[side]);
                    }
                }
            }
            if (!closed) continue;
            int lo = side < 2 ? 2 : 0, hi = side < 2 ? 3 : 1;   // the walls at either end of this one
            boolean loC = e[lo] == Level1882ChunkGenerator.CLOSED, hiC = e[hi] == Level1882ChunkGenerator.CLOSED;
            for (int gi = 0; gi < galleries.length; gi++) {
                int g = galleries[gi], d1 = gi == 0 ? 5 : 4;
                for (int t = 3; t <= 28; t++) {
                    for (int d = 3; d <= d1; d++) c.set(sx(m.ox(), side, d, t), g, sz(m.oz(), side, d, t), planks);
                    if (t >= (loC ? d1 : 3) && t <= (hiC ? M() - 1 - d1 : 28)) {
                        c.set(sx(m.ox(), side, d1, t), g + 1, sz(m.oz(), side, d1, t), rail);
                    }
                    if ((t == 3 && e[lo] == Level1882ChunkGenerator.ARCADE) || (t == 28 && e[hi] == Level1882ChunkGenerator.ARCADE)) {
                        for (int d = 3; d < d1; d++) c.set(sx(m.ox(), side, d, t), g + 1, sz(m.oz(), side, d, t), rail);
                    }
                }
                org.bukkit.block.data.type.Ladder ladder = (org.bukkit.block.data.type.Ladder) LADDER.createBlockData();
                ladder.setFacing(INTO_ROOM[side]);
                int t = gi % 2 == 0 ? 6 : 25, from = gi == 0 ? F + 1 : galleries[gi - 1] + 1;
                for (int y = from; y <= g; y++) c.set(sx(m.ox(), side, 3, t), y, sz(m.oz(), side, 3, t), ladder);
            }
        }
    }

    private static int M() {
        return Level1882ChunkGenerator.M;
    }

    /** Carpet over the floor from (a0, b0) to (a1, b1). */
    private static void carpet(Lib L, int a0, int a1, int b0, int b1) {
        BlockData carpet = L.fit().carpet().createBlockData();
        for (int a = a0; a <= a1; a++) for (int b = b0; b <= b1; b++) L.at(a, L.m().F() + 1, b, carpet);
    }

    /** A glass-topped table of things from the collection, a ≤ a0..a1, b ≤ b0..b1, labelled at (la, lb) facing {@code f}. */
    private static void vitrine(Lib L, int a0, int a1, int b0, int b1, int la, int lb, BlockFace f) {
        Room m = L.m();
        int F = m.F();
        Specimen[] pool = MuseumCatalogue.pool(m.theme());
        BlockData planks = L.fit().planks(), glass = Material.GLASS.createBlockData();
        Specimen first = null;
        for (int a = a0; a <= a1; a++) {
            for (int b = b0; b <= b1; b++) {
                L.at(a, F + 1, b, planks);
                L.at(a, F + 3, b, glass);
                if ((a + b) % 2 != 0) continue;
                Specimen sp = m.r().nextInt(100) < potChance(m.theme()) ? pot(m.r()) : pool[m.r().nextInt(pool.length)];
                if (first == null) first = sp;
                put(m.c(), X(m, L.alongX(), a, b), F + 2, Z(m, L.alongX(), a, b), sp, f, label(m, sp));
            }
        }
        if (first != null) m.c().sign(X(m, L.alongX(), la, lb), F + 1, Z(m, L.alongX(), la, lb), f, label(m, first));
    }

    /**
     * Duke Humfrey's: stalls of bookcases two deep standing out from the walls, desks with candles
     * between them, carpet down the aisles, glass-topped tables in the cross aisle, a gallery.
     */
    private static void stalls(Lib L) {
        Room m = L.m();
        MuseumCanvas c = m.c();
        Random r = m.r();
        int F = m.F();
        boolean alongX = L.alongX();
        wallBooks(L, F + 12, F + 6);
        for (int a = 6; a <= 25; a++) {
            for (int b = 6; b <= 25; b++) {
                if ((b >= 14 && b <= 17) || (a >= 14 && a <= 17)) L.at(a, F + 1, b, L.fit().carpet().createBlockData());
            }
        }
        int h = r.nextBoolean() ? 3 : 5;
        BlockData planks = L.fit().planks();
        for (int s : new int[]{6, 12, 18, 24}) {
            for (int b = 6; b <= 25; b++) {
                if (b > 11 && b < 20) continue;
                for (int k = 0; k <= 1; k++) {
                    int x = X(m, alongX, s + k, b), z = Z(m, alongX, s + k, b);
                    for (int y = F + 1; y <= F + h; y++) L.books(x, y, z, facing(!alongX, k == 1));
                    c.set(x, F + h + 1, z, planks);
                }
            }
            for (int b : new int[]{7, 10, 21, 24}) {
                if (r.nextInt(3) == 0) continue;
                int a = s + r.nextInt(2);
                ornament(m, X(m, alongX, a, b), F + h + 2, Z(m, alongX, a, b), L.deco(), facing(alongX, b < 16));
            }
            // the stall's shelfmark, toward the aisle
            L.shelfmark(s, F + 1, 12, facing(alongX, true));
            L.shelfmark(s + 1, F + 1, 19, facing(alongX, false));
        }
        for (int a0 : new int[]{8, 20}) {
            for (int b0 : new int[]{6, 20}) {
                for (int b = b0 + 1; b <= b0 + 4; b++) {
                    L.at(a0 + 1, F + 1, b, L.fit().topSlab());
                    L.at(a0 + 2, F + 1, b, L.fit().topSlab());
                    Stairs chair = (Stairs) L.fit().stairs().createBlockData();
                    chair.setFacing(facing(!alongX, false));
                    L.at(a0, F + 1, b, chair);
                    Stairs chair2 = (Stairs) L.fit().stairs().createBlockData();
                    chair2.setFacing(facing(!alongX, true));
                    L.at(a0 + 3, F + 1, b, chair2);
                }
                L.at(a0 + 1 + r.nextInt(2), F + 2, b0 + 2 + r.nextInt(2), L.fit().litCandle(r));
            }
        }
        vitrine(L, 15, 16, 7, 10, 15, 11, facing(alongX, true));
        vitrine(L, 15, 16, 21, 24, 15, 20, facing(alongX, false));
        for (int a : new int[]{5, 26}) if (r.nextInt(3) > 0) L.lectern(a, 16, facing(!alongX, a < 16));
    }

    /**
     * A rotunda: a ring of low bookcases round a round reading table, broken by four aisles,
     * carpet inside the ring, the walls lined behind.
     */
    private static void rotunda(Lib L) {
        Room m = L.m();
        MuseumCanvas c = m.c();
        Random r = m.r();
        int F = m.F();
        wallBooks(L, F + 12, r.nextBoolean() ? new int[]{F + 6} : new int[0]);
        BlockData planks = L.fit().planks(), carpet = L.fit().carpet().createBlockData(), table = L.fit().topSlab();
        double r0 = 8.6, r1 = 10.6, mid = 9.6;
        for (int lx = 3; lx <= 28; lx++) {
            for (int lz = 3; lz <= 28; lz++) {
                double dx = lx + 0.5 - 16, dz = lz + 0.5 - 16, d = Math.hypot(dx, dz);
                int x = m.ox() + lx, z = m.oz() + lz;
                if (d >= r0 && d < r1 && Math.abs(dx) >= 2.5 && Math.abs(dz) >= 2.5) {
                    BlockFace out = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? BlockFace.EAST : BlockFace.WEST)
                            : (dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
                    BlockFace f = d < mid ? out.getOppositeFace() : out;
                    double fx = dx + f.getModX(), fz = dz + f.getModZ(), fd = Math.hypot(fx, fz);
                    boolean blocked = fd >= r0 && fd < r1 && Math.abs(fx) >= 2.5 && Math.abs(fz) >= 2.5;
                    for (int y = F + 1; y <= F + 4; y++) {
                        if (blocked) c.set(x, y, z, BOOKSHELF);
                        else L.books(x, y, z, f);
                    }
                    c.set(x, F + 5, z, planks);
                    if (r.nextInt(12) == 0) ornament(m, x, F + 6, z, L.deco(), out);
                } else if (d < 2.6) {
                    c.set(x, F + 1, z, table);
                } else if (d < 3.6) {
                    BlockFace out = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? BlockFace.EAST : BlockFace.WEST)
                            : (dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
                    Stairs chair = (Stairs) L.fit().stairs().createBlockData();
                    chair.setFacing(out);
                    c.set(x, F + 1, z, chair);
                } else if (d < r0) {
                    c.set(x, F + 1, z, carpet);
                }
            }
        }
        c.set(m.ox() + 15, F + 2, m.oz() + 15, L.fit().litCandle(r));
        c.set(m.ox() + 16, F + 2, m.oz() + 16, L.fit().litCandle(r));
        // lecterns on the carpet, readers on the side toward the table
        c.lectern(m.ox() + 10, F + 1, m.oz() + 10, BlockFace.EAST,
                new Subject("", Topic.LIBRARY, Rarity.COMMON, m.gallery(), m.theme(), m.seed() + 1));
        c.lectern(m.ox() + 21, F + 1, m.oz() + 21, BlockFace.WEST,
                new Subject("", Topic.LIBRARY, Rarity.COMMON, m.gallery(), m.theme(), m.seed() + 2));
    }

    /**
     * The stacks: tall bookcases two deep in close rows either side of a carpeted corridor, a
     * shelfmark at the end of each row.
     */
    private static void stacks(Lib L) {
        Room m = L.m();
        MuseumCanvas c = m.c();
        Random r = m.r();
        int F = m.F();
        boolean alongX = L.alongX();
        wallBooks(L, F + 12);
        carpet(L, 3, 28, 14, 17);
        int h = 6 + r.nextInt(3);
        BlockData planks = L.fit().planks();
        for (int s = 5; s <= 25; s += 4) {
            for (int b = 6; b <= 25; b++) {
                if (b > 13 && b < 18) continue;
                for (int k = 0; k <= 1; k++) {
                    int x = X(m, alongX, s + k, b), z = Z(m, alongX, s + k, b);
                    for (int y = F + 1; y <= F + h; y++) L.books(x, y, z, facing(!alongX, k == 1));
                    c.set(x, F + h + 1, z, planks);
                }
            }
            L.shelfmark(s, F + 2, 14, facing(alongX, true));
            L.shelfmark(s + 1, F + 2, 17, facing(alongX, false));
            if (r.nextInt(4) == 0) ornament(m, X(m, alongX, s, 7), F + h + 2, Z(m, alongX, s, 7), L.deco(), facing(alongX, true));
        }
        // a desk at a closed end of the corridor
        for (int end : new int[]{0, 1}) {
            int side = alongX ? end : 2 + end;
            if (m.e()[side] != Level1882ChunkGenerator.CLOSED) continue;
            int a = end == 0 ? 3 : 28;
            L.lectern(a, 15, facing(!alongX, end == 0));
            L.at(a, F + 1, 16, L.fit().planks());
            L.at(a, F + 2, 16, L.fit().litCandle(r));
            break;
        }
    }

    /** A reading room: three long tables with lamps and chairs, under two galleries of bookcases. */
    private static void tables(Lib L) {
        Room m = L.m();
        Random r = m.r();
        int F = m.F();
        boolean alongX = L.alongX();
        wallBooks(L, F + 13, F + 6, F + 10);
        carpet(L, 6, 25, 6, 25);
        boolean lanterns = r.nextBoolean();
        for (int b0 : new int[]{8, 15, 22}) {
            for (int a = 6; a <= 25; a++) {
                if (a == 15 || a == 16) continue;
                L.at(a, F + 1, b0, L.fit().topSlab());
                L.at(a, F + 1, b0 + 1, L.fit().topSlab());
                Stairs chair = (Stairs) L.fit().stairs().createBlockData();
                chair.setFacing(facing(alongX, false));
                if (a % 2 == 0) L.at(a, F + 1, b0 - 1, chair);
                Stairs chair2 = (Stairs) L.fit().stairs().createBlockData();
                chair2.setFacing(facing(alongX, true));
                if (a % 2 == 0) L.at(a, F + 1, b0 + 2, chair2);
                if (a % 4 == 1) {
                    BlockData lamp = lanterns ? LANTERN.createBlockData() : L.fit().litCandle(r);
                    L.at(a, F + 2, b0 + (a % 8 == 1 ? 0 : 1), lamp);
                }
            }
        }
        L.lectern(5, 12, facing(!alongX, true));
        L.lectern(26, 19, facing(!alongX, false));
        if (r.nextBoolean()) vitrine(L, 15, 16, 4, 5, 15, 6, facing(alongX, true));
    }

    /**
     * A map room: low map cases round the walls with charts hung above, a great table of
     * cartography tables, globes on stands in the corners, plan chests, lecterns.
     */
    private static void mapRoom(Lib L) {
        Room m = L.m();
        MuseumCanvas c = m.c();
        Random r = m.r();
        int F = m.F();
        boolean alongX = L.alongX();
        wallBooks(L, F + 5);
        for (int s = 0; s < 4; s++) {
            byte e = m.e()[s];
            if (e == Level1882ChunkGenerator.ARCADE) continue;
            int[] slots = e == Level1882ChunkGenerator.DOOR ? new int[]{6, 25} : new int[]{8, 15, 23};
            for (int t : slots) {
                c.painting(sx(m.ox(), s, 1, t), F + 9, sz(m.oz(), s, 1, t), INTO_ROOM[s], m.subject("", Topic.PAINTING, Rarity.COMMON), 4, 3);
            }
        }
        carpet(L, 4, 27, 4, 27);
        for (int a = 12; a <= 19; a++) for (int b = 13; b <= 18; b++) L.at(a, F + 1, b, CARTOGRAPHY_TABLE.createBlockData());
        // plan chests, two high, their drawers toward the table
        for (int[] run : new int[][]{{6, 11}, {20, 25}}) {
            for (int a = run[0]; a <= run[1]; a++) {
                for (int b : new int[]{9, 22}) {
                    BlockFace f = facing(alongX, b < 16);
                    for (int y = F + 1; y <= F + 2; y++) {
                        c.chest(X(m, alongX, a, b), y, Z(m, alongX, a, b), BARREL, f,
                                new Subject("", Topic.CHEST, Rarity.COMMON, m.gallery(), m.theme(), 0));
                    }
                    L.at(a, F + 3, b, L.fit().topSlab());
                }
            }
        }
        // globes, terrestrial and celestial
        long seed = r.nextLong();
        for (int[] g : new int[][]{{6, 6}, {6, 25}, {25, 6}, {25, 25}}) {
            boolean celestial = r.nextInt(3) == 0;
            int gx = m.ox() + g[0], gz = m.oz() + g[1];
            c.set(gx, F + 1, gz, m.pal().carved);
            c.set(gx, F + 2, gz, L.fit().fence());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx * dx + dy * dy + dz * dz > 2) continue;
                        double n = SimplexNoise.noise3(seed, (gx + dx) * 0.6, dy * 0.6, (gz + dz) * 0.6);
                        Material mt = celestial ? (n > 0.45 ? GOLD_BLOCK : BLUE_CONCRETE)
                                : n > 0.15 ? (n > 0.45 ? LIME_TERRACOTTA : GREEN_TERRACOTTA) : (n < -0.4 ? BLUE_CONCRETE : LIGHT_BLUE_CONCRETE);
                        c.set(gx + dx, F + 4 + dy, gz + dz, mt.createBlockData());
                    }
                }
            }
            BlockFace f = g[1] < 16 ? BlockFace.SOUTH : BlockFace.NORTH;
            c.sign(gx, F + 1, gz + f.getModZ(), f, m.subject(celestial ? "Globe, celestial" : "Globe, terrestrial", Topic.SPECIMEN, Rarity.RARE));
        }
        L.lectern(15, 11, facing(alongX, false));
        L.lectern(16, 20, facing(alongX, true));
    }

    // ── Storerooms ───────────────────────────────────────────────────────

    /**
     * A staff-only storeroom: racks of shelves crammed with whatever didn't fit in the galleries,
     * chests and crates, bookshelves of files, things left on the floor, a desk by the door with
     * the day book on a lectern.
     */
    static void storeroom(Room m, boolean alongX, int doorSide) {
        MuseumCanvas c = m.c();
        Random r = m.r();
        int F = m.F();
        Specimen[] pool = MuseumCatalogue.pool(m.theme());
        BlockData post = STRIPPED_SPRUCE_LOG.createBlockData(), plank = SPRUCE_PLANKS.createBlockData();
        Material[] crates = {BARREL, BARREL, CHEST, COMPOSTER, HAY_BLOCK, BOOKSHELF, BOOKSHELF, SCAFFOLDING, DECORATED_POT, DECORATED_POT};
        for (int b0 : new int[]{6, 12, 18, 24}) {
            for (int a = 7; a <= 27; a++) {
                for (int b = b0; b <= b0 + 1; b++) {
                    int x = X(m, alongX, a, b), z = Z(m, alongX, a, b);
                    boolean upright = (a - 7) % 5 == 0 || a == 27;
                    for (int y = F + 1; y <= F + 9; y++) {
                        int lvl = y - F;
                        if (upright) c.set(x, y, z, post);
                        else if (lvl == 3 || lvl == 6 || lvl == 9) c.set(x, y, z, plank);
                        else if ((lvl == 1 || lvl == 4 || lvl == 7) && r.nextInt(100) < 55) {
                            BlockFace f = facing(alongX, b != b0);
                            if (r.nextInt(3) == 0) {
                                Material cm = crates[r.nextInt(crates.length)];
                                if (cm == CHEST || cm == BARREL) {
                                    c.chest(x, y, z, cm, cm == BARREL ? BlockFace.UP : f,
                                            new Subject("", Topic.CHEST, Rarity.COMMON, m.gallery(), m.theme(), 0));
                                    if (r.nextInt(3) == 0) c.sign(x + f.getModX(), y, z + f.getModZ(), f,
                                            new Subject(MuseumCatalogue.name(pool[r.nextInt(pool.length)]), Topic.CRATE,
                                                    Rarity.COMMON, m.gallery(), m.theme(), 0));
                                } else {
                                    if (cm == DECORATED_POT) c.pot(x, y, z, f, new Subject("", Topic.POTS, Rarity.COMMON, m.gallery(), m.theme(), 0));
                                    else c.set(x, y, z, cm.createBlockData());
                                }
                            } else {
                                Specimen sp = pool[r.nextInt(pool.length)];
                                put(c, x, y, z, sp, f, label(m, sp));
                            }
                        }
                    }
                }
            }
        }
        // things left on the floor of the aisles
        Material[] floor = {BONE_BLOCK, BARREL, FLOWER_POT, WHITE_CARPET, BROWN_CARPET, LANTERN, CHIPPED_ANVIL, HAY_BLOCK,
                CAULDRON, SCAFFOLDING};
        for (int k = 0; k < 10; k++) {
            int a = 4 + r.nextInt(24), b = new int[]{4, 9, 10, 15, 16, 21, 22, 27}[r.nextInt(8)];
            Material fm = floor[r.nextInt(floor.length)];
            BlockData bd = fm.createBlockData();
            if (bd instanceof org.bukkit.block.data.type.Lantern l) l.setHanging(false);
            c.set(X(m, alongX, a, b), F + 1, Z(m, alongX, a, b), bd);
        }
        // dust in the top corners
        for (int[] q : new int[][]{{1, 1}, {30, 1}, {1, 30}, {30, 30}}) {
            if (r.nextBoolean()) c.set(m.ox() + q[0], F + 17, m.oz() + q[1], COBWEB.createBlockData());
        }
        // the desk by the door: a table, a chair, a chest of files, the day book
        int t = 23;
        int dx = sx(m.ox(), doorSide, 3, t), dz = sz(m.oz(), doorSide, 3, t);
        c.set(dx, F + 1, dz, m.pal().wood);
        c.set(sx(m.ox(), doorSide, 3, t + 1), F + 1, sz(m.oz(), doorSide, 3, t + 1), m.pal().wood);
        Stairs chair = (Stairs) SPRUCE_STAIRS.createBlockData();
        chair.setFacing(INTO_ROOM[doorSide]);
        c.set(sx(m.ox(), doorSide, 4, t), F + 1, sz(m.oz(), doorSide, 4, t), chair);
        c.lectern(sx(m.ox(), doorSide, 3, t + 2), F + 1, sz(m.oz(), doorSide, 3, t + 2), INTO_ROOM[doorSide],
                new Subject("", Topic.LOG, Rarity.COMMON, m.gallery(), m.theme(), m.seed()));
        c.chest(sx(m.ox(), doorSide, 1, t), F + 1, sz(m.oz(), doorSide, 1, t), CHEST, INTO_ROOM[doorSide],
                new Subject("", Topic.CHEST, Rarity.COMMON, m.gallery(), m.theme(), 0));
        c.set(sx(m.ox(), doorSide, 1, t + 1), F + 1, sz(m.oz(), doorSide, 1, t + 1), BOOKSHELF.createBlockData());
        c.set(sx(m.ox(), doorSide, 1, t + 1), F + 2, sz(m.oz(), doorSide, 1, t + 1), BOOKSHELF.createBlockData());
    }
}
