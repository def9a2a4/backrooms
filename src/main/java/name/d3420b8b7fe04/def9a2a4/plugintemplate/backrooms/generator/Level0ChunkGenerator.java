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
 * slab reaching down to the void.
 *
 * Passes, in order:
 * <ol>
 *   <li>Surfaces: floors, ceilings and walls each take their material from an
 *   independent, very large {@link PatchPalette}. Around spawn everything is
 *   classic yellow terracotta, with no carpet.</li>
 *   <li>Furnishing per 48-block zone ({@link Level0Zones}): the classic random
 *   walls and pillars, or cubicle farms, cafeterias, offices, corridors.</li>
 *   <li>Set pieces per 128-block macro cell ({@link Level0Features}): sunken
 *   rooms (some with Level 101 houses) and huge stairwell pits to the void.</li>
 *   <li>Carpet, sometimes with damp mud / soul sand underneath.</li>
 *   <li>Lights on a 4-block grid: flush in the ceiling at y69, or hung on
 *   chains lower down in some sunken rooms (light events scan for both).</li>
 * </ol>
 */
public class Level0ChunkGenerator extends BackroomsChunkGenerator {

    public Level0ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    private static final int WORLD_MIN_Y = -64;
    // Floor top sits at Level 101's ground height (its structures are shifted onto sunken floors).
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
    private static final double CLASSIC_EDGE_SCALE = 1.0 / 60.0;
    private static final double NOISE_OFFSET = 100000.0;
    private static final double SPAWN_PLAIN_RADIUS = 200.0;

    private static final double DAMP_MUD = 0.45;
    private static final double DAMP_SOUL_SAND = 0.75;

    private static final int REGION_EMPTY = 0;
    private static final int REGION_WALLS = 1;
    private static final int REGION_PILLARS = 2;

    private static final PatchPalette FLOOR_PALETTE = new PatchPalette(0xF100L, 320,
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
    private static final PatchPalette CEILING_PALETTE = new PatchPalette(0xCE11L, 384,
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

    private static final PatchPalette WALL_PALETTE = new PatchPalette(0xA11L, 256,
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
    private int roomCellChance = 35;    // percent of 128-block cells with a sunken room
    private int pitCellChance = 4;      // percent of 128-block cells with a pit
    private int houseChunkChance = 45;  // percent of eligible chunks in a house room

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config == null) return;
        roomCellChance = config.getInt("room_cell_chance", roomCellChance);
        pitCellChance = config.getInt("pit_cell_chance", pitCellChance);
        houseChunkChance = config.getInt("house_chunk_chance", houseChunkChance);
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        chunkData.setRegion(0, WORLD_MIN_Y, 0, 16, FLOOR_TOP_Y + 1, 16, Material.YELLOW_TERRACOTTA);
        chunkData.setRegion(0, CEILING_MIN_Y, 0, 16, CEILING_MAX_Y, 16, Material.YELLOW_TERRACOTTA);

        long seed = worldInfo.getSeed();
        Level0Chunk c = new Level0Chunk(chunkData, seed, chunkX, chunkZ);

        // --- Surfaces ---
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = c.wx(x), worldZ = c.wz(z);
                boolean classic = spawnBias(worldX, worldZ)
                        + 0.25 * SimplexNoise.noise2(seed + 500, worldX * CLASSIC_EDGE_SCALE, worldZ * CLASSIC_EDGE_SCALE)
                        > 0.5;
                c.classic[x][z] = classic;
                c.anyClassic |= classic;
                c.floorMats[x][z] = classic ? Material.YELLOW_TERRACOTTA : FLOOR_PALETTE.pick(seed, worldX, worldZ);
                c.wallMats[x][z] = classic ? Material.YELLOW_TERRACOTTA : WALL_PALETTE.pick(seed, worldX, worldZ);
                c.floorTop[x][z] = FLOOR_TOP_Y;
                c.lightY[x][z] = worldX % LIGHT_SPACING == 0 && worldZ % LIGHT_SPACING == 0
                        ? CEILING_MIN_Y : Level0Chunk.NO_LIGHT;
                chunkData.setBlock(x, FLOOR_TOP_Y, z, c.floorMats[x][z]);
                chunkData.setBlock(x, CEILING_MIN_Y, z, classic
                        ? Material.YELLOW_TERRACOTTA : CEILING_PALETTE.pick(seed, worldX, worldZ));
            }
        }

        // --- Furnishing ---
        int zoneX = Math.floorDiv(chunkX * 16, Level0Zones.ZONE);
        int zoneZ = Math.floorDiv(chunkZ * 16, Level0Zones.ZONE);
        Level0Zones.Zone zone = Level0Zones.zoneAt(seed, zoneX, zoneZ);
        if (zone == Level0Zones.Zone.LOBBY) {
            placeLobbyCells(c);
        } else {
            Level0Zones.furnish(c, zone, zoneX, zoneZ);
        }

        // --- Set pieces ---
        int cellX = Math.floorDiv(chunkX * 16, Level0Features.MACRO);
        int cellZ = Math.floorDiv(chunkZ * 16, Level0Features.MACRO);
        Level0Features.render(c, Level0Features.featureAt(seed, cellX, cellZ, roomCellChance, pitCellChance),
                alphaStructures, houseChunkChance);

