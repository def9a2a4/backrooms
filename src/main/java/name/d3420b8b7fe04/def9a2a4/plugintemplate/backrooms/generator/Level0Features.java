package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;

import java.util.Random;

import static name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level0Chunk.hash;
import static name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level0Chunk.pick;
import static name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level0Chunk.stairs;

/**
 * Level 0's large set pieces, one per 128-block macro cell at most, kept
 * inside the cell so a chunk only ever consults its own cell:
 *
 * <ul>
 *   <li><b>Sunken rooms</b>: rectangles dug down below the lobby floor, lit
 *   from the ordinary ceiling. Flat ones get a wide staircase down one wall
 *   and a parapet around the rim, and sometimes Level 101 houses on the
 *   floor, with or without grass; stepped ones descend in terraces. Some also
 *   raise their ceiling and hang their lights on chains, down to the same
 *   y69 as every other light; only those.</li>
 *   <li><b>Pits</b>: a 25x25 shaft open to the void, with a 3-wide staircase
 *   of straight flights and square corner landings around its walls that
 *   simply runs out partway down.</li>
 *   <li><b>Office buildings</b> ({@link Level0Building}): oak, several
 *   storeys, standing free in a hall or embedded in the slab.</li>
 * </ul>
 */
final class Level0Features {

    static final int MACRO = 128;
    private static final int CELL_MARGIN = 8;
    private static final double SPAWN_CLEARANCE = 200.0;

    private static final int FLOOR_TOP_Y = Level0ChunkGenerator.AIR_MIN_Y - 1;
    private static final int CEILING_Y = Level0ChunkGenerator.CEILING_MIN_Y;

    private static final int PIT_SIZE = 25;
    private static final int PIT_BAND = 3;
    private static final int PIT_FLIGHT = PIT_SIZE - 2 * PIT_BAND;
    private static final int PIT_APRON = 4;
    private static final Material[][] PIT_STAIR_SETS = {
            { Material.SMOOTH_SANDSTONE, Material.SMOOTH_SANDSTONE_STAIRS },
            { Material.SMOOTH_RED_SANDSTONE, Material.SMOOTH_RED_SANDSTONE_STAIRS },
            { Material.OAK_PLANKS, Material.OAK_STAIRS },
            { Material.POLISHED_ANDESITE, Material.POLISHED_ANDESITE_STAIRS },
            { Material.STRIPPED_BIRCH_WOOD, Material.BIRCH_STAIRS },
    };

    enum Kind { NONE, ROOM, PIT, BUILDING }

    /** x0..x1 / z0..z1 inclusive: the room floor, or the pit shaft. */
    record Feature(Kind kind, int x0, int z0, int x1, int z1, long hash) {
        static final Feature NONE = new Feature(Kind.NONE, 0, 0, 0, 0, 0);
    }

    /**
     * Sunken-room layout derived from the feature hash. {@code raise} lifts the
     * ceiling above the lobby's; it is nonzero exactly for pendant-lit rooms,
     * whose lights hang on chains down to the lobby's light level. Every light
     * in the level stays at y69, and chains only hang under a raised ceiling.
     */
    private record Room(int depth, boolean stepped, int stepWidth, boolean pendant, int raise,
                        boolean grass, boolean houses, int stairDir) {}

    private Level0Features() {}

    static Feature featureAt(long seed, int cellX, int cellZ, int roomChance, int pitChance) {
        return featureAt(seed, cellX, cellZ, roomChance, pitChance, 0);
    }

    static Feature featureAt(long seed, int cellX, int cellZ, int roomChance, int pitChance, int buildingChance) {
        long h = hash(seed, cellX, cellZ, 0xFEA7L);
        int roll = pick(h, 0, 100);
        Kind kind = roll < pitChance ? Kind.PIT
                : roll < pitChance + roomChance ? Kind.ROOM
                : roll < pitChance + roomChance + buildingChance ? Kind.BUILDING
                : Kind.NONE;
        if (kind == Kind.NONE) return Feature.NONE;

        int cx0 = cellX * MACRO + CELL_MARGIN, cz0 = cellZ * MACRO + CELL_MARGIN;
        int avail = MACRO - 2 * CELL_MARGIN;
        Feature f;
        if (kind == Kind.BUILDING) {
            Level0Building b = Level0Building.get(h, avail);
            int x0 = cx0 + pick(h, 8, avail - b.worldSizeX() + 1);
            int z0 = cz0 + pick(h, 16, avail - b.worldSizeZ() + 1);
            f = new Feature(kind, x0, z0, x0 + b.worldSizeX() - 1, z0 + b.worldSizeZ() - 1, h);
        } else if (kind == Kind.PIT) {
            int span = PIT_SIZE + 2 * PIT_APRON;
            int x0 = cx0 + PIT_APRON + pick(h, 8, avail - span + 1);
            int z0 = cz0 + PIT_APRON + pick(h, 16, avail - span + 1);
            f = new Feature(kind, x0, z0, x0 + PIT_SIZE - 1, z0 + PIT_SIZE - 1, h);
        } else {
            // Multiples of 4 so room edges line up with the lobby's cell grid.
            int w = 24 + 4 * pick(h, 8, 13);
            int l = 24 + 4 * pick(h, 12, 13);
            int x0 = cx0 + 4 + 4 * pick(h, 16, (avail - 8 - w) / 4 + 1);
            int z0 = cz0 + 4 + 4 * pick(h, 20, (avail - 8 - l) / 4 + 1);
            f = new Feature(kind, x0, z0, x0 + w - 1, z0 + l - 1, h);
        }
        return nearSpawn(f) ? Feature.NONE : f;
    }

