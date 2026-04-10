package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Level 3 — "The Server Room"
 * Tall white concrete halls on a 24x24 grid. Open floor plan with sparse
 * noise-driven orthogonal walls. Redstone component chains on the floor
 * form one big complicated circuit in patches.
 */
public class Level3ChunkGenerator extends ChunkGenerator {

    // Y layout
    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 4;
    private static final int CEILING_Y = 24;
    private static final int CEILING_MAX_Y = 28;

    // Grid constants
    private static final int CELL_SIZE = 24;
    private static final int WALL_THICK = 2;
    private static final int DOOR_WIDTH = 6;
    private static final int DOOR_START = (CELL_SIZE - DOOR_WIDTH) / 2;
    private static final int DOOR_END = DOOR_START + DOOR_WIDTH;

    // Single material — sterile white concrete
    private static final Material BLOCK = Material.WHITE_CONCRETE;

    // Interior wall noise
    private static final double WALL_NOISE_SCALE = 1.0 / 32.0;
    private static final double WALL_AXIS_SCALE = 1.0 / 24.0;
    private static final double WALL_THRESHOLD = 0.6;

    // Redstone chain grid
    private static final int CHAIN_SPACING = 4;
    private static final int CHAIN_PERIOD = 3; // component, dust, dust

    private static final Material[] REDSTONE_COMPONENTS = {
        Material.REPEATER,
        Material.COMPARATOR,
        Material.REDSTONE_TORCH,
        Material.REDSTONE_LAMP,
        Material.TARGET,
        Material.OBSERVER,
        Material.DAYLIGHT_DETECTOR,
        Material.NOTE_BLOCK,
        Material.DROPPER,
        Material.DISPENSER,
        Material.HOPPER,
        Material.PISTON,
        Material.STICKY_PISTON,
        Material.LECTERN,
        Material.TNT,
        Material.LEVER,
        Material.TRIPWIRE_HOOK,
        Material.REDSTONE_BLOCK,
    };

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                int cellX = Math.floorDiv(worldX, CELL_SIZE);
                int cellZ = Math.floorDiv(worldZ, CELL_SIZE);
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);

                boolean inWallX = localX < WALL_THICK || localX >= CELL_SIZE - WALL_THICK;
                boolean inWallZ = localZ < WALL_THICK || localZ >= CELL_SIZE - WALL_THICK;

                boolean wallRemovedX = false;
                boolean wallRemovedZ = false;
                boolean isDoorX = false;
                boolean isDoorZ = false;

                if (inWallX) {
                    int neighborCellX = localX < WALL_THICK ? cellX - 1 : cellX + 1;
                    wallRemovedX = isWallRemoved(cellX, cellZ, neighborCellX, cellZ, seed);
                    if (!wallRemovedX && localZ >= DOOR_START && localZ < DOOR_END) {
                        isDoorX = isDoorOpen(cellX, cellZ, neighborCellX, cellZ, seed);
                    }
                }
                if (inWallZ) {
                    int neighborCellZ = localZ < WALL_THICK ? cellZ - 1 : cellZ + 1;
                    wallRemovedZ = isWallRemoved(cellX, cellZ, cellX, neighborCellZ, seed);
                    if (!wallRemovedZ && localX >= DOOR_START && localX < DOOR_END) {
                        isDoorZ = isDoorOpen(cellX, cellZ, cellX, neighborCellZ, seed);
                    }
                }

                boolean isWall;
                if (inWallX && inWallZ) {
                    isWall = !(wallRemovedX && wallRemovedZ);
                } else if (inWallX) {
                    isWall = !wallRemovedX && !isDoorX;
                } else if (inWallZ) {
                    isWall = !wallRemovedZ && !isDoorZ;
                } else {
                    isWall = false;
                }

                // Sub-floor fill
                chunkData.setRegion(x, FLOOR_Y, z, x + 1, FLOOR_HEIGHT, z + 1, BLOCK);

                if (isWall) {
                    for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, BLOCK);
                    }
                    // Wall-mounted redstone lamps
                    int edX = cellEdgeDist(localX);
                    int edZ = cellEdgeDist(localZ);
                    if (edX % 6 == 0 && edZ % 6 == 0) {
                        chunkData.setBlock(x, FLOOR_HEIGHT + 4, z, Material.REDSTONE_LAMP);
                        chunkData.setBlock(x, CEILING_Y - 2, z, Material.REDSTONE_LAMP);
                    }
                } else if (isDoorX || isDoorZ) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, BLOCK);
                    for (int y = FLOOR_HEIGHT + 1; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                    placeArchBlock(chunkData, x, z, localX, localZ, inWallX);
                } else {
                    // Interior — floor + air
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, BLOCK);
                    for (int y = FLOOR_HEIGHT + 1; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }

                    // Sparse noise-based interior walls
                    placeInteriorWall(chunkData, x, z, worldX, worldZ, localX, localZ, seed);
                }

                // Ceiling slab
                for (int y = CEILING_Y; y < CEILING_MAX_Y; y++) {
                    chunkData.setBlock(x, y, z, BLOCK);
                }

                // Ceiling-mounted redstone lamps on a grid
                int edCeilX = cellEdgeDist(localX);
                int edCeilZ = cellEdgeDist(localZ);
                if (edCeilX % 4 == 0 && edCeilZ % 4 == 0 && !isWall) {
                    chunkData.setBlock(x, CEILING_Y, z, Material.REDSTONE_LAMP);
                }
            }
        }

        // Second pass: redstone chains on the floor
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                placeRedstoneChain(chunkData, x, z, worldX, worldZ, seed);
            }
        }
    }

    // --- Cell helpers ---

    private static int cellEdgeDist(int localPos) {
        return Math.min(localPos, CELL_SIZE - 1 - localPos);
    }

    private long wallHash(int cellAX, int cellAZ, int cellBX, int cellBZ, long seed) {
        int minX = Math.min(cellAX, cellBX);
        int maxX = Math.max(cellAX, cellBX);
        int minZ = Math.min(cellAZ, cellBZ);
        int maxZ = Math.max(cellAZ, cellBZ);
        return seed ^ ((long) minX * 735632791L + (long) maxX * 524287L
                + (long) minZ * 433494437L + (long) maxZ * 291371L);
    }

    private boolean isWallRemoved(int cellAX, int cellAZ, int cellBX, int cellBZ, long seed) {
        long h = wallHash(cellAX, cellAZ, cellBX, cellBZ, seed);
        return Math.floorMod(h, 100) < 25;
    }

    private boolean isDoorOpen(int cellAX, int cellAZ, int cellBX, int cellBZ, long seed) {
        long h = wallHash(cellAX, cellAZ, cellBX, cellBZ, seed + 7);
        return Math.floorMod(h, 100) < 80;
    }

    // --- Structure placement ---

    private void placeArchBlock(ChunkData chunkData, int x, int z,
                                int localX, int localZ, boolean inWallX) {
        int doorLocalPos = inWallX ? localZ : localX;
        int distFromEdge = Math.min(doorLocalPos - DOOR_START, DOOR_END - 1 - doorLocalPos);

        if (distFromEdge == 0) {
            chunkData.setBlock(x, CEILING_Y - 1, z, BLOCK);
            chunkData.setBlock(x, CEILING_Y - 2, z, BLOCK);
            chunkData.setBlock(x, CEILING_Y - 3, z, BLOCK);
        } else if (distFromEdge == 1) {
            chunkData.setBlock(x, CEILING_Y - 1, z, BLOCK);
            chunkData.setBlock(x, CEILING_Y - 2, z, BLOCK);
        } else if (distFromEdge == 2) {
            chunkData.setBlock(x, CEILING_Y - 1, z, BLOCK);
        }
    }

    private void placeInteriorWall(ChunkData chunkData, int x, int z,
                                   int worldX, int worldZ,
                                   int localX, int localZ,
                                   long seed) {
        // Don't place interior walls too close to perimeter walls
        if (localX < WALL_THICK + 1 || localX >= CELL_SIZE - WALL_THICK - 1
                || localZ < WALL_THICK + 1 || localZ >= CELL_SIZE - WALL_THICK - 1) {
            return;
        }

        double noise = SimplexNoise.noise2(seed + 555L, worldX * WALL_NOISE_SCALE, worldZ * WALL_NOISE_SCALE);
        if (noise < WALL_THRESHOLD) {
            return;
        }

        // Second noise layer determines wall axis
        double axisNoise = SimplexNoise.noise2(seed + 777L, worldX * WALL_AXIS_SCALE, worldZ * WALL_AXIS_SCALE);

        if (axisNoise >= 0) {
            // N-S wall: only place if aligned to even X positions
            if (Math.floorMod(worldX, 2) == 0) {
                for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                    chunkData.setBlock(x, y, z, BLOCK);
                }
            }
        } else {
            // E-W wall: only place if aligned to even Z positions
            if (Math.floorMod(worldZ, 2) == 0) {
                for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                    chunkData.setBlock(x, y, z, BLOCK);
                }
            }
        }
    }

    /** Hash a single chain line coordinate to decide if that line is active. */
    private boolean isChainLineActive(long seed, int lineCoord, boolean isXLine) {
        long h = seed ^ ((long) lineCoord * (isXLine ? 198491317L : 6542989L) + 99999L);
        return Math.floorMod(h, 100) < 40;
    }

    private void placeRedstoneChain(ChunkData chunkData, int x, int z,
                                    int worldX, int worldZ,
                                    long seed) {
        boolean onNSLine = Math.floorMod(worldX, CHAIN_SPACING) == 0;
        boolean onEWLine = Math.floorMod(worldZ, CHAIN_SPACING) == 0;

        if (!onNSLine && !onEWLine) {
            return;
        }

        // Per-line activation: hash the line's fixed coordinate
        boolean nsActive = onNSLine && isChainLineActive(seed, worldX / CHAIN_SPACING, true);
        boolean ewActive = onEWLine && isChainLineActive(seed, worldZ / CHAIN_SPACING, false);

        if (!nsActive && !ewActive) {
            return;
        }

        // Guard: only place on floor, not inside walls
        if (chunkData.getType(x, FLOOR_HEIGHT, z) != BLOCK) {
            return;
        }
        if (chunkData.getType(x, FLOOR_HEIGHT + 1, z) != Material.AIR) {
            return;
        }

        // Determine if this is a component or dust position
        boolean isComponent;
        if (nsActive && ewActive) {
            // Intersection of two active lines: always a component
            isComponent = true;
        } else if (nsActive) {
            isComponent = Math.floorMod(worldZ, CHAIN_PERIOD) == 0;
        } else {
            isComponent = Math.floorMod(worldX, CHAIN_PERIOD) == 0;
        }

        if (isComponent) {
            placeComponent(chunkData, x, z, worldX, worldZ, seed, nsActive, ewActive);
        } else {
            placeDust(chunkData, x, z, nsActive);
        }
    }

    private void placeComponent(ChunkData chunkData, int x, int z,
                                int worldX, int worldZ, long seed,
                                boolean nsActive, boolean ewActive) {
        long posHash = seed ^ ((long) worldX * 198491317L + (long) worldZ * 6542989L + 12345L);
        int compIndex = Math.floorMod(posHash, REDSTONE_COMPONENTS.length);
        Material component = REDSTONE_COMPONENTS[compIndex];

        // Determine facing along the chain direction
        BlockFace facing;
        if (nsActive && !ewActive) {
            facing = Math.floorMod(worldZ, 2) == 0 ? BlockFace.NORTH : BlockFace.SOUTH;
        } else if (ewActive && !nsActive) {
            facing = Math.floorMod(worldX, 2) == 0 ? BlockFace.EAST : BlockFace.WEST;
        } else {
            facing = BlockFace.NORTH;
        }

        try {
            org.bukkit.block.data.BlockData blockData = Bukkit.createBlockData(component);
            if (blockData instanceof Directional directional) {
                if (directional.getFaces().contains(facing)) {
                    directional.setFacing(facing);
                }
            }
            chunkData.setBlock(x, FLOOR_HEIGHT + 1, z, blockData);
        } catch (Exception e) {
            chunkData.setBlock(x, FLOOR_HEIGHT + 1, z, component);
        }
    }

    private void placeDust(ChunkData chunkData, int x, int z, boolean nsChain) {
        try {
            org.bukkit.block.data.BlockData wireData = Bukkit.createBlockData(Material.REDSTONE_WIRE);
            if (wireData instanceof org.bukkit.block.data.type.RedstoneWire wire) {
                if (nsChain) {
                    wire.setFace(BlockFace.NORTH, org.bukkit.block.data.type.RedstoneWire.Connection.SIDE);
                    wire.setFace(BlockFace.SOUTH, org.bukkit.block.data.type.RedstoneWire.Connection.SIDE);
                    wire.setFace(BlockFace.EAST, org.bukkit.block.data.type.RedstoneWire.Connection.NONE);
                    wire.setFace(BlockFace.WEST, org.bukkit.block.data.type.RedstoneWire.Connection.NONE);
                } else {
                    wire.setFace(BlockFace.EAST, org.bukkit.block.data.type.RedstoneWire.Connection.SIDE);
                    wire.setFace(BlockFace.WEST, org.bukkit.block.data.type.RedstoneWire.Connection.SIDE);
                    wire.setFace(BlockFace.NORTH, org.bukkit.block.data.type.RedstoneWire.Connection.NONE);
                    wire.setFace(BlockFace.SOUTH, org.bukkit.block.data.type.RedstoneWire.Connection.NONE);
                }
            }
            chunkData.setBlock(x, FLOOR_HEIGHT + 1, z, wireData);
        } catch (Exception e) {
            chunkData.setBlock(x, FLOOR_HEIGHT + 1, z, Material.REDSTONE_WIRE);
        }
    }

    // --- Standard overrides ---

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, FLOOR_HEIGHT + 2, 8.5);
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
