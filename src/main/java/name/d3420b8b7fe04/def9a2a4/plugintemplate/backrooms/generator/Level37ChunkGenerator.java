package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

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
 * Level 37 — "The Poolrooms"
 * 24x24 symmetric rooms on a grid with large palette regions.
 * Rooms can merge. Clean, symmetric aesthetic. Overworld at midday.
 */
public class Level37ChunkGenerator extends ChunkGenerator {

    // Y layout
    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 8;
    private static final int CEILING_Y = 20;
    private static final int CEILING_MAX_Y = 26;

    // Grid constants
    private static final int CELL_SIZE = 24;
    private static final int WALL_THICK = 2;
    private static final int INTERIOR_SIZE = CELL_SIZE - WALL_THICK * 2; // 20
    private static final int DOOR_WIDTH = 6;
    private static final int DOOR_START = (CELL_SIZE - DOOR_WIDTH) / 2; // 9
    private static final int DOOR_END = DOOR_START + DOOR_WIDTH;        // 15

    // Region size: multiple of CELL_SIZE so boundaries align with room walls
    private static final int REGION_SIZE = CELL_SIZE * 14; // 336 blocks

    // Palette indices
    private static final int PAL_WALL = 0;
    private static final int PAL_FLOOR = 1;
    private static final int PAL_PILLAR = 2;
    private static final int PAL_CEILING = 3;

    private static final Material[][] PALETTES = {
        // Quartz
        { Material.QUARTZ_BLOCK, Material.SMOOTH_QUARTZ, Material.QUARTZ_PILLAR, Material.QUARTZ_BRICKS },
        // White concrete
        { Material.WHITE_CONCRETE, Material.WHITE_CONCRETE, Material.WHITE_CONCRETE, Material.WHITE_CONCRETE },
        // Smooth stone
        { Material.SMOOTH_STONE, Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.SMOOTH_STONE },
        // Dark prismarine
        { Material.DARK_PRISMARINE, Material.DARK_PRISMARINE, Material.DARK_PRISMARINE, Material.DARK_PRISMARINE },
        // End stone bricks (pillar also uses bricks, no raw end stone)
        { Material.END_STONE_BRICKS, Material.END_STONE_BRICKS, Material.END_STONE_BRICKS, Material.END_STONE_BRICKS },
        // Sandstone (chiseled, smooth, cut only)
        { Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.CHISELED_SANDSTONE, Material.SMOOTH_SANDSTONE },
    };

    // Pool types
    private static final int POOL_DRY = 0;
    private static final int POOL_SHALLOW = 1;
    private static final int POOL_DEEP = 2;

