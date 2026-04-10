package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Level 2 — "The Pipe Works"
 * Mostly solid deepslate mass with sparse utility hallways carved through.
 * Variable width (2-3) and height (3-7 blocks) hallways on a 12-block grid.
 * Pipes coherent per-hallway-line. Machinery rooms at intersections.
 * Cracked/polished wall patches, cobwebs in corners, rare water puddles.
 */
public class Level2ChunkGenerator extends BackroomsChunkGenerator {

    public Level2ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 8;
    private static final int AIR_MIN_Y = 8;
    private static final int CEILING_LOW = 11;
    private static final int CEILING_NORMAL = 13;
    private static final int CEILING_TALL = 15;
    private static final int CEILING_MAX_Y = 22;

    private static final int HALLWAY_PERIOD = 20;
    private static final int HALLWAY_WIDTH = 2;


    private static final int PIPE_CEILING = 1;
    private static final int PIPE_WALL = 2;
    private static final int PIPE_NONE = 0;

    private static final Material[] PIPE_MATERIALS = {
            Material.COPPER_BLOCK, Material.CUT_COPPER, Material.COPPER_GRATE,
            Material.EXPOSED_COPPER, Material.EXPOSED_CUT_COPPER, Material.EXPOSED_COPPER_GRATE,
            Material.WEATHERED_COPPER, Material.WEATHERED_CUT_COPPER, Material.WEATHERED_COPPER_GRATE,
            Material.OXIDIZED_COPPER, Material.OXIDIZED_CUT_COPPER, Material.OXIDIZED_COPPER_GRATE
    };

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();

        // Floor
        chunkData.setRegion(0, FLOOR_Y, 0, 16, FLOOR_HEIGHT, 16, Material.DEEPSLATE);

        // Fill solid from floor to max ceiling
        chunkData.setRegion(0, AIR_MIN_Y, 0, 16, CEILING_MAX_Y, 16, Material.DEEPSLATE_BRICKS);

        // Pipe material for this chunk region
        double pipeRegionNoise = SimplexNoise.noise2(seed + 4, chunkX * 0.3, chunkZ * 0.3);
        Material pipeMat = PIPE_MATERIALS[Math.abs((int) (pipeRegionNoise * 1000)) % PIPE_MATERIALS.length];

