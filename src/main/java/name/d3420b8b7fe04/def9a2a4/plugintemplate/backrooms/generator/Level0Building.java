package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.structure.StructureRotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Oak office blocks for Level 0, laid out after IronSource's control centre
 * ({@code assets/shapes/control.shp}): a main block with a ring of windowed
 * rooms round a corridor, a core with one or two stairwells and a windowless
 * control room two or three storeys high (screens, tiered consoles, a gallery
 * window from the corridor behind), offices over it; wings with rooms both
 * sides and stairs at their far end; the back wing sometimes an auditorium;
 * taller roof parts with stair houses.
 *
 * <p>Storeys are 5 blocks (a floor and 4 of room), lined up with the lobby so
 * one storey's floor is the lobby floor (y64) and the next's is its ceiling
 * (y69). A building either stands free in a hall dug down and raised up round
 * it, reached by a bridge at lobby level and stairs down to the hall floor, or
 * is embedded in the slab: only its lobby storey shows, the rest of it above
 * and below, windows onto solid terracotta.
 *
 * <p>The whole building is drawn once into a block array in its own frame
 * (u east, v south, the entrance on the north) and cached; chunks copy their
 * part of it, turned to the building's rotation.
 */
final class Level0Building {

    static final int STOREY = 5;
    private static final int GAP = 6;        // free-standing: hall floor round the plot
    private static final int APRON = 2;      // lobby kept clear round the hall or building
    private static final int LOBBY_FLOOR_Y = Level0ChunkGenerator.AIR_MIN_Y - 1;
    private static final int MAX_TOP_Y = 99; // under the ceiling bedrock

    static final byte ROLE_NONE = 0, ROLE_APRON = 1, ROLE_HALL = 2, ROLE_BUILT = 3;

    // Palette sentinels: resolved per column from the lobby's patch palettes.
    private static final short UNSET = 0, AIR = 1, CEILING = 2, FLOOR = 3, WALL = 4;

    // control.shp's fixed sizes.
    private static final int SL = 6;         // a core stairwell, across (with its back wall)
    private static final int VG = 3;         // the hall each side of the control room
    private static final int FY = 2;         // the entrance canopy's depth
    private static final int SW = 9;         // a wing's stairwell, along it
    private static final int WING_MIN = 14;

    private enum Kind { OFFICE, MEETING, SERVER, RECORDS, BREAK, TOILETS, CONFERENCE, CAFETERIA }

