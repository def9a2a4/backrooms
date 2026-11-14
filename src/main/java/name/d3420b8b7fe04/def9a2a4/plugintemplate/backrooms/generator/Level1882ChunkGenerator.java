package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Level 1882 — "The Collections". Hintze Hall's museum gone on forever: two floors of terracotta
 * galleries joined by tall round arches, buff walls banded in blue-grey, display cases along the
 * walls, skeletons on plinths, fossils, minerals and plants, and every so often a great vaulted hall
 * with a balcony, a grand staircase and a giant skeleton.
 *
 * <p>Built like Alpha's megastructures ({@link AlphaMegastructures}): everything is hashed from the
 * world seed and grid coordinates, so every chunk works out its own slice and nothing is stored.
 * The world is a grid of 32-block rooms (two chunks a side), grouped into districts of 8×8 rooms.
 * Each district has one central hall (2×4 rooms, both floors, roofed with a pointed vault) and on
 * each floor a maze through its other rooms: a random spanning tree, so every room can be
 * reached, plus a few extra openings so it isn't a perfect tree. Neighbouring districts are joined
 * by two doors in each shared wall, picked from a hash of the wall alone so both sides agree. Two
 * rooms per district hold a staircase between the floors; the hall's grand staircase climbs to its
 * balcony, which opens onto the upper rooms.
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

    // wall between two rooms
    static final byte CLOSED = 0, DOOR = 1, ARCADE = 2, OPEN = 3;   // OPEN: both sides are the hall

    private static final long PLAN_SALT = 0x1882, BORDER_SALT = 0xB0D3;

    // ── Palette (Waterhouse's buff and blue-grey terracotta) ────────────
    private static final BlockData BUFF = Material.SMOOTH_SANDSTONE.createBlockData();
    private static final BlockData TRIM = Material.CUT_SANDSTONE.createBlockData();
    private static final BlockData CARVED = Material.CHISELED_SANDSTONE.createBlockData();
    private static final BlockData BAND = Material.LIGHT_BLUE_TERRACOTTA.createBlockData();
    private static final BlockData TILE_A = Material.SMOOTH_SANDSTONE.createBlockData();
    private static final BlockData TILE_B = Material.WHITE_TERRACOTTA.createBlockData();
    private static final BlockData TILE_EDGE = Material.TERRACOTTA.createBlockData();
    private static final BlockData FOUND = Material.POLISHED_ANDESITE.createBlockData();
    private static final BlockData ROOF_TILE = Material.DEEPSLATE_TILES.createBlockData();
    private static final BlockData GLASS = Material.GLASS.createBlockData();
    private static final BlockData WOOD = Material.DARK_OAK_PLANKS.createBlockData();
    private static final BlockData DAIS = Material.POLISHED_DEEPSLATE.createBlockData();
    private static final BlockData DAIS_TOP = Material.SMOOTH_STONE.createBlockData();
    private static final BlockData LIGHT = Material.LIGHT.createBlockData();
    private static final BlockData CHAIN = Material.IRON_CHAIN.createBlockData();
    private static final BlockData LANTERN;
    private static final BlockData[] BONE = new BlockData[3];   // axis x, y, z
    private static final BlockData IVORY = Material.CALCITE.createBlockData();

    static {
        Lantern l = (Lantern) Material.LANTERN.createBlockData();
        l.setHanging(true);
        LANTERN = l;
        Axis[] axes = {Axis.X, Axis.Y, Axis.Z};
        for (int i = 0; i < 3; i++) {
            Orientable b = (Orientable) Material.BONE_BLOCK.createBlockData();
            b.setAxis(axes[i]);
            BONE[i] = b;
        }
    }

    /** Small things in display cases. */
    private static final Material[] SPECIMENS = {
            Material.SKELETON_SKULL, Material.SKELETON_SKULL, Material.BONE_BLOCK, Material.AMETHYST_CLUSTER,
            Material.BUDDING_AMETHYST, Material.RAW_IRON_BLOCK, Material.RAW_COPPER_BLOCK, Material.RAW_GOLD_BLOCK,
            Material.EMERALD_ORE, Material.DIAMOND_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.TURTLE_EGG, Material.SNIFFER_EGG, Material.DEAD_BRAIN_CORAL_BLOCK, Material.DEAD_TUBE_CORAL_BLOCK,
            Material.DEAD_FIRE_CORAL_BLOCK, Material.DECORATED_POT, Material.HONEYCOMB_BLOCK, Material.POTTED_FERN,
            Material.POTTED_CACTUS, Material.POTTED_DEAD_BUSH, Material.POTTED_AZALEA_BUSH, Material.HEAVY_CORE,
            Material.CONDUIT, Material.SEA_PICKLE, Material.MOSS_BLOCK, Material.SMOOTH_BASALT, Material.OBSIDIAN,
    };

    private static final Material[] PLANTS = {
            Material.AZALEA, Material.FLOWERING_AZALEA, Material.FERN, Material.SHORT_GRASS, Material.MOSS_CARPET,
            Material.FERN, Material.AZALEA,
    };

    public Level1882ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    // ── District plan ─────────────────────────────────────────────────────

    /** One district's layout: its hall, staircase rooms and the walls between its rooms. */
    private static final class Plan {
        final int ddx, ddz;
        final boolean[] hall = new boolean[D * D], stair = new boolean[D * D];
        // [floor][room]: the wall on the room's west / north side
        final byte[][] west = new byte[FLOORS][D * D], north = new byte[FLOORS][D * D];
        final long[] roomSeed = new long[D * D];
        boolean alongX;         // the hall's long axis
        int hmx, hmz, sizeX, sizeZ;
        int stairEnd;           // 0: the grand staircase is at the hall's low end, 1: the high end
        int skeleton;           // 0 whale, 1 sauropod
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

    private static Plan plan(long seed, int ddx, int ddz) {
        Random r = new Random(AlphaMegastructures.hash(ddx, ddz, seed, (int) PLAN_SALT));
        Plan p = new Plan(ddx, ddz);
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
        p.skeleton = r.nextInt(2);
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
        long h = AlphaMegastructures.hash(ddx, ddz, seed, (int) (BORDER_SALT + f * 16 + axis));
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

    // ── Chunk ────────────────────────────────────────────────────────────

    /** Writes in world coordinates, clipped to one chunk. */
    private static final class Canvas {
        final ChunkData data;
        final int x0, z0, minY, maxY;

        Canvas(ChunkData data, int x0, int z0) {
            this.data = data;
            this.x0 = x0;
            this.z0 = z0;
            this.minY = data.getMinHeight();
            this.maxY = data.getMaxHeight() - 1;
        }

        boolean has(int x, int z) {
            return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
        }

        void set(int x, int y, int z, BlockData b) {
            if (y < minY || y > maxY || !has(x, z)) return;
            data.setBlock(x - x0, y, z - z0, b);
        }

        /** Air, or an invisible light block. */
        boolean air(int x, int y, int z) {
            if (y < minY || y > maxY || !has(x, z)) return false;
            Material m = data.getType(x - x0, y, z - z0);
            return m == Material.AIR || m == Material.CAVE_AIR || m == Material.LIGHT;
        }
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        long seed = worldInfo.getSeed();
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        int gmx = Math.floorDiv(x0, M), gmz = Math.floorDiv(z0, M);
        Plan p = plan(seed, Math.floorDiv(gmx, D), Math.floorDiv(gmz, D));
        Canvas c = new Canvas(data, x0, z0);

        // this room's four walls on each floor: west, east, north, south
        byte[][] e = new byte[FLOORS][4];
        for (int f = 0; f < FLOORS; f++) {
            e[f][0] = westEdge(seed, p, f, gmx, gmz);
            e[f][1] = westEdge(seed, p, f, gmx + 1, gmz);
            e[f][2] = northEdge(seed, p, f, gmx, gmz);
            e[f][3] = northEdge(seed, p, f, gmx, gmz + 1);
        }
        boolean hall = isHall(p, gmx, gmz);

        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) column(c, p, x, z, gmx, gmz, e, hall);
        }
        if (hall) {
            hallInterior(c, p);
        } else {
            for (int f = 0; f < FLOORS; f++) room(c, p, gmx, gmz, f, e[f]);
        }
        // the vault and gables stand on the hall's wall belts too, which are in the rooms around it
        if (x0 + 15 >= p.hx0() - 1 && x0 <= p.hx1() + 1 && z0 + 15 >= p.hz0() - 1 && z0 <= p.hz1() + 1) {
            hallRoof(c, p);
            if (hall) hallSkeleton(c, p);
        }
    }

    /** Foundation, walls with their arches, floors and ceilings of one column. */
    private void column(Canvas c, Plan p, int x, int z, int gmx, int gmz, byte[][] e, boolean hall) {
        int mlx = Math.floorMod(x, M), mlz = Math.floorMod(z, M);
        for (int y = FOUNDATION; y < FLOOR0; y++) c.set(x, y, z, FOUND);

        boolean nearX = mlx <= 1 || mlx >= M - 2, nearZ = mlz <= 1 || mlz >= M - 2;
        if (nearX && nearZ) {
            int cmx = mlx <= 1 ? gmx : gmx + 1, cmz = mlz <= 1 ? gmz : gmz + 1;
            boolean inside = isHall(p, cmx - 1, cmz - 1) && isHall(p, cmx, cmz - 1)
                    && isHall(p, cmx - 1, cmz) && isHall(p, cmx, cmz);
            if (!inside) {
                pier(c, x, z);
                return;
            }
        } else if (mlx == 0 || mlx == M - 1 || mlz == 0 || mlz == M - 1) {
            int side = mlx == 0 ? 0 : mlx == M - 1 ? 1 : mlz == 0 ? 2 : 3;
            int t = side < 2 ? mlz : mlx;
            if (e[0][side] != OPEN) {
                for (int f = 0; f < FLOORS; f++) wall(c, x, z, f, t, e[f][side]);
                c.set(x, ROOF, z, ROOF_TILE);
                return;
            }
        }
        if (hall) {
            hallColumn(c, p, x, z);
            return;
        }
        for (int f = 0; f < FLOORS; f++) {
            int F = FLOOR0 + f * STORY;
            c.set(x, F, z, floorTile(mlx, mlz));
            int dx = Math.min(mlx - 1, M - 2 - mlx), dz = Math.min(mlz - 1, M - 2 - mlz);
            boolean beam = dx % 5 == 4 || dz % 5 == 4;
            c.set(x, F + STORY - 2, z, beam ? TRIM : dx % 5 == 2 && dz % 5 == 2 ? BAND : BUFF);
            if (beam) c.set(x, F + STORY - 3, z, TRIM);
            c.set(x, F + STORY - 1, z, BUFF);
        }
        c.set(x, ROOF, z, ROOF_TILE);
    }

    private static BlockData floorTile(int mlx, int mlz) {
        int d = Math.min(Math.min(mlx - 1, M - 2 - mlx), Math.min(mlz - 1, M - 2 - mlz));
        if (d <= 1) return TILE_EDGE;
        if (d == 2) return TRIM;
        return ((mlx >> 1) + (mlz >> 1) & 1) == 0 ? TILE_A : TILE_B;
    }

    /** Banding of a wall course {@code r} blocks above its story's floor. */
    private static BlockData course(int r, int t) {
        return switch (r) {
            case 0, 1, 2, 16 -> TRIM;
            case 3, 8, 13 -> BAND;
            case 15 -> (t & 1) == 0 ? CARVED : TRIM;
            default -> BUFF;
        };
    }

    private void pier(Canvas c, int x, int z) {
        for (int f = 0; f < FLOORS; f++) {
            int F = FLOOR0 + f * STORY;
            for (int r = 0; r < STORY; r++) {
                c.set(x, F + r, z, r == 14 || r == 15 ? CARVED : r == 3 || r == 8 || r == 13 ? BAND : TRIM);
            }
        }
        c.set(x, ROOF, z, ROOF_TILE);
    }

    /** One story of a wall column, {@code t} blocks along the wall, with its arch if it has one. */
    private void wall(Canvas c, int x, int z, int f, int t, byte type) {
        int F = FLOOR0 + f * STORY;
        for (int r = 0; r < STORY; r++) {
            int a = type == CLOSED ? 0 : arch(type, t, r);
            if (a == 1) continue;   // open
            BlockData b = course(r, t);
            if (a == 2) b = TRIM;
            else if (a == 3) b = BAND;
            c.set(x, F + r, z, b);
        }
    }

    /**
     * 0 wall, 1 opening, 2 jamb or light voussoir, 3 dark voussoir. A door is a round arch 8 wide
     * and 13 tall; an arcade a wide elliptical arch 24 wide and 15 tall.
     */
    static int arch(byte type, int t, int r) {
        if (r <= 0) return 0;
        double tc = t + 0.5 - M / 2.0;
        double hw = type == ARCADE ? 12 : 4, vr = type == ARCADE ? 7 : 4;
        int spring = type == ARCADE ? 8 : 9;
        double ax = Math.abs(tc);
        if (r <= spring) return ax < hw ? 1 : ax < hw + 1 ? 2 : 0;
        double ry = r - spring - 0.5;
        double e = (ax / hw) * (ax / hw) + (ry / vr) * (ry / vr);
        if (e < 1) return 1;
        double ex = ax / (hw + 1.3), ey = ry / (vr + 1.3);
        if (ex * ex + ey * ey >= 1) return 0;
        int sector = (int) (Math.atan2(ry / vr, tc / hw) / (Math.PI / 9));
        return (sector & 1) == 0 ? 2 : 3;
    }

    // ── Rooms ────────────────────────────────────────────────────────────

    private void room(Canvas c, Plan p, int gmx, int gmz, int f, byte[] e) {
        int i = idx(Math.floorMod(gmx, D), Math.floorMod(gmz, D));
        int ox = gmx * M, oz = gmz * M, F = FLOOR0 + f * STORY;
        Random r = new Random(p.roomSeed[i] ^ (f + 1) * 0x9E3779B97F4A7C15L);

        // even light from hidden light blocks, and a chandelier
        for (int lx : new int[]{4, 11, 20, 27}) {
            for (int lz : new int[]{4, 11, 20, 27}) c.set(ox + lx, F + STORY - 4, oz + lz, LIGHT);
        }
        chandelier(c, ox + 15, F + STORY - 3, oz + 15);

        if (p.stair[i]) {
            if (f == 0) stairFlight(c, ox, oz);
            else stairWell(c, ox, oz, F);
            if (e[3] == CLOSED) wallCase(c, r, ox, oz, F, 3, 3, M - 4);
            return;
        }

        int kind = r.nextInt(100);
        boolean alongX = r.nextBoolean();
        int feature = -1;   // the wall the ammonite hangs on
        if (kind >= 75 && kind < 85) {
            int start = r.nextInt(4);
            for (int s = 0; s < 4 && feature < 0; s++) if (e[(start + s) % 4] == CLOSED) feature = (start + s) % 4;
        }
        for (int s = 0; s < 4; s++) {
            if (s == feature) continue;
            if (e[s] == CLOSED) wallCase(c, r, ox, oz, F, s, 3, M - 4);
            else if (e[s] == DOOR) {
                wallCase(c, r, ox, oz, F, s, 3, 9);
                wallCase(c, r, ox, oz, F, s, 22, M - 4);
            }
        }

        if (kind < 30) {
            int sk = r.nextInt(100);
            double len = sk < 40 ? 20 : sk < 75 ? 15 : 20;
            dais(c, ox, oz, F, alongX, (int) (len / 2), 5);
            int ux = alongX ? 1 : 0, uz = alongX ? 0 : 1;
            double hx = ox + 16 - ux * len / 2, hz = oz + 16 - uz * len / 2;
            if (sk < 40) theropod(c, hx, F + 2, hz, ux, uz, len);
            else if (sk < 75) mammoth(c, hx, F + 2, hz, ux, uz, len);
            else sauropod(c, hx, F + 2, hz, ux, uz, len);
        } else if (kind < 55) {
            int[] across = {11, 20}, along = {8, 15, 23};
            for (int a : along) {
                for (int b : across) islandCase(c, r, ox + (alongX ? a : b), oz + (alongX ? b : a), F);
            }
        } else if (kind < 65) {
            dais(c, ox, oz, F, alongX, 5, 5);
            if (r.nextBoolean()) geode(c, ox + 16, F + 7, oz + 16, alongX);
            else meteorite(c, r, ox + 16, F + 6, oz + 16);
            benches(c, ox, oz, F, !alongX, 9, 22, true);
        } else if (kind < 75) {
            planters(c, r, ox, oz, F, alongX);
        } else if (kind < 85 && feature >= 0) {
            ammonite(c, ox, oz, F, feature);
            benches(c, ox, oz, F, feature >= 2, 13, 18, true);
        } else {
            benches(c, ox, oz, F, alongX, 15, 16, false);
        }
    }

    /** Room-local (side, depth from the wall, t along it) to world x/z. Sides: 0 W, 1 E, 2 N, 3 S. */
    private static int sx(int ox, int side, int d, int t) {
        return ox + switch (side) {
            case 0 -> d;
            case 1 -> M - 1 - d;
            default -> t;
        };
    }

    private static int sz(int oz, int side, int d, int t) {
        return oz + switch (side) {
            case 2 -> d;
            case 3 -> M - 1 - d;
            default -> t;
        };
    }

    private static final BlockFace[] INTO_ROOM = {BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.NORTH};

    /** A glazed case against a wall from t0 to t1: wooden base and lid, specimens on the base. */
    private void wallCase(Canvas c, Random r, int ox, int oz, int F, int side, int t0, int t1) {
        for (int t = t0; t <= t1; t++) {
            for (int d = 1; d <= 2; d++) {
                int x = sx(ox, side, d, t), z = sz(oz, side, d, t);
                c.set(x, F + 1, z, WOOD);
                c.set(x, F + 5, z, WOOD);
                boolean pane = d == 2 || t == t0 || t == t1;
                for (int y = F + 2; y <= F + 4; y++) if (pane) c.set(x, y, z, GLASS);
                if (!pane && (t - t0) % 3 == 1) c.set(x, F + 2, z, specimen(r, INTO_ROOM[side]));
            }
        }
    }

    private static BlockData specimen(Random r, BlockFace facing) {
        Material m = SPECIMENS[r.nextInt(SPECIMENS.length)];
        BlockData b = m.createBlockData();
        if (b instanceof Rotatable rot) rot.setRotation(facing);
        else if (b instanceof Directional dir && dir.getFaces().contains(BlockFace.UP)) dir.setFacing(BlockFace.UP);
        else if (b instanceof Directional dir && dir.getFaces().contains(facing)) dir.setFacing(facing);
        return b;
    }

    /** A free-standing 3×3 glass case on a wooden plinth, one specimen inside. */
    private void islandCase(Canvas c, Random r, int cx, int cz, int F) {
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                c.set(x, F + 1, z, WOOD);
                boolean mid = x == cx && z == cz;
                for (int y = F + 2; y <= F + 4; y++) if (!mid || y == F + 4) c.set(x, y, z, GLASS);
            }
        }
        c.set(cx, F + 2, cz, specimen(r, BlockFace.SOUTH));
    }

    /** A raised plinth around the room's center: half-length {@code al} along the axis, {@code ac} across. */
    private void dais(Canvas c, int ox, int oz, int F, boolean alongX, int al, int ac) {
        for (int a = 16 - al - 1; a <= 15 + al + 1; a++) {
            for (int b = 16 - ac - 1; b <= 15 + ac + 1; b++) {
                boolean rim = a == 16 - al - 1 || a == 15 + al + 1 || b == 16 - ac - 1 || b == 15 + ac + 1;
                c.set(ox + (alongX ? a : b), F + 1, oz + (alongX ? b : a), rim ? DAIS : DAIS_TOP);
            }
        }
    }

    private void chandelier(Canvas c, int x, int y, int z) {
        c.set(x, y, z, CHAIN);
        c.set(x, y - 1, z, CHAIN);
        c.set(x, y - 2, z, LANTERN);
        for (int[] o : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            c.set(x + o[0], y, z + o[1], CHAIN);
            c.set(x + o[0], y - 1, z + o[1], LANTERN);
        }
    }

    /**
     * Two rows of benches running along the axis, at {@code b0} and {@code b1} across: seated
     * facing the room's middle, or back to back facing out.
     */
    private void benches(Canvas c, int ox, int oz, int F, boolean alongX, int b0, int b1, boolean faceMiddle) {
        for (int row : new int[]{b0, b1}) {
            // a stair's facing is its back: away from where the sitter looks
            BlockFace low = alongX ? BlockFace.NORTH : BlockFace.WEST;
            boolean looksUp = (row < 16) == faceMiddle;   // looking toward higher across
            Stairs s = (Stairs) Material.SPRUCE_STAIRS.createBlockData();
            s.setFacing(looksUp ? low : low.getOppositeFace());
            for (int a = 11; a <= 20; a++) {
                if (a == 15 || a == 16) continue;
                c.set(ox + (alongX ? a : row), F + 1, oz + (alongX ? row : a), s);
            }
        }
    }

    private void planters(Canvas c, Random r, int ox, int oz, int F, boolean alongX) {
        for (int b0 : new int[]{8, 19}) {
            for (int a = 5; a <= 26; a++) {
                for (int b = b0; b < b0 + 5; b++) {
                    int x = ox + (alongX ? a : b), z = oz + (alongX ? b : a);
                    boolean rim = a == 5 || a == 26 || b == b0 || b == b0 + 4;
                    c.set(x, F + 1, z, rim ? TRIM : Material.MOSS_BLOCK.createBlockData());
                    if (!rim && r.nextInt(3) > 0) c.set(x, F + 2, z, PLANTS[r.nextInt(PLANTS.length)].createBlockData());
                }
            }
        }
    }

    /** A geode cut in half: basalt, calcite, amethyst, hollow. */
    private void geode(Canvas c, int cx, int cy, int cz, boolean alongX) {
        BlockData basalt = Material.SMOOTH_BASALT.createBlockData(), calcite = Material.CALCITE.createBlockData(),
                amethyst = Material.AMETHYST_BLOCK.createBlockData(), budding = Material.BUDDING_AMETHYST.createBlockData();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.1 + dz * dz);
                    if (d > 5.2 || d < 2.6 || (alongX ? dx : dz) > 0) continue;
                    BlockData b = d > 4.4 ? basalt : d > 3.6 ? calcite : (dx * 7 + dy * 3 + dz) % 5 == 0 ? budding : amethyst;
                    c.set(cx + dx, cy + dy, cz + dz, b);
                }
            }
        }
    }

    private void meteorite(Canvas c, Random r, int cx, int cy, int cz) {
        long s = r.nextLong();
        BlockData[] mats = {Material.BLACKSTONE.createBlockData(), Material.BASALT.createBlockData(),
                Material.GILDED_BLACKSTONE.createBlockData(), Material.RAW_IRON_BLOCK.createBlockData()};
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -4; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.3 + dz * dz);
                    double n = SimplexNoise.noise3(s, dx * 0.35, dy * 0.35, dz * 0.35);
                    if (d > 3.8 + 1.4 * n) continue;
                    double k = SimplexNoise.noise3(s + 1, dx * 0.6, dy * 0.6, dz * 0.6);
                    c.set(cx + dx, cy + dy, cz + dz, mats[k > 0.45 ? 2 : k < -0.5 ? 3 : (dx + dy + dz & 1)]);
                }
            }
        }
    }

    /** A giant ammonite on a wall: a bone spiral with ribs. */
    private void ammonite(Canvas c, int ox, int oz, int F, int side) {
        double ct = 15.5, cr = 9.5;
        for (double th = 0.6; th < 6 * Math.PI; th += 0.04) {
            double rad = 0.42 * th;
            int t = (int) Math.floor(ct + rad * Math.cos(th)), y = (int) Math.floor(cr + rad * Math.sin(th));
            c.set(sx(ox, side, 1, t), F + y, sz(oz, side, 1, t), BONE[1]);
        }
        for (double th = 2 * Math.PI; th < 6 * Math.PI; th += Math.PI / 7) {
            for (double rad = 0.42 * (th - 2 * Math.PI); rad < 0.42 * th; rad += 0.4) {
                int t = (int) Math.floor(ct + rad * Math.cos(th)), y = (int) Math.floor(cr + rad * Math.sin(th));
                c.set(sx(ox, side, 1, t), F + y, sz(oz, side, 1, t), IVORY);
            }
        }
    }

    /** Staircase room, ground floor: a flight along the north wall up through the ceiling. */
    private void stairFlight(Canvas c, int ox, int oz) {
        int F1 = FLOOR0 + STORY;
        for (int lx = 3; lx <= 23; lx++) {
            for (int lz = 1; lz <= 6; lz++) {
                for (int y = F1 - 3; y <= F1; y++) c.set(ox + lx, y, oz + lz, AIR);
            }
        }
        Stairs st = (Stairs) Material.SMOOTH_SANDSTONE_STAIRS.createBlockData();
        st.setFacing(BlockFace.EAST);
        for (int k = 0; k < STORY; k++) {
            int top = FLOOR0 + 1 + k, x = ox + 4 + k;
            for (int lz = 2; lz <= 5; lz++) {
                for (int y = FLOOR0 + 1; y < top; y++) c.set(x, y, oz + lz, TRIM);
                c.set(x, top, oz + lz, k == STORY - 1 ? BUFF : st);
            }
            for (int y = FLOOR0 + 1; y <= top + 1; y++) c.set(x, y, oz + 6, BUFF);   // the outer side wall
        }
    }

    /** Staircase room, upper floor: a parapet round the stairwell. */
    private void stairWell(Canvas c, int ox, int oz, int F) {
        for (int lx = 2; lx <= 23; lx++) c.set(ox + lx, F + 1, oz + 7, BUFF);
        for (int lz = 1; lz <= 6; lz++) c.set(ox + 2, F + 1, oz + lz, BUFF);
    }

    private static final BlockData AIR = Material.AIR.createBlockData();

    // ── The hall ─────────────────────────────────────────────────────────

    /** Along/across coordinates of a column in the hall, measured inside its walls. */
    private record HallPos(double along, double across, int aLow, int aHigh, int dWall, double o) {}

    private static HallPos hallPos(Plan p, int x, int z) {
        int ix0 = p.hx0() + 1, ix1 = p.hx1() - 1, iz0 = p.hz0() + 1, iz1 = p.hz1() - 1;
        int dWall = Math.min(Math.min(x - ix0, ix1 - x), Math.min(z - iz0, iz1 - z));
        int along = p.alongX ? x : z, a0 = p.alongX ? ix0 : iz0, a1 = p.alongX ? ix1 : iz1;
        int across = p.alongX ? z : x, b0 = p.alongX ? iz0 : ix0, b1 = p.alongX ? iz1 : ix1;
        double o = across + 0.5 - (b0 + b1 + 1) / 2.0;   // from the hall's center line
        return new HallPos(along, across, along - a0, a1 - along, dWall, o);
    }

    /** Distance (blocks) of the column from the end with the grand staircase. */
    private static int fromStairEnd(Plan p, HallPos h) {
        return p.stairEnd == 0 ? h.aLow() : h.aHigh();
    }

    private static final int FLIGHT = 24;   // the grand staircase's run

    /** Height of step {@code k} of the grand staircase (k = 0 at the foot); a landing halfway. */
    private static int stepRise(int k) {
        if (k < 10) return k;
        if (k < 15) return 10;
        return Math.min(k - 4, STORY - 1);
    }

    private void hallColumn(Canvas c, Plan p, int x, int z) {
        HallPos h = hallPos(p, x, z);
        int F1 = FLOOR0 + STORY;
        double ao = Math.abs(h.o());
        int fromEnd = fromStairEnd(p, h);
        boolean stairGap = fromEnd < BAL && ao < 6;

        // floor: tiles with a terracotta runner down the middle
        c.set(x, FLOOR0, z, ao < 3 ? TILE_EDGE : ao < 4 ? TRIM
                : h.dWall() <= 1 ? TILE_EDGE : ((x >> 1) + (z >> 1) & 1) == 0 ? TILE_A : TILE_B);

        if (h.dWall() < BAL) {
            // the balcony round the hall, on an arcade; open where the staircase arrives
            c.set(x, F1 - 2, z, TRIM);
            c.set(x, F1 - 1, z, BUFF);
            c.set(x, F1, z, h.dWall() == 0 ? TILE_EDGE : TILE_A);
            if (h.dWall() == BAL - 1 && !stairGap) c.set(x, F1 + 1, z, TRIM);
            if (h.dWall() >= BAL - 2 && !stairGap) {
                // along the edge: piers every 8 blocks, round arches between
                int dxw = Math.min(x - p.hx0() - 1, p.hx1() - 1 - x), dzw = Math.min(z - p.hz0() - 1, p.hz1() - 1 - z);
                int m = Math.floorMod(dxw <= dzw ? z : x, 8);
                if (m <= 1) {
                    for (int y = FLOOR0 + 1; y < F1 - 2; y++) c.set(x, y, z, course(y - FLOOR0, 0));
                } else {
                    double sc = m + 0.5 - 5.0;
                    for (int y = FLOOR0 + 12; y < F1 - 2; y++) {
                        double ry = y + 0.5 - (FLOOR0 + 13);
                        if (ry > 0 && (sc / 3) * (sc / 3) + (ry / 4) * (ry / 4) > 1) c.set(x, y, z, y == F1 - 3 ? BAND : TRIM);
                    }
                }
            }
        }

        // the grand staircase, rising toward its end of the hall
        int k = BAL + FLIGHT - 1 - fromEnd;
        if (k >= 0 && k < FLIGHT && ao < 6) {
            int top = FLOOR0 + 1 + stepRise(k);
            boolean rail = ao >= 5;
            for (int y = FLOOR0 + 1; y < top; y++) c.set(x, y, z, TRIM);
            if (rail) {
                c.set(x, top, z, TRIM);
                c.set(x, top + 1, z, BUFF);
            } else if (k + 1 < FLIGHT && stepRise(k + 1) > stepRise(k)) {
                Stairs st = (Stairs) Material.SMOOTH_SANDSTONE_STAIRS.createBlockData();
                BlockFace up = p.alongX ? BlockFace.EAST : BlockFace.SOUTH;
                st.setFacing(p.stairEnd == 0 ? up.getOppositeFace() : up);
                c.set(x, top, z, st);
            } else {
                c.set(x, top, z, BUFF);
            }
        }

        // light: under the balcony, in the nave, and high up
        if (Math.floorMod(x, 8) == 4 && Math.floorMod(z, 8) == 4) {
            for (int y : new int[]{FLOOR0 + 14, F1 + 10, ROOF + 4}) if (c.air(x, y, z)) c.set(x, y, z, LIGHT);
        }
    }

    /** Benches down the nave, clear of the staircase. */
    private void hallInterior(Canvas c, Plan p) {
        for (int x = c.x0; x < c.x0 + 16; x++) {
            for (int z = c.z0; z < c.z0 + 16; z++) {
                if (x <= p.hx0() || x >= p.hx1() || z <= p.hz0() || z >= p.hz1()) continue;
                HallPos h = hallPos(p, x, z);
                double ao = Math.abs(h.o());
                int fromEnd = fromStairEnd(p, h), farEnd = p.stairEnd == 0 ? h.aHigh() : h.aLow();
                if (fromEnd < BAL + FLIGHT + 8 || farEnd < BAL + 4) continue;
                if (ao > 14 && ao < 15 && Math.floorMod((int) h.along(), 16) < 6) {
                    Stairs s = (Stairs) Material.SPRUCE_STAIRS.createBlockData();
                    BlockFace out = p.alongX ? (h.o() < 0 ? BlockFace.NORTH : BlockFace.SOUTH)
                            : (h.o() < 0 ? BlockFace.WEST : BlockFace.EAST);
                    s.setFacing(out);   // seated facing the middle
                    c.set(x, FLOOR0 + 1, z, s);
                }
            }
        }
    }

    /** Inner radius of the vault above a column {@code ao} blocks off the center line, or -1. */
    private static double vaultY(double ao, int half) {
        double dd = ao + VAULT_R - half, rr = (VAULT_R - 2.0) * (VAULT_R - 2.0) - dd * dd;
        return rr < 0 ? -1 : ROOF + Math.sqrt(rr);
    }

    /** The pointed vault over the hall, with a skylight along its ridge; gables with rose windows. */
    private void hallRoof(Canvas c, Plan p) {
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
                        BlockData b = BUFF;
                        if (rw < 9) {
                            double ang = Math.atan2(wy, h.o()) / (Math.PI / 4);
                            boolean spoke = Math.abs(ang - Math.rint(ang)) * rw < 0.6 && rw > 1.5;
                            b = spoke || (rw >= 4 && rw < 5) ? TRIM : GLASS;
                        } else if (rw < 10) {
                            b = BAND;
                        }
                        c.set(x, y, z, b);
                    } else if (ao <= half && r >= VAULT_R - 2 && r <= VAULT_R) {
                        boolean rib = Math.floorMod(h.aLow(), 8) == 0;
                        c.set(x, y, z, ao < 3 ? GLASS : rib ? BAND : r > VAULT_R - 1 ? TRIM : BUFF);
                    }
                }
            }
        }
    }

    /** The hall's skeleton: a whale hanging under the vault, or a sauropod on a plinth. */
    private void hallSkeleton(Canvas c, Plan p) {
        int ix0 = p.hx0() + 1, ix1 = p.hx1() - 1, iz0 = p.hz0() + 1, iz1 = p.hz1() - 1;
        int a0 = p.alongX ? ix0 : iz0, a1 = p.alongX ? ix1 : iz1;
        double mid = p.alongX ? (iz0 + iz1 + 1) / 2.0 : (ix0 + ix1 + 1) / 2.0;
        // clear of the staircase and its foot
        int lo = p.stairEnd == 0 ? a0 + BAL + FLIGHT + 6 : a0 + BAL + 4;
        int hi = p.stairEnd == 0 ? a1 - BAL - 4 : a1 - BAL - FLIGHT - 6;
        int u = p.stairEnd == 0 ? -1 : 1;           // head toward the staircase
        double head = u < 0 ? lo + 2 : hi - 2;
        int ux = p.alongX ? u : 0, uz = p.alongX ? 0 : u;
        if (p.skeleton == 0) {
            double len = Math.min(80, hi - lo - 4);
            double hx = p.alongX ? head : mid, hz = p.alongX ? mid : head;
            whale(c, hx, FLOOR0 + STORY + 16, hz, -ux, -uz, len, (p.alongX ? p.sizeZ : p.sizeX) * M / 2 - 1);
        } else {
            double len = Math.min(72, hi - lo - 8);
            double hx = p.alongX ? head : mid, hz = p.alongX ? mid : head;
            // plinth under it
            double c0 = head - u * -1, c1 = head - u * (len + 1);
            for (int x = c.x0; x < c.x0 + 16; x++) {
                for (int z = c.z0; z < c.z0 + 16; z++) {
                    double a = p.alongX ? x : z, b = (p.alongX ? z : x) + 0.5 - mid;
                    if (a >= Math.min(c0, c1) && a <= Math.max(c0, c1) && Math.abs(b) < 8) {
                        c.set(x, FLOOR0 + 1, z, Math.abs(b) > 7 ? DAIS : DAIS_TOP);
                    }
                }
            }
            sauropod(c, hx, FLOOR0 + 2, hz, -ux, -uz, len);
        }
    }

    // ── Skeletons ────────────────────────────────────────────────────────

    /** Draws bones along a body: a = along from the head end, l = to the left, v = up. */
    private record Body(Canvas c, double hx, double hy, double hz, int ux, int uz) {
        double x(double a, double l) { return hx + ux * a - uz * l; }
        double z(double a, double l) { return hz + uz * a + ux * l; }

        void bone(double a0, double l0, double v0, double a1, double l1, double v1, double r, BlockData[] mat) {
            double x0 = x(a0, l0), z0 = z(a0, l0), x1 = x(a1, l1), z1 = z(a1, l1);
            double dx = x1 - x0, dy = v1 - v0, dz = z1 - z0;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            BlockData b = mat.length == 1 ? mat[0]
                    : mat[Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz) ? 0 : Math.abs(dy) >= Math.abs(dz) ? 1 : 2];
            int n = Math.max(1, (int) Math.ceil(len * 2));
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n;
                ball(x0 + dx * t, hy + v0 + dy * t, z0 + dz * t, r, b);
            }
        }

        void ball(double x, double y, double z, double r, BlockData b) {
            if (r < 0.75) {
                c.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), b);
                return;
            }
            int R = (int) Math.ceil(r);
            for (int dx = -R; dx <= R; dx++) {
                for (int dy = -R; dy <= R; dy++) {
                    for (int dz = -R; dz <= R; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= r * r) {
                            c.set((int) Math.floor(x) + dx, (int) Math.floor(y) + dy, (int) Math.floor(z) + dz, b);
                        }
                    }
                }
            }
        }

        /** A pair of ribs hanging from the spine at (a, v): out {@code w}, down {@code h}. */
        void ribs(double a, double v, double w, double h, double sweep) {
            int n = (int) Math.ceil((w + h) * 2.5);
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i <= n; i++) {
                    double th = 0.93 * Math.PI * i / n;
                    double l = side * w * Math.sin(th), dv = -h * (1 - Math.cos(th)) / 2;
                    ball(x(a + sweep * (1 - Math.cos(th)) / 2, l), hy + v + dv, z(a + sweep * (1 - Math.cos(th)) / 2, l), 0.5,
                            BONE[1]);
                }
            }
        }

        /** A chain from (a, v) up to the first solid block. */
        void hang(double a, double v) {
            int x = (int) Math.floor(x(a, 0)), z = (int) Math.floor(z(a, 0));
            for (int y = (int) Math.floor(hy + v) + 1; y < c.maxY && c.air(x, y, z); y++) c.set(x, y, z, CHAIN);
        }
    }

    /** A blue whale, head at (hx, hy, hz), body running along (ux, uz); {@code half}: the hall's half width. */
    private void whale(Canvas c, double hx, double hy, double hz, int ux, int uz, double len, int half) {
        Body b = new Body(c, hx, hy, hz, ux, uz);
        double skull = 0.2 * len;
        // rostrum: a broad plate, wider toward the back
        for (double a = 0; a <= skull; a += 0.5) {
            double q = a / skull, w = 1.2 + 4.5 * q, v = 1 - (1 - q) * 1.5;
            for (double l = -w; l <= w; l += 0.5) b.ball(b.x(a, l), hy + v, b.z(a, l), 0.5, BONE[ux != 0 ? 0 : 2]);
            if (q > 0.5) for (double l = -w * 0.4; l <= w * 0.4; l += 0.5) b.ball(b.x(a, l), hy + v + 1, b.z(a, l), 0.5, BONE[1]);
        }
        // the two lower jaws, bowed outward
        for (int side = -1; side <= 1; side += 2) {
            b.bone(skull, side * 5.9, -2.5, skull / 2, side * 5.0, -3.2, 0.6, BONE);
            b.bone(skull / 2, side * 5.0, -3.2, 0, side * 1.0, -1.5, 0.6, BONE);
        }
        // spine
        double prevA = skull, prevL = 0, prevV = 0;
        for (double a = skull; a <= len; a += 1) {
            double s = a / len;
            double v = 3 * Math.sin(Math.PI * s) - 1.5 * s, l = 2.5 * Math.sin(2 * Math.PI * s) * s;
            double r = 1.3 * (1 - s) + 0.5;
            b.bone(prevA, prevL, prevV, a, l, v, r, BONE);
            if (s < 0.7 && Math.round(a) % 2 == 0) b.bone(a, l, v, a + 0.5, l, v + 1.5 + 2 * (1 - s), 0.5, BONE);
            if (s >= 0.24 && s <= 0.5 && Math.round(a) % 2 == 0) {
                double q = (s - 0.24) / 0.26, size = Math.sin(Math.PI * (0.25 + 0.75 * q));
                b.ribs(a, v, 6.5 * size + 1, 8 * size + 1, 1.5);
            }
            prevA = a;
            prevL = l;
            prevV = v;
        }
        // flippers
        double fa = 0.27 * len;
        for (int side = -1; side <= 1; side += 2) {
            b.bone(fa, side * 4, -2, fa + 2, side * 8, -6, 0.8, BONE);
            b.bone(fa + 2, side * 8, -6, fa + 5, side * Math.min(11, half - 2), -10, 0.6, BONE);
        }
        for (double s : new double[]{0.1, 0.35, 0.6, 0.85}) {
            double a = s * len;
            b.hang(a, 3 * Math.sin(Math.PI * s) - 1.5 * s + (s < 0.2 ? 2 : 1.6));
        }
    }

    /** A long-necked sauropod standing at y0, head end at (hx, hz); {@code len} nose to tail. */
    private void sauropod(Canvas c, double hx, double y0, double hz, int ux, int uz, double len) {
        Body b = new Body(c, hx, y0, hz, ux, uz);
        double k = len / 70.0, sh = 15 * k;
        java.util.function.DoubleUnaryOperator spine = s -> {
            if (s < 0.28) return sh + 16 * k * Math.pow((0.28 - s) / 0.28, 1.2);
            if (s < 0.55) return sh + 1.5 * k * Math.sin(Math.PI * (s - 0.28) / 0.27);
            return sh - (sh - 2.5 * k) * Math.pow((s - 0.55) / 0.45, 0.8);
        };
        java.util.function.DoubleUnaryOperator side = s -> s > 0.55 ? 4 * k * Math.sin(Math.PI * (s - 0.55) / 0.45) : 0;
        double pa = 0, pl = 0, pv = spine.applyAsDouble(0);
        for (double a = 1; a <= len; a += 1) {
            double s = a / len, v = spine.applyAsDouble(s), l = side.applyAsDouble(s);
            double r = s < 0.28 ? (0.6 + 0.4 * s / 0.28) * Math.max(1, k * 1.2) : s < 0.55 ? 1.5 * Math.max(0.6, k) : (1.3 - s) * Math.max(0.6, k);
            b.bone(pa, pl, pv, a, l, v, r, BONE);
            if (s > 0.3 && s < 0.53 && (int) a % Math.max(2, (int) Math.round(2 * k)) == 0) {
                b.ribs(a, v, 4.5 * k + 0.8, 8 * k + 0.8, 0);
            }
            pa = a;
            pl = l;
            pv = v;
        }
        // head
        double hv = spine.applyAsDouble(0);
        b.bone(0, 0, hv, -2.5 * k - 1, 0, hv - 0.8, Math.max(0.6, 1.2 * k), BONE);
        b.bone(-0.5, 0, hv - 1.2 * k - 0.5, -2.5 * k - 1, 0, hv - 1.5 * k - 0.5, 0.5, BONE);
        // legs: hip, knee, foot
        for (double s : new double[]{0.31, 0.53}) {
            double a = s * len, v = spine.applyAsDouble(s);
            double fwd = s < 0.4 ? 1 : -1;
            for (int sd = -1; sd <= 1; sd += 2) {
                b.bone(a, sd * 3 * k, v - 1, a + fwd * k, sd * 3.5 * k, 7 * k, Math.max(0.6, 1.1 * k), BONE);
                b.bone(a + fwd * k, sd * 3.5 * k, 7 * k, a, sd * 3.5 * k, 0, Math.max(0.6, k), BONE);
            }
        }
    }

    /** A two-legged predator, jaws open; {@code len} snout to tail. */
    private void theropod(Canvas c, double hx, double y0, double hz, int ux, int uz, double len) {
        Body b = new Body(c, hx, y0, hz, ux, uz);
        double k = len / 22.0, hh = 8 * k;
        // skull and open jaw
        b.bone(0, 0, hh + 2 * k, 4 * k, 0, hh + 3 * k, 1.2 * Math.max(0.7, k), BONE);
        b.bone(0.3, 0, hh + 2.5 * k, 3.5 * k, 0, hh + 3.8 * k, 0.5, BONE);
        b.bone(4 * k, 0, hh + 1.5 * k, 0.5, 0, hh - 0.5 * k, 0.6, BONE);
        // neck
        b.bone(4.5 * k, 0, hh + 2.5 * k, 6 * k, 0, hh + 1 * k, 0.9, BONE);
        // back and tail
        double pa = 6 * k, pl = 0, pv = hh + k;
        for (double a = 6 * k + 1; a <= len; a += 1) {
            double s = a / len;
            double v = s < 0.48 ? hh + k - 0.5 * k * (s - 0.27) / 0.21 : hh + 0.5 * k - 2.5 * k * (s - 0.48) / 0.52;
            double l = s > 0.48 ? 1.5 * k * Math.sin(Math.PI * (s - 0.48) / 0.52) : 0;
            b.bone(pa, pl, pv, a, l, v, s < 0.5 ? 0.9 : 0.6, BONE);
            if (s > 0.27 && s < 0.46) b.ribs(a, v, 2.2 * k, 3.5 * k, 0.5);
            pa = a;
            pl = l;
            pv = v;
        }
        // legs: hip, knee forward, ankle back, toes forward
        double la = 0.45 * len;
        for (int sd = -1; sd <= 1; sd += 2) {
            b.bone(la, sd * 1.8 * k, hh, la - 2 * k, sd * 2.2 * k, hh - 4 * k, 0.9, BONE);
            b.bone(la - 2 * k, sd * 2.2 * k, hh - 4 * k, la + 1.5 * k, sd * 2.2 * k, 1.5 * k, 0.7, BONE);
            b.bone(la + 1.5 * k, sd * 2.2 * k, 1.5 * k, la - 1.5 * k, sd * 2.2 * k, 0, 0.5, BONE);
            // little arms
            double aa = 0.28 * len;
            b.bone(aa, sd * 1.5 * k, hh, aa - 1.2 * k, sd * 1.8 * k, hh - 1.5 * k, 0.5, BONE);
        }
    }

    /** A mammoth with curling ivory tusks; {@code len} tusk tip to tail. */
    private void mammoth(Canvas c, double hx, double y0, double hz, int ux, int uz, double len) {
        Body b = new Body(c, hx, y0, hz, ux, uz);
        double k = len / 18.0, sh = 9 * k, h0 = 5.5 * k;   // h0: where the skull is, along
        BlockData[] ivory = {IVORY};
        for (int sd = -1; sd <= 1; sd += 2) {
            b.bone(h0 + k, sd * 1.3 * k, sh - k, h0 - 3 * k, sd * 2.5 * k, 3 * k, 0.6, ivory);
            b.bone(h0 - 3 * k, sd * 2.5 * k, 3 * k, h0 - 5.5 * k, sd * 1.5 * k, 6 * k, 0.6, ivory);
            b.bone(h0 - 5.5 * k, sd * 1.5 * k, 6 * k, h0 - 5 * k, sd * 0.5 * k, 8 * k, 0.5, ivory);
        }
        b.ball(b.x(h0 + 1.5 * k, 0), y0 + sh + 1, b.z(h0 + 1.5 * k, 0), 1.8 * k, BONE[1]);
        double pa = h0 + 3 * k, pl = 0, pv = sh + k;
        for (double a = pa + 1; a <= len; a += 1) {
            double s = (a - h0) / (len - h0);
            double v = s < 0.8 ? sh + 1.5 * k * Math.sin(Math.PI * s / 0.8) + k * (1 - s) : sh - 4 * k * (s - 0.8) / 0.2;
            b.bone(pa, pl, pv, a, 0, v, s < 0.8 ? 1.0 : 0.5, BONE);
            if (s > 0.1 && s < 0.7 && Math.round(a) % 2 == 0) b.ribs(a, v, 3 * k, 5 * k, 0);
            pa = a;
            pv = v;
        }
        for (double s : new double[]{0.15, 0.7}) {
            double a = h0 + s * (len - h0);
            for (int sd = -1; sd <= 1; sd += 2) b.bone(a, sd * 2 * k, sh - 1, a, sd * 2.2 * k, 0, 0.9 * Math.max(0.8, k), BONE);
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
        int ix0 = p.hx0() + 1, ix1 = p.hx1() - 1, iz0 = p.hz0() + 1, iz1 = p.hz1() - 1;
        int foot = BAL + FLIGHT + 3;
        double along = p.stairEnd == 0 ? (p.alongX ? ix0 : iz0) + foot : (p.alongX ? ix1 : iz1) - foot;
        double mid = p.alongX ? (iz0 + iz1 + 1) / 2.0 : (ix0 + ix1 + 1) / 2.0;
        float yaw = p.alongX ? (p.stairEnd == 0 ? -90 : 90) : (p.stairEnd == 0 ? 0 : 180);
        return p.alongX ? new Location(world, along + 0.5, FLOOR0 + 1, mid, yaw, 0)
                : new Location(world, mid, FLOOR0 + 1, along + 0.5, yaw, 0);
    }
}
