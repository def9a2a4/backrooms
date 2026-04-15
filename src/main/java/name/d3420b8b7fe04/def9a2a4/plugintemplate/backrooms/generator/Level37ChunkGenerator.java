package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Level 37 — "The Poolrooms"
 * 24x24 symmetric rooms on a grid with large palette regions.
 * Rooms can merge. Clean, symmetric aesthetic. Overworld at midday.
 */
public class Level37ChunkGenerator extends BackroomsChunkGenerator {

    public Level37ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    // Y layout
    private static final int FLOOR_Y = -10;
    private static final int FLOOR_HEIGHT = 8;
    private static final int CEILING_Y = 20;        // normal ceiling
    private static final int TALL_CEILING_Y = 28;    // tall rooms
    private static final int CEILING_MAX_Y = 44;     // universal ceiling slab top (all room types)
    private static final int SKYLIGHT_MAX_Y = 34;   // skylight clears up to here (below barrier zone)
    private static final int MID_FLOOR_Y = 14;       // second story floor for short rooms

    // Room height classes
    private static final int CLASS_SHORT = 0;
    private static final int CLASS_NORMAL = 1;
    private static final int CLASS_TALL = 2;

    // Grid constants
    private static final int CELL_SIZE = 24;
    private static final int WALL_THICK = 2;
    private static final int INTERIOR_SIZE = CELL_SIZE - WALL_THICK * 2; // 20
    private static final int DOOR_WIDTH = 6;
    private static final int DOOR_START = (CELL_SIZE - DOOR_WIDTH) / 2; // 9
    private static final int DOOR_END = DOOR_START + DOOR_WIDTH;        // 15

    // Region size: multiple of CELL_SIZE so boundaries align with room walls
    private static final int REGION_SIZE = CELL_SIZE * 14; // 336 blocks

    // Palette record — each entry defines wall/floor/pillar/ceiling/light materials
    record Palette(String name, Material wall, Material floor, Material pillar, Material ceiling, Material light) {}

    private static final Palette[] DEFAULT_PALETTES = {
        new Palette("Quartz",          Material.QUARTZ_BLOCK,     Material.SMOOTH_QUARTZ,    Material.QUARTZ_PILLAR,    Material.QUARTZ_BRICKS,    Material.PEARLESCENT_FROGLIGHT),
        new Palette("White Concrete",  Material.WHITE_CONCRETE,   Material.WHITE_CONCRETE,   Material.WHITE_CONCRETE,   Material.WHITE_CONCRETE,   Material.PEARLESCENT_FROGLIGHT),
        new Palette("Smooth Stone",    Material.SMOOTH_STONE,     Material.SMOOTH_STONE,     Material.STONE,            Material.SMOOTH_STONE,     Material.PEARLESCENT_FROGLIGHT),
        new Palette("Dark Prismarine", Material.DARK_PRISMARINE,  Material.DARK_PRISMARINE,  Material.DARK_PRISMARINE,  Material.DARK_PRISMARINE,  Material.SEA_LANTERN),
        new Palette("End Stone",       Material.END_STONE_BRICKS, Material.END_STONE_BRICKS, Material.END_STONE_BRICKS, Material.END_STONE_BRICKS, Material.OCHRE_FROGLIGHT),
        new Palette("Sandstone",       Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE,    Material.CUT_SANDSTONE,    Material.SMOOTH_SANDSTONE, Material.OCHRE_FROGLIGHT),
        new Palette("Stone",           Material.STONE,            Material.STONE,            Material.STONE,            Material.STONE,            Material.PEARLESCENT_FROGLIGHT),
    };

    private Palette[] palettes = DEFAULT_PALETTES;

    // Skylight types
    private static final int SKY_NONE = 0;
    private static final int SKY_STANDARD = 1;    // existing 8x8 center
    private static final int SKY_PINHOLE = 2;     // small 4x4 center
    private static final int SKY_CROSS = 3;       // plus-shaped opening
    private static final int SKY_BARS = 4;        // full ceiling open with periodic bars

