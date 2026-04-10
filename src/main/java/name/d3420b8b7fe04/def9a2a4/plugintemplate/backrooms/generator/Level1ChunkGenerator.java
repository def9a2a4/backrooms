package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Level 1 — "The Habitable Zone"
 * Massive stone warehouse-like spaces. Very open compared to L0.
 * Large continuous stone regions, ceiling drips, support beams, embedded froglight lighting.
 * Think: parking garage at 3 AM, industrial basement, liminal warehouse.
 */
public class Level1ChunkGenerator extends BackroomsChunkGenerator {

    public Level1ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

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

    public static final int LIGHT_SPACING = 11;

    private static final Material[] PILLAR_MATERIALS = {
            Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.POLISHED_ANDESITE
    };

    private static final Material[] WALL_MATERIALS = {
            Material.SMOOTH_STONE, Material.STONE, Material.STONE_BRICKS,
            Material.ANDESITE
    };

    // Noise scale for large continuous stone regions
    private static final double MATERIAL_SCALE = 0.02;
    // Noise scale for accent block variation
    private static final double ACCENT_SCALE = 0.15;

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // Floor and ceiling: large continuous stone regions
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                Material baseMat = getRegionMaterial(seed, worldX, worldZ);
                Material accentedMat = applyAccent(seed, worldX, worldZ, baseMat);

                // Sub-floor fill + surface
                chunkData.setRegion(x, FLOOR_Y, z, x + 1, FLOOR_HEIGHT, z + 1, baseMat);
                chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, accentedMat);

                // Ceiling: same material system
                chunkData.setRegion(x, CEILING_MIN_Y, z, x + 1, CEILING_MAX_Y, z + 1, baseMat);
                chunkData.setBlock(x, CEILING_MIN_Y, z, accentedMat);
            }
        }

        // Very rare single water source in ceiling — ~20% of chunks get one drip
        if (chunkRng.nextInt(5) == 0) {
            int wx = chunkRng.nextInt(16);
            int wz = chunkRng.nextInt(16);
            chunkData.setBlock(wx, CEILING_MIN_Y, wz, Material.WATER);
        }

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

        // Support beams along ceiling between pillars
        placeBeams(chunkData, seed, chunkX, chunkZ);

        // Embedded ceiling lights: verdant froglights in ceiling grid
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % LIGHT_SPACING == 0 && worldZ % LIGHT_SPACING == 0) {
                    chunkData.setBlock(x, CEILING_MIN_Y, z, Material.VERDANT_FROGLIGHT);
                }
            }
        }
    }

    /**
     * Returns the base material for a world position using low-frequency noise
     * to create large continuous stone regions.
     */
    private Material getRegionMaterial(long seed, int worldX, int worldZ) {
        double noise = SimplexNoise.noise2(seed + 1, worldX * MATERIAL_SCALE, worldZ * MATERIAL_SCALE);
        if (noise < -0.2) {
            return Material.STONE;
        } else if (noise < 0.2) {
            return Material.SMOOTH_STONE;
        } else {
            return Material.STONE_BRICKS;
        }
    }

    /**
     * Applies accent blocks: cracked stone bricks replace some stone brick areas,
     * diorite replaces some stone areas.
     */
    private Material applyAccent(long seed, int worldX, int worldZ, Material baseMat) {
        double accentNoise = SimplexNoise.noise2(seed + 10, worldX * ACCENT_SCALE, worldZ * ACCENT_SCALE);
        if (baseMat == Material.STONE_BRICKS && accentNoise > 0.55) {
            return Material.CRACKED_STONE_BRICKS;
        }
        if (baseMat == Material.STONE && accentNoise > 0.6) {
            return Material.POLISHED_DIORITE;
        }
        return baseMat;
    }

    private static final int BEAM_SPACING = 5;
    private static final Material[] BEAM_MATERIALS = {
            Material.STONE_BRICKS, Material.SMOOTH_STONE, Material.POLISHED_ANDESITE
    };

    private static boolean isBeamMaterial(Material mat) {
        for (Material m : BEAM_MATERIALS) {
            if (m == mat) return true;
        }
        return false;
    }

    private static boolean canPlaceBeam(ChunkData chunkData, int x, int y, int z) {
        Material existing = chunkData.getType(x, y, z);
        return existing == Material.AIR || isBeamMaterial(existing);
    }

    /**
     * Places support beams along the ceiling with varied cross-sections (1x1, 2x1, 1x2, 2x2),
     * in both X and Z directions. Beams can intersect each other.
     */
    private void placeBeams(ChunkData chunkData, long seed, int chunkX, int chunkZ) {
        int beamTopY = CEILING_MIN_Y - 1; // Y=17

        // Beams running along Z (placed on X grid lines)
        for (int x = 0; x < 16; x++) {
            int worldX = chunkX * 16 + x;
            if (worldX % BEAM_SPACING != 0) continue;

            double presenceNoise = SimplexNoise.noise2(seed + 20, worldX * 0.04, 0);
            if (presenceNoise < -0.1) continue; // ~55% of rows get beams

            int matIndex = Math.abs((int) (presenceNoise * 1000)) % BEAM_MATERIALS.length;
            Material beamMat = BEAM_MATERIALS[matIndex];

            // Cross-section: width (along X) and height (downward from ceiling)
            double sizeNoise = SimplexNoise.noise2(seed + 22, worldX * 0.08, 0);
            int beamWidth = sizeNoise > 0.3 ? 2 : 1;   // 2x_ ~35%
            int beamHeight = sizeNoise < -0.3 ? 2 : 1;  // _x2 ~35%

            for (int z = 0; z < 16; z++) {
                for (int dx = 0; dx < beamWidth && x + dx < 16; dx++) {
                    for (int dy = 0; dy < beamHeight; dy++) {
                        int py = beamTopY - dy;
                        if (canPlaceBeam(chunkData, x + dx, py, z)) {
                            chunkData.setBlock(x + dx, py, z, beamMat);
                        }
                    }
                }
            }
        }

        // Beams running along X (placed on Z grid lines)
        for (int z = 0; z < 16; z++) {
            int worldZ = chunkZ * 16 + z;
            if (worldZ % BEAM_SPACING != 0) continue;

            double presenceNoise = SimplexNoise.noise2(seed + 21, 0, worldZ * 0.04);
            if (presenceNoise < -0.1) continue;

            int matIndex = Math.abs((int) (presenceNoise * 1000)) % BEAM_MATERIALS.length;
            Material beamMat = BEAM_MATERIALS[matIndex];

            double sizeNoise = SimplexNoise.noise2(seed + 23, 0, worldZ * 0.08);
            int beamWidth = sizeNoise > 0.3 ? 2 : 1;   // width along Z
            int beamHeight = sizeNoise < -0.3 ? 2 : 1;

            for (int x = 0; x < 16; x++) {
                for (int dz = 0; dz < beamWidth && z + dz < 16; dz++) {
                    for (int dy = 0; dy < beamHeight; dy++) {
                        int py = beamTopY - dy;
                        if (canPlaceBeam(chunkData, x, py, z + dz)) {
                            chunkData.setBlock(x, py, z + dz, beamMat);
                        }
                    }
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
        int offset = rng.nextInt(2); // fixed offset for cross-axis
        for (int i = 0; i < CELL_SIZE && baseX + (dir == 0 ? i : 0) < 16 && baseZ + (dir == 1 ? i : 0) < 16; i++) {
            int x = baseX + (dir == 0 ? i : offset);
            int z = baseZ + (dir == 1 ? i : offset);
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

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, AIR_MIN_Y, 8.5);
    }
}
