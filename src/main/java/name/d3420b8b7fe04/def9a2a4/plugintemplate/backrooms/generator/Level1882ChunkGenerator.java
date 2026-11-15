package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumCanvas.Label;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumCanvas.Subject;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumCatalogue.Rarity;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumCatalogue.Specimen;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumCatalogue.Theme;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumCatalogue.Topic;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumExhibits.Center;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumExhibits.Room;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.MuseumSkeletons.Body;
import net.kyori.adventure.text.Component;
import org.bukkit.Art;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.DecoratedPot;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Painting;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Level 1882 — "The Collections". Hintze Hall's museum gone on forever: two floors of galleries
 * joined by tall arches, display cases along the walls, skeletons, minerals, plants, monoliths,
 * pots, paintings and aquariums, every exhibit labelled, and every so often a great vaulted hall
 * with a balcony, a grand staircase and a giant skeleton.
 *
 * <p>Built like Alpha's megastructures ({@link AlphaMegastructures}): everything is hashed from the
 * world seed and grid coordinates, so every chunk works out its own slice and nothing is stored.
 * The world is a grid of 32-block rooms (two chunks a side), grouped into districts of 8×8 rooms.
 * Each district has its own architecture ({@link MuseumPalette}: Waterhouse terracotta, marble,
 * brick, Gothic, mud brick and more), a few favourite subjects, one central hall (2×4 rooms, both
 * floors, under a pointed vault), and on each floor a maze through its other rooms: a random
 * spanning tree, so every room can be reached, plus a few extra openings. Neighbouring districts
 * are joined by two doors in each shared wall, picked from a hash of the wall alone. Two rooms per
 * district hold a staircase between the floors; the hall's grand staircase climbs to its balcony.
 *
 * <p>Each room has its own subject ({@link Theme}), floor pattern, wall finish and ceiling, and
 * its exhibits come from {@link MuseumExhibits} and {@link MuseumSkeletons}. Signs, lectern books,
 * pot sherds and paintings can't be set from ChunkData; {@link #applyBlockEntities} fills them in
 * when the chunk first loads (Level1882Listener), from the same labels the generator placed.
 */
public class Level1882ChunkGenerator extends BackroomsChunkGenerator {

    static final int M = 32;                        // room side
    static final int D = 8;                         // rooms per district side
    static final int FLOOR0 = 64;                   // ground floor's floor block
    static final int STORY = 20;                    // floor block to floor block
    static final int FLOORS = 2;
    static final int ROOF = FLOOR0 + STORY * FLOORS;    // 104
    static final int FOUNDATION = FLOOR0 - 4;
    static final int BAL = 6;                       // hall balcony depth
    static final int VAULT_R = 40;                  // radius of the hall vault's arcs
    static final int FLIGHT = 24;                   // the grand staircase's run

    // wall between two rooms
    static final byte CLOSED = 0, DOOR = 1, ARCADE = 2, OPEN = 3;   // OPEN: both sides are the hall

    private static final int PLAN_SALT = 0x1882, BORDER_SALT = 0xB0D3, LABEL_SALT = 0x1AB;

    private static final BlockData FOUND = Material.POLISHED_ANDESITE.createBlockData();
    private static final BlockData GLASS = Material.GLASS.createBlockData();
    private static final BlockData LIGHT = Material.LIGHT.createBlockData();
    private static final BlockData AIR = Material.AIR.createBlockData();
    private static final BlockData CHAIN = MuseumSkeletons.CHAIN;

    public Level1882ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    // ── District plan ─────────────────────────────────────────────────────

    /** One district's layout: its look, its hall, staircase rooms and the walls between its rooms. */
    private static final class Plan {
        final int ddx, ddz;
        final boolean[] hall = new boolean[D * D], stair = new boolean[D * D];
        // [floor][room]: the wall on the room's west / north side
        final byte[][] west = new byte[FLOORS][D * D], north = new byte[FLOORS][D * D];
        final long[] roomSeed = new long[D * D];
        MuseumPalette pal;
        Theme[] themes;         // the district's favourite subjects
        boolean alongX;         // the hall's long axis
        int hmx, hmz, sizeX, sizeZ;
        int stairEnd;           // 0: the grand staircase is at the hall's low end, 1: the high end
        int skeleton;           // 0 whale, 1 sauropod, 2 dragon, 3 theropod, 4 mammoth
        long hallSeed;

        Plan(int ddx, int ddz) {
            this.ddx = ddx;
            this.ddz = ddz;
        }

        boolean contains(int gmx, int gmz) {
            return Math.floorDiv(gmx, D) == ddx && Math.floorDiv(gmz, D) == ddz;
        }

        // hall bounds in blocks: the wall belts are at x0/x1 (and the next block out); inside is x0+1..x1-1
        int hx0() { return (ddx * D + hmx) * M; }
        int hx1() { return hx0() + sizeX * M - 1; }
        int hz0() { return (ddz * D + hmz) * M; }
        int hz1() { return hz0() + sizeZ * M - 1; }
    }

    private static int idx(int dx, int dz) {
        return dx * D + dz;
    }

    /** Subjects that suit an architecture, to weight a district's choice. */
    private static Theme[] suits(MuseumPalette p) {
        return switch (p.name) {
            case "prismarine" -> new Theme[]{Theme.MARINE, Theme.MARINE, Theme.FOSSILS};
            case "blackstone" -> new Theme[]{Theme.NETHER, Theme.NETHER, Theme.MOBS};
            case "end stone" -> new Theme[]{Theme.END, Theme.END, Theme.CURIOSITIES};
            case "calcite" -> new Theme[]{Theme.GEOLOGY, Theme.MINERALS, Theme.FOSSILS};
            case "library" -> new Theme[]{Theme.ART, Theme.CURIOSITIES, Theme.BOTANY};
            case "mudbrick", "red sandstone" -> new Theme[]{Theme.ARCHAEOLOGY, Theme.ART, Theme.MINERALS};
            case "copper" -> new Theme[]{Theme.CURIOSITIES, Theme.MINERALS, Theme.ART};
            case "gothic" -> new Theme[]{Theme.FOSSILS, Theme.MOBS, Theme.GEOLOGY};
            default -> null;
        };
    }

