package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * What's under a Level 143 city's streets (see {@link MaceCity}): sewers, an underground railway,
 * and the passages that tie them to the surface, the mine and the guard towers. Built after the
 * city's buildings, mineshaft, guard towers and farms, from an RNG of its own.
 *
 * <ul>
 *   <li><b>Sewers</b> (walkways on y46, headroom to y49, ceiling y50: below the deepest cellars,
 *   which stop at y55) under every street: the main streets, the ring street inside the wall and
 *   the side streets. Brick tunnels three wide, a water channel down the middle with a walkway
 *   either side, a torch now and then. Each runs one block off its street's line, so a walkway lies
 *   under the street itself: that's where the manholes come down, a ladder under a trapdoor set in
 *   the street. The main sewers drain through the city wall behind a grate, into the moat by a
 *   flooded culvert (a dry moat's sewers run on under it to a grated outfall in the field
 *   beyond). A few maintenance rooms open off the tunnels.</li>
 *   <li><b>The railway</b> (rails on y40, ceiling y43, between the sewers and the mineshaft's
 *   first level, whose ceiling is y37): a line under each main street from near its gate to a
 *   central station under the crossing, where the four lines end at platforms. Stairs climb from the
 *   station to the street beside the main street, well away from the crossing, and at each line's
 *   far end a short stair climbs into the sewer above.</li>
 *   <li><b>Passages</b>: a service tunnel from the sewer to the mine entrance's ladder, and from
 *   the ring sewer up a ladder into each corner guard tower.</li>
 * </ul>
 *
 * <p>Everything is dug only through natural ground (and the wall's foundations), so nothing breaks
 * into a building, the moat or the mine except where meant to; every opening at the surface is a
 * trapdoor, a grate or a railed stairwell.
 */
final class CityUnderground {

    private static final long SALT = 0x5E3E2A11L;

    // Levels (world y)
    private static final int SEWER_BED = 45, SEWER_WALK = 46, SEWER_CEIL = 50;
    private static final int RAIL_BED = 39, RAIL = 40, RAIL_CEIL = 43, HALL_CEIL = 44;
    private static final int STREET = 63;
    private static final int GRID_LO = 36, GRID_HI = 64;
    private static final int HALL = 6;                    // the station's hall: 2 * HALL + 1 square
    private static final int RAIL_END = 26;               // lines end this far from the city's edge
    private static final int STEPS = STREET - RAIL + 1;   // stair blocks from the platforms to the street

    // Who dug a cell (the owner grid), so one part doesn't break into another
    private static final byte NONE = 0, SEWER = 1, TRACK = 2, STATION = 3, STAIR = 4, SHAFT = 5, PASSAGE = 6,
            ROOM = 7, OUTFALL = 8, KEEP = 9;

    /** One theme's underground materials. */
    private record Style(Material[] sewer, Material walk, Material[] rail, Material railFloor, Material hallFloor,
                         Material platform, Material stairs, Material fence, Material trapdoor, Material sign,
                         Material light, Material bumper) {}

    private static Style style(MaceCity.Theme t) {
        return switch (t) {
            case OCHRE -> new Style(new Material[]{Material.MUD_BRICKS, Material.MUD_BRICKS, Material.PACKED_MUD},
                    Material.PACKED_MUD, new Material[]{Material.CUT_SANDSTONE, Material.SMOOTH_SANDSTONE},
                    Material.SMOOTH_SANDSTONE, Material.SMOOTH_SANDSTONE, Material.SMOOTH_SANDSTONE_SLAB,
                    Material.SMOOTH_SANDSTONE_STAIRS, Material.ACACIA_FENCE, Material.ACACIA_TRAPDOOR,
                    Material.ACACIA_WALL_SIGN, Material.SHROOMLIGHT, Material.ACACIA_LOG);
            case ASHEN -> new Style(new Material[]{Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICKS,
                    Material.CRACKED_DEEPSLATE_BRICKS}, Material.POLISHED_DEEPSLATE,
                    new Material[]{Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS},
                    Material.POLISHED_BLACKSTONE, Material.POLISHED_DEEPSLATE, Material.POLISHED_DEEPSLATE_SLAB,
                    Material.DEEPSLATE_BRICK_STAIRS, Material.DARK_OAK_FENCE, Material.DARK_OAK_TRAPDOOR,
                    Material.DARK_OAK_WALL_SIGN, Material.GLOWSTONE, Material.DARK_OAK_LOG);
            default -> new Style(new Material[]{Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
                    Material.CRACKED_STONE_BRICKS}, Material.POLISHED_ANDESITE,
                    new Material[]{Material.BRICKS, Material.BRICKS}, Material.STONE_BRICKS,
                    Material.POLISHED_ANDESITE, Material.SMOOTH_STONE_SLAB, Material.STONE_BRICK_STAIRS,
                    Material.SPRUCE_FENCE, Material.SPRUCE_TRAPDOOR, Material.SPRUCE_WALL_SIGN,
                    Material.GLOWSTONE, Material.SPRUCE_LOG);
        };
    }

    /** A sewer's centre line: along x (at z = {@code at}) or along z (at x = {@code at}), from..to inclusive. */
    private record Line(boolean alongX, int at, int from, int to) {
        int x(int u, int v) { return alongX ? u : v; }
        int z(int u, int v) { return alongX ? v : u; }
    }

    private final MaceCity c;
    private final int[] entrance;
    private final Random rng;
    private final Style st;
    private final int m, mid, k, p, side;
    private final byte[] owner;
    private final Set<Material> natural = EnumSet.noneOf(Material.class);
    private final List<Line> lines = new ArrayList<>();
    private final List<int[]> manholes = new ArrayList<>();
    private String station = "Central";

    CityUnderground(MaceCity c, int[] entrance) {
        this.c = c;
        this.entrance = entrance;
        this.rng = new Random(c.plan.seed() ^ SALT);
        this.st = style(c.theme);
        this.m = c.mapLength;
        this.mid = m / 2;
        this.k = mid - 1;          // both networks run one block off the main streets' middle
        this.p = c.pathExtends;
        this.side = m + 1;
        this.owner = new byte[side * side * (GRID_HI - GRID_LO)];
        natural.addAll(c.theme.underground);
        natural.addAll(List.of(Material.STONE, Material.DIRT, Material.GRAVEL, Material.SAND, Material.SANDSTONE,
                Material.ANDESITE, Material.GRANITE, Material.DIORITE, Material.TUFF, Material.DEEPSLATE,
                Material.TERRACOTTA, Material.COAL_ORE, Material.IRON_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE,
                Material.GOLD_ORE, Material.DIAMOND_ORE, c.theme.soil, c.theme.rock));
    }

    void build() {
        if (m < 90) return;
        if (entrance[0] >= 0) {   // the mine's ladder and the stone behind it stay as they are
            for (int y = GRID_LO; y < GRID_HI; y++) {
                claim(entrance[0], y, entrance[1], KEEP);
                claim(entrance[0], y, entrance[1] + 1, KEEP);
            }
        }
        station = words("adjectives") + " Cross";
        sewers();
        railway();
        for (int[] d : DIRS) stationStairs(d);
        hallFittings();
        outfalls();
        manholes();
        if (entrance[0] >= 0) mineTunnel();
        towerPassages();
        rooms();
        sewerLights();
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};   // E, W, S, N

    // ── The owner grid and digging ────────────────────────────────────────

    private boolean inGrid(int x, int y, int z) {
        return x >= 0 && x <= m && z >= 0 && z <= m && y >= GRID_LO && y < GRID_HI;
    }

    private byte own(int x, int y, int z) {
        return inGrid(x, y, z) ? owner[((y - GRID_LO) * side + z) * side + x] : KEEP;
    }

    private void claim(int x, int y, int z, byte part) {
        if (inGrid(x, y, z)) owner[((y - GRID_LO) * side + z) * side + x] = part;
    }

    /** Untouched ground: the theme's rock and ore, and below y53 the wall's (and towers') foundations. */
    private boolean natural(int x, int y, int z, boolean wallOk) {
        Material t = c.type(x, y, z);
        return natural.contains(t) || (wallOk || y <= 52) && t == c.wall.getMaterial() && y < STREET;
    }

    private boolean air(int x, int y, int z) {
        return c.type(x, y, z).isAir();
    }

    /** Whether a part may dig here: natural ground, or a cell of one of the parts in {@code over} (a bit mask). */
    private boolean diggable(int x, int y, int z, int over, boolean wallOk) {
        byte o = own(x, y, z);
        if (o == NONE) return natural(x, y, z, wallOk);
        return o != KEEP && (over & (1 << o)) != 0;
    }

    private void put(int x, int y, int z, @Nullable BlockData b, byte part) {
        c.set(x, y, z, b);
        claim(x, y, z, part);
    }

    private void dig(int x, int y, int z, @Nullable BlockData b, byte part, int over) {
        if (diggable(x, y, z, over | (1 << part), false)) put(x, y, z, b, part);
    }

    /** A lining block, into natural ground only: anything else there is already solid, or ours. */
    private void line(int x, int y, int z, BlockData b, byte part) {
        if (own(x, y, z) == NONE && natural(x, y, z, false)) put(x, y, z, b, part);
    }

    private BlockData sewerBrick() {
        return c.data(st.sewer()[rng.nextInt(st.sewer().length)]);
    }

    private BlockData railBrick() {
        return c.data(rng.nextInt(6) == 0 ? st.rail()[1] : st.rail()[0]);
    }

    private BlockData water() {
        return c.data(Material.WATER);
    }

    private static BlockData facing(Material m, BlockFace f) {
        BlockData b = m.createBlockData();
        if (b instanceof Directional d) d.setFacing(f);
        return b;
    }

    private static BlockData stairs(Material m, BlockFace f) {
        Stairs s = (Stairs) m.createBlockData();
        s.setFacing(f);
        s.setHalf(Bisected.Half.BOTTOM);
        return s;
    }

    private static BlockData trapdoor(Material m, BlockFace f) {
        TrapDoor t = (TrapDoor) m.createBlockData();
        t.setFacing(f);
        t.setHalf(Bisected.Half.TOP);
        t.setOpen(false);
        return t;
    }

    private static BlockData bars(boolean wet) {
        BlockData b = Material.IRON_BARS.createBlockData();
        if (b instanceof Waterlogged w) w.setWaterlogged(wet);
        return b;
    }

    private static BlockFace face(int dx, int dz) {
        return dx > 0 ? BlockFace.EAST : dx < 0 ? BlockFace.WEST : dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private String words(String list) {
        String w = String.valueOf(c.lib.words.line(list, rng)).trim();
        return w.isEmpty() || w.equals("null") ? "Market" : w;
    }

    private void sign(int x, int y, int z, BlockFace f, String... text) {
        c.set(x, y, z, facing(st.sign(), f));
        String[] lines = {"", "", "", ""};
        for (int i = 0; i < text.length && i < 4; i++) lines[i] = text[i];
        c.signs.put(MaceCity.key(x, y, z), lines);
    }

    // ── Sewers ────────────────────────────────────────────────────────────

    /** Which sides' main sewers drain out through the wall: all but the north, unless its moat takes them. */
    private boolean outfall(int[] d) {
        if (!c.hasMoat()) return false;
        boolean wet = c.moatType.equals("Water") || c.moatType.equals("Drop to Bedrock");
        return wet || d[1] >= 0;
    }

    private void sewers() {
        int bs = MaceCity.BLOCK_START, up = m - 2 * bs, half = up / 2;
        int lo = half - (p + 1), hi = half + (p + 1);
        int ring0 = bs - 1, ring1 = m - bs - 1;   // every line runs one off its street, so they meet
        // the main streets, carried on through the wall where they drain
        lines.add(new Line(true, k, outfall(DIRS[1]) ? 16 : ring0, outfall(DIRS[0]) ? m - 16 : ring1));
        lines.add(new Line(false, k, outfall(DIRS[3]) ? 16 : ring0, outfall(DIRS[2]) ? m - 16 : ring1));
        // the ring street inside the wall
        lines.add(new Line(true, ring0, ring0, ring1));
        lines.add(new Line(true, ring1, ring0, ring1));
        lines.add(new Line(false, ring0, ring0, ring1));
        lines.add(new Line(false, ring1, ring0, ring1));
        // the side streets, each meeting the main sewer where it meets the main street
        for (int[] s : c.splits) {
            int from = bs + s[2] - 1, to = bs + s[3] - 1;
            if (s[2] == hi) from = k;
            if (s[3] == lo) to = k;
            lines.add(new Line(s[0] == 0, bs + s[1] - 1, from, to));
        }
        boolean[][] centre = new boolean[side][side];
        for (Line l : lines) {
            for (int u = Math.max(0, l.from()); u <= Math.min(m, l.to()); u++) {
                int x = l.x(u, l.at()), z = l.z(u, l.at());
                if (x >= 0 && x <= m && z >= 0 && z <= m) centre[x][z] = true;
            }
        }
        int[][] dist = distance(centre, 2);
        for (int x = 0; x <= m; x++) {
            for (int z = 0; z <= m; z++) {
                if (dist[x][z] == 0) continue;
                if (dist[x][z] == 3) {
                    for (int y = SEWER_BED; y <= SEWER_CEIL; y++) line(x, y, z, sewerBrick(), SEWER);
                    continue;
                }
                dig(x, SEWER_BED, z, sewerBrick(), SEWER, 0);
                dig(x, SEWER_WALK, z, centre[x][z] ? water() : c.data(st.walk()), SEWER, 0);
                for (int y = SEWER_WALK + 1; y < SEWER_CEIL; y++) dig(x, y, z, null, SEWER, 0);
                dig(x, SEWER_CEIL, z, sewerBrick(), SEWER, 0);
            }
        }
    }

    /**
     * Chebyshev distance + 1 from the marked cells, up to {@code r} + 1 (0: farther). So 1 is a
     * marked cell, 2 its ring, and so on.
     */
    private int[][] distance(boolean[][] marked, int r) {
        int[][] d = new int[side][side];
        for (int x = 0; x <= m; x++) {
            for (int z = 0; z <= m; z++) {
                if (!marked[x][z]) continue;
                for (int i = -r; i <= r; i++) {
                    for (int j = -r; j <= r; j++) {
                        int px = x + i, pz = z + j;
                        if (px < 0 || pz < 0 || px > m || pz > m) continue;
                        int v = Math.max(Math.abs(i), Math.abs(j)) + 1;
                        if (d[px][pz] == 0 || v < d[px][pz]) d[px][pz] = v;
                    }
                }
            }
        }
        return d;
    }

    private boolean walkway(int x, int z) {
        return own(x, SEWER_WALK + 1, z) == SEWER && air(x, SEWER_WALK + 1, z)
                && c.type(x, SEWER_WALK, z) == st.walk();
    }

    private void sewerLights() {
        int n = 0;
        for (Line l : lines) {
            for (int u = l.from() + 4; u <= l.to() - 2; u += 9) {
                int s = (n++ & 1) == 0 ? 1 : -1;
                int x = l.x(u, l.at() + s), z = l.z(u, l.at() + s);
                int wx = l.x(u, l.at() + 2 * s), wz = l.z(u, l.at() + 2 * s);
                int y = SEWER_WALK + 2;
                if (!walkway(x, z) || !air(x, y, z) || air(wx, y, wz) || own(wx, y, wz) != SEWER) continue;
                put(x, y, z, facing(Material.WALL_TORCH, face(x - wx, z - wz)), SEWER);
            }
        }
    }

    // ── The railway and its station ───────────────────────────────────────

    private int lineEnd(int[] d) {
        return d[0] + d[1] > 0 ? m - RAIL_END : RAIL_END;
    }

    private void railway() {
        boolean[][] centre = new boolean[side][side];
        for (int[] d : DIRS) {
            int end = lineEnd(d);
            for (int u = k + d[0] * (HALL + 1) + d[1] * (HALL + 1); u != end + d[0] + d[1]; u += d[0] + d[1]) {
                centre[d[0] != 0 ? u : k][d[0] != 0 ? k : u] = true;
            }
        }
        int[][] dist = distance(centre, 2);
        for (int x = 0; x <= m; x++) {
            for (int z = 0; z <= m; z++) {
                if (dist[x][z] == 0) continue;
                if (dist[x][z] == 3) {
                    for (int y = RAIL_BED; y <= RAIL_CEIL; y++) line(x, y, z, railBrick(), TRACK);
                    continue;
                }
                dig(x, RAIL_BED, z, c.data(st.railFloor()), TRACK, 0);
                for (int y = RAIL; y < RAIL_CEIL; y++) dig(x, y, z, null, TRACK, 0);
                dig(x, RAIL_CEIL, z, railBrick(), TRACK, 0);
            }
        }
        // the hall, over the lines' first blocks
        int over = 1 << TRACK;
        for (int x = k - HALL - 1; x <= k + HALL + 1; x++) {
            for (int z = k - HALL - 1; z <= k + HALL + 1; z++) {
                boolean wall = Math.abs(x - k) > HALL || Math.abs(z - k) > HALL;
                for (int y = RAIL_BED; y <= HALL_CEIL; y++) {
                    if (wall) line(x, y, z, railBrick(), STATION);
                    else if (y == RAIL_BED) dig(x, y, z, c.data(st.hallFloor()), STATION, over);
                    else if (y == HALL_CEIL) dig(x, y, z, railBrick(), STATION, over);
                    else dig(x, y, z, y == RAIL ? platform() : null, STATION, over);
                }
            }
        }
        // rails: from 3 short of the middle out to a buffer at the far end; lights every few blocks
        for (int[] d : DIRS) {
            int end = lineEnd(d), step = d[0] + d[1];
            int x0 = k + 2 * d[0], z0 = k + 2 * d[1];
            put(x0, RAIL, z0, c.data(st.bumper()), STATION);
            for (int u = 3; ; u++) {
                int x = k + u * d[0], z = k + u * d[1];
                int along = d[0] != 0 ? x : z;
                if (along == end) {
                    if (own(x, RAIL, z) == TRACK) put(x, RAIL, z, c.data(st.bumper()), TRACK);
                    break;
                }
                if (air(x, RAIL, z) || c.type(x, RAIL, z) == st.platform()) put(x, RAIL, z, c.data(Material.RAIL), own(x, RAIL, z));
                if (u > HALL + 2 && u % 7 == 0 && own(x, RAIL_CEIL, z) == TRACK) put(x, RAIL_CEIL, z, c.data(st.light()), TRACK);
                if (Math.abs(along - k) > m) break;
            }
            railToSewer(d, end, step);
        }
    }

    private BlockData platform() {
        Slab s = (Slab) st.platform().createBlockData();
        s.setType(Slab.Type.BOTTOM);
        return s;
    }

    /** Lanterns, the station's name and the lines' names. */
    private void hallFittings() {
        String[] names = {"East", "West", "South", "North"};
        for (int i = 0; i < 4; i++) {
            int[] d = DIRS[i];
            // the station's name on the wall to the line's left, the line's beside its mouth
            int lx = k + d[0] * HALL + d[1] * 3, lz = k + d[1] * HALL - d[0] * 3;
            int wx = lx + d[0], wz = lz + d[1];
            if (air(lx, RAIL + 2, lz) && !air(wx, RAIL + 2, wz)) {
                sign(lx, RAIL + 2, lz, face(-d[0], -d[1]), "", station, "Station", "");
            }
            int nx = k + d[0] * HALL + d[1] * 2, nz = k + d[1] * HALL - d[0] * 2;
            if (air(nx, RAIL + 2, nz) && !air(nx + d[0], RAIL + 2, nz + d[1])) {
                sign(nx, RAIL + 2, nz, face(-d[0], -d[1]), names[i] + " Line", "to the", names[i] + " Gate", "");
            }
        }
        for (int x = k - 4; x <= k + 4; x += 4) {
            for (int z = k - 4; z <= k + 4; z += 4) {
                if (x == k && z == k) continue;
                if (air(x, HALL_CEIL - 1, z) && own(x, HALL_CEIL - 1, z) == STATION && own(x, HALL_CEIL, z) == STATION
                        && !air(x, HALL_CEIL, z)) {
                    Lantern l = (Lantern) Material.LANTERN.createBlockData();
                    l.setHanging(true);
                    put(x, HALL_CEIL - 1, z, l, STATION);
                }
            }
        }
    }

    /**
     * At a line's far end, a stair from the track's side up through the ceiling onto the sewer's
     * walkway above: seven steps, rising toward the gate.
     */
    private void railToSewer(int[] d, int end, int step) {
        int n = SEWER_WALK - RAIL + 1;
        int lat = k + 1;
        int over = (1 << TRACK) | (1 << SEWER);
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int u = end - step * (n - i), s = RAIL + i;
            int x = d[0] != 0 ? u : lat, z = d[0] != 0 ? lat : u;
            for (int y = s; y <= s + 3; y++) {
                if (!diggable(x, y, z, over, false)) return;
            }
            if (i == n - 1 && !walkway(x, z)) return;
            cells.add(new int[]{x, z, s});
        }
        BlockFace up = face(d[0], d[1]);
        for (int[] cell : cells) {
            int x = cell[0], z = cell[1], s = cell[2];
            put(x, s, z, stairs(st.stairs(), up), STAIR);
            for (int y = s + 1; y <= s + 3; y++) {
                if (y < SEWER_WALK + 1 || own(x, y, z) != SEWER) put(x, y, z, null, STAIR);
            }
            // where the stairwell opens beside the channel, a weir and a rail keep the water and walkers out
            int cx = d[0] != 0 ? x : k, cz = d[0] != 0 ? k : z;
            if (s + 3 >= SEWER_WALK && s < SEWER_WALK) {
                put(cx, SEWER_WALK, cz, sewerBrick(), SEWER);
                put(cx, SEWER_WALK + 1, cz, c.data(st.fence()), SEWER);
            }
        }
        // the walkway stops at a rail where the stairwell begins
        int[] first = cells.get(SEWER_WALK - 3 - RAIL);
        int bx = first[0] - d[0], bz = first[1] - d[1];
        if (walkway(bx, bz)) put(bx, SEWER_WALK + 1, bz, c.data(st.fence()), SEWER);
        int sx = cells.get(0)[0] - d[0] * 2, sz = cells.get(0)[1] - d[1] * 2;
        int ox = d[0] != 0 ? sx : k - 2, oz = d[0] != 0 ? k - 2 : sz;
        int ix = d[0] != 0 ? sx : k - 1, iz = d[0] != 0 ? k - 1 : sz;
        if (air(ix, RAIL + 1, iz) && !air(ox, RAIL + 1, oz)) {
            sign(ix, RAIL + 1, iz, face(ix - ox, iz - oz), "", "Sewer", "Stairs", "");
        }
    }

    /**
     * Stairs from the station up to the street beside a main street (on its south or east side,
     * clear of the sewer that runs under the street's middle), climbing away from the crossing:
     * the first that fits, starting from inside the hall. The opening is railed on three sides.
     */
    private void stationStairs(int[] d) {
        int step = d[0] + d[1];
        boolean alongX = d[0] != 0;
        int r = mid + p + 1;
        for (int ub = k - step * (HALL - 1); Math.abs(ub - k) <= HALL; ub += step) {
            if (tryStairs(alongX, step, r, ub)) return;
        }
    }

    private boolean tryStairs(boolean alongX, int step, int r, int ub) {
        int over = 1 << STATION;
        Map<Long, BlockData> inside = new LinkedHashMap<>();
        List<int[]> lining = new ArrayList<>(), rails = new ArrayList<>();
        BlockFace up = alongX ? face(step, 0) : face(0, step);
        // the step before the first must be the hall's floor
        int bx = alongX ? ub - step : r, bz = alongX ? r : ub - step;
        if (own(bx, RAIL, bz) != STATION || !air(bx, RAIL + 1, bz)) return false;
        for (int i = 0; i < STEPS; i++) {
            int u = ub + step * i, s = RAIL + i;
            int x = alongX ? u : r, z = alongX ? r : u;
            for (int y = s; y <= s + 3; y++) {
                if (y < STREET) {
                    if (!diggable(x, y, z, over, false)) return false;
                } else if (y == STREET) {
                    if (!street(x, z)) return false;
                } else if (!air(x, y, z)) {
                    return false;
                }
                inside.put(MaceCity.key(x, y, z), y == s ? stairs(st.stairs(), up) : null);
            }
            int[][] around = {{x + (alongX ? 0 : 1), z + (alongX ? 1 : 0)}, {x - (alongX ? 0 : 1), z - (alongX ? 1 : 0)}};
            for (int y = Math.max(RAIL_BED, s - 1); y <= Math.min(STREET - 1, s + 4); y++) {
                for (int[] a : around) lining.add(new int[]{a[0], y, a[1]});
            }
            if (s + 4 < STREET) lining.add(new int[]{x, s + 4, z});
            if (s - 1 > RAIL_BED) lining.add(new int[]{x, s - 1, z});
            if (s >= STREET - 4 && s < STREET) {   // the opening (and the step before it): railed
                for (int[] a : around) rails.add(new int[]{a[0], STREET + 1, a[1]});
                if (s == STREET - 4) rails.add(new int[]{x, STREET + 1, z});
            }
        }
        // off the top step, onto the street
        int ex = alongX ? ub + step * STEPS : r, ez = alongX ? r : ub + step * STEPS;
        if (air(ex, STREET, ez) || !air(ex, STREET + 1, ez) || !air(ex, STREET + 2, ez)) return false;
        for (int[] a : lining) {
            if (inside.containsKey(MaceCity.key(a[0], a[1], a[2]))) continue;
            byte o = own(a[0], a[1], a[2]);
            if (o == STATION) continue;
            if (air(a[0], a[1], a[2])) return false;
        }
        List<int[]> fences = new ArrayList<>();
        for (int[] a : rails) {
            if (inside.containsKey(MaceCity.key(a[0], a[1], a[2]))) continue;
            Material here = c.type(a[0], a[1], a[2]), below = c.type(a[0], STREET, a[2]);
            if (below.isAir() || inside.containsKey(MaceCity.key(a[0], STREET, a[2]))) return false;
            if (here.isAir() || c.pathAlternative != null && here == c.pathAlternative.getMaterial()) fences.add(a);
            else if (!here.isOccluding()) return false;
        }
        // dig it
        for (Map.Entry<Long, BlockData> e : inside.entrySet()) {
            long key = e.getKey();
            put(MaceCity.keyX(key), MaceCity.keyY(key), MaceCity.keyZ(key), e.getValue(), STAIR);
        }
        for (int[] a : lining) line(a[0], a[1], a[2], railBrick(), STAIR);
        for (int i = 6; i < STEPS - 4; i += 6) {   // lights in the stairwell's ceiling
            int x = alongX ? ub + step * i : r, z = alongX ? r : ub + step * i, y = RAIL + i + 4;
            if (own(x, y, z) == STAIR && !air(x, y, z)) put(x, y, z, c.data(st.light()), STAIR);
        }
        for (int[] a : fences) c.set(a[0], a[1], a[2], c.data(st.fence()));
        // the station's name on a post at the head of the stairs
        int hx = alongX ? ub + step * (STEPS - 5) : r, hz = alongX ? r : ub + step * (STEPS - 5);
        if (c.type(hx, STREET + 1, hz) == st.fence() && air(hx, STREET + 2, hz)) {
            org.bukkit.block.data.type.Sign post = (org.bukkit.block.data.type.Sign)
                    Material.valueOf(st.sign().name().replace("_WALL_SIGN", "_SIGN")).createBlockData();
            post.setRotation(up);
            c.set(hx, STREET + 2, hz, post);
            c.signs.put(MaceCity.key(hx, STREET + 2, hz), new String[]{"", station, "Station", ""});
        }
        return true;
    }

    /** A street cell: paved (or the ground of the street's edge), with nothing standing on it. */
    private boolean street(int x, int z) {
        Material t = c.type(x, STREET, z);
        return (t == c.pathBlock.getMaterial() || t == c.ground.getMaterial()) && air(x, STREET + 1, z);
    }

    // ── Outfalls: the main sewers drain through the wall ──────────────────

    private void outfalls() {
        for (int[] d : DIRS) {
            if (!outfall(d)) continue;
            // a = distance in from the city's edge on that side; l = across the sewer
            boolean dry = !c.moatType.equals("Water") && !c.moatType.equals("Drop to Bedrock");
            // the grate, in the wall's outer face
            for (int l = -1; l <= 1; l++) {
                for (int y = SEWER_WALK; y < SEWER_CEIL; y++) {
                    int[] q = at(d, 14, k + l);
                    if (y == SEWER_WALK && l != 0) put(q[0], y, q[1], sewerBrick(), OUTFALL);
                    else put(q[0], y, q[1], bars(false), OUTFALL);
                }
            }
            if (c.moatType.equals("Drop to Bedrock")) continue;   // it opens on the chasm
            // beyond it a flooded culvert, and a riser up into the moat (or past a dry one, into the field)
            List<int[]> wet = new ArrayList<>();
            int riserLo = dry ? 2 : 11, riserHi = dry ? 4 : 13, culvertLo = dry ? 3 : 11, top = dry ? STREET - 1 : 58;
            if (dry) {
                for (int a = riserLo; a <= riserHi; a++) {
                    for (int l = -1; l <= 1; l++) {
                        int[] q = at(d, a, k + l);
                        if (!street(q[0], q[1]) && c.type(q[0], STREET, q[1]) != c.theme.ground) top = -1;
                    }
                }
                if (top < 0) continue;
            }
            for (int a = culvertLo; a <= 13; a++) {
                for (int l = -1; l <= 1; l++) {
                    for (int y = SEWER_WALK; y < SEWER_CEIL; y++) wet.add(new int[]{a, l, y});
                }
            }
            for (int a = riserLo; a <= riserHi; a++) {
                for (int l = -1; l <= 1; l++) {
                    for (int y = SEWER_CEIL; y <= top; y++) wet.add(new int[]{a, l, y});
                }
            }
            Set<Long> volume = new java.util.HashSet<>();
            for (int[] w : wet) {
                int[] q = at(d, w[0], k + w[1]);
                volume.add(MaceCity.key(q[0], w[2], q[1]));
            }
            for (long key : volume) {
                int x = MaceCity.keyX(key), y = MaceCity.keyY(key), z = MaceCity.keyZ(key);
                if (diggable(x, y, z, 0, true)) put(x, y, z, water(), OUTFALL);
            }
            for (long key : volume) {
                int x = MaceCity.keyX(key), y = MaceCity.keyY(key), z = MaceCity.keyZ(key);
                for (int[] n : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                    int nx = x + n[0], ny = y + n[1], nz = z + n[2];
                    if (volume.contains(MaceCity.key(nx, ny, nz))) continue;
                    if (own(nx, ny, nz) == NONE && natural(nx, ny, nz, true)) put(nx, ny, nz, sewerBrick(), OUTFALL);
                }
            }
            if (dry) {
                for (int a = riserLo; a <= riserHi; a++) {
                    for (int l = -1; l <= 1; l++) {
                        int[] q = at(d, a, k + l);
                        c.set(q[0], STREET, q[1], bars(true));
                    }
                }
            }
        }
    }

    /** City (x, z) at {@code a} in from the edge on side {@code d}, at {@code across} on the other axis. */
    private int[] at(int[] d, int a, int across) {
        int along = d[0] + d[1] > 0 ? m - a : a;
        return d[0] != 0 ? new int[]{along, across} : new int[]{across, along};
    }

    // ── Manholes ──────────────────────────────────────────────────────────

    /** Down from the street to a walkway: a ladder in a lined shaft, under a trapdoor in the paving. */
    private void manholes() {
        for (Line l : lines) {
            List<Integer> spots = new ArrayList<>();
            for (int u = l.from() + 3; u <= l.to() - 3; u += 12) spots.add(u);
            spots.add(l.to() - 3);
            for (int u0 : spots) {
                for (int o : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                    if (manhole(l, u0 + o)) break;
                }
            }
        }
    }

    private boolean manhole(Line l, int u) {
        int v = l.at() + 1, x = l.x(u, v), z = l.z(u, v);
        int sx = l.x(u, v + 1), sz = l.z(u, v + 1);   // the wall the ladder hangs on
        if (Math.max(Math.abs(x - mid), Math.abs(z - mid)) <= 5) return false;   // clear of the crossing
        for (int[] h : manholes) if (Math.max(Math.abs(h[0] - x), Math.abs(h[1] - z)) < 8) return false;
        if (!walkway(x, z) || c.type(x, STREET, z) != c.pathBlock.getMaterial()) return false;
        if (!air(x, STREET + 1, z) || !air(x, STREET + 2, z)) return false;
        if (own(x, SEWER_CEIL, z) != SEWER) return false;
        for (int y = SEWER_CEIL + 1; y < STREET; y++) if (!diggable(x, y, z, 0, false)) return false;
        for (int y = SEWER_WALK + 1; y < STREET; y++) {
            if (air(sx, y, sz) || own(sx, y, sz) != NONE && own(sx, y, sz) != SEWER) return false;
        }
        for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int y = SEWER_CEIL + 1; y < STREET; y++) if (air(x + n[0], y, z + n[1])) return false;
        }
        BlockFace f = face(x - sx, z - sz);
        BlockData ladder = facing(Material.LADDER, f);
        for (int y = SEWER_WALK + 1; y < STREET; y++) put(x, y, z, ladder, SHAFT);
        for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int y = SEWER_CEIL + 1; y < STREET; y++) line(x + n[0], y, z + n[1], sewerBrick(), SHAFT);
        }
        c.set(x, STREET, z, trapdoor(st.trapdoor(), f));
        manholes.add(new int[]{x, z});
        return true;
    }

    // ── Passages: to the mine's ladder, up into the guard towers ──────────

    /** The mine entrance's ladder runs down past the sewers: a short tunnel to its front. */
    private void mineTunnel() {
        int ex = entrance[0], ez = entrance[1] - 1;
        if (c.type(ex, SEWER_WALK + 1, ez + 1) != Material.LADDER || walkway(ex, ez)) return;
        List<int[]> path = route(ex, ez, false);
        if (path == null) return;
        tunnel(path, PASSAGE);
        int y = SEWER_WALK + 2;
        // a torch halfway, a sign where it leaves the sewer
        int[] half = path.get(path.size() / 2), last = path.get(path.size() - 1);
        for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (path.size() > 4 && air(half[0], y, half[1]) && !air(half[0] + n[0], y, half[1] + n[1])) {
                put(half[0], y, half[1], facing(Material.WALL_TORCH, face(-n[0], -n[1])), PASSAGE);
                break;
            }
        }
        for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (air(last[0], y, last[1]) && !air(last[0] + n[0], y, last[1] + n[1])
                    && !air(last[0] - n[0], y, last[1] - n[1])) {
                sign(last[0], y, last[1], face(-n[0], -n[1]), "", "To the", "Mine", "");
                break;
            }
        }
    }

    /**
     * A path at sewer level from (x, z) to the nearest sewer walkway, through natural ground (and
     * the wall's foundations if {@code wallOk}), ending with the cell of the sewer's lining it
     * breaks through. Null if there's none near.
     */
    private @Nullable List<int[]> route(int x0, int z0, boolean wallOk) {
        Map<Long, Long> from = new HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        long start = MaceCity.key(x0, 0, z0);
        from.put(start, start);
        queue.add(new int[]{x0, z0});
        while (!queue.isEmpty() && from.size() < 40_000) {
            int[] q = queue.poll();
            for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = q[0] + n[0], z = q[1] + n[1];
                if (walkway(x, z)) {
                    List<int[]> path = new ArrayList<>();
                    for (long kk = MaceCity.key(q[0], 0, q[1]); ; kk = from.get(kk)) {
                        path.add(0, new int[]{MaceCity.keyX(kk), MaceCity.keyZ(kk)});
                        if (kk == start) break;
                    }
                    return path;
                }
                long kk = MaceCity.key(x, 0, z);
                if (from.containsKey(kk) || Math.abs(x - x0) + Math.abs(z - z0) > 90) continue;
                if (!passable(x, z, wallOk)) continue;
                from.put(kk, MaceCity.key(q[0], 0, q[1]));
                queue.add(new int[]{x, z});
            }
        }
        return null;
    }

    /** Whether a passage may run through (x, z): natural all the way up, or the lining of a sewer it's about to enter. */
    private boolean passable(int x, int z, boolean wallOk) {
        boolean lining = true;
        for (int y = SEWER_BED; y <= SEWER_CEIL; y++) {
            if (!(own(x, y, z) == SEWER && !air(x, y, z))) lining = false;
        }
        if (lining) {
            for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (walkway(x + n[0], z + n[1])) return true;
            return false;
        }
        for (int y = SEWER_BED; y <= SEWER_CEIL + 1; y++) if (!diggable(x, y, z, 0, wallOk)) return false;
        return true;
    }

    /** A one-wide tunnel along the path at sewer level: floor, headroom, ceiling, lined. */
    private void tunnel(List<int[]> path, byte part) {
        int over = 1 << SEWER;
        for (int[] q : path) {
            int x = q[0], z = q[1];
            if (diggable(x, SEWER_WALK, z, over, true)) put(x, SEWER_WALK, z, c.data(st.walk()), part);
            for (int y = SEWER_WALK + 1; y < SEWER_CEIL; y++) {
                if (diggable(x, y, z, over, true) && c.type(x, y, z) != Material.LADDER) put(x, y, z, null, part);
            }
        }
        for (int[] q : path) {
            for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 0}}) {
                for (int y = SEWER_BED; y <= SEWER_CEIL; y++) {
                    if (n[0] == 0 && n[1] == 0 && y > SEWER_BED && y < SEWER_CEIL) continue;
                    line(q[0] + n[0], y, q[1] + n[1], sewerBrick(), part);
                }
            }
        }
    }

    /** From the ring sewer, a passage and a ladder up through each corner tower's base into its ground floor. */
    private void towerPassages() {
        int e = MaceCity.EDGE;
        Material wall = c.wall.getMaterial();
        for (int[] corner : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
            List<int[]> cells = new ArrayList<>();
            for (int a = e + 5; a <= e + 11; a++) {
                for (int b = e + 5; b <= e + 11; b++) {
                    cells.add(new int[]{corner[0] == 0 ? a : m - a, corner[1] == 0 ? b : m - b});
                }
            }
            cells.sort((q, r) -> Integer.compare(Math.abs(q[0] - mid) + Math.abs(q[1] - mid),
                    Math.abs(r[0] - mid) + Math.abs(r[1] - mid)));
            for (int[] q : cells) {
                int x = q[0], z = q[1];
                if (c.type(x, STREET, z) != wall || !air(x, STREET + 1, z) || !air(x, STREET + 2, z)) continue;
                // the ladder hangs on the side away from the city
                int ox = corner[0] == 0 ? -1 : 1, oz = 0;
                boolean ok = true;
                for (int y = SEWER_WALK; y < STREET && ok; y++) {
                    ok = diggable(x, y, z, 1 << SEWER, true) && !air(x + ox, y, z + oz)
                            && own(x + ox, y, z + oz) != KEEP;
                }
                if (!ok) continue;
                List<int[]> path = walkway(x, z) ? List.of(q) : route(x, z, true);
                if (path == null) continue;
                tunnel(path, PASSAGE);
                BlockData ladder = facing(Material.LADDER, face(-ox, -oz));
                for (int y = SEWER_WALK + 1; y < STREET; y++) put(x, y, z, ladder, SHAFT);
                for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    for (int y = SEWER_CEIL + 1; y < STREET; y++) line(x + n[0], y, z + n[1], sewerBrick(), SHAFT);
                }
                c.set(x, STREET, z, trapdoor(st.trapdoor(), face(-ox, -oz)));
                break;
            }
        }
    }

    // ── Maintenance rooms ─────────────────────────────────────────────────

    private void rooms() {
        int want = Math.max(2, Math.min(5, m / 100 + 1)), made = 0;
        List<int[]> placed = new ArrayList<>();
        List<Line> side = new ArrayList<>();
        for (Line l : lines) if (l.to() - l.from() > 14) side.add(l);
        for (int tries = 0; made < want && tries < 300 && !side.isEmpty(); tries++) {
            Line l = side.get(rng.nextInt(side.size()));
            int u = l.from() + 6 + rng.nextInt(Math.max(1, l.to() - l.from() - 12));
            int s = rng.nextBoolean() ? 1 : -1;
            int x = l.x(u, l.at()), z = l.z(u, l.at());
            boolean near = false;
            for (int[] q : placed) near |= Math.abs(q[0] - x) + Math.abs(q[1] - z) < 30;
            if (near) continue;
            if (room(l, u, s, made + 1)) {
                placed.add(new int[]{x, z});
                made++;
            }
        }
    }

    /** A room four deep and five wide off the sewer's side {@code s}, through a doorway at u. */
    private boolean room(Line l, int u, int s, int number) {
        int v0 = l.at();
        // check: the room and its lining all natural, the doorway in the sewer's lining off a walkway
        for (int du = -3; du <= 3; du++) {
            for (int dv = 3; dv <= 7; dv++) {
                int x = l.x(u + du, v0 + s * dv), z = l.z(u + du, v0 + s * dv);
                for (int y = SEWER_BED; y <= SEWER_CEIL + 1; y++) {
                    if (own(x, y, z) != NONE || !natural(x, y, z, false)) return false;
                }
            }
        }
        int dx = l.x(u, v0 + 2 * s), dz = l.z(u, v0 + 2 * s);
        if (!walkway(l.x(u, v0 + s), l.z(u, v0 + s)) || own(dx, SEWER_WALK + 1, dz) != SEWER || air(dx, SEWER_WALK + 1, dz)) {
            return false;
        }
        for (int du = -3; du <= 3; du++) {
            for (int dv = 3; dv <= 7; dv++) {
                int x = l.x(u + du, v0 + s * dv), z = l.z(u + du, v0 + s * dv);
                boolean edge = Math.abs(du) == 3 || dv == 7;
                for (int y = SEWER_BED; y <= SEWER_CEIL; y++) {
                    BlockData b = edge || y == SEWER_BED || y == SEWER_CEIL ? sewerBrick()
                            : y == SEWER_WALK ? c.data(st.walk()) : null;
                    put(x, y, z, b, ROOM);
                }
            }
        }
        put(dx, SEWER_WALK, dz, c.data(st.walk()), ROOM);
        put(dx, SEWER_WALK + 1, dz, null, ROOM);
        put(dx, SEWER_WALK + 2, dz, null, ROOM);
        // fittings: a chest of supplies, a workbench, barrels, a lantern, a name on the wall
        BlockFace in = l.alongX() ? face(0, -s) : face(-s, 0);   // facing back toward the sewer
        int y = SEWER_WALK + 1;
        int cx = l.x(u - 2, v0 + 6 * s), cz = l.z(u - 2, v0 + 6 * s);
        put(cx, y, cz, facing(Material.CHEST, in), ROOM);
        c.containers.put(MaceCity.key(cx, y, cz), supplies());
        put(l.x(u + 2, v0 + 6 * s), y, l.z(u + 2, v0 + 6 * s), c.data(Material.CRAFTING_TABLE), ROOM);
        put(l.x(u + 2, v0 + 5 * s), y, l.z(u + 2, v0 + 5 * s), facing(Material.BARREL, BlockFace.UP), ROOM);
        put(l.x(u - 2, v0 + 3 * s), y, l.z(u - 2, v0 + 3 * s), facing(Material.BARREL, BlockFace.UP), ROOM);
        put(l.x(u, v0 + 6 * s), y + 1, l.z(u, v0 + 6 * s),
                facing(Material.LEVER, l.alongX() ? face(0, -s) : face(-s, 0)), ROOM);
        Lantern lamp = (Lantern) Material.LANTERN.createBlockData();
        lamp.setHanging(true);
        put(l.x(u, v0 + 5 * s), SEWER_CEIL - 1, l.z(u, v0 + 5 * s), lamp, ROOM);
        int sx = l.x(u + 1, v0 + 3 * s), sz = l.z(u + 1, v0 + 3 * s);
        sign(sx, y + 1, sz, l.alongX() ? face(0, s) : face(s, 0), "Sewer Works", "Depot No. " + number, "", c.name);
        return true;
    }

    private ItemStack[] supplies() {
        ItemStack[] items = new ItemStack[27];
        Object[][] kinds = {{Material.TORCH, 8, 32}, {Material.LADDER, 4, 16}, {Material.RAIL, 4, 16},
            {Material.COAL, 2, 12}, {Material.IRON_INGOT, 1, 4}, {Material.BUCKET, 1, 1}, {Material.IRON_SHOVEL, 1, 1},
            {Material.STONE_BRICKS, 8, 32}, {Material.BREAD, 1, 4}, {Material.LANTERN, 1, 3}};
        for (int slot = 0; slot < 27; slot++) {
            if (rng.nextInt(3) != 0) continue;
            Object[] kind = kinds[rng.nextInt(kinds.length)];
            int lo = (int) kind[1], hi = (int) kind[2];
            items[slot] = new ItemStack((Material) kind[0], lo + rng.nextInt(hi - lo + 1));
        }
        return items;
    }
}
