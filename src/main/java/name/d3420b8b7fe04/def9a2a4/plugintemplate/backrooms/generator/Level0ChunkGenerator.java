package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

import static name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.PatchPalette.entry;

/**
 * Level 0, the Lobby. Needs a world of y -64..320 (dimension type
 * backrooms:lobby, NORMAL environment): 4-high rooms at y65-68 over a floor
 * slab reaching down to the void, so pits can fall all the way through.
 *
 * Floors, ceilings and walls each take their material from an independent
 * {@link PatchPalette}; near spawn everything stays classic yellow terracotta.
 * Rarer features: damp mud / soul sand under carpet, tall halls (10-32 high,
 * lit by pendant lights) that sometimes hold a Level 101 structure on a patch
 * of grass, and bottomless pits ringed by a spiral staircase that just stops.
 */
public class Level0ChunkGenerator extends BackroomsChunkGenerator {

    public Level0ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    private static final int WORLD_MIN_Y = -64;
    // Floor top sits at Level 101's ground height so its structures drop into the halls unchanged.
    private static final int FLOOR_TOP_Y = Level101ChunkGenerator.GROUND_Y;
    public static final int AIR_MIN_Y = FLOOR_TOP_Y + 1;
    private static final int AIR_MAX_Y = AIR_MIN_Y + 4;
    public static final int CEILING_MIN_Y = AIR_MAX_Y;
    private static final int CEILING_MAX_Y = 110;
    private static final int BEDROCK_LAYERS = 10;

    private static final int CELL_SIZE = 4;
    private static final int CELLS_PER_AXIS = 16 / CELL_SIZE;
    public static final int LIGHT_SPACING = 4;

    private static final double REGION_SCALE = 1.0 / 64.0;
    private static final double CARPET_SCALE = 1.0 / 340.0;
    private static final double DAMP_SCALE = 1.0 / 28.0;
    private static final double HALL_SCALE = 1.0 / 200.0;
    private static final double HALL_HEIGHT_SCALE = 1.0 / 90.0;
    private static final double CLASSIC_EDGE_SCALE = 1.0 / 60.0;
    private static final double NOISE_OFFSET = 100000.0;
    private static final double SPAWN_PLAIN_RADIUS = 200.0;

    private static final double DAMP_MUD = 0.45;
    private static final double DAMP_SOUL_SAND = 0.75;

    private static final int HALL_MIN_HEIGHT = 10;
    private static final int HALL_MAX_HEIGHT = 32;
    // Two-story house roof is base+8; leave headroom.
    private static final int HALL_STRUCTURE_MIN_HEIGHT = 12;

    private static final int REGION_EMPTY = 0;
    private static final int REGION_WALLS = 1;
    private static final int REGION_PILLARS = 2;

    // Pit: 5x5 shaft centered in the chunk, middle 2x2 cells cleared around it.
    private static final int PIT_MIN = 6;
    private static final int PIT_MAX = 10;
    private static final int PIT_CLEAR_MIN = 4;
    private static final int PIT_CLEAR_MAX = 11;
    private static final Material[][] PIT_STAIR_SETS = {
            { Material.SMOOTH_SANDSTONE, Material.SMOOTH_SANDSTONE_STAIRS },
            { Material.SMOOTH_RED_SANDSTONE, Material.SMOOTH_RED_SANDSTONE_STAIRS },
            { Material.OAK_PLANKS, Material.OAK_STAIRS },
            { Material.POLISHED_ANDESITE, Material.POLISHED_ANDESITE_STAIRS },
    };

    private static final PatchPalette FLOOR_PALETTE = new PatchPalette(0xF100L, 64,
            entry(Material.YELLOW_TERRACOTTA, 6),
            entry(Material.YELLOW_CONCRETE, 3),
            entry(Material.SMOOTH_SANDSTONE, 3),
            entry(Material.SMOOTH_RED_SANDSTONE, 2),
            entry(Material.STRIPPED_JUNGLE_WOOD, 2),
            entry(Material.STRIPPED_OAK_WOOD, 2),
            entry(Material.STRIPPED_BIRCH_WOOD, 2),
            entry(Material.STRIPPED_ACACIA_WOOD, 2),
            entry(Material.STRIPPED_MANGROVE_WOOD, 2));

