package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Level 101 — "Alpha"
 * An endless, dead-flat grass plain under a white sky, drowned in white fog.
 * Occasional oak trees, scattered dandelions and poppies, and lonely
 * player-built-looking structures: a plank cottage, a dirt hut, a
 * cobblestone well, a fence pen. The old world, too perfect and too empty.
 *
 * Structures are single-chunk (jittered inside a 2-block margin) — with the
 * fog wall at ~64 blocks the chunk grid is never visible. Blocks placed via
 * ChunkData get no neighbor updates, so fences, gates, doors, beds and wall
 * torches are placed with explicit BlockData (faces/facing/part set by hand).
 */
public class Level101ChunkGenerator extends BackroomsChunkGenerator {

    private static final int MAX_Y = 128;
    private static final int GROUND_Y = 64;

    // Structure templates: identical order for weights and placement switch
    private static final int[] STRUCTURE_WEIGHTS = { 35, 25, 25, 15 }; // cottage, hut, well, pen
    private static final int TOTAL_STRUCTURE_WEIGHT = 100;

    // Tunables (generator_config)
    private int treeChunkChance = 30;        // percent of chunks with 1-2 trees
    private int structureChunkChance = 5;    // percent of chunks with a structure
    private int decorationEmptyChance = 90;  // percent chance of bare grass per column
    private Material[] decorationMaterials = {
            Material.SHORT_GRASS, Material.DANDELION, Material.POPPY
    };
    private int[] decorationWeights = { 60, 25, 15 };
    private int totalDecorationWeight = 100;

