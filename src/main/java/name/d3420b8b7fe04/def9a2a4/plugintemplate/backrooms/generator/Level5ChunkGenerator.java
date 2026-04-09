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
 * Level 5 — "Disc 11" / The Tunnels
 * Narrow 2-wide tunnels through solid stone. Recursive backtracker maze.
 * Stone, cobblestone, mossy cobblestone. No rooms larger than 4x4.
 * Dead-end jukebox rooms with bone items on the floor.
 */
public class Level5ChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_Y = 0;
    private static final int SOLID_HEIGHT = 40;
    private static final int AIR_MIN_Y = 10;
    private static final int AIR_MAX_Y = 13; // 3 blocks tall — claustrophobic
    private static final int TUNNEL_WIDTH = 2;

    // Maze uses 4-block cells: 2 wall + 2 passage
    private static final int MAZE_CELL = 4;
    private static final int MAZE_CELLS_PER_AXIS = 16 / MAZE_CELL; // 4 cells per chunk axis

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // Fill everything solid with uniform stone
        chunkData.setRegion(0, FLOOR_Y, 0, 16, SOLID_HEIGHT, 16, Material.STONE);

        // Carve maze tunnels using noise-based connectivity
        // Each 4x4 cell can connect N, S, E, W based on noise
        for (int cx = 0; cx < MAZE_CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < MAZE_CELLS_PER_AXIS; cz++) {
                int cellWorldX = chunkX * 16 + cx * MAZE_CELL;
                int cellWorldZ = chunkZ * 16 + cz * MAZE_CELL;

                // Always carve the cell center (2x2 room)
                carveRect(chunkData, cx * MAZE_CELL + 1, cz * MAZE_CELL + 1, TUNNEL_WIDTH, TUNNEL_WIDTH);

                // Determine connections using noise (deterministic across chunks)
                double noiseN = SimplexNoise.noise2(seed + 20, cellWorldX * 0.25, (cellWorldZ - 2) * 0.25);
                double noiseS = SimplexNoise.noise2(seed + 20, cellWorldX * 0.25, (cellWorldZ + 2) * 0.25);
                double noiseE = SimplexNoise.noise2(seed + 21, (cellWorldX + 2) * 0.25, cellWorldZ * 0.25);
                double noiseW = SimplexNoise.noise2(seed + 21, (cellWorldX - 2) * 0.25, cellWorldZ * 0.25);

                // Connect if noise > threshold (ensures ~60% connectivity)
                if (noiseN > -0.1 && cz > 0) {
                    carveRect(chunkData, cx * MAZE_CELL + 1, cz * MAZE_CELL - 1, TUNNEL_WIDTH, 2);
                }
                if (noiseS > -0.1 && cz < MAZE_CELLS_PER_AXIS - 1) {
                    carveRect(chunkData, cx * MAZE_CELL + 1, cz * MAZE_CELL + TUNNEL_WIDTH + 1, TUNNEL_WIDTH, 2);
                }
                if (noiseE > -0.1 && cx < MAZE_CELLS_PER_AXIS - 1) {
                    carveRect(chunkData, cx * MAZE_CELL + TUNNEL_WIDTH + 1, cz * MAZE_CELL + 1, 2, TUNNEL_WIDTH);
                }
                if (noiseW > -0.1 && cx > 0) {
                    carveRect(chunkData, cx * MAZE_CELL - 1, cz * MAZE_CELL + 1, 2, TUNNEL_WIDTH);
                }

                // Dead-end rooms: if only 1 connection, place a jukebox room
                int connections = 0;
                if (noiseN > -0.1) connections++;
                if (noiseS > -0.1) connections++;
                if (noiseE > -0.1) connections++;
                if (noiseW > -0.1) connections++;

                if (connections <= 1 && chunkRng.nextDouble() < 0.3) {
                    // Expand to 4x4 room
                    carveRect(chunkData, cx * MAZE_CELL, cz * MAZE_CELL, MAZE_CELL, MAZE_CELL);
                    // Mossy cobblestone floor for jukebox rooms
                    for (int dx = 0; dx < MAZE_CELL; dx++) {
                        for (int dz = 0; dz < MAZE_CELL; dz++) {
                            int fx = cx * MAZE_CELL + dx;
                            int fz = cz * MAZE_CELL + dz;
                            if (fx < 16 && fz < 16) {
                                chunkData.setBlock(fx, AIR_MIN_Y - 1, fz, Material.MOSSY_COBBLESTONE);
                            }
                        }
                    }
                    // Place jukebox
                    int jx = cx * MAZE_CELL + 1;
                    int jz = cz * MAZE_CELL + 1;
                    if (jx < 16 && jz < 16) {
                        chunkData.setBlock(jx, AIR_MIN_Y, jz, Material.JUKEBOX);
                    }
                }
            }
        }

        // Cross-chunk corridor connections (carve edges where adjacent chunks should connect)
        for (int cx = 0; cx < MAZE_CELLS_PER_AXIS; cx++) {
            int cellWorldX = chunkX * 16 + cx * MAZE_CELL;
            // North edge (z=0)
            double northConn = SimplexNoise.noise2(seed + 20, cellWorldX * 0.25, (chunkZ * 16 - 2) * 0.25);
            if (northConn > -0.1) {
                carveRect(chunkData, cx * MAZE_CELL + 1, 0, TUNNEL_WIDTH, 1);
            }
            // South edge (z=15)
            int southCellZ = chunkZ * 16 + 14;
            double southConn = SimplexNoise.noise2(seed + 20, cellWorldX * 0.25, (southCellZ + 2) * 0.25);
            if (southConn > -0.1) {
                carveRect(chunkData, cx * MAZE_CELL + 1, 15, TUNNEL_WIDTH, 1);
            }
        }
        for (int cz = 0; cz < MAZE_CELLS_PER_AXIS; cz++) {
            int cellWorldZ = chunkZ * 16 + cz * MAZE_CELL;
            // West edge
            double westConn = SimplexNoise.noise2(seed + 21, (chunkX * 16 - 2) * 0.25, cellWorldZ * 0.25);
            if (westConn > -0.1) {
                carveRect(chunkData, 0, cz * MAZE_CELL + 1, 1, TUNNEL_WIDTH);
            }
            // East edge
            int eastCellX = chunkX * 16 + 14;
            double eastConn = SimplexNoise.noise2(seed + 21, (eastCellX + 2) * 0.25, cellWorldZ * 0.25);
            if (eastConn > -0.1) {
                carveRect(chunkData, 15, cz * MAZE_CELL + 1, 1, TUNNEL_WIDTH);
            }
        }

        // Very sparse torches (most will go out via events anyway)
        for (int cx = 0; cx < MAZE_CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < MAZE_CELLS_PER_AXIS; cz++) {
                if (chunkRng.nextDouble() < 0.20) {
                    int tx = cx * MAZE_CELL + 1;
                    int tz = cz * MAZE_CELL + 1;
                    if (tx < 16 && tz < 16 && chunkData.getType(tx, AIR_MIN_Y, tz) == Material.AIR) {
                        chunkData.setBlock(tx, AIR_MIN_Y, tz, Material.SOUL_TORCH);
                    }
                }
            }
        }
    }

    private void carveRect(ChunkData chunkData, int startX, int startZ, int width, int depth) {
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                int x = startX + dx;
                int z = startZ + dz;
                if (x >= 0 && x < 16 && z >= 0 && z < 16) {
                    for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
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
        return new Location(world, 5.5, AIR_MIN_Y, 5.5);
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
