package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

import static name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level0Chunk.hash;
import static name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level0Chunk.pick;
import static name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level0Chunk.slab;
import static name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level0Chunk.stairs;

/**
 * Furnished districts of Level 0, one type per 48-block zone (3x3 chunks).
 * Everything is laid out from world coordinates within the zone, so each
 * chunk draws its own part of a layout that continues across chunk borders.
 * LOBBY zones keep the classic random walls and pillars (drawn by the
 * generator itself); the rest are office spaces gone wrong. The offices
 * and the control room borrow IronSource's control.shp, the same building
 * {@link Level0Building} follows: its rooms (offices, meeting and conference
 * rooms, server rooms, records, break rooms, toilets), its open-plan floor,
 * and its control room of screens and consoles, all on the lobby's one storey.
 */
final class Level0Zones {

    static final int ZONE = 48;
    private static final double SPAWN_CLEARANCE = 200.0;

    private static final int Y0 = Level0ChunkGenerator.AIR_MIN_Y;   // floor-standing y
    private static final int ROOM_TOP = Level0ChunkGenerator.CEILING_MIN_Y;

    enum Zone { LOBBY, CUBICLES, OPEN_PLAN, CAFETERIA, OFFICES, CONTROL, CORRIDORS }

    private Level0Zones() {}

    static Zone zoneAt(long seed, int zoneX, int zoneZ) {
        double nx = Math.max(zoneX * ZONE, Math.min(0, zoneX * ZONE + ZONE - 1));
        double nz = Math.max(zoneZ * ZONE, Math.min(0, zoneZ * ZONE + ZONE - 1));
        if (nx * nx + nz * nz < SPAWN_CLEARANCE * SPAWN_CLEARANCE) return Zone.LOBBY;

        int roll = pick(hash(seed, zoneX, zoneZ, 0x2011EL), 0, 100);
        if (roll < 70) return Zone.LOBBY;
        if (roll < 79) return Zone.CUBICLES;
        if (roll < 84) return Zone.OPEN_PLAN;
        if (roll < 92) return Zone.OFFICES;
        if (roll < 94) return Zone.CONTROL;
        if (roll < 99) return Zone.CORRIDORS;
        return Zone.CAFETERIA;
    }

