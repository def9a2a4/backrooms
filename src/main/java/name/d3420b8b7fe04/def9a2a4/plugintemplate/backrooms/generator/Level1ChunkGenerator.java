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
 * Level 1 — "The Habitable Zone"
 * Massive concrete/stone warehouse-like spaces. Very open compared to L0.
 * Smooth stone floors, concrete pillars, occasional puddles, dim lighting.
 * Think: parking garage at 3 AM, industrial basement, liminal warehouse.
 */
public class Level1ChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 10;
    private static final int AIR_MIN_Y = 10;
    private static final int AIR_MAX_Y = 18;
    private static final int CEILING_MIN_Y = 18;
    private static final int CEILING_MAX_Y = 28;

    private static final int CELL_SIZE = 8;
    private static final int CELLS_PER_AXIS = 16 / CELL_SIZE;

    private static final double REGION_SCALE = 1.0 / 48.0;
    private static final double DETAIL_SCALE = 1.0 / 16.0;

    private static final Material[] FLOOR_MATERIALS = {
            Material.SMOOTH_STONE, Material.SMOOTH_STONE, Material.SMOOTH_STONE,
            Material.STONE, Material.STONE,
            Material.POLISHED_ANDESITE, Material.ANDESITE
    };

    private static final Material[] PILLAR_MATERIALS = {
            Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.POLISHED_ANDESITE
    };

    private static final Material[] WALL_MATERIALS = {
            Material.SMOOTH_STONE, Material.STONE, Material.STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.ANDESITE
    };

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // Floor: varied stone types
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double floorNoise = SimplexNoise.noise2(seed + 1, (chunkX * 16 + x) * 0.1, (chunkZ * 16 + z) * 0.1);
                int matIndex = Math.abs((int) (floorNoise * 1000)) % FLOOR_MATERIALS.length;
                Material floorMat = FLOOR_MATERIALS[matIndex];

                // Sub-floor fill
                chunkData.setRegion(x, FLOOR_Y, z, x + 1, FLOOR_HEIGHT, z + 1, Material.STONE);
                // Surface
                chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, floorMat);

                // Occasional puddles
                double puddleNoise = SimplexNoise.noise2(seed + 2, (chunkX * 16 + x) * 0.15, (chunkZ * 16 + z) * 0.15);
                if (puddleNoise > 0.55) {
                    chunkData.setBlock(x, AIR_MIN_Y, z, Material.WATER);
                }
            }
        }

        // Ceiling: concrete
        chunkData.setRegion(0, CEILING_MIN_Y, 0, 16, CEILING_MAX_Y, 16, Material.GRAY_CONCRETE);

        // Structural elements per cell
        for (int cx = 0; cx < CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < CELLS_PER_AXIS; cz++) {
                int baseX = cx * CELL_SIZE;
                int baseZ = cz * CELL_SIZE;

                double worldCenterX = (chunkX * 16 + baseX + CELL_SIZE / 2.0) * REGION_SCALE;
                double worldCenterZ = (chunkZ * 16 + baseZ + CELL_SIZE / 2.0) * REGION_SCALE;
                double noise = SimplexNoise.noise2(seed, worldCenterX, worldCenterZ);
                double detail = SimplexNoise.noise2(seed + 3, worldCenterX * 3, worldCenterZ * 3);

                placeStructure(chunkData, chunkRng, baseX, baseZ, noise, detail);
            }
        }

        // Sparse dim lighting: soul lanterns on ceiling, widely spaced
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % 8 == 0 && worldZ % 8 == 0) {
                    // Lantern hangs from ceiling
                    chunkData.setBlock(x, CEILING_MIN_Y - 1, z, Material.IRON_CHAIN);
                    chunkData.setBlock(x, CEILING_MIN_Y - 2, z, Material.SOUL_LANTERN);
                }
            }
        }
    }

    private void placeStructure(ChunkData chunkData, Random rng, int baseX, int baseZ, double noise, double detail) {
        if (noise < -0.2) {
            // Open area — maybe a single pillar
            if (detail > 0.3) {
                placeThickPillar(chunkData, rng, baseX + 3, baseZ + 3);
            }
        } else if (noise < 0.15) {
            // Pillar zone — parking garage columns
            placeThickPillar(chunkData, rng, baseX + 2, baseZ + 2);
            if (detail > 0.2) {
                placeThickPillar(chunkData, rng, baseX + 5, baseZ + 5);
            }
        } else if (noise < 0.4) {
            // Low wall / barrier
            placeLowWall(chunkData, rng, baseX, baseZ);
        } else {
            // Solid wall section — creates rooms
            placeWallSection(chunkData, rng, baseX, baseZ);
        }
    }

    private void placeThickPillar(ChunkData chunkData, Random rng, int x, int z) {
        if (x >= 16 || z >= 16) return;
        Material mat = PILLAR_MATERIALS[rng.nextInt(PILLAR_MATERIALS.length)];
        // 2x2 pillar
        for (int dx = 0; dx < 2 && x + dx < 16; dx++) {
            for (int dz = 0; dz < 2 && z + dz < 16; dz++) {
                for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                    chunkData.setBlock(x + dx, y, z + dz, mat);
                }
            }
        }
    }

    private void placeLowWall(ChunkData chunkData, Random rng, int baseX, int baseZ) {
        Material mat = WALL_MATERIALS[rng.nextInt(WALL_MATERIALS.length)];
        int dir = rng.nextInt(2); // 0=X, 1=Z
        int height = AIR_MIN_Y + 2 + rng.nextInt(2); // 2-3 blocks tall
        for (int i = 0; i < CELL_SIZE && baseX + (dir == 0 ? i : 0) < 16 && baseZ + (dir == 1 ? i : 0) < 16; i++) {
            int x = baseX + (dir == 0 ? i : rng.nextInt(2));
            int z = baseZ + (dir == 1 ? i : rng.nextInt(2));
            if (x < 16 && z < 16) {
                for (int y = AIR_MIN_Y; y < height; y++) {
                    chunkData.setBlock(x, y, z, mat);
                }
            }
        }
    }

    private void placeWallSection(ChunkData chunkData, Random rng, int baseX, int baseZ) {
        Material mat = WALL_MATERIALS[rng.nextInt(WALL_MATERIALS.length)];
        int dir = rng.nextInt(4);
        for (int i = 0; i < CELL_SIZE; i++) {
            int x, z;
            switch (dir) {
                case 0 -> { x = baseX + i; z = baseZ; }
                case 1 -> { x = baseX + i; z = baseZ + CELL_SIZE - 1; }
                case 2 -> { x = baseX; z = baseZ + i; }
                default -> { x = baseX + CELL_SIZE - 1; z = baseZ + i; }
            }
            if (x < 16 && z < 16) {
                for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                    chunkData.setBlock(x, y, z, mat);
                }
            }
        }
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
        return new Location(world, 8.5, AIR_MIN_Y, 8.5);
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
