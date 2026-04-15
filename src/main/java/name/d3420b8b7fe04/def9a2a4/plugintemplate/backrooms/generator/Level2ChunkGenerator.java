package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Level 2 — "The Pipe Works"
 * Mostly solid deepslate mass with sparse utility hallways carved through.
 * Variable width/height hallways on a 20-block grid.
 * 0-4 pipes per hallway line with waterlogged grate leaks.
 * Rare boiler rooms with magma, water, pistons.
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
    private static final int CEILING_MAX_Y = 26;

    private static final int HALLWAY_PERIOD = 20;
    private static final int HALLWAY_WIDTH = 2;

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

                int ceilingY = getCeilingHeight(seed, worldX, worldZ);

                for (int y = AIR_MIN_Y; y < ceilingY; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }

                // Floor material
                if (Math.floorMod(worldX + worldZ, 24) == 0) {
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

        // Pass 2: rooms at hallway intersections
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                int xMod = Math.floorMod(worldX, HALLWAY_PERIOD);
                int zMod = Math.floorMod(worldZ, HALLWAY_PERIOD);
                if (xMod != 0 || zMod != 0) continue;

                int gridX = Math.floorDiv(worldX, HALLWAY_PERIOD);
                int gridZ = Math.floorDiv(worldZ, HALLWAY_PERIOD);

                // Only place rooms where at least one hallway line is active
                if (!isLineActive(seed, gridZ, 0) && !isLineActive(seed, gridX, 1)) continue;

                double roomNoise = SimplexNoise.noise2(seed + 7, gridX * 0.9, gridZ * 0.9);
                if (roomNoise <= 0.3) continue;

                boolean isBoilerRoom = roomNoise > 0.7;
                int roomRadius = isBoilerRoom ? 6 : (roomNoise > 0.5 ? 4 : 3);
                int ceilingY = isBoilerRoom ? CEILING_TALL : getCeilingHeight(seed, worldX, worldZ);
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

                if (isBoilerRoom) {
                    placeBoilerRoom(chunkData, chunkX, chunkZ, worldX, worldZ, ceilingY, pipeMat);
                } else {
                    int roomType = Math.floorMod(gridX * 7 + gridZ * 13, 14);
                    placeMachinery(chunkData, chunkX, chunkZ, worldX, worldZ, roomType, pipeMat, ceilingY);
                }
            }
        }

        // Pass 3: multi-slot pipes per hallway line
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;

                int zMod = Math.floorMod(worldZ, HALLWAY_PERIOD);
                int xMod = Math.floorMod(worldX, HALLWAY_PERIOD);
                int ewLineIndex = Math.floorDiv(worldZ, HALLWAY_PERIOD);
                int nsLineIndex = Math.floorDiv(worldX, HALLWAY_PERIOD);
                int ewWidth = getLineWidth(seed, ewLineIndex, 0);
                int nsWidth = getLineWidth(seed, nsLineIndex, 1);
                boolean onEWLine = (zMod < ewWidth);
                boolean onNSLine = (xMod < nsWidth);

                // Find actual ceiling for this column
                int ceilingY = AIR_MIN_Y;
                for (int y = AIR_MIN_Y; y < CEILING_MAX_Y; y++) {
                    if (chunkData.getType(x, y, z) != Material.AIR) {
                        ceilingY = y;
                        break;
                    }
                }
                if (ceilingY <= AIR_MIN_Y + 2) continue;

                // N-S pipes (placed first, E-W overwrites at conflicts)
                if (onNSLine) {
                    placePipeSlots(chunkData, x, z, worldX, worldZ, seed, nsLineIndex, 1,
                            xMod, nsWidth, ceilingY, pipeMat, BlockFace.NORTH);
                }

                // E-W pipes
                if (onEWLine) {
                    placePipeSlots(chunkData, x, z, worldX, worldZ, seed, ewLineIndex, 0,
                            zMod, ewWidth, ceilingY, pipeMat, BlockFace.EAST);
                }
            }
        }

        // Pass 4: cobwebs in corners
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;

                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                int adjWalls = 0;
                if (x == 0 || chunkData.getType(x - 1, AIR_MIN_Y, z) != Material.AIR) adjWalls++;
                if (x == 15 || chunkData.getType(x + 1, AIR_MIN_Y, z) != Material.AIR) adjWalls++;
                if (z == 0 || chunkData.getType(x, AIR_MIN_Y, z - 1) != Material.AIR) adjWalls++;
                if (z == 15 || chunkData.getType(x, AIR_MIN_Y, z + 1) != Material.AIR) adjWalls++;

                if (adjWalls >= 3) {
                    double cobwebNoise = SimplexNoise.noise2(seed + 11, worldX * 0.15, worldZ * 0.15);
                    if (cobwebNoise > 0.95) {
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
                if (Math.floorMod(worldX, 72) == 0 && Math.floorMod(worldZ, 72) == 0) {
                    long lightHash = (worldX * 48611L) ^ (worldZ * 29423L) ^ seed;
                    if (Math.floorMod(lightHash, 10) >= 3) continue;
                    if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;

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

        // Bedrock boundaries: 6-block floor, 10-block ceiling (onlySolid for ceiling)
        applyBoundaryLayer(chunkData, FLOOR_Y, FLOOR_Y + 6, Material.BEDROCK, false);
        applyBoundaryLayer(chunkData, CEILING_MAX_Y - 10, CEILING_MAX_Y, Material.BEDROCK, true);
    }

    /**
     * Place 0-4 pipe slots for a single hallway line at this column.
     */
    private void placePipeSlots(ChunkData chunkData, int x, int z,
                                int worldX, int worldZ, long seed,
                                int lineIndex, int direction,
                                int mod, int width, int ceilingY,
                                Material pipeMat, BlockFace rodFace) {
        // Slot 0: ceiling, near-wall edge (mod == 0) — most common
        if (mod == 0 && isPipeSlotActive(seed, lineIndex, direction, 0)) {
            placePipeBlock(chunkData, x, ceilingY - 1, z, worldX, worldZ, seed,
                    lineIndex, direction, 0, pipeMat, rodFace);
        }
        // Slot 1: ceiling, far-wall edge (mod == width - 1)
        if (mod == width - 1 && isPipeSlotActive(seed, lineIndex, direction, 1)) {
            placePipeBlock(chunkData, x, ceilingY - 1, z, worldX, worldZ, seed,
                    lineIndex, direction, 1, pipeMat, rodFace);
        }
        // Slot 2: wall height, near-wall edge (mod == 0) — rare
        if (mod == 0 && isPipeSlotActive(seed, lineIndex, direction, 2)) {
            placePipeBlock(chunkData, x, AIR_MIN_Y + 2, z, worldX, worldZ, seed,
                    lineIndex, direction, 2, pipeMat, rodFace);
        }
        // Slot 3: wall height, far-wall edge (mod == width - 1) — rare
        if (mod == width - 1 && isPipeSlotActive(seed, lineIndex, direction, 3)) {
            placePipeBlock(chunkData, x, AIR_MIN_Y + 2, z, worldX, worldZ, seed,
                    lineIndex, direction, 3, pipeMat, rodFace);
        }
        // Slot 4: floor level, near-wall edge (mod == 0) — very rare
        if (mod == 0 && isPipeSlotActive(seed, lineIndex, direction, 4)) {
            placePipeBlock(chunkData, x, AIR_MIN_Y + 1, z, worldX, worldZ, seed,
                    lineIndex, direction, 4, pipeMat, rodFace);
        }
        // Slot 5: floor level, far-wall edge (mod == width - 1) — very rare
        if (mod == width - 1 && isPipeSlotActive(seed, lineIndex, direction, 5)) {
            placePipeBlock(chunkData, x, AIR_MIN_Y + 1, z, worldX, worldZ, seed,
                    lineIndex, direction, 5, pipeMat, rodFace);
        }
    }

    /**
     * Place a single pipe block — either copper material, waterlogged grate, or lightning rod.
     */
    private void placePipeBlock(ChunkData chunkData, int x, int y, int z,
                                int worldX, int worldZ, long seed,
                                int lineIndex, int direction, int slot,
                                Material pipeMat, BlockFace rodFace) {
        if (chunkData.getType(x, y, z) != Material.AIR) return;

        if (isSlotRod(seed, lineIndex, direction, slot)) {
            boolean atEndpoint = (rodFace == BlockFace.EAST)
                    ? Math.floorMod(worldX, HALLWAY_PERIOD) == 0
                    : Math.floorMod(worldZ, HALLWAY_PERIOD) == 0;
            if (atEndpoint) {
                chunkData.setBlock(x, y, z, Material.CHISELED_COPPER);
            } else {
                BlockData rod = Material.LIGHTNING_ROD.createBlockData();
                ((Directional) rod).setFacing(rodFace);
                chunkData.setBlock(x, y, z, rod);
            }
        } else {
            // Rare waterlogged grate leak
            long wHash = (worldX * 73856093L) ^ (worldZ * 19349663L) ^ (seed + slot);
            if (Math.floorMod(wHash, 80) == 0) {
                BlockData grate = Material.COPPER_GRATE.createBlockData();
                ((Waterlogged) grate).setWaterlogged(true);
                chunkData.setBlock(x, y, z, grate);
            } else {
                chunkData.setBlock(x, y, z, pipeMat);
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

    private boolean isPipeSlotActive(long seed, int lineIndex, int direction, int slot) {
        double noise = SimplexNoise.noise2(seed + 20 + slot, lineIndex * 1.7, direction * 100.0);
        double threshold = switch (slot) {
            case 0 -> -0.1;  // ceiling near-wall: ~55%
            case 1 ->  0.3;  // ceiling far-wall:  ~35%
            case 2 ->  0.7;  // wall near:         ~15%
            case 3 ->  0.7;  // wall far:          ~15%
            case 4 ->  0.9;  // floor near:         ~5%
            default ->  0.9; // floor far:          ~5%
        };
        return noise > threshold;
    }

    private boolean isSlotRod(long seed, int lineIndex, int direction, int slot) {
        long hash = (seed + 30 + slot) * 6364136223846793005L + (lineIndex * 1442695040888963407L) + (direction * 73856093L);
        return Math.floorMod(hash, 200) == 0;
    }

    // --- Rotation helpers (90° CW increments viewed top-down) ---

    private static int rotX(int dx, int dz, int rot) {
        return switch (rot & 3) {
            case 1 -> -dz;
            case 2 -> -dx;
            case 3 ->  dz;
            default -> dx;
        };
    }

    private static int rotZ(int dx, int dz, int rot) {
        return switch (rot & 3) {
            case 1 ->  dx;
            case 2 -> -dz;
            case 3 -> -dx;
            default -> dz;
        };
    }

    // --- Room placement ---

    private void placeMachinery(ChunkData chunkData, int chunkX, int chunkZ,
                                int centerWorldX, int centerWorldZ, int roomType,
                                Material pipeMat, int ceilingY) {
        int gridX = Math.floorDiv(centerWorldX, HALLWAY_PERIOD);
        int gridZ = Math.floorDiv(centerWorldZ, HALLWAY_PERIOD);
        int rot = (int) Math.floorMod(gridX * 3L + gridZ * 7L, 4);
        switch (roomType) {
            case 0 -> placeBoiler(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, rot);
            case 1 -> placePipeJunction(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, pipeMat, rot);
            case 2 -> placeOldEquipment(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, rot);
            case 3 -> placeVentUnit(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ);
            case 4 -> placeControlPanel(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, rot);
            case 5 -> placePumpStation(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, ceilingY, rot);
            case 6 -> placeStorageTank(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ);
            case 7 -> placeValveCluster(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ);
            case 8 -> placeOverflowSump(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ);
            case 9 -> placePressureManifold(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, rot);
            case 10 -> placeFiltrationArray(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, rot);
            case 11 -> placeEmergencyShutoff(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, rot);
            case 12 -> placeCondenserUnit(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, rot);
            default -> placeInspectionPit(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, rot);
        }

        placeRoomLantern(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, ceilingY);

        // Rare lever placement (~5% of machinery rooms)
        long leverHash = (long) centerWorldX * 198491317L ^ (long) centerWorldZ * 6542989L;
        if (Math.floorMod(leverHash, 20) == 0) {
            int lx = centerWorldX + 2 - chunkX * 16;
            int lz = centerWorldZ - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.LEVER);
            }
        }

        // Very rare copper trapdoor tunnel opening (~2% of machinery rooms)
        if (Math.floorMod(leverHash + 1, 50) == 0) {
            int tx = centerWorldX - 3 - chunkX * 16;
            int tz = centerWorldZ - chunkZ * 16;
            if (tx >= 0 && tx < 16 && tz >= 0 && tz < 16) {
                chunkData.setBlock(tx, AIR_MIN_Y, tz, Material.COPPER_TRAPDOOR);
            }
        }
    }

    private void placeRoomLantern(ChunkData chunkData, int chunkX, int chunkZ,
                                  int centerWorldX, int centerWorldZ, int ceilingY) {
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

    private void placeBoilerRoom(ChunkData chunkData, int chunkX, int chunkZ,
                                 int centerWorldX, int centerWorldZ, int ceilingY,
                                 Material pipeMat) {
        // Copper grate floor patch (5x5 center)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int lx = centerWorldX + dx - chunkX * 16;
                int lz = centerWorldZ + dz - chunkZ * 16;
                if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                    chunkData.setBlock(lx, FLOOR_HEIGHT - 1, lz, Material.COPPER_GRATE);
                }
            }
        }

        // Central boiler: 3x3 copper shell, magma bottom, water inside, grate lid
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int lx = centerWorldX + dx - chunkX * 16;
                int lz = centerWorldZ + dz - chunkZ * 16;
                if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) continue;

                boolean isEdge = dx == -1 || dx == 1 || dz == -1 || dz == 1;
                if (isEdge) {
                    // Shell walls
                    chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.COPPER_BLOCK);
                    chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.COPPER_BLOCK);
                    chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.COPPER_BLOCK);
                    chunkData.setBlock(lx, AIR_MIN_Y + 3, lz, Material.COPPER_GRATE);
                } else {
                    // Interior: magma bottom, water above
                    chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.MAGMA_BLOCK);
                    BlockData waterGrate = Material.COPPER_GRATE.createBlockData();
                    ((Waterlogged) waterGrate).setWaterlogged(true);
                    chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, waterGrate);
                    chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.AIR);
                    chunkData.setBlock(lx, AIR_MIN_Y + 3, lz, Material.COPPER_GRATE);
                }
            }
        }

        // Secondary smaller boiler (2x2) offset
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int lx = centerWorldX + 4 + dx - chunkX * 16;
                int lz = centerWorldZ + 3 + dz - chunkZ * 16;
                if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) continue;

                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.COPPER_BLOCK);
                chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.COPPER_BLOCK);
                chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.CHISELED_COPPER);
            }
        }

        // Pipe runs along ceiling from boiler to walls
        for (int i = -5; i <= 5; i++) {
            int lx = centerWorldX + i - chunkX * 16;
            int lz = centerWorldZ - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                if (chunkData.getType(lx, ceilingY - 1, lz) == Material.AIR) {
                    chunkData.setBlock(lx, ceilingY - 1, lz, pipeMat);
                }
            }
        }

        // Room lantern
        placeRoomLantern(chunkData, chunkX, chunkZ, centerWorldX, centerWorldZ, ceilingY);
    }

    // --- Small machinery room types ---

    private void placeBoiler(ChunkData chunkData, int chunkX, int chunkZ,
                             int centerWorldX, int centerWorldZ, int rot) {
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                int lx = centerWorldX + rotX(dx, dz, rot) - chunkX * 16;
                int lz = centerWorldZ + rotZ(dx, dz, rot) - chunkZ * 16;
                if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                    chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.COPPER_BLOCK);
                    chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.COPPER_BLOCK);
                    chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.CHISELED_COPPER);
                }
            }
        }
    }

    private void placePipeJunction(ChunkData chunkData, int chunkX, int chunkZ,
                                   int centerWorldX, int centerWorldZ, Material pipeMat, int rot) {
        for (int i = -2; i <= 2; i++) {
            int lx = centerWorldX + rotX(i, 2, rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(i, 2, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.COPPER_GRATE);
            }
            lx = centerWorldX + rotX(2, i, rot) - chunkX * 16;
            lz = centerWorldZ + rotZ(2, i, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.COPPER_GRATE);
            }
        }
        int cx = centerWorldX + rotX(2, 2, rot) - chunkX * 16;
        int cz = centerWorldZ + rotZ(2, 2, rot) - chunkZ * 16;
        if (cx >= 0 && cx < 16 && cz >= 0 && cz < 16) {
            chunkData.setBlock(cx, AIR_MIN_Y, cz, Material.CUT_COPPER);
            chunkData.setBlock(cx, AIR_MIN_Y + 1, cz, Material.CUT_COPPER);
        }
    }

    private void placeOldEquipment(ChunkData chunkData, int chunkX, int chunkZ,
                                   int centerWorldX, int centerWorldZ, int rot) {
        for (int i = -2; i <= 1; i++) {
            int lx = centerWorldX + rotX(i, -2, rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(i, -2, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.WEATHERED_CUT_COPPER);
            }
        }
        int lx = centerWorldX + rotX(1, 1, rot) - chunkX * 16;
        int lz = centerWorldZ + rotZ(1, 1, rot) - chunkZ * 16;
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
                    chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.COPPER_BULB);
                }
            }
        }
    }

    private void placeControlPanel(ChunkData chunkData, int chunkX, int chunkZ,
                                   int centerWorldX, int centerWorldZ, int rot) {
        for (int i = -2; i <= 1; i++) {
            int lx = centerWorldX + rotX(-2, i, rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(-2, i, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.CHISELED_COPPER);
                chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.EXPOSED_CHISELED_COPPER);
            }
        }
        for (int i = -1; i <= 0; i++) {
            int lx = centerWorldX + rotX(-2, i, rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(-2, i, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.EXPOSED_COPPER_BULB);
            }
        }
    }

    private void placePumpStation(ChunkData chunkData, int chunkX, int chunkZ,
                                  int centerWorldX, int centerWorldZ, int ceilingY, int rot) {
        int lx = centerWorldX - chunkX * 16;
        int lz = centerWorldZ - chunkZ * 16;
        if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
            for (int y = AIR_MIN_Y; y < ceilingY; y++) {
                chunkData.setBlock(lx, y, lz, Material.COPPER_BLOCK);
            }
        }
        // 3x3 grate ring (symmetric, no rotation needed)
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
        // First pipe arm (rotated)
        for (int i = -3; i <= -1; i++) {
            int px = centerWorldX + rotX(i, 0, rot) - chunkX * 16;
            int pz = centerWorldZ + rotZ(i, 0, rot) - chunkZ * 16;
            if (px >= 0 && px < 16 && pz >= 0 && pz < 16) {
                chunkData.setBlock(px, AIR_MIN_Y + 2, pz, Material.EXPOSED_COPPER);
            }
        }
        // Second pipe arm (rotated, 90° offset from first)
        for (int i = 1; i <= 3; i++) {
            int px = centerWorldX + rotX(0, i, rot) - chunkX * 16;
            int pz = centerWorldZ + rotZ(0, i, rot) - chunkZ * 16;
            if (px >= 0 && px < 16 && pz >= 0 && pz < 16) {
                chunkData.setBlock(px, AIR_MIN_Y + 2, pz, Material.EXPOSED_COPPER);
            }
        }
    }

    private void placeStorageTank(ChunkData chunkData, int chunkX, int chunkZ,
                                  int centerWorldX, int centerWorldZ) {
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
            chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.WEATHERED_COPPER);
            chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.WEATHERED_COPPER);
            chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.WEATHERED_COPPER);
            chunkData.setBlock(lx, AIR_MIN_Y + 3, lz, Material.LIGHTNING_ROD);
        }
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] off : offsets) {
            int px = centerWorldX + off[0] - chunkX * 16;
            int pz = centerWorldZ + off[1] - chunkZ * 16;
            if (px >= 0 && px < 16 && pz >= 0 && pz < 16) {
                chunkData.setBlock(px, AIR_MIN_Y + 1, pz, Material.COPPER_TRAPDOOR);
            }
        }
    }

    // --- New machinery room types ---

    private void placeOverflowSump(ChunkData chunkData, int chunkX, int chunkZ,
                                   int centerWorldX, int centerWorldZ) {
        // 3x3 waterlogged grate pool
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int lx = centerWorldX + dx - chunkX * 16;
                int lz = centerWorldZ + dz - chunkZ * 16;
                if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                    BlockData wg = Material.COPPER_GRATE.createBlockData();
                    ((Waterlogged) wg).setWaterlogged(true);
                    chunkData.setBlock(lx, FLOOR_HEIGHT - 1, lz, wg);
                }
            }
        }
        // Raised copper rim
        int[][] rim = {{-2,-1},{-2,0},{-2,1},{2,-1},{2,0},{2,1},{-1,-2},{0,-2},{1,-2},{-1,2},{0,2},{1,2}};
        for (int[] r : rim) {
            int lx = centerWorldX + r[0] - chunkX * 16;
            int lz = centerWorldZ + r[1] - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.EXPOSED_CUT_COPPER);
            }
        }
    }

    private void placePressureManifold(ChunkData chunkData, int chunkX, int chunkZ,
                                       int centerWorldX, int centerWorldZ, int rot) {
        // Row of chiseled copper gauges on one wall with chains above
        for (int i = -2; i <= 1; i++) {
            int lx = centerWorldX + rotX(i, -2, rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(i, -2, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.WEATHERED_COPPER);
                chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.CHISELED_COPPER);
                chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.COPPER_CHAIN);
                chunkData.setBlock(lx, AIR_MIN_Y + 3, lz, Material.COPPER_CHAIN);
            }
        }
        // Copper grate floor patch in front
        for (int i = -1; i <= 0; i++) {
            int lx = centerWorldX + rotX(i, -1, rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(i, -1, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, FLOOR_HEIGHT - 1, lz, Material.COPPER_GRATE);
            }
        }
    }

    private void placeFiltrationArray(ChunkData chunkData, int chunkX, int chunkZ,
                                      int centerWorldX, int centerWorldZ, int rot) {
        // 3 chain columns hanging floor-to-ceiling
        int[] positions = {-2, 0, 2};
        for (int dx : positions) {
            int lx = centerWorldX + rotX(dx, 0, rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(dx, 0, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                for (int y = AIR_MIN_Y + 1; y < CEILING_NORMAL - 1; y++) {
                    chunkData.setBlock(lx, y, lz, Material.COPPER_CHAIN);
                }
            }
        }
        // Copper grate floor strip between chains
        for (int i = -2; i <= 2; i++) {
            int lx = centerWorldX + rotX(i, 0, rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(i, 0, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, FLOOR_HEIGHT - 1, lz, Material.COPPER_GRATE);
            }
        }
        // Waterlogged grate at one end (intake)
        int lx = centerWorldX + rotX(-2, 1, rot) - chunkX * 16;
        int lz = centerWorldZ + rotZ(-2, 1, rot) - chunkZ * 16;
        if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
            BlockData wg = Material.COPPER_GRATE.createBlockData();
            ((Waterlogged) wg).setWaterlogged(true);
            chunkData.setBlock(lx, FLOOR_HEIGHT - 1, lz, wg);
        }
    }

    private void placeEmergencyShutoff(ChunkData chunkData, int chunkX, int chunkZ,
                                       int centerWorldX, int centerWorldZ, int rot) {
        // Wall of copper trapdoors (shut valves)
        for (int i = -2; i <= 1; i++) {
            int lx = centerWorldX + rotX(i, -2, rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(i, -2, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.WEATHERED_COPPER);
                chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.COPPER_TRAPDOOR);
                chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.COPPER_TRAPDOOR);
            }
        }
        // Central shutoff handle (lightning rod)
        int lx = centerWorldX + rotX(0, -1, rot) - chunkX * 16;
        int lz = centerWorldZ + rotZ(0, -1, rot) - chunkZ * 16;
        if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
            chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.OXIDIZED_COPPER);
            chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.LIGHTNING_ROD);
        }
    }

    private void placeCondenserUnit(ChunkData chunkData, int chunkX, int chunkZ,
                                    int centerWorldX, int centerWorldZ, int rot) {
        // Two copper columns with grate caps
        int[][] cols = {{-2, 0}, {2, 0}};
        for (int[] col : cols) {
            int lx = centerWorldX + rotX(col[0], col[1], rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(col[0], col[1], rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y, lz, Material.EXPOSED_COPPER);
                chunkData.setBlock(lx, AIR_MIN_Y + 1, lz, Material.EXPOSED_COPPER);
                chunkData.setBlock(lx, AIR_MIN_Y + 2, lz, Material.EXPOSED_COPPER);
                chunkData.setBlock(lx, AIR_MIN_Y + 3, lz, Material.OXIDIZED_COPPER_GRATE);
            }
        }
        // Chain bridge between columns at top
        for (int i = -1; i <= 1; i++) {
            int lx = centerWorldX + rotX(i, 0, rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(i, 0, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, AIR_MIN_Y + 3, lz, Material.COPPER_CHAIN);
            }
        }
        // Waterlogged grate between columns at floor
        int lx = centerWorldX - chunkX * 16;
        int lz = centerWorldZ - chunkZ * 16;
        if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
            BlockData wg = Material.COPPER_GRATE.createBlockData();
            ((Waterlogged) wg).setWaterlogged(true);
            chunkData.setBlock(lx, FLOOR_HEIGHT - 1, lz, wg);
        }
    }

    private void placeInspectionPit(ChunkData chunkData, int chunkX, int chunkZ,
                                    int centerWorldX, int centerWorldZ, int rot) {
        // 1x4 trench dug 1 block into the floor
        for (int i = -2; i <= 1; i++) {
            int lx = centerWorldX + rotX(0, i, rot) - chunkX * 16;
            int lz = centerWorldZ + rotZ(0, i, rot) - chunkZ * 16;
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                chunkData.setBlock(lx, FLOOR_HEIGHT - 1, lz, Material.AIR);
                chunkData.setBlock(lx, FLOOR_HEIGHT - 2, lz, Material.COPPER_GRATE);
            }
        }
        // Cut copper walkway edges alongside the trench
        for (int i = -2; i <= 1; i++) {
            for (int side : new int[]{-1, 1}) {
                int lx = centerWorldX + rotX(side, i, rot) - chunkX * 16;
                int lz = centerWorldZ + rotZ(side, i, rot) - chunkZ * 16;
                if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                    chunkData.setBlock(lx, FLOOR_HEIGHT - 1, lz, Material.CUT_COPPER);
                }
            }
        }
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
