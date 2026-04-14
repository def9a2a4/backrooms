package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.generator.WorldInfo;

import java.util.Arrays;
import java.util.Random;

/**
 * Level 7 — "The Corrupted Chunk" / error_422
 * Every biome's blocks mixed together. Netherrack next to grass next to end stone
 * next to coral next to ice. Structure fragments placed partially, rotated, overlapping.
 * The world generator has lost its mind.
 */
public class Level7ChunkGenerator extends BackroomsChunkGenerator {

    public Level7ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    private static final int MIN_Y = 0;
    private static final int MAX_Y = 128;

    // Every block type in the game (for truly random placement)
    private static final Material[] ALL_BLOCKS = Arrays.stream(Material.values())
            .filter(Material::isBlock)
            .toArray(Material[]::new);

    // Every possible block that could appear in the corruption
    private static final Material[] CHAOS_BLOCKS = {
            Material.GRASS_BLOCK, Material.DIRT, Material.STONE, Material.NETHERRACK,
            Material.END_STONE, Material.SAND, Material.RED_SAND, Material.GRAVEL,
            Material.SOUL_SAND, Material.SOUL_SOIL, Material.MYCELIUM, Material.PODZOL,
            Material.TERRACOTTA, Material.PRISMARINE, Material.DARK_PRISMARINE,
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.SNOW_BLOCK,
            Material.MAGMA_BLOCK, Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
            Material.DEEPSLATE, Material.CALCITE, Material.TUFF, Material.DRIPSTONE_BLOCK,
            Material.BASALT, Material.BLACKSTONE, Material.CRIMSON_NYLIUM,
            Material.WARPED_NYLIUM, Material.BUBBLE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK,
            Material.TUBE_CORAL_BLOCK, Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK,
            Material.MOSS_BLOCK, Material.MUD, Material.SCULK
    };

    // Structure fragment blocks
    private static final Material[] STRUCTURE_BLOCKS = {
            Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS,
            Material.NETHER_BRICKS, Material.PURPUR_BLOCK, Material.END_STONE_BRICKS,
            Material.POLISHED_BLACKSTONE_BRICKS, Material.DEEPSLATE_BRICKS
    };

    // Stair materials for staircases to nowhere
    private static final Material[] STAIR_BLOCKS = {
            Material.OAK_STAIRS, Material.COBBLESTONE_STAIRS, Material.STONE_BRICK_STAIRS,
            Material.NETHER_BRICK_STAIRS, Material.PURPUR_STAIRS, Material.QUARTZ_STAIRS,
            Material.DARK_OAK_STAIRS, Material.SPRUCE_STAIRS, Material.BIRCH_STAIRS,
            Material.SANDSTONE_STAIRS, Material.RED_SANDSTONE_STAIRS, Material.BLACKSTONE_STAIRS,
            Material.DEEPSLATE_BRICK_STAIRS, Material.PRISMARINE_STAIRS
    };

    // Redstone components for redstone messes
    private static final Material[] REDSTONE_COMPONENTS = {
            Material.REDSTONE_WIRE, Material.REPEATER, Material.COMPARATOR,
            Material.REDSTONE_TORCH, Material.REDSTONE_LAMP, Material.TARGET,
            Material.OBSERVER, Material.PISTON, Material.STICKY_PISTON,
            Material.LEVER, Material.STONE_BUTTON, Material.DAYLIGHT_DETECTOR
    };

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // --- Pass 1: 3D density field ---
        boolean[][][] solid = new boolean[16][MAX_Y][16];

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Domain warp XZ (low freq, computed per-column)
                double warpX = SimplexNoise.noise3(seed + 100, worldX * 0.01, 0, worldZ * 0.01) * 12.0;
                double warpZ = SimplexNoise.noise3(seed + 102, worldX * 0.01, 0, worldZ * 0.01) * 12.0;

