package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

public class Level0ChunkGenerator extends BackroomsChunkGenerator {

    public Level0ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

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
    private static final double CEILING_SCALE = 1.0 / 300.0;
    private static final double CARPET_SCALE = 1.0 / 340.0;
    private static final double WALL_MAT_SCALE = 1.0 / 320.0;
    private static final double NOISE_OFFSET = 100000.0;
    private static final double SPAWN_PLAIN_RADIUS = 200.0;

    private static final int REGION_EMPTY = 0;
    private static final int REGION_WALLS = 1;
    private static final int REGION_PILLARS = 2;

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        chunkData.setRegion(0, FLOOR_MIN_Y, 0, 16, FLOOR_MAX_Y, 16, Material.YELLOW_TERRACOTTA);
        chunkData.setRegion(0, CEILING_MIN_Y, 0, 16, CEILING_MAX_Y, 16, Material.YELLOW_TERRACOTTA);

        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // ceiling variation: large zones of smooth stone
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                double spawnBias = Math.max(0, 1.0 - (double) (worldX * worldX + worldZ * worldZ) / (SPAWN_PLAIN_RADIUS * SPAWN_PLAIN_RADIUS));
                double cnx = (worldX + NOISE_OFFSET) * CEILING_SCALE;
                double cnz = (worldZ + NOISE_OFFSET) * CEILING_SCALE;
                double ceilingNoise = SimplexNoise.noise2(seed + 100, cnx, cnz)
                        + 0.5 * SimplexNoise.noise2(seed + 101, cnx * 2, cnz * 2)
                        - spawnBias;
                if (ceilingNoise > 0.0) {
                    chunkData.setBlock(x, CEILING_MIN_Y, z, Material.SMOOTH_STONE);
                }
            }
        }

        // cell structures with noise-driven wall material
        for (int cx = 0; cx < CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < CELLS_PER_AXIS; cz++) {
                int baseX = cx * CELL_SIZE;
                int baseZ = cz * CELL_SIZE;

                double rawCenterX = chunkX * 16 + baseX + CELL_SIZE / 2.0;
                double rawCenterZ = chunkZ * 16 + baseZ + CELL_SIZE / 2.0;

                double regionNoise = SimplexNoise.noise2(seed, rawCenterX * REGION_SCALE, rawCenterZ * REGION_SCALE);
                int region = classifyRegion(regionNoise);

                double wallSpawnBias = Math.max(0, 1.0 - (rawCenterX * rawCenterX + rawCenterZ * rawCenterZ) / (SPAWN_PLAIN_RADIUS * SPAWN_PLAIN_RADIUS));
                double wnx = (rawCenterX + NOISE_OFFSET) * WALL_MAT_SCALE;
                double wnz = (rawCenterZ + NOISE_OFFSET) * WALL_MAT_SCALE;
                double wallMatNoise = SimplexNoise.noise2(seed + 300, wnx, wnz)
                        + 0.5 * SimplexNoise.noise2(seed + 301, wnx * 2, wnz * 2)
                        - wallSpawnBias;
                Material wallMat = wallMatNoise > 0.0 ? Material.BAMBOO_PLANKS : Material.YELLOW_TERRACOTTA;

                placeStructure(chunkData, chunkRng, baseX, baseZ, region, wallMat);
            }
        }

        // floor carpet: large zones of yellow carpet on exposed floor
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                double carpetSpawnBias = Math.max(0, 1.0 - (double) (worldX * worldX + worldZ * worldZ) / (SPAWN_PLAIN_RADIUS * SPAWN_PLAIN_RADIUS));
                double fpnx = (worldX + NOISE_OFFSET) * CARPET_SCALE;
                double fpnz = (worldZ + NOISE_OFFSET) * CARPET_SCALE;
                double carpetNoise = SimplexNoise.noise2(seed + 200, fpnx, fpnz)
                        + 0.5 * SimplexNoise.noise2(seed + 201, fpnx * 2, fpnz * 2)
                        - carpetSpawnBias;
                if (carpetNoise > 0.0) {
                    chunkData.setBlock(x, AIR_MIN_Y, z, Material.YELLOW_CARPET);
                }
            }
        }

        // lights
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % LIGHT_SPACING == 0 && worldZ % LIGHT_SPACING == 0) {
                    chunkData.setBlock(x, CEILING_MIN_Y, z, Material.OCHRE_FROGLIGHT);
                }
            }
        }

        // Bedrock boundaries: 10-block floor and 10-block ceiling
        applyBoundaryLayer(chunkData, FLOOR_MIN_Y, FLOOR_MIN_Y + 10, Material.BEDROCK, false);
        applyBoundaryLayer(chunkData, CEILING_MAX_Y - 10, CEILING_MAX_Y, Material.BEDROCK, false);
    }

    private int classifyRegion(double noise) {
        if (noise < -0.3) return REGION_PILLARS;
        if (noise > 0.3) return REGION_EMPTY;
        return REGION_WALLS;
    }

    private void placeStructure(ChunkData chunkData, Random rng, int baseX, int baseZ, int region, Material wallMat) {
        double roll = rng.nextDouble();
        switch (region) {
            case REGION_EMPTY -> placeEmpty(chunkData, rng, baseX, baseZ, roll, wallMat);
            case REGION_WALLS -> placeWallRegion(chunkData, rng, baseX, baseZ, roll, wallMat);
            case REGION_PILLARS -> placePillarRegion(chunkData, rng, baseX, baseZ, roll, wallMat);
        }
    }

    private void placeEmpty(ChunkData chunkData, Random rng, int baseX, int baseZ, double roll, Material wallMat) {
        if (roll < 0.596) {
            rng.nextInt(4);
        } else if (roll < 0.60) {
            placeDesk(chunkData, baseX, baseZ, rng.nextInt(4));
        } else if (roll < 0.75) {
            placePillar(chunkData, rng, baseX, baseZ, wallMat);
        } else if (roll < 0.90) {
            placeRandomWall(chunkData, rng, baseX, baseZ, wallMat);
        } else {
            placeLCorner(chunkData, baseX, baseZ, rng.nextInt(4), wallMat);
        }
    }

    private void placeWallRegion(ChunkData chunkData, Random rng, int baseX, int baseZ, double roll, Material wallMat) {
        if (roll < 0.10) {
            rng.nextInt(4);
        } else if (roll < 0.20) {
            placePillar(chunkData, rng, baseX, baseZ, wallMat);
        } else if (roll < 0.55) {
            placeRandomWall(chunkData, rng, baseX, baseZ, wallMat);
        } else if (roll < 0.75) {
            placeCeilingWall(chunkData, rng, baseX, baseZ, wallMat);
        } else {
            placeLCorner(chunkData, baseX, baseZ, rng.nextInt(4), wallMat);
        }
    }

    private void placePillarRegion(ChunkData chunkData, Random rng, int baseX, int baseZ, double roll, Material wallMat) {
        if (roll < 0.15) {
            rng.nextInt(4);
        } else if (roll < 0.70) {
            placePillar(chunkData, rng, baseX, baseZ, wallMat);
        } else if (roll < 0.85) {
            placeRandomWall(chunkData, rng, baseX, baseZ, wallMat);
        } else {
            placeLCorner(chunkData, baseX, baseZ, rng.nextInt(4), wallMat);
        }
    }

    private void placeRandomWall(ChunkData chunkData, Random rng, int baseX, int baseZ, Material wallMat) {
        int dir = rng.nextInt(4);
        double heightRoll = rng.nextDouble();
        if (heightRoll < 0.35) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MAX_Y, wallMat);
        } else if (heightRoll < 0.65) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MIN_Y + 3, wallMat);
        } else {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MIN_Y + 1, wallMat);
        }
    }

    private void placeCeilingWall(ChunkData chunkData, Random rng, int baseX, int baseZ, Material wallMat) {
        int dir = rng.nextInt(4);
        double heightRoll = rng.nextDouble();
        if (heightRoll < 0.30) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MAX_Y, wallMat);
        } else if (heightRoll < 0.70) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MAX_Y - 3, AIR_MAX_Y, wallMat);
        } else {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MAX_Y - 1, AIR_MAX_Y, wallMat);
        }
    }

    private void placePillar(ChunkData chunkData, Random rng, int baseX, int baseZ, Material wallMat) {
        int px = baseX + 1 + rng.nextInt(2);
        int pz = baseZ + 1 + rng.nextInt(2);
        for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
            chunkData.setBlock(px, y, pz, wallMat);
        }
    }

    private void placeWall(ChunkData chunkData, int baseX, int baseZ, int direction, int yMin, int yMax, Material wallMat) {
        for (int i = 0; i < CELL_SIZE; i++) {
            int x, z;
            switch (direction) {
                case 0 -> { x = baseX + i; z = baseZ; }
                case 1 -> { x = baseX + i; z = baseZ + 3; }
                case 2 -> { x = baseX; z = baseZ + i; }
                default -> { x = baseX + 3; z = baseZ + i; }
            }
            for (int y = yMin; y < yMax; y++) {
                chunkData.setBlock(x, y, z, wallMat);
            }
        }
    }

    private void placeLCorner(ChunkData chunkData, int baseX, int baseZ, int corner, Material wallMat) {
        switch (corner) {
            case 0 -> {
                placeWall(chunkData, baseX, baseZ, 0, AIR_MIN_Y, AIR_MAX_Y, wallMat);
                placeWall(chunkData, baseX, baseZ, 2, AIR_MIN_Y, AIR_MAX_Y, wallMat);
            }
            case 1 -> {
                placeWall(chunkData, baseX, baseZ, 0, AIR_MIN_Y, AIR_MAX_Y, wallMat);
                placeWall(chunkData, baseX, baseZ, 3, AIR_MIN_Y, AIR_MAX_Y, wallMat);
            }
            case 2 -> {
                placeWall(chunkData, baseX, baseZ, 1, AIR_MIN_Y, AIR_MAX_Y, wallMat);
                placeWall(chunkData, baseX, baseZ, 2, AIR_MIN_Y, AIR_MAX_Y, wallMat);
            }
            default -> {
                placeWall(chunkData, baseX, baseZ, 1, AIR_MIN_Y, AIR_MAX_Y, wallMat);
                placeWall(chunkData, baseX, baseZ, 3, AIR_MIN_Y, AIR_MAX_Y, wallMat);
            }
        }
    }

    private void placeDesk(ChunkData chunkData, int baseX, int baseZ, int orientation) {
        int x = baseX + 1;
        int z = baseZ + 1;
        int y = AIR_MIN_Y;

        BlockFace stair0Face, stair1Face, shelfFace;
        int s0x, s0z, s1x, s1z, bx, bz;

        switch (orientation) {
            case 0 -> { // line along +X
                s0x = x; s0z = z; s1x = x + 1; s1z = z; bx = x + 2; bz = z;
                stair0Face = BlockFace.WEST; stair1Face = BlockFace.EAST; shelfFace = BlockFace.NORTH;
            }
            case 1 -> { // line along +Z
                s0x = x; s0z = z; s1x = x; s1z = z + 1; bx = x; bz = z + 2;
                stair0Face = BlockFace.NORTH; stair1Face = BlockFace.SOUTH; shelfFace = BlockFace.EAST;
            }
            case 2 -> { // line along -X
                s0x = x + 2; s0z = z; s1x = x + 1; s1z = z; bx = x; bz = z;
                stair0Face = BlockFace.EAST; stair1Face = BlockFace.WEST; shelfFace = BlockFace.SOUTH;
            }
            default -> { // line along -Z
                s0x = x; s0z = z + 2; s1x = x; s1z = z + 1; bx = x; bz = z;
                stair0Face = BlockFace.SOUTH; stair1Face = BlockFace.NORTH; shelfFace = BlockFace.WEST;
            }
        }

        org.bukkit.block.data.BlockData stair0 = Bukkit.createBlockData(Material.OAK_STAIRS);
        if (stair0 instanceof Stairs s) {
            s.setHalf(Stairs.Half.TOP);
            s.setFacing(stair0Face);
        }
        chunkData.setBlock(s0x, y, s0z, stair0);

        org.bukkit.block.data.BlockData stair1 = Bukkit.createBlockData(Material.OAK_STAIRS);
        if (stair1 instanceof Stairs s) {
            s.setHalf(Stairs.Half.TOP);
            s.setFacing(stair1Face);
        }
        chunkData.setBlock(s1x, y, s1z, stair1);

        org.bukkit.block.data.BlockData shelf = Bukkit.createBlockData(Material.CHISELED_BOOKSHELF);
        if (shelf instanceof Directional d) {
            d.setFacing(shelfFace);
        }
        chunkData.setBlock(bx, y, bz, shelf);
    }

    @Override
    public int getSpawnY() {
        return AIR_MIN_Y;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, AIR_MIN_Y, 8.5);
    }
}