    private static boolean nearSpawn(Feature f) {
        double nx = Math.max(f.x0() - PIT_APRON, Math.min(0, f.x1() + PIT_APRON));
        double nz = Math.max(f.z0() - PIT_APRON, Math.min(0, f.z1() + PIT_APRON));
        return nx * nx + nz * nz < SPAWN_CLEARANCE * SPAWN_CLEARANCE;
    }

    private static Room room(Feature f) {
        long h = f.hash();
        int minSide = Math.min(f.x1() - f.x0(), f.z1() - f.z0()) + 1;
        boolean stepped = pick(h, 26, 100) < 30;
        boolean pendant = pick(h, 30, 100) < 40;
        int stepWidth = 2 + pick(h, 34, 2);
        int depth;
        if (stepped) {
            int maxDepth = Math.max(3, Math.min(10, (minSide - 8) / (2 * stepWidth)));
            depth = 3 + pick(h, 38, maxDepth - 2);
        } else {
            depth = 6 + pick(h, 38, 11);    // 6-16: the deepest floors are dim
        }
        depth = Math.min(depth, minSide - 4);
        boolean grass = !stepped && pick(h, 42, 100) < 40;
        boolean houses = !stepped && depth >= 8 && pick(h, 46, 100) < 60;
        int raise = pendant ? 4 + pick(h, 54, 13) : 0;   // up to y85: light_y in level_0.yml
        return new Room(depth, stepped, stepWidth, pendant, raise, grass, houses, pick(h, 50, 4));
    }

    static void render(Level0Chunk c, Feature f, Level101ChunkGenerator alpha, int houseChance) {
        switch (f.kind()) {
            case ROOM -> renderRoom(c, f, room(f), alpha, houseChance);
            case PIT -> renderPit(c, f);
            case BUILDING -> Level0Building.get(f.hash(), MACRO - 2 * CELL_MARGIN).render(c, f.x0(), f.z0());
            case NONE -> { }
        }
    }

    // ---------------------------------------------------------------
    //  Sunken rooms
    // ---------------------------------------------------------------

    /** Floor depth below the lobby floor at a column inside the room. */
    private static int level(Feature f, Room r, int wx, int wz) {
        if (!r.stepped()) return r.depth();
        int ring = Math.min(Math.min(wx - f.x0(), f.x1() - wx), Math.min(wz - f.z0(), f.z1() - wz));
        return Math.min(r.depth(), ring / r.stepWidth() + 1);
    }

    /**
     * Staircase coordinates: u runs along the stair wall (down the flight), v
     * away from that wall. Returns {wx, wz}.
     */
    private static int[] stairPos(Feature f, int dir, int u, int v) {
        return switch (dir) {
            case 0 -> new int[]{ f.x0() + u, f.z0() + v };
            case 1 -> new int[]{ f.x1() - u, f.z1() - v };
            case 2 -> new int[]{ f.x0() + v, f.z1() - u };
            default -> new int[]{ f.x1() - v, f.z0() + u };
        };
    }

    /** The direction back up the flight, which the stairs face. */
    private static BlockFace stairUp(int dir) {
        return switch (dir) {
            case 0 -> BlockFace.WEST;
            case 1 -> BlockFace.EAST;
            case 2 -> BlockFace.SOUTH;
            default -> BlockFace.NORTH;
        };
    }