                for (int y = MIN_Y; y < MAX_Y; y++) {
                    if (y == 0) { solid[x][y][z] = true; continue; }

                    double warpY = SimplexNoise.noise3(seed + 101, worldX * 0.01, y * 0.01, worldZ * 0.01) * 8.0;
                    double wx = worldX + warpX;
                    double wy = y + warpY;
                    double wz = worldZ + warpZ;

                    // Vertical bias: solid at bottom, air at top
                    double normalizedY = (double) (y - MIN_Y) / (MAX_Y - MIN_Y);
                    double verticalBias = 0.6 - 1.0 * normalizedY;

                    // 4 octaves with mismatched, anisotropic frequencies
                    double n1 = SimplexNoise.noise3(seed + 1, wx * 0.015, wy * 0.012, wz * 0.015) * 0.5;
                    double n2 = SimplexNoise.noise3(seed + 2, wx * 0.04,  wy * 0.02,  wz * 0.04)  * 0.3;
                    double n3 = SimplexNoise.noise3(seed + 3, wx * 0.003, wy * 0.07,  wz * 0.07)  * 0.2;
                    double n4 = SimplexNoise.noise3(seed + 4, wx * 0.12,  wy * 0.12,  wz * 0.12)  * 0.1;

                    // Spatially varying threshold for extra chaos
                    double threshMod = SimplexNoise.noise3(seed + 50, worldX * 0.008, y * 0.008, worldZ * 0.008) * 0.25;

                    double density = verticalBias + n1 + n2 + n3 + n4 - threshMod;
                    solid[x][y][z] = density > 0;
                }
            }
        }

        // --- Pass 2: material assignment ---
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                for (int y = MIN_Y; y < MAX_Y; y++) {
                    if (!solid[x][y][z]) continue;

                    // ~5% chance: completely random block with randomized block data
                    if (chunkRng.nextDouble() < 0.05) {
                        Material randMat = ALL_BLOCKS[chunkRng.nextInt(ALL_BLOCKS.length)];
                        try {
                            BlockData bd = Bukkit.createBlockData(randMat);
                            if (bd instanceof Directional dir) {
                                BlockFace[] faces = dir.getFaces().toArray(new BlockFace[0]);
                                if (faces.length > 0) {
                                    dir.setFacing(faces[chunkRng.nextInt(faces.length)]);
                                }
                            }
                            if (bd instanceof Waterlogged wl) {
                                wl.setWaterlogged(chunkRng.nextBoolean());
                            }
                            chunkData.setBlock(x, y, z, bd);
                        } catch (Exception e) {
                            chunkData.setBlock(x, y, z, randMat);
                        }
                        continue;
                    }

                    double matCoarse = SimplexNoise.noise3(seed + 10, worldX * 0.03, y * 0.03, worldZ * 0.03);
                    double matFine   = SimplexNoise.noise3(seed + 11, worldX * 0.15, y * 0.15, worldZ * 0.15);
                    double matValue = matCoarse * 0.7 + matFine * 0.3;
                    int matIndex = (int) ((matValue + 1.0) * 0.5 * CHAOS_BLOCKS.length);
                    matIndex = Math.max(0, Math.min(CHAOS_BLOCKS.length - 1, matIndex));
                    chunkData.setBlock(x, y, z, CHAOS_BLOCKS[matIndex]);
                }
            }
        }

        // Floating blocks scattered in air pockets
        for (int i = 0; i < 8; i++) {
            int fx = chunkRng.nextInt(16);
            int fz = chunkRng.nextInt(16);
            int fy = 40 + chunkRng.nextInt(80);
            if (fy < MAX_Y && !solid[fx][fy][fz]) {
                chunkData.setBlock(fx, fy, fz, CHAOS_BLOCKS[chunkRng.nextInt(CHAOS_BLOCKS.length)]);
            }
        }

        // Structure fragments: 1-3 random structures per chunk
        int structureCount = 1 + chunkRng.nextInt(3);
        for (int i = 0; i < structureCount; i++) {
            if (chunkRng.nextDouble() < 0.7) {
                placeRandomStructure(chunkData, chunkRng);
            }
        }

        // The Calm Room: at world origin chunk
        if (chunkX == 0 && chunkZ == 0) {
            placeCalmRoom(chunkData);
        }

        // Scattered light sources (mixed types)
        for (int i = 0; i < 4; i++) {
            int lx = chunkRng.nextInt(16);
            int lz = chunkRng.nextInt(16);
            // Find surface
            for (int y = MAX_Y - 1; y > MIN_Y; y--) {
                if (chunkData.getType(lx, y, lz) != Material.AIR) {
                    Material light = switch (chunkRng.nextInt(5)) {
                        case 0 -> Material.GLOWSTONE;
                        case 1 -> Material.SEA_LANTERN;
                        case 2 -> Material.SHROOMLIGHT;
                        case 3 -> Material.JACK_O_LANTERN;
                        default -> Material.REDSTONE_LAMP;
                    };
                    chunkData.setBlock(lx, y + 1, lz, light);
                    break;
                }
            }
        }
    }

    private void placeRandomStructure(ChunkData chunkData, Random rng) {
        switch (rng.nextInt(7)) {
            case 0 -> placePartialWalls(chunkData, rng);
            case 1 -> placeStaircaseToNowhere(chunkData, rng);
            case 2 -> placePillar(chunkData, rng);
            case 3 -> placeFloatingSlab(chunkData, rng);
            case 4 -> placeBrokenPortalFrame(chunkData, rng);
            case 5 -> placeRedstoneMess(chunkData, rng);
            case 6 -> placeArch(chunkData, rng);
        }
    }

    private int findSurface(ChunkData chunkData, int x, int z) {
        for (int y = MAX_Y - 1; y > MIN_Y; y--) {
            if (chunkData.getType(x, y, z) != Material.AIR) return y + 1;
        }
        return 48;
    }

    private void placePartialWalls(ChunkData chunkData, Random rng) {
        int sx = rng.nextInt(12);
        int sz = rng.nextInt(12);
        int sizeX = 3 + rng.nextInt(5);
        int sizeZ = 3 + rng.nextInt(5);
        int height = 3 + rng.nextInt(4);
        Material wallMat = STRUCTURE_BLOCKS[rng.nextInt(STRUCTURE_BLOCKS.length)];
        int groundY = findSurface(chunkData, sx + sizeX / 2, sz + sizeZ / 2);

        boolean[] walls = {rng.nextBoolean(), rng.nextBoolean(), rng.nextBoolean(), rng.nextBoolean()};
        for (int dy = 0; dy < height; dy++) {
            int y = groundY + dy;
            if (y >= MAX_Y) break;
            if (walls[0]) for (int dx = 0; dx < sizeX && sx + dx < 16; dx++)
                chunkData.setBlock(sx + dx, y, sz, wallMat);
            if (walls[1]) for (int dx = 0; dx < sizeX && sx + dx < 16; dx++) {
                if (sz + sizeZ - 1 < 16) chunkData.setBlock(sx + dx, y, sz + sizeZ - 1, wallMat);
            }
            if (walls[2]) for (int dz = 0; dz < sizeZ && sz + dz < 16; dz++)
                chunkData.setBlock(sx, y, sz + dz, wallMat);
            if (walls[3]) for (int dz = 0; dz < sizeZ && sz + dz < 16; dz++) {
                if (sx + sizeX - 1 < 16) chunkData.setBlock(sx + sizeX - 1, y, sz + dz, wallMat);
            }
        }
    }

    private void placeStaircaseToNowhere(ChunkData chunkData, Random rng) {
        int sx = rng.nextInt(14);
        int sz = rng.nextInt(14);
        int groundY = findSurface(chunkData, sx, sz);
        int steps = 3 + rng.nextInt(4);
        Material stairMat = STAIR_BLOCKS[rng.nextInt(STAIR_BLOCKS.length)];
        // Pick a random direction: 0=+x, 1=-x, 2=+z, 3=-z
        int dir = rng.nextInt(4);
        BlockFace facing = switch (dir) {
            case 0 -> BlockFace.EAST;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.SOUTH;
            default -> BlockFace.NORTH;
        };

        for (int i = 0; i < steps; i++) {
            int bx = sx + (dir == 0 ? i : dir == 1 ? -i : 0);
            int bz = sz + (dir == 2 ? i : dir == 3 ? -i : 0);
            int by = groundY + i;
            if (bx < 0 || bx >= 16 || bz < 0 || bz >= 16 || by >= MAX_Y) break;
            try {
                BlockData bd = Bukkit.createBlockData(stairMat);
                if (bd instanceof Directional d) d.setFacing(facing);
                chunkData.setBlock(bx, by, bz, bd);
            } catch (Exception e) {
                chunkData.setBlock(bx, by, bz, stairMat);
            }
        }
    }

    private void placePillar(ChunkData chunkData, Random rng) {
        int px = rng.nextInt(16);
        int pz = rng.nextInt(16);
        int height = 4 + rng.nextInt(9);
        // Sometimes start mid-air, sometimes from a surface
        int startY = rng.nextBoolean() ? findSurface(chunkData, px, pz) : 20 + rng.nextInt(80);
        Material mat = rng.nextBoolean()
                ? STRUCTURE_BLOCKS[rng.nextInt(STRUCTURE_BLOCKS.length)]
                : ALL_BLOCKS[rng.nextInt(ALL_BLOCKS.length)];
        for (int dy = 0; dy < height; dy++) {
            int y = startY + dy;
            if (y >= MAX_Y || y < MIN_Y) break;
            chunkData.setBlock(px, y, pz, mat);
        }
    }

    private void placeFloatingSlab(ChunkData chunkData, Random rng) {
        int sx = rng.nextInt(12);
        int sz = rng.nextInt(12);
        int sizeX = 3 + rng.nextInt(4);
        int sizeZ = 3 + rng.nextInt(4);
        int slabY = 20 + rng.nextInt(90);
        Material mat = rng.nextBoolean()
                ? STRUCTURE_BLOCKS[rng.nextInt(STRUCTURE_BLOCKS.length)]
                : CHAOS_BLOCKS[rng.nextInt(CHAOS_BLOCKS.length)];
        for (int dx = 0; dx < sizeX && sx + dx < 16; dx++) {
            for (int dz = 0; dz < sizeZ && sz + dz < 16; dz++) {
                if (slabY < MAX_Y) chunkData.setBlock(sx + dx, slabY, sz + dz, mat);
            }
        }
    }

    private void placeBrokenPortalFrame(ChunkData chunkData, Random rng) {
        int sx = rng.nextInt(12);
        int sz = rng.nextInt(14);
        int groundY = findSurface(chunkData, sx + 2, sz);
        // 4 wide, 5 tall portal frame — then randomly delete some blocks
        int[][] frame = {
                {0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4},  // left pillar
                {3, 0}, {3, 1}, {3, 2}, {3, 3}, {3, 4},  // right pillar
                {1, 4}, {2, 4},                            // top bar
        };
        for (int[] pos : frame) {
            if (rng.nextDouble() < 0.3) continue; // 30% chance each block is missing
            int bx = sx + pos[0];
            int by = groundY + pos[1];
            if (bx >= 16 || by >= MAX_Y) continue;
            chunkData.setBlock(bx, by, sz, Material.OBSIDIAN);
        }
    }

    private void placeRedstoneMess(ChunkData chunkData, Random rng) {
        int sx = rng.nextInt(12);
        int sz = rng.nextInt(12);
        int groundY = findSurface(chunkData, sx + 2, sz + 2);
        int sizeX = 3 + rng.nextInt(3);
        int sizeZ = 3 + rng.nextInt(3);
        // Place a solid floor first so redstone can sit on it
        Material floorMat = STRUCTURE_BLOCKS[rng.nextInt(STRUCTURE_BLOCKS.length)];
        for (int dx = 0; dx < sizeX && sx + dx < 16; dx++) {
            for (int dz = 0; dz < sizeZ && sz + dz < 16; dz++) {
                if (groundY - 1 >= MIN_Y && groundY - 1 < MAX_Y)
                    chunkData.setBlock(sx + dx, groundY - 1, sz + dz, floorMat);
                if (groundY < MAX_Y) {
                    Material comp = REDSTONE_COMPONENTS[rng.nextInt(REDSTONE_COMPONENTS.length)];
                    try {
                        BlockData bd = Bukkit.createBlockData(comp);
                        if (bd instanceof Directional d) {
                            BlockFace[] faces = d.getFaces().toArray(new BlockFace[0]);
                            if (faces.length > 0) d.setFacing(faces[rng.nextInt(faces.length)]);
                        }
                        chunkData.setBlock(sx + dx, groundY, sz + dz, bd);
                    } catch (Exception e) {
                        chunkData.setBlock(sx + dx, groundY, sz + dz, comp);
                    }
                }
            }
        }
    }

    private void placeArch(ChunkData chunkData, Random rng) {
        int sx = rng.nextInt(10);
        int sz = rng.nextInt(14);
        int groundY = findSurface(chunkData, sx + 3, sz);
        int pillarHeight = 3 + rng.nextInt(4);
        int span = 4 + rng.nextInt(3);
        Material mat = STRUCTURE_BLOCKS[rng.nextInt(STRUCTURE_BLOCKS.length)];
        // Left pillar
        for (int dy = 0; dy < pillarHeight; dy++) {
            int y = groundY + dy;
            if (y < MAX_Y && sx < 16) chunkData.setBlock(sx, y, sz, mat);
        }
        // Right pillar
        for (int dy = 0; dy < pillarHeight; dy++) {
            int y = groundY + dy;
            if (y < MAX_Y && sx + span < 16) chunkData.setBlock(sx + span, y, sz, mat);
        }
        // Bridge across the top
        int bridgeY = groundY + pillarHeight - 1;
        if (bridgeY < MAX_Y) {
            for (int dx = 0; dx <= span && sx + dx < 16; dx++) {
                chunkData.setBlock(sx + dx, bridgeY, sz, mat);
            }
        }
    }

    private void placeCalmRoom(ChunkData chunkData) {
        // Clear a 7x7 room at Y=64
        int baseY = 48;
        // Floor
        for (int x = 4; x <= 10; x++) {
            for (int z = 4; z <= 10; z++) {
                chunkData.setBlock(x, baseY, z, Material.SMOOTH_STONE);
                for (int y = baseY + 1; y <= baseY + 5; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }
                chunkData.setBlock(x, baseY + 5, z, Material.SMOOTH_STONE);
            }
        }
        // Walls
        for (int y = baseY; y <= baseY + 5; y++) {
            for (int x = 4; x <= 10; x++) {
                chunkData.setBlock(x, y, 4, Material.SMOOTH_STONE);
                chunkData.setBlock(x, y, 10, Material.SMOOTH_STONE);
            }
            for (int z = 4; z <= 10; z++) {
                chunkData.setBlock(4, y, z, Material.SMOOTH_STONE);
                chunkData.setBlock(10, y, z, Material.SMOOTH_STONE);
            }
        }
        // Interior air
        for (int x = 5; x <= 9; x++) {
            for (int z = 5; z <= 9; z++) {
                for (int y = baseY + 1; y <= baseY + 4; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }
            }
        }
        // 4 torches in corners
        chunkData.setBlock(5, baseY + 1, 5, Material.TORCH);
        chunkData.setBlock(9, baseY + 1, 5, Material.TORCH);
        chunkData.setBlock(5, baseY + 1, 9, Material.TORCH);
        chunkData.setBlock(9, baseY + 1, 9, Material.TORCH);
        // Chest in center
        chunkData.setBlock(7, baseY + 1, 7, Material.CHEST);
        // Doorway
        chunkData.setBlock(7, baseY + 1, 4, Material.AIR);
        chunkData.setBlock(7, baseY + 2, 4, Material.AIR);
        chunkData.setBlock(7, baseY + 3, 4, Material.AIR);
        // Bedrock floor with one hole beneath
        for (int x = 5; x <= 9; x++) {
            for (int z = 5; z <= 9; z++) {
                chunkData.setBlock(x, baseY, z, Material.SMOOTH_STONE);
            }
        }
        chunkData.setBlock(7, baseY, 7, Material.BEDROCK);
        // The hole beneath (one block gap in bedrock leading to void)
        chunkData.setBlock(7, baseY - 1, 7, Material.AIR);
        chunkData.setBlock(7, baseY - 2, 7, Material.AIR);
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, 64, 8.5);
    }
}
