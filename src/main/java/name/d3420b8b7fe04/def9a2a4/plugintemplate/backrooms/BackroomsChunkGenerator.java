package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

public class BackroomsChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_MIN_Y = 0;
    private static final int FLOOR_MAX_Y = 20;   // exclusive: 0-19
    private static final int AIR_MIN_Y = 20;      // walkable floor surface
    private static final int AIR_MAX_Y = 24;      // exclusive: 20-23
    private static final int CEILING_MIN_Y = 24;
    private static final int CEILING_MAX_Y = 44;  // exclusive: 24-43

    private static final int CELL_SIZE = 4;
    private static final int CELLS_PER_AXIS = 16 / CELL_SIZE; // 4
    private static final int LIGHT_SPACING = 4;

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Floor and ceiling
        chunkData.setRegion(0, FLOOR_MIN_Y, 0, 16, FLOOR_MAX_Y, 16, Material.YELLOW_TERRACOTTA);
        chunkData.setRegion(0, CEILING_MIN_Y, 0, 16, CEILING_MAX_Y, 16, Material.YELLOW_TERRACOTTA);

        // Walls and pillars
        Random chunkRng = new Random(worldInfo.getSeed() ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        for (int cx = 0; cx < CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < CELLS_PER_AXIS; cz++) {
                int baseX = cx * CELL_SIZE;
                int baseZ = cz * CELL_SIZE;
                double roll = chunkRng.nextDouble();

                if (roll < 0.30) {
                    // empty
                } else if (roll < 0.50) {
                    placePillar(chunkData, chunkRng, baseX, baseZ);
                } else if (roll < 0.70) {
                    placeWall(chunkData, baseX, baseZ, chunkRng.nextInt(4), AIR_MIN_Y, AIR_MAX_Y);
                } else if (roll < 0.85) {
                    placeWall(chunkData, baseX, baseZ, chunkRng.nextInt(4), AIR_MIN_Y, AIR_MIN_Y + 2);
                } else {
                    placeLCorner(chunkData, baseX, baseZ, chunkRng.nextInt(4));
                }
            }
        }

        // Ceiling lights
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
                case 0 -> { x = baseX + i; z = baseZ; }           // north
                case 1 -> { x = baseX + i; z = baseZ + 3; }       // south
                case 2 -> { x = baseX; z = baseZ + i; }           // west
                default -> { x = baseX + 3; z = baseZ + i; }      // east
            }
            for (int y = yMin; y < yMax; y++) {
                chunkData.setBlock(x, y, z, Material.YELLOW_TERRACOTTA);
            }
        }
    }

    private void placeLCorner(ChunkData chunkData, int baseX, int baseZ, int corner) {
        // Two perpendicular walls meeting at a corner
        switch (corner) {
            case 0 -> { // NW
                placeWall(chunkData, baseX, baseZ, 0, AIR_MIN_Y, AIR_MAX_Y); // north edge
                placeWall(chunkData, baseX, baseZ, 2, AIR_MIN_Y, AIR_MAX_Y); // west edge
            }
            case 1 -> { // NE
                placeWall(chunkData, baseX, baseZ, 0, AIR_MIN_Y, AIR_MAX_Y);
                placeWall(chunkData, baseX, baseZ, 3, AIR_MIN_Y, AIR_MAX_Y);
            }
            case 2 -> { // SW
                placeWall(chunkData, baseX, baseZ, 1, AIR_MIN_Y, AIR_MAX_Y);
                placeWall(chunkData, baseX, baseZ, 2, AIR_MIN_Y, AIR_MAX_Y);
            }
            default -> { // SE
                placeWall(chunkData, baseX, baseZ, 1, AIR_MIN_Y, AIR_MAX_Y);
                placeWall(chunkData, baseX, baseZ, 3, AIR_MIN_Y, AIR_MAX_Y);
            }
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

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