    public Level101ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config == null) return;

        treeChunkChance = config.getInt("tree_chunk_chance", treeChunkChance);
        structureChunkChance = config.getInt("structure_chunk_chance", structureChunkChance);
        decorationEmptyChance = config.getInt("decoration_empty_chance", decorationEmptyChance);

        ConfigurationSection decoSection = config.getConfigurationSection("ground_decorations");
        if (decoSection != null) {
            var mats = new java.util.ArrayList<Material>();
            var weights = new java.util.ArrayList<Integer>();
            for (String key : decoSection.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m != null) {
                    mats.add(m);
                    weights.add(decoSection.getInt(key, 1));
                }
            }
            if (!mats.isEmpty()) {
                decorationMaterials = mats.toArray(new Material[0]);
                decorationWeights = weights.stream().mapToInt(Integer::intValue).toArray();
                totalDecorationWeight = 0;
                for (int w : decorationWeights) totalDecorationWeight += w;
            }
        }
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // --- Pass 1: flat terrain columns ---
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunkData.setBlock(x, 0, z, Material.BEDROCK);
                for (int y = 1; y <= 3; y++) {
                    chunkData.setBlock(x, y, z,
                            chunkRng.nextInt(4) < (4 - y) ? Material.BEDROCK : Material.STONE);
                }
                for (int y = 4; y < 60; y++) {
                    int roll = chunkRng.nextInt(1000);
                    if (y < 50 && roll < 20) {
                        chunkData.setBlock(x, y, z, Material.COAL_ORE);
                    } else if (y < 40 && roll < 30) {
                        chunkData.setBlock(x, y, z, Material.IRON_ORE);
                    } else if (y < 25 && roll < 33) {
                        chunkData.setBlock(x, y, z, Material.GOLD_ORE);
                    } else {
                        chunkData.setBlock(x, y, z, Material.STONE);
                    }
                }
                for (int y = 60; y < GROUND_Y; y++) {
                    chunkData.setBlock(x, y, z, Material.DIRT);
                }
                chunkData.setBlock(x, GROUND_Y, z, Material.GRASS_BLOCK);
            }
        }

        // --- Pass 2: at most one structure per eligible chunk ---
        boolean placedStructure = false;
        boolean spawnChunk = chunkX == 0 && chunkZ == 0;
        if (!spawnChunk && structureChunkChance > 0
                && Math.floorMod(blockHash(chunkX, chunkZ, seed, 0xA1FA), 100) < structureChunkChance) {
            int roll = chunkRng.nextInt(TOTAL_STRUCTURE_WEIGHT);
            if (roll < STRUCTURE_WEIGHTS[0]) {
                placeCottage(chunkData, chunkRng);
            } else if (roll < STRUCTURE_WEIGHTS[0] + STRUCTURE_WEIGHTS[1]) {
                placeDirtHut(chunkData, chunkRng);
            } else if (roll < STRUCTURE_WEIGHTS[0] + STRUCTURE_WEIGHTS[1] + STRUCTURE_WEIGHTS[2]) {
                placeWell(chunkData, chunkRng);
            } else {
                placeFencePen(chunkData, chunkRng);
            }
            placedStructure = true;
        }

        // --- Pass 3: trees, only in structure-free chunks ---
        if (!placedStructure && chunkRng.nextDouble() < treeChunkChance / 100.0) {
            int count = 1 + chunkRng.nextInt(2);
            for (int i = 0; i < count; i++) {
                int tx = chunkRng.nextInt(12) + 2;
                int tz = chunkRng.nextInt(12) + 2;
                if (chunkData.getType(tx, GROUND_Y, tz) == Material.GRASS_BLOCK) {
                    placeOakTree(chunkData, chunkRng, tx, GROUND_Y + 1, tz);
                }
            }
        }

        // --- Pass 4: flower / grass scatter on untouched grass ---
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (chunkData.getType(x, GROUND_Y + 1, z) != Material.AIR) continue;
                if (chunkData.getType(x, GROUND_Y, z) != Material.GRASS_BLOCK) continue;
                placeGroundDecoration(chunkData, x, z, chunkX * 16 + x, chunkZ * 16 + z, seed);
            }
        }
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
    //  Structures — all single-chunk, base y = GROUND_Y + 1
    // ---------------------------------------------------------------

    /** Origin so a {@code width}-wide footprint stays within [2, 13]. */
    private static int jitterOrigin(Random rng, int width) {
        return 2 + rng.nextInt(14 - width);
    }

    /**
     * 7x6 oak-plank cottage: log corners, plank walls y65-68, flat roof y69,
     * glass window in each non-door wall, oak door, wall torch, crafting
     * table, and a red bed against the wall opposite the door.
     */
    private void placeCottage(ChunkData chunkData, Random rng) {
        final int w = 7, d = 6;
        int ox = jitterOrigin(rng, w);
        int oz = jitterOrigin(rng, d);
        int base = GROUND_Y + 1;
        int wallTop = base + 3;   // walls y65-68
        int roofY = wallTop + 1;  // y69

        // Plank floor replaces grass
        for (int x = ox; x < ox + w; x++) {
            for (int z = oz; z < oz + d; z++) {
                chunkData.setBlock(x, GROUND_Y, z, Material.OAK_PLANKS);
            }
        }
        // Walls + interior air + roof
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
        BlockFace[] outwardFaces = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST };
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
        int inX = wallCenters[doorSide][2] == 0 ? 1 : 0;   // offset along the door wall
        int inZ = inX == 1 ? 0 : 1;
        int tx = wallCenters[doorSide][0] + inX - wallCenters[doorSide][2];
        int tz = wallCenters[doorSide][1] + inZ - wallCenters[doorSide][3];
        Directional torch = (Directional) Material.WALL_TORCH.createBlockData();
        torch.setFacing(inwardFaces[doorSide]);
        chunkData.setBlock(tx, base + 2, tz, torch);

        // Bed against the wall opposite the door, crafting table near the door wall.
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

        // Crafting table in an interior corner on the door side (doorway is centered, corners are clear)
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

        // Open 1x2 doorway centered on a random wall
        int side = rng.nextInt(4);
        int c = ox + s / 2, cMid = oz + s / 2;
        int dx = switch (side) { case 2 -> ox; case 3 -> ox + s - 1; default -> c; };
        int dz = switch (side) { case 0 -> oz; case 1 -> oz + s - 1; default -> cMid; };
        chunkData.setBlock(dx, base, dz, Material.AIR);
        chunkData.setBlock(dx, base + 1, dz, Material.AIR);

        // Floor torch in an interior corner (grass floor is solid)
        chunkData.setBlock(ox + 1, base, oz + 1, Material.TORCH);
    }

    /** 3x3 cobblestone well with a water shaft sunk into the ground. */
    private void placeWell(ChunkData chunkData, Random rng) {
        final int s = 3;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;
        int cx = ox + 1, cz = oz + 1;

        // Rim ring at y65
        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                if (x == cx && z == cz) continue;
                chunkData.setBlock(x, base, z, Material.COBBLESTONE);
            }
        }
        // Shaft: cobble bottom, still water column up to ground level
        chunkData.setBlock(cx, GROUND_Y - 3, cz, Material.COBBLESTONE);
        for (int y = GROUND_Y - 2; y <= GROUND_Y; y++) {
            chunkData.setBlock(cx, y, cz, Material.WATER);
        }
        // Posts at two opposite corners + slab roof
        for (int y = base + 1; y <= base + 2; y++) {
            chunkData.setBlock(ox, y, oz, Material.OAK_FENCE);
            chunkData.setBlock(ox + 2, y, oz + 2, Material.OAK_FENCE);
        }
        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                chunkData.setBlock(x, base + 3, z, Material.COBBLESTONE_SLAB);
            }
        }
    }

    /** 7x7 oak fence pen with a gate; interior left as grass for the decoration pass. */
    private void placeFencePen(ChunkData chunkData, Random rng) {
        final int s = 7;
        int ox = jitterOrigin(rng, s);
        int oz = jitterOrigin(rng, s);
        int base = GROUND_Y + 1;

        int gateSide = rng.nextInt(4);
        int gx = switch (gateSide) { case 2 -> ox; case 3 -> ox + s - 1; default -> ox + s / 2; };
        int gz = switch (gateSide) { case 0 -> oz; case 1 -> oz + s - 1; default -> oz + s / 2; };

        for (int x = ox; x < ox + s; x++) {
            for (int z = oz; z < oz + s; z++) {
                boolean perimeter = x == ox || x == ox + s - 1 || z == oz || z == oz + s - 1;
                if (!perimeter) continue;
                if (x == gx && z == gz) {
                    Gate gate = (Gate) Material.OAK_FENCE_GATE.createBlockData();
                    gate.setFacing(gateSide <= 1 ? BlockFace.SOUTH : BlockFace.EAST);
                    chunkData.setBlock(x, base, z, gate);
                    continue;
                }
                // Explicit connections: ChunkData placement never triggers neighbor updates
                Fence fence = (Fence) Material.OAK_FENCE.createBlockData();
                if (onPerimeter(x - 1, z, ox, oz, s)) fence.setFace(BlockFace.WEST, true);
                if (onPerimeter(x + 1, z, ox, oz, s)) fence.setFace(BlockFace.EAST, true);
                if (onPerimeter(x, z - 1, ox, oz, s)) fence.setFace(BlockFace.NORTH, true);
                if (onPerimeter(x, z + 1, ox, oz, s)) fence.setFace(BlockFace.SOUTH, true);
                chunkData.setBlock(x, base, z, fence);
            }
        }

        // Torch on a corner post
        chunkData.setBlock(ox, base + 1, oz, Material.TORCH);
    }

    private static boolean onPerimeter(int x, int z, int ox, int oz, int s) {
        if (x < ox || x >= ox + s || z < oz || z >= oz + s) return false;
        return x == ox || x == ox + s - 1 || z == oz || z == oz + s - 1;
    }

    // ---------------------------------------------------------------
    //  Ground decoration (Maze pattern: stateless per-position hash)
    // ---------------------------------------------------------------

    private void placeGroundDecoration(ChunkData chunkData, int x, int z,
                                       int worldX, int worldZ, long seed) {
        if (decorationMaterials.length == 0 || totalDecorationWeight <= 0) return;
        long h = blockHash(worldX, worldZ, seed, 0xF10E);
        if (Math.floorMod(h, 100) < decorationEmptyChance) return;
        int roll = (int) Math.floorMod(h >>> 16, totalDecorationWeight);
        Material mat = decorationMaterials[0];
        int cumulative = 0;
        for (int i = 0; i < decorationMaterials.length; i++) {
            cumulative += decorationWeights[i];
            if (roll < cumulative) {
                mat = decorationMaterials[i];
                break;
            }
        }
        chunkData.setBlock(x, GROUND_Y + 1, z, mat);
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

    @Override
    public int getSpawnY() {
        return GROUND_Y + 1;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, GROUND_Y + 1, 0.5);
    }
}