        // Wall texture pass: vary solid material per column
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                double wallNoise = SimplexNoise.noise2(seed + 12, worldX * 0.15, worldZ * 0.15);
                Material wallMat;
                if (wallNoise > 0.45) {
                    wallMat = Material.CRACKED_DEEPSLATE_BRICKS;
                } else if (wallNoise < -0.45) {
                    wallMat = Material.POLISHED_DEEPSLATE;
                } else if (wallNoise > 0.25) {
                    wallMat = Material.DEEPSLATE_TILES;
                } else {
                    continue;
                }
                for (int y = AIR_MIN_Y; y < CEILING_MAX_Y; y++) {
                    chunkData.setBlock(x, y, z, wallMat);
                }
            }
        }

        // Pass 1: carve hallways — connected grid with per-line suppression
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                int zMod = Math.floorMod(worldZ, HALLWAY_PERIOD);
                int xMod = Math.floorMod(worldX, HALLWAY_PERIOD);

                int ewLineIndex = Math.floorDiv(worldZ, HALLWAY_PERIOD);
                int nsLineIndex = Math.floorDiv(worldX, HALLWAY_PERIOD);

                int ewWidth = getLineWidth(seed, ewLineIndex, 0);
                int nsWidth = getLineWidth(seed, nsLineIndex, 1);

                boolean onEWLine = (zMod < ewWidth) && isLineActive(seed, ewLineIndex, 0);
                boolean onNSLine = (xMod < nsWidth) && isLineActive(seed, nsLineIndex, 1);

                if (!onEWLine && !onNSLine) continue;

                // Variable ceiling height
                int ceilingY = getCeilingHeight(seed, worldX, worldZ);

                for (int y = AIR_MIN_Y; y < ceilingY; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }

                // Floor material
                if ((worldX + worldZ) % 24 == 0) {
                    chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.COPPER_GRATE);
                } else {
                    double floorDetail = SimplexNoise.noise2(seed + 3, worldX * 0.2, worldZ * 0.2);
                    if (floorDetail > 0.5) {
                        chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.COBBLED_DEEPSLATE);
                    } else if (floorDetail > 0.3) {
                        chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.DEEPSLATE_TILES);
                    } else if (floorDetail > 0.0) {
                        chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.DEEPSLATE_BRICKS);
                    } else if (floorDetail < -0.4) {
                        chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.TUFF);
                    }
                }

            }
        }

        // Pass 2: machinery rooms at hallway intersections
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                int xMod = Math.floorMod(worldX, HALLWAY_PERIOD);
                int zMod = Math.floorMod(worldZ, HALLWAY_PERIOD);
                if (xMod != 0 || zMod != 0) continue;

                int gridX = Math.floorDiv(worldX, HALLWAY_PERIOD);
                int gridZ = Math.floorDiv(worldZ, HALLWAY_PERIOD);

                double roomNoise = SimplexNoise.noise2(seed + 7, gridX * 0.9, gridZ * 0.9);
                if (roomNoise <= 0.98) continue;

                // Variable room size
                int roomRadius = roomNoise > 0.6 ? 4 : 3;
                int ceilingY = getCeilingHeight(seed, worldX, worldZ);
                // Rooms always get at least normal height
                if (ceilingY < CEILING_NORMAL) ceilingY = CEILING_NORMAL;

                int roomStartX = worldX - roomRadius + 1;
                int roomStartZ = worldZ - roomRadius + 1;
                int roomEndX = worldX + roomRadius + 1;
                int roomEndZ = worldZ + roomRadius + 1;

                for (int rx = roomStartX; rx < roomEndX; rx++) {
                    for (int rz = roomStartZ; rz < roomEndZ; rz++) {
                        int lx = rx - chunkX * 16;
                        int lz = rz - chunkZ * 16;
                        if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) continue;

                        for (int y = AIR_MIN_Y; y < ceilingY; y++) {
                            chunkData.setBlock(lx, y, lz, Material.AIR);
                        }
                    }
                }

                int roomType = Math.floorMod(gridX * 7 + gridZ * 13, 8);
                placeMachinery(chunkData, chunkX, chunkZ, worldX, worldZ, roomType, pipeMat, ceilingY);
            }
        }

        // Pass 3: coherent pipes per hallway line
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;

                int zMod = Math.floorMod(worldZ, HALLWAY_PERIOD);
                int xMod = Math.floorMod(worldX, HALLWAY_PERIOD);
                boolean onEWLine = (zMod < getLineWidth(seed, Math.floorDiv(worldZ, HALLWAY_PERIOD), 0));
                boolean onNSLine = (xMod < getLineWidth(seed, Math.floorDiv(worldX, HALLWAY_PERIOD), 1));

                // Find actual ceiling for this column
                int ceilingY = AIR_MIN_Y;
                for (int y = AIR_MIN_Y; y < CEILING_MAX_Y; y++) {
                    if (chunkData.getType(x, y, z) != Material.AIR) {
                        ceilingY = y;
                        break;
                    }
                }
                if (ceilingY <= AIR_MIN_Y + 1) continue;

                if (onEWLine) {
                    int ewLineIndex = Math.floorDiv(worldZ, HALLWAY_PERIOD);
                    int pipeType = getLinePipeType(seed, ewLineIndex, 0);
                    if (pipeType == PIPE_CEILING && zMod == 0) {
                        chunkData.setBlock(x, ceilingY - 1, z, pipeMat);
                    } else if (pipeType == PIPE_WALL && zMod == 0) {
                        chunkData.setBlock(x, AIR_MIN_Y + 2, z, pipeMat);
                    }
                }

                if (onNSLine && !onEWLine) {
                    int nsLineIndex = Math.floorDiv(worldX, HALLWAY_PERIOD);
                    int pipeType = getLinePipeType(seed, nsLineIndex, 1);
                    if (pipeType == PIPE_CEILING && xMod == 0) {
                        chunkData.setBlock(x, ceilingY - 1, z, pipeMat);
                    } else if (pipeType == PIPE_WALL && xMod == 0) {
                        chunkData.setBlock(x, AIR_MIN_Y + 2, z, pipeMat);
                    }
                }
            }
        }

        // Pass 4: cobwebs in corners
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;

                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Count adjacent walls
                int adjWalls = 0;
                if (x == 0 || chunkData.getType(x - 1, AIR_MIN_Y, z) != Material.AIR) adjWalls++;
                if (x == 15 || chunkData.getType(x + 1, AIR_MIN_Y, z) != Material.AIR) adjWalls++;
                if (z == 0 || chunkData.getType(x, AIR_MIN_Y, z - 1) != Material.AIR) adjWalls++;
                if (z == 15 || chunkData.getType(x, AIR_MIN_Y, z + 1) != Material.AIR) adjWalls++;

                if (adjWalls >= 3) {
                    double cobwebNoise = SimplexNoise.noise2(seed + 11, worldX * 0.15, worldZ * 0.15);
                    if (cobwebNoise > 0.95) {
                        // Find ceiling
                        for (int y = CEILING_TALL - 1; y >= AIR_MIN_Y + 1; y--) {
                            if (chunkData.getType(x, y, z) == Material.AIR
                                    && chunkData.getType(x, y + 1, z) != Material.AIR) {
                                chunkData.setBlock(x, y, z, Material.COBWEB);
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Pass 5: lighting — copper lanterns on copper chains
        BlockData hangingLantern = Material.COPPER_LANTERN.createBlockData();
        ((Lantern) hangingLantern).setHanging(true);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % 18 == 0 && worldZ % 18 == 0) {
                    if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;

                    // Find ceiling
                    for (int y = CEILING_TALL - 1; y >= AIR_MIN_Y + 2; y--) {
                        if (chunkData.getType(x, y + 1, z) != Material.AIR
                                && chunkData.getType(x, y, z) == Material.AIR) {
                            chunkData.setBlock(x, y, z, Material.COPPER_CHAIN);
                            chunkData.setBlock(x, y - 1, z, hangingLantern);
                            break;
                        }
                    }
                }
            }
        }

        // Pass 6: extremely rare isolated ceiling water drips
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;

                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                long hash = (worldX * 73856093L) ^ (worldZ * 19349663L) ^ seed;
                if (Math.floorMod(hash, 400) == 0) {
                    // Find ceiling and place water one block below it
                    for (int y = CEILING_TALL - 1; y >= AIR_MIN_Y + 1; y--) {
                        if (chunkData.getType(x, y + 1, z) != Material.AIR
                                && chunkData.getType(x, y, z) == Material.AIR) {
                            chunkData.setBlock(x, y, z, Material.WATER);
                            break;
                        }
                    }
                }
            }
        }
    }

    private int getCeilingHeight(long seed, int worldX, int worldZ) {
        double noise = SimplexNoise.noise2(seed + 8, worldX * (1.0 / 24.0), worldZ * (1.0 / 24.0));
        if (noise > 0.3) return CEILING_TALL;
        if (noise < -0.3) return CEILING_LOW;
        return CEILING_NORMAL;
    }

    private int getLineWidth(long seed, int lineIndex, int direction) {
        double noise = SimplexNoise.noise2(seed + 9, lineIndex * 1.3, direction * 100.0);
        if (noise > 0.4 || noise < -0.4) return 3;
        return HALLWAY_WIDTH;
    }

    private boolean isLineActive(long seed, int lineIndex, int direction) {
        return SimplexNoise.noise2(seed + 13, lineIndex * 0.8, direction * 100.0) > -0.4;
    }

    private int getLinePipeType(long seed, int lineIndex, int direction) {
        double noise = SimplexNoise.noise2(seed + 5, lineIndex * 1.7, direction * 100.0);
        if (noise > 0.3) return PIPE_CEILING;
        if (noise < -0.3) return PIPE_WALL;
        return PIPE_NONE;
    }

    private void placeMachinery(ChunkData chunkData, int chunkX, int chunkZ,
                                int centerWorldX, int centerWorldZ, int roomType,
                                Material pipeMat, int ceilingY) {
        switch (roomType) {
            case 0 -> placeBoiler(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ);
            case 1 -> placePipeJunction(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, pipeMat);
            case 2 -> placeOldEquipment(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ);
            case 3 -> placeVentUnit(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ);
            case 4 -> placeControlPanel(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ);
            case 5 -> placePumpStation(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, ceilingY);
            case 6 -> placeStorageTank(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ);
            default -> placeValveCluster(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ);
        }

        // Hanging copper lantern on ceiling at room center
        int lx = centerWorldX - chunkX * 16;
        int lz = centerWorldZ - chunkZ * 16;
        if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
            BlockData hanging = Material.COPPER_LANTERN.createBlockData();
            ((Lantern) hanging).setHanging(true);
            for (int y = ceilingY - 1; y >= AIR_MIN_Y + 2; y--) {
                if (chunkData.getType(lx, y, lz) == Material.AIR
                        && chunkData.getType(lx, y + 1, lz) != Material.AIR) {
                    chunkData.setBlock(lx, y, lz, Material.COPPER_CHAIN);
                    chunkData.setBlock(lx, y - 1, lz, hanging);
                    break;
                }
            }
        }
    }

    private void placeBoiler(ChunkData chunkData, int chunkX, int chunkZ,
                             int centerWorldX, int centerWorldZ) {
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                int lx = centerWorldX + dx - chunkX * 16;
                int lz = centerWorldZ + dz - chunkZ * 16;
                if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                    chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.COPPER_BLOCK);
                    chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.COPPER_BLOCK);
                    chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.CHISELED_COPPER);
                }
            }
        }
    }

    private void placePipeJunction(ChunkData chunkData, int chunkX, int chunkZ,
                                   int centerWorldX, int centerWorldZ, Material pipeMat) {
        for (int i = -2; i <= 2; i++) {
            int lx = centerWorldX + i - chunkX * 16;
            int lz = centerWorldZ + 2 - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.COPPER_GRATE);
            }
            lx = centerWorldX + 2 - chunkX * 16;
            lz = centerWorldZ + i - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.COPPER_GRATE);
            }
        }
        int cx = centerWorldX + 2 - chunkX * 16;
        int cz = centerWorldZ + 2 - chunkZ * 16;
        if (cx >= 0 && cx < 16 && cz >= 0 && cz < 16) {
            chunkData.setBlock(cx, AIR_MIN_Y, cz, Material.CUT_COPPER);
            chunkData.setBlock(cx, AIR_MIN_Y + 1, cz, Material.CUT_COPPER);
        }
    }

    private void placeOldEquipment(ChunkData chunkData, int chunkX, int chunkZ,
                                   int centerWorldX, int centerWorldZ) {
        for (int i = -2; i <= 1; i++) {
            int lx = centerWorldX + i - chunkX * 16;
            int lz = centerWorldZ - 2 - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.WEATHERED_CUT_COPPER);
            }
        }
        int lx = centerWorldX + 1 - chunkX * 16;
        int lz = centerWorldZ + 1 - chunkZ * 16;
        if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
            chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.OXIDIZED_CHISELED_COPPER);
            chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.OXIDIZED_COPPER);
        }
    }

    private void placeVentUnit(ChunkData chunkData, int chunkX, int chunkZ,
                               int centerWorldX, int centerWorldZ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int lx = centerWorldX + dx - chunkX * 16;
                int lz = centerWorldZ + dz - chunkZ * 16;
                if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                    chunkData.setBlock(lx, FLOOR_HEIGHT - 1, lz, Material.COPPER_GRATE);
                    chunkData.setBlock(lx, FLOOR_HEIGHT - 2, lz, Material.COPPER_BULB);
                }
            }
        }
    }

    private void placeControlPanel(ChunkData chunkData, int chunkX, int chunkZ,
                                   int centerWorldX, int centerWorldZ) {
        for (int i = -2; i <= 1; i++) {
            int lx = centerWorldX - 2 - chunkX * 16;
            int lz = centerWorldZ + i - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.CHISELED_COPPER);
                chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.EXPOSED_CHISELED_COPPER);
            }
        }
        for (int i = -1; i <= 0; i++) {
            int lx = centerWorldX - 2 - chunkX * 16;
            int lz = centerWorldZ + i - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.EXPOSED_COPPER_BULB);
            }
        }
    }

    private void placePumpStation(ChunkData chunkData, int chunkX, int chunkZ,
                                  int centerWorldX, int centerWorldZ, int ceilingY) {
        // Central 1x1 copper column floor to ceiling
        int lx = centerWorldX - chunkX * 16;
        int lz = centerWorldZ - chunkZ * 16;
        if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
            for (int y = AIR_MIN_Y; y < ceilingY; y++) {
                chunkData.setBlock(lx, y, lz, Material.COPPER_BLOCK);
            }
        }
        // Copper grate ring around base
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int px = centerWorldX + dx - chunkX * 16;
                int pz = centerWorldZ + dz - chunkZ * 16;
                if (px >= 0 && px < 16 && pz >= 0 && pz < 16) {
                    chunkData.setBlock(px, AIR_MIN_Y, pz, Material.COPPER_GRATE);
                }
            }
        }
        // Exposed copper pipes entering from 2 walls
        for (int i = -3; i <= -1; i++) {
            int px = centerWorldX + i - chunkX * 16;
            if (px >= 0 && px < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(px, AIR_MIN_Y + 2, lz, Material.EXPOSED_COPPER);
            }
        }
        for (int i = 1; i <= 3; i++) {
            int pz = centerWorldZ + i - chunkZ * 16;
            if (lx >= 0 && lx < 16 && pz >= 0 && pz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y + 2, pz, Material.EXPOSED_COPPER);
            }
        }
    }

    private void placeStorageTank(ChunkData chunkData, int chunkX, int chunkZ,
                                  int centerWorldX, int centerWorldZ) {
        // 3x3 hollow ring of cut_copper, 2 blocks tall
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean isWall = dx == -1 || dx == 1 || dz == -1 || dz == 1;
                int lx = centerWorldX + dx - chunkX * 16;
                int lz = centerWorldZ + dz - chunkZ * 16;
                if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                    if (isWall) {
                        chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.CUT_COPPER);
                        chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.CUT_COPPER);
                        chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.OXIDIZED_COPPER_GRATE);
                    } else {
                        // Interior hollow
                        chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.OXIDIZED_COPPER_GRATE);
                    }
                }
            }
        }
    }

    private void placeValveCluster(ChunkData chunkData, int chunkX, int chunkZ,
                                   int centerWorldX, int centerWorldZ) {
        int lx = centerWorldX - chunkX * 16;
        int lz = centerWorldZ - chunkZ * 16;
        if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
            // Central column
            chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.WEATHERED_COPPER);
            chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.WEATHERED_COPPER);
            chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.WEATHERED_COPPER);
            chunkData.setBlock(lx, AIR_MIN_Y + 3, lz, Material.LIGHTNING_ROD);
        }
        // Copper trapdoors on each face at mid-height
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] off : offsets) {
            int px = centerWorldX + off[0] - chunkX * 16;
            int pz = centerWorldZ + off[1] - chunkZ * 16;
            if (px >= 0 && px < 16 && pz >= 0 && pz < 16) {
                chunkData.setBlock(px, AIR_MIN_Y + 1, pz, Material.COPPER_TRAPDOOR);
            }
        }
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, AIR_MIN_Y, 8.5);
    }
}