    private static Plan plan(long seed, int ddx, int ddz) {
        Random r = new Random(AlphaMegastructures.hash(ddx, ddz, seed, PLAN_SALT));
        Plan p = new Plan(ddx, ddz);
        p.pal = ddx == 0 && ddz == 0 ? MuseumPalette.ALL[0] : MuseumPalette.pick(r.nextLong());
        Theme[] all = Theme.values(), suit = suits(p.pal);
        p.themes = new Theme[3];
        for (int i = 0; i < 3; i++) p.themes[i] = suit != null && r.nextInt(3) > 0 ? suit[i] : all[r.nextInt(all.length)];
        p.alongX = r.nextBoolean();
        p.sizeX = p.alongX ? 4 : 2;
        p.sizeZ = p.alongX ? 2 : 4;
        // a ring of rooms all round, so the hall never touches the district's edge
        p.hmx = 1 + r.nextInt(D - 1 - p.sizeX);
        p.hmz = 1 + r.nextInt(D - 1 - p.sizeZ);
        for (int dx = p.hmx; dx < p.hmx + p.sizeX; dx++) {
            for (int dz = p.hmz; dz < p.hmz + p.sizeZ; dz++) p.hall[idx(dx, dz)] = true;
        }
        p.stairEnd = r.nextInt(2);
        p.skeleton = r.nextInt(5);
        p.hallSeed = r.nextLong();
        // staircase rooms: never in the district's north row (their north wall must stay shut, and
        // that row's north walls belong to the border)
        for (int n = 0, tries = 0; n < 2 && tries < 64; tries++) {
            int i = idx(r.nextInt(D), 1 + r.nextInt(D - 1));
            if (!p.hall[i] && !p.stair[i]) {
                p.stair[i] = true;
                n++;
            }
        }
        for (int i = 0; i < D * D; i++) p.roomSeed[i] = r.nextLong();
        for (int f = 0; f < FLOORS; f++) maze(p, f, r);
        return p;
    }