    // Polished deepslate is ~1% of ceiling patches.
    private static final PatchPalette CEILING_PALETTE = new PatchPalette(0xCE11L, 80,
            entry(Material.YELLOW_TERRACOTTA, 6),
            entry(Material.YELLOW_CONCRETE, 3),
            entry(Material.SMOOTH_SANDSTONE, 3),
            entry(Material.SMOOTH_RED_SANDSTONE, 3),
            entry(Material.STRIPPED_JUNGLE_WOOD, 3),
            entry(Material.STRIPPED_OAK_WOOD, 3),
            entry(Material.STRIPPED_BIRCH_WOOD, 3),
            entry(Material.STRIPPED_ACACIA_WOOD, 3),
            entry(Material.STRIPPED_MANGROVE_WOOD, 3),
            entry(Material.SMOOTH_STONE, 30),
            entry(Material.POLISHED_DIORITE, 10),
            entry(Material.POLISHED_ANDESITE, 10),
            entry(Material.POLISHED_GRANITE, 10),
            entry(Material.POLISHED_DEEPSLATE, 1));

    private static final PatchPalette WALL_PALETTE = new PatchPalette(0xA11L, 56,
            entry(Material.YELLOW_TERRACOTTA, 6),
            entry(Material.YELLOW_CONCRETE, 2),
            entry(Material.SMOOTH_SANDSTONE, 2),
            entry(Material.SMOOTH_RED_SANDSTONE, 2),
            entry(Material.STRIPPED_JUNGLE_WOOD, 2),
            entry(Material.STRIPPED_OAK_WOOD, 2),
            entry(Material.STRIPPED_BIRCH_WOOD, 2),
            entry(Material.STRIPPED_ACACIA_WOOD, 2),
            entry(Material.STRIPPED_MANGROVE_WOOD, 2),
            entry(Material.BAMBOO_PLANKS, 6),
            entry(Material.STRIPPED_BAMBOO_BLOCK, 3),
            entry(Material.END_STONE_BRICKS, 3));

    // Only used for its structure templates; never generates terrain itself.
    private final Level101ChunkGenerator alphaStructures = new Level101ChunkGenerator(null);