    static void furnish(Level0Chunk c, Zone zone, int zoneX, int zoneZ) {
        long zh = hash(c.seed, zoneX, zoneZ, 0x2011F0L);
        boolean flip = (zh & 1) != 0;   // rows along z instead of x
        int zx0 = zoneX * ZONE, zz0 = zoneZ * ZONE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int lx = c.wx(x) - zx0, lz = c.wz(z) - zz0;
                switch (zone) {
                    case CUBICLES -> cubicle(c, x, z, flip ? lz : lx, flip ? lx : lz, flip, zx0, zz0);
                    case CAFETERIA -> cafeteria(c, x, z, flip ? lz : lx, flip ? lx : lz, flip);
                    case OPEN_PLAN -> openPlan(c, x, z, flip ? lz : lx, flip ? lx : lz, flip, zx0, zz0);
                    case OFFICES -> office(c, x, z, lx, lz, zx0, zz0);
                    case CONTROL -> control(c, x, z, lx, lz, zx0, zz0, zh);
                    case CORRIDORS -> corridor(c, x, z, flip ? lz : lx, flip ? lx : lz, zx0, zz0);
                    case LOBBY -> { }
                }
            }
        }
    }

    /** World facing of a direction given in zone (a, b) row coordinates. */
    private static BlockFace face(boolean flip, int da, int db) {
        if (da != 0) {
            if (flip) return da > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
            return da > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        if (flip) return db > 0 ? BlockFace.EAST : BlockFace.WEST;
        return db > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private static void wall(Level0Chunk c, int x, int z, int height) {
        for (int y = Y0; y < Y0 + height; y++) c.data.setBlock(x, y, z, c.wallMats[x][z]);
    }

    // ---------------------------------------------------------------
    //  Cubicle farm: back-to-back rows of 4-wide cubicles with low partitions
    // ---------------------------------------------------------------

    private static void cubicle(Level0Chunk c, int x, int z, int a, int b, boolean flip, int zx0, int zz0) {
        if (a < 2 || a > 46 || b < 2 || b > 41) return;
        int m = (b - 2) % 10;          // 0-1 aisle, 2-5 cubicle A, 6 back partition, 7-9 cubicle B
        if (m <= 1) return;
        int col = (a - 2) / 4, ai = (a - 2) % 4;
        int row = (b - 2) / 10;

        if (m == 6) {
            wall(c, x, z, 2);
            return;
        }
        boolean sideA = m < 6;
        long ch = hash(c.seed, zx0 + col * 4, zz0 + row * 10 + (sideA ? 0 : 1), 0xC0B1C1EL);
        if (ai == 0) {
            if (pick(ch, 0, 100) >= 8) wall(c, x, z, 2);   // the odd partition is missing
            return;
        }
        if (pick(ch, 8, 100) < 8) return;   // cleared-out cubicle

        int deskM = sideA ? 5 : 7, chairM = sideA ? 4 : 8;
        int awayFromDesk = sideA ? -1 : 1;
        if (m == deskM) {
            if (ai == 2 && pick(ch, 16, 100) < 12) {
                c.data.setBlock(x, Y0, z, Material.BOOKSHELF);   // LobbyBookshelfListener fills it
            } else {
                c.data.setBlock(x, Y0, z, slab(Material.OAK_SLAB, Slab.Type.TOP));
            }
        } else if (m == chairM && ai == 2) {
            BlockFace back = pick(ch, 24, 100) < 15
                    ? new BlockFace[]{ BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST }[pick(ch, 32, 4)]
                    : face(flip, 0, awayFromDesk);
            c.data.setBlock(x, Y0, z, stairs(Material.SPRUCE_STAIRS, back, Stairs.Half.BOTTOM));
        }
    }

    // ---------------------------------------------------------------
    //  Cafeteria: long tables with benches, a serving counter, bins
    // ---------------------------------------------------------------

    private static void cafeteria(Level0Chunk c, int x, int z, int a, int b, boolean flip) {
        c.noCarpet[x][z] = true;
        if (b == 1 && a >= 6 && a <= 41) {
            c.data.setBlock(x, Y0, z, Material.POLISHED_ANDESITE);
            return;
        }
        if (a < 4 || a > 43 || b < 4 || b > 39) return;
        int m = (b - 4) % 6;           // 1 bench, 2 table, 3 bench, others aisle
        boolean gap = (a - 4) % 9 == 8;
        if (m == 2 && !gap) {
            c.data.setBlock(x, Y0, z, slab(Material.SMOOTH_STONE_SLAB, Slab.Type.TOP));
        } else if ((m == 1 || m == 3) && !gap) {
            c.data.setBlock(x, Y0, z, slab(Material.OAK_SLAB, Slab.Type.BOTTOM));
        } else if (m == 5 && a % 12 == 7) {
            c.data.setBlock(x, Y0, z, Material.CAULDRON);
        }
    }

    // ---------------------------------------------------------------
    //  Open plan: a desk and its chair every 3 blocks, a walkway round
    // ---------------------------------------------------------------

    private static void openPlan(Level0Chunk c, int x, int z, int a, int b, boolean flip, int zx0, int zz0) {
        if (a < 2 || a > 45 || b < 2 || b > 44) return;
        int da = (a - 2) % 3, db = (b - 2) % 3;
        if (da == 2 || db == 2) return;
        long dh = hash(c.seed, zx0 + (a - 2) / 3 * 3, zz0 + (b - 2) / 3 * 3, 0x09E4F1A4L);
        if (pick(dh, 0, 100) < 7) return;   // a desk taken away
        if (db == 0) {
            c.data.setBlock(x, Y0, z, da == 1 && pick(dh, 8, 100) < 10
                    ? Material.BOOKSHELF.createBlockData()   // LobbyBookshelfListener fills it
                    : slab(Material.BIRCH_SLAB, Slab.Type.TOP));
        } else if (da == 0 && pick(dh, 16, 100) >= 10) {
            c.data.setBlock(x, Y0, z, stairs(Material.SPRUCE_STAIRS,
                    pick(dh, 24, 100) < 15 ? CW[pick(dh, 32, 4)] : face(flip, 0, 1), Stairs.Half.BOTTOM));
        }
    }

    // ---------------------------------------------------------------
    //  Offices: an 8-block grid of rooms with doorways (and the odd door),
    //  each room one of control.shp's, turned to face any way
    // ---------------------------------------------------------------

    private enum Room { OFFICE, MEETING, CONFERENCE, SERVER, RECORDS, BREAK, TOILETS, EMPTY }

    /** control.shp's weights, with conference rooms and the odd emptied room added in. */
    private static final Room[] ROOMS = {
            Room.OFFICE, Room.OFFICE, Room.OFFICE, Room.OFFICE, Room.OFFICE,
            Room.MEETING, Room.MEETING, Room.CONFERENCE, Room.SERVER, Room.RECORDS,
            Room.BREAK, Room.TOILETS, Room.EMPTY, Room.EMPTY,
    };

    private static final BlockFace[] CW = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
    private static final Material[] DESKS = { Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.BIRCH_SLAB, Material.DARK_OAK_SLAB };

    private static void office(Level0Chunk c, int x, int z, int lx, int lz, int zx0, int zz0) {
        boolean wallX = lx % 8 == 0, wallZ = lz % 8 == 0;
        if (wallX && wallZ) {
            wall(c, x, z, ROOM_TOP - Y0);
            return;
        }
        if (wallX || wallZ) {
            // Segment between two posts; offset 1-7 along it.
            int segA = wallX ? lx / 8 : lz / 8, segB = wallX ? lz / 8 : lx / 8;
            int offset = wallX ? lz % 8 : lx % 8;
            int doorAt = doorway(c.seed, zx0, zz0, segA, segB, wallX);
            wall(c, x, z, ROOM_TOP - Y0);
            if (doorAt > 0 && (offset == doorAt || offset == doorAt + 1)) {
                c.data.setBlock(x, Y0, z, Material.AIR);
                c.data.setBlock(x, Y0 + 1, z, Material.AIR);
                long sh = hash(c.seed, zx0 + segA * 8, zz0 + segB * 8, wallX ? 0x0FF1CEL : 0x0FF1CFL);
                if (offset == doorAt && pick(sh, 16, 100) < 30) {
                    BlockFace facing = wallX ? BlockFace.EAST : BlockFace.SOUTH;
                    c.data.setBlock(x, Y0, z, door(facing, Bisected.Half.BOTTOM));
                    c.data.setBlock(x, Y0 + 1, z, door(facing, Bisected.Half.TOP));
                }
            }
            return;
        }
        officeRoom(c, x, z, lx / 8, lz / 8, lx % 8, lz % 8, zx0, zz0);
    }

    /** Where a wall segment's doorway starts (2-5, two wide), or -1 for a closed wall. */
    private static int doorway(long seed, int zx0, int zz0, int segA, int segB, boolean wallX) {
        long sh = hash(seed, zx0 + segA * 8, zz0 + segB * 8, wallX ? 0x0FF1CEL : 0x0FF1CFL);
        return pick(sh, 0, 100) < 85 ? 2 + pick(sh, 8, 4) : -1;
    }

    /** True for a room cell just inside one of its doorways, which furniture must leave clear. */
    private static boolean byDoor(long seed, int zx0, int zz0, int i, int j, int ox, int oz) {
        return ox == 1 && near(doorway(seed, zx0, zz0, i, j, true), oz)
                || ox == 7 && near(doorway(seed, zx0, zz0, i + 1, j, true), oz)
                || oz == 1 && near(doorway(seed, zx0, zz0, j, i, false), ox)
                || oz == 7 && near(doorway(seed, zx0, zz0, j + 1, i, false), ox);
    }

    private static boolean near(int doorAt, int offset) {
        return doorAt > 0 && (offset == doorAt || offset == doorAt + 1);
    }

    /**
     * One cell (ox, oz: 1-7) of the room at grid (i, j). The room is laid out
     * in its own (u, v), v = 1 along its front wall, then turned one of four
     * ways; facings turn with it.
     */
    private static void officeRoom(Level0Chunk c, int x, int z, int i, int j, int ox, int oz, int zx0, int zz0) {
        long rh = hash(c.seed, zx0 + i * 8, zz0 + j * 8, 0x0FF1D0L);
        Room kind = ROOMS[pick(rh, 0, ROOMS.length)];
        int rot = pick(rh, 40, 4);
        int u = switch (rot) { case 0 -> ox; case 1 -> oz; case 2 -> 8 - ox; default -> 8 - oz; };
        int v = switch (rot) { case 0 -> oz; case 1 -> 8 - ox; case 2 -> 8 - oz; default -> ox; };
        if (kind == Room.TOILETS) {
            // Tiles, chequered, no carpet.
            c.data.setBlock(x, Y0 - 1, z, (u + v) % 2 == 0 ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_CONCRETE);
            c.noCarpet[x][z] = true;
        }
        if (byDoor(c.seed, zx0, zz0, i, j, ox, oz)) return;
        long ch = hash(rh, u, v, 0x0FF1D1L);   // this cell's own rolls
        Material desk = DESKS[pick(rh, 44, DESKS.length)];
        switch (kind) {
            case OFFICE -> {
                boolean second = pick(rh, 48, 100) < 35;
                if ((v == 2 || second && v == 5) && u >= 3 && u <= 5) {
                    c.data.setBlock(x, Y0, z, slab(desk, Slab.Type.TOP));
                    if (u == 5 && pick(ch, 0, 100) < 25) c.data.setBlock(x, Y0 + 1, z, Material.POTTED_DEAD_BUSH);
                } else if ((v == 3 || second && v == 6) && u == 4 && pick(ch, 0, 100) < 90) {
                    c.data.setBlock(x, Y0, z, stairs(Material.SPRUCE_STAIRS,
                            turn(pick(ch, 8, 100) < 15 ? CW[pick(ch, 16, 4)] : BlockFace.SOUTH, rot), Stairs.Half.BOTTOM));
                } else if (u == 7 && v == 7) {
                    filing(c, x, z, ch);
                } else if (u == 1 && v == 7 && pick(rh, 52, 100) < 30) {
                    c.data.setBlock(x, Y0, z, Material.BOOKSHELF);   // LobbyBookshelfListener fills it
                }
            }
            case MEETING -> {
                if (u == 4 && v >= 2 && v <= 6) {
                    c.data.setBlock(x, Y0, z, slab(Material.DARK_OAK_SLAB, Slab.Type.TOP));
                } else if ((u == 3 || u == 5) && v >= 2 && v <= 6 && pick(ch, 0, 100) < 80) {
                    c.data.setBlock(x, Y0, z, stairs(Material.SPRUCE_STAIRS,
                            turn(u == 3 ? BlockFace.WEST : BlockFace.EAST, rot), Stairs.Half.BOTTOM));
                }
            }
            case CONFERENCE -> {
                // A long table, chairs both sides, a lectern and the projector screen at the far end.
                if (v == 4 && u >= 2 && u <= 5) {
                    c.data.setBlock(x, Y0, z, slab(Material.DARK_OAK_SLAB, Slab.Type.TOP));
                } else if ((v == 3 || v == 5) && u >= 2 && u <= 5 && pick(ch, 0, 100) < 85) {
                    c.data.setBlock(x, Y0, z, stairs(Material.SPRUCE_STAIRS,
                            turn(v == 3 ? BlockFace.NORTH : BlockFace.SOUTH, rot), Stairs.Half.BOTTOM));
                } else if (u == 6 && v == 4) {
                    c.data.setBlock(x, Y0, z, facing(Material.LECTERN, turn(BlockFace.WEST, rot)));
                } else if (u == 7 && v >= 3 && v <= 5) {
                    for (int y = Y0 + 1; y < ROOM_TOP; y++) c.data.setBlock(x, y, z, Material.WHITE_WOOL);
                }
            }
            case SERVER, RECORDS -> {
                if ((u == 2 || u == 4 || u == 6) && v >= 2 && v <= 6) {
                    BlockFace aisle = turn(u == 2 ? BlockFace.EAST : BlockFace.WEST, rot);
                    for (int y = Y0; y < Y0 + 2; y++) {
                        if (kind == Room.RECORDS) {
                            if (y == Y0 || pick(ch, 0, 100) >= 15) c.data.setBlock(x, y, z, Material.BARREL);
                        } else {
                            BlockData rack = facing(Material.BLAST_FURNACE, aisle);
                            if (rack instanceof Lightable l) l.setLit(pick(ch, y - Y0, 100) < 15);
                            c.data.setBlock(x, y, z, rack);
                        }
                    }
                }
            }
            case BREAK -> {
                if (u == 1 && v >= 2 && v <= 5) {
                    c.data.setBlock(x, Y0, z, slab(Material.SMOOTH_STONE_SLAB, Slab.Type.TOP));
                } else if (u == 1 && v == 6) {
                    c.data.setBlock(x, Y0, z, Material.IRON_BLOCK);       // the fridge
                    c.data.setBlock(x, Y0 + 1, z, Material.IRON_BLOCK);
                } else if (u == 4 && v == 4) {
                    c.data.setBlock(x, Y0, z, Material.OAK_FENCE);
                    c.data.setBlock(x, Y0 + 1, z, Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
                } else if (Math.abs(u - 4) + Math.abs(v - 4) == 1 && pick(ch, 0, 100) < 60) {
                    c.data.setBlock(x, Y0, z, stairs(Material.SPRUCE_STAIRS,
                            turn(u < 4 ? BlockFace.WEST : u > 4 ? BlockFace.EAST : v < 4 ? BlockFace.NORTH : BlockFace.SOUTH, rot),
                            Stairs.Half.BOTTOM));
                } else if (v == 1 && (u == 4 || u == 5)) {
                    c.data.setBlock(x, Y0 + 1, z, Material.BLACK_CONCRETE);   // the television
                }
            }
            case TOILETS -> {
                if (v == 1 && (u == 2 || u == 4 || u == 6)) {
                    c.data.setBlock(x, Y0, z, Material.CAULDRON);
                } else if (v <= 2 && (u == 3 || u == 5)) {
                    c.data.setBlock(x, Y0, z, Material.SMOOTH_QUARTZ);   // stall partitions
                    c.data.setBlock(x, Y0 + 1, z, Material.SMOOTH_QUARTZ);
                } else if (v == 7 && (u == 2 || u == 4 || u == 6)) {
                    c.data.setBlock(x, Y0, z, slab(Material.POLISHED_DIORITE_SLAB, Slab.Type.TOP));   // sinks
                }
            }
            case EMPTY -> {
                if (u == 4 && v == 4 && pick(rh, 56, 100) < 40) {
                    c.data.setBlock(x, Y0, z, stairs(Material.SPRUCE_STAIRS, CW[pick(rh, 60, 4)], Stairs.Half.BOTTOM));
                }
            }
        }
    }

    /** A filing cabinet: two barrels, the top one now and then gone. */
    private static void filing(Level0Chunk c, int x, int z, long ch) {
        c.data.setBlock(x, Y0, z, Material.BARREL);
        if (pick(ch, 24, 100) >= 20) c.data.setBlock(x, Y0 + 1, z, Material.BARREL);
    }

    // ---------------------------------------------------------------
    //  Control room: control.shp's core, flattened into the lobby's one
    //  storey, set in the office grid with offices all round
    // ---------------------------------------------------------------

    /**
     * The room spans 3-4 office-grid squares each way (its walls on grid
     * lines, so the offices round it open into it through their own
     * doorways), its front wall shut. Inside, (u, v): u across, v back from
     * the screen wall, which faces one of four ways.
     */
    private static void control(Level0Chunk c, int x, int z, int lx, int lz, int zx0, int zz0, long zh) {
        int na = 3 + pick(zh, 4, 2), nb = 3 + pick(zh, 6, 2);
        int a0 = 8 * pick(zh, 8, 6 - na), b0 = 8 * pick(zh, 12, 6 - nb);
        int a1 = a0 + 8 * na, b1 = b0 + 8 * nb;
        if (lx < a0 || lx > a1 || lz < b0 || lz > b1) {
            office(c, x, z, lx, lz, zx0, zz0);
            return;
        }
        int front = pick(zh, 16, 4);
        int u, v, w, d;
        BlockFace back;
        switch (front) {
            case 0 -> { u = lx - a0; v = lz - b0; w = a1 - a0; d = b1 - b0; back = BlockFace.SOUTH; }
            case 1 -> { u = a1 - lx; v = b1 - lz; w = a1 - a0; d = b1 - b0; back = BlockFace.NORTH; }
            case 2 -> { u = b1 - lz; v = lx - a0; w = b1 - b0; d = a1 - a0; back = BlockFace.EAST; }
            default -> { u = lz - b0; v = a1 - lx; w = b1 - b0; d = a1 - a0; back = BlockFace.WEST; }
        }
        if (v == 0) {
            wall(c, x, z, ROOM_TOP - Y0);   // the screen wall: no doorways
            return;
        }
        if (u == 0 || u == w || v == d) {
            office(c, x, z, lx, lz, zx0, zz0);
            return;
        }
        // Half of them sit dark but for their screens.
        if (pick(zh, 20, 100) < 50) c.lightY[x][z] = Level0Chunk.NO_LIGHT;
        c.noCarpet[x][z] = true;
        long ch = hash(c.seed, zx0 + lx, zz0 + lz, 0xC047201L);

        // The screens along the front, floor to ceiling, in 2x2 panels; most are off.
        if (v == 1) {
            if (u < 2 || u > w - 2) return;
            for (int y = Y0; y < ROOM_TOP; y++) {
                long ph = hash(zh, u / 2, (y - Y0) / 2, 0x5C2EE7L);
                int p = pick(ph, 0, 100);
                c.data.setBlock(x, y, z, p < 4 ? Material.SEA_LANTERN : p < 20 ? Material.LIGHT_BLUE_CONCRETE
                        : p < 23 ? Material.LIME_CONCRETE : Material.BLACK_CONCRETE);
            }
            return;
        }
        // Three clear, then rows: consoles, seats, a walkway; 3-wide aisles each side
        // (and down the middle of a wide room). The back row is the flight director's.
        if (v < 5 || v > d - 3) {
            if ((u == 1 || u == w - 1) && v == d - 1) filing(c, x, z, ch);
            return;
        }
        int row = (v - 5) / 3, m = (v - 5) % 3, rows = (d - 9) / 3 + 1;
        if (m == 2 || row >= rows || u < 4 || u > w - 4 || w >= 32 && Math.abs(u - w / 2) <= 1) return;
        if (row == rows - 1 && rows >= 3) {
            int mid = w / 2;
            if (m == 0) {
                if (u == mid - 2) c.data.setBlock(x, Y0, z, facing(Material.OBSERVER, back));
                else if (Math.abs(u - mid) <= 1) c.data.setBlock(x, Y0, z, slab(Material.DARK_OAK_SLAB, Slab.Type.TOP));
                else if (u == mid + 2) c.data.setBlock(x, Y0, z, facing(Material.LECTERN, back));
            } else if (u == mid) {
                c.data.setBlock(x, Y0, z, stairs(Material.SPRUCE_STAIRS, back, Stairs.Half.BOTTOM));
            }
            return;
        }
        if (m == 0) {
            c.data.setBlock(x, Y0, z, pick(ch, 0, 100) < 25
                    ? slab(Material.SMOOTH_STONE_SLAB, Slab.Type.TOP)
                    : facing(Material.OBSERVER, back));
        } else if (pick(ch, 8, 100) < 85) {
            c.data.setBlock(x, Y0, z, stairs(Material.SPRUCE_STAIRS,
                    pick(ch, 16, 100) < 10 ? CW[pick(ch, 24, 4)] : back, Stairs.Half.BOTTOM));
        }
    }

    private static BlockFace turn(BlockFace f, int rot) {
        for (int i = 0; i < 4; i++) if (CW[i] == f) return CW[(i + rot) % 4];
        return f;
    }

    private static BlockData facing(Material m, BlockFace f) {
        BlockData data = m.createBlockData();
        if (data instanceof Directional dir) dir.setFacing(f);
        return data;
    }

    private static Door door(BlockFace facing, Bisected.Half half) {
        Door door = (Door) Material.OAK_DOOR.createBlockData();
        door.setFacing(facing);
        door.setHalf(half);
        return door;
    }

    // ---------------------------------------------------------------
    //  Corridors: long parallel 5-wide halls with occasional crossings
    // ---------------------------------------------------------------

    private static void corridor(Level0Chunk c, int x, int z, int a, int b, int zx0, int zz0) {
        if (b % 6 != 0) return;
        int seg = a / 12;
        long sh = hash(c.seed, zx0 + seg * 12, zz0 + b, 0xC0441D0L);
        boolean crossing = pick(sh, 0, 100) < 55 && (a % 12 == 5 || a % 12 == 6);
        if (!crossing) wall(c, x, z, ROOM_TOP - Y0);
    }
}
