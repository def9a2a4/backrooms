package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Level 7 — "The Corrupted Chunk" / error_422
 * Every biome's blocks mixed together. Netherrack next to grass next to end stone
 * next to coral next to ice. Structure fragments placed partially, rotated, overlapping.
 * The world generator has lost its mind.
 */
public class Level7ChunkGenerator extends ChunkGenerator {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 192;

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

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // Chaotic terrain with multiple overlapping noise functions
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Multiple terrain height sources fighting each other
                double h1 = SimplexNoise.noise2(seed, worldX * 0.01, worldZ * 0.01) * 60;
                double h2 = SimplexNoise.noise2(seed + 1, worldX * 0.05, worldZ * 0.05) * 20;
                double h3 = SimplexNoise.noise2(seed + 2, worldX * 0.02, worldZ * 0.003) * 40; // Stretched in Z
                double h4 = SimplexNoise.noise2(seed + 3, worldX * 0.003, worldZ * 0.02) * 40; // Stretched in X

                double height = 64 + h1 + h2 + Math.max(h3, h4);
                int terrainHeight = Math.max(MIN_Y + 1, Math.min((int) height, MAX_Y - 1));

                // Material selection: different noise layer picks from different palettes
                double matNoise = SimplexNoise.noise2(seed + 10, worldX * 0.08, worldZ * 0.08);
                double matDetail = SimplexNoise.noise2(seed + 11, worldX * 0.3, worldZ * 0.3);

                for (int y = MIN_Y; y < terrainHeight; y++) {
                    // Material changes with height AND position (total chaos)
                    double yFactor = SimplexNoise.noise2(seed + 12, worldX * 0.1 + y * 0.05, worldZ * 0.1);
                    int matIndex = Math.abs((int) ((matNoise + matDetail + yFactor) * 500)) % CHAOS_BLOCKS.length;
                    chunkData.setBlock(x, y, z, CHAOS_BLOCKS[matIndex]);
                }

                // Random floating blocks above terrain
                if (chunkRng.nextDouble() < 0.03) {
                    int floatY = terrainHeight + 5 + chunkRng.nextInt(20);
                    if (floatY < MAX_Y) {
                        Material floatMat = CHAOS_BLOCKS[chunkRng.nextInt(CHAOS_BLOCKS.length)];
                        chunkData.setBlock(x, floatY, z, floatMat);
                    }
                }
            }
        }

        // Structure fragments: partial buildings placed randomly
        if (chunkRng.nextDouble() < 0.4) {
            placeStructureFragment(chunkData, chunkRng);
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

    private void placeStructureFragment(ChunkData chunkData, Random rng) {
        int sx = rng.nextInt(12);
        int sz = rng.nextInt(12);
        int sizeX = 3 + rng.nextInt(5);
        int sizeZ = 3 + rng.nextInt(5);
        int height = 3 + rng.nextInt(4);
        Material wallMat = STRUCTURE_BLOCKS[rng.nextInt(STRUCTURE_BLOCKS.length)];

        // Find ground level
        int groundY = 64;
        for (int y = MAX_Y - 1; y > MIN_Y; y--) {
            if (chunkData.getType(sx + sizeX / 2, y, sz + sizeZ / 2) != Material.AIR) {
                groundY = y + 1;
                break;
            }
        }

        // Place partial walls (some sides missing)
        boolean[] walls = {rng.nextBoolean(), rng.nextBoolean(), rng.nextBoolean(), rng.nextBoolean()};
        for (int dy = 0; dy < height; dy++) {
            int y = groundY + dy;
            if (y >= MAX_Y) break;
            // North wall
            if (walls[0]) for (int dx = 0; dx < sizeX && sx + dx < 16; dx++)
                chunkData.setBlock(sx + dx, y, sz, wallMat);
            // South wall
            if (walls[1]) for (int dx = 0; dx < sizeX && sx + dx < 16; dx++) {
                if (sz + sizeZ - 1 < 16) chunkData.setBlock(sx + dx, y, sz + sizeZ - 1, wallMat);
            }
            // West wall
            if (walls[2]) for (int dz = 0; dz < sizeZ && sz + dz < 16; dz++)
                chunkData.setBlock(sx, y, sz + dz, wallMat);
            // East wall
            if (walls[3]) for (int dz = 0; dz < sizeZ && sz + dz < 16; dz++) {
                if (sx + sizeX - 1 < 16) chunkData.setBlock(sx + sizeX - 1, y, sz + dz, wallMat);
            }
        }
    }

    private void placeCalmRoom(ChunkData chunkData) {
        // Clear a 7x7 room at Y=64
        int baseY = 64;
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

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, 100, 8.5);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return Biome.THE_VOID;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.THE_VOID);
            }
        };
    }
}