    // Tunables (generator_config)
    private double hallThreshold = 0.55;      // hall-mask noise cutoff; higher = rarer halls
    private int hallStructureChance = 30;     // percent of eligible all-hall chunks
    private double pitChunkChance = 0.8;      // percent of chunks with a pit

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config == null) return;
        hallThreshold = config.getDouble("hall_threshold", hallThreshold);
        hallStructureChance = config.getInt("hall_structure_chance", hallStructureChance);
        pitChunkChance = config.getDouble("pit_chunk_chance", pitChunkChance);
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        chunkData.setRegion(0, WORLD_MIN_Y, 0, 16, FLOOR_TOP_Y + 1, 16, Material.YELLOW_TERRACOTTA);
        chunkData.setRegion(0, CEILING_MIN_Y, 0, 16, CEILING_MAX_Y, 16, Material.YELLOW_TERRACOTTA);

        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // --- Per-column layout: classic spawn area, hall heights, surface palettes ---
        boolean[][] classic = new boolean[16][16];
        int[][] hallHeight = new int[16][16];   // 0 = ordinary 4-high room
        Material[][] wallMats = new Material[16][16];
        boolean allHall = true, anyClassic = false;
        int minHallHeight = Integer.MAX_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                double spawnBias = spawnBias(worldX, worldZ);
                classic[x][z] = spawnBias
                        + 0.25 * SimplexNoise.noise2(seed + 500, worldX * CLASSIC_EDGE_SCALE, worldZ * CLASSIC_EDGE_SCALE)
                        > 0.5;
                anyClassic |= classic[x][z];
                hallHeight[x][z] = hallHeight(seed, worldX, worldZ, spawnBias);
                if (hallHeight[x][z] == 0) {
                    allHall = false;
                } else {
                    minHallHeight = Math.min(minHallHeight, hallHeight[x][z]);
                }

                chunkData.setBlock(x, FLOOR_TOP_Y, z, classic[x][z]
                        ? Material.YELLOW_TERRACOTTA : FLOOR_PALETTE.pick(seed, worldX, worldZ));
                chunkData.setBlock(x, CEILING_MIN_Y, z, classic[x][z]
                        ? Material.YELLOW_TERRACOTTA : CEILING_PALETTE.pick(seed, worldX, worldZ));
                wallMats[x][z] = classic[x][z]
                        ? Material.YELLOW_TERRACOTTA : WALL_PALETTE.pick(seed, worldX, worldZ);
            }
        }

        // --- Cell structures ---
        for (int cx = 0; cx < CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < CELLS_PER_AXIS; cz++) {
                int baseX = cx * CELL_SIZE;
                int baseZ = cz * CELL_SIZE;

                double rawCenterX = chunkX * 16 + baseX + CELL_SIZE / 2.0;
                double rawCenterZ = chunkZ * 16 + baseZ + CELL_SIZE / 2.0;

                double regionNoise = SimplexNoise.noise2(seed, rawCenterX * REGION_SCALE, rawCenterZ * REGION_SCALE);
                int region = classifyRegion(regionNoise);

                placeStructure(chunkData, chunkRng, baseX, baseZ, region, wallMats);

                // Rare bookshelf alcove: the door to the Library (break_block exit
                // in level_0.yml). Hashed on world position, not chunkRng, so it
                // never shifts existing structure layouts; the froglight above
                // makes it readable down a corridor. LobbyBookshelfListener skips
                // stacked shelves, so the column survives chiseling.
                if (region == REGION_WALLS
                        && isLibraryAlcove(seed, chunkX * 16 + baseX, chunkZ * 16 + baseZ)) {
                    int ax = baseX + 1, az = baseZ + 1;
                    chunkData.setBlock(ax, AIR_MIN_Y, az, Material.BOOKSHELF);
                    chunkData.setBlock(ax, AIR_MIN_Y + 1, az, Material.BOOKSHELF);
                    chunkData.setBlock(ax, AIR_MIN_Y + 2, az, Material.OCHRE_FROGLIGHT);
                }
            }
        }

        // --- Tall halls: open floor, raised ceiling ---
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int h = hallHeight[x][z];
                if (h == 0) continue;
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                for (int y = AIR_MIN_Y; y < AIR_MIN_Y + h; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }
                chunkData.setBlock(x, AIR_MIN_Y + h, z, CEILING_PALETTE.pick(seed, worldX, worldZ));
            }
        }

        // --- Bottomless pit, away from halls and spawn ---
        long pitHash = chunkHash(seed, chunkX, chunkZ, 0x9177L);
        boolean hasPit = !anyClassic
                && Math.floorMod(pitHash, 10000L) < Math.round(pitChunkChance * 100)
                && noHallIn(hallHeight, PIT_CLEAR_MIN, PIT_CLEAR_MAX);
        if (hasPit) {
            placePit(chunkData, pitHash);
        }

        // --- Level 101 structure on a patch of grass, in roomy all-hall chunks ---
        long hallStructureHash = chunkHash(seed, chunkX, chunkZ, 0xA1FAL);
        boolean hasHallStructure = allHall && !anyClassic
                && minHallHeight >= HALL_STRUCTURE_MIN_HEIGHT
                && Math.floorMod(hallStructureHash, 100L) < hallStructureChance;
        if (hasHallStructure) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunkData.setBlock(x, FLOOR_TOP_Y, z, Material.GRASS_BLOCK);
                    for (int y = FLOOR_TOP_Y - 3; y < FLOOR_TOP_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.DIRT);
                    }
                }
            }
            alphaStructures.placeRandomStructure(chunkData, new Random(hallStructureHash), true);
        }

        // --- Floor carpet: large zones on exposed floor, sometimes damp underneath ---
        if (!hasHallStructure) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;
                    if (!chunkData.getType(x, FLOOR_TOP_Y, z).isSolid()) continue;
                    int worldX = chunkX * 16 + x;
                    int worldZ = chunkZ * 16 + z;
                    double fpnx = (worldX + NOISE_OFFSET) * CARPET_SCALE;
                    double fpnz = (worldZ + NOISE_OFFSET) * CARPET_SCALE;
                    double carpetNoise = SimplexNoise.noise2(seed + 200, fpnx, fpnz)
                            + 0.5 * SimplexNoise.noise2(seed + 201, fpnx * 2, fpnz * 2)
                            - spawnBias(worldX, worldZ);
                    if (carpetNoise <= 0.0) continue;
                    chunkData.setBlock(x, AIR_MIN_Y, z, Material.YELLOW_CARPET);

                    // Damp patches: hidden under the carpet, heard (combination step sound) and felt (soul sand).
                    if (classic[x][z]) continue;
                    double damp = SimplexNoise.noise2(seed + 600, worldX * DAMP_SCALE, worldZ * DAMP_SCALE)
                            + 0.4 * SimplexNoise.noise2(seed + 601, worldX * DAMP_SCALE * 3, worldZ * DAMP_SCALE * 3);
                    if (damp > DAMP_SOUL_SAND) {
                        chunkData.setBlock(x, FLOOR_TOP_Y, z, Material.SOUL_SAND);
                    } else if (damp > DAMP_MUD) {
                        chunkData.setBlock(x, FLOOR_TOP_Y, z, Material.MUD);
                    }
                }
            }
        }

        // --- Lights: flush in the ceiling, or pendants hung at the same height in halls ---
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (worldX % LIGHT_SPACING != 0 || worldZ % LIGHT_SPACING != 0) continue;
                int h = hallHeight[x][z];
                if (h == 0) {
                    chunkData.setBlock(x, CEILING_MIN_Y, z, Material.OCHRE_FROGLIGHT);
                    continue;
                }
                // A pendant at the normal light height keeps the hall floor lit and
                // keeps light_blink / blackout on a single light_y. Skip columns a
                // structure occupies.
                boolean clear = true;
                for (int y = CEILING_MIN_Y; y < AIR_MIN_Y + h && clear; y++) {
                    clear = chunkData.getType(x, y, z) == Material.AIR;
                }
                if (!clear) continue;
                chunkData.setBlock(x, CEILING_MIN_Y, z, Material.OCHRE_FROGLIGHT);
                for (int y = CEILING_MIN_Y + 1; y < AIR_MIN_Y + h; y++) {
                    chunkData.setBlock(x, y, z, Material.IRON_CHAIN);
                }
            }
        }

        // Bedrock boundaries; the floor layer skips air so pits stay open to the void.
        applyBoundaryLayer(chunkData, WORLD_MIN_Y, WORLD_MIN_Y + BEDROCK_LAYERS, Material.BEDROCK, true);
        applyBoundaryLayer(chunkData, CEILING_MAX_Y - BEDROCK_LAYERS, CEILING_MAX_Y, Material.BEDROCK, false);
    }

    private static double spawnBias(double worldX, double worldZ) {
        return Math.max(0, 1.0 - (worldX * worldX + worldZ * worldZ) / (SPAWN_PLAIN_RADIUS * SPAWN_PLAIN_RADIUS));
    }

    /**
     * Air height of a tall hall at this column, or 0 for an ordinary room. Halls
     * rise from {@link #HALL_MIN_HEIGHT} at their edges toward their middles, in
     * 2-block steps.
     */
    private int hallHeight(long seed, int worldX, int worldZ, double spawnBias) {
        double hnx = (worldX + NOISE_OFFSET) * HALL_SCALE;
        double hnz = (worldZ + NOISE_OFFSET) * HALL_SCALE;
        double mask = SimplexNoise.noise2(seed + 400, hnx, hnz)
                + 0.5 * SimplexNoise.noise2(seed + 401, hnx * 2, hnz * 2)
                - 2.0 * spawnBias;
        if (mask <= hallThreshold) return 0;

        double depth = Math.min(1.0, (mask - hallThreshold) / 0.15);
        // Stretched past the noise's usual range so the tallest heights actually occur.
        double t = Math.clamp(0.5 + 0.8 * SimplexNoise.noise2(seed + 410, worldX * HALL_HEIGHT_SCALE, worldZ * HALL_HEIGHT_SCALE), 0.0, 1.0);
        double h = HALL_MIN_HEIGHT + (HALL_MAX_HEIGHT - HALL_MIN_HEIGHT) * t * depth;
        int stepped = HALL_MIN_HEIGHT + 2 * (int) Math.round((h - HALL_MIN_HEIGHT) / 2);
        return Math.min(HALL_MAX_HEIGHT, stepped);
    }

    private static boolean noHallIn(int[][] hallHeight, int min, int max) {
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                if (hallHeight[x][z] != 0) return false;
            }
        }
        return true;
    }

    /**
     * A 5x5 shaft from the floor straight through the bedrock into the void,
     * with a staircase spiralling down its walls (one block lower per step,
     * full-block landings at the corners) that runs out 40-60 blocks down.
     */
    private void placePit(ChunkData chunkData, long pitHash) {
        for (int x = PIT_CLEAR_MIN; x <= PIT_CLEAR_MAX; x++) {
            for (int z = PIT_CLEAR_MIN; z <= PIT_CLEAR_MAX; z++) {
                for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }
            }
        }
        for (int x = PIT_MIN; x <= PIT_MAX; x++) {
            for (int z = PIT_MIN; z <= PIT_MAX; z++) {
                for (int y = WORLD_MIN_Y; y <= FLOOR_TOP_Y; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }
            }
        }

        // Ring of the 16 perimeter blocks, walked clockwise from a corner.
        int[][] ring = new int[16][];
        int i = 0;
        for (int x = PIT_MIN; x < PIT_MAX; x++) ring[i++] = new int[]{ x, PIT_MIN };
        for (int z = PIT_MIN; z < PIT_MAX; z++) ring[i++] = new int[]{ PIT_MAX, z };
        for (int x = PIT_MAX; x > PIT_MIN; x--) ring[i++] = new int[]{ x, PIT_MAX };
        for (int z = PIT_MAX; z > PIT_MIN; z--) ring[i++] = new int[]{ PIT_MIN, z };

        Material[] set = PIT_STAIR_SETS[(int) Math.floorMod(pitHash >>> 20, (long) PIT_STAIR_SETS.length)];
        int start = (int) Math.floorMod(pitHash >>> 8, 4L) * 4; // begin at one of the corners
        int length = 40 + (int) Math.floorMod(pitHash >>> 12, 21L);
        for (int k = 0; k <= length; k++) {
            int[] cur = ring[(start + k) % 16];
            int y = FLOOR_TOP_Y - k;
            boolean corner = (cur[0] == PIT_MIN || cur[0] == PIT_MAX) && (cur[1] == PIT_MIN || cur[1] == PIT_MAX);
            if (corner) {
                chunkData.setBlock(cur[0], y, cur[1], set[0]);
                continue;
            }
            // Stairs rise toward the step behind them.
            int[] prev = ring[(start + k + 15) % 16];
            Stairs stair = (Stairs) set[1].createBlockData();
            stair.setFacing(faceToward(prev[0] - cur[0], prev[1] - cur[1]));
            stair.setHalf(Stairs.Half.BOTTOM);
            chunkData.setBlock(cur[0], y, cur[1], stair);
        }
    }

    private static BlockFace faceToward(int dx, int dz) {
        if (dx > 0) return BlockFace.EAST;
        if (dx < 0) return BlockFace.WEST;
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private static long chunkHash(long seed, int chunkX, int chunkZ, long salt) {
        long h = seed
                ^ ((long) chunkX * 0x517CC1B727220A95L)
                ^ ((long) chunkZ * 0x6C62272E07BB0142L)
                ^ salt;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    /** ~1 in 1250 wall-region cells (about one per 75 chunks) holds the Library alcove. */
    private static boolean isLibraryAlcove(long seed, int worldX, int worldZ) {
        long h = seed
                ^ ((long) worldX * 341873128712L)
                ^ ((long) worldZ * 132897987541L)
                ^ 0xA1C07EL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return Math.floorMod(h, 1250) == 0;
    }

    private int classifyRegion(double noise) {
        if (noise < -0.3) return REGION_PILLARS;
        if (noise > 0.3) return REGION_EMPTY;
        return REGION_WALLS;
    }

    private void placeStructure(ChunkData chunkData, Random rng, int baseX, int baseZ, int region, Material[][] wallMats) {
        double roll = rng.nextDouble();
        switch (region) {
            case REGION_EMPTY -> placeEmpty(chunkData, rng, baseX, baseZ, roll, wallMats);
            case REGION_WALLS -> placeWallRegion(chunkData, rng, baseX, baseZ, roll, wallMats);
            case REGION_PILLARS -> placePillarRegion(chunkData, rng, baseX, baseZ, roll, wallMats);
        }
    }

    private void placeEmpty(ChunkData chunkData, Random rng, int baseX, int baseZ, double roll, Material[][] wallMats) {
        if (roll < 0.596) {
            rng.nextInt(4);
        } else if (roll < 0.60) {
            placeDesk(chunkData, baseX, baseZ, rng.nextInt(4));
        } else if (roll < 0.75) {
            placePillar(chunkData, rng, baseX, baseZ, wallMats);
        } else if (roll < 0.90) {
            placeRandomWall(chunkData, rng, baseX, baseZ, wallMats);
        } else {
            placeLCorner(chunkData, baseX, baseZ, rng.nextInt(4), wallMats);
        }
    }

    private void placeWallRegion(ChunkData chunkData, Random rng, int baseX, int baseZ, double roll, Material[][] wallMats) {
        if (roll < 0.10) {
            rng.nextInt(4);
        } else if (roll < 0.20) {
            placePillar(chunkData, rng, baseX, baseZ, wallMats);
        } else if (roll < 0.55) {
            placeRandomWall(chunkData, rng, baseX, baseZ, wallMats);
        } else if (roll < 0.75) {
            placeCeilingWall(chunkData, rng, baseX, baseZ, wallMats);
        } else {
            placeLCorner(chunkData, baseX, baseZ, rng.nextInt(4), wallMats);
        }
    }

    private void placePillarRegion(ChunkData chunkData, Random rng, int baseX, int baseZ, double roll, Material[][] wallMats) {
        if (roll < 0.15) {
            rng.nextInt(4);
        } else if (roll < 0.70) {
            placePillar(chunkData, rng, baseX, baseZ, wallMats);
        } else if (roll < 0.85) {
            placeRandomWall(chunkData, rng, baseX, baseZ, wallMats);
        } else {
            placeLCorner(chunkData, baseX, baseZ, rng.nextInt(4), wallMats);
        }
    }

    private void placeRandomWall(ChunkData chunkData, Random rng, int baseX, int baseZ, Material[][] wallMats) {
        int dir = rng.nextInt(4);
        double heightRoll = rng.nextDouble();
        if (heightRoll < 0.35) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MAX_Y, wallMats);
        } else if (heightRoll < 0.65) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MIN_Y + 3, wallMats);
        } else {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MIN_Y + 1, wallMats);
        }
    }

    private void placeCeilingWall(ChunkData chunkData, Random rng, int baseX, int baseZ, Material[][] wallMats) {
        int dir = rng.nextInt(4);
        double heightRoll = rng.nextDouble();
        if (heightRoll < 0.30) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MIN_Y, AIR_MAX_Y, wallMats);
        } else if (heightRoll < 0.70) {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MAX_Y - 3, AIR_MAX_Y, wallMats);
        } else {
            placeWall(chunkData, baseX, baseZ, dir, AIR_MAX_Y - 1, AIR_MAX_Y, wallMats);
        }
    }

    private void placePillar(ChunkData chunkData, Random rng, int baseX, int baseZ, Material[][] wallMats) {
        int px = baseX + 1 + rng.nextInt(2);
        int pz = baseZ + 1 + rng.nextInt(2);
        for (int y = AIR_MIN_Y; y < AIR_MAX_Y; y++) {
            chunkData.setBlock(px, y, pz, wallMats[px][pz]);
        }
    }

    private void placeWall(ChunkData chunkData, int baseX, int baseZ, int direction, int yMin, int yMax, Material[][] wallMats) {
        for (int i = 0; i < CELL_SIZE; i++) {
            int x, z;
            switch (direction) {
                case 0 -> { x = baseX + i; z = baseZ; }
                case 1 -> { x = baseX + i; z = baseZ + 3; }
                case 2 -> { x = baseX; z = baseZ + i; }
                default -> { x = baseX + 3; z = baseZ + i; }
            }
            for (int y = yMin; y < yMax; y++) {
                chunkData.setBlock(x, y, z, wallMats[x][z]);
            }
        }
    }

    private void placeLCorner(ChunkData chunkData, int baseX, int baseZ, int corner, Material[][] wallMats) {
        switch (corner) {
            case 0 -> {
                placeWall(chunkData, baseX, baseZ, 0, AIR_MIN_Y, AIR_MAX_Y, wallMats);
                placeWall(chunkData, baseX, baseZ, 2, AIR_MIN_Y, AIR_MAX_Y, wallMats);
            }
            case 1 -> {
                placeWall(chunkData, baseX, baseZ, 0, AIR_MIN_Y, AIR_MAX_Y, wallMats);
                placeWall(chunkData, baseX, baseZ, 3, AIR_MIN_Y, AIR_MAX_Y, wallMats);
            }
            case 2 -> {
                placeWall(chunkData, baseX, baseZ, 1, AIR_MIN_Y, AIR_MAX_Y, wallMats);
                placeWall(chunkData, baseX, baseZ, 2, AIR_MIN_Y, AIR_MAX_Y, wallMats);
            }
            default -> {
                placeWall(chunkData, baseX, baseZ, 1, AIR_MIN_Y, AIR_MAX_Y, wallMats);
                placeWall(chunkData, baseX, baseZ, 3, AIR_MIN_Y, AIR_MAX_Y, wallMats);
            }
        }
    }

    private void placeDesk(ChunkData chunkData, int baseX, int baseZ, int orientation) {
        int x = baseX + 1;
        int z = baseZ + 1;
        int y = AIR_MIN_Y;

        BlockFace stair0Face, stair1Face;
        int s0x, s0z, s1x, s1z, bx, bz;

        switch (orientation) {
            case 0 -> { // line along +X
                s0x = x; s0z = z; s1x = x + 1; s1z = z; bx = x + 2; bz = z;
                stair0Face = BlockFace.WEST; stair1Face = BlockFace.EAST;
            }
            case 1 -> { // line along +Z
                s0x = x; s0z = z; s1x = x; s1z = z + 1; bx = x; bz = z + 2;
                stair0Face = BlockFace.NORTH; stair1Face = BlockFace.SOUTH;
            }
            case 2 -> { // line along -X
                s0x = x + 2; s0z = z; s1x = x + 1; s1z = z; bx = x; bz = z;
                stair0Face = BlockFace.EAST; stair1Face = BlockFace.WEST;
            }
            default -> { // line along -Z
                s0x = x; s0z = z + 2; s1x = x; s1z = z + 1; bx = x; bz = z;
                stair0Face = BlockFace.SOUTH; stair1Face = BlockFace.NORTH;
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

        // Place plain BOOKSHELF — the LobbyBookshelfListener converts it to
        // CHISELED_BOOKSHELF at runtime, which forces proper tile entity creation.
        chunkData.setBlock(bx, y, bz, Material.BOOKSHELF);
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