    /** Dark prismarine uses sea lanterns; everything else uses yellow froglights. */
    private static Material lightBlock(Material[] palette) {
        return palette[PAL_WALL] == Material.DARK_PRISMARINE
                ? Material.SEA_LANTERN
                : Material.OCHRE_FROGLIGHT;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                Material[] palette = getPalette(worldX, worldZ, seed);
                Material light = lightBlock(palette);

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
                chunkData.setRegion(x, FLOOR_Y, z, x + 1, FLOOR_HEIGHT, z + 1, palette[PAL_FLOOR]);

                if (isWall) {
                    for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_WALL]);
                    }
                    // Symmetric wall lights: use cell-local edge distance
                    int edWallX = cellEdgeDist(localX);
                    int edWallZ = cellEdgeDist(localZ);
                    // Lights at symmetric positions along walls (edgeDist 0,6,12 on the cross-axis)
                    if (edWallX % 6 == 0 && edWallZ % 6 == 0) {
                        chunkData.setBlock(x, FLOOR_HEIGHT + 3, z, light);
                        chunkData.setBlock(x, CEILING_Y - 2, z, light);
                    }
                } else if (isDoorX || isDoorZ) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette[PAL_FLOOR]);
                    for (int y = FLOOR_HEIGHT + 1; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                    placeArchBlock(chunkData, x, z, localX, localZ, inWallX, palette);
                } else {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette[PAL_FLOOR]);
                    for (int y = FLOOR_HEIGHT + 1; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }

                    int masterCellX = getMasterCellX(cellX, cellZ, seed);
                    int masterCellZ = getMasterCellZ(cellX, cellZ, seed);
                    placeInterior(chunkData, x, z, localX, localZ, masterCellX, masterCellZ, seed, palette, light);
                    placePool(chunkData, x, z, localX, localZ, masterCellX, masterCellZ, seed, palette);
                }

                // Ceiling
                boolean skylight = isSkylightRoom(cellX, cellZ, seed);
                boolean inSkylightZone = skylight
                        && localX >= 8 && localX < 16
                        && localZ >= 8 && localZ < 16;

                if (inSkylightZone && !isWall) {
                    for (int y = CEILING_Y; y < CEILING_MAX_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                } else {
                    for (int y = CEILING_Y; y < CEILING_MAX_Y; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_CEILING]);
                    }
                }

                // Symmetric ceiling lights: edgeDist on both axes, every 3
                int edCeilX = cellEdgeDist(localX);
                int edCeilZ = cellEdgeDist(localZ);
                if (edCeilX % 3 == 0 && edCeilZ % 3 == 0) {
                    if (chunkData.getType(x, CEILING_Y, z) != Material.AIR) {
                        chunkData.setBlock(x, CEILING_Y, z, light);
                    }
                }
            }
        }

        // Symmetric underwater and dry floor lights (second pass)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);
                int edX = cellEdgeDist(localX);
                int edZ = cellEdgeDist(localZ);
                Material[] palette = getPalette(worldX, worldZ, seed);
                Material light = lightBlock(palette);

                // Underwater lights: symmetric every 4 edge-distance units
                if (edX % 4 == 0 && edZ % 4 == 0) {
                    if (chunkData.getType(x, FLOOR_HEIGHT, z) == Material.WATER) {
                        for (int y = FLOOR_HEIGHT; y >= FLOOR_Y; y--) {
                            if (chunkData.getType(x, y, z) != Material.WATER) {
                                chunkData.setBlock(x, y, z, light);
                                break;
                            }
                        }
                    }
                }

                // Dry floor lights: symmetric every 6 edge-distance units, offset by 3
                if (edX % 6 == 3 && edZ % 6 == 3) {
                    Material above = chunkData.getType(x, FLOOR_HEIGHT + 1, z);
                    Material at = chunkData.getType(x, FLOOR_HEIGHT, z);
                    if (above == Material.AIR && at != Material.WATER && at != Material.AIR) {
                        chunkData.setBlock(x, FLOOR_HEIGHT, z, light);
                    }
                }
            }
        }
    }

    // --- Symmetric distance helpers ---

    /** Distance from nearest cell edge (0 at edges, 11 at center for 24-wide cell). */
    private static int cellEdgeDist(int localPos) {
        return Math.min(localPos, CELL_SIZE - 1 - localPos);
    }

    /** Distance from nearest interior edge (0 at edge, 9 at center for 20-wide interior). */
    private static int interiorEdgeDist(int interiorPos) {
        return Math.min(interiorPos, INTERIOR_SIZE - 1 - interiorPos);
    }

    /** Check if position matches a symmetric pair at given distance from center. */
    private static boolean symmetricMatch(int pos, int distFromCenter) {
        int center = INTERIOR_SIZE / 2; // 10
        return pos == center - 1 - distFromCenter || pos == center + distFromCenter;
    }

    // --- Palette region (aligned to cell grid) ---

    private Material[] getPalette(int worldX, int worldZ, long seed) {
        int regionX = Math.floorDiv(worldX, REGION_SIZE);
        int regionZ = Math.floorDiv(worldZ, REGION_SIZE);
        long regionSeed = seed ^ (regionX * 198491317L + regionZ * 6542989L);
        int paletteIndex = Math.floorMod(regionSeed, PALETTES.length);
        return PALETTES[paletteIndex];
    }

    // --- Cell helpers ---

    private Random getCellRandom(int cellX, int cellZ, long seed) {
        return new Random(seed ^ ((long) cellX * 982451653L + (long) cellZ * 472882049L));
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

    private int getMasterCellX(int cellX, int cellZ, long seed) {
        int cx = cellX;
        for (int i = 0; i < 3; i++) {
            if (isWallRemoved(cx - 1, cellZ, cx, cellZ, seed)) {
                cx--;
            } else {
                break;
            }
        }
        return cx;
    }

    private int getMasterCellZ(int cellX, int cellZ, long seed) {
        int cz = cellZ;
        for (int i = 0; i < 3; i++) {
            if (isWallRemoved(cellX, cz - 1, cellX, cz, seed)) {
                cz--;
            } else {
                break;
            }
        }
        return cz;
    }

    private int getRoomType(int cellX, int cellZ, long seed) {
        Random rng = getCellRandom(cellX, cellZ, seed);
        int roll = rng.nextInt(100);
        if (roll < 14) return 0;  // open
        if (roll < 28) return 1;  // pillared
        if (roll < 40) return 2;  // colonnade
        if (roll < 50) return 3;  // gallery
        if (roll < 60) return 4;  // light columns
        if (roll < 70) return 5;  // floor inlay
        if (roll < 78) return 6;  // fountain
        if (roll < 86) return 7;  // platform
        if (roll < 93) return 8;  // alcoves
        return 9;                 // grand
    }

    /** Fully independent seed for pool type to avoid correlation with room type. */
    private int getPoolType(int cellX, int cellZ, long seed) {
        Random rng = new Random(seed ^ ((long) cellX * 314159265L + (long) cellZ * 271828182L));
        int roll = rng.nextInt(100);
        if (roll < 30) return POOL_DRY;
        if (roll < 80) return POOL_SHALLOW;
        return POOL_DEEP;
    }

    private boolean isSkylightRoom(int cellX, int cellZ, long seed) {
        Random rng = getCellRandom(cellX, cellZ, seed);
        rng.nextInt(); rng.nextInt();
        return rng.nextInt(100) < 20;
    }

    // --- Structure placement ---

    private void placeArchBlock(ChunkData chunkData, int x, int z,
                                int localX, int localZ,
                                boolean inWallX, Material[] palette) {
        int doorLocalPos = inWallX ? localZ : localX;
        int distFromEdge = Math.min(doorLocalPos - DOOR_START, DOOR_END - 1 - doorLocalPos);

        // Arches use wall material (not pillar) for consistency
        if (distFromEdge == 0) {
            chunkData.setBlock(x, CEILING_Y - 1, z, palette[PAL_WALL]);
            chunkData.setBlock(x, CEILING_Y - 2, z, palette[PAL_WALL]);
        } else if (distFromEdge == 1) {
            chunkData.setBlock(x, CEILING_Y - 1, z, palette[PAL_WALL]);
        }
    }

    private void placeInterior(ChunkData chunkData, int x, int z,
                               int localX, int localZ,
                               int masterCellX, int masterCellZ, long seed,
                               Material[] palette, Material light) {
        int roomType = getRoomType(masterCellX, masterCellZ, seed);

        int ix = localX - WALL_THICK;
        int iz = localZ - WALL_THICK;

        if (ix < 0 || ix >= INTERIOR_SIZE || iz < 0 || iz >= INTERIOR_SIZE) {
            return;
        }

        int edX = interiorEdgeDist(ix);
        int edZ = interiorEdgeDist(iz);

        switch (roomType) {
            case 1 -> { // Pillared: 4 symmetric 2x2 pillars
                // Symmetric positions: (4,5) and (14,15) → edgeDist 4,5 on each axis
                boolean onPillarX = symmetricMatch(ix, 5) || symmetricMatch(ix, 4);
                boolean onPillarZ = symmetricMatch(iz, 5) || symmetricMatch(iz, 4);
                if (onPillarX && onPillarZ) {
                    for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_PILLAR]);
                    }
                }
            }
            case 2 -> { // Colonnade: symmetric pillar rows along Z edges
                boolean nearEdgeZ = edZ <= 2;
                // Symmetric positions: (2,17) and (7,12)
                boolean onPillar = symmetricMatch(ix, 8) || symmetricMatch(ix, 3);
                if (nearEdgeZ && onPillar) {
                    for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_PILLAR]);
                    }
                }
            }
            case 3 -> { // Gallery: symmetric half-height cross partitions
                boolean onPartX = symmetricMatch(ix, 4) && edZ > 3;
                boolean onPartZ = symmetricMatch(iz, 4) && edX > 3;
                if (onPartX || onPartZ) {
                    for (int y = FLOOR_HEIGHT + 1; y < FLOOR_HEIGHT + 6; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_WALL]);
                    }
                    chunkData.setBlock(x, FLOOR_HEIGHT + 6, z, palette[PAL_PILLAR]);
                }
            }
            case 4 -> { // Light columns: symmetric glass+froglight towers
                // Symmetric positions at edgeDist 3 and 7 on each axis
                boolean onColX = edX == 3 || edX == 7;
                boolean onColZ = edZ == 3 || edZ == 7;
                // Only place at corners of this grid (both axes match)
                if (onColX && onColZ) {
                    for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS);
                    }
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, light);
                    chunkData.setBlock(x, (FLOOR_HEIGHT + CEILING_Y) / 2, z, light);
                    chunkData.setBlock(x, CEILING_Y - 1, z, light);
                }
            }
            case 5 -> { // Floor inlay: symmetric cross + diamond ring
                // Use edgeDist for perfect symmetry
                int dfc = Math.max(INTERIOR_SIZE / 2 - edX, 1); // 1 at center, 10 at edge
                int dfcZ = Math.max(INTERIOR_SIZE / 2 - edZ, 1);
                // Cross: within 1 of center axis, extending 6 out
                boolean onCross = (edX >= 9 && edZ >= 4) || (edZ >= 9 && edX >= 4);
                // Diamond ring at manhattan distance 7 from center
                boolean onDiamond = Math.abs(dfc + dfcZ - 3) <= 1 && dfc < 8 && dfcZ < 8;
                if (onCross || onDiamond) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette[PAL_PILLAR]);
                }
            }
            case 6 -> { // Fountain: center feature + pillar ring + floor accent
                // Center 4x4 (edgeDist >= 8 on both axes)
                if (edX >= 8 && edZ >= 8) {
                    for (int y = FLOOR_HEIGHT; y < FLOOR_HEIGHT + 5; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_PILLAR]);
                    }
                    chunkData.setBlock(x, FLOOR_HEIGHT + 5, z, light);
                }
                // 4 pillar pairs at symmetric positions
                boolean ringX = edX == 4 || edX == 5;
                boolean ringZ = edZ == 4 || edZ == 5;
                if (ringX && ringZ) {
                    for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_PILLAR]);
                    }
                }
                // Floor accent ring
                if (edX + edZ == 6 && edX >= 1 && edZ >= 1) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette[PAL_PILLAR]);
                }
            }
            case 7 -> { // Platform: raised center with corner pillars
                // 8x8 center (edgeDist >= 6)
                if (edX >= 6 && edZ >= 6) {
                    chunkData.setBlock(x, FLOOR_HEIGHT + 1, z, palette[PAL_PILLAR]);
                    if (edX == 6 && edZ == 6) {
                        for (int y = FLOOR_HEIGHT + 1; y < CEILING_Y; y++) {
                            chunkData.setBlock(x, y, z, palette[PAL_PILLAR]);
                        }
                    }
                }
                // Step around platform (symmetric)
                if ((edX == 5 && edZ >= 5) || (edZ == 5 && edX >= 5)) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette[PAL_PILLAR]);
                }
            }
            case 8 -> { // Alcoves: pillar-framed niches on all 4 walls
                boolean pillarX = edX == 4 || edX == 5;
                boolean atEdgeZ = edZ <= 3;
                boolean pillarZ = edZ == 4 || edZ == 5;
                boolean atEdgeX = edX <= 3;

                if ((pillarX && atEdgeZ) || (pillarZ && atEdgeX)) {
                    for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_PILLAR]);
                    }
                }
                // Niche light at center of each wall (edgeDist 9 on one axis, 0 on other)
                if ((edX >= 9 && edZ == 0) || (edZ >= 9 && edX == 0)) {
                    chunkData.setBlock(x, FLOOR_HEIGHT + 3, z, light);
                }
            }
            case 9 -> { // Grand: corner pillars + center inlay + perimeter trim
                // Corner 2x2 pillars (edgeDist 2-3 on both axes)
                if (edX >= 2 && edX <= 3 && edZ >= 2 && edZ <= 3) {
                    for (int y = FLOOR_HEIGHT; y < CEILING_Y; y++) {
                        chunkData.setBlock(x, y, z, palette[PAL_PILLAR]);
                    }
                }
                // Center concentric square inlay
                if (edX >= 6 && edZ >= 6) {
                    if (edX == 6 || edZ == 6 || (edX >= 8 && edZ >= 8)) {
                        chunkData.setBlock(x, FLOOR_HEIGHT, z, palette[PAL_PILLAR]);
                    }
                }
                // Perimeter trim at edgeDist 1
                if (edX == 1 && edZ >= 1 || edZ == 1 && edX >= 1) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette[PAL_PILLAR]);
                }
            }
            // case 0 (open): nothing
        }
    }

    private void placePool(ChunkData chunkData, int x, int z,
                           int localX, int localZ,
                           int masterCellX, int masterCellZ, long seed,
                           Material[] palette) {
        if (chunkData.getType(x, FLOOR_HEIGHT + 1, z) != Material.AIR) {
            return;
        }

        int poolType = getPoolType(masterCellX, masterCellZ, seed);
        if (poolType == POOL_DRY) {
            return;
        }

        // Symmetric dry border: 2 blocks inside walls
        int interiorStart = WALL_THICK + 2;
        int interiorEnd = CELL_SIZE - WALL_THICK - 2;
        if (localX < interiorStart || localX >= interiorEnd
                || localZ < interiorStart || localZ >= interiorEnd) {
            return;
        }

        // Don't overwrite decorated floor tiles or lights
        Material currentFloor = chunkData.getType(x, FLOOR_HEIGHT, z);
        if (currentFloor != palette[PAL_FLOOR]) {
            return;
        }

        if (poolType == POOL_SHALLOW) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
            chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, palette[PAL_FLOOR]);
        } else {
            // Symmetric stepped entry
            int distFromPoolEdge = Math.min(
                    Math.min(localX - interiorStart, interiorEnd - 1 - localX),
                    Math.min(localZ - interiorStart, interiorEnd - 1 - localZ));
            if (distFromPoolEdge <= 1) {
                chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
                chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, palette[PAL_FLOOR]);
            } else {
                chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
                chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.WATER);
                chunkData.setBlock(x, FLOOR_HEIGHT - 2, z, Material.WATER);
                chunkData.setBlock(x, FLOOR_HEIGHT - 3, z, palette[PAL_FLOOR]);
            }
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
                return Biome.PLAINS;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.PLAINS);
            }
        };
    }
}