        placeCarpet(c);
        placeLights(c);

        // Bedrock boundaries; the floor layer skips air so pits stay open to the void.
        applyBoundaryLayer(chunkData, WORLD_MIN_Y, WORLD_MIN_Y + BEDROCK_LAYERS, Material.BEDROCK, true);
        applyBoundaryLayer(chunkData, CEILING_MAX_Y - BEDROCK_LAYERS, CEILING_MAX_Y, Material.BEDROCK, false);
    }

    private static double spawnBias(double worldX, double worldZ) {
        return Math.max(0, 1.0 - (worldX * worldX + worldZ * worldZ) / (SPAWN_PLAIN_RADIUS * SPAWN_PLAIN_RADIUS));
    }

    /** The classic lobby: random walls, pillars and desks per 4x4 cell, plus the Library alcove. */
    private void placeLobbyCells(Level0Chunk c) {
        Random chunkRng = new Random(c.seed ^ ((long) c.chunkX * 341873128712L + (long) c.chunkZ * 132897987541L));
        for (int cx = 0; cx < CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < CELLS_PER_AXIS; cz++) {
                int baseX = cx * CELL_SIZE;
                int baseZ = cz * CELL_SIZE;

                double rawCenterX = c.wx(baseX) + CELL_SIZE / 2.0;
                double rawCenterZ = c.wz(baseZ) + CELL_SIZE / 2.0;

                double regionNoise = SimplexNoise.noise2(c.seed, rawCenterX * REGION_SCALE, rawCenterZ * REGION_SCALE);
                int region = classifyRegion(regionNoise);

                placeStructure(c.data, chunkRng, baseX, baseZ, region, c.wallMats);

                // Rare bookshelf alcove: the door to the Library (break_block exit
                // in level_0.yml). Hashed on world position, not chunkRng, so it
                // never shifts existing structure layouts; the froglight above
                // makes it readable down a corridor. LobbyBookshelfListener skips
                // stacked shelves, so the column survives chiseling.
                if (region == REGION_WALLS && isLibraryAlcove(c.seed, c.wx(baseX), c.wz(baseZ))) {
                    int ax = baseX + 1, az = baseZ + 1;
                    c.data.setBlock(ax, AIR_MIN_Y, az, Material.BOOKSHELF);
                    c.data.setBlock(ax, AIR_MIN_Y + 1, az, Material.BOOKSHELF);
                    c.data.setBlock(ax, AIR_MIN_Y + 2, az, Material.OCHRE_FROGLIGHT);
                }
            }
        }
    }

    /** Large carpet zones on exposed floor (never near spawn), sometimes damp underneath. */
    private void placeCarpet(Level0Chunk c) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (c.classic[x][z] || c.noCarpet[x][z]) continue;
                int floorY = c.floorTop[x][z];
                if (c.data.getType(x, floorY + 1, z) != Material.AIR) continue;
                if (!c.data.getType(x, floorY, z).isSolid()) continue;
                int worldX = c.wx(x), worldZ = c.wz(z);
                double fpnx = (worldX + NOISE_OFFSET) * CARPET_SCALE;
                double fpnz = (worldZ + NOISE_OFFSET) * CARPET_SCALE;
                double carpetNoise = SimplexNoise.noise2(c.seed + 200, fpnx, fpnz)
                        + 0.5 * SimplexNoise.noise2(c.seed + 201, fpnx * 2, fpnz * 2)
                        - spawnBias(worldX, worldZ);
                if (carpetNoise <= 0.0) continue;
                c.data.setBlock(x, floorY + 1, z, Material.YELLOW_CARPET);

                // Damp patches: hidden under the carpet, heard (combination step sound) and felt (soul sand).
                double damp = SimplexNoise.noise2(c.seed + 600, worldX * DAMP_SCALE, worldZ * DAMP_SCALE)
                        + 0.4 * SimplexNoise.noise2(c.seed + 601, worldX * DAMP_SCALE * 3, worldZ * DAMP_SCALE * 3);
                if (damp > DAMP_SOUL_SAND) {
                    c.data.setBlock(x, floorY, z, Material.SOUL_SAND);
                } else if (damp > DAMP_MUD) {
                    c.data.setBlock(x, floorY, z, Material.MUD);
                }
            }
        }
    }

    /**
     * Grid lights: flush in the ceiling, or a pendant on chains where a sunken
     * room hangs its lights low. A pendant whose drop is blocked (stairs, a
     * house) falls back to a flush light.
     */
    private void placeLights(Level0Chunk c) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int ly = c.lightY[x][z];
                if (ly == Level0Chunk.NO_LIGHT) continue;
                boolean clear = true;
                for (int y = ly; y < CEILING_MIN_Y && clear; y++) {
                    clear = c.data.getType(x, y, z) == Material.AIR;
                }
                if (ly >= CEILING_MIN_Y || !clear) {
                    c.data.setBlock(x, CEILING_MIN_Y, z, Material.OCHRE_FROGLIGHT);
                    continue;
                }
                c.data.setBlock(x, ly, z, Material.OCHRE_FROGLIGHT);
                for (int y = ly + 1; y < CEILING_MIN_Y; y++) {
                    c.data.setBlock(x, y, z, Material.IRON_CHAIN);
                }
            }
        }
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
