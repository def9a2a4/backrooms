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
            case MINERALS -> pick(r, Center.CASES, 3, Center.PEDESTALS, 3, Center.GEODE, 2, Center.CRYSTALS, 2, Center.METEORITE, 1);
            case FOSSILS -> pick(r, Center.SKELETON, 6, Center.HANGING, 3, Center.AMMONITE, 2, Center.CASES, 1);
            case MOBS -> pick(r, Center.SKELETON, 7, Center.HANGING, 2, Center.PEDESTALS, 2);
            case BOTANY -> pick(r, Center.PLANTERS, 3, Center.TREE, 3, Center.MUSHROOM, 2, Center.CASES, 1);
            case MARINE -> pick(r, Center.AQUARIUM, 3, Center.HANGING, 3, Center.AMMONITE, 1, Center.CASES, 1, Center.SKELETON, 1);
            case ARCHAEOLOGY -> pick(r, Center.MONOLITH, 4, Center.POTS, 3, Center.PEDESTALS, 2, Center.CASES, 1);
            case NETHER -> pick(r, Center.SKELETON, 3, Center.HANGING, 2, Center.PEDESTALS, 2, Center.METEORITE, 1, Center.CASES, 1);
            case END -> pick(r, Center.SKELETON, 1, Center.HANGING, 2, Center.PEDESTALS, 3, Center.MONOLITH, 2, Center.CRYSTALS, 2, Center.CASES, 2);
            case CURIOSITIES -> pick(r, Center.PEDESTALS, 4, Center.CASES, 3, Center.BENCHES, 1);
            case ART -> pick(r, Center.PAINTINGS, 6, Center.POTS, 1, Center.MONOLITH, 1);
            case ZOOLOGY -> pick(r, Center.SKELETON, 4, Center.CASES, 2, Center.PEDESTALS, 1, Center.HANGING, 1);
            case GEOLOGY -> pick(r, Center.METEORITE, 2, Center.GEODE, 2, Center.CRYSTALS, 1, Center.MONOLITH, 1, Center.PEDESTALS, 2, Center.CASES, 1);
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
                Specimen s = pool[m.r().nextInt(pool.length)];
                c.set(sx(m.ox(), side, 1, t), m.F() + tier, sz(m.oz(), side, 1, t), specimen(s, INTO_ROOM[side]));
                if (tier == 2) {
                    c.sign(sx(m.ox(), side, 3, t), m.F() + 1, sz(m.oz(), side, 3, t), INTO_ROOM[side],
                            m.subject(MuseumCatalogue.name(s), Topic.SPECIMEN, s.rarity()));
                }
            }
        }
    }

    static BlockData specimen(Specimen s, BlockFace facing) {
        BlockData b = s.block().createBlockData();
        if (b instanceof Rotatable rot) rot.setRotation(facing);
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
                Specimen s = pool[m.r().nextInt(pool.length)];
                BlockFace f = facing(alongX, true);
                m.c().set(cx, m.F() + 2, cz, specimen(s, f));
                m.c().sign(cx + f.getModX() * 2, m.F() + 1, cz + f.getModZ() * 2, f,
                        m.subject(MuseumCatalogue.name(s), Topic.SPECIMEN, s.rarity()));
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
                Specimen s = pool[m.r().nextInt(pool.length)];
                BlockFace f = facing(alongX, b > 16);
                m.c().set(x, m.F() + 3, z, specimen(s, f));
                m.c().sign(x + f.getModX(), m.F() + 1, z + f.getModZ(), f,
                        m.subject(MuseumCatalogue.name(s), Topic.SPECIMEN, s.rarity()));
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

    /** A monolith: obelisk, inscribed stela, menhir, a head, a ring of standing stones, or a totem. */
    static void monolith(Room m, boolean alongX) {
        int cx = m.ox() + 16, cz = m.oz() + 16, y0 = m.F() + 1;
        Random r = m.r();
        String name;
        switch (r.nextInt(6)) {
            case 0 -> {
                BlockData[] st = r.nextBoolean() ? new BlockData[]{QUARTZ_BLOCK.createBlockData(), CHISELED_QUARTZ_BLOCK.createBlockData()}
                        : r.nextBoolean() ? new BlockData[]{SMOOTH_SANDSTONE.createBlockData(), CHISELED_SANDSTONE.createBlockData()}
                        : new BlockData[]{POLISHED_BLACKSTONE.createBlockData(), CHISELED_POLISHED_BLACKSTONE.createBlockData()};
                for (int y = 0; y < 15; y++) {
                    int half = y < 2 ? 2 : y < 13 ? 1 : 0;
                    for (int dx = -half; dx <= half; dx++) {
                        for (int dz = -half; dz <= half; dz++) {
                            m.c().set(cx + dx, y0 + y, cz + dz, y % 3 == 1 && y > 2 && half == 1 ? st[1] : st[0]);
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
                        m.c().set(X(m, alongX, a, 15), y0 + y, Z(m, alongX, a, 15), line ? glyph : stone);
                        m.c().set(X(m, alongX, a, 16), y0 + y, Z(m, alongX, a, 16), stone);
                    }
                }
                name = r.nextBoolean() ? "Stela, inscribed" : "Boundary stone";
            }
            case 2 -> {
                long s = r.nextLong();
                BlockData[] st = {STONE.createBlockData(), MOSSY_COBBLESTONE.createBlockData(), ANDESITE.createBlockData()};
                for (int y = 0; y < 13; y++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            double w = 2.2 - y * 0.08 + 0.5 * SimplexNoise.noise3(s, dx * 0.4, y * 0.2, dz * 0.4);
                            if (Math.sqrt(dx * dx + dz * dz * 2.5) < w) m.c().set(cx + dx, y0 + y, cz + dz, st[Math.floorMod(dx + y + dz, 3)]);
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
                            boolean nose = b == 3 - 1 && a == 0 && y >= 5 && y <= 8;
                            if (y > 10 && Math.abs(a) > 1) continue;
                            m.c().set(X(m, alongX, 16 + a, 16 + b), y0 + y, Z(m, alongX, 16 + a, 16 + b), y == 9 && b == 2 ? dark : tuff);
                            if (nose) m.c().set(X(m, alongX, 16 + a, 16 + b + 1), y0 + y, Z(m, alongX, 16 + a, 16 + b + 1), tuff);
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
                name = "Stone circle, model";
            }
            default -> {
                Material[] bands = {RED_TERRACOTTA, BLACK_TERRACOTTA, WHITE_TERRACOTTA, CYAN_TERRACOTTA, YELLOW_TERRACOTTA};
                for (int y = 0; y < 14; y++) {
                    BlockData b = y % 3 == 2 ? bands[Math.floorMod(y * 7 + cx, bands.length)].createBlockData() : STRIPPED_SPRUCE_LOG.createBlockData();
                    m.c().set(cx, y0 + y, cz, b);
                    m.c().set(cx + 1, y0 + y, cz, b);
                    m.c().set(cx, y0 + y, cz + 1, b);
                    m.c().set(cx + 1, y0 + y, cz + 1, b);
                }
                for (int d = -3; d <= 4; d++) {
                    m.c().set(X(m, alongX, 16 + d, 16), y0 + 11, Z(m, alongX, 16 + d, 16), SPRUCE_PLANKS.createBlockData());
                }
                name = "Totem pole";
            }
        }
        BlockFace f = facing(alongX, true);
        m.c().sign(X(m, alongX, 15, 24), y0, Z(m, alongX, 15, 24), f, m.subject(name, Topic.MONOLITH, Rarity.RARE));
        m.c().set(X(m, alongX, 15, 25), y0, Z(m, alongX, 15, 25), m.pal().trim);
    }

    /** Decorated pots on stepped plinths, or a giant amphora. */
    static void pots(Room m, boolean alongX) {
        if (m.r().nextInt(3) == 0) {
            int cx = m.ox() + 16, cz = m.oz() + 16, y0 = m.F() + 1;
            double[] prof = {1.2, 2.5, 3.6, 4.2, 4.4, 4.2, 3.8, 3.2, 2.4, 1.6, 1.3, 1.3, 1.8, 2.2};
            BlockData body = (m.r().nextBoolean() ? TERRACOTTA : ORANGE_TERRACOTTA).createBlockData(), band = BLACK_TERRACOTTA.createBlockData();
            for (int y = 0; y < prof.length; y++) {
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        double d = Math.sqrt((dx + 0.5) * (dx + 0.5) + (dz + 0.5) * (dz + 0.5));
                        if (d <= prof[y] && (d > prof[y] - 1.3 || y == 0)) m.c().set(cx + dx, y0 + y, cz + dz, y == 4 || y == 5 ? band : body);
                    }
                }
            }
            m.c().sign(X(m, alongX, 15, 22), y0, Z(m, alongX, 15, 22), facing(alongX, true), m.subject("Amphora, reconstructed", Topic.POTS, Rarity.RARE));
            m.c().set(X(m, alongX, 15, 23), y0, Z(m, alongX, 15, 23), m.pal().trim);
            return;
        }
        for (int a = 8; a <= 23; a++) {
            for (int b = 12; b <= 19; b++) {
                int step = Math.min(Math.min(a - 8, 23 - a), Math.min(b - 12, 19 - b));
                int h = Math.min(3, step + 1);
                for (int y = 1; y <= h; y++) m.c().set(X(m, alongX, a, b), m.F() + y, Z(m, alongX, a, b), y == h ? m.pal().daisTop : m.pal().dais);
                if (step < 3 && (a + b) % 2 == 0 && step % 1 == 0 && m.r().nextInt(3) > 0) {
                    m.c().pot(X(m, alongX, a, b), m.F() + h + 1, Z(m, alongX, a, b), facing(alongX, true),
                            m.subject("Pot", Topic.POTS, Rarity.COMMON));
                }
            }
        }
        m.c().sign(X(m, alongX, 15, 20), m.F() + 1, Z(m, alongX, 15, 20), facing(alongX, true),
                m.subject("Pots, decorated", Topic.POTS, Rarity.UNCOMMON));
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
}