    // Pool types
    private static final int POOL_DRY = 0;
    private static final int POOL_SHALLOW = 1;
    private static final int POOL_DEEP = 2;
    private static final int POOL_CHANNEL = 3;    // 4-wide water strip through center
    private static final int POOL_MOAT = 4;       // perimeter water ring, dry center
    private static final int POOL_HALF = 5;       // one half wet, one dry
    private static final int POOL_ABYSS = 6;      // extra-deep stepped pool
    private static final int POOL_ABYSS_DEEP = 7; // rare extra-extra-deep pool

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config == null) return;
        List<Map<?, ?>> paletteList = config.getMapList("palettes");
        if (paletteList.isEmpty()) return;

        Palette[] parsed = new Palette[paletteList.size()];
        for (int i = 0; i < paletteList.size(); i++) {
            Map<?, ?> entry = paletteList.get(i);
            String name    = entry.containsKey("name") ? String.valueOf(entry.get("name")) : "Palette " + i;
            Material wall    = Material.matchMaterial(String.valueOf(entry.get("wall")));
            Material floor   = Material.matchMaterial(String.valueOf(entry.get("floor")));
            Material pillar  = Material.matchMaterial(String.valueOf(entry.get("pillar")));
            Material ceiling = Material.matchMaterial(String.valueOf(entry.get("ceiling")));
            Material light   = Material.matchMaterial(String.valueOf(entry.get("light")));
            if (wall == null || floor == null || pillar == null || ceiling == null || light == null) {
                // Invalid palette entry — fall back to all defaults
                return;
            }
            parsed[i] = new Palette(name, wall, floor, pillar, ceiling, light);
        }
        this.palettes = parsed;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                Palette palette = getPalette(worldX, worldZ, seed);
                Material light = palette.light();

                int cellX = Math.floorDiv(worldX, CELL_SIZE);
                int cellZ = Math.floorDiv(worldZ, CELL_SIZE);
                int localX = Math.floorMod(worldX, CELL_SIZE);
                int localZ = Math.floorMod(worldZ, CELL_SIZE);

                int roomClass = getRoomClass(cellX, cellZ, seed);
                int ceilingY = getCeilingY(roomClass);

                boolean inWallX = localX < WALL_THICK || localX >= CELL_SIZE - WALL_THICK;
                boolean inWallZ = localZ < WALL_THICK || localZ >= CELL_SIZE - WALL_THICK;

                boolean wallRemovedX = false;
                boolean wallRemovedZ = false;
                boolean isDoorX = false;
                boolean isDoorZ = false;
                boolean bothShortX = false;
                boolean bothShortZ = false;

                if (inWallX) {
                    int neighborCellX = localX < WALL_THICK ? cellX - 1 : cellX + 1;
                    wallRemovedX = isWallRemoved(cellX, cellZ, neighborCellX, cellZ, seed);
                    if (!wallRemovedX && localZ >= DOOR_START && localZ < DOOR_END) {
                        isDoorX = isDoorOpen(cellX, cellZ, neighborCellX, cellZ, seed);
                    }
                    bothShortX = roomClass == CLASS_SHORT
                            && getRoomClass(neighborCellX, cellZ, seed) == CLASS_SHORT;
                }
                if (inWallZ) {
                    int neighborCellZ = localZ < WALL_THICK ? cellZ - 1 : cellZ + 1;
                    wallRemovedZ = isWallRemoved(cellX, cellZ, cellX, neighborCellZ, seed);
                    if (!wallRemovedZ && localX >= DOOR_START && localX < DOOR_END) {
                        isDoorZ = isDoorOpen(cellX, cellZ, cellX, neighborCellZ, seed);
                    }
                    bothShortZ = roomClass == CLASS_SHORT
                            && getRoomClass(cellX, neighborCellZ, seed) == CLASS_SHORT;
                }
                boolean shortTransition = (inWallX && bothShortX) || (inWallZ && bothShortZ);

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
                chunkData.setRegion(x, FLOOR_Y, z, x + 1, FLOOR_HEIGHT, z + 1, palette.floor());

                if (isWall) {
                    for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, palette.wall());
                    }
                    // Symmetric wall lights: use cell-local edge distance
                    int edWallX = cellEdgeDist(localX);
                    int edWallZ = cellEdgeDist(localZ);
                    if (edWallX % 6 == 0 && edWallZ % 6 == 0) {
                        chunkData.setBlock(x, FLOOR_HEIGHT + 3, z, light);
                        chunkData.setBlock(x, ceilingY - 2, z, light);
                        if (roomClass == CLASS_TALL) {
                            chunkData.setBlock(x, (FLOOR_HEIGHT + ceilingY) / 2, z, light);
                        }
                    }
                } else if (isDoorX || isDoorZ) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.floor());
                    if (shortTransition) {
                        for (int y = FLOOR_HEIGHT + 1; y < MID_FLOOR_Y; y++) {
                            chunkData.setBlock(x, y, z, Material.AIR);
                        }
                        chunkData.setBlock(x, MID_FLOOR_Y, z, palette.floor());
                        for (int y = MID_FLOOR_Y + 1; y < ceilingY; y++) {
                            chunkData.setBlock(x, y, z, Material.AIR);
                        }
                    } else {
                        for (int y = FLOOR_HEIGHT + 1; y < ceilingY; y++) {
                            chunkData.setBlock(x, y, z, Material.AIR);
                        }
                    }
                    placeArchBlock(chunkData, x, z, localX, localZ, inWallX, palette, ceilingY);
                } else if ((inWallX || inWallZ) && shortTransition) {
                    // Wall removed between two short rooms — extend mid-floor
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.floor());
                    for (int y = FLOOR_HEIGHT + 1; y < MID_FLOOR_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                    chunkData.setBlock(x, MID_FLOOR_Y, z, palette.floor());
                    for (int y = MID_FLOOR_Y + 1; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                } else {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.floor());
                    for (int y = FLOOR_HEIGHT + 1; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }

                    int masterCellX = getMasterCellX(cellX, cellZ, seed);
                    int masterCellZ = getMasterCellZ(cellX, cellZ, seed);
                    int roomType = getRoomType(masterCellX, masterCellZ, seed, roomClass);
                    int skylightType = getSkylightType(cellX, cellZ, seed);
                    placeInterior(chunkData, x, z, localX, localZ, masterCellX, masterCellZ, seed, palette, light, roomType, ceilingY, skylightType);
                    placePool(chunkData, x, z, localX, localZ, masterCellX, masterCellZ, seed, palette);
                }

                // Ceiling
                int skylightType = getSkylightType(cellX, cellZ, seed);
                boolean inSkylightZone = skylightType != SKY_NONE
                        && isInSkylightZone(skylightType, localX, localZ);

                if (skylightType == SKY_BARS && !isWall
                        && localX >= WALL_THICK && localX < CELL_SIZE - WALL_THICK
                        && localZ >= WALL_THICK && localZ < CELL_SIZE - WALL_THICK) {
                    // Bars: entire interior ceiling carved out 5 blocks.
                    // Solid bar strips (localX % 4 == 0) hang down from upper ceiling.
                    // Gaps between bars are fully open to sky.
                    boolean isBar = localX % 4 == 0;
                    if (isBar) {
                        for (int y = ceilingY; y < CEILING_MAX_Y; y++) {
                            chunkData.setBlock(x, y, z, palette.ceiling());
                        }
                    } else {
                        for (int y = ceilingY; y < SKYLIGHT_MAX_Y; y++) {
                            chunkData.setBlock(x, y, z, Material.AIR);
                        }
                    }
                } else if (inSkylightZone && !isWall) {
                    for (int y = ceilingY; y < SKYLIGHT_MAX_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                } else {
                    for (int y = ceilingY; y < CEILING_MAX_Y; y++) {
                        chunkData.setBlock(x, y, z, palette.ceiling());
                    }
                }

                // Symmetric ceiling lights: edgeDist on both axes, every 3
                int edCeilX = cellEdgeDist(localX);
                int edCeilZ = cellEdgeDist(localZ);
                if (edCeilX % 3 == 0 && edCeilZ % 3 == 0) {
                    if (chunkData.getType(x, ceilingY, z) != Material.AIR) {
                        chunkData.setBlock(x, ceilingY, z, light);
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
                Palette palette = getPalette(worldX, worldZ, seed);
                Material light = palette.light();

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

        // Bedrock floor (6 blocks), barrier ceiling (10 blocks including over skylights)
        applyBoundaryLayer(chunkData, FLOOR_Y, FLOOR_Y + 6, Material.BEDROCK, false);
        applyBoundaryLayer(chunkData, SKYLIGHT_MAX_Y, CEILING_MAX_Y, Material.BARRIER, false);
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

    private Palette getPalette(int worldX, int worldZ, long seed) {
        int regionX = Math.floorDiv(worldX, REGION_SIZE);
        int regionZ = Math.floorDiv(worldZ, REGION_SIZE);
        long regionSeed = seed ^ (regionX * 198491317L + regionZ * 6542989L);
        int paletteIndex = (int) Math.floorMod(regionSeed, palettes.length);
        return palettes[paletteIndex];
    }

    private int getPaletteIndex(int worldX, int worldZ, long seed) {
        int regionX = Math.floorDiv(worldX, REGION_SIZE);
        int regionZ = Math.floorDiv(worldZ, REGION_SIZE);
        long regionSeed = seed ^ (regionX * 198491317L + regionZ * 6542989L);
        return (int) Math.floorMod(regionSeed, palettes.length);
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

    private int getRoomClass(int cellX, int cellZ, long seed) {
        double n = SimplexNoise.noise2(seed + 999, cellX * 0.15, cellZ * 0.15);
        if (n < -0.3) return CLASS_SHORT;
        if (n > 0.3) return CLASS_TALL;
        return CLASS_NORMAL;
    }

    private int getCeilingY(int roomClass) {
        return roomClass == CLASS_TALL ? TALL_CEILING_Y : CEILING_Y;
    }

    private int getRoomType(int cellX, int cellZ, long seed, int roomClass) {
        Random rng = getCellRandom(cellX, cellZ, seed);
        int roll = rng.nextInt(100);
        switch (roomClass) {
            case CLASS_SHORT: {
                if (roll < 20) return 10; // open two-level
                if (roll < 37) return 11; // partitioned lower
                if (roll < 52) return 12; // corner stair
                if (roll < 67) return 13; // mezzanine ring
                if (roll < 82) return 14; // dense pillars two-level
                return 15;                // small pools
            }
            case CLASS_TALL: {
                if (roll < 30) return 20; // grand cathedral
                if (roll < 55) return 21; // atrium
                if (roll < 80) return 22; // towering columns
                return 23;                // layered
            }
            default: {
                if (roll < 10) return 0;  // open
                if (roll < 20) return 1;  // pillared
                if (roll < 29) return 2;  // colonnade
                if (roll < 37) return 3;  // gallery
                if (roll < 44) return 4;  // light columns
                if (roll < 51) return 5;  // floor inlay
                if (roll < 57) return 6;  // fountain
                if (roll < 63) return 7;  // platform
                if (roll < 68) return 8;  // alcoves
                if (roll < 73) return 9;  // grand
                if (roll < 82) return 30; // walls with gap
                if (roll < 91) return 31; // walls solid
                return 32;               // walls cross
            }
        }
    }

    /** Fully independent seed for pool type to avoid correlation with room type. */
    private int getPoolType(int cellX, int cellZ, long seed) {
        Random rng = new Random(seed ^ ((long) cellX * 314159265L + (long) cellZ * 271828182L));
        int roll = rng.nextInt(100);
        if (roll < 20) return POOL_DRY;
        if (roll < 44) return POOL_SHALLOW;
        if (roll < 57) return POOL_DEEP;
        if (roll < 69) return POOL_CHANNEL;
        if (roll < 79) return POOL_MOAT;
        if (roll < 88) return POOL_HALF;
        if (roll < 97) return POOL_ABYSS;
        return POOL_ABYSS_DEEP;
    }

    private int getSkylightType(int cellX, int cellZ, long seed) {
        Random rng = getCellRandom(cellX, cellZ, seed);
        rng.nextInt(); rng.nextInt();
        int roll = rng.nextInt(100);
        if (roll < 80) return SKY_NONE;
        if (roll < 88) return SKY_STANDARD;
        if (roll < 93) return SKY_PINHOLE;
        if (roll < 95) return SKY_CROSS;
        if (roll < 98) return SKY_BARS;
        return SKY_STANDARD;
    }

    private boolean isInSkylightZone(int skylightType, int localX, int localZ) {
        boolean inInterior = localX >= WALL_THICK && localX < CELL_SIZE - WALL_THICK
                          && localZ >= WALL_THICK && localZ < CELL_SIZE - WALL_THICK;
        return switch (skylightType) {
            case SKY_STANDARD -> localX >= 8 && localX < 16 && localZ >= 8 && localZ < 16;
            case SKY_PINHOLE -> localX >= 10 && localX < 14 && localZ >= 10 && localZ < 14;
            case SKY_CROSS -> inInterior && (localX >= 10 && localX < 14 || localZ >= 10 && localZ < 14);
            case SKY_BARS -> false; // handled separately in ceiling generation
            default -> false;
        };
    }

    // --- Structure placement ---

    private void placeArchBlock(ChunkData chunkData, int x, int z,
                                int localX, int localZ,
                                boolean inWallX, Palette palette, int ceilingY) {
        int doorLocalPos = inWallX ? localZ : localX;
        int distFromEdge = Math.min(doorLocalPos - DOOR_START, DOOR_END - 1 - doorLocalPos);

        if (distFromEdge == 0) {
            chunkData.setBlock(x, ceilingY - 1, z, palette.wall());
            chunkData.setBlock(x, ceilingY - 2, z, palette.wall());
        } else if (distFromEdge == 1) {
            chunkData.setBlock(x, ceilingY - 1, z, palette.wall());
        }
    }

    private void placeInterior(ChunkData chunkData, int x, int z,
                               int localX, int localZ,
                               int masterCellX, int masterCellZ, long seed,
                               Palette palette, Material light,
                               int roomType, int ceilingY, int skylightType) {
        int ix = localX - WALL_THICK;
        int iz = localZ - WALL_THICK;

        if (ix < 0 || ix >= INTERIOR_SIZE || iz < 0 || iz >= INTERIOR_SIZE) {
            return;
        }

        int edX = interiorEdgeDist(ix);
        int edZ = interiorEdgeDist(iz);

        switch (roomType) {
            // --- NORMAL room types (cases 0-9) ---
            case 1 -> { // Pillared: 4 symmetric 2x2 pillars
                boolean onPillarX = symmetricMatch(ix, 5) || symmetricMatch(ix, 4);
                boolean onPillarZ = symmetricMatch(iz, 5) || symmetricMatch(iz, 4);
                if (onPillarX && onPillarZ) {
                    for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, palette.pillar());
                    }
                }
            }
            case 2 -> { // Colonnade: symmetric pillar rows along Z edges
                boolean nearEdgeZ = edZ <= 2;
                boolean onPillar = symmetricMatch(ix, 8) || symmetricMatch(ix, 3);
                if (nearEdgeZ && onPillar) {
                    for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, palette.pillar());
                    }
                }
            }
            case 3 -> { // Gallery: symmetric half-height cross partitions
                boolean onPartX = symmetricMatch(ix, 4) && edZ > 3;
                boolean onPartZ = symmetricMatch(iz, 4) && edX > 3;
                if (onPartX || onPartZ) {
                    for (int y = FLOOR_HEIGHT + 1; y < FLOOR_HEIGHT + 6; y++) {
                        chunkData.setBlock(x, y, z, palette.wall());
                    }
                    chunkData.setBlock(x, FLOOR_HEIGHT + 6, z, palette.pillar());
                }
            }
            case 4 -> { // Light columns: symmetric glass+froglight towers
                boolean onColX = edX == 3 || edX == 7;
                boolean onColZ = edZ == 3 || edZ == 7;
                if (onColX && onColZ) {
                    for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS);
                    }
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, light);
                    chunkData.setBlock(x, (FLOOR_HEIGHT + ceilingY) / 2, z, light);
                    chunkData.setBlock(x, ceilingY - 1, z, light);
                }
            }
            case 5 -> { // Floor inlay: symmetric cross + diamond ring
                int dfc = Math.max(INTERIOR_SIZE / 2 - edX, 1);
                int dfcZ = Math.max(INTERIOR_SIZE / 2 - edZ, 1);
                boolean onCross = (edX >= 9 && edZ >= 4) || (edZ >= 9 && edX >= 4);
                boolean onDiamond = Math.abs(dfc + dfcZ - 3) <= 1 && dfc < 8 && dfcZ < 8;
                if (onCross || onDiamond) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.pillar());
                }
            }
            case 6 -> { // Fountain: center feature + pillar ring + floor accent
                if (edX >= 8 && edZ >= 8) {
                    for (int y = FLOOR_HEIGHT; y < FLOOR_HEIGHT + 5; y++) {
                        chunkData.setBlock(x, y, z, palette.pillar());
                    }
                    chunkData.setBlock(x, FLOOR_HEIGHT + 5, z, light);
                }
                boolean ringX = edX == 4 || edX == 5;
                boolean ringZ = edZ == 4 || edZ == 5;
                if (ringX && ringZ) {
                    for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, palette.pillar());
                    }
                }
                if (edX + edZ == 6 && edX >= 1 && edZ >= 1) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.pillar());
                }
            }
            case 7 -> { // Platform: raised center with corner pillars
                if (edX >= 6 && edZ >= 6) {
                    chunkData.setBlock(x, FLOOR_HEIGHT + 1, z, palette.pillar());
                    if (edX == 6 && edZ == 6) {
                        for (int y = FLOOR_HEIGHT + 1; y < ceilingY; y++) {
                            chunkData.setBlock(x, y, z, palette.pillar());
                        }
                    }
                }
                if ((edX == 5 && edZ >= 5) || (edZ == 5 && edX >= 5)) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.pillar());
                }
            }
            case 8 -> { // Alcoves: pillar-framed niches on all 4 walls
                boolean pillarX = edX == 4 || edX == 5;
                boolean atEdgeZ = edZ <= 3;
                boolean pillarZ = edZ == 4 || edZ == 5;
                boolean atEdgeX = edX <= 3;
                if ((pillarX && atEdgeZ) || (pillarZ && atEdgeX)) {
                    for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, palette.pillar());
                    }
                }
                if ((edX >= 9 && edZ == 0) || (edZ >= 9 && edX == 0)) {
                    chunkData.setBlock(x, FLOOR_HEIGHT + 3, z, light);
                }
            }
            case 9 -> { // Grand: corner pillars + center inlay + perimeter trim
                if (edX >= 2 && edX <= 3 && edZ >= 2 && edZ <= 3) {
                    for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                        chunkData.setBlock(x, y, z, palette.pillar());
                    }
                }
                if (edX >= 6 && edZ >= 6) {
                    if (edX == 6 || edZ == 6 || (edX >= 8 && edZ >= 8)) {
                        chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.pillar());
                    }
                }
                if (edX == 1 && edZ >= 1 || edZ == 1 && edX >= 1) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.pillar());
                }
            }

            // --- SHORT room types (two-level, cases 10-14) ---
            case 10 -> placeShortOpen(chunkData, x, z, ix, iz, edX, edZ, palette, light, skylightType);
            case 11 -> placeShortPartitioned(chunkData, x, z, ix, iz, edX, edZ, palette, light);
            case 12 -> placeShortCorner(chunkData, x, z, ix, iz, edX, edZ, masterCellX, masterCellZ, seed, palette, light);
            case 13 -> placeShortMezzanine(chunkData, x, z, ix, iz, edX, edZ, palette, light);
            case 14 -> placeShortDensePillars(chunkData, x, z, ix, iz, edX, edZ, palette, light);
            case 15 -> placeShortSmallPools(chunkData, x, z, ix, iz, edX, edZ, palette, light);

            // --- TALL room types (cathedral, cases 20-23) ---
            case 20 -> placeTallCathedral(chunkData, x, z, ix, iz, edX, edZ, ceilingY, palette, light);
            case 21 -> placeTallAtrium(chunkData, x, z, ix, iz, edX, edZ, ceilingY, palette, light);
            case 22 -> placeTallColumns(chunkData, x, z, ix, iz, edX, edZ, ceilingY, palette, light);
            case 23 -> placeTallLayered(chunkData, x, z, ix, iz, edX, edZ, ceilingY, palette, light);

            // --- WALLS variants (normal height) ---
            case 30 -> placeWallsGap(chunkData, x, z, ix, iz, edX, edZ, ceilingY, palette);
            case 31 -> placeWallsSolid(chunkData, x, z, ix, iz, edX, edZ, ceilingY, palette);
            case 32 -> placeWallsCross(chunkData, x, z, ix, iz, edX, edZ, ceilingY, palette);

            // case 0 (open): nothing
        }
    }

    // --- SHORT: two-level rooms ---

    private void placeMidFloor(ChunkData chunkData, int x, int z, Palette palette) {
        chunkData.setBlock(x, MID_FLOOR_Y, z, palette.floor());
    }

    /** Case 10: open two-level with center void and symmetric staircases */
    private void placeShortOpen(ChunkData chunkData, int x, int z,
                                int ix, int iz, int edX, int edZ,
                                Palette palette, Material light, int skylightType) {
        boolean centerOpening = edX >= 6 && edZ >= 6;
        // West staircase: ix=0-2, iz=2-7 ascending
        boolean westStair = ix <= 2 && iz >= 2 && iz <= 7;
        // East staircase: ix=17-19, iz=12-17 ascending (180° rotational symmetry)
        boolean eastStair = ix >= 17 && iz >= 12 && iz <= 17;

        if (centerOpening) {
            if (edX == 6 && edZ == 6) {
                int pillarTop = skylightType != SKY_NONE ? MID_FLOOR_Y : CEILING_Y;
                for (int y = FLOOR_HEIGHT; y < pillarTop; y++) {
                    chunkData.setBlock(x, y, z, palette.pillar());
                }
            }
        } else if (westStair) {
            int stepY = FLOOR_HEIGHT + 1 + (iz - 2) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        } else if (eastStair) {
            int stepY = FLOOR_HEIGHT + 1 + (17 - iz) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        } else {
            placeMidFloor(chunkData, x, z, palette);
            if (edX % 5 == 0 && edZ % 5 == 0) {
                chunkData.setBlock(x, MID_FLOOR_Y, z, light);
            }
        }
    }

    /** Case 11: partitioned lower level with stairwells */
    private void placeShortPartitioned(ChunkData chunkData, int x, int z,
                                       int ix, int iz, int edX, int edZ,
                                       Palette palette, Material light) {
        boolean northStair = ix >= 8 && ix <= 11 && iz <= 5;
        boolean southStair = ix >= 8 && ix <= 11 && iz >= 14;

        if (northStair) {
            int stepY = FLOOR_HEIGHT + 1 + iz * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        } else if (southStair) {
            int stepY = FLOOR_HEIGHT + 1 + (19 - iz) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 5;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        } else {
            placeMidFloor(chunkData, x, z, palette);
            boolean onPartition = symmetricMatch(ix, 5) || symmetricMatch(ix, 0);
            if (onPartition && iz > 5 && iz < 14) {
                for (int y = FLOOR_HEIGHT + 1; y < MID_FLOOR_Y - 1; y++) {
                    chunkData.setBlock(x, y, z, palette.wall());
                }
            }
            if (edX % 4 == 0 && edZ % 4 == 0) {
                chunkData.setBlock(x, MID_FLOOR_Y - 1, z, light);
            }
        }
    }

    /** Case 12: L-shaped corner staircase.
     *  Two legs along walls, each 2 blocks wide. Corner chosen per-cell. */
    private void placeShortCorner(ChunkData chunkData, int x, int z,
                                  int ix, int iz, int edX, int edZ,
                                  int cellX, int cellZ, long seed,
                                  Palette palette, Material light) {
        Random rng = new Random(seed ^ ((long) cellX * 111111L + (long) cellZ * 222222L));
        int corner = rng.nextInt(4);

        // Transform coordinates so corner 0 (NW) logic applies to all corners
        int lx, lz;
        switch (corner) {
            case 1 -> { lx = 19 - ix; lz = iz; }       // NE: mirror X
            case 2 -> { lx = 19 - ix; lz = 19 - iz; }  // SE: mirror both
            case 3 -> { lx = ix; lz = 19 - iz; }       // SW: mirror Z
            default -> { lx = ix; lz = iz; }            // NW: identity
        }

        // Leg 1: along wall, lx=0-1, lz=0-4, ascending (5 steps: y=9,10,11,12,13)
        boolean leg1 = lx <= 1 && lz >= 0 && lz <= 4;
        // Leg 2: turns corner, lx=0-4, lz=4-5, ascending (5 steps: y=10,11,12,13,14)
        boolean leg2 = lz >= 4 && lz <= 5 && lx >= 0 && lx <= 4 && !leg1;

        if (leg1) {
            int stepY = FLOOR_HEIGHT + 1 + lz;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        } else if (leg2) {
            int stepY = FLOOR_HEIGHT + 1 + 4 + (lx - 1);  // continues from leg1 top
            stepY = Math.min(stepY, MID_FLOOR_Y);
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        } else {
            placeMidFloor(chunkData, x, z, palette);
            if (edX >= 8 && edZ >= 8) {
                for (int y = MID_FLOOR_Y + 1; y < CEILING_Y; y++) {
                    chunkData.setBlock(x, y, z, palette.pillar());
                }
                chunkData.setBlock(x, MID_FLOOR_Y + 1, z, light);
                chunkData.setBlock(x, CEILING_Y - 1, z, light);
            }
        }
    }

    /** Case 13: mezzanine ring — perimeter balcony, open center.
     *  Staircases descend from ring inner edge into the center. */
    private void placeShortMezzanine(ChunkData chunkData, int x, int z,
                                     int ix, int iz, int edX, int edZ,
                                     Palette palette, Material light) {
        boolean onRing = edX <= 3 || edZ <= 3;
        // Staircases: 2 blocks wide at center, descend from ring edge (iz/ix=3 or 16) into center
        boolean northStair = iz >= 3 && iz <= 7 && (ix == 9 || ix == 10);
        boolean southStair = iz >= 12 && iz <= 16 && (ix == 9 || ix == 10);
        boolean westStair = ix >= 3 && ix <= 7 && (iz == 9 || iz == 10);
        boolean eastStair = ix >= 12 && ix <= 16 && (iz == 9 || iz == 10);

        if (northStair) {
            int stepY = MID_FLOOR_Y - (iz - 3) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 4;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        } else if (southStair) {
            int stepY = MID_FLOOR_Y - (16 - iz) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 4;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        } else if (westStair) {
            int stepY = MID_FLOOR_Y - (ix - 3) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 4;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        } else if (eastStair) {
            int stepY = MID_FLOOR_Y - (16 - ix) * (MID_FLOOR_Y - FLOOR_HEIGHT - 1) / 4;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        } else if (onRing) {
            placeMidFloor(chunkData, x, z, palette);
            if ((edX == 3 && edZ > 3) || (edZ == 3 && edX > 3)) {
                chunkData.setBlock(x, MID_FLOOR_Y + 1, z, palette.wall());
            }
        }
        if (edX >= 8 && edZ >= 8) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, light);
        }
    }

    /** Case 14: dense pillars with 2-wide central spiral staircase.
     *  Outer ring (ix 6-13, iz 6-13) and inner ring (ix 7-12, iz 7-12). */
    private void placeShortDensePillars(ChunkData chunkData, int x, int z,
                                        int ix, int iz, int edX, int edZ,
                                        Palette palette, Material light) {
        // Outer ring progress (28 positions, clockwise from south)
        int outerProgress = -1;
        if (iz == 13 && ix >= 6 && ix <= 13) { outerProgress = ix - 6; }             // S: 0-7
        else if (ix == 13 && iz >= 6 && iz <= 12) { outerProgress = 20 - iz; }       // E: 8-14
        else if (iz == 6 && ix >= 6 && ix <= 12) { outerProgress = 21 + (12 - ix); } // N: 15-21
        else if (ix == 6 && iz >= 7 && iz <= 12) { outerProgress = 22 + (iz - 7); }  // W: 22-27

        // Inner ring progress (20 positions, clockwise from south)
        int innerProgress = -1;
        if (iz == 12 && ix >= 7 && ix <= 12) { innerProgress = ix - 7; }             // S: 0-5
        else if (ix == 12 && iz >= 7 && iz <= 11) { innerProgress = 17 - iz; }       // E: 6-10
        else if (iz == 7 && ix >= 7 && ix <= 11) { innerProgress = 22 - ix; }        // N: 11-15
        else if (ix == 7 && iz >= 8 && iz <= 11) { innerProgress = iz + 8; }         // W: 16-19

        boolean onSpiral = outerProgress >= 0 || innerProgress >= 0;
        if (onSpiral) {
            int progress;
            if (outerProgress >= 0) {
                progress = outerProgress;
            } else {
                // Map inner ring progress to outer ring scale
                progress = innerProgress * 28 / 20;
            }
            int stepY = FLOOR_HEIGHT + progress * (MID_FLOOR_Y - FLOOR_HEIGHT) / 28;
            for (int y = FLOOR_HEIGHT + 1; y <= stepY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        } else {
            // Everything else gets mid-floor
            boolean insideSpiral = ix >= 8 && ix <= 11 && iz >= 8 && iz <= 11;
            if (!insideSpiral) {
                placeMidFloor(chunkData, x, z, palette);
            }
            // Pillars on both levels outside spiral zone
            if (edX % 4 == 1 && edZ % 4 == 1 && !insideSpiral) {
                for (int y = FLOOR_HEIGHT; y < MID_FLOOR_Y; y++) {
                    chunkData.setBlock(x, y, z, palette.pillar());
                }
                for (int y = MID_FLOOR_Y; y < CEILING_Y; y++) {
                    chunkData.setBlock(x, y, z, palette.pillar());
                }
            }
            if (edX % 4 == 3 && edZ % 4 == 3 && !insideSpiral) {
                chunkData.setBlock(x, FLOOR_HEIGHT + 4, z, light);
                chunkData.setBlock(x, MID_FLOOR_Y, z, light);
            }
        }
    }

    /** Case 15: small pools — 4 symmetric pools on lower level, mezzanine ring (no stairs) */
    private void placeShortSmallPools(ChunkData chunkData, int x, int z,
                                      int ix, int iz, int edX, int edZ,
                                      Palette palette, Material light) {
        boolean onRing = edX <= 3 || edZ <= 3;
        // 4 symmetric pools: NW(4-7,4-7), NE(12-15,4-7), SW(4-7,12-15), SE(12-15,12-15)
        boolean inPool = (ix >= 4 && ix <= 7 || ix >= 12 && ix <= 15)
                      && (iz >= 4 && iz <= 7 || iz >= 12 && iz <= 15);

        if (onRing) {
            placeMidFloor(chunkData, x, z, palette);
            // Trim wall at ring inner edge
            if ((edX == 3 && edZ > 3) || (edZ == 3 && edX > 3)) {
                chunkData.setBlock(x, MID_FLOOR_Y + 1, z, palette.wall());
            }
        }
        if (inPool && !onRing) {
            // Shallow pool on lower level
            chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
            chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, palette.floor());
        }
        // Single center light
        if (edX == 9 && edZ == 9) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, light);
        }
    }

    // --- TALL: cathedral rooms ---

    /** Case 20: grand cathedral — massive pillars + floor inlay */
    private void placeTallCathedral(ChunkData chunkData, int x, int z,
                                    int ix, int iz, int edX, int edZ, int ceilingY,
                                    Palette palette, Material light) {
        if (edX >= 4 && edX <= 6 && edZ >= 4 && edZ <= 6) {
            for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, palette.pillar());
            }
            int midY = (FLOOR_HEIGHT + ceilingY) / 2;
            chunkData.setBlock(x, midY, z, light);
            chunkData.setBlock(x, FLOOR_HEIGHT + 3, z, light);
            chunkData.setBlock(x, ceilingY - 2, z, light);
        }
        if (edX >= 7 && edZ >= 7) {
            if (edX == 7 || edZ == 7 || (edX >= 9 && edZ >= 9)) {
                chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.pillar());
            }
        }
        if ((edX == 1 && edZ >= 1) || (edZ == 1 && edX >= 1)) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.pillar());
        }
    }

    /** Case 21: atrium — outer solid pillars, inner glass pillars */
    private void placeTallAtrium(ChunkData chunkData, int x, int z,
                                 int ix, int iz, int edX, int edZ, int ceilingY,
                                 Palette palette, Material light) {
        boolean outerPillar = edX == 5 && edZ == 5;
        boolean innerPillar = (edX == 9 && edZ == 5) || (edX == 5 && edZ == 9);

        if (outerPillar) {
            for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, palette.pillar());
            }
            chunkData.setBlock(x, (FLOOR_HEIGHT + ceilingY) / 2, z, light);
            chunkData.setBlock(x, ceilingY - 3, z, light);
        } else if (innerPillar) {
            for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS);
            }
            chunkData.setBlock(x, (FLOOR_HEIGHT + ceilingY) / 2, z, light);
            chunkData.setBlock(x, ceilingY - 3, z, light);
        }
        if (edX + edZ == 8 && edX >= 2 && edZ >= 2) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.pillar());
        }
    }

    /** Case 22: towering columns — alternating solid and glass */
    private void placeTallColumns(ChunkData chunkData, int x, int z,
                                  int ix, int iz, int edX, int edZ, int ceilingY,
                                  Palette palette, Material light) {
        boolean colX = symmetricMatch(ix, 3) || symmetricMatch(ix, 7);
        boolean colZ = symmetricMatch(iz, 3) || symmetricMatch(iz, 7);
        if (colX && colZ) {
            boolean isGlassCol = (edX == 3 || edZ == 3) && !(edX == 7 && edZ == 7);
            Material colMat = isGlassCol ? Material.LIGHT_BLUE_STAINED_GLASS : palette.pillar();
            for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, colMat);
            }
            chunkData.setBlock(x, (FLOOR_HEIGHT + ceilingY) / 2, z, light);
        }
        if (edX == 5 && edZ == 5) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, light);
        }
    }

    /** Case 23: layered — trim bands + corner pillars */
    private void placeTallLayered(ChunkData chunkData, int x, int z,
                                  int ix, int iz, int edX, int edZ, int ceilingY,
                                  Palette palette, Material light) {
        if (edX >= 2 && edX <= 3 && edZ >= 2 && edZ <= 3) {
            for (int y = FLOOR_HEIGHT; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, palette.pillar());
            }
        }
        if (edX <= 2 || edZ <= 2) {
            int bandY1 = FLOOR_HEIGHT + (ceilingY - FLOOR_HEIGHT) / 3;
            int bandY2 = FLOOR_HEIGHT + 2 * (ceilingY - FLOOR_HEIGHT) / 3;
            chunkData.setBlock(x, bandY1, z, palette.wall());
            chunkData.setBlock(x, bandY2, z, palette.wall());
            if (edX % 4 == 0 && edZ % 4 == 0) {
                chunkData.setBlock(x, bandY1, z, light);
                chunkData.setBlock(x, bandY2, z, light);
            }
        }
        if (edX >= 6 && edZ >= 6) {
            chunkData.setBlock(x, FLOOR_HEIGHT, z, palette.pillar());
        }
    }

    // --- WALLS: corridor-like subdivisions ---

    /** Case 30: parallel walls along Z axis with center gap */
    private void placeWallsGap(ChunkData chunkData, int x, int z,
                               int ix, int iz, int edX, int edZ, int ceilingY,
                               Palette palette) {
        boolean onWall = symmetricMatch(ix, 3) || symmetricMatch(ix, 7);
        if (onWall && edZ < 8) {
            for (int y = FLOOR_HEIGHT + 1; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        }
    }

    /** Case 31: parallel walls along X axis, no gap */
    private void placeWallsSolid(ChunkData chunkData, int x, int z,
                                 int ix, int iz, int edX, int edZ, int ceilingY,
                                 Palette palette) {
        boolean onWall = symmetricMatch(iz, 4) || symmetricMatch(iz, 8);
        if (onWall) {
            for (int y = FLOOR_HEIGHT + 1; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        }
    }

    /** Case 32: cross grid — X and Z walls creating sub-rooms with edge doorways */
    private void placeWallsCross(ChunkData chunkData, int x, int z,
                                 int ix, int iz, int edX, int edZ, int ceilingY,
                                 Palette palette) {
        boolean wallX = symmetricMatch(ix, 5);
        boolean wallZ = symmetricMatch(iz, 5);
        // Doorway gaps near room edges
        boolean gapX = edZ <= 2;
        boolean gapZ = edX <= 2;
        if ((wallX && !gapX) || (wallZ && !gapZ)) {
            for (int y = FLOOR_HEIGHT + 1; y < ceilingY; y++) {
                chunkData.setBlock(x, y, z, palette.wall());
            }
        }
    }

    private void placePool(ChunkData chunkData, int x, int z,
                           int localX, int localZ,
                           int masterCellX, int masterCellZ, long seed,
                           Palette palette) {
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
        if (currentFloor != palette.floor()) {
            return;
        }

        int ix = localX - WALL_THICK;
        int iz = localZ - WALL_THICK;
        int distFromPoolEdge = Math.min(
                Math.min(localX - interiorStart, interiorEnd - 1 - localX),
                Math.min(localZ - interiorStart, interiorEnd - 1 - localZ));

        switch (poolType) {
            case POOL_SHALLOW -> {
                chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
                chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, palette.floor());
            }
            case POOL_DEEP -> {
                if (distFromPoolEdge <= 1) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
                    chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, palette.floor());
                } else {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
                    chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.WATER);
                    chunkData.setBlock(x, FLOOR_HEIGHT - 2, z, Material.WATER);
                    chunkData.setBlock(x, FLOOR_HEIGHT - 3, z, palette.floor());
                }
            }
            case POOL_CHANNEL -> {
                // 4-wide centered water strip along Z axis
                boolean inStrip = symmetricMatch(ix, 0) || symmetricMatch(ix, 1);
                if (inStrip) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
                    chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, palette.floor());
                }
            }
            case POOL_MOAT -> {
                // 3-block-wide perimeter water ring, dry center island
                int px = localX - interiorStart;
                int pz = localZ - interiorStart;
                int poolZoneSize = interiorEnd - interiorStart; // 16
                int poolEdgeDist = Math.min(Math.min(px, poolZoneSize - 1 - px),
                                            Math.min(pz, poolZoneSize - 1 - pz));
                if (poolEdgeDist < 3) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
                    chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, palette.floor());
                }
            }
            case POOL_HALF -> {
                // One half wet, one dry — axis chosen per cell
                int halfAxis = (int) Math.floorMod(
                        seed ^ ((long) masterCellX * 159265L + (long) masterCellZ * 358979L), 2);
                boolean inWetHalf = halfAxis == 0 ? ix < INTERIOR_SIZE / 2 : iz < INTERIOR_SIZE / 2;
                if (inWetHalf) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
                    chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, palette.floor());
                }
            }
            case POOL_ABYSS -> {
                // Extra-deep stepped pool (6 blocks at center)
                if (distFromPoolEdge <= 1) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
                    chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, palette.floor());
                } else if (distFromPoolEdge == 2) {
                    for (int y = FLOOR_HEIGHT; y > FLOOR_HEIGHT - 2; y--) {
                        chunkData.setBlock(x, y, z, Material.WATER);
                    }
                    chunkData.setBlock(x, FLOOR_HEIGHT - 2, z, palette.floor());
                } else {
                    for (int y = FLOOR_HEIGHT; y > FLOOR_HEIGHT - 6; y--) {
                        chunkData.setBlock(x, y, z, Material.WATER);
                    }
                    chunkData.setBlock(x, FLOOR_HEIGHT - 6, z, palette.floor());
                }
            }
            case POOL_ABYSS_DEEP -> {
                // Rare extra-extra-deep pool (7 blocks at center)
                if (distFromPoolEdge <= 1) {
                    chunkData.setBlock(x, FLOOR_HEIGHT, z, Material.WATER);
                    chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, palette.floor());
                } else if (distFromPoolEdge == 2) {
                    for (int y = FLOOR_HEIGHT; y > FLOOR_HEIGHT - 3; y--) {
                        chunkData.setBlock(x, y, z, Material.WATER);
                    }
                    chunkData.setBlock(x, FLOOR_HEIGHT - 3, z, palette.floor());
                } else {
                    for (int y = FLOOR_HEIGHT; y > FLOOR_HEIGHT - 7; y--) {
                        chunkData.setBlock(x, y, z, Material.WATER);
                    }
                    chunkData.setBlock(x, FLOOR_HEIGHT - 7, z, palette.floor());
                }
            }
        }
    }

    // --- Debug info ---

    private static final String[] ROOM_TYPE_NAMES = {
        "Open", "Pillared", "Colonnade", "Gallery", "Light Columns",
        "Floor Inlay", "Fountain", "Platform", "Alcoves", "Grand",
        "Short: Open Two-Level", "Short: Partitioned", "Short: Corner Stair",
        "Short: Mezzanine Ring", "Short: Dense Pillars", "Short: Small Pools",
        null, null, null, null,
        "Tall: Cathedral", "Tall: Atrium", "Tall: Towering Columns", "Tall: Layered",
        null, null, null, null, null, null,
        "Walls: Gap", "Walls: Solid", "Walls: Cross"
    };

    private static final String[] CLASS_NAMES = { "SHORT", "NORMAL", "TALL" };
    private static final String[] SKYLIGHT_TYPE_NAMES = { "None", "Standard", "Pinhole", "Cross", "Bars" };
    private static final String[] POOL_TYPE_NAMES = { "Dry", "Shallow", "Deep", "Channel", "Moat", "Half", "Abyss", "Deep Abyss" };

    public static String getDebugInfo(int worldX, int worldZ, long seed) {
        int cellX = Math.floorDiv(worldX, CELL_SIZE);
        int cellZ = Math.floorDiv(worldZ, CELL_SIZE);

        // Room class
        double n = SimplexNoise.noise2(seed + 999, cellX * 0.15, cellZ * 0.15);
        int roomClass;
        if (n < -0.3) roomClass = CLASS_SHORT;
        else if (n > 0.3) roomClass = CLASS_TALL;
        else roomClass = CLASS_NORMAL;

        // Palette
        int regionX = Math.floorDiv(worldX, REGION_SIZE);
        int regionZ = Math.floorDiv(worldZ, REGION_SIZE);
        long regionSeed = seed ^ (regionX * 198491317L + regionZ * 6542989L);
        int paletteIndex = (int) Math.floorMod(regionSeed, DEFAULT_PALETTES.length);

        // Master cell for room type
        int cx = cellX;
        for (int i = 0; i < 3; i++) {
            long h = seed ^ ((long) Math.min(cx - 1, cx) * 735632791L + (long) Math.max(cx - 1, cx) * 524287L
                    + (long) Math.min(cellZ, cellZ) * 433494437L + (long) Math.max(cellZ, cellZ) * 291371L);
            if (Math.floorMod(h, 100) < 25) cx--;
            else break;
        }
        int cz = cellZ;
        for (int i = 0; i < 3; i++) {
            long h = seed ^ ((long) Math.min(cellX, cellX) * 735632791L + (long) Math.max(cellX, cellX) * 524287L
                    + (long) Math.min(cz - 1, cz) * 433494437L + (long) Math.max(cz - 1, cz) * 291371L);
            if (Math.floorMod(h, 100) < 25) cz--;
            else break;
        }

        Random rng = new Random(seed ^ ((long) cx * 982451653L + (long) cz * 472882049L));
        int roll = rng.nextInt(100);
        int roomType;
        switch (roomClass) {
            case CLASS_SHORT:
                if (roll < 20) roomType = 10;
                else if (roll < 37) roomType = 11;
                else if (roll < 52) roomType = 12;
                else if (roll < 67) roomType = 13;
                else if (roll < 82) roomType = 14;
                else roomType = 15;
                break;
            case CLASS_TALL:
                if (roll < 30) roomType = 20;
                else if (roll < 55) roomType = 21;
                else if (roll < 80) roomType = 22;
                else roomType = 23;
                break;
            default:
                if (roll < 10) roomType = 0;
                else if (roll < 20) roomType = 1;
                else if (roll < 29) roomType = 2;
                else if (roll < 37) roomType = 3;
                else if (roll < 44) roomType = 4;
                else if (roll < 51) roomType = 5;
                else if (roll < 57) roomType = 6;
                else if (roll < 63) roomType = 7;
                else if (roll < 68) roomType = 8;
                else if (roll < 73) roomType = 9;
                else if (roll < 82) roomType = 30;
                else if (roll < 91) roomType = 31;
                else roomType = 32;
                break;
        }

        // Skylight type (same RNG logic as getSkylightType)
        Random skyRng = new Random(seed ^ ((long) cellX * 982451653L + (long) cellZ * 472882049L));
        skyRng.nextInt(); skyRng.nextInt();
        int skyRoll = skyRng.nextInt(100);
        int skylightType;
        if (skyRoll < 80) skylightType = SKY_NONE;
        else if (skyRoll < 88) skylightType = SKY_STANDARD;
        else if (skyRoll < 93) skylightType = SKY_PINHOLE;
        else if (skyRoll < 95) skylightType = SKY_CROSS;
        else if (skyRoll < 98) skylightType = SKY_BARS;
        else skylightType = SKY_STANDARD;

        // Pool type (same RNG logic as getPoolType, uses master cell)
        Random poolRng = new Random(seed ^ ((long) cx * 314159265L + (long) cz * 271828182L));
        int poolRoll = poolRng.nextInt(100);
        int poolType;
        if (poolRoll < 20) poolType = POOL_DRY;
        else if (poolRoll < 44) poolType = POOL_SHALLOW;
        else if (poolRoll < 57) poolType = POOL_DEEP;
        else if (poolRoll < 69) poolType = POOL_CHANNEL;
        else if (poolRoll < 79) poolType = POOL_MOAT;
        else if (poolRoll < 88) poolType = POOL_HALF;
        else if (poolRoll < 97) poolType = POOL_ABYSS;
        else poolType = POOL_ABYSS_DEEP;

        String className = CLASS_NAMES[roomClass];
        String paletteName = DEFAULT_PALETTES[paletteIndex].name();
        String typeName = (roomType >= 0 && roomType < ROOM_TYPE_NAMES.length && ROOM_TYPE_NAMES[roomType] != null)
                ? ROOM_TYPE_NAMES[roomType] : "Unknown(" + roomType + ")";
        String skylightName = SKYLIGHT_TYPE_NAMES[skylightType];
        String poolName = POOL_TYPE_NAMES[poolType];

        return "§e" + className + " §7| §b" + paletteName + " §7| §a" + typeName
                + " §7| §dSky:" + skylightName + " §7| §9Pool:" + poolName;
    }

    @Override
    public int getSpawnY() {
        return FLOOR_HEIGHT + 2;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, FLOOR_HEIGHT + 2, 8.5);
    }
}