    /** Randomized Kruskal over the district's rooms (the hall is one room), plus a few loops. */
    private static void maze(Plan p, int f, Random r) {
        int[] parent = new int[D * D];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        int hallRoot = idx(p.hmx, p.hmz);
        for (int i = 0; i < D * D; i++) if (p.hall[i]) parent[i] = hallRoot;

        List<int[]> edges = new ArrayList<>();      // {0 west / 1 north, room}
        for (int dx = 0; dx < D; dx++) {
            for (int dz = 0; dz < D; dz++) {
                int b = idx(dx, dz);
                if (dx > 0) {
                    int a = idx(dx - 1, dz);
                    if (p.hall[a] && p.hall[b]) p.west[f][b] = OPEN;
                    else edges.add(new int[]{0, b});
                }
                if (dz > 0) {
                    int a = idx(dx, dz - 1);
                    if (p.hall[a] && p.hall[b]) p.north[f][b] = OPEN;
                    else if (!p.stair[b]) edges.add(new int[]{1, b});   // the stairs run along it
                }
            }
        }
        Collections.shuffle(edges, r);
        for (int[] e : edges) {
            int b = e[1], a = e[0] == 0 ? b - D : b - 1;
            boolean hallEdge = p.hall[a] || p.hall[b];
            int ra = find(parent, a), rb = find(parent, b);
            boolean open;
            if (ra != rb) {
                parent[ra] = rb;
                open = true;
            } else {
                open = r.nextDouble() < (hallEdge ? 0.4 : 0.12);
            }
            if (!open) continue;
            byte type = hallEdge ? (f == 0 ? ARCADE : DOOR) : (r.nextDouble() < 0.25 ? ARCADE : DOOR);
            (e[0] == 0 ? p.west : p.north)[f][b] = type;
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** A wall on a district's border: two doors in it, from a hash of the wall alone. */
    private static byte border(long seed, int f, int ddx, int ddz, int along, int axis) {
        long h = AlphaMegastructures.hash(ddx, ddz, seed, BORDER_SALT + f * 16 + axis);
        int a = (int) Math.floorMod(h, (long) D), b = (int) Math.floorMod(h >>> 20, (long) D);
        return along == a || along == b ? DOOR : CLOSED;
    }

    /** The wall on the west side of room (gmx, gmz). */
    private static byte westEdge(long seed, Plan p, int f, int gmx, int gmz) {
        int ldx = Math.floorMod(gmx, D), ldz = Math.floorMod(gmz, D);
        if (ldx == 0) return border(seed, f, Math.floorDiv(gmx, D), Math.floorDiv(gmz, D), ldz, 1);
        Plan q = p.contains(gmx, gmz) ? p : plan(seed, Math.floorDiv(gmx, D), Math.floorDiv(gmz, D));
        return q.west[f][idx(ldx, ldz)];
    }

    /** The wall on the north side of room (gmx, gmz). */
    private static byte northEdge(long seed, Plan p, int f, int gmx, int gmz) {
        int ldx = Math.floorMod(gmx, D), ldz = Math.floorMod(gmz, D);
        if (ldz == 0) return border(seed, f, Math.floorDiv(gmx, D), Math.floorDiv(gmz, D), ldx, 2);
        Plan q = p.contains(gmx, gmz) ? p : plan(seed, Math.floorDiv(gmx, D), Math.floorDiv(gmz, D));
        return q.north[f][idx(ldx, ldz)];
    }

    private static boolean isHall(Plan p, int gmx, int gmz) {
        return p.contains(gmx, gmz) && p.hall[idx(Math.floorMod(gmx, D), Math.floorMod(gmz, D))];
    }

    /** A room's look on one floor: its subject, floor pattern (0-6), wall finish (0-3), ceiling (0-3), cases (0-2). */
    private record Style(Theme theme, int floor, int wall, int ceil, int cases, int gallery, long seed) {}

    private static Style style(Plan p, int i, int f) {
        long seed = p.roomSeed[i] ^ (f + 1) * 0x9E3779B97F4A7C15L;
        Random r = new Random(seed);
        Theme[] all = Theme.values();
        Theme t = r.nextInt(100) < 65 ? p.themes[r.nextInt(p.themes.length)] : all[r.nextInt(all.length)];
        return new Style(t, r.nextInt(7), r.nextInt(4), r.nextInt(4), r.nextInt(3), MuseumCatalogue.gallery(seed), seed);
    }

    // ── Chunk ────────────────────────────────────────────────────────────

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        generate(new MuseumCanvas(data, chunkX * 16, chunkZ * 16), worldInfo.getSeed(), chunkX, chunkZ);
    }

    /** The labels (signs, lecterns, pots, paintings) the generator placed in this chunk. */
    public List<Label> labels(long seed, int chunkX, int chunkZ) {
        MuseumCanvas c = new MuseumCanvas(null, chunkX * 16, chunkZ * 16);
        generate(c, seed, chunkX, chunkZ);
        return c.labels;
    }

    private void generate(MuseumCanvas c, long seed, int chunkX, int chunkZ) {
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        int gmx = Math.floorDiv(x0, M), gmz = Math.floorDiv(z0, M);
        Plan p = plan(seed, Math.floorDiv(gmx, D), Math.floorDiv(gmz, D));
        c.palette = p.pal;

        // this room's four walls on each floor: west, east, north, south
        byte[][] e = new byte[FLOORS][4];
        for (int f = 0; f < FLOORS; f++) {
            e[f][0] = westEdge(seed, p, f, gmx, gmz);
            e[f][1] = westEdge(seed, p, f, gmx + 1, gmz);
            e[f][2] = northEdge(seed, p, f, gmx, gmz);
            e[f][3] = northEdge(seed, p, f, gmx, gmz + 1);
        }
        boolean hall = isHall(p, gmx, gmz);
        int i = idx(Math.floorMod(gmx, D), Math.floorMod(gmz, D));
        Style[] st = {style(p, i, 0), style(p, i, 1)};

        if (c.data != null) {
            for (int x = x0; x < x0 + 16; x++) {
                for (int z = z0; z < z0 + 16; z++) column(c, p, x, z, gmx, gmz, e, hall, st);
            }
        }
        if (hall) {
            hallInterior(c, p);
        } else {
            for (int f = 0; f < FLOORS; f++) room(c, p, gmx, gmz, f, e[f], st[f]);
        }
        // the vault and gables stand on the hall's wall belts too, which are in the rooms around it
        if (x0 + 15 >= p.hx0() - 1 && x0 <= p.hx1() + 1 && z0 + 15 >= p.hz0() - 1 && z0 <= p.hz1() + 1) {
            if (c.data != null) hallRoof(c, p);
            if (hall) hallSkeleton(c, p);
        }
    }

    /** Foundation, walls with their arches, floors and ceilings of one column. */
    private void column(MuseumCanvas c, Plan p, int x, int z, int gmx, int gmz, byte[][] e, boolean hall, Style[] st) {
        MuseumPalette pal = p.pal;
        int mlx = Math.floorMod(x, M), mlz = Math.floorMod(z, M);
        for (int y = FOUNDATION; y < FLOOR0; y++) c.set(x, y, z, FOUND);

        boolean nearX = mlx <= 1 || mlx >= M - 2, nearZ = mlz <= 1 || mlz >= M - 2;
        if (nearX && nearZ) {
            int cmx = mlx <= 1 ? gmx : gmx + 1, cmz = mlz <= 1 ? gmz : gmz + 1;
            boolean inside = isHall(p, cmx - 1, cmz - 1) && isHall(p, cmx, cmz - 1)
                    && isHall(p, cmx - 1, cmz) && isHall(p, cmx, cmz);
            if (!inside) {
                pier(c, pal, x, z);
                return;
            }
        } else if (mlx == 0 || mlx == M - 1 || mlz == 0 || mlz == M - 1) {
            int side = mlx == 0 ? 0 : mlx == M - 1 ? 1 : mlz == 0 ? 2 : 3;
            int t = side < 2 ? mlz : mlx;
            if (e[0][side] != OPEN) {
                for (int f = 0; f < FLOORS; f++) wall(c, pal, x, z, f, t, e[f][side], hall ? 0 : st[f].wall());
                c.set(x, ROOF, z, pal.roof);
                return;
            }
        }
        if (hall) {
            hallColumn(c, p, x, z);
            return;
        }
        for (int f = 0; f < FLOORS; f++) {
            int F = FLOOR0 + f * STORY, C = F + STORY - 2;
            c.set(x, F, z, floorTile(pal, mlx, mlz, st[f].floor()));
            int dx = Math.min(mlx - 1, M - 2 - mlx), dz = Math.min(mlz - 1, M - 2 - mlz);
            int ceil = st[f].ceil() == 3 && f < FLOORS - 1 ? 0 : st[f].ceil();
            if (ceil == 3 && dx >= 7 && dz >= 7) {
                // a skylight through the roof
                boolean bar = mlx % 4 == 0 || mlz % 4 == 0;
                c.set(x, C, z, bar ? pal.trim : GLASS);
                c.set(x, ROOF, z, GLASS);
                continue;
            }
            boolean beam = ceil == 0 ? dx % 5 == 4 || dz % 5 == 4 : ceil == 1 && dx % 5 == 4;
            double rc = Math.hypot(mlx - 15.5, mlz - 15.5);
            BlockData cb = beam ? pal.trim
                    : ceil == 0 && dx % 5 == 2 && dz % 5 == 2 ? pal.band
                    : ceil == 2 && rc >= 3 && rc < 4.5 ? pal.band
                    : ceil == 2 && rc < 1.5 ? pal.carved
                    : ceil == 3 && (dx == 6 || dz == 6) && dx >= 6 && dz >= 6 ? pal.trim : pal.wall;
            c.set(x, C, z, cb);
            if (beam) c.set(x, C - 1, z, pal.trim);
            c.set(x, C + 1, z, pal.wall);
        }
        c.set(x, ROOF, z, pal.roof);
    }

    private static BlockData floorTile(MuseumPalette pal, int mlx, int mlz, int style) {
        int d = Math.min(Math.min(mlx - 1, M - 2 - mlx), Math.min(mlz - 1, M - 2 - mlz));
        if (d <= 1) return pal.edge;
        if (d == 2) return pal.trim;
        int k = switch (style) {
            case 0 -> (mlx >> 1) + (mlz >> 1);
            case 1 -> mlx + mlz;
            case 2 -> mlx >> 1;
            case 3 -> (int) (Math.abs(mlx - 15.5) + Math.abs(mlz - 15.5)) >> 1;
            case 4 -> d == 4 ? 1 : 0;
            case 5 -> d >> 1;
            default -> mlx / 3 + mlz / 3;
        };
        return (k & 1) == 0 ? pal.tileA : pal.tileB;
    }

    /** A wall course {@code r} blocks above its story's floor: 0 banded, 1 wainscot, 2 plain, 3 dado. */
    private static BlockData course(MuseumPalette pal, int r, int t, int style) {
        return switch (style) {
            case 1 -> switch (r) {
                case 0, 4, 16 -> pal.trim;
                case 1, 2, 3 -> pal.wood;
                case 15 -> (t & 1) == 0 ? pal.carved : pal.trim;
                default -> pal.wall;
            };
            case 2 -> r <= 1 || r == 16 ? pal.trim : pal.wall;
            case 3 -> switch (r) {
                case 0, 3, 16 -> pal.trim;
                case 1, 2, 14 -> pal.band;
                case 15 -> pal.accent;
                default -> pal.wall;
            };
            default -> switch (r) {
                case 0, 1, 2, 16 -> pal.trim;
                case 3, 8, 13 -> pal.band;
                case 15 -> (t & 1) == 0 ? pal.carved : pal.trim;
                default -> pal.wall;
            };
        };
    }

    private void pier(MuseumCanvas c, MuseumPalette pal, int x, int z) {
        for (int f = 0; f < FLOORS; f++) {
            int F = FLOOR0 + f * STORY;
            for (int r = 0; r < STORY; r++) {
                c.set(x, F + r, z, r == 14 || r == 15 ? pal.carved : r == 3 || r == 8 || r == 13 ? pal.band : pal.trim);
            }
        }
        c.set(x, ROOF, z, pal.roof);
    }

    /** One story of a wall column, {@code t} blocks along the wall, with its arch if it has one. */
    private void wall(MuseumCanvas c, MuseumPalette pal, int x, int z, int f, int t, byte type, int style) {
        int F = FLOOR0 + f * STORY;
        for (int r = 0; r < STORY; r++) {
            int a = type == CLOSED ? 0 : arch(pal.arch, type, t, r);
            if (a == 1) continue;   // open
            BlockData b = a == 2 ? pal.trim : a == 3 ? pal.accent : course(pal, r, t, style);
            c.set(x, F + r, z, b);
        }
    }

    /**
     * 0 wall, 1 opening, 2 jamb or light voussoir, 3 dark voussoir. A door is 8 wide and about 13
     * tall; an arcade 24 wide and about 15 tall. Round, pointed or square-headed, by the palette.
     */
    static int arch(MuseumPalette.Arch shape, byte type, int t, int r) {
        if (r <= 0) return 0;
        double tc = t + 0.5 - M / 2.0;
        double hw = type == ARCADE ? 12 : 4, vr = type == ARCADE ? 7 : 4;
        int spring = type == ARCADE ? 8 : 9;
        double ax = Math.abs(tc);
        if (r <= spring) return ax < hw ? 1 : ax < hw + 1 ? 2 : 0;
        double ry = r - spring - 0.5;
        switch (shape) {
            case SQUARE -> {
                if (ry < vr - 1 && ax < hw) return 1;
                return ry < vr && ax < hw + 1 ? 2 : 0;
            }
            case POINTED -> {
                double R = 1.6 * hw, yy = ry * 1.48 * hw / vr;
                double inner = Math.sqrt(Math.max(0, R * R - yy * yy)) - (R - hw);
                if (yy < R && ax < inner) return 1;
                double R2 = R + 1.3, outer = Math.sqrt(Math.max(0, R2 * R2 - yy * yy)) - (R - hw);
                if (yy >= R2 || ax >= outer) return 0;
                return ((int) (yy / 2) & 1) == 0 ? 2 : 3;
            }
            default -> {
                double e = (ax / hw) * (ax / hw) + (ry / vr) * (ry / vr);
                if (e < 1) return 1;
                double ex = ax / (hw + 1.3), ey = ry / (vr + 1.3);
                if (ex * ex + ey * ey >= 1) return 0;
                int sector = (int) (Math.atan2(ry / vr, tc / hw) / (Math.PI / 9));
                return (sector & 1) == 0 ? 2 : 3;
            }
        }
    }

    // ── Rooms ────────────────────────────────────────────────────────────

    private void room(MuseumCanvas c, Plan p, int gmx, int gmz, int f, byte[] e, Style st) {
        int i = idx(Math.floorMod(gmx, D), Math.floorMod(gmz, D));
        int ox = gmx * M, oz = gmz * M, F = FLOOR0 + f * STORY;
        Random r = new Random(st.seed() ^ 0x5EEDL);
        Room m = new Room(c, r, ox, oz, F, p.pal, st.theme(), st.gallery(), e, f == FLOORS - 1, st.seed());

        // even light from hidden light blocks
        for (int lx : new int[]{4, 11, 20, 27}) {
            for (int lz : new int[]{4, 11, 20, 27}) c.set(ox + lx, F + STORY - 4, oz + lz, LIGHT);
        }

        if (p.stair[i]) {
            if (f == 0) stairFlight(c, p.pal, ox, oz);
            else stairWell(c, p.pal, ox, oz, F);
            if (e[3] == CLOSED) MuseumExhibits.wallCase(m, 3, 3, M - 4, st.cases());
            chandelier(c, p.pal, ox + 15, F + STORY - 3, oz + 20);
            titles(m, st);
            return;
        }

        Center ctr = MuseumExhibits.center(st.theme(), r);
        boolean alongX = r.nextBoolean();
        int feature = -1;   // the wall the ammonite hangs on
        if (ctr == Center.AMMONITE) {
            int start = r.nextInt(4);
            for (int s = 0; s < 4 && feature < 0; s++) if (e[(start + s) % 4] == CLOSED) feature = (start + s) % 4;
            if (feature < 0) ctr = Center.CASES;
        }
        if (ctr != Center.PAINTINGS) {
            for (int s = 0; s < 4; s++) {
                if (s == feature) continue;
                if (e[s] == CLOSED) MuseumExhibits.wallCase(m, s, 3, M - 4, st.cases());
                else if (e[s] == DOOR) {
                    MuseumExhibits.wallCase(m, s, 3, 9, st.cases());
                    MuseumExhibits.wallCase(m, s, 22, M - 4, st.cases());
                }
            }
        }
        titles(m, st);

        switch (ctr) {
            case SKELETON -> MuseumExhibits.skeleton(m, alongX, MuseumExhibits.skeleton(st.theme(), r, false));
            case HANGING -> MuseumExhibits.hanging(m, alongX, MuseumExhibits.skeleton(st.theme(), r, true));
            case CASES -> MuseumExhibits.islandCases(m, alongX);
            case PEDESTALS -> MuseumExhibits.pedestals(m, alongX);
            case GEODE -> MuseumExhibits.geode(m, alongX);
            case CRYSTALS -> MuseumExhibits.crystals(m, alongX);
            case METEORITE -> MuseumExhibits.meteorite(m, alongX);
            case PLANTERS -> MuseumExhibits.planters(m, alongX);
            case TREE -> MuseumExhibits.tree(m, alongX);
            case MUSHROOM -> MuseumExhibits.mushroom(m, alongX);
            case AQUARIUM -> MuseumExhibits.aquarium(m, alongX);
            case MONOLITH -> MuseumExhibits.monolith(m, alongX);
            case POTS -> MuseumExhibits.pots(m, alongX);
            case PAINTINGS -> MuseumExhibits.paintings(m);
            case AMMONITE -> {
                MuseumExhibits.ammonite(m, feature);
                MuseumExhibits.benchRows(m, feature >= 2, 13, 18, true);
            }
            default -> MuseumExhibits.benchRows(m, alongX, 15, 16, false);
        }
        if (MuseumExhibits.compact(ctr) && r.nextInt(3) == 0) MuseumExhibits.columns(m, F + STORY - 2);

        boolean tall = switch (ctr) {
            case HANGING, TREE, MONOLITH, CRYSTALS, MUSHROOM -> true;
            default -> false;
        };
        if (tall) {
            for (int[] o : new int[][]{{8, 8}, {8, 23}, {23, 8}, {23, 23}}) {
                c.set(ox + o[0], F + STORY - 3, oz + o[1], CHAIN);
                c.set(ox + o[0], F + STORY - 4, oz + o[1], p.pal.lantern);
            }
        } else {
            chandelier(c, p.pal, ox + 15, F + STORY - 3, oz + 15);
        }
        if (r.nextInt(5) < 3) {
            for (int s = 0; s < 4; s++) {
                if (e[s] != ARCADE && s != feature) {
                    MuseumExhibits.lectern(m, s, 10);
                    break;
                }
            }
        }
    }

    /** The gallery's number and subject over each door, inside. */
    private static void titles(Room m, Style st) {
        for (int s = 0; s < 4; s++) {
            if (m.e()[s] != DOOR) continue;
            int x = MuseumExhibits.sx(m.ox(), s, 1, 15), z = MuseumExhibits.sz(m.oz(), s, 1, 15);
            m.c().sign(x, m.F() + 14, z, MuseumExhibits.INTO_ROOM[s],
                    new Subject("", Topic.ROOM, Rarity.COMMON, st.gallery(), st.theme(), st.seed()));
        }
    }

    private static void chandelier(MuseumCanvas c, MuseumPalette pal, int x, int y, int z) {
        c.set(x, y, z, CHAIN);
        c.set(x, y - 1, z, CHAIN);
        c.set(x, y - 2, z, pal.lantern);
        for (int[] o : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            c.set(x + o[0], y, z + o[1], CHAIN);
            c.set(x + o[0], y - 1, z + o[1], pal.lantern);
        }
    }

    /** Staircase room, ground floor: a flight along the north wall up through the ceiling. */
    private void stairFlight(MuseumCanvas c, MuseumPalette pal, int ox, int oz) {
        int F1 = FLOOR0 + STORY;
        for (int lx = 3; lx <= 23; lx++) {
            for (int lz = 1; lz <= 6; lz++) {
                for (int y = F1 - 3; y <= F1; y++) c.set(ox + lx, y, oz + lz, AIR);
            }
        }
        Stairs st = (Stairs) pal.stairs.createBlockData();
        st.setFacing(BlockFace.EAST);
        for (int k = 0; k < STORY; k++) {
            int top = FLOOR0 + 1 + k, x = ox + 4 + k;
            for (int lz = 2; lz <= 5; lz++) {
                for (int y = FLOOR0 + 1; y < top; y++) c.set(x, y, oz + lz, pal.trim);
                c.set(x, top, oz + lz, k == STORY - 1 ? pal.wall : st);
            }
            // the outer side: a panelled stringer with a parapet
            for (int y = FLOOR0 + 1; y <= top + 1; y++) c.set(x, y, oz + 6, y == top + 1 ? pal.trim : (y - FLOOR0) % 5 == 0 ? pal.band : pal.wall);
        }
    }

    /** Staircase room, upper floor: a parapet round the stairwell. */
    private void stairWell(MuseumCanvas c, MuseumPalette pal, int ox, int oz, int F) {
        for (int lx = 2; lx <= 23; lx++) c.set(ox + lx, F + 1, oz + 7, pal.trim);
        for (int lz = 1; lz <= 6; lz++) c.set(ox + 2, F + 1, oz + lz, pal.trim);
    }

    // ── The hall ─────────────────────────────────────────────────────────

    /** Along/across coordinates of a column in the hall, measured inside its walls. */
    private record HallPos(int along, int aLow, int aHigh, int dWall, double o) {}

    private static HallPos hallPos(Plan p, int x, int z) {
        int ix0 = p.hx0() + 1, ix1 = p.hx1() - 1, iz0 = p.hz0() + 1, iz1 = p.hz1() - 1;
        int dWall = Math.min(Math.min(x - ix0, ix1 - x), Math.min(z - iz0, iz1 - z));
        int along = p.alongX ? x : z, a0 = p.alongX ? ix0 : iz0, a1 = p.alongX ? ix1 : iz1;
        int across = p.alongX ? z : x, b0 = p.alongX ? iz0 : ix0, b1 = p.alongX ? iz1 : ix1;
        double o = across + 0.5 - (b0 + b1 + 1) / 2.0;   // from the hall's center line
        return new HallPos(along, along - a0, a1 - along, dWall, o);
    }

    /** Distance (blocks) of the column from the end with the grand staircase. */
    private static int fromStairEnd(Plan p, HallPos h) {
        return p.stairEnd == 0 ? h.aLow() : h.aHigh();
    }

    /** World x/z of the point {@code fromEnd} blocks from the staircase end, {@code o} off the center line. */
    private static int[] hallPoint(Plan p, int fromEnd, int o) {
        int ix0 = p.hx0() + 1, ix1 = p.hx1() - 1, iz0 = p.hz0() + 1, iz1 = p.hz1() - 1;
        int along = p.stairEnd == 0 ? (p.alongX ? ix0 : iz0) + fromEnd : (p.alongX ? ix1 : iz1) - fromEnd;
        int mid = p.alongX ? (iz0 + iz1 + 1) / 2 : (ix0 + ix1 + 1) / 2;
        return p.alongX ? new int[]{along, mid + o} : new int[]{mid + o, along};
    }

    /** The direction from the staircase toward the far end of the hall. */
    private static BlockFace downHall(Plan p) {
        BlockFace up = p.alongX ? BlockFace.EAST : BlockFace.SOUTH;
        return p.stairEnd == 0 ? up : up.getOppositeFace();
    }

    /** Height of step {@code k} of the grand staircase (k = 0 at the foot); a landing halfway. */
    private static int stepRise(int k) {
        if (k < 10) return k;
        if (k < 15) return 10;
        return Math.min(k - 4, STORY - 1);
    }

    private void hallColumn(MuseumCanvas c, Plan p, int x, int z) {
        MuseumPalette pal = p.pal;
        HallPos h = hallPos(p, x, z);
        int F1 = FLOOR0 + STORY;
        double ao = Math.abs(h.o());
        int fromEnd = fromStairEnd(p, h);
        boolean stairGap = fromEnd < BAL && ao < 6;

        // floor: tiles with a runner down the middle
        c.set(x, FLOOR0, z, ao < 3 ? pal.edge : ao < 4 ? pal.trim
                : h.dWall() <= 1 ? pal.edge : ((x >> 1) + (z >> 1) & 1) == 0 ? pal.tileA : pal.tileB);

        if (h.dWall() < BAL) {
            // the balcony round the hall, on an arcade; open where the staircase arrives
            c.set(x, F1 - 2, z, pal.trim);
            c.set(x, F1 - 1, z, pal.wall);
            c.set(x, F1, z, h.dWall() == 0 ? pal.edge : pal.tileA);
            if (h.dWall() == BAL - 1 && !stairGap) c.set(x, F1 + 1, z, pal.trim);
            if (h.dWall() >= BAL - 2 && !stairGap) {
                // along the edge: piers every 8 blocks, round arches between
                int dxw = Math.min(x - p.hx0() - 1, p.hx1() - 1 - x), dzw = Math.min(z - p.hz0() - 1, p.hz1() - 1 - z);
                int m = Math.floorMod(dxw <= dzw ? z : x, 8);
                if (m <= 1) {
                    for (int y = FLOOR0 + 1; y < F1 - 2; y++) c.set(x, y, z, course(pal, y - FLOOR0, 0, 0));
                } else {
                    double sc = m + 0.5 - 5.0;
                    for (int y = FLOOR0 + 12; y < F1 - 2; y++) {
                        double ry = y + 0.5 - (FLOOR0 + 13);
                        if (ry > 0 && (sc / 3) * (sc / 3) + (ry / 4) * (ry / 4) > 1) c.set(x, y, z, y == F1 - 3 ? pal.band : pal.trim);
                    }
                }
            }
        }

        // the grand staircase, rising toward its end of the hall
        int k = BAL + FLIGHT - 1 - fromEnd;
        if (k >= 0 && k < FLIGHT && ao < 6) {
            int top = FLOOR0 + 1 + stepRise(k);
            boolean rail = ao >= 5;
            for (int y = FLOOR0 + 1; y < top; y++) c.set(x, y, z, pal.trim);
            if (rail) {
                c.set(x, top, z, pal.trim);
                c.set(x, top + 1, z, pal.wall);
            } else if (k + 1 < FLIGHT && stepRise(k + 1) > stepRise(k)) {
                Stairs st = (Stairs) pal.stairs.createBlockData();
                st.setFacing(downHall(p).getOppositeFace());
                c.set(x, top, z, st);
            } else {
                c.set(x, top, z, pal.wall);
            }
        }

        // light: under the balcony, in the nave, and high up
        if (Math.floorMod(x, 8) == 4 && Math.floorMod(z, 8) == 4) {
            for (int y : new int[]{FLOOR0 + 14, F1 + 10, ROOF + 4}) if (c.air(x, y, z)) c.set(x, y, z, LIGHT);
        }
    }

    /** Down the nave: benches, pedestals with things from the district's collections, a guide and a label. */
    private void hallInterior(MuseumCanvas c, Plan p) {
        MuseumPalette pal = p.pal;
        BlockFace down = downHall(p);
        int inner = (p.alongX ? p.sizeX : p.sizeZ) * M - 2;
        Stairs bench = (Stairs) Material.SPRUCE_STAIRS.createBlockData();
        for (int fe = BAL + FLIGHT + 10; fe < inner - BAL - 4; fe += 16) {
            for (int sd = -1; sd <= 1; sd += 2) {
                // benches facing the middle
                BlockFace out = p.alongX ? (sd < 0 ? BlockFace.NORTH : BlockFace.SOUTH) : (sd < 0 ? BlockFace.WEST : BlockFace.EAST);
                bench.setFacing(out);
                for (int d = 0; d < 6; d++) {
                    int[] q = hallPoint(p, fe + d, sd * 14 + (sd < 0 ? 0 : -1));
                    c.set(q[0], FLOOR0 + 1, q[1], bench);
                }
                // a pedestal between bench rows, by the arcade
                int[] q = hallPoint(p, fe + 9, sd * 19 + (sd < 0 ? 0 : -1));
                Random r = new Random(AlphaMegastructures.hash(q[0], q[1], p.hallSeed, 0x9ED));
                Theme t = p.themes[r.nextInt(p.themes.length)];
                Specimen[] pool = MuseumCatalogue.pool(t);
                Specimen s = pool[r.nextInt(pool.length)];
                BlockFace face = out.getOppositeFace();
                c.set(q[0], FLOOR0 + 1, q[1], pal.trim);
                c.set(q[0], FLOOR0 + 2, q[1], pal.carved);
                c.set(q[0], FLOOR0 + 3, q[1], MuseumExhibits.specimen(s, face));
                c.sign(q[0] + face.getModX(), FLOOR0 + 1, q[1] + face.getModZ(), face,
                        new Subject(MuseumCatalogue.name(s), Topic.SPECIMEN, s.rarity(), 0, t, 0));
            }
        }
        // the skeleton's label on a post, and a guide on a lectern, by the foot of the staircase
        int[] post = hallPoint(p, BAL + FLIGHT + 5, 4);
        c.set(post[0], FLOOR0 + 1, post[1], pal.carved);
        BlockFace back = down.getOppositeFace();
        c.sign(post[0] + back.getModX(), FLOOR0 + 1, post[1] + back.getModZ(), back,
                new Subject(hallSkeletonName(p), Topic.SKELETON, Rarity.EPIC, 0, p.themes[0], 0));
        int[] lec = hallPoint(p, BAL + FLIGHT + 5, -4);
        c.lectern(lec[0], FLOOR0 + 1, lec[1], back,
                new Subject("", Topic.GUIDE, Rarity.COMMON, MuseumCatalogue.gallery(p.hallSeed), p.themes[0], p.hallSeed));
    }

    private static String hallSkeletonName(Plan p) {
        return switch (p.skeleton) {
            case 0 -> "Blue Whale";
            case 1 -> "Sauropod";
            case 2 -> "Ender Dragon";
            case 3 -> "Theropod";
            default -> "Mammoth";
        };
    }

    /** The pointed vault over the hall, with a skylight along its ridge; gables with rose windows. */
    private void hallRoof(MuseumCanvas c, Plan p) {
        MuseumPalette pal = p.pal;
        int half = ((p.alongX ? p.sizeZ : p.sizeX) * M - 2) / 2;   // 31
        for (int x = c.x0; x < c.x0 + 16; x++) {
            for (int z = c.z0; z < c.z0 + 16; z++) {
                if (x < p.hx0() - 1 || x > p.hx1() + 1 || z < p.hz0() - 1 || z > p.hz1() + 1) continue;
                HallPos h = hallPos(p, x, z);
                double ao = Math.abs(h.o());
                if (ao > half + 2) continue;
                boolean gable = h.aLow() < 0 || h.aHigh() < 0;
                for (int y = ROOF; y <= ROOF + VAULT_R; y++) {
                    double dd = ao + VAULT_R - half, r = Math.sqrt(dd * dd + (double) (y - ROOF) * (y - ROOF));
                    if (gable) {
                        if (y == ROOF || r > VAULT_R) continue;
                        double wy = y + 0.5 - (ROOF + 17), rw = Math.sqrt(h.o() * h.o() + wy * wy);
                        BlockData b = pal.wall;
                        if (rw < 9) {
                            double ang = Math.atan2(wy, h.o()) / (Math.PI / 4);
                            boolean spoke = Math.abs(ang - Math.rint(ang)) * rw < 0.6 && rw > 1.5;
                            b = spoke || (rw >= 4 && rw < 5) ? pal.trim : GLASS;
                        } else if (rw < 10) {
                            b = pal.band;
                        }
                        c.set(x, y, z, b);
                    } else if (ao <= half && r >= VAULT_R - 2 && r <= VAULT_R) {
                        boolean rib = Math.floorMod(h.aLow(), 8) == 0;
                        c.set(x, y, z, ao < 3 ? GLASS : rib ? pal.band : r > VAULT_R - 1 ? pal.trim : pal.wall);
                    }
                }
            }
        }
    }

    /** The hall's skeleton: a whale or a dragon hanging under the vault, or a giant on a plinth. */
    private void hallSkeleton(MuseumCanvas c, Plan p) {
        int ix0 = p.hx0() + 1, ix1 = p.hx1() - 1, iz0 = p.hz0() + 1, iz1 = p.hz1() - 1;
        int a0 = p.alongX ? ix0 : iz0, a1 = p.alongX ? ix1 : iz1;
        double mid = p.alongX ? (iz0 + iz1 + 1) / 2.0 : (ix0 + ix1 + 1) / 2.0;
        // clear of the staircase and its foot
        int lo = p.stairEnd == 0 ? a0 + BAL + FLIGHT + 8 : a0 + BAL + 4;
        int hi = p.stairEnd == 0 ? a1 - BAL - 4 : a1 - BAL - FLIGHT - 8;
        int u = p.stairEnd == 0 ? -1 : 1;           // head toward the staircase
        double head = u < 0 ? lo + 2 : hi - 2;
        int ux = p.alongX ? -u : 0, uz = p.alongX ? 0 : -u;   // the body runs away from the staircase
        double hx = p.alongX ? head : mid, hz = p.alongX ? mid : head;
        int half = (p.alongX ? p.sizeZ : p.sizeX) * M / 2 - 1;
        double room = hi - lo - 4;
        switch (p.skeleton) {
            case 0 -> MuseumSkeletons.whale(new Body(c, hx, FLOOR0 + STORY + 16, hz, ux, uz), Math.min(80, room), half);
            case 2 -> MuseumSkeletons.flyer(new Body(c, hx, FLOOR0 + STORY + 14, hz, ux, uz), Math.min(70, room), 26, 0.3, 0.4, 6, false);
            default -> {
                double len = switch (p.skeleton) {
                    case 1 -> Math.min(72, room - 4);
                    case 3 -> Math.min(48, room - 4);
                    default -> Math.min(34, room - 4);
                };
                int w = p.skeleton == 3 ? 6 : 8;
                double c0 = head - u * -1, c1 = head - u * (len + 1);
                for (int x = c.x0; x < c.x0 + 16; x++) {
                    for (int z = c.z0; z < c.z0 + 16; z++) {
                        double a = p.alongX ? x : z, b = (p.alongX ? z : x) + 0.5 - mid;
                        if (a >= Math.min(c0, c1) && a <= Math.max(c0, c1) && Math.abs(b) < w) {
                            c.set(x, FLOOR0 + 1, z, Math.abs(b) > w - 1 ? p.pal.dais : p.pal.daisTop);
                        }
                    }
                }
                Body b = new Body(c, hx, FLOOR0 + 2, hz, ux, uz);
                if (p.skeleton == 1) MuseumSkeletons.sauropod(b, len);
                else if (p.skeleton == 3) MuseumSkeletons.theropod(b, len);
                else MuseumSkeletons.mammoth(b, len);
            }
        }
    }

    // ── Block entities ───────────────────────────────────────────────────

    /** Writes sign text, lectern books, pot sherds and paintings into a newly generated chunk. */
    public void applyBlockEntities(Chunk chunk) {
        World world = chunk.getWorld();
        long seed = world.getSeed();
        for (Label l : labels(seed, chunk.getX(), chunk.getZ())) {
            Subject s = l.subject();
            Random r = new Random(s.seed() != 0 ? s.seed()
                    : AlphaMegastructures.hash(l.x() * 31L + l.y(), l.z(), seed, LABEL_SALT));
            Block b = world.getBlockAt(l.x(), l.y(), l.z());
            switch (l.kind()) {
                case SIGN -> {
                    if (!(b.getState() instanceof Sign sign)) continue;
                    String[] lines = MuseumCatalogue.sign(s, r);
                    for (int i = 0; i < 4; i++) sign.getSide(Side.FRONT).line(i, Component.text(lines[i]));
                    sign.setWaxed(true);
                    sign.update(true, false);
                }
                case LECTERN -> {
                    if (!(b.getState() instanceof Lectern lectern)) continue;
                    MuseumCatalogue.Book book = MuseumCatalogue.book(s, r);
                    ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
                    BookMeta meta = (BookMeta) item.getItemMeta();
                    meta.setTitle(book.title().length() > 32 ? book.title().substring(0, 32) : book.title());
                    meta.setAuthor(book.author());
                    meta.setPages(book.pages());
                    item.setItemMeta(meta);
                    lectern.getInventory().setItem(0, item);
                }
                case POT -> {
                    if (!(b.getState() instanceof DecoratedPot pot)) continue;
                    Material[] sh = MuseumCatalogue.sherds(r);
                    DecoratedPot.Side[] sides = DecoratedPot.Side.values();
                    for (int i = 0; i < sides.length && i < sh.length; i++) pot.setSherd(sides[i], sh[i]);
                    pot.update(true, false);
                }
                case PAINTING -> hangPainting(world, l, r);
            }
        }
    }

    private static List<Art> arts;

    private static void hangPainting(World world, Label l, Random r) {
        if (arts == null) {
            List<Art> all = new ArrayList<>();
            RegistryAccess.registryAccess().getRegistry(RegistryKey.PAINTING_VARIANT).forEach(all::add);
            arts = all;
        }
        List<Art> fits = arts.stream().filter(a -> a.getBlockWidth() <= l.w() && a.getBlockHeight() <= l.h()).toList();
        if (fits.isEmpty()) return;
        Art art = fits.get(r.nextInt(fits.size()));
        try {
            world.spawn(new Location(world, l.x(), l.y(), l.z()), Painting.class, pt -> {
                pt.setFacingDirection(l.face(), true);
                pt.setArt(art, true);
            });
        } catch (IllegalArgumentException ignored) {
            // no room for it here
        }
    }

    // ── Spawn ────────────────────────────────────────────────────────────

    @Override
    public int getSpawnY() {
        return FLOOR0 + 1;
    }

    /** At the foot of the grand staircase in the first district's hall, facing down the nave. */
    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        Plan p = plan(world.getSeed(), 0, 0);
        int[] q = hallPoint(p, BAL + FLIGHT + 3, 0);
        BlockFace d = downHall(p);
        float yaw = switch (d) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            default -> -90;
        };
        return new Location(world, q[0] + 0.5, FLOOR0 + 1, q[1] + 0.5, yaw, 0);
    }
}