    private static final Map<Long, Level0Building> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Level0Building> eldest) {
            return size() > 24;
        }
    };

    /** The building for a macro cell's feature hash, fitting {@code maxSide} square. */
    static Level0Building get(long hash, int maxSide) {
        synchronized (CACHE) {
            Level0Building b = CACHE.get(hash);
            if (b != null) return b;
        }
        Level0Building b = new Level0Building(hash, maxSide);
        synchronized (CACHE) {
            Level0Building prior = CACHE.putIfAbsent(hash, b);
            return prior != null ? prior : b;
        }
    }

    final boolean free;
    final int rotation;
    final int width, depth, height;   // the array: u, v, y
    final int groundY;                // world y of the array's y 0 (the ground floor)
    final int ceilingY;               // free-standing: the hall's ceiling
    private final short[] cells;
    private final byte[] roles;
    private final boolean[] bare;     // the bridge and hall stairs: no carpet
    private final List<BlockData> palette = new ArrayList<>();
    private final Map<String, Short> paletteIndex = new HashMap<>();
    private final BlockData[][] rotated = new BlockData[4][];
    private final List<int[]> lights = new ArrayList<>();
    private final Random rng;
    private final int off;            // plot origin within the array
    private boolean marking;

    // Rolled sizes (control.shp's names).
    private int M, R, SR, CRX, CRY, CBY, CRS, MX, MY, NM, OW, WWIN, E0, E2, B;
    private boolean tiered, glassCurtain;
    private int LZ, RZ, BZ, CL, RDL, WSL, LENL, CRV, RDR, WSR, LENR, CB, CBO, RDB, WWB, WSB, LENB, PF;
    private boolean okL, okR, okB, aud;

    // Materials.
    private short wallOut, post, inner, floor, roof, light, riser;
    private Material bandLog;

    private Level0Building(long hash, int maxSide) {
        rng = new Random(hash);
        rotation = rng.nextInt(4);
        free = rng.nextInt(100) < 55;
        off = free ? GAP + APRON : APRON;
        int maxPlot = Math.min(104, maxSide - 2 * off);

        int pw = randInt(60, maxPlot);
        int pd = randInt(50, maxPlot);
        M = Math.max(7, randInt(8, 11));
        R = M - 4;
        int stairs = randInt(1, 2);
        SR = stairs >= 2 ? 6 : 0;
        // The core as big as fits, under a rolled cap so the plot often leaves room for wings.
        CRX = clamp(pw - SL - SR - 2 * VG - 2 * M, 5, Math.max(5, Math.min(randInt(11, 34), pw / 2)));
        CRY = clamp(pd - FY - 2 * M, 5, Math.max(5, Math.min(randInt(9, 26), pd / 2)));
        CBY = Math.max(CRY, 9);
        CRS = clamp((int) Math.round(Math.min(CRX, CRY) / (double) STOREY), 2, 3);
        tiered = CRY >= 10 && CRX >= 11;
        MX = CRX + SL + SR + 2 * VG + 2 * M;
        MY = CBY + 2 * M;
        NM = randInt(3, 5);
        OW = randInt(4, 6);
        WWIN = randInt(1, 2);
        E0 = randInt(0, 2);
        E2 = stairs >= 2 ? randInt(0, 2) : 0;
        B = randInt(1, Math.min(3, NM - 1));
        while (NM + Math.max(E0, E2) + 1 - B > 6) {
            if (E0 >= E2 && E0 > 0) E0--;
            else if (E2 > 0) E2--;
            else NM--;
        }
        PF = (MX - 2 - 2 * R - 3) / 2;

        // Wings: left, right and back of the main block, in what the plot leaves.
        int lx = Math.max(0, pw - MX);
        int by = Math.max(0, pd - FY - MY);
        boolean side = rng.nextBoolean();
        boolean hl = rng.nextInt(100) < 85;
        LENL = lx >= 2 * WING_MIN ? lx / 2 : lx >= WING_MIN && side ? lx : 0;
        RDL = randInt(4, 6);
        WSL = randInt(1, Math.min(NM, 3));
        int clo = Math.max(R + 2, 1 + RDL), chi = Math.min(MY - R - 5, MY - RDL - 4);
        okL = hl && LENL >= WING_MIN && clo <= chi;
        CL = randInt(clo, Math.max(clo, chi));

        boolean hr = rng.nextInt(100) < 85;
        LENR = lx - LENL;
        RDR = randInt(4, 6);
        WSR = randInt(1, Math.min(NM, 3));
        int crlo = Math.max(R + 2, 1 + RDR), crhi = Math.min(MY - R - 5, MY - RDR - 4);
        okR = hr && LENR >= WING_MIN && crlo <= crhi;
        CRV = randInt(crlo, Math.max(crlo, crhi));

        LZ = okL ? LENL : 0;
        RZ = okR ? LENR : 0;

        boolean hb = rng.nextInt(100) < 85;
        aud = by >= 20 && rng.nextInt(100) < 40;
        LENB = aud ? Math.min(by, 32) : by;
        RDB = randInt(4, 6);
        WWB = aud ? randInt(18, 28) : 2 * RDB + 5;
        WSB = aud ? 2 : randInt(1, Math.min(NM, 3));
        CBO = aud ? (WWB - 3) / 2 : 1 + RDB;
        int cblo = Math.max(LZ + R + 2, CBO), cbhi = Math.min(LZ + MX - R - 5, pw - (WWB - CBO));
        okB = hb && LENB >= WING_MIN && cblo <= cbhi;
        CB = randInt(cblo, Math.max(cblo, cbhi));
        BZ = okB ? LENB : 0;
        aud &= okB;

        int plotW = Math.max(LZ + MX + RZ, okB ? CB - CBO + WWB : 0);
        int plotD = FY + MY + BZ;
        width = plotW + 2 * off;
        depth = plotD + 2 * off;

        groundY = LOBBY_FLOOR_Y - STOREY * B;
        int topY = STOREY * (NM + Math.max(E0, E2) + 1);   // the highest stair house's roof
        ceilingY = free
                ? Math.max(LOBBY_FLOOR_Y + 10, Math.min(MAX_TOP_Y, groundY + topY + 3 + rng.nextInt(6)))
                : 0;
        height = free ? ceilingY - groundY + 1 : topY + 2;
        cells = new short[width * depth * height];
        roles = new byte[width * depth];
        bare = new boolean[width * depth];

        for (int i = 0; i <= WALL; i++) palette.add(null);
        palette.set(AIR, Material.AIR.createBlockData());
        chooseMaterials();

        if (free) paintHall();
        marking = true;
        paintMain();
        paintWings();
        for (int[] l : lights) put(l[0], l[1], l[2], light);
        marking = false;
        if (free) paintApproach();
        else paintApron();

        StructureRotation[] turns = {
                StructureRotation.NONE, StructureRotation.CLOCKWISE_90,
                StructureRotation.CLOCKWISE_180, StructureRotation.COUNTERCLOCKWISE_90 };
        for (int r = 0; r < 4; r++) {
            rotated[r] = new BlockData[palette.size()];
            for (int i = 0; i < palette.size(); i++) {
                BlockData bd = palette.get(i);
                if (bd == null) continue;
                if (r == 0) {
                    rotated[r][i] = bd;
                } else {
                    BlockData turned = bd.clone();
                    turned.rotate(turns[r]);
                    rotated[r][i] = turned;
                }
            }
        }
    }

    /** World-rotated footprint, x then z. */
    int worldSizeX() { return rotation % 2 == 0 ? width : depth; }
    int worldSizeZ() { return rotation % 2 == 0 ? depth : width; }

    /** Copies this chunk's part of the building in, with x0/z0 the footprint's world corner. */
    void render(Level0Chunk c, int x0, int z0) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int dx = c.wx(x) - x0, dz = c.wz(z) - z0;
                if (dx < 0 || dz < 0 || dx >= worldSizeX() || dz >= worldSizeZ()) continue;
                int u, v;
                switch (rotation) {
                    case 0 -> { u = dx; v = dz; }
                    case 1 -> { u = dz; v = depth - 1 - dx; }
                    case 2 -> { u = width - 1 - dx; v = depth - 1 - dz; }
                    default -> { u = width - 1 - dz; v = dx; }
                }
                byte role = roles[v * width + u];
                if (role == ROLE_NONE) continue;
                if (bare[v * width + u]) c.noCarpet[x][z] = true;
                for (int y = 0; y < height; y++) {
                    short id = cells[(y * depth + v) * width + u];
                    switch (id) {
                        case UNSET -> { }
                        case CEILING -> c.data.setBlock(x, groundY + y, z, c.ceilingMats[x][z]);
                        case FLOOR -> c.data.setBlock(x, groundY + y, z, c.floorMats[x][z]);
                        case WALL -> c.data.setBlock(x, groundY + y, z, c.wallMats[x][z]);
                        default -> c.data.setBlock(x, groundY + y, z, rotated[rotation][id]);
                    }
                }
                switch (role) {
                    case ROLE_HALL -> {
                        c.floorTop[x][z] = groundY;
                        c.ceilingY[x][z] = ceilingY;
                    }
                    case ROLE_BUILT -> {
                        c.noCarpet[x][z] = true;
                        if (free) c.ceilingY[x][z] = ceilingY;   // lights hang only where clear of it
                        else c.lightY[x][z] = Level0Chunk.NO_LIGHT;
                    }
                    default -> { }
                }
            }
        }
    }

    // ---------------------------------------------------------------
    //  Painting primitives (plot coordinates)
    // ---------------------------------------------------------------

    /** A local frame: a and b axes as unit steps in plot (u, v). */
    private record Frame(int ou, int ov, int au, int av, int bu, int bv) {
        int u(int a, int b) { return ou + a * au + b * bu; }
        int v(int a, int b) { return ov + a * av + b * bv; }

        BlockFace face(int da, int db) {
            int du = da * au + db * bu, dv = da * av + db * bv;
            if (du > 0) return BlockFace.EAST;
            if (du < 0) return BlockFace.WEST;
            if (dv > 0) return BlockFace.SOUTH;
            return BlockFace.NORTH;
        }

        Axis axis(int da, int db) {
            return da * au + db * bu != 0 ? Axis.X : Axis.Z;
        }

        Frame at(int a, int b) { return new Frame(u(a, b), v(a, b), au, av, bu, bv); }
    }

    private void put(int pu, int pv, int y, short id) {
        int u = pu + off, v = pv + off;
        if (u < 0 || v < 0 || u >= width || v >= depth || y < 0 || y >= height) return;
        cells[(y * depth + v) * width + u] = id;
        if (marking) roles[v * width + u] = ROLE_BUILT;
    }

    private void setBare(int pu, int pv) {
        int u = pu + off, v = pv + off;
        if (u >= 0 && v >= 0 && u < width && v < depth) bare[v * width + u] = true;
    }

    private void put(Frame f, int a, int b, int y, short id) {
        put(f.u(a, b), f.v(a, b), y, id);
    }

    private void column(Frame f, int a, int b, int y0, int y1, short id) {
        for (int y = y0; y <= y1; y++) put(f, a, b, y, id);
    }

    private void rect(Frame f, int a0, int a1, int b0, int b1, int y0, int y1, short id) {
        for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++) {
            for (int b = Math.min(b0, b1); b <= Math.max(b0, b1); b++) column(f, a, b, y0, y1, id);
        }
    }

    private short id(String key, Supplier<BlockData> make) {
        Short id = paletteIndex.get(key);
        if (id != null) return id;
        short next = (short) palette.size();
        palette.add(make.get());
        paletteIndex.put(key, next);
        return next;
    }

    private short block(Material m) {
        return m == Material.AIR ? AIR : id(m.name(), m::createBlockData);
    }

    private short stairs(Material m, BlockFace facing) {
        return id(m + ":" + facing, () -> {
            Stairs s = (Stairs) m.createBlockData();
            s.setFacing(facing);
            s.setHalf(Bisected.Half.BOTTOM);
            return s;
        });
    }

    private short topSlab(Material m) {
        return id(m + ":top", () -> {
            Slab s = (Slab) m.createBlockData();
            s.setType(Slab.Type.TOP);
            return s;
        });
    }

    private short log(Material m, Axis axis) {
        return id(m + ":" + axis, () -> {
            Orientable o = (Orientable) m.createBlockData();
            o.setAxis(axis);
            return o;
        });
    }

    private short facing(Material m, BlockFace f) {
        return id(m + ":" + f, () -> {
            Directional d = (Directional) m.createBlockData();
            d.setFacing(f);
            return d;
        });
    }

    /** A pane or fence joined to its neighbours on the two given sides. */
    private short joined(Material m, BlockFace a, BlockFace b) {
        return id(m + ":" + a + b, () -> {
            MultipleFacing mf = (MultipleFacing) m.createBlockData();
            mf.setFace(a, true);
            mf.setFace(b, true);
            return mf;
        });
    }

    private short door(BlockFace f, Bisected.Half half) {
        return id("door:" + f + half, () -> {
            Door d = (Door) Material.OAK_DOOR.createBlockData();
            d.setFacing(f);
            d.setHalf(half);
            return d;
        });
    }

    private int randInt(int lo, int hi) {
        return hi <= lo ? lo : lo + rng.nextInt(hi - lo + 1);
    }

    private static int clamp(int x, int lo, int hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    private void chooseMaterials() {
        // Outside walls: oak in four framings, the last a glass curtain wall.
        int pick = rng.nextInt(8);
        glassCurtain = pick == 7;
        Material wall, postLog;
        if (pick < 3 || glassCurtain) {
            wall = Material.OAK_PLANKS;
            postLog = Material.STRIPPED_OAK_LOG;
        } else if (pick < 5) {
            wall = Material.STRIPPED_OAK_WOOD;
            postLog = Material.OAK_LOG;
        } else {
            wall = Material.OAK_PLANKS;
            postLog = Material.DARK_OAK_LOG;
        }
        wallOut = block(wall);
        post = log(postLog, Axis.Y);
        bandLog = postLog;
        inner = block(wall == Material.STRIPPED_OAK_WOOD ? Material.OAK_PLANKS : Material.STRIPPED_OAK_WOOD);
        floor = block(Material.OAK_PLANKS);
        roof = block(Material.DARK_OAK_PLANKS);
        riser = block(Material.OAK_PLANKS);
        light = block(Material.OCHRE_FROGLIGHT);
    }

    private static int floorY(int k) { return STOREY * k; }

    // ---------------------------------------------------------------
    //  Hall (free-standing) and apron (embedded)
    // ---------------------------------------------------------------

    private void paintHall() {
        int lobby = floorY(B);
        for (int u = 0; u < width; u++) {
            for (int v = 0; v < depth; v++) {
                int ring = Math.min(Math.min(u, width - 1 - u), Math.min(v, depth - 1 - v));
                int pu = u - off, pv = v - off;
                if (ring >= APRON) {
                    put(pu, pv, 0, FLOOR);
                    for (int y = 1; y < height - 1; y++) put(pu, pv, y, AIR);
                    put(pu, pv, height - 1, CEILING);
                    roles[v * width + u] = ROLE_HALL;
                    continue;
                }
                roles[v * width + u] = ROLE_APRON;
                for (int y = lobby + 1; y < lobby + STOREY; y++) put(pu, pv, y, AIR);
                if (ring == APRON - 1) {
                    // The hall's sides lined in wall material, a parapet round the rim.
                    for (int y = 1; y < lobby; y++) put(pu, pv, y, WALL);
                    for (int y = lobby + STOREY; y < height - 1; y++) put(pu, pv, y, WALL);
                    put(pu, pv, lobby + 1, WALL);
                }
            }
        }
    }

    /** The bridge at lobby level to the front, and stairs down the hall's west side. */
    private void paintApproach() {
        int lobby = floorY(B);
        int rim = APRON - off;   // plot v of the hall's front row
        int u0 = LZ + 1 + R + PF;
        short fence = joined(Material.OAK_FENCE, BlockFace.NORTH, BlockFace.SOUTH);
        for (int v = rim - 1; v < FY; v++) {
            for (int u = u0; u <= u0 + 2; u++) {
                put(u, v, lobby, floor);
                put(u, v, lobby + 1, AIR);
                setBare(u, v);
            }
            if (v >= rim && v < 0) {
                put(u0 - 1, v, lobby, floor);
                put(u0 + 3, v, lobby, floor);
                put(u0 - 1, v, lobby + 1, fence);
                put(u0 + 3, v, lobby + 1, fence);
            }
        }

        int su = APRON - off;    // plot u of the hall's west column
        short step = stairs(Material.OAK_STAIRS, BlockFace.NORTH);
        for (int u = su; u <= su + 2; u++) {
            put(u, rim - 1, lobby + 1, AIR);   // gap in the parapet
            setBare(u, rim - 1);
            for (int i = 0; i <= lobby - 2; i++) {
                setBare(u, rim + i);
                int y = lobby - 1 - i;
                put(u, rim + i, y, step);
                for (int fy = 1; fy < y; fy++) put(u, rim + i, fy, WALL);
            }
        }
    }

    private void paintApron() {
        byte[] built = roles.clone();
        int lobby = floorY(B);
        for (int u = 0; u < width; u++) {
            for (int v = 0; v < depth; v++) {
                if (built[v * width + u] != ROLE_BUILT) continue;
                // Only columns that reach the lobby storey show in the lobby.
                boolean shows = false;
                for (int y = lobby; y <= lobby + STOREY && !shows; y++) {
                    shows = cells[(y * depth + v) * width + u] != UNSET;
                }
                if (!shows) roles[v * width + u] = ROLE_APRON;
            }
        }
        for (int u = 0; u < width; u++) {
            for (int v = 0; v < depth; v++) {
                if (roles[v * width + u] != ROLE_BUILT) continue;
                for (int du = -APRON; du <= APRON; du++) {
                    for (int dv = -APRON; dv <= APRON; dv++) {
                        int nu = u + du, nv = v + dv;
                        if (nu < 0 || nv < 0 || nu >= width || nv >= depth) continue;
                        int n = nv * width + nu;
                        if (roles[n] == ROLE_BUILT || built[n] == ROLE_BUILT) continue;
                        roles[n] = ROLE_APRON;
                        for (int y = lobby + 1; y < lobby + STOREY; y++) {
                            cells[(y * depth + nv) * width + nu] = AIR;
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------
    //  Walls and rooms
    // ---------------------------------------------------------------

    /**
     * An outside wall, one storey, a along it: posts at its ends and between
     * bays, a log band at the floor, a window in each bay, and a 3-wide way
     * through at {@code open} (if not -1).
     */
    private void outerWall(Frame w, int len, int k, int open) {
        int y0 = floorY(k);
        short band = log(bandLog, w.axis(1, 0));
        short pane = joined(Material.GLASS_PANE, w.face(1, 0), w.face(-1, 0));
        int bay = WWIN + 2;
        for (int a = 0; a < len; a++) {
            if (a == 0 || a == len - 1) {
                column(w, a, 0, y0, y0 + 4, post);
                continue;
            }
            put(w, a, 0, y0, band);
            int pos = (a - 1) % bay;
            boolean full = a - pos + bay - 1 <= len - 1;
            column(w, a, 0, y0 + 1, y0 + 4, pos == 0 ? post : wallOut);
            if (full && pos >= 1 && pos <= WWIN) {
                if (glassCurtain) column(w, a, 0, y0 + 1, y0 + 4, pane);
                else column(w, a, 0, y0 + 2, y0 + 3, pane);
            }
            if (open >= 0 && a >= open && a <= open + 2) {
                column(w, a, 0, y0 + 1, y0 + 3, AIR);
                put(w, a, 0, y0 + 4, wallOut);
            }
        }
    }

    /**
     * A row of rooms, a along it, b in from its window wall to its door wall
     * at {@code deep - 1}; a 3-wide way through at {@code p} (if not -1).
     */
    private void band(Frame f, int len, int deep, int k, int p) {
        if (p >= 1 && p + 3 < len) {
            run(f, 0, p - 2, deep, k);
            wallAcross(f, p - 1, deep, k);
            for (int a = p; a <= p + 2; a++) put(f, a, deep - 1, floorY(k) + 4, inner);
            wallAcross(f, p + 3, deep, k);
            run(f, p + 4, len - 1, deep, k);
        } else {
            run(f, 0, len - 1, deep, k);
        }
    }

    private void wallAcross(Frame f, int a, int deep, int k) {
        for (int b = 0; b < deep; b++) column(f, a, b, floorY(k) + 1, floorY(k) + 4, inner);
    }

    /** Rooms across a0..a1 (control.shp's Run): offices, now and then a conference room. */
    private void run(Frame f, int a0, int a1, int deep, int k) {
        while (a1 - a0 + 1 >= 1) {
            int rem = a1 - a0 + 1;
            if (rem <= OW + 3) {
                room(f, a0, a1, deep, k, rem >= 8 ? Kind.CONFERENCE : rollKind());
                return;
            }
            int w;
            Kind kind;
            if (rem >= 13 && rng.nextInt(100) < 25) {
                w = randInt(8, Math.min(14, rem - 5));
                kind = Kind.CONFERENCE;
            } else if (rem >= 12 && rng.nextInt(100) < 10) {
                w = randInt(8, Math.min(14, rem - 4));
                kind = Kind.CAFETERIA;
            } else {
                w = OW;
                kind = rollKind();
            }
            room(f, a0, a0 + w - 1, deep, k, kind);
            wallAcross(f, a0 + w, deep, k);
            a0 += w + 1;
        }
    }

    private Kind rollKind() {
        int r = rng.nextInt(12);
        if (r < 6) return Kind.OFFICE;
        if (r < 8) return Kind.MEETING;
        return switch (r) {
            case 8 -> Kind.SERVER;
            case 9 -> Kind.RECORDS;
            case 10 -> Kind.BREAK;
            default -> Kind.TOILETS;
        };
    }

    private void room(Frame f, int a0, int a1, int deep, int k, Kind kind) {
        int y0 = floorY(k);
        int mid = (a0 + a1) / 2;
        for (int a = a0; a <= a1; a++) column(f, a, deep - 1, y0 + 1, y0 + 4, inner);
        if (rng.nextInt(100) < 70) {
            BlockFace face = f.face(0, 1);
            put(f, mid, deep - 1, y0 + 1, door(face, Bisected.Half.BOTTOM));
            put(f, mid, deep - 1, y0 + 2, door(face, Bisected.Half.TOP));
        } else {
            column(f, mid, deep - 1, y0 + 1, y0 + 2, AIR);
        }
        lights.add(new int[]{ f.u(mid, (deep - 2) / 2), f.v(mid, (deep - 2) / 2), y0 + STOREY });
        things(f.at(a0, 0), a1 - a0 + 1, deep - 1, y0 + 1, kind);
    }

    /** A room's furniture: x across it, y from the window (0) to its last row (d - 1). */
    private void things(Frame r, int w, int d, int y, Kind kind) {
        if (w < 2 || d < 3) return;
        int mid = w / 2;
        switch (kind) {
            case OFFICE -> {
                put(r, mid, 1, y, topSlab(Material.OAK_SLAB));
                put(r, mid, 2, y, stairs(Material.SPRUCE_STAIRS, r.face(0, 1)));
                if (d > 3) {
                    put(r, 0, 3, y, block(Material.BARREL));
                    put(r, 0, 3, y + 1, block(Material.BARREL));
                }
            }
            case MEETING -> {
                for (int b = 1; b <= d - 2; b++) {
                    put(r, mid, b, y, topSlab(Material.DARK_OAK_SLAB));
                    if (mid - 1 >= 0) put(r, mid - 1, b, y, stairs(Material.SPRUCE_STAIRS, r.face(-1, 0)));
                    if (mid + 1 < w) put(r, mid + 1, b, y, stairs(Material.SPRUCE_STAIRS, r.face(1, 0)));
                }
            }
            case SERVER, RECORDS -> {
                Material m = kind == Kind.SERVER ? Material.BLAST_FURNACE : Material.BARREL;
                for (int x = 0; x < w; x += 2) {
                    for (int b = 0; b <= d - 2; b++) {
                        short id = kind == Kind.SERVER ? facing(m, r.face(1, 0)) : block(m);
                        put(r, x, b, y, id);
                        put(r, x, b, y + 1, id);
                    }
                }
            }
            case BREAK -> {
                for (int b = 0; b <= d - 2; b++) put(r, 0, b, y, topSlab(Material.SMOOTH_STONE_SLAB));
                put(r, 0, d - 1, y, block(Material.IRON_BLOCK));
                put(r, 0, d - 1, y + 1, block(Material.IRON_BLOCK));
                table(r, Math.max(1, mid), d / 2, y);
            }
            case TOILETS -> {
                for (int x = 0; x < w; x++) put(r, x, 0, y, block(Material.CAULDRON));
                for (int x = 0; x < w; x += 2) put(r, x, d - 1, y, topSlab(Material.POLISHED_DIORITE_SLAB));
            }
            case CONFERENCE -> {
                if (w < 6) {
                    things(r, w, d, y, Kind.MEETING);
                    return;
                }
                int bm = d / 2;
                for (int x = 1; x <= w - 4; x++) {
                    put(r, x, bm, y, topSlab(Material.DARK_OAK_SLAB));
                    put(r, x, bm - 1, y, stairs(Material.SPRUCE_STAIRS, r.face(0, -1)));
                    if (bm + 1 < d) put(r, x, bm + 1, y, stairs(Material.SPRUCE_STAIRS, r.face(0, 1)));
                }
                put(r, w - 2, bm, y, facing(Material.LECTERN, r.face(-1, 0)));
                for (int b = bm - 1; b <= bm + 1; b++) {
                    if (b >= 0 && b < d) column(r, w - 1, b, y, y + 2, block(Material.WHITE_WOOL));
                }
            }
            case CAFETERIA -> {
                for (int b = 0; b <= d - 2; b++) put(r, w - 1, b, y, topSlab(Material.SMOOTH_STONE_SLAB));
                cafe(r, 0, w - 2, 0, d - 1, y);
            }
        }
    }

    private void table(Frame r, int x, int b, int y) {
        put(r, x, b, y, block(Material.OAK_FENCE));
        put(r, x, b, y + 1, block(Material.OAK_PRESSURE_PLATE));
    }

    /** Small tables, one to each 4x4 now and then none, one to four chairs round each. */
    private void cafe(Frame r, int x0, int x1, int b0, int b1, int y) {
        for (int x = x0; x + 2 <= x1; x += 4) {
            for (int b = b0; b + 2 <= b1; b += 4) {
                if (rng.nextInt(5) == 0) continue;
                int cx = x + 1 + rng.nextInt(Math.min(2, x1 - x - 1)), cb = b + 1 + rng.nextInt(Math.min(2, b1 - b - 1));
                table(r, cx, cb, y);
                int[][] around = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
                for (int[] o : around) {
                    if (rng.nextInt(5) < 3) put(r, cx + o[0], cb + o[1], y, stairs(Material.OAK_STAIRS, r.face(o[0], o[1])));
                }
            }
        }
    }

    /** Desks with chairs every 3 blocks, a walkway round, lights every 6; skipping one box. */
    private void openPlan(Frame f, int a0, int a1, int b0, int b1, int k, int[] skip) {
        int y = floorY(k) + 1;
        for (int a = a0 + 1; a < a1; a++) {
            for (int b = b0 + 1; b < b1; b++) {
                if (skip != null && a >= skip[0] - 1 && a <= skip[1] + 1 && b >= skip[2] - 1 && b <= skip[3] + 1) continue;
                int da = (a - a0 - 1) % 3, db = (b - b0 - 1) % 3;
                if (da == 0 && db == 0) put(f, a, b, y, topSlab(Material.OAK_SLAB));
                else if (da == 0 && db == 1) put(f, a, b, y, stairs(Material.SPRUCE_STAIRS, f.face(0, 1)));
            }
        }
        for (int a = a0 + 3; a < a1; a += 6) {
            for (int b = b0 + 3; b < b1; b += 6) {
                if (skip != null && a >= skip[0] - 1 && a <= skip[1] + 1 && b >= skip[2] - 1 && b <= skip[3] + 1) continue;
                lights.add(new int[]{ f.u(a, b), f.v(a, b), floorY(k) + STOREY });
            }
        }
    }

    /**
     * A stairwell, one storey: a along it with end walls at 0 and len - 1; b
     * across: lanes at 0 and 1, a walkway, the door wall at wid - 1. Flights of
     * 4 stairs up 5 (switching lanes each storey) for storeys below {@code top},
     * the floor left open over the flight that comes up into it.
     */
    private void stairwell(Frame f, int len, int wid, int k, int top) {
        int y0 = floorY(k);
        rect(f, 1, len - 2, 0, wid - 2, y0 + 1, y0 + 4, AIR);
        for (int b = 0; b < wid; b++) {
            column(f, 0, b, y0 + 1, y0 + 4, inner);
            column(f, len - 1, b, y0 + 1, y0 + 4, inner);
        }
        int am = len / 2;
        for (int a = 1; a < len - 1; a++) column(f, a, wid - 1, y0 + 1, y0 + 4, inner);
        column(f, am, wid - 1, y0 + 1, y0 + 2, AIR);

        int x0 = 1 + (len - 2 - 7) / 2;
        if (k > 0) {
            int lane = (k - 1) % 2;
            for (int x = 1; x <= 4; x++) put(f, x0 + x + lane, lane, y0, AIR);
        }
        if (k < top) {
            int lane = k % 2;
            for (int i = 0; i < 4; i++) {
                int x = lane == 0 ? 1 + i : 5 - i;
                put(f, x0 + x, lane, y0 + 1 + i, stairs(Material.OAK_STAIRS, f.face(lane == 0 ? 1 : -1, 0)));
            }
        }
        lights.add(new int[]{ f.u(am, 2), f.v(am, 2), y0 + STOREY });
    }

    // ---------------------------------------------------------------
    //  Main block
    // ---------------------------------------------------------------

    private Frame main() { return new Frame(LZ, FY, 1, 0, 0, 1); }

    private void paintMain() {
        Frame mf = main();
        for (int k = 0; k < NM; k++) storey(mf, k);

        // Roof over it all, a parapet round; the taller parts and stair houses on it.
        rect(mf, 0, MX - 1, 0, MY - 1, floorY(NM), floorY(NM), roof);
        if (free) parapet(mf, 0, MX - 1, 0, MY - 1, floorY(NM) + 1);
        int p1 = M + SL + VG + CRX / 3, p2 = M + SL + VG + CRX - CRX / 3;
        upperPart(mf, 0, p1 - 1, E0, 0);
        if (SR > 0) upperPart(mf, p2, MX - 1, E2, 1);

        controlRoom(mf);

        if (free) {
            // The entrance canopy.
            int u0 = 1 + R + PF;
            rect(mf, u0, u0 + 2, -FY, -1, 4, 4, topSlab(Material.DARK_OAK_SLAB));
        }
    }

    private boolean entranceStorey(int k) {
        return k == B || (free && k == 0);
    }

    private void storey(Frame mf, int k) {
        int y0 = floorY(k);
        boolean rightWay = okR && k < WSR;
        boolean backWay = okB && k < WSB && (!aud || k == 1);
        boolean leftWay = okL && k < WSL;

        outerWall(new Frame(LZ, FY, 1, 0, 0, -1), MX, k, entranceStorey(k) ? 1 + R + PF : -1);
        outerWall(new Frame(LZ + MX - 1, FY, 0, 1, 1, 0), MY, k, rightWay ? CRV : -1);
        outerWall(new Frame(LZ, FY + MY - 1, 1, 0, 0, 1), MX, k, backWay ? CB - LZ : -1);
        outerWall(new Frame(LZ, FY, 0, 1, -1, 0), MY, k, leftWay ? CL : -1);

        rect(mf, 1, MX - 2, 1, MY - 2, y0, y0, floor);
        rect(mf, 1, MX - 2, 1, MY - 2, y0 + 1, y0 + 4, AIR);

        int lenX = MX - 2 - 2 * R, lenY = MY - 2 - 2 * R;
        band(new Frame(LZ + 1 + R, FY + 1, 1, 0, 0, 1), lenX, R, k, entranceStorey(k) ? PF : -1);
        band(new Frame(LZ + MX - 2, FY + 1 + R, 0, 1, -1, 0), lenY, R, k, rightWay ? CRV - 1 - R : -1);
        Frame back = new Frame(LZ + MX - 2 - R, FY + MY - 2, -1, 0, 0, -1);
        int backP = backWay ? MX - 4 - R - (CB - LZ) : -1;
        if (k >= 1 && k < CRS) {
            // Behind the control room: an open observation hall with a cafe.
            cafe(back, 0, lenX - 1, 0, R - 1, y0 + 1);
            if (backP >= 0) rect(back, backP, backP + 2, 0, R - 1, y0 + 1, y0 + 1, AIR);
            for (int a = 3; a < lenX; a += 6) lights.add(new int[]{ back.u(a, R / 2), back.v(a, R / 2), y0 + STOREY });
        } else {
            band(back, lenX, R, k, backP);
        }
        band(new Frame(LZ + 1, FY + MY - 2 - R, 0, -1, 1, 0), lenY, R, k, leftWay ? MY - 4 - R - CL : -1);

        corners(mf, k);

        // The corridor ring's lights, and the observation seats behind the control room.
        for (int u = R + 2; u <= MX - 3 - R; u++) {
            if (u % 6 != 0) continue;
            lights.add(new int[]{ mf.u(u, R + 2), mf.v(u, R + 2), y0 + STOREY });
            lights.add(new int[]{ mf.u(u, MY - 3 - R), mf.v(u, MY - 3 - R), y0 + STOREY });
        }
        for (int v = R + 2; v <= MY - 3 - R; v++) {
            if (v % 6 != 0) continue;
            lights.add(new int[]{ mf.u(R + 2, v), mf.v(R + 2, v), y0 + STOREY });
            lights.add(new int[]{ mf.u(MX - 3 - R, v), mf.v(MX - 3 - R, v), y0 + STOREY });
        }
        if (k >= 1 && k < CRS) {
            for (int u = M + 2; u <= MX - M - 3; u++) {
                if ((u - M - 2) % 4 != 3) put(mf, u, MY - R - 3, y0 + 1, stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH));
            }
        }

        coreStairs(mf, k, 0, NM + E0);
        if (SR > 0) coreStairs(mf, k, 1, NM + E2);
        int ur = M + SL + 2 * VG + CRX;
        for (int v = M + 2; v < M + CBY; v += 6) {
            lights.add(new int[]{ mf.u(M + SL + 1, v), mf.v(M + SL + 1, v), y0 + STOREY });
            lights.add(new int[]{ mf.u(ur - 2, v), mf.v(ur - 2, v), y0 + STOREY });
        }
        if (k >= CRS) {
            int u0c = M + SL + VG, v0c = M + (CBY - CRY) / 2;
            openPlan(mf, u0c - 1, u0c + CRX, v0c - 1, v0c + CRY, k, null);
        }
    }

    /** The core's stairwell on side 0 (west) or 1 (east), storey k, with its back wall. */
    private void coreStairs(Frame mf, int k, int side, int top) {
        int y0 = floorY(k);
        if (side == 0) {
            stairwell(new Frame(mf.u(M + 1, M), mf.v(M + 1, M), 0, 1, 1, 0), CBY, 5, k, top);
            for (int v = M; v < M + CBY; v++) column(mf, M, v, y0 + 1, y0 + 4, inner);
        } else {
            int ur = M + SL + 2 * VG + CRX;
            stairwell(new Frame(mf.u(ur + 4, M), mf.v(ur + 4, M), 0, 1, -1, 0), CBY, 5, k, top);
            for (int v = M; v < M + CBY; v++) column(mf, ur + 5, v, y0 + 1, y0 + 4, inner);
        }
    }

    /** Corner rooms, R square: a door into the front or back row, a plain wall to the side row. */
    private void corners(Frame mf, int k) {
        int y0 = floorY(k);
        int[][] cs = {
                { 1, R - 1, 1, R - 1, R, R },
                { MX - R, MX - 2, 1, R - 1, MX - 1 - R, R },
                { MX - R, MX - 2, MY - R, MY - 2, MX - 1 - R, MY - 1 - R },
                { 1, R - 1, MY - R, MY - 2, R, MY - 1 - R } };
        for (int[] c : cs) {
            int u0 = c[0], u1 = c[1], v0 = c[2], v1 = c[3], wu = c[4], wv = c[5];
            for (int v = Math.min(v0, wv); v <= Math.max(v1, wv); v++) column(mf, wu, v, y0 + 1, y0 + 4, inner);
            for (int u = Math.min(u0, wu); u <= Math.max(u1, wu); u++) column(mf, u, wv, y0 + 1, y0 + 4, inner);
            column(mf, wu, (v0 + v1) / 2, y0 + 1, y0 + 2, AIR);
            int mu = (u0 + u1) / 2, mv = (v0 + v1) / 2;
            boolean frontRow = v0 == 1;
            int toWindow = frontRow ? -1 : 1;
            put(mf, mu, mv + toWindow, y0 + 1, topSlab(Material.OAK_SLAB));
            put(mf, mu, mv, y0 + 1, stairs(Material.SPRUCE_STAIRS, frontRow ? BlockFace.SOUTH : BlockFace.NORTH));
            put(mf, u0 == 1 ? u0 : u1, mv - toWindow, y0 + 1, block(Material.POTTED_FERN));
            lights.add(new int[]{ mf.u(mu, mv), mf.v(mu, mv), y0 + STOREY });
        }
    }

    private void parapet(Frame f, int a0, int a1, int b0, int b1, int y) {
        for (int a = a0; a <= a1; a++) {
            put(f, a, b0, y, wallOut);
            put(f, a, b1, y, wallOut);
        }
        for (int b = b0; b <= b1; b++) {
            put(f, a0, b, y, wallOut);
            put(f, a1, b, y, wallOut);
        }
    }

    /** A roof part u0..u1 rising {@code e} storeys more (offices round its stairs), then its stair house. */
    private void upperPart(Frame mf, int u0, int u1, int e, int side) {
        int top = NM + e;
        int[] stairBox = side == 0
                ? new int[]{ M, M + SL - 1, M, M + CBY - 1 }
                : new int[]{ M + SL + 2 * VG + CRX, M + SL + 2 * VG + CRX + SL - 1, M, M + CBY - 1 };
        for (int k = NM; k < top; k++) {
            int y0 = floorY(k);
            outerWall(new Frame(mf.u(u0, 0), mf.v(u0, 0), 1, 0, 0, -1), u1 - u0 + 1, k, -1);
            outerWall(new Frame(mf.u(u1, 0), mf.v(u1, 0), 0, 1, 1, 0), MY, k, -1);
            outerWall(new Frame(mf.u(u0, MY - 1), mf.v(u0, MY - 1), 1, 0, 0, 1), u1 - u0 + 1, k, -1);
            outerWall(new Frame(mf.u(u0, 0), mf.v(u0, 0), 0, 1, -1, 0), MY, k, -1);
            rect(mf, u0 + 1, u1 - 1, 1, MY - 2, y0, y0, floor);
            rect(mf, u0 + 1, u1 - 1, 1, MY - 2, y0 + 1, y0 + 4, AIR);
            openPlan(mf, u0, u1, 0, MY - 1, k, stairBox);
            coreStairs(mf, k, side, top);
        }
        if (e > 0) {
            rect(mf, u0, u1, 0, MY - 1, floorY(top), floorY(top), roof);
            if (free) parapet(mf, u0, u1, 0, MY - 1, floorY(top) + 1);
        }
        // The stair house: the stairwell's last storey, roofed.
        coreStairs(mf, top, side, top);
        rect(mf, stairBox[0], stairBox[1], stairBox[2], stairBox[3], floorY(top + 1), floorY(top + 1), roof);
    }

    // ---------------------------------------------------------------
    //  The control room
    // ---------------------------------------------------------------

    private void controlRoom(Frame mf) {
        int u0 = M + SL + VG, v0 = M + (CBY - CRY) / 2;
        int topY = floorY(CRS) - 1;
        for (int u = u0; u < u0 + CRX; u++) {
            for (int v = v0; v < v0 + CRY; v++) {
                boolean edge = u == u0 || u == u0 + CRX - 1 || v == v0 || v == v0 + CRY - 1;
                put(mf, u, v, 0, floor);
                column(mf, u, v, 1, topY, edge ? inner : AIR);
            }
        }
        Frame in = new Frame(mf.u(u0 + 1, v0 + 1), mf.v(u0 + 1, v0 + 1), 1, 0, 0, 1);
        int w = CRX - 2, d = CRY - 2;
        for (int a = 2; a < w; a += 6) {
            for (int b = 2; b < d; b += 6) lights.add(new int[]{ in.u(a, b), in.v(a, b), floorY(CRS) });
        }

        // Side doors near the front.
        column(mf, u0, v0 + 2, 1, 2, AIR);
        column(mf, u0 + CRX - 1, v0 + 2, 1, 2, AIR);

        // The gallery window in the back wall, from the observation row on storey 1.
        short pane = joined(Material.GLASS_PANE, BlockFace.EAST, BlockFace.WEST);
        for (int u = u0 + 2; u <= u0 + CRX - 3; u++) column(mf, u, v0 + CRY - 1, STOREY + 2, STOREY + 4, pane);

        short screenDead = block(Material.BLACK_CONCRETE), screenLit = block(Material.LIGHT_BLUE_CONCRETE);
        if (!tiered) {
            // Consoles along the screen wall and both sides, facing in, clear of the doors.
            for (int a = 1; a < w - 1; a++) put(in, a, 0, 1, facing(Material.OBSERVER, BlockFace.SOUTH));
            for (int b = 3; b < d - 1; b++) {
                put(in, 0, b, 1, facing(Material.OBSERVER, BlockFace.EAST));
                put(in, w - 1, b, 1, facing(Material.OBSERVER, BlockFace.WEST));
            }
            return;
        }

        // Screens at the front, 3 clear, then rows 2 deep each a block up on the last.
        for (int a = 1; a < w - 1; a++) {
            for (int y = 1; y <= topY - 3; y++) put(in, a, 0, y, rng.nextInt(100) < 18 ? screenLit : screenDead);
        }
        int n = (d - 7) / 2;
        int hPrev = 0;
        short seat = stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH);
        short console = facing(Material.OBSERVER, BlockFace.SOUTH);
        short aisleStep = stairs(Material.OAK_STAIRS, BlockFace.SOUTH);
        for (int i = 0; i < n; i++) {
            int h = Math.min(i, 2 * CRS - 2);
            boolean boss = n > 2 && i == n - 1;
            int b = 4 + 2 * i;
            for (int a = 0; a < w; a++) {
                if (h > 0) {
                    column(in, a, b, 1, h, riser);
                    column(in, a, b + 1, 1, h, riser);
                }
                boolean aisle = a < 2 || a >= w - 2;
                if (aisle) {
                    if (h > hPrev) put(in, a, b, h, aisleStep);
                    continue;
                }
                if (boss) {
                    if (a == 2) put(in, a, b, h + 1, console);
                    else if (a >= 3 && a <= 5) put(in, a, b, h + 1, topSlab(Material.DARK_OAK_SLAB));
                    else if (a == 6) put(in, a, b, h + 1, facing(Material.LECTERN, BlockFace.SOUTH));
                    if (a == 4) put(in, a, b + 1, h + 1, seat);
                } else {
                    put(in, a, b, h + 1, console);
                    put(in, a, b + 1, h + 1, seat);
                }
            }
            hPrev = h;
        }

        // The back area at the back row's height, flights up to doors on storey 1.
        int hb = Math.min(Math.max(n - 1, 0), 2 * CRS - 2);
        int rb0 = 4 + 2 * n;
        if (hb > 0) rect(in, 0, w - 1, rb0, d - 1, 1, hb, riser);
        int steps = STOREY - hb;
        if (2 * (2 + steps) <= w) {
            for (int b = d - 2; b <= d - 1; b++) {
                for (int s = 0; s < 2; s++) {
                    int dir = s == 0 ? 1 : -1;
                    int edge = s == 0 ? 0 : w - 1;
                    column(in, edge, b, 1, STOREY, riser);
                    column(in, edge + dir, b, 1, STOREY, riser);
                    for (int j = 1; j <= steps; j++) {
                        int a = edge + dir * (1 + j);
                        int top = STOREY - j + 1;
                        if (top - 1 >= 1) column(in, a, b, 1, top - 1, riser);
                        put(in, a, b, top, stairs(Material.OAK_STAIRS, in.face(-dir, 0)));
                    }
                }
                column(mf, u0, v0 + 1 + b, STOREY + 1, STOREY + 2, AIR);
                column(mf, u0 + CRX - 1, v0 + 1 + b, STOREY + 1, STOREY + 2, AIR);
            }
        } else if (hb == 0) {
            column(mf, u0, v0 + d - 1, 1, 2, AIR);
            column(mf, u0 + CRX - 1, v0 + d - 1, 1, 2, AIR);
        }
    }

    // ---------------------------------------------------------------
    //  Wings and the auditorium
    // ---------------------------------------------------------------

    private void paintWings() {
        if (okL) wing(new Frame(LZ - 1, FY + CL - 1 - RDL, 0, 1, -1, 0), 2 * RDL + 5, LENL, WSL, RDL);
        if (okR) wing(new Frame(LZ + MX, FY + CRV - 1 - RDR, 0, 1, 1, 0), 2 * RDR + 5, LENR, WSR, RDR);
        if (okB) {
            Frame f = new Frame(CB - CBO, FY + MY, 1, 0, 0, 1);
            if (aud) auditorium(f);
            else wing(f, WWB, LENB, WSB, RDB);
        }
    }

    /**
     * A wing: a across (outside wall, rooms, a 3-wide corridor, rooms, outside
     * wall), b out from its parent's wall; stairs at its far end.
     */
    private void wing(Frame f, int ww, int len, int ws, int rd) {
        boolean stairs = ws > 1;
        int stairB = len - 1 - SW;
        for (int k = 0; k < ws; k++) {
            int y0 = floorY(k);
            outerWall(new Frame(f.u(0, 0), f.v(0, 0), f.bu(), f.bv(), -f.au(), -f.av()), len, k, -1);
            outerWall(new Frame(f.u(ww - 1, 0), f.v(ww - 1, 0), f.bu(), f.bv(), f.au(), f.av()), len, k, -1);
            outerWall(new Frame(f.u(0, len - 1), f.v(0, len - 1), f.au(), f.av(), f.bu(), f.bv()), ww, k, -1);
            rect(f, 1, ww - 2, 0, len - 2, y0, y0, floor);
            rect(f, 1, ww - 2, 0, len - 2, y0 + 1, y0 + 4, AIR);

            Frame left = new Frame(f.u(1, 0), f.v(1, 0), f.bu(), f.bv(), f.au(), f.av());
            Frame right = new Frame(f.u(ww - 2, 0), f.v(ww - 2, 0), f.bu(), f.bv(), -f.au(), -f.av());
            band(left, stairs ? stairB : len - 1, rd, k, -1);
            band(right, len - 1, rd, k, -1);
            if (stairs) {
                stairwell(new Frame(f.u(1, stairB), f.v(1, stairB), f.bu(), f.bv(), f.au(), f.av()), SW, rd, k, ws - 1);
            }
            for (int b = 3; b < len - 1; b += 6) lights.add(new int[]{ f.u(rd + 2, b), f.v(rd + 2, b), y0 + STOREY });
        }
        rect(f, 0, ww - 1, 0, len - 1, floorY(ws), floorY(ws), roof);
        if (free) {
            for (int b = 0; b < len; b++) {
                put(f, 0, b, floorY(ws) + 1, wallOut);
                put(f, ww - 1, b, floorY(ws) + 1, wallOut);
            }
            for (int a = 0; a < ww; a++) put(f, a, len - 1, floorY(ws) + 1, wallOut);
        }
    }

    /**
     * Two storeys in one: in from the main block's corridor on storey 1 onto
     * a balcony, rows of seats stepping down toward a stage and screen at the
     * far end, stepped aisles each side.
     */
    private void auditorium(Frame f) {
        int ww = WWB, len = LENB;
        for (int k = 0; k < 2; k++) {
            outerWall(new Frame(f.u(0, 0), f.v(0, 0), f.bu(), f.bv(), -f.au(), -f.av()), len, k, -1);
            outerWall(new Frame(f.u(ww - 1, 0), f.v(ww - 1, 0), f.bu(), f.bv(), f.au(), f.av()), len, k, -1);
            outerWall(new Frame(f.u(0, len - 1), f.v(0, len - 1), f.au(), f.av(), f.bu(), f.bv()), ww, k, -1);
        }
        rect(f, 1, ww - 2, 0, len - 2, 0, 0, floor);
        rect(f, 1, ww - 2, 0, len - 2, 1, 2 * STOREY - 1, AIR);
        rect(f, 0, ww - 1, 0, len - 1, 2 * STOREY, 2 * STOREY, roof);
        if (free) {
            for (int b = 0; b < len; b++) {
                put(f, 0, b, 2 * STOREY + 1, wallOut);
                put(f, ww - 1, b, 2 * STOREY + 1, wallOut);
            }
            for (int a = 0; a < ww; a++) put(f, a, len - 1, 2 * STOREY + 1, wallOut);
        }

        // The balcony, level with storey 1.
        rect(f, 1, ww - 2, 0, 2, 1, STOREY, riser);
        int stageB = len - 2 - 5;
        int n = Math.max(0, (stageB - 3) / 2);
        short seat = stairs(Material.SPRUCE_STAIRS, f.face(0, -1));
        short step = stairs(Material.OAK_STAIRS, f.face(0, -1));
        int hPrev = STOREY;
        for (int i = 0; i < n; i++) {
            int h = Math.max(0, STOREY - 1 - i);
            int b = 3 + 2 * i;
            for (int a = 1; a <= ww - 2; a++) {
                if (h > 0) {
                    column(f, a, b, 1, h, riser);
                    column(f, a, b + 1, 1, h, riser);
                }
                boolean aisle = a <= 2 || a >= ww - 3;
                if (aisle) {
                    if (h < hPrev) put(f, a, b, h + 1, step);
                } else {
                    put(f, a, b + 1, h + 1, seat);
                }
            }
            hPrev = h;
        }
        rect(f, 1, ww - 2, stageB, stageB + 3, 1, 1, block(Material.DARK_OAK_PLANKS));
        put(f, ww / 2, stageB + 1, 2, facing(Material.LECTERN, f.face(0, -1)));
        rect(f, 3, ww - 4, len - 2, len - 2, 3, 8, block(Material.WHITE_WOOL));
        for (int a = 3; a < ww - 1; a += 6) {
            for (int b = 3; b < len - 1; b += 6) lights.add(new int[]{ f.u(a, b), f.v(a, b), 2 * STOREY });
        }
    }
}
