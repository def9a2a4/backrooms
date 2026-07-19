package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.Snowable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Level 101 — "Alpha"
 * An endless, dead-flat grass plain under a white sky, drowned in white fog,
 * lit like a day that is almost over. Bare grass — no flowers, no tall grass.
 * Rare oak trees. Rarer still: lonely structures, most of them ruined,
 * abandoned mid-build, or simply wrong. Below the surface, giant empty caves
 * that never reach the light. Occasionally, a clean 1x1 hole goes all the way
 * down to the void. The old world, too perfect and too empty.
 *
 * Only alpha-era blocks (one deliberate exception: the cottage bed, which is
 * the level's exit mechanic). Structures are single-chunk and never generate
 * within 200 blocks of spawn; with the fog wall at ~64 blocks the chunk grid
 * is never visible. Blocks placed via ChunkData get no neighbor updates, so
 * fences, doors, beds, rails, stairs, snow layers etc. are placed with
 * explicit BlockData (faces/facing/part/shape set by hand).
 */
public class Level101ChunkGenerator extends BackroomsChunkGenerator {

    private static final int MAX_Y = 128;
    private static final int GROUND_Y = 64;

    // Cave carving band: leaves >= 7 blocks of stone plus the dirt cap intact,
    // so caverns never open to the surface on their own.
    private static final int CAVE_MIN_Y = 6;
    private static final int CAVE_MAX_Y = 52;

    // No structures near spawn — the plain must feel empty until you commit
    // to walking into the fog.
    private static final int STRUCTURE_SPAWN_EXCLUSION = 200;
    private static final int PIT_SPAWN_EXCLUSION = 100;

    private static final BlockFace[] CARDINALS = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST
    };
    private static final BlockFace[] SIGN_ROTATIONS = {
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST,
            BlockFace.EAST_NORTH_EAST, BlockFace.EAST, BlockFace.EAST_SOUTH_EAST,
            BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST, BlockFace.SOUTH,
            BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST,
            BlockFace.NORTH_NORTH_WEST
    };

    @FunctionalInterface
    private interface Placer {
        void place(ChunkData chunkData, Random rng);
    }

    private record Template(int weight, Placer placer) {}

    private final List<Template> templates = new ArrayList<>();
    private final int totalStructureWeight;

    // Tunables (generator_config)
    private int treeChunkChance = 8;         // percent of chunks with 1-2 trees
    private int structureChunkChance = 2;    // percent of chunks with a structure
    private double voidPitChunkChance = 0.15; // percent of chunks with a 1x1 pit to the void

    public Level101ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);

        templates.add(new Template(8, this::placeCottage));
        templates.add(new Template(7, this::placeDirtHut));
        templates.add(new Template(6, this::placeWell));
        templates.add(new Template(6, this::placeFencePen));
        templates.add(new Template(12, this::placeRuinedCottage));
        templates.add(new Template(12, this::placeStoneRuin));
        templates.add(new Template(9, this::placeHouseFrame));
        templates.add(new Template(8, this::placeCollapsedTower));
        templates.add(new Template(6, this::placeGraveMound));
        templates.add(new Template(6, this::placeWheatPlot));
        templates.add(new Template(5, this::placeLoneDoorframe));
        templates.add(new Template(7, this::placePillarCluster));
        templates.add(new Template(7, this::placeMineshaftEntrance));
        templates.add(new Template(6, this::placeExcavationPit));
        templates.add(new Template(4, this::placeBrokenPortalFrame));
        templates.add(new Template(2, this::placeMonolith));
        templates.add(new Template(3, this::placeGlassBox));
        templates.add(new Template(5, this::placeGraveyard));
        templates.add(new Template(5, this::placeChapelRuin));
        templates.add(new Template(5, this::placeWinterScar));
        templates.add(new Template(5, this::placePumpkinPatch));
        templates.add(new Template(5, this::placeLavaPool));
        templates.add(new Template(4, this::placeRailsToNowhere));
        templates.add(new Template(4, this::placeSignpostCluster));
        templates.add(new Template(1, this::placeSponge));
        templates.add(new Template(5, this::placeDungeonBreach));
        templates.add(new Template(4, this::placeSandAnomaly));
        templates.add(new Template(8, this::placeTwoStoryHouse));
        templates.add(new Template(7, this::placeStoneCottage));
        templates.add(new Template(6, this::placeFarmhouse));
        templates.add(new Template(6, this::placeBarn));
        templates.add(new Template(5, this::placeAFrameCabin));
        templates.add(new Template(5, this::placeWatchhouse));

        int sum = 0;
        for (Template t : templates) sum += t.weight();
        totalStructureWeight = sum;
    }

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config == null) return;
        treeChunkChance = config.getInt("tree_chunk_chance", treeChunkChance);
        structureChunkChance = config.getInt("structure_chunk_chance", structureChunkChance);
        voidPitChunkChance = config.getDouble("void_pit_chunk_chance", voidPitChunkChance);
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        long centerX = chunkX * 16L + 8, centerZ = chunkZ * 16L + 8;
        long distSq = centerX * centerX + centerZ * centerZ;

        boolean hasStructure = structureChunkChance > 0
                && distSq >= (long) STRUCTURE_SPAWN_EXCLUSION * STRUCTURE_SPAWN_EXCLUSION
                && Math.floorMod(blockHash(chunkX, chunkZ, seed, 0xA1FA), 100) < structureChunkChance;

        // A pit chunk gets one 1x1 hole from the grass straight down to the void.
        long pitHash = blockHash(chunkX, chunkZ, seed, 0x9117);
        boolean hasPit = !hasStructure && voidPitChunkChance > 0
                && distSq >= (long) PIT_SPAWN_EXCLUSION * PIT_SPAWN_EXCLUSION
                && Math.floorMod(pitHash, 10000) < (int) Math.round(voidPitChunkChance * 100);
        int pitX = (int) ((pitHash >>> 8) & 15);
        int pitZ = (int) ((pitHash >>> 12) & 15);

        // --- Pass 1: flat terrain columns with giant caves carved out ---
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (hasPit && x == pitX && z == pitZ) {
                    continue; // the pit column: nothing at all, bedrock included
                }
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                chunkData.setBlock(x, 0, z, Material.BEDROCK);
                for (int y = 1; y <= 3; y++) {
                    chunkData.setBlock(x, y, z,
                            chunkRng.nextInt(4) < (4 - y) ? Material.BEDROCK : Material.STONE);
                }
                for (int y = 4; y < 60; y++) {
                    if (y >= CAVE_MIN_Y && y <= CAVE_MAX_Y && isCave(worldX, y, worldZ, seed)) {
                        continue; // leave air
                    }
                    chunkData.setBlock(x, y, z, Material.STONE);
                }
                for (int y = 60; y < GROUND_Y; y++) {
                    chunkData.setBlock(x, y, z, Material.DIRT);
                }
                chunkData.setBlock(x, GROUND_Y, z, Material.GRASS_BLOCK);
            }
        }

        // --- Pass 2: at most one structure per eligible chunk ---
        if (hasStructure) {
            int roll = chunkRng.nextInt(totalStructureWeight);
            int cumulative = 0;
            for (Template t : templates) {
                cumulative += t.weight();
                if (roll < cumulative) {
                    t.placer().place(chunkData, chunkRng);
                    break;
                }
            }
        }

        // --- Pass 3: rare trees, only in structure-free chunks ---
        if (!hasStructure && chunkRng.nextDouble() < treeChunkChance / 100.0) {
            int count = 1 + chunkRng.nextInt(2);
            for (int i = 0; i < count; i++) {
                int tx = chunkRng.nextInt(12) + 2;
                int tz = chunkRng.nextInt(12) + 2;
                if (chunkData.getType(tx, GROUND_Y, tz) == Material.GRASS_BLOCK) {
                    placeOakTree(chunkData, chunkRng, tx, GROUND_Y + 1, tz);
                }
            }
        }
    }

    // ---------------------------------------------------------------
    //  Caves — huge low-frequency caverns, sealed off from the surface
    // ---------------------------------------------------------------

    private static boolean isCave(int worldX, int y, int worldZ, long seed) {
        double n1 = SimplexNoise.noise3(seed + 40, worldX / 90.0, y / 55.0, worldZ / 90.0);
        double n2 = SimplexNoise.noise3(seed + 41, worldX / 35.0, y / 28.0, worldZ / 35.0);
        double density = n1 + 0.35 * n2;

        double threshold = 0.30;
        // Taper toward the surface cap and the floor so caverns stay enclosed
        if (y > 44) threshold += (y - 44) / 8.0 * 0.6;
        if (y < 12) threshold += (12 - y) / 6.0 * 0.4;

        return density > threshold;
    }

    // ---------------------------------------------------------------
    //  Trees (Level 4's alpha oak)
    // ---------------------------------------------------------------

    private void placeOakTree(ChunkData chunkData, Random rng, int x, int y, int z) {
        int trunkHeight = 4 + rng.nextInt(3); // 4-6
        int topLog = y + trunkHeight - 1;

        for (int dy = 0; dy < trunkHeight; dy++) {
            if (y + dy < MAX_Y) {
                chunkData.setBlock(x, y + dy, z, Material.OAK_LOG);
            }
        }

        // 2 wide leaf layers (5x5, corners 50% chance)
        for (int layer = 0; layer < 2; layer++) {
            int ly = topLog - 3 + layer;
            if (ly < y || ly >= MAX_Y) continue;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && rng.nextBoolean()) continue;
                    int lx = x + dx, lz = z + dz;
                    if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16
                            && chunkData.getType(lx, ly, lz) == Material.AIR) {
                        chunkData.setBlock(lx, ly, lz, Material.OAK_LEAVES);
                    }
                }
            }
        }

        // 2 narrow leaf layers (3x3, corners 50% chance)
        for (int layer = 0; layer < 2; layer++) {
            int ly = topLog - 1 + layer;
            if (ly < y || ly >= MAX_Y) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && layer == 0) continue;
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1 && rng.nextBoolean()) continue;
                    int lx = x + dx, lz = z + dz;
                    if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                        chunkData.setBlock(lx, ly, lz, Material.OAK_LEAVES);
                    }
                }
            }
        }

        if (topLog < MAX_Y) {
            chunkData.setBlock(x, topLog, z, Material.OAK_LEAVES);
        }
    }

    // ---------------------------------------------------------------
    //  Shared structure helpers
    // ---------------------------------------------------------------

    /** Origin so a {@code width}-wide footprint stays inside the chunk with margin. */
    private static int jitterOrigin(Random rng, int width) {
        return 2 + rng.nextInt(14 - width);
    }

    /** Cobblestone, sometimes mossy — nothing here has been maintained. */
    private static Material weatheredCobble(Random rng) {
        return rng.nextInt(100) < 30 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE;
    }

    /** Irregular blob mask: cell included when dist-from-center + jitter < radius. */
    private static boolean[][] blobMask(Random rng, int size, double radius) {
        boolean[][] mask = new boolean[size][size];
        double c = (size - 1) / 2.0;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                double dist = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
                mask[x][z] = dist + rng.nextDouble() * 1.5 < radius;
            }
        }
        return mask;
    }

    private static boolean onPerimeter(int x, int z, int ox, int oz, int s) {
        if (x < ox || x >= ox + s || z < oz || z >= oz + s) return false;
        return x == ox || x == ox + s - 1 || z == oz || z == oz + s - 1;
    }

    /** Facing from (x,z) toward the chunk center, by dominant axis. */
    private static BlockFace facingChunkCenter(int x, int z) {
        int dx = 8 - x, dz = 8 - z;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private static long blockHash(int worldX, int worldZ, long seed, int salt) {
        long h = seed;
        h ^= (long) worldX * 0x517cc1b727220a95L;
        h ^= (long) worldZ * 0x6c62272e07bb0142L;
        h += salt * 0x6364136223846793L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    // ---------------------------------------------------------------
    //  Structures — all single-chunk, base y = GROUND_Y + 1
    // ---------------------------------------------------------------

    /**
     * 7x6 oak-plank cottage: log corners, plank walls, flat roof, glass
     * window in each non-door wall, oak door, wall torch, crafting table,
     * sometimes a furnace or bookshelf, and a red bed (the exit) against
     * the wall opposite the door.
     */
    private void placeCottage(ChunkData chunkData, Random rng) {
        final int w = 7, d = 6;
        int ox = jitterOrigin(rng, w);
        int oz = jitterOrigin(rng, d);
        int base = GROUND_Y + 1;
        int wallTop = base + 3;   // walls y65-68
        int roofY = wallTop + 1;  // y69

        for (int x = ox; x < ox + w; x++) {
            for (int z = oz; z < oz + d; z++) {
                chunkData.setBlock(x, GROUND_Y, z, Material.OAK_PLANKS);
            }
        }
        for (int x = ox; x < ox + w; x++) {
            for (int z = oz; z < oz + d; z++) {
                boolean perimeter = x == ox || x == ox + w - 1 || z == oz || z == oz + d - 1;
                boolean corner = (x == ox || x == ox + w - 1) && (z == oz || z == oz + d - 1);
                for (int y = base; y <= wallTop; y++) {
                    if (corner) {
                        chunkData.setBlock(x, y, z, Material.OAK_LOG);
                    } else if (perimeter) {
                        chunkData.setBlock(x, y, z, Material.OAK_PLANKS);
                    } else {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                }
                chunkData.setBlock(x, roofY, z, Material.OAK_PLANKS);
            }
        }

        // Door wall: 0=N(-z) 1=S(+z) 2=W(-x) 3=E(+x); windows on the other three
        int doorSide = rng.nextInt(4);
        int cx = ox + w / 2, cz = oz + d / 2;
        int[][] wallCenters = {
                { cx, oz, 0, -1 },          // north wall center, outward -z
                { cx, oz + d - 1, 0, 1 },   // south
                { ox, cz, -1, 0 },          // west
                { ox + w - 1, cz, 1, 0 },   // east
        };
        BlockFace[] inwardFaces = { BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST };

        for (int side = 0; side < 4; side++) {
            int wx = wallCenters[side][0], wz = wallCenters[side][1];
            if (side == doorSide) {
                Door bottom = (Door) Material.OAK_DOOR.createBlockData();
                bottom.setFacing(inwardFaces[side]);
                bottom.setHalf(Bisected.Half.BOTTOM);
                Door top = (Door) Material.OAK_DOOR.createBlockData();
                top.setFacing(inwardFaces[side]);
                top.setHalf(Bisected.Half.TOP);
                chunkData.setBlock(wx, base, wz, bottom);
                chunkData.setBlock(wx, base + 1, wz, top);
            } else {
                chunkData.setBlock(wx, base + 1, wz, Material.GLASS);
            }
        }

        // Wall torch inside, beside the door (attached to the door wall)
        int inX = wallCenters[doorSide][2] == 0 ? 1 : 0;
        int inZ = inX == 1 ? 0 : 1;
        int tx = wallCenters[doorSide][0] + inX - wallCenters[doorSide][2];
        int tz = wallCenters[doorSide][1] + inZ - wallCenters[doorSide][3];
        Directional torch = (Directional) Material.WALL_TORCH.createBlockData();
        torch.setFacing(inwardFaces[doorSide]);
        chunkData.setBlock(tx, base + 2, tz, torch);

        // Bed against the wall opposite the door.
        // Interior spans [ox+1, ox+w-2] x [oz+1, oz+d-2].
        int backX, backZ, bedDx, bedDz;
        BlockFace bedFacing;
        switch (doorSide) {
            case 0 -> { backX = ox + 1; backZ = oz + d - 2; bedDx = 1; bedDz = 0; bedFacing = BlockFace.EAST; }
            case 1 -> { backX = ox + 1; backZ = oz + 1;     bedDx = 1; bedDz = 0; bedFacing = BlockFace.EAST; }
            case 2 -> { backX = ox + w - 2; backZ = oz + 1; bedDx = 0; bedDz = 1; bedFacing = BlockFace.SOUTH; }
            default -> { backX = ox + 1; backZ = oz + 1;    bedDx = 0; bedDz = 1; bedFacing = BlockFace.SOUTH; }
        }
        Bed foot = (Bed) Material.RED_BED.createBlockData();
        foot.setPart(Bed.Part.FOOT);
        foot.setFacing(bedFacing);
        Bed head = (Bed) Material.RED_BED.createBlockData();
        head.setPart(Bed.Part.HEAD);
        head.setFacing(bedFacing);
        chunkData.setBlock(backX, base, backZ, foot);
        chunkData.setBlock(backX + bedDx, base, backZ + bedDz, head);

        // Crafting table in an interior corner on the door side
        int craftX, craftZ;
        switch (doorSide) {
            case 0 -> { craftX = ox + w - 2; craftZ = oz + 1; }
            case 1 -> { craftX = ox + w - 2; craftZ = oz + d - 2; }
            case 2 -> { craftX = ox + 1; craftZ = oz + d - 2; }
            default -> { craftX = ox + w - 2; craftZ = oz + d - 2; }
        }
        if (chunkData.getType(craftX, base, craftZ) == Material.AIR) {
            chunkData.setBlock(craftX, base, craftZ, Material.CRAFTING_TABLE);
        }

        // 40% a furnace beside the crafting table, facing into the room
        if (rng.nextInt(100) < 40) {
            int fx = craftX + (craftX > cx ? -1 : 1), fz = craftZ;
            if (chunkData.getType(fx, base, fz) == Material.AIR) {
                Directional furnace = (Directional) Material.FURNACE.createBlockData();
                furnace.setFacing(facingChunkCenter(fx, fz));
                chunkData.setBlock(fx, base, fz, furnace);
            }
        }
        // 20% one bookshelf in another interior corner
        if (rng.nextInt(100) < 20) {
            int bx = ox + 1, bz = oz + 1;
            if (bx == backX && bz == backZ) bz = oz + d - 2;
            if (chunkData.getType(bx, base, bz) == Material.AIR) {
                chunkData.setBlock(bx, base, bz, Material.BOOKSHELF);
            }
        }
    }

    /** 5x5 dirt hut: the classic panicked first-night shelter. */
    private void placeDirtHut(ChunkData chunkData, Random rng) {
        final int s = 5;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;
        int wallTop = base + 2;   // walls y65-67
        int roofY = wallTop + 1;  // y68

        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                boolean perimeter = x == ox || x == ox + s - 1 || z == oz || z == oz + s - 1;
                for (int y = base; y <= wallTop; y++) {
                    chunkData.setBlock(x, y, z, perimeter ? Material.DIRT : Material.AIR);
                }
                chunkData.setBlock(x, roofY, z, Material.DIRT);
            }
        }

        // 25% chance of 1-3 holes in the roof
        if (rng.nextInt(100) < 25) {
            int holes = 1 + rng.nextInt(3);
            for (int i = 0; i < holes; i++) {
                chunkData.setBlock(ox + rng.nextInt(s), roofY, oz + rng.nextInt(s), Material.AIR);
            }
        }

        // Open 1x2 doorway centered on a random wall
        int side = rng.nextInt(4);
        int c = ox + s / 2, cMid = oz + s / 2;
        int dx = switch (side) { case 2 -> ox; case 3 -> ox + s - 1; default -> c; };
        int dz = switch (side) { case 0 -> oz; case 1 -> oz + s - 1; default -> cMid; };
        chunkData.setBlock(dx, base, dz, Material.AIR);
        chunkData.setBlock(dx, base + 1, dz, Material.AIR);

        // Floor torch in an interior corner — 15% of huts are pitch black instead
        if (rng.nextInt(100) >= 15) {
            chunkData.setBlock(ox + 1, base, oz + 1, Material.TORCH);
        }
    }

    /** 3x3 cobblestone well; 20% have gone dry. */
    private void placeWell(ChunkData chunkData, Random rng) {
        final int s = 3;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;
        int cx = ox + 1, cz = oz + 1;

        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                if (x == cx && z == cz) continue;
                chunkData.setBlock(x, base, z, weatheredCobble(rng));
            }
        }
        boolean dry = rng.nextInt(100) < 20;
        if (dry) {
            chunkData.setBlock(cx, GROUND_Y - 3, cz, Material.GRAVEL);
            for (int y = GROUND_Y - 2; y <= GROUND_Y; y++) {
                chunkData.setBlock(cx, y, cz, Material.AIR);
            }
        } else {
            chunkData.setBlock(cx, GROUND_Y - 3, cz, Material.COBBLESTONE);
            for (int y = GROUND_Y - 2; y <= GROUND_Y; y++) {
                chunkData.setBlock(cx, y, cz, Material.WATER);
            }
        }
        for (int y = base + 1; y <= base + 2; y++) {
            chunkData.setBlock(ox, y, oz, Material.OAK_FENCE);
            chunkData.setBlock(ox + 2, y, oz + 2, Material.OAK_FENCE);
        }
        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                chunkData.setBlock(x, base + 3, z, Material.SMOOTH_STONE_SLAB);
            }
        }
    }

    /** 7x7 oak fence pen with a gap where the gate would be; whatever it held is gone. */
    private void placeFencePen(ChunkData chunkData, Random rng) {
        final int s = 7;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;

        // Gap position (no fence gates in alpha — the pen just stands open)
        int gapSide = rng.nextInt(4);
        int gx = switch (gapSide) { case 2 -> ox; case 3 -> ox + s - 1; default -> ox + s / 2; };
        int gz = switch (gapSide) { case 0 -> oz; case 1 -> oz + s - 1; default -> oz + s / 2; };

        // A couple more posts trampled down, 25% of the time
        boolean[][] missing = new boolean[16][16];
        missing[gx][gz] = true;
        if (rng.nextInt(100) < 25) {
            int extra = 1 + rng.nextInt(2);
            for (int i = 0; i < extra; i++) {
                int mx = switch (rng.nextInt(4)) { case 2 -> ox; case 3 -> ox + s - 1; default -> ox + rng.nextInt(s); };
                int mz = (mx == ox || mx == ox + s - 1) ? oz + rng.nextInt(s)
                        : (rng.nextBoolean() ? oz : oz + s - 1);
                missing[mx][mz] = true;
            }
        }

        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                if (!onPerimeter(x, z, ox, oz, s) || missing[x][z]) continue;
                // Explicit connections: ChunkData placement never triggers neighbor
                // updates; don't point arms at missing posts.
                Fence fence = (Fence) Material.OAK_FENCE.createBlockData();
                if (onPerimeter(x - 1, z, ox, oz, s) && !missing[x - 1][z]) fence.setFace(BlockFace.WEST, true);
                if (onPerimeter(x + 1, z, ox, oz, s) && !missing[x + 1][z]) fence.setFace(BlockFace.EAST, true);
                if (onPerimeter(x, z - 1, ox, oz, s) && !missing[x][z - 1]) fence.setFace(BlockFace.NORTH, true);
                if (onPerimeter(x, z + 1, ox, oz, s) && !missing[x][z + 1]) fence.setFace(BlockFace.SOUTH, true);
                chunkData.setBlock(x, base, z, fence);
            }
        }

        // Torch on a corner post, half the time
        if (rng.nextBoolean() && !missing[ox][oz]) {
            chunkData.setBlock(ox, base + 1, oz, Material.TORCH);
        }
    }

    /** 7x6 cottage that lost its roof and most of its walls a long time ago. */
    private void placeRuinedCottage(ChunkData chunkData, Random rng) {
        final int w = 7, d = 6;
        int ox = jitterOrigin(rng, w);
        int oz = jitterOrigin(rng, d);
        int base = GROUND_Y + 1;

        for (int x = ox; x < ox + w; x++) {
            for (int z = oz; z < oz + d; z++) {
                chunkData.setBlock(x, GROUND_Y, z,
                        rng.nextInt(100) < 30 ? Material.DIRT : Material.OAK_PLANKS);

                boolean perimeter = x == ox || x == ox + w - 1 || z == oz || z == oz + d - 1;
                boolean corner = (x == ox || x == ox + w - 1) && (z == oz || z == oz + d - 1);
                if (corner) {
                    int h = rng.nextInt(5);
                    for (int y = base; y < base + h; y++) {
                        chunkData.setBlock(x, y, z, Material.OAK_LOG);
                    }
                } else if (perimeter) {
                    if (rng.nextInt(100) < 55) {
                        chunkData.setBlock(x, base, z, Material.OAK_PLANKS);
                        if (rng.nextInt(100) < 25) {
                            chunkData.setBlock(x, base + 1, z, Material.OAK_PLANKS);
                        }
                    }
                }
            }
        }

        // Sometimes something is left standing: a crafting table, or a bookshelf
        int relic = rng.nextInt(100);
        if (relic < 35) {
            chunkData.setBlock(ox + 1 + rng.nextInt(w - 2), base, oz + 1 + rng.nextInt(d - 2),
                    Material.CRAFTING_TABLE);
        } else if (relic < 50) {
            chunkData.setBlock(ox + 1 + rng.nextInt(w - 2), base, oz + 1 + rng.nextInt(d - 2),
                    Material.BOOKSHELF);
        }
    }

    /** 6x5 crumbled cobblestone foundation, walls worn down to a course or two. */
    private void placeStoneRuin(ChunkData chunkData, Random rng) {
        final int w = 6, d = 5;
        int ox = jitterOrigin(rng, w);
        int oz = jitterOrigin(rng, d);
        int base = GROUND_Y + 1;
        boolean furnacePlaced = rng.nextInt(100) >= 20; // 20% get one embedded furnace

        for (int x = ox; x < ox + w; x++) {
            for (int z = oz; z < oz + d; z++) {
                boolean perimeter = x == ox || x == ox + w - 1 || z == oz || z == oz + d - 1;
                if (perimeter) {
                    if (rng.nextInt(100) < 25) continue; // gap
                    if (!furnacePlaced && rng.nextInt(100) < 15) {
                        Directional furnace = (Directional) Material.FURNACE.createBlockData();
                        BlockFace inward = facingChunkCenter(x, z);
                        furnace.setFacing(inward.getOppositeFace());
                        chunkData.setBlock(x, base, z, furnace);
                        furnacePlaced = true;
                        continue;
                    }
                    int h = 1 + (rng.nextInt(100) < 35 ? 1 : 0);
                    for (int y = base; y < base + h; y++) {
                        chunkData.setBlock(x, y, z, weatheredCobble(rng));
                    }
                } else if (rng.nextInt(100) < 12) {
                    chunkData.setBlock(x, base, z, weatheredCobble(rng));
                }
            }
        }
    }

    /** 7x6 house someone stopped building: floor, corner posts, one started wall. */
    private void placeHouseFrame(ChunkData chunkData, Random rng) {
        final int w = 7, d = 6;
        int ox = jitterOrigin(rng, w);
        int oz = jitterOrigin(rng, d);
        int base = GROUND_Y + 1;

        for (int x = ox; x < ox + w; x++) {
            for (int z = oz; z < oz + d; z++) {
                chunkData.setBlock(x, GROUND_Y, z, Material.OAK_PLANKS);
            }
        }
        for (int y = base; y <= base + 3; y++) {
            chunkData.setBlock(ox, y, oz, Material.OAK_LOG);
            chunkData.setBlock(ox + w - 1, y, oz, Material.OAK_LOG);
            chunkData.setBlock(ox, y, oz + d - 1, Material.OAK_LOG);
            chunkData.setBlock(ox + w - 1, y, oz + d - 1, Material.OAK_LOG);
        }
        int done = 1 + rng.nextInt(w - 3);
        for (int x = ox + 1; x <= ox + done; x++) {
            chunkData.setBlock(x, base, oz, Material.OAK_PLANKS);
        }
        // Leftover materials, exactly where they were put down
        chunkData.setBlock(ox + w - 2, base, oz + d - 2, Material.CRAFTING_TABLE);
        chunkData.setBlock(ox + 1, base, oz + d - 2, Material.OAK_PLANKS);
        if (rng.nextBoolean()) {
            chunkData.setBlock(ox + 1, base + 1, oz + d - 2, Material.OAK_PLANKS);
        }
        // 30% an empty chest beside the crafting table — supplies never used
        if (rng.nextInt(100) < 30) {
            int chestX = ox + w - 3, chestZ = oz + d - 2;
            if (chunkData.getType(chestX, base, chestZ) == Material.AIR) {
                org.bukkit.block.data.type.Chest chest =
                        (org.bukkit.block.data.type.Chest) Material.CHEST.createBlockData();
                chest.setFacing(BlockFace.NORTH);
                chunkData.setBlock(chestX, base, chestZ, chest);
            }
        }
    }

    /** 3x3 cobblestone watchtower, half fallen — every column a different height. */
    private void placeCollapsedTower(ChunkData chunkData, Random rng) {
        final int s = 3;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;
        int maxH = 4 + rng.nextInt(3);

        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                boolean center = x == ox + 1 && z == oz + 1;
                int h = center ? 0 : 1 + rng.nextInt(maxH);
                for (int y = base; y < base + h; y++) {
                    chunkData.setBlock(x, y, z, weatheredCobble(rng));
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            int rx = ox - 1 + rng.nextInt(s + 2);
            int rz = oz - 1 + rng.nextInt(s + 2);
            if (rx < 0 || rx > 15 || rz < 0 || rz > 15) continue;
            if (chunkData.getType(rx, base, rz) == Material.AIR) {
                chunkData.setBlock(rx, base, rz, weatheredCobble(rng));
            }
        }
    }

    /** A low dirt mound with a marker. Nobody left a name. */
    private void placeGraveMound(ChunkData chunkData, Random rng) {
        int ox = jitterOrigin(rng, 4);
        int oz = jitterOrigin(rng, 3);
        int base = GROUND_Y + 1;

        for (int x = ox + 1; x <= ox + 2; x++) {
            for (int z = oz; z <= oz + 2; z++) {
                chunkData.setBlock(x, base, z, Material.DIRT);
            }
        }
        chunkData.setBlock(ox + 1, base + 1, oz + 1, Material.DIRT);
        chunkData.setBlock(ox + 2, base + 1, oz + 1, Material.DIRT);
        placeHeadstone(chunkData, rng, ox, oz + 1, BlockFace.WEST);
    }

    /** Cobble marker, or 25% a blank oak sign facing away from the mound. */
    private void placeHeadstone(ChunkData chunkData, Random rng, int x, int z, BlockFace awayFace) {
        int base = GROUND_Y + 1;
        if (rng.nextInt(100) < 25) {
            Rotatable sign = (Rotatable) Material.OAK_SIGN.createBlockData();
            sign.setRotation(awayFace);
            chunkData.setBlock(x, base, z, sign);
        } else {
            chunkData.setBlock(x, base, z, Material.COBBLESTONE);
            chunkData.setBlock(x, base + 1, z, Material.SMOOTH_STONE_SLAB);
        }
    }

    /** 5x5 wheat plot around a water hole — mostly still growing, with no one to harvest it. */
    private void placeWheatPlot(ChunkData chunkData, Random rng) {
        final int s = 5;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int cx = ox + 2, cz = oz + 2;

        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                if (x == cx && z == cz) {
                    chunkData.setBlock(x, GROUND_Y, z, Material.WATER);
                    continue;
                }
                if (rng.nextInt(100) < 10) {
                    // The tending stopped here first
                    chunkData.setBlock(x, GROUND_Y, z, Material.DIRT);
                    continue;
                }
                Farmland farmland = (Farmland) Material.FARMLAND.createBlockData();
                farmland.setMoisture(farmland.getMaximumMoisture());
                chunkData.setBlock(x, GROUND_Y, z, farmland);

                Ageable wheat = (Ageable) Material.WHEAT.createBlockData();
                wheat.setAge(2 + rng.nextInt(wheat.getMaximumAge() - 1));
                chunkData.setBlock(x, GROUND_Y + 1, z, wheat);
            }
        }
    }

    /** A working oak door standing alone in the field, sometimes with a frame. */
    private void placeLoneDoorframe(ChunkData chunkData, Random rng) {
        int cx = jitterOrigin(rng, 3) + 1;
        int cz = jitterOrigin(rng, 3) + 1;
        int base = GROUND_Y + 1;
        BlockFace facing = CARDINALS[rng.nextInt(4)];

        Door bottom = (Door) Material.OAK_DOOR.createBlockData();
        bottom.setFacing(facing);
        bottom.setHalf(Bisected.Half.BOTTOM);
        Door top = (Door) Material.OAK_DOOR.createBlockData();
        top.setFacing(facing);
        top.setHalf(Bisected.Half.TOP);
        chunkData.setBlock(cx, base, cz, bottom);
        chunkData.setBlock(cx, base + 1, cz, top);

        // Half of them get a frame: two log posts and a plank lintel
        if (rng.nextBoolean()) {
            boolean alongX = facing == BlockFace.NORTH || facing == BlockFace.SOUTH;
            int dx = alongX ? 1 : 0, dz = alongX ? 0 : 1;
            for (int y = base; y <= base + 2; y++) {
                chunkData.setBlock(cx - dx, y, cz - dz, Material.OAK_LOG);
                chunkData.setBlock(cx + dx, y, cz + dz, Material.OAK_LOG);
            }
            for (int i = -1; i <= 1; i++) {
                chunkData.setBlock(cx + i * dx, base + 3, cz + i * dz, Material.OAK_PLANKS);
            }
        }
    }

    /** 3-6 dirt panic pillars; the tallest still has its torch. */
    private void placePillarCluster(ChunkData chunkData, Random rng) {
        final int s = 9;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;

        List<int[]> pillars = new ArrayList<>();
        int want = 3 + rng.nextInt(4);
        for (int attempt = 0; attempt < 20 && pillars.size() < want; attempt++) {
            int px = ox + rng.nextInt(s), pz = oz + rng.nextInt(s);
            boolean tooClose = false;
            for (int[] p : pillars) {
                if (Math.abs(p[0] - px) <= 1 && Math.abs(p[1] - pz) <= 1) { tooClose = true; break; }
            }
            if (!tooClose) pillars.add(new int[]{ px, pz, 3 + rng.nextInt(5) });
        }

        int tallest = 0;
        for (int i = 1; i < pillars.size(); i++) {
            if (pillars.get(i)[2] > pillars.get(tallest)[2]) tallest = i;
        }
        for (int i = 0; i < pillars.size(); i++) {
            int[] p = pillars.get(i);
            for (int y = base; y < base + p[2]; y++) {
                chunkData.setBlock(p[0], y, p[1], Material.DIRT);
            }
            if (i == tallest || rng.nextInt(100) < 30) {
                chunkData.setBlock(p[0], base + p[2], p[1], Material.TORCH);
            }
        }
    }

    /** A 1x1 ladder shaft dug down toward the caves, cobble-lined, torch at the collar. */
    private void placeMineshaftEntrance(ChunkData chunkData, Random rng) {
        int ox = jitterOrigin(rng, 3);
        int oz = jitterOrigin(rng, 3);
        int base = GROUND_Y + 1;
        int cx = ox + 1, cz = oz + 1;
        final int shaftBottom = 45;

        // Line the four shaft walls so the shaft stays enclosed even through caves
        for (int y = shaftBottom - 1; y <= GROUND_Y; y++) {
            chunkData.setBlock(cx - 1, y, cz, Material.COBBLESTONE);
            chunkData.setBlock(cx + 1, y, cz, Material.COBBLESTONE);
            chunkData.setBlock(cx, y, cz - 1, Material.COBBLESTONE);
            chunkData.setBlock(cx, y, cz + 1, Material.COBBLESTONE);
        }
        // The shaft itself
        chunkData.setBlock(cx, shaftBottom - 1, cz, Material.COBBLESTONE);
        for (int y = shaftBottom; y <= GROUND_Y; y++) {
            chunkData.setBlock(cx, y, cz, Material.AIR);
        }
        chunkData.setBlock(cx, shaftBottom, cz, Material.WATER); // someone's safety water

        // Ladder on the north wall; 40% of the time it stops short of the bottom
        int ladderBottom = shaftBottom + 1;
        if (rng.nextInt(100) < 40) ladderBottom += 5 + rng.nextInt(4);
        for (int y = ladderBottom; y <= GROUND_Y; y++) {
            org.bukkit.block.data.type.Ladder ladder =
                    (org.bukkit.block.data.type.Ladder) Material.LADDER.createBlockData();
            ladder.setFacing(BlockFace.SOUTH);
            chunkData.setBlock(cx, y, cz, ladder);
        }

        // Weathered collar ring with a gap or two, one torch
        for (int x = ox; x < ox + 3; x++) {
            for (int z = oz; z < oz + 3; z++) {
                if (x == cx && z == cz) continue;
                if (rng.nextInt(100) < 20) continue;
                chunkData.setBlock(x, base, z, weatheredCobble(rng));
            }
        }
        if (chunkData.getType(ox, base, oz) != Material.AIR) {
            chunkData.setBlock(ox, base + 1, oz, Material.TORCH);
        }
    }

    /** 6x6 dig site: nothing was found, the digging just stopped. */
    private void placeExcavationPit(ChunkData chunkData, Random rng) {
        final int s = 6;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int cz = oz + s / 2;

        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                // Ramp carved into the west side at mid-z; floor steps down eastward
                int floor;
                if (z == cz && x <= ox + 2) {
                    floor = GROUND_Y - 1 - (x - ox); // y63, y62, y61
                } else {
                    floor = GROUND_Y - 4; // y60
                }
                for (int y = floor + 1; y <= GROUND_Y; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }
                chunkData.setBlock(x, floor, z,
                        floor == GROUND_Y - 4 && rng.nextInt(100) < 12 ? Material.STONE : Material.DIRT);
            }
        }

        int floorY = GROUND_Y - 3; // first air block above the y60 floor
        if (rng.nextInt(100) < 40) {
            chunkData.setBlock(ox + s - 2, floorY, oz + 1, Material.TORCH);
        }
        if (rng.nextInt(100) < 30) {
            chunkData.setBlock(ox + s - 2, floorY, oz + s - 2, Material.CRAFTING_TABLE);
        }
        if (rng.nextInt(100) < 20) {
            chunkData.setBlock(ox + 2, floorY, oz + s - 2, Material.DIRT);
            chunkData.setBlock(ox + 2, floorY + 1, oz + s - 2, Material.DIRT);
        }
        if (rng.nextInt(100) < 10) {
            chunkData.setBlock(ox + s - 2, floorY, oz + 2, Material.TNT);
            chunkData.setBlock(ox + s - 2, floorY + 1, oz + 2, Material.TNT);
        }
    }

    /** A cold, broken obsidian portal frame. Someone made sure it stayed shut. */
    private void placeBrokenPortalFrame(ChunkData chunkData, Random rng) {
        boolean alongX = rng.nextBoolean();
        int len = 4, height = 5;
        int ox = jitterOrigin(rng, alongX ? len : 1);
        int oz = jitterOrigin(rng, alongX ? 1 : len);
        int base = GROUND_Y + 1;
        int dx = alongX ? 1 : 0, dz = alongX ? 0 : 1;

        // Corner-less frame: bottom/top rows span the middle two, sides full height
        for (int i = 1; i <= 2; i++) {
            chunkData.setBlock(ox + i * dx, base, oz + i * dz, Material.OBSIDIAN);
            chunkData.setBlock(ox + i * dx, base + height - 1, oz + i * dz, Material.OBSIDIAN);
        }
        for (int y = base + 1; y <= base + height - 2; y++) {
            chunkData.setBlock(ox, y, oz, Material.OBSIDIAN);
            chunkData.setBlock(ox + 3 * dx, y, oz + 3 * dz, Material.OBSIDIAN);
        }

        // Break the top: remove 1-3 of the upper blocks
        int breaks = 1 + rng.nextInt(3);
        for (int i = 0; i < breaks; i++) {
            switch (rng.nextInt(4)) {
                case 0 -> chunkData.setBlock(ox + dx, base + height - 1, oz + dz, Material.AIR);
                case 1 -> chunkData.setBlock(ox + 2 * dx, base + height - 1, oz + 2 * dz, Material.AIR);
                case 2 -> chunkData.setBlock(ox, base + height - 2, oz, Material.AIR);
                default -> chunkData.setBlock(ox + 3 * dx, base + height - 2, oz + 3 * dz, Material.AIR);
            }
        }
        // The broken blocks landed nearby
        int loose = 1 + rng.nextInt(2);
        for (int i = 0; i < loose; i++) {
            int lx = Math.min(13, Math.max(2, ox + rng.nextInt(6) - 1));
            int lz = Math.min(13, Math.max(2, oz + rng.nextInt(6) - 1));
            if (chunkData.getType(lx, base, lz) == Material.AIR) {
                chunkData.setBlock(lx, base, lz, Material.OBSIDIAN);
            }
        }
        if (rng.nextInt(100) < 40) {
            int nx = Math.min(13, ox + 1 + dx), nz = Math.min(13, oz + 1 + dz);
            if (chunkData.getType(nx, base, nz) == Material.AIR) {
                chunkData.setBlock(nx, base, nz, Material.NETHERRACK);
            }
        }
    }

    /** A perfect obsidian pillar. Everything else here decays; this does not. */
    private void placeMonolith(ChunkData chunkData, Random rng) {
        int cx = jitterOrigin(rng, 3) + 1;
        int cz = jitterOrigin(rng, 3) + 1;
        int base = GROUND_Y + 1;

        for (int y = base; y < base + 5; y++) {
            chunkData.setBlock(cx, y, cz, Material.OBSIDIAN);
        }
        // The grass around it died
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                chunkData.setBlock(cx + dx, GROUND_Y, cz + dz, Material.DIRT);
            }
        }
    }

    /** A sealed 3x3x3 glass case, curated for nobody. */
    private void placeGlassBox(ChunkData chunkData, Random rng) {
        final int s = 3;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;

        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                for (int y = base; y < base + s; y++) {
                    boolean interior = x == ox + 1 && z == oz + 1 && y > base && y < base + s - 1;
                    boolean interiorFloor = x == ox + 1 && z == oz + 1 && y == base;
                    if (interior) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    } else if (!interiorFloor) {
                        chunkData.setBlock(x, y, z, Material.GLASS);
                    }
                }
            }
        }
        // The exhibit
        int exhibit = rng.nextInt(100);
        Material item = exhibit < 30 ? Material.TORCH
                : exhibit < 50 ? Material.OAK_SAPLING
                : exhibit < 60 ? Material.COBBLESTONE
                : Material.AIR;
        chunkData.setBlock(ox + 1, base, oz + 1, item);
    }

    /** Six graves, five occupants. */
    private void placeGraveyard(ChunkData chunkData, Random rng) {
        int ox = jitterOrigin(rng, 9);
        int oz = jitterOrigin(rng, 8);
        int base = GROUND_Y + 1;
        int open = rng.nextInt(6);

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int gx = ox + col * 3 + 1;   // mound cells gx, gx+1; headstone at gx-1
                int gz = oz + row * 4 + 1;
                if (row * 3 + col == open) {
                    // Dug open: a 2-deep hole and the soil heaped beside it
                    for (int x = gx; x <= gx + 1; x++) {
                        chunkData.setBlock(x, GROUND_Y, gz, Material.AIR);
                        chunkData.setBlock(x, GROUND_Y - 1, gz, Material.AIR);
                    }
                    chunkData.setBlock(gx, base, gz + 1, Material.DIRT);
                    chunkData.setBlock(gx + 1, base, gz + 1, Material.DIRT);
                } else {
                    chunkData.setBlock(gx, base, gz, Material.DIRT);
                    chunkData.setBlock(gx + 1, base, gz, Material.DIRT);
                }
                placeHeadstone(chunkData, rng, gx - 1, gz, BlockFace.WEST);
            }
        }

        // A surviving fragment of the old fence along the north edge
        if (rng.nextInt(100) < 30) {
            int len = 3 + rng.nextInt(3);
            int fx = ox + rng.nextInt(Math.max(1, 9 - len));
            for (int i = 0; i < len; i++) {
                Fence fence = (Fence) Material.OAK_FENCE.createBlockData();
                if (i > 0) fence.setFace(BlockFace.WEST, true);
                if (i < len - 1) fence.setFace(BlockFace.EAST, true);
                chunkData.setBlock(fx + i, base, oz, fence);
            }
        }
    }

    /** 10x6 chapel, tall at the altar end, gone at the door end. The pews still face west. */
    private void placeChapelRuin(ChunkData chunkData, Random rng) {
        final int w = 10, d = 6;
        int ox = jitterOrigin(rng, w);
        int oz = jitterOrigin(rng, d);
        int base = GROUND_Y + 1;
        int cz = oz + d / 2;

        for (int x = ox; x < ox + w; x++) {
            double t = (x - ox) / (double) (w - 1);
            for (int z = oz; z < oz + d; z++) {
                boolean perimeter = x == ox || x == ox + w - 1 || z == oz || z == oz + d - 1;
                if (!perimeter) continue;
                if (x == ox + w - 1 && z == cz) continue; // east doorway gap
                int h = (int) Math.round((1 - t) * 3.5) + rng.nextInt(2);
                h = Math.max(0, Math.min(4, h));
                for (int y = base; y < base + h; y++) {
                    if (rng.nextInt(100) < 15) continue; // dropout
                    chunkData.setBlock(x, y, z, weatheredCobble(rng));
                }
            }
        }

        // Altar at the west-center
        chunkData.setBlock(ox + 1, base, cz, Material.COBBLESTONE);
        chunkData.setBlock(ox + 1, base + 1, cz, Material.SMOOTH_STONE_SLAB);
        if (chunkData.getType(ox, base + 1, cz) != Material.AIR) {
            Directional torch = (Directional) Material.WALL_TORCH.createBlockData();
            torch.setFacing(BlockFace.EAST);
            chunkData.setBlock(ox + 1, base + 2, cz, torch);
        } else {
            chunkData.setBlock(ox + 2, base, cz - 1, Material.TORCH);
        }

        // Pews: oak stairs facing the altar, 60% survival
        for (int px = ox + 3; px <= ox + 7; px += 2) {
            for (int pz : new int[]{ cz - 1, cz + 1 }) {
                if (rng.nextInt(100) >= 60) continue;
                Stairs pew = (Stairs) Material.OAK_STAIRS.createBlockData();
                pew.setFacing(BlockFace.WEST);
                chunkData.setBlock(px, base, pz, pew);
            }
        }

        // One glass window column in the tall west wall
        chunkData.setBlock(ox, base + 1, cz - 1, Material.GLASS);
        chunkData.setBlock(ox, base + 2, cz - 1, Material.GLASS);
    }

    /** A patch of alpha winter mode the update never reached. */
    private void placeWinterScar(ChunkData chunkData, Random rng) {
        final int s = 9;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;
        boolean[][] mask = blobMask(rng, s, 4.5);
        double c = (s - 1) / 2.0;

        for (int x = 0; x < s; x++) {
            for (int z = 0; z < s; z++) {
                if (!mask[x][z]) continue;
                double dist = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));

                Snowable grass = (Snowable) Material.GRASS_BLOCK.createBlockData();
                grass.setSnowy(true);
                chunkData.setBlock(ox + x, GROUND_Y, oz + z, grass);

                Snow snow = (Snow) Material.SNOW.createBlockData();
                snow.setLayers(Math.max(1, Math.min(3, 3 - (int) (dist / 2))));
                chunkData.setBlock(ox + x, base, oz + z, snow);

                if (dist < 1.5 && rng.nextInt(100) < 50) {
                    chunkData.setBlock(ox + x, GROUND_Y, oz + z, Material.ICE);
                    chunkData.setBlock(ox + x, base, oz + z, Material.AIR);
                }
            }
        }
    }

    /** Ten faces looking in random directions, and one that is looking at you. */
    private void placePumpkinPatch(ChunkData chunkData, Random rng) {
        final int s = 7;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;

        List<int[]> spots = new ArrayList<>();
        int want = 6 + rng.nextInt(5);
        for (int attempt = 0; attempt < 30 && spots.size() < want; attempt++) {
            int px = ox + rng.nextInt(s), pz = oz + rng.nextInt(s);
            boolean adjacent = false;
            for (int[] p : spots) {
                if (Math.abs(p[0] - px) <= 1 && Math.abs(p[1] - pz) <= 1) { adjacent = true; break; }
            }
            if (!adjacent) spots.add(new int[]{ px, pz });
        }

        int watcher = rng.nextInt(spots.size());
        for (int i = 0; i < spots.size(); i++) {
            int[] p = spots.get(i);
            Directional pumpkin;
            if (i == watcher) {
                pumpkin = (Directional) Material.JACK_O_LANTERN.createBlockData();
                pumpkin.setFacing(facingChunkCenter(p[0], p[1]));
            } else {
                pumpkin = (Directional) Material.CARVED_PUMPKIN.createBlockData();
                pumpkin.setFacing(CARDINALS[rng.nextInt(4)]);
            }
            chunkData.setBlock(p[0], base, p[1], pumpkin);
        }
    }

    /** The ground still has an open wound that glows. Stone bowl keeps it contained. */
    private void placeLavaPool(ChunkData chunkData, Random rng) {
        final int s = 6;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        boolean[][] mask = blobMask(rng, s, 2.4);

        // Stone lip: every non-blob cell within 1 of a blob cell
        for (int x = 0; x < s; x++) {
            for (int z = 0; z < s; z++) {
                if (mask[x][z]) continue;
                boolean nearBlob = false;
                for (int dx = -1; dx <= 1 && !nearBlob; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx, nz = z + dz;
                        if (nx >= 0 && nx < s && nz >= 0 && nz < s && mask[nx][nz]) { nearBlob = true; break; }
                    }
                }
                if (nearBlob) {
                    chunkData.setBlock(ox + x, GROUND_Y, oz + z, Material.STONE);
                }
            }
        }
        boolean obsidianPlaced = rng.nextInt(100) >= 30;
        for (int x = 0; x < s; x++) {
            for (int z = 0; z < s; z++) {
                if (!mask[x][z]) continue;
                chunkData.setBlock(ox + x, GROUND_Y - 1, oz + z, Material.STONE);
                if (!obsidianPlaced) {
                    chunkData.setBlock(ox + x, GROUND_Y, oz + z, Material.OBSIDIAN);
                    obsidianPlaced = true;
                } else {
                    chunkData.setBlock(ox + x, GROUND_Y, oz + z, Material.LAVA);
                }
            }
        }
    }

    /** Track laid with real care between two points that don't exist. */
    private void placeRailsToNowhere(ChunkData chunkData, Random rng) {
        boolean alongX = rng.nextBoolean();
        int len = 6 + rng.nextInt(7);
        int ox = jitterOrigin(rng, alongX ? len : 1);
        int oz = jitterOrigin(rng, alongX ? 1 : len);
        int base = GROUND_Y + 1;

        for (int i = 0; i < len; i++) {
            Rail rail = (Rail) Material.RAIL.createBlockData();
            rail.setShape(alongX ? Rail.Shape.EAST_WEST : Rail.Shape.NORTH_SOUTH);
            chunkData.setBlock(ox + (alongX ? i : 0), base, oz + (alongX ? 0 : i), rail);
        }
        if (rng.nextInt(100) < 30) {
            int mid = len / 2;
            int bx = ox + (alongX ? mid : 1), bz = oz + (alongX ? 1 : mid);
            if (bx <= 14 && bz <= 14 && chunkData.getType(bx, base, bz) == Material.AIR) {
                chunkData.setBlock(bx, base, bz, Material.GRAVEL);
            }
        }
        if (rng.nextInt(100) < 20) {
            chunkData.setBlock(ox, base + 1, oz, Material.TORCH);
            chunkData.setBlock(ox, base, oz, Material.COBBLESTONE);
        }
    }

    /** Signs were the only way to leave a message; every one of them says nothing. */
    private void placeSignpostCluster(ChunkData chunkData, Random rng) {
        final int s = 4;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;

        int count = 1 + rng.nextInt(3);
        List<int[]> spots = new ArrayList<>();
        for (int attempt = 0; attempt < 15 && spots.size() < count; attempt++) {
            int px = ox + rng.nextInt(s), pz = oz + rng.nextInt(s);
            boolean adjacent = false;
            for (int[] p : spots) {
                if (Math.abs(p[0] - px) <= 1 && Math.abs(p[1] - pz) <= 1) { adjacent = true; break; }
            }
            if (!adjacent) spots.add(new int[]{ px, pz });
        }

        boolean elevatedUsed = rng.nextInt(100) >= 25;
        for (int[] p : spots) {
            Rotatable sign = (Rotatable) Material.OAK_SIGN.createBlockData();
            sign.setRotation(SIGN_ROTATIONS[rng.nextInt(SIGN_ROTATIONS.length)]);
            if (!elevatedUsed) {
                chunkData.setBlock(p[0], base, p[1], Material.OAK_FENCE);
                chunkData.setBlock(p[0], base + 1, p[1], sign);
                elevatedUsed = true;
            } else {
                chunkData.setBlock(p[0], base, p[1], sign);
            }
        }
    }

    /** One sponge. Someone with operator powers was here, once. */
    private void placeSponge(ChunkData chunkData, Random rng) {
        int x = jitterOrigin(rng, 1);
        int z = jitterOrigin(rng, 1);
        chunkData.setBlock(x, GROUND_Y + 1, z, Material.SPONGE);
    }

    /** A mossy room just under the field, with a hole where the ceiling gave in. */
    private void placeDungeonBreach(ChunkData chunkData, Random rng) {
        final int s = 7;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int floorY = GROUND_Y - 6;      // y58
        int ceilY = GROUND_Y - 2;       // y62

        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                boolean perimeter = x == ox || x == ox + s - 1 || z == oz || z == oz + s - 1;
                Material mossy = rng.nextBoolean() ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE;
                chunkData.setBlock(x, floorY, z, mossy);
                chunkData.setBlock(x, ceilY, z, rng.nextBoolean() ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
                for (int y = floorY + 1; y < ceilY; y++) {
                    chunkData.setBlock(x, y, z,
                            perimeter ? (rng.nextBoolean() ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE)
                                      : Material.AIR);
                }
            }
        }

        // The furniture: an inert spawner and an empty chest
        int cx = ox + s / 2, cz = oz + s / 2;
        chunkData.setBlock(cx, floorY + 1, cz, Material.SPAWNER);
        org.bukkit.block.data.type.Chest chest =
                (org.bukkit.block.data.type.Chest) Material.CHEST.createBlockData();
        chest.setFacing(BlockFace.EAST);
        chunkData.setBlock(ox + 1, floorY + 1, cz, chest);

        // The breach: a 2x2 hole in the field looking down into the dark
        int bx = ox + 1 + rng.nextInt(2) * (s - 4);
        int bz = oz + 1 + rng.nextInt(2) * (s - 4);
        for (int x = bx; x <= bx + 1; x++) {
            for (int z = bz; z <= bz + 1; z++) {
                chunkData.setBlock(x, GROUND_Y, z, Material.AIR);
                chunkData.setBlock(x, GROUND_Y - 1, z, Material.AIR);
                chunkData.setBlock(x, ceilY, z, Material.AIR);
            }
        }
        // The fallen soil, and the way back out
        chunkData.setBlock(bx, floorY + 1, bz, Material.DIRT);
        chunkData.setBlock(bx + 1, floorY + 1, bz, Material.DIRT);
        if (rng.nextBoolean()) {
            chunkData.setBlock(bx, floorY + 2, bz, Material.DIRT);
        }
    }

    /** A square of the wrong biome, as if the generator briefly forgot where it was. */
    private void placeSandAnomaly(ChunkData chunkData, Random rng) {
        final int s = 6;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;
        boolean[][] mask = blobMask(rng, s, 2.6);

        for (int x = 0; x < s; x++) {
            for (int z = 0; z < s; z++) {
                if (!mask[x][z]) continue;
                chunkData.setBlock(ox + x, GROUND_Y, oz + z, Material.SAND);
                chunkData.setBlock(ox + x, GROUND_Y - 1, oz + z, Material.SAND);
            }
        }

        // 1-2 cactus columns on cells with all four neighbors clear
        int placed = 0, want = 1 + rng.nextInt(2);
        for (int attempt = 0; attempt < 15 && placed < want; attempt++) {
            int x = rng.nextInt(s), z = rng.nextInt(s);
            if (!mask[x][z]) continue;
            int wx = ox + x, wz = oz + z;
            boolean clear = chunkData.getType(wx, base, wz) == Material.AIR
                    && chunkData.getType(wx - 1, base, wz) == Material.AIR
                    && chunkData.getType(wx + 1, base, wz) == Material.AIR
                    && chunkData.getType(wx, base, wz - 1) == Material.AIR
                    && chunkData.getType(wx, base, wz + 1) == Material.AIR;
            if (!clear) continue;
            int h = 1 + rng.nextInt(3);
            for (int y = base; y < base + h; y++) {
                chunkData.setBlock(wx, y, wz, Material.CACTUS);
            }
            placed++;
        }
    }

    // ---------------------------------------------------------------
    //  Houses — actual builds. Someone lived here. Past tense.
    // ---------------------------------------------------------------

    /** Foot at (x,z), head one step toward {@code facing}. */
    private static void placeBed(ChunkData chunkData, int x, int y, int z, BlockFace facing) {
        Bed foot = (Bed) Material.RED_BED.createBlockData();
        foot.setPart(Bed.Part.FOOT);
        foot.setFacing(facing);
        Bed head = (Bed) Material.RED_BED.createBlockData();
        head.setPart(Bed.Part.HEAD);
        head.setFacing(facing);
        chunkData.setBlock(x, y, z, foot);
        chunkData.setBlock(x + facing.getModX(), y, z + facing.getModZ(), head);
    }

    private static void placeLadderColumn(ChunkData chunkData, int x, int z,
                                          int yMin, int yMax, BlockFace facing) {
        for (int y = yMin; y <= yMax; y++) {
            org.bukkit.block.data.type.Ladder ladder =
                    (org.bukkit.block.data.type.Ladder) Material.LADDER.createBlockData();
            ladder.setFacing(facing);
            chunkData.setBlock(x, y, z, ladder);
        }
    }

    /** 8x7 two-story plank house: ladder to the bedroom upstairs. */
    private void placeTwoStoryHouse(ChunkData chunkData, Random rng) {
        final int w = 8, d = 7;
        int ox = jitterOrigin(rng, w);
        int oz = jitterOrigin(rng, d);
        int base = GROUND_Y + 1;      // ground floor y65-68
        int floor2 = base + 4;        // second floor planks y69
        int upperTop = floor2 + 3;    // upper walls y70-72
        int roofY = upperTop + 1;     // y73
        int cx = ox + w / 2;

        for (int x = ox; x < ox + w; x++) {
            for (int z = oz; z < oz + d; z++) {
                boolean perimeter = x == ox || x == ox + w - 1 || z == oz || z == oz + d - 1;
                boolean corner = (x == ox || x == ox + w - 1) && (z == oz || z == oz + d - 1);
                chunkData.setBlock(x, GROUND_Y, z, Material.OAK_PLANKS);
                for (int y = base; y < roofY; y++) {
                    if (y == floor2) {
                        chunkData.setBlock(x, y, z, Material.OAK_PLANKS); // full second floor
                    } else if (corner) {
                        chunkData.setBlock(x, y, z, Material.OAK_LOG);
                    } else if (perimeter) {
                        chunkData.setBlock(x, y, z, Material.OAK_PLANKS);
                    } else {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                }
                chunkData.setBlock(x, roofY, z, Material.OAK_PLANKS);
            }
        }

        // Door on the south wall, ground-floor and upstairs windows
        int doorZ = oz + d - 1;
        Door bottom = (Door) Material.OAK_DOOR.createBlockData();
        bottom.setFacing(BlockFace.NORTH);
        bottom.setHalf(Bisected.Half.BOTTOM);
        Door top = (Door) Material.OAK_DOOR.createBlockData();
        top.setFacing(BlockFace.NORTH);
        top.setHalf(Bisected.Half.TOP);
        chunkData.setBlock(cx, base, doorZ, bottom);
        chunkData.setBlock(cx, base + 1, doorZ, top);

        chunkData.setBlock(ox + 2, base + 1, oz, Material.GLASS);
        chunkData.setBlock(ox + w - 3, base + 1, oz, Material.GLASS);
        chunkData.setBlock(ox, base + 1, oz + d / 2, Material.GLASS);
        chunkData.setBlock(ox + w - 1, base + 1, oz + d / 2, Material.GLASS);
        chunkData.setBlock(ox + 2, floor2 + 2, doorZ, Material.GLASS);
        chunkData.setBlock(ox + w - 3, floor2 + 2, doorZ, Material.GLASS);
        chunkData.setBlock(cx, floor2 + 2, oz, Material.GLASS);

        // Ladder up the north wall with an opening in the second floor
        int lx = ox + 1, lz = oz + 1;
        chunkData.setBlock(lx, floor2, lz, Material.AIR);
        placeLadderColumn(chunkData, lx, lz, base, floor2, BlockFace.SOUTH);

        // Downstairs: crafting table + furnace; upstairs: bed + bookshelf
        chunkData.setBlock(ox + w - 2, base, oz + 1, Material.CRAFTING_TABLE);
        Directional furnace = (Directional) Material.FURNACE.createBlockData();
        furnace.setFacing(BlockFace.SOUTH);
        chunkData.setBlock(ox + w - 3, base, oz + 1, furnace);
        placeBed(chunkData, ox + w - 3, floor2 + 1, oz + 1, BlockFace.WEST);
        chunkData.setBlock(ox + 1, floor2 + 1, oz + d - 2, Material.BOOKSHELF);

        // A torch on each floor
        Directional torch = (Directional) Material.WALL_TORCH.createBlockData();
        torch.setFacing(BlockFace.NORTH);
        chunkData.setBlock(cx, base + 2, doorZ - 1, torch);
        chunkData.setBlock(cx, floor2 + 2, oz + d / 2, Material.TORCH);
    }

    /** 8x6 stone cottage with a pitched oak roof. */
    private void placeStoneCottage(ChunkData chunkData, Random rng) {
        final int w = 8, d = 6;
        int ox = jitterOrigin(rng, w);
        int oz = jitterOrigin(rng, d);
        int base = GROUND_Y + 1;
        int wallTop = base + 2;   // cobble walls y65-67
        int cx = ox + w / 2;

        for (int x = ox; x < ox + w; x++) {
            for (int z = oz; z < oz + d; z++) {
                boolean perimeter = x == ox || x == ox + w - 1 || z == oz || z == oz + d - 1;
                for (int y = base; y <= wallTop; y++) {
                    chunkData.setBlock(x, y, z, perimeter ? weatheredCobble(rng) : Material.AIR);
                }
            }
        }

        // Pitched roof sloping over z: stairs rise from both long sides to a plank ridge
        for (int x = ox; x < ox + w; x++) {
            for (int zi = 0; zi < d; zi++) {
                int z = oz + zi;
                int rise = Math.min(zi, d - 1 - zi);   // 0,1,2,2,1,0
                int y = wallTop + 1 + rise;
                if (rise >= 2) {
                    chunkData.setBlock(x, y, z, Material.OAK_PLANKS); // ridge
                } else {
                    Stairs stair = (Stairs) Material.OAK_STAIRS.createBlockData();
                    stair.setFacing(zi < d / 2 ? BlockFace.SOUTH : BlockFace.NORTH);
                    chunkData.setBlock(x, y, z, stair);
                }
            }
        }
        // Gable fill on the short ends, under the roof line
        for (int x : new int[]{ ox, ox + w - 1 }) {
            for (int zi = 1; zi < d - 1; zi++) {
                int rise = Math.min(zi, d - 1 - zi);
                for (int y = wallTop + 1; y < wallTop + 1 + rise; y++) {
                    chunkData.setBlock(x, y, oz + zi, weatheredCobble(rng));
                }
            }
        }

        // Door on the south side, windows north
        Door bottom = (Door) Material.OAK_DOOR.createBlockData();
        bottom.setFacing(BlockFace.NORTH);
        bottom.setHalf(Bisected.Half.BOTTOM);
        Door top = (Door) Material.OAK_DOOR.createBlockData();
        top.setFacing(BlockFace.NORTH);
        top.setHalf(Bisected.Half.TOP);
        chunkData.setBlock(cx, base, oz + d - 1, bottom);
        chunkData.setBlock(cx, base + 1, oz + d - 1, top);
        chunkData.setBlock(ox + 2, base + 1, oz, Material.GLASS);
        chunkData.setBlock(ox + w - 3, base + 1, oz, Material.GLASS);

        // Interior: bed, furnace, torch
        placeBed(chunkData, ox + 1, base, oz + 1, BlockFace.EAST);
        Directional furnace = (Directional) Material.FURNACE.createBlockData();
        furnace.setFacing(BlockFace.SOUTH);
        chunkData.setBlock(ox + w - 2, base, oz + 1, furnace);
        Directional torch = (Directional) Material.WALL_TORCH.createBlockData();
        torch.setFacing(BlockFace.NORTH);
        chunkData.setBlock(cx - 1, base + 2, oz + d - 2, torch);
    }

    /** 11x10 farmhouse: a small house, its pen, and the crops nobody harvested. */
    private void placeFarmhouse(ChunkData chunkData, Random rng) {
        int ox = jitterOrigin(rng, 11);
        int oz = jitterOrigin(rng, 10);
        int base = GROUND_Y + 1;
        final int hw = 7, hd = 6;   // house footprint at (ox, oz)
        int wallTop = base + 2;
        int roofY = wallTop + 1;
        int cx = ox + hw / 2;

        // House: plank walls, flat roof
        for (int x = ox; x < ox + hw; x++) {
            for (int z = oz; z < oz + hd; z++) {
                boolean perimeter = x == ox || x == ox + hw - 1 || z == oz || z == oz + hd - 1;
                boolean corner = (x == ox || x == ox + hw - 1) && (z == oz || z == oz + hd - 1);
                for (int y = base; y <= wallTop; y++) {
                    if (corner) {
                        chunkData.setBlock(x, y, z, Material.OAK_LOG);
                    } else if (perimeter) {
                        chunkData.setBlock(x, y, z, Material.OAK_PLANKS);
                    } else {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                }
                chunkData.setBlock(x, roofY, z, Material.OAK_PLANKS);
            }
        }
        // Door faces the fields (south); one window west
        Door bottom = (Door) Material.OAK_DOOR.createBlockData();
        bottom.setFacing(BlockFace.NORTH);
        bottom.setHalf(Bisected.Half.BOTTOM);
        Door top = (Door) Material.OAK_DOOR.createBlockData();
        top.setFacing(BlockFace.NORTH);
        top.setHalf(Bisected.Half.TOP);
        chunkData.setBlock(cx, base, oz + hd - 1, bottom);
        chunkData.setBlock(cx, base + 1, oz + hd - 1, top);
        chunkData.setBlock(ox, base + 1, oz + hd / 2, Material.GLASS);

        placeBed(chunkData, ox + 1, base, oz + 1, BlockFace.EAST);
        chunkData.setBlock(ox + hw - 2, base, oz + 1, Material.CRAFTING_TABLE);

        // Pen south of the house (fence square with a gap toward the door)
        int px = ox, pz = oz + hd + 1, ps = 4;
        if (pz + ps <= 15) {
            for (int x = px; x < px + ps; x++) {
                for (int z = pz; z < pz + ps; z++) {
                    if (!onPerimeter(x, z, px, pz, ps)) continue;
                    if (x == px + ps / 2 && z == pz) continue; // gap facing the house
                    Fence fence = (Fence) Material.OAK_FENCE.createBlockData();
                    if (onPerimeter(x - 1, z, px, pz, ps) && !(x - 1 == px + ps / 2 && z == pz)) fence.setFace(BlockFace.WEST, true);
                    if (onPerimeter(x + 1, z, px, pz, ps) && !(x + 1 == px + ps / 2 && z == pz)) fence.setFace(BlockFace.EAST, true);
                    if (onPerimeter(x, z - 1, px, pz, ps)) fence.setFace(BlockFace.NORTH, true);
                    if (onPerimeter(x, z + 1, px, pz, ps)) fence.setFace(BlockFace.SOUTH, true);
                    chunkData.setBlock(x, base, z, fence);
                }
            }
        }

        // Crop rows east of the pen: wheat / water trench / wheat
        int fx = ox + 5, fz = oz + hd + 1;
        if (fz + 2 <= 15) {
            for (int i = 0; i < 4 && fx + i <= 13; i++) {
                for (int row = 0; row < 3; row++) {
                    int z = fz + row;
                    if (row == 1) {
                        chunkData.setBlock(fx + i, GROUND_Y, z, Material.WATER);
                        continue;
                    }
                    Farmland farmland = (Farmland) Material.FARMLAND.createBlockData();
                    farmland.setMoisture(farmland.getMaximumMoisture());
                    chunkData.setBlock(fx + i, GROUND_Y, z, farmland);
                    Ageable wheat = (Ageable) Material.WHEAT.createBlockData();
                    wheat.setAge(2 + rng.nextInt(wheat.getMaximumAge() - 1));
                    chunkData.setBlock(fx + i, base, z, wheat);
                }
            }
        }
    }

    /** 9x8 barn: log frame, tall doorway, a loft with a ladder. Empty stalls. */
    private void placeBarn(ChunkData chunkData, Random rng) {
        final int w = 9, d = 8;
        int ox = jitterOrigin(rng, w);
        int oz = jitterOrigin(rng, d);
        int base = GROUND_Y + 1;
        int wallTop = base + 4;   // walls y65-69
        int roofY = wallTop + 1;  // y70
        int loftY = base + 3;     // loft floor y68
        int doorZ = oz + d - 1;

        for (int x = ox; x < ox + w; x++) {
            for (int z = oz; z < oz + d; z++) {
                boolean perimeter = x == ox || x == ox + w - 1 || z == oz || z == oz + d - 1;
                boolean corner = (x == ox || x == ox + w - 1) && (z == oz || z == oz + d - 1);
                for (int y = base; y <= wallTop; y++) {
                    if (corner) {
                        chunkData.setBlock(x, y, z, Material.OAK_LOG);
                    } else if (perimeter) {
                        chunkData.setBlock(x, y, z, Material.OAK_PLANKS);
                    } else {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                }
                chunkData.setBlock(x, roofY, z, Material.OAK_PLANKS);
            }
        }

        // Big open doorway, 2 wide x 3 tall, south end
        for (int x = ox + w / 2 - 1; x <= ox + w / 2; x++) {
            for (int y = base; y <= base + 2; y++) {
                chunkData.setBlock(x, y, doorZ, Material.AIR);
            }
        }

        // Loft over the north half, with a ladder
        for (int x = ox + 1; x < ox + w - 1; x++) {
            for (int z = oz + 1; z <= oz + 3; z++) {
                chunkData.setBlock(x, loftY, z, Material.OAK_PLANKS);
            }
        }
        placeLadderColumn(chunkData, ox + 1, oz + 4, base, loftY, BlockFace.EAST);

        // Stalls along the west wall, a plank stack in one
        for (int z = oz + 4; z <= oz + 6; z++) {
            Fence fence = (Fence) Material.OAK_FENCE.createBlockData();
            if (z > oz + 4) fence.setFace(BlockFace.NORTH, true);
            if (z < oz + 6) fence.setFace(BlockFace.SOUTH, true);
            chunkData.setBlock(ox + 2, base, z, fence);
        }
        chunkData.setBlock(ox + 1, base, oz + 5, Material.OAK_PLANKS);
        if (rng.nextBoolean()) {
            chunkData.setBlock(ox + 1, base + 1, oz + 5, Material.OAK_PLANKS);
        }

        // Torch beside the doorway
        Directional torch = (Directional) Material.WALL_TORCH.createBlockData();
        torch.setFacing(BlockFace.NORTH);
        chunkData.setBlock(ox + w / 2 - 2, base + 2, doorZ - 1, torch);
    }

    /** 7x8 A-frame cabin: all roof, one room, one bed. */
    private void placeAFrameCabin(ChunkData chunkData, Random rng) {
        final int w = 7, d = 8;
        int ox = jitterOrigin(rng, w);
        int oz = jitterOrigin(rng, d);
        int base = GROUND_Y + 1;
        int cx = ox + 3;
        int frontZ = oz + d - 1;

        // Roof: stairs rising from the ground on both sides to a plank ridge
        for (int z = oz; z < oz + d; z++) {
            for (int xi = 0; xi < w; xi++) {
                int rise = Math.min(xi, w - 1 - xi);   // 0,1,2,3,2,1,0
                int y = base + rise;
                if (rise == 3) {
                    chunkData.setBlock(ox + xi, y, z, Material.OAK_PLANKS); // ridge
                } else {
                    Stairs stair = (Stairs) Material.OAK_STAIRS.createBlockData();
                    stair.setFacing(xi < 3 ? BlockFace.EAST : BlockFace.WEST);
                    chunkData.setBlock(ox + xi, y, z, stair);
                }
            }
        }

        // Gable ends: triangular plank fill (y65 spans x1-x5, y66 x2-x4, y67 x3)
        for (int z : new int[]{ oz, frontZ }) {
            for (int xi = 1; xi <= 5; xi++) chunkData.setBlock(ox + xi, base, z, Material.OAK_PLANKS);
            for (int xi = 2; xi <= 4; xi++) chunkData.setBlock(ox + xi, base + 1, z, Material.OAK_PLANKS);
            chunkData.setBlock(cx, base + 2, z, Material.OAK_PLANKS);
        }
        // Front gable: door + two windows
        Door bottom = (Door) Material.OAK_DOOR.createBlockData();
        bottom.setFacing(BlockFace.NORTH);
        bottom.setHalf(Bisected.Half.BOTTOM);
        Door top = (Door) Material.OAK_DOOR.createBlockData();
        top.setFacing(BlockFace.NORTH);
        top.setHalf(Bisected.Half.TOP);
        chunkData.setBlock(cx, base, frontZ, bottom);
        chunkData.setBlock(cx, base + 1, frontZ, top);
        chunkData.setBlock(ox + 2, base, frontZ, Material.GLASS);
        chunkData.setBlock(ox + 4, base, frontZ, Material.GLASS);

        // Interior: bed along the ridge line, a floor torch by the back wall
        placeBed(chunkData, cx, base, oz + 2, BlockFace.SOUTH);
        chunkData.setBlock(ox + 2, base, oz + 4, Material.TORCH);
    }

    /** 5x5 watchhouse: a sealed, lit cabin on a stone base. The ladder only reaches the roof. */
    private void placeWatchhouse(ChunkData chunkData, Random rng) {
        final int s = 5;
        int ox = 2 + rng.nextInt(8);   // keep room for the ladder row at oz+s
        int oz = 2 + rng.nextInt(8);
        int base = GROUND_Y + 1;
        int baseTop = base + 2;    // cobble y65-67
        int cabinTop = baseTop + 3; // cabin walls y68-70
        int roofY = cabinTop + 1;  // y71
        int cx = ox + 2, cz = oz + 2;

        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                for (int y = base; y <= baseTop; y++) {
                    chunkData.setBlock(x, y, z, weatheredCobble(rng));
                }
                boolean perimeter = x == ox || x == ox + s - 1 || z == oz || z == oz + s - 1;
                for (int y = baseTop + 1; y <= cabinTop; y++) {
                    chunkData.setBlock(x, y, z, perimeter ? Material.OAK_PLANKS : Material.AIR);
                }
                chunkData.setBlock(x, roofY, z, Material.OAK_PLANKS);
            }
        }
        // A window in each cabin wall; the light inside is on, the room is sealed
        int windowY = baseTop + 2;
        chunkData.setBlock(cx, windowY, oz, Material.GLASS);
        chunkData.setBlock(cx, windowY, oz + s - 1, Material.GLASS);
        chunkData.setBlock(ox, windowY, cz, Material.GLASS);
        chunkData.setBlock(ox + s - 1, windowY, cz, Material.GLASS);
        chunkData.setBlock(cx, baseTop + 1, cz, Material.TORCH);

        // Exterior ladder up the south face, and a torch on the roof
        placeLadderColumn(chunkData, cx, oz + s, base, roofY, BlockFace.SOUTH);
        chunkData.setBlock(cx, roofY + 1, cz, Material.TORCH);
    }

    @Override
    public int getSpawnY() {
        return GROUND_Y + 1;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, GROUND_Y + 1, 0.5);
    }
}
