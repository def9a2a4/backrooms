package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
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
 * generator itself); the rest are office spaces gone wrong.
 */
final class Level0Zones {

    static final int ZONE = 48;
    private static final double SPAWN_CLEARANCE = 200.0;

    private static final int Y0 = Level0ChunkGenerator.AIR_MIN_Y;   // floor-standing y
    private static final int ROOM_TOP = Level0ChunkGenerator.CEILING_MIN_Y;

    enum Zone { LOBBY, CUBICLES, CAFETERIA, OFFICES, CORRIDORS }

    private Level0Zones() {}

    static Zone zoneAt(long seed, int zoneX, int zoneZ) {
        double nx = Math.max(zoneX * ZONE, Math.min(0, zoneX * ZONE + ZONE - 1));
        double nz = Math.max(zoneZ * ZONE, Math.min(0, zoneZ * ZONE + ZONE - 1));
        if (nx * nx + nz * nz < SPAWN_CLEARANCE * SPAWN_CLEARANCE) return Zone.LOBBY;

        int roll = pick(hash(seed, zoneX, zoneZ, 0x2011EL), 0, 100);
        if (roll < 73) return Zone.LOBBY;
        if (roll < 85) return Zone.CUBICLES;
        if (roll < 93) return Zone.OFFICES;
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
                    case OFFICES -> office(c, x, z, lx, lz, zx0, zz0);
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
    //  Offices: an 8-block grid of rooms with doorways (and the odd door)
    // ---------------------------------------------------------------

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
            long sh = hash(c.seed, zx0 + segA * 8, zz0 + segB * 8, wallX ? 0x0FF1CEL : 0x0FF1CFL);
            int doorAt = 2 + pick(sh, 8, 4);
            boolean open = pick(sh, 0, 100) < 85;
            wall(c, x, z, ROOM_TOP - Y0);
            if (open && (offset == doorAt || offset == doorAt + 1)) {
                c.data.setBlock(x, Y0, z, Material.AIR);
                c.data.setBlock(x, Y0 + 1, z, Material.AIR);
                if (offset == doorAt && pick(sh, 16, 100) < 30) {
                    BlockFace facing = wallX ? BlockFace.EAST : BlockFace.SOUTH;
                    c.data.setBlock(x, Y0, z, door(facing, Bisected.Half.BOTTOM));
                    c.data.setBlock(x, Y0 + 1, z, door(facing, Bisected.Half.TOP));
                }
            }
            return;
        }

        int ox = lx % 8, oz = lz % 8;   // 1-7 inside the room
        long rh = hash(c.seed, zx0 + (lx / 8) * 8, zz0 + (lz / 8) * 8, 0x0FF1D0L);
        int kind = pick(rh, 0, 100);
        if (kind < 35) {
            // Conference table with chairs down both long sides.
            if (ox >= 3 && ox <= 5 && (oz == 3 || oz == 4)) {
                c.data.setBlock(x, Y0, z, slab(Material.DARK_OAK_SLAB, Slab.Type.TOP));
            } else if (ox >= 3 && ox <= 5 && (oz == 2 || oz == 5) && pick(rh, 8 + ox * 2 + oz, 100) < 80) {
                c.data.setBlock(x, Y0, z, stairs(Material.SPRUCE_STAIRS,
                        oz == 2 ? BlockFace.NORTH : BlockFace.SOUTH, Stairs.Half.BOTTOM));
            }
        } else if (kind < 65) {
            // A desk against the north wall.
            if (oz == 1 && ox >= 2 && ox <= 4) {
                c.data.setBlock(x, Y0, z, slab(Material.OAK_SLAB, Slab.Type.TOP));
            } else if (oz == 1 && ox == 5 && pick(rh, 8, 100) < 30) {
                c.data.setBlock(x, Y0, z, Material.BOOKSHELF);
            } else if (oz == 2 && ox == 3) {
                c.data.setBlock(x, Y0, z, stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH, Stairs.Half.BOTTOM));
            }
        }
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
