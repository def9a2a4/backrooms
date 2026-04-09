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

public class Level0ChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_MIN_Y = 0;
    private static final int FLOOR_MAX_Y = 20;
    public static final int AIR_MIN_Y = 20;
    private static final int AIR_MAX_Y = 24;
    public static final int CEILING_MIN_Y = 24;
    private static final int CEILING_MAX_Y = 44;

    private static final int CELL_SIZE = 4;
    private static final int CELLS_PER_AXIS = 16 / CELL_SIZE;
    public static final int LIGHT_SPACING = 4;

    private static final double REGION_SCALE = 1.0 / 64.0;

    private static final int REGION_EMPTY = 0;
    private static final int REGION_WALLS = 1;
    private static final int REGION_PILLARS = 2;

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        chunkData.setRegion(0, FLOOR_MIN_Y, 0, 16, FLOOR_MAX_Y, 16, Material.YELLOW_TERRACOTTA);
        chunkData.setRegion(0, CEILING_MIN_Y, 0, 16, CEILING_MAX_Y, 16, Material.YELLOW_TERRACOTTA);

        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        for (int cx = 0; cx < CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < CELLS_PER_AXIS; cz++) {
                int baseX = cx * CELL_SIZE;
                int baseZ = cz * CELL_SIZE;

                double worldCenterX = (chunkX * 16 + baseX + CELL_SIZE / 2.0) * REGION_SCALE;
                double worldCenterZ = (chunkZ * 16 + baseZ + CELL_SIZE / 2.0) * REGION_SCALE;
                double noise = SimplexNoise.noise2(seed, worldCenterX, worldCenterZ);

                int region = classifyRegion(noise);
                placeStructure(chunkData, chunkRng, baseX, baseZ, region);
            }
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % LIGHT_SPACING == 0 && worldZ % LIGHT_SPACING == 0) {
                    chunkData.setBlock(x, CEILING_MIN_Y, z, Material.OCHRE_FROGLIGHT);
                }
            }
        }
    }

    private int classifyRegion(double noise) {
        if (noise < -0.3) return REGION_PILLARS;
        if (noise > 0.3) return REGION_EMPTY;
        return REGION_WALLS;
    }

    private void placeStructure(ChunkData chunkData, Random rng, int baseX, int baseZ, int region) {
        double roll = rng.nextDouble();
        switch (region) {
            case REGION_EMPTY -> placeEmpty(chunkData, rng, baseX, baseZ, roll);
            case REGION_WALLS -> placeWallRegion(chunkData, rng, baseX, baseZ, roll);
            case REGION_PILLARS -> placePillarRegion(chunkData, rng, baseX, baseZ, roll);
        }
    }

    private void placeEmpty(ChunkData chunkData, Random rng, int baseX, int baseZ, double roll) {
        if (roll < 0.60) {
            rng.nextInt(4);
        } else if (roll < 0.75) {
            placePillar(chunkData, rng, baseX, baseZ);
        } else if (roll < 0.90) {
            placeRandomWall(chunkData, rng, baseX, baseZ);
        } else {
            placeLCorner(chunkData, baseX, baseZ, rng.nextInt(4));
        }
    }

    private void placeWallRegion(ChunkData chunkData, Random rng, int baseX, int baseZ, double roll) {
        if (roll < 0.10) {
            rng.nextInt(4);
        } else if (roll < 0.20) {
            placePillar(chunkData, rng, baseX, baseZ);
        } else if (roll < 0.55) {
            placeRandomWall(chunkData, rng, baseX, baseZ);
        } else if (roll < 0.75) {
            placeCeilingWall(chunkData, rng, baseX, baseZ);
        } else {
            placeLCorner(chunkData, baseX, baseZ, rng.nextInt(4));
        }
    }

    private void placePillarRegion(ChunkData chunkData, Random rng, int baseX, int baseZ, double roll) {
        if (roll < 0.15) {
            rng.nextInt(4);
        } else if (roll < 0.70) {
            placePillar(chunkData, rng, baseX, baseZ);
        } else if (roll < 0.85) {
            placeRandomWall(chunkData, rng, baseX, baseZ);
        } else {
            placeLCorner(chunkData, baseX, baseZ, rng.nextInt(4));
        }
    }

    private void placeRandomWall(ChunkData chunkData, Random rng, int baseX, int baseZ) {
        int dir = rng.nextInt(4);
        double heightRoll = rng.nextDouble();
        if (heightRoll < 0.35) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MAX_Y);
        } else if (heightRoll < 0.65) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MIN_Y + 3);
        } else {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MIN_Y + 1);
        }
    }

    private void placeCeilingWall(ChunkData chunkData, Random rng, int baseX, int baseZ) {
        int dir = rng.nextInt(4);
        double heightRoll = rng.nextDouble();
        if (heightRoll < 0.30) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MAX_Y);
        } else if (heightRoll < 0.70) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MAX_Y - 3, AIR_MAX_Y);
        } else {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MAX_Y - 1, AIR_MAX_Y);
        }
    }

    private void placePillar(ChunkData chunkData, Random rng, int baseX, int baseZ) {
        int px = baseX + 1 + rng.nextInt(2);
        int pz = baseZ + 1 + rng.nextInt(2);
        for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
            chunkData.setBlock(px, y, pz, Material.YELLOW_TERRACOTTA);
        }
    }

    private void placeWall(ChunkData chunkData, int baseX, int baseZ, int direction, int yMin, int yMax) {
        for (int i = 0; i < CELL_SIZE; i++) {
            int x, z;
            switch (direction) {
                case 0 -> { x = baseX + i; z = baseZ; }
                case 1 -> { x = baseX + i; z = baseZ + 3; }
                case 2 -> { x = baseX; z = baseZ + i; }
                default -> { x = baseX + 3; z = baseZ + i; }
            }
            for (int y = yMin; y < yMax; y++) {
                chunkData.setBlock(x, y, z, Material.YELLOW_TERRACOTTA);
            }
        }
    }

    private void placeLCorner(ChunkData chunkData, int baseX, int baseZ, int corner) {
        switch (corner) {
            case 0 -> {
                placeWall(chunkData, baseX, baseZ, 0, AIR_MIN_Y, AIR_MAX_Y);
                placeWall(chunkData, baseX, baseZ, 2, AIR_MIN_Y, AIR_MAX_Y);
            }
            case 1 -> {
                placeWall(chunkData, baseX, baseZ, 0, AIR_MIN_Y, AIR_MAX_Y);
                placeWall(chunkData, baseX, baseZ, 3, AIR_MIN_Y, AIR_MAX_Y);
            }
            case 2 -> {
                placeWall(chunkData, baseX, baseZ, 1, AIR_MIN_Y, AIR_MAX_Y);
                placeWall(chunkData, baseX, baseZ, 2, AIR_MIN_Y, AIR_MAX_Y);
            }
            default -> {
                placeWall(chunkData, baseX, baseZ, 1, AIR_MIN_Y, AIR_MAX_Y);
                placeWall(chunkData, baseX, baseZ, 3, AIR_MIN_Y, AIR_MAX_Y);
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