    private static void renderRoom(Level0Chunk c, Feature f, Room r, Level101ChunkGenerator alpha, int houseChance) {
        // Rim cells where the staircase starts get no parapet.
        int[] gapA = stairPos(f, r.stairDir(), -1, 0);
        int[] gapB = stairPos(f, r.stairDir(), -1, 2);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = c.wx(x), wz = c.wz(z);
                int out = Math.max(Math.max(f.x0() - wx, wx - f.x1()), Math.max(f.z0() - wz, wz - f.z1()));
                if (out > 2) continue;

                if (out <= 0) {
                    int floorY = FLOOR_TOP_Y - level(f, r, wx, wz);
                    int ceilingY = CEILING_Y + r.raise();
                    for (int y = floorY + 1; y < ceilingY; y++) {
                        c.data.setBlock(x, y, z, Material.AIR);
                    }
                    c.data.setBlock(x, ceilingY, z, c.ceilingMats[x][z]);
                    c.ceilingY[x][z] = ceilingY;
                    if (r.grass()) {
                        c.data.setBlock(x, floorY, z, Material.GRASS_BLOCK);
                        for (int y = floorY - 3; y < floorY; y++) c.data.setBlock(x, y, z, Material.DIRT);
                        c.noCarpet[x][z] = true;
                    } else {
                        c.data.setBlock(x, floorY, z, c.floorMats[x][z]);
                    }
                    c.floorTop[x][z] = floorY;
                    continue;
                }

                // Apron: keep the approach clear of lobby furniture.
                c.clearRoom(x, z);
                if (out == 1 && !r.stepped()) {
                    // Line the drop with wall material, and stand a parapet on the rim.
                    for (int y = FLOOR_TOP_Y - r.depth() + 1; y < FLOOR_TOP_Y; y++) {
                        c.data.setBlock(x, y, z, c.wallMats[x][z]);
                    }
                    boolean gap = wx >= Math.min(gapA[0], gapB[0]) && wx <= Math.max(gapA[0], gapB[0])
                            && wz >= Math.min(gapA[1], gapB[1]) && wz <= Math.max(gapA[1], gapB[1]);
                    if (!gap) c.data.setBlock(x, FLOOR_TOP_Y + 1, z, c.wallMats[x][z]);
                }
            }
        }

        if (r.stepped()) return;

        // A 3-wide flight down one wall, solid underneath; its top step is level
        // with the rim, so climbing out never needs a jump.
        BlockFace up = stairUp(r.stairDir());
        int floorY = FLOOR_TOP_Y - r.depth();
        for (int k = 1; k <= r.depth(); k++) {
            int y = FLOOR_TOP_Y - k + 1;
            for (int v = 0; v < 3; v++) {
                int[] p = stairPos(f, r.stairDir(), k - 1, v);
                int x = c.lx(p[0]), z = c.lz(p[1]);
                if (x < 0 || z < 0) continue;
                c.data.setBlock(x, y, z, stairs(Material.OAK_STAIRS, up, Stairs.Half.BOTTOM));
                for (int fy = floorY + 1; fy < y; fy++) {
                    c.data.setBlock(x, fy, z, c.wallMats[x][z]);
                }
                c.noCarpet[x][z] = true;
            }
        }

        if (r.houses()) placeHouse(c, f, r, alpha, houseChance, floorY);
    }

    /** One Level 101 structure in this chunk if it lies wholly on the room floor, clear of the stairs. */
    private static void placeHouse(Level0Chunk c, Feature f, Room r, Level101ChunkGenerator alpha,
                                   int houseChance, int floorY) {
        int cx0 = c.wx(0), cz0 = c.wz(0), cx1 = cx0 + 15, cz1 = cz0 + 15;
        if (cx0 <= f.x0() || cx1 >= f.x1() || cz0 <= f.z0() || cz1 >= f.z1()) return;

        int[] s0 = stairPos(f, r.stairDir(), 0, 0);
        int[] s1 = stairPos(f, r.stairDir(), r.depth(), 3);
        int sx0 = Math.min(s0[0], s1[0]), sx1 = Math.max(s0[0], s1[0]);
        int sz0 = Math.min(s0[1], s1[1]), sz1 = Math.max(s0[1], s1[1]);
        if (cx0 <= sx1 && cx1 >= sx0 && cz0 <= sz1 && cz1 >= sz0) return;

        long h = hash(c.seed, c.chunkX, c.chunkZ, 0x40115EL);
        if (pick(h, 0, 100) >= houseChance) return;

        // Level 101 builds on its ground at GROUND_Y; shift that onto this floor.
        alpha.placeRandomStructure(new OffsetChunkData(c.data, floorY - Level101ChunkGenerator.GROUND_Y),
                new Random(h), true);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) c.noCarpet[x][z] = true;
        }
    }

    // ---------------------------------------------------------------
    //  Pits
    // ---------------------------------------------------------------

    private static void renderPit(Level0Chunk c, Feature f) {
        long h = f.hash();
        Material[] set = PIT_STAIR_SETS[pick(h, 24, PIT_STAIR_SETS.length)];
        int startCorner = pick(h, 28, 4);
        int maxDrop = 50 + pick(h, 32, 46);   // stays above the y -40 exit until you jump

        // Shaft (open from the floor through the bedrock), railing, apron.
        int[] start = cornerOrigin(startCorner);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = c.wx(x), wz = c.wz(z);
                int out = Math.max(Math.max(f.x0() - wx, wx - f.x1()), Math.max(f.z0() - wz, wz - f.z1()));
                if (out > PIT_APRON) continue;
                if (out <= 0) {
                    for (int y = c.data.getMinHeight(); y <= FLOOR_TOP_Y; y++) {
                        c.data.setBlock(x, y, z, Material.AIR);
                    }
                    c.clearRoom(x, z);
                    c.noCarpet[x][z] = true;
                    continue;
                }
                c.clearRoom(x, z);
                if (out == 1) {
                    // Railing, open where it meets the top landing.
                    int sx = wx - f.x0(), sz = wz - f.z0();
                    boolean gap = sx >= start[0] - 1 && sx <= start[0] + PIT_BAND
                            && sz >= start[1] - 1 && sz <= start[1] + PIT_BAND;
                    if (!gap) c.data.setBlock(x, FLOOR_TOP_Y + 1, z, c.wallMats[x][z]);
                }
            }
        }

        // Flights of PIT_FLIGHT steps between 3x3 corner landings, each step one
        // lower: a flight's first step is level with the landing above it, and
        // the landing below is one under its last, so it climbs without jumps.
        int y = FLOOR_TOP_Y;
        int corner = startCorner;
        placeLanding(c, f, corner, y, set[0]);
        while (true) {
            for (int u = 0; u < PIT_FLIGHT; u++, y--) {
                if (FLOOR_TOP_Y - y > maxDrop) return;
                for (int v = 0; v < PIT_BAND; v++) {
                    int[] p = flightPos(corner, u, v);
                    BlockFace up = flightUp(corner);
                    c.setWorld(f.x0() + p[0], y, f.z0() + p[1], stairs(set[1], up, Stairs.Half.BOTTOM));
                    // Upside-down stair beneath gives the flight a sloped underside.
                    c.setWorld(f.x0() + p[0], y - 1, f.z0() + p[1],
                            stairs(set[1], up.getOppositeFace(), Stairs.Half.TOP));
                }
            }
            if (FLOOR_TOP_Y - y > maxDrop) return;
            corner = (corner + 1) % 4;
            placeLanding(c, f, corner, y, set[0]);
        }
    }

    /** Shaft-local origin of a corner landing, clockwise from the north-west. */
    private static int[] cornerOrigin(int corner) {
        int far = PIT_SIZE - PIT_BAND;
        return switch (corner) {
            case 0 -> new int[]{ 0, 0 };
            case 1 -> new int[]{ far, 0 };
            case 2 -> new int[]{ far, far };
            default -> new int[]{ 0, far };
        };
    }

    private static void placeLanding(Level0Chunk c, Feature f, int corner, int y, Material material) {
        int[] o = cornerOrigin(corner);
        for (int dx = 0; dx < PIT_BAND; dx++) {
            for (int dz = 0; dz < PIT_BAND; dz++) {
                c.setWorld(f.x0() + o[0] + dx, y, f.z0() + o[1] + dz, material);
            }
        }
    }

    /** Shaft-local position of step u (0 = top), lane v, on the flight leaving {@code corner}. */
    private static int[] flightPos(int corner, int u, int v) {
        int far = PIT_SIZE - PIT_BAND;
        return switch (corner) {
            case 0 -> new int[]{ PIT_BAND + u, v };                         // north wall, heading east
            case 1 -> new int[]{ far + v, PIT_BAND + u };                   // east wall, heading south
            case 2 -> new int[]{ far - 1 - u, far + v };                    // south wall, heading west
            default -> new int[]{ v, far - 1 - u };                         // west wall, heading north
        };
    }

    private static BlockFace flightUp(int corner) {
        return switch (corner) {
            case 0 -> BlockFace.WEST;
            case 1 -> BlockFace.NORTH;
            case 2 -> BlockFace.EAST;
            default -> BlockFace.SOUTH;
        };
    }
}
