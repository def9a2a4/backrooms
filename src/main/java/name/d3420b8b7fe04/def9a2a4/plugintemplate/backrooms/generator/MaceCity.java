package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * One of Mace's cities, built the way Mace builds it (GenerateCity.Generate and the Make* classes
 * of https://github.com/corbin-auriti/mace-minecraft, GPLv3), into a block buffer in Mace's own
 * coordinates: the city square runs from 0 to {@code mapLength} in x and z, the farms from
 * {@code -farmLength} to 0 in z, and y is world y (ground at 63, bedrock at 0-1).
 *
 * <p>In Mace's order: underground terrain (a smudged blocky mix of the theme's underground
 * blocks, with ore patches richer further down), walls with walkways, lights and an emblem; a
 * gatehouse in the middle of each side; a moat and drawbridges; main streets crossing the middle
 * and smaller streets splitting the quarters into districts, each packed with the theme's
 * buildings facing the nearest street (the first district gets the mineshaft entrance); street
 * lights and signs; seven levels of mineshaft, joined by the entrance's ladder; guard towers at
 * the corners; farms outside the north wall, with ponds and hills; flowers. Mace's console log,
 * NPCs, spawners and lighting pass are left out; Minecraft does the light.
 *
 * <p>Like Mace, everything comes from one RNG in a fixed order, so a seed always gives the same
 * city. The finished buffer is read-only and shared between chunk threads.
 */
final class MaceCity {

    static final int HEIGHT = 128;
    static final int EDGE = 8;                      // City.edgeLength
    private static final int MULTIPLIER = 5;        // mineshaft cells
    private static final int SPLIT_CHANCE = 7;
    private static final int[] BUILDING_FREQUENCY = {9, 8, 7, 6, 5};
    private static final int[] FARM_FREQUENCY = {20, 16, 12, 8, 4};

    // ── Themes (Resources/Themes/*.xml) ───────────────────────────────────

    enum Theme {
        MEDIEVAL("Medieval Buildings", "adjectives", "nouns", true,
                List.of("Drop to Bedrock", "Lava", "Fire", "Water"),
                List.of("cobweb", "craftersguild", "creeper", "cross", "heart", "pickaxe", "pride flag", "shield",
                        "sword", "tree", "yin and yang"),
                List.of(Material.SHORT_GRASS, Material.FERN, Material.DANDELION, Material.POPPY),
                List.of(Material.STONE, Material.STONE, Material.STONE, Material.DIRT, Material.DIRT, Material.SAND,
                        Material.GRAVEL),
                List.of(Material.STONE, Material.OAK_PLANKS, Material.BRICKS, Material.STONE_BRICKS),
                List.of("stone_raised", "woodplanks_raised", "brick_raised", "stonebrick_raised", "stonebrick", "wood"),
                4, Material.GRASS_BLOCK),
        NOVV("Novv's Medieval Buildings", "adjectives", "novv", true,
                List.of("Drop to Bedrock", "Lava", "Fire", "Water"),
                List.of("cobweb", "craftersguild", "creeper", "cross", "heart", "pickaxe", "pride flag", "shield",
                        "sword", "tree", "yin and yang"),
                List.of(Material.SHORT_GRASS, Material.FERN, Material.DANDELION, Material.POPPY),
                List.of(Material.STONE, Material.STONE, Material.STONE, Material.DIRT, Material.DIRT, Material.SAND,
                        Material.GRAVEL),
                List.of(Material.STONE, Material.OAK_PLANKS, Material.STONE_BRICKS),
                List.of("stone_raised", "woodplanks_raised", "brick_raised", "stonebrick_raised", "stonebrick", "stone",
                        "wood"),
                3, Material.GRASS_BLOCK),
        DESERT("Desert Buildings", "desert_adjectives", "desert_nouns", false,
                List.of("Cactus", "Drop to Bedrock"),
                List.of("ankh", "cobweb", "cross", "heart", "pride flag", "shield", "yin and yang"),
                List.of(Material.DEAD_BUSH, Material.CACTUS),
                List.of(Material.STONE, Material.DIRT, Material.SAND, Material.SAND, Material.GRAVEL, Material.SANDSTONE),
                List.of(Material.SANDSTONE),
                List.of("sandstone_raised", "stone_raised", "stonebrick"),
                0, Material.SAND);

        final String file, prefixList, suffixList;
        final boolean farms;
        final List<String> moats, emblems, paths;
        final List<Material> flowers, underground, walls;
        final int farmSize;
        final Material ground;

        Theme(String file, String prefixList, String suffixList, boolean farms, List<String> moats,
              List<String> emblems, List<Material> flowers, List<Material> underground, List<Material> walls,
              List<String> paths, int farmSize, Material ground) {
            this.file = file;
            this.prefixList = prefixList;
            this.suffixList = suffixList;
            this.farms = farms;
            this.moats = moats;
            this.emblems = emblems;
            this.flowers = flowers;
            this.underground = underground;
            this.walls = walls;
            this.paths = paths;
            this.farmSize = farmSize;
            this.ground = ground;
        }
    }

    /** What's known before building: enough to place the city and its spawn. */
    record Plan(Theme theme, int cityLength, int farmLength, long seed) {
        int mapLength() {
            return cityLength + EDGE * 2;
        }

        /** A city {@code chunks} chunks across (odd, like Mace's, so the main streets meet in the middle). */
        static Plan of(long seed, int chunks) {
            Random rng = new Random(seed);
            int t = rng.nextInt(100);
            Theme theme = t < 20 ? Theme.DESERT : t < 55 ? Theme.NOVV : Theme.MEDIEVAL;
            int farm = theme.farms ? Math.max(2, theme.farmSize) * 16 : 0;
            return new Plan(theme, Math.max(5, chunks | 1) * 16, farm, rng.nextLong());
        }
    }

    // ── The buffer ────────────────────────────────────────────────────────

    final Plan plan;
    final int mapLength, farmLength;
    final String name;
    private final int sizeX, sizeZ;                   // x 0..mapLength, z -farmLength..mapLength
    private final short[] cells;
    private final List<BlockData> palette = new ArrayList<>();
    private final Map<BlockData, Short> paletteIndex = new HashMap<>();
    private final Map<Material, BlockData> plain = new HashMap<>();
    /** Sign text and container contents by packed position (see {@link #key}). */
    final Map<Long, String[]> signs = new HashMap<>();
    final Map<Long, ItemStack[]> containers = new HashMap<>();

    // City.* options
    private final Random rng;
    private final MaceLibrary lib;
    private final Theme theme;
    private final boolean hasFarms, hasMoat, hasWalls, hasDrawbridges, hasGuardTowers, hasBuildings, hasPaths,
            hasMainStreets, hasMineshaft, hasEmblems, hasOutsideLights, hasTowerAddition, hasFlowers, hasSkyFeature,
            hasStreetLights, hasTorchesOnWalkways;
    private String moatType, outsideLightType;
    private final String emblemType, towerAdditionType, streetLightType;
    private final BlockData ground, wall;
    private BlockData pathBlock;
    private @Nullable BlockData pathAlternative;
    private int pathExtends = 1;
    private final int flowerPercent = 15;
    private final MaceBuilding.Fill fill;

    private MaceCity(Plan plan, MaceLibrary lib) {
        this.plan = plan;
        this.lib = lib;
        this.theme = plan.theme();
        this.mapLength = plan.mapLength();
        this.farmLength = plan.farmLength();
        this.sizeX = mapLength + 1;
        this.sizeZ = mapLength + farmLength + 1;
        this.cells = new short[sizeX * sizeZ * HEIGHT];
        palette.add(Material.AIR.createBlockData());
        paletteIndex.put(palette.get(0), (short) 0);
        this.rng = new Random(plan.seed());

        name = lib.words.cityName(theme.prefixList, theme.suffixList, rng);
        // GenerateWorld: every theme says yes to everything; farms and sky features vary.
        hasFarms = theme.farms;
        hasMoat = hasWalls = hasDrawbridges = hasGuardTowers = hasBuildings = hasPaths = hasMainStreets = true;
        hasMineshaft = hasEmblems = hasOutsideLights = hasTowerAddition = hasFlowers = hasStreetLights = true;
        hasTorchesOnWalkways = true;
        hasSkyFeature = true;   // Mace's Desert theme had none; here every city gets the balloon or airship
        ground = theme.ground.createBlockData();
        moatType = pick(theme.moats);
        emblemType = pick(theme.emblems);
        outsideLightType = pick(List.of("Fire", "Torches"));
        towerAdditionType = pick(List.of("Fire beacon", "Flag"));
        streetLightType = pick(List.of("Glowstones", "Torches"));
        Material wallMaterial = pick(theme.walls);
        wall = wallMaterial.createBlockData();
        path(pick(theme.paths));
        // "Fixing fire-spreading combination"
        if (wallMaterial == Material.OAK_PLANKS) {
            if (outsideLightType.equals("Fire")) outsideLightType = "Torches";
            if (moatType.equals("Fire") || moatType.equals("Lava")) moatType = "Water";
        }
        fill = new MaceBuilding.Fill(ground, pathBlock, wall, hasTorchesOnWalkways, name,
                lib.words.forCity(new Random(rng.nextLong())), true, new HashSet<>());
    }

    /** Builds the whole city. Slow (about a second); callers cache it. */
    static MaceCity build(Plan plan, MaceLibrary lib) {
        MaceCity c = new MaceCity(plan, lib);
        c.makeTerrain();
        if (c.hasWalls) c.makeWalls();
        if (c.hasDrawbridges && c.hasWalls) c.makeEntrances();
        if (c.hasMoat) c.makeMoat();
        if (c.hasBuildings || c.hasPaths) {
            int[][] area = c.makePaths();
            if (c.hasBuildings) {
                int[] entrance = c.makeInsideCity(area);
                if (c.hasMineshaft && entrance[0] >= 0) c.makeMineshaft(entrance);
            }
        }
        if (c.hasGuardTowers) c.makeGuardTowers();
        if (c.hasFarms) c.makeFarms();
        if (c.hasFlowers) c.makeFlowers();
        c.positionRails();
        c.connectFences();
        return c;
    }

    private void path(String type) {
        switch (type) {
            case "stone_raised" -> raised(Material.SMOOTH_STONE_SLAB, Material.SMOOTH_STONE_SLAB);
            case "sandstone_raised" -> raised(Material.SANDSTONE, Material.SANDSTONE_SLAB);
            case "woodplanks_raised" -> raised(Material.OAK_PLANKS, Material.OAK_SLAB);
            case "brick_raised" -> raised(Material.BRICKS, Material.BRICK_SLAB);
            case "stonebrick_raised" -> raised(Material.STONE_BRICKS, Material.STONE_BRICK_SLAB);
            case "stonebrick" -> pathBlock = Material.STONE_BRICKS.createBlockData();
            case "stone" -> pathBlock = Material.STONE.createBlockData();
            case "wood" -> pathBlock = (rng.nextBoolean() ? Material.OAK_LOG : Material.SPRUCE_LOG).createBlockData();
            default -> pathBlock = Material.SANDSTONE.createBlockData();
        }
    }

    /** A "raised" path: full blocks down the middle, a slab kerb along the main streets. */
    private void raised(Material block, Material slab) {
        BlockData b = block.createBlockData();
        if (b instanceof Slab s) s.setType(Slab.Type.DOUBLE);   // stone_raised: Mace's double stone slab
        pathBlock = b;
        pathAlternative = slab.createBlockData();
        pathExtends = 2;
    }

    // ── Buffer access ─────────────────────────────────────────────────────

    boolean contains(int x, int z) {
        return x >= 0 && x < sizeX && z >= -farmLength && z <= mapLength;
    }

    private int index(int x, int y, int z) {
        return (y * sizeZ + (z + farmLength)) * sizeX + x;
    }

    BlockData get(int x, int y, int z) {
        if (!contains(x, z) || y < 0 || y >= HEIGHT) return palette.get(0);
        return palette.get(cells[index(x, y, z)]);
    }

    private Material type(int x, int y, int z) {
        return get(x, y, z).getMaterial();
    }

    private void set(int x, int y, int z, @Nullable BlockData b) {
        if (!contains(x, z) || y < 0 || y >= HEIGHT) return;
        BlockData d = b == null ? palette.get(0) : b;
        Short i = paletteIndex.get(d);
        if (i == null) {
            i = (short) palette.size();
            palette.add(d);
            paletteIndex.put(d, i);
        }
        cells[index(x, y, z)] = i;
        if (!signs.isEmpty() || !containers.isEmpty()) {   // a block entity goes with its block
            long k = key(x, y, z);
            signs.remove(k);
            containers.remove(k);
        }
    }

    private void set(int x, int y, int z, Material m) {
        set(x, y, z, data(m));
    }

    private BlockData data(Material m) {
        return plain.computeIfAbsent(m, Material::createBlockData);
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int keyX(long k) { return (int) (k >> 38) << 6 >> 6; }
    static int keyY(long k) { return (int) (k & 0xFFF); }
    static int keyZ(long k) { return (int) (k << 26 >> 38); }

    private <T> T pick(List<T> list) {
        return list.get(rng.nextInt(list.size()));
    }

    private int next(int lo, int hi) {          // C#'s Random.Next(lo, hi)
        return hi > lo ? lo + rng.nextInt(hi - lo) : lo;
    }

    // ── BlockShapes (mirror 1: the four quarters; 2: also swapped in x and z) ─────

    private void solidBox(int x1, int x2, int y1, int y2, int z1, int z2, @Nullable BlockData b, int mirror) {
        int m = mapLength;
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    set(x, y, z, b);
                    if (mirror >= 1) {
                        set(m - x, y, z, b);
                        set(x, y, m - z, b);
                        set(m - x, y, m - z, b);
                    }
                }
            }
        }
        if (mirror == 2) solidBox(z1, z2, y1, y2, x1, x2, b, 1);
    }

    private void solidBox(int x1, int x2, int y1, int y2, int z1, int z2, Material m, int mirror) {
        solidBox(x1, x2, y1, y2, z1, z2, data(m), mirror);
    }

    private void hollowBox(int x1, int x2, int y1, int y2, int z1, int z2, BlockData b, int mirror) {
        int m = mapLength;
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    if (x != x1 && y != y1 && z != z1 && x != x2 && y != y2 && z != z2) continue;
                    set(x, y, z, b);
                    if (mirror >= 1) {
                        set(m - x, y, z, b);
                        set(x, y, m - z, b);
                        set(m - x, y, m - z, b);
                    }
                }
            }
        }
        if (mirror == 2) hollowBox(z1, z2, y1, y2, x1, x2, b, 1);
    }

    /** A cardboard box with no top or bottom. */
    private void hollowLayers(int x1, int x2, int y1, int y2, int z1, int z2, @Nullable BlockData b, int mirror) {
        int m = mapLength;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (x != x1 && z != z1 && x != x2 && z != z2) continue;
                for (int y = y1; y <= y2; y++) {
                    set(x, y, z, b);
                    if (mirror >= 1) {
                        set(m - x, y, z, b);
                        set(x, y, m - z, b);
                        set(m - x, y, m - z, b);
                    }
                }
            }
        }
    }

    private void block(int x, int y, int z, @Nullable BlockData b, int mirror, int chance) {
        if (rng.nextInt(100) <= chance) set(x, y, z, b);
        if (mirror >= 1) {
            int m = mapLength;
            block(m - x, y, z, b, 0, chance);
            block(x, y, m - z, b, 0, chance);
            block(m - x, y, m - z, b, 0, chance);
            if (mirror == 2) block(z, y, x, b, 1, chance);
        }
    }

    private void block(int x, int y, int z, Material m, int mirror, int chance) {
        block(x, y, z, data(m), mirror, chance);
    }

    /** Mirrors a placement that works out its own facing at each spot. */
    private void mirrored(int x, int z, int mirror, java.util.function.BiConsumer<Integer, Integer> make) {
        make.accept(x, z);
        if (mirror >= 1) {
            int m = mapLength;
            make.accept(m - x, z);
            make.accept(x, m - z);
            make.accept(m - x, m - z);
            if (mirror == 2) mirrored(z, x, 1, make);
        }
    }

    // ── BlockHelper ───────────────────────────────────────────────────────

    /** BlockDirection: facing away from the first neighbour of {@code against}, in x-, x+, z-, z+ order. */
    private @Nullable BlockFace awayFrom(int x, int y, int z, Material against) {
        if (type(x - 1, y, z) == against) return BlockFace.EAST;
        if (type(x + 1, y, z) == against) return BlockFace.WEST;
        if (type(x, y, z - 1) == against) return BlockFace.SOUTH;
        if (type(x, y, z + 1) == against) return BlockFace.NORTH;
        return null;
    }

    /** BlockDirectionLadderSign: the same, in z+, z-, x+, x- order. */
    private BlockFace awayFromLadder(int x, int y, int z, Material against) {
        if (type(x, y, z + 1) == against) return BlockFace.NORTH;
        if (type(x, y, z - 1) == against) return BlockFace.SOUTH;
        if (type(x + 1, y, z) == against) return BlockFace.WEST;
        if (type(x - 1, y, z) == against) return BlockFace.EAST;
        return BlockFace.NORTH;
    }

    private BlockData facing(Material m, BlockFace f) {
        BlockData b = m.createBlockData();
        if (b instanceof Directional d) d.setFacing(f);
        return b;
    }

    private void torch(int x, int y, int z, Material against, int mirror) {
        mirrored(x, z, mirror, (tx, tz) -> {
            BlockFace f = awayFrom(tx, y, tz, against);
            set(tx, y, tz, f == null ? data(Material.TORCH) : facing(Material.WALL_TORCH, f));
        });
    }

    private void ladder(int x, int y1, int y2, int z, int mirror, Material against) {
        mirrored(x, z, mirror, (lx, lz) -> {
            BlockData b = facing(Material.LADDER, awayFromLadder(lx, y1, lz, against));
            for (int y = y1; y <= y2; y++) set(lx, y, lz, b);
        });
    }

    private void sign(int x, int y, int z, String text, Material against, int mirror) {
        String[] parts = (text + "~~~~").split("~", -1);
        mirrored(x, z, mirror, (sx, sz) -> {
            set(sx, y, sz, facing(Material.OAK_WALL_SIGN, awayFromLadder(sx, y, sz, against)));
            signs.put(key(sx, y, sz), new String[]{parts[0], parts[1], parts[2], parts[3]});
        });
    }

    private void chest(int x, int y, int z, Material against, ItemStack[] items) {
        BlockFace f = awayFrom(x, y, z, against);
        set(x, y, z, facing(Material.CHEST, f == null ? BlockFace.NORTH : f));
        containers.put(key(x, y, z), items);
    }

    /** MakeDoor: an iron or wooden door whose panel sits against {@code against}. */
    private void door(int x, int y, int z, Material against, boolean iron, int mirror) {
        mirrored(x, z, mirror, (dx, dz) -> {
            BlockFace f = awayFrom(dx, y + 1, dz, against);
            Material m = iron ? Material.IRON_DOOR : Material.OAK_DOOR;
            Door lower = (Door) m.createBlockData();
            lower.setFacing(f == null ? BlockFace.NORTH : f);
            lower.setHalf(Bisected.Half.BOTTOM);
            Door upper = (Door) lower.clone();
            upper.setHalf(Bisected.Half.TOP);
            set(dx, y, dz, lower);
            set(dx, y + 1, dz, upper);
        });
    }

    /** MakeBed: head at (x1, z1), foot at (x2, z2). */
    private void bed(int x1, int x2, int y, int z1, int z2, int mirror) {
        int m = mapLength;
        BlockFace f = x1 < x2 ? BlockFace.WEST : x1 > x2 ? BlockFace.EAST : z1 < z2 ? BlockFace.NORTH : BlockFace.SOUTH;
        Bed head = (Bed) Material.RED_BED.createBlockData();
        head.setFacing(f);
        head.setPart(Bed.Part.HEAD);
        Bed foot = (Bed) head.clone();
        foot.setPart(Bed.Part.FOOT);
        set(x1, y, z1, head);
        set(x2, y, z2, foot);
        if (mirror > 0) {
            bed(m - x1, m - x2, y, z1, z2, 0);
            bed(x1, x2, y, m - z1, m - z2, 0);
            bed(m - x1, m - x2, y, m - z1, m - z2, 0);
            if (mirror == 2) bed(z1, z2, y, x1, x2, 1);
        }
    }

    // ── Chunks.CreateInitialChunks: bedrock, smudged underground, ground ──

    private void makeTerrain() {
        int nx = mapLength / 8 + 1, ny = 64 / 8 + 1, nz = (farmLength + mapLength) / 8 + 1;
        int[][][] a = new int[nx][ny][nz];
        List<Material> blocks = theme.underground;
        List<Material> ids = new ArrayList<>(List.of(Material.STONE));   // id 0 is stone, for ore patches
        for (Material m : blocks) if (!ids.contains(m)) ids.add(m);
        for (int x = 0; x < nx; x++) {
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) a[x][y][z] = ids.indexOf(blocks.get(rng.nextInt(blocks.size())));
            }
        }
        a = smudge(enlarge(a, 4), 0.6);
        a = addResources(a, ids);
        a = smudge(enlarge(a, 2), 0.6);
        int sand = ids.indexOf(Material.SAND), sandstone = ids.indexOf(Material.SANDSTONE);
        if (sand >= 0 && sandstone < 0) {
            ids.add(Material.SANDSTONE);
            sandstone = ids.size() - 1;
        }
        for (int[][] plane : a) for (int[] col : plane) for (int i = 0; i < col.length; i++) {
            if (col[i] == sand && rng.nextDouble() < 0.2) col[i] = sandstone;
        }
        BlockData[] states = ids.stream().map(Material::createBlockData).toArray(BlockData[]::new);
        for (int x = 0; x < sizeX; x++) {
            for (int z = -farmLength; z <= mapLength; z++) {
                set(x, 0, z, Material.BEDROCK);
                set(x, 1, z, Material.BEDROCK);
                for (int y = 2; y < 63; y++) {
                    int v = a[Math.min(x, a.length - 1)][y][Math.min(farmLength + z, a[0][0].length - 1)];
                    set(x, y, z, v >= 0 ? states[v] : ORES[-v - 1]);
                }
                set(x, 63, z, ground);
            }
        }
    }

    private static final BlockData[] ORES = {
        Material.COAL_ORE.createBlockData(), Material.IRON_ORE.createBlockData(), Material.LAPIS_ORE.createBlockData(),
        Material.REDSTONE_ORE.createBlockData(), Material.GOLD_ORE.createBlockData(), Material.DIAMOND_ORE.createBlockData(),
    };

    /** Ore patches (as -1 - ORES index) in the stone, more and better further down. */
    private int[][][] addResources(int[][][] a, List<Material> ids) {
        int resources = mapLength * 64 * (farmLength + mapLength) / 200;   // "Normal"
        int stone = ids.indexOf(Material.STONE);
        for (int tries = 0; resources > 0 && tries < resources * 50; tries++) {
            int x = next(1, a.length - 1), y = next(1, a[0].length - 1), z = next(1, a[0][0].length - 1);
            if (a[x][y][z] != stone) continue;
            double depth = (double) y / a[0].length;
            if (depth < rng.nextDouble() * 1.5) {
                int ore = depth > 0.66 ? rng.nextInt(2) : depth > 0.33 ? rng.nextInt(4) : rng.nextInt(6);
                a[x][y][z] = -1 - ore;
                resources--;
            }
        }
        return a;
    }

    private static int[][][] enlarge(int[][][] a, int k) {
        int[][][] out = new int[a.length * k][a[0].length * k][a[0][0].length * k];
        for (int x = 0; x < out.length; x++) {
            for (int y = 0; y < out[0].length; y++) {
                for (int z = 0; z < out[0][0].length; z++) out[x][y][z] = a[x / k][y / k][z / k];
            }
        }
        return out;
    }

    private int[][][] smudge(int[][][] a, double chance) {
        int[][][] out = new int[a.length][a[0].length][a[0][0].length];
        for (int x = 0; x < a.length; x++) {
            for (int y = 0; y < a[0].length; y++) {
                for (int z = 0; z < a[0][0].length; z++) {
                    if (rng.nextDouble() <= chance) {
                        int dx, dy, dz;
                        do {
                            dx = rng.nextInt(3) - 1;
                            dy = rng.nextInt(3) - 1;
                            dz = rng.nextInt(3) - 1;
                        } while (x + dx < 0 || y + dy < 0 || z + dz < 0 || x + dx >= a.length || y + dy >= a[0].length
                                || z + dz >= a[0][0].length || Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1);
                        out[x][y][z] = a[x + dx][y + dy][z + dz];
                    } else {
                        out[x][y][z] = a[x][y][z];
                    }
                }
            }
        }
        return out;
    }

    // ── Walls ─────────────────────────────────────────────────────────────

    private void makeWalls() {
        int m = mapLength, e = EDGE;
        Material w = wall.getMaterial();
        for (int a = e + 6; a <= e + 10; a++) hollowLayers(a, m - a, 1, 72, a, m - a, wall, 0);
        hollowLayers(e + 5, m - (e + 5), 72, 73, e + 5, m - (e + 5), wall, 0);
        hollowLayers(e + 11, m - (e + 11), 72, 73, e + 11, m - (e + 11), wall, 0);
        for (int a = e + 6; a <= m - (e + 6); a += 2) block(a, 74, e + 5, wall, 2, 100);
        for (int a = e + 12; a <= m - (e + 12); a += 2) block(a, 74, e + 11, wall, 2, 100);
        ladder(m / 2 - 5, 64, 72, e + 11, 2, w);
        solidBox(m / 2 - 5, m / 2 + 5, 65, 74, e + 11, e + 11, (BlockData) null, 2);
        if (hasOutsideLights) {
            if (outsideLightType.equals("Fire")) {
                block(m / 2 - 1, 69, e + 5, Material.NETHERRACK, 2, 100);
                block(m / 2, 69, e + 5, Material.NETHERRACK, 2, 100);
                block(m / 2 - 1, 70, e + 5, Material.FIRE, 2, 100);
                block(m / 2, 70, e + 5, Material.FIRE, 2, 100);
                for (int a = e + 8; a < m / 2 - 9; a += 4) {
                    block(a, 69, e + 5, Material.NETHERRACK, 2, 100);
                    block(a, 70, e + 5, Material.FIRE, 2, 100);
                }
            } else {
                torch(m / 2, 70, e + 5, w, 2);
                torch(m / 2 - 1, 70, e + 5, w, 2);
                for (int a = e + 8; a < m / 2 - 9; a += 4) torch(a, 70, e + 5, w, 2);
            }
        }
        if (hasTorchesOnWalkways) {
            for (int a = e + 16; a < m / 2; a += 4) torch(a, 69, e + 11, w, 2);
            for (int a = e + 16; a < m / 2; a += 4) block(a, 73, e + 8, Material.TORCH, 2, 100);
        }
        if (hasEmblems) makeEmblem();
    }

    private void makeEmblem() {
        List<String[]> rows = lib.emblems.get(emblemType);
        if (rows == null) return;
        BlockData precious = data(List.of(Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.DIAMOND_BLOCK).get(rng.nextInt(3)));
        int m = mapLength, e = EDGE;
        for (int y = 0; y < rows.size(); y++) {
            String[] line = rows.get(y);
            for (int x = 0; x < line.length; x++) {
                BlockData b = line[x].equals("-1") ? precious : state(line[x]);
                block((m / 2 - (line.length + 6)) + x, 71 - y, e + 5, b, 2, 100);
            }
            for (int x = line.length + 1; x < line.length + 5; x++) {
                block(m / 2 - (6 + x), 69, e + 5, (BlockData) null, 2, 100);
                block(m / 2 - (6 + x), 70, e + 5, (BlockData) null, 2, 100);
            }
        }
    }

    private BlockData state(String s) {
        try {
            return Bukkit.createBlockData(s);
        } catch (IllegalArgumentException ex) {
            return palette.get(0);
        }
    }

    // ── Entrances: a gatehouse in the middle of each wall ─────────────────

    private void makeEntrances() {
        int m = mapLength, e = EDGE;
        List<MaceBuilding> gates = lib.of(theme.file, "city_entrance");
        if (gates.isEmpty()) return;
        MaceBuilding b = select(gates, BUILDING_FREQUENCY, "frequency");
        insert(b, m / 2 - b.width / 2, e + 5, 0, null, 0, b.sourceY, true);
        b = select(gates, BUILDING_FREQUENCY, "frequency");
        insert(b, e + 5, m / 2 - b.width / 2, 0, null, 1, b.sourceY, true);
        b = select(gates, BUILDING_FREQUENCY, "frequency");
        insert(b, m - (e + 4 + b.width), m / 2 - b.width / 2, 0, null, 2, b.sourceY, true);
        b = select(gates, BUILDING_FREQUENCY, "frequency");
        insert(b, m / 2 - b.width / 2, m - (e + 4 + b.width), 0, null, 3, b.sourceY, true);
    }

    private MaceBuilding select(List<MaceBuilding> pool, int[] weights, String key) {
        MaceBuilding b = MaceLibrary.select(pool, weights, key, rng);
        return b != null ? b : pool.get(rng.nextInt(pool.size()));
    }

    // ── Moat and drawbridges ──────────────────────────────────────────────

    private void makeMoat() {
        int m = mapLength, e = EDGE;
        switch (moatType) {
            case "Drop to Bedrock" -> {
                for (int a = e - 1; a <= e + 5; a++) hollowLayers(a, m - a, 2, 63, a, m - a, null, 0);
                hollowLayers(e - 2, m - (e - 2), 64, 64, e - 2, m - (e - 2), data(Material.OAK_FENCE), 0);
            }
            case "Cactus" -> {
                for (int a = e - 1; a <= e + 5; a++) hollowLayers(a, m - a, 63, 63, a, m - a, data(Material.SAND), 0);
                for (int a = e + 1; a <= m / 2; a += 2) {
                    cactus(a, 64, e + 1);
                    cactus(a, 64, e + 3);
                }
                for (int a = e; a <= m / 2; a += 2) {
                    cactus(a, 64, e);
                    cactus(a, 64, e + 2);
                    cactus(a, 64, e + 4);
                }
                if (hasGuardTowers) {
                    for (int a = e + 3; a <= e + 13; a += 2) {
                        block(a, 64, e + 3, (BlockData) null, 2, 100);
                        block(a, 65, e + 3, (BlockData) null, 2, 100);
                    }
                }
            }
            case "Lava" -> {
                for (int a = e - 1; a <= e + 5; a++) {
                    hollowLayers(a, m - a, 55, 56, a, m - a, data(Material.LAVA), 0);
                    hollowLayers(a, m - a, 57, 63, a, m - a, null, 0);
                }
            }
            case "Fire" -> {
                for (int a = e - 1; a <= e + 5; a++) {
                    hollowLayers(a, m - a, 56, 56, a, m - a, data(Material.NETHERRACK), 0);
                    hollowLayers(a, m - a, 57, 57, a, m - a, data(Material.FIRE), 0);
                    hollowLayers(a, m - a, 58, 63, a, m - a, null, 0);
                }
            }
            default -> {   // Water
                for (int a = e - 1; a <= e + 5; a++) hollowLayers(a, m - a, 59, 63, a, m - a, data(Material.WATER), 0);
            }
        }
        int bridgeEnd = hasMoat ? -2 : 5;
        Material deck = moatType.equals("Lava") || moatType.equals("Fire") ? Material.STONE_BRICKS : Material.OAK_PLANKS;
        solidBox(m / 2 - 2, m / 2, 63, 63, e + bridgeEnd, e + 6, deck, 2);
        solidBox(m / 2 - 2, m / 2, 64, 65, e + bridgeEnd, e + 5, (BlockData) null, 2);
    }

    private void cactus(int x, int y, int z) {
        if (rng.nextDouble() > 0.5) {
            block(x, y, z, Material.CACTUS, 2, 100);
            block(x, y + 1, z, Material.CACTUS, 2, 50);
        }
    }

    // ── Paths: main streets, districts, and the buildings in them ─────────

    private static final int BLOCK_START = EDGE + 13;
    private record District(int x1, int z1, int x2, int z2) {}

    private final List<District> districts = new ArrayList<>();
    private final Set<String> streetsUsed = new HashSet<>();
    private final List<MaceBuilding> allBuildings = new ArrayList<>();
    /** Building ids in the area arrays (100 + id), as in Mace. */
    private final List<MaceBuilding> ids = new ArrayList<>();
    private boolean includedMineshaft;

    private int id(MaceBuilding b) {
        int i = ids.indexOf(b);
        if (i < 0) {
            ids.add(b);
            i = ids.size() - 1;
        }
        return i;
    }

    private int[][] makePaths() {
        includedMineshaft = !hasMineshaft;
        int plot = (1 + mapLength) - BLOCK_START * 2;
        int[][] area = new int[plot][plot];
        int up = plot - 1, half = plot / 2;
        if (hasMainStreets) {
            for (int a = 0; a <= up; a++) {
                for (int c = -pathExtends; c <= pathExtends; c++) {
                    area[a][up / 2 + c] = 1;
                    area[up / 2 + c][a] = 1;
                }
            }
            int lo = half - (pathExtends + 1), hi = half + (pathExtends + 1);
            splitArea(area, 0, 0, lo, lo);
            splitArea(area, hi, hi, up, up);
            splitArea(area, hi, 0, up, lo);
            splitArea(area, 0, hi, lo, up);
            Collections.shuffle(districts, rng);
        } else {
            splitArea(area, 0, 0, up, up);
        }
        for (District d : districts) {
            int[][] district = fillArea(area, d.x2() - d.x1(), d.z2() - d.z1(), d.x1(), d.z1(), mapLength - 2 * EDGE > 5 * 16);
            for (int x = 0; x < district.length - 2; x++) {
                for (int z = 0; z < district[0].length - 2; z++) area[d.x1() + x + 1][d.z1() + z + 1] = district[x + 1][z + 1];
            }
        }
        drawPaths(area);
        return area;
    }

    private void splitArea(int[][] area, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if ((x == x1 || x == x2 || z == z1 || z == z2) && area[x][z] < 500) area[x][z] = 1;
            }
        }
        boolean possible = true, byX = false;
        if (Math.abs(x1 - x2) > 50 && Math.abs(z1 - z2) > 50) byX = rng.nextDouble() > 0.5;
        else if (Math.abs(x1 - x2) > 50) byX = true;
        else if (Math.abs(z1 - z2) <= 50) possible = false;
        if (hasPaths && possible) {
            if (byX) {
                int split = next(x1 + 20, x2 - 20);
                splitArea(area, x1, z1, split, z2);
                splitArea(area, split, z1, x2, z2);
                streetSign(split - 1, z1 + 1, split - 1, z2 - 1);
            } else {
                int split = next(z1 + 20, z2 - 20);
                splitArea(area, x1, z1, x2, split);
                splitArea(area, x1, split, x2, z2);
                streetSign(x1 + 1, split - 1, x2 - 1, split - 1);
            }
        } else {
            districts.add(new District(x1, z1, x2, z2));
        }
    }

    /** Packs a district with buildings: the least wasted of many tries (Paths.FillArea). */
    private int[][] fillArea(int[][] area, int sx, int sz, int startX, int startZ, boolean uniqueBonus) {
        int[][] district = new int[sx][sz];
        int[][] best = new int[sx][sz];
        int wasted = sx * sz, attempts = 15;
        List<MaceBuilding> pool = lib.of(theme.file, "city");
        List<MaceBuilding> mineEntrances = lib.of(theme.file, "mineshaft_entrance");
        List<MaceBuilding> accepted = new ArrayList<>();
        do {
            List<MaceBuilding> here = new ArrayList<>();
            int bonus = 0, fails = 0;
            boolean needsMineshaft = !includedMineshaft && !mineEntrances.isEmpty();
            do {
                MaceBuilding b;
                if (needsMineshaft) {
                    b = select(mineEntrances, BUILDING_FREQUENCY, "frequency");
                    needsMineshaft = false;
                } else {
                    int guard = 0;
                    do {
                        b = select(pool, BUILDING_FREQUENCY, "frequency");
                    } while (!validBuilding(b, here, area, startX, startZ, sx, sz) && ++guard < 1000);
                }
                if (rng.nextDouble() > 0.5) district = rotateArray(district, rng.nextInt(4));
                boolean found = false;
                int x, z = 0;
                for (x = 0; x < district.length - b.width && !found; x++) {
                    for (z = 0; z < district[0].length - b.length && !found; z++) {
                        found = allZeros(district, x, z, x + b.width, z + b.length);
                    }
                }
                x--;
                z--;
                if (found) {
                    for (int a = x + 1; a <= x + b.width - 1; a++) for (int c = z + 1; c <= z + b.length - 1; c++) district[a][c] = 2;
                    if (MaceLibrary.unique(b) && uniqueBonus) bonus += 15;
                    here.add(b);
                    district[x + 1][z + 1] = 100 + id(b);
                    district[x + b.width - 1][z + b.length - 1] = 100 + id(b);
                    fails = 0;
                } else {
                    fails++;
                }
            } while (fails < 10);
            int curWasted = zeros(district) - bonus;
            if (curWasted < wasted) {
                best = copy(district);
                wasted = curWasted;
                attempts = 10;
                accepted = here;
            }
            district = new int[district.length][district[0].length];
            attempts--;
        } while (attempts > 0);
        if (sx == best[0].length) best = rotateArray(best, 1);
        allBuildings.addAll(accepted);
        includedMineshaft = true;
        return best;
    }

    private boolean validBuilding(MaceBuilding b, List<MaceBuilding> here, int[][] area, int startX, int startZ, int sx, int sz) {
        if (MaceLibrary.unique(b)) {
            if (allBuildings.contains(b) || here.contains(b)) return false;
            for (MaceBuilding h : here) if (MaceLibrary.unique(h)) return false;
            for (int x = startX - 8; x < startX + sx + 8; x++) {
                for (int z = startZ - 8; z < startZ + sz + 8; z++) {
                    if (x >= 0 && z >= 0 && x < area.length && z < area[0].length && area[x][z] >= 100
                            && area[x][z] - 100 < ids.size() && MaceLibrary.unique(ids.get(area[x][z] - 100))) {
                        return false;
                    }
                }
            }
            return true;
        }
        String f = MaceLibrary.frequency(b);
        switch (f) {
            case "very common", "common" -> {
                return true;
            }
            case "average", "rare", "very rare" -> {
                if (rng.nextDouble() > 0.975) return true;
                if (here.contains(b)) return false;
                int distance = f.equals("average") ? 12 : f.equals("rare") ? 25 : 50;
                int bid = ids.indexOf(b);
                for (int x = startX - distance; x < startX + sx + distance; x++) {
                    for (int z = startZ - distance; z < startZ + sz + distance; z++) {
                        if (x >= 0 && z >= 0 && x < area.length && z < area[0].length && bid >= 0 && area[x][z] - 100 == bid) {
                            return false;
                        }
                    }
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean allZeros(int[][] a, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) if (a[x][z] > 0) return false;
        return true;
    }

    private static int zeros(int[][] a) {
        int n = 0;
        for (int[] col : a) for (int v : col) if (v == 0) n++;
        return n;
    }

    private static int[][] copy(int[][] a) {
        int[][] out = new int[a.length][];
        for (int i = 0; i < a.length; i++) out[i] = a[i].clone();
        return out;
    }

    /** Mace's RotateArray (1 and 3 transpose, 2 turns it around). */
    private static int[][] rotateArray(int[][] a, int r) {
        if (r == 0) return a;
        int w = a.length, h = a[0].length;
        int[][] out = r == 2 ? new int[w][h] : new int[h][w];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                switch (r) {
                    case 1 -> out[y][x] = a[x][y];
                    case 2 -> out[x][y] = a[w - 1 - x][h - 1 - y];
                    default -> out[out.length - 1 - y][out[0].length - 1 - x] = a[x][y];
                }
            }
        }
        return out;
    }

    private void drawPaths(int[][] area) {
        int n = area.length, half = n / 2;
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                if (area[x][z] != 1) continue;
                int dx = Math.abs(x - half), dz = Math.abs(z - half);
                if (dx == pathExtends + 1 && dz == pathExtends + 1) {
                    // don't need these
                } else if (dx == pathExtends + 1 || dz == pathExtends + 1) {
                    if (hasMainStreets && neighbouringPaths(area, x, z)) set(BLOCK_START + x, 63, BLOCK_START + z, pathBlock);
                } else {
                    set(BLOCK_START + x, 63, BLOCK_START + z, pathBlock);
                }
                if (hasMainStreets && pathAlternative != null && x > 0 && z > 0 && x < n - 1 && z < n - 1
                        && (dx == pathExtends || dz == pathExtends) && dx >= pathExtends && dz >= pathExtends) {
                    set(BLOCK_START + x, 64, BLOCK_START + z, pathAlternative);
                }
            }
        }
        if (hasMainStreets) {
            for (int a = 0; a < n; a++) {
                for (int c = -pathExtends; c <= pathExtends; c++) {
                    area[a][(n - 1) / 2 + c] = 1;
                    area[(n - 1) / 2 + c][a] = 1;
                }
            }
        }
    }

    private static boolean neighbouringPaths(int[][] a, int x, int z) {
        int n = 0;
        if (x == 0 || a[x - 1][z] == 1) n++;
        if (z == 0 || a[x][z - 1] == 1) n++;
        if (x == a.length - 1 || a[x + 1][z] == 1) n++;
        if (z == a[0].length - 1 || a[x][z + 1] == 1) n++;
        return n == 4;
    }

    /** A post with a torch at each end of a new street, and its name on both. */
    private void streetSign(int x1, int z1, int x2, int z2) {
        x1 += BLOCK_START;
        z1 += BLOCK_START;
        x2 += BLOCK_START;
        z2 += BLOCK_START;
        for (int[] p : new int[][]{{x1, z1}, {x2, z2}}) {
            set(p[0], 64, p[1], Material.OAK_FENCE);
            set(p[0], 65, p[1], Material.OAK_PLANKS);
            set(p[0], 66, p[1], Material.TORCH);
        }
        if (z1 == z2) {
            x1--;
            x2++;
        } else {
            z1--;
            z2++;
        }
        String name = "", type = "";
        for (int i = 0; i < 100; i++) {
            name = String.valueOf(lib.words.line("adjectives", rng)).trim();
            type = String.valueOf(lib.words.line("roadtypes", rng)).trim();
            if (streetsUsed.add(name + " " + type)) break;
        }
        sign(x1, 65, z1, "~" + name + "~" + type + "~", Material.OAK_PLANKS, 0);
        sign(x2, 65, z2, "~" + name + "~" + type + "~", Material.OAK_PLANKS, 0);
    }

    // ── Buildings.MakeInsideCity ──────────────────────────────────────────

    /** Places the district buildings; returns the mineshaft entrance's middle, or {-1, -1}. */
    private int[] makeInsideCity(int[][] area) {
        int[] mine = {-1, -1};
        for (int x = 0; x < area.length; x++) {
            for (int z = 0; z < area[0].length; z++) {
                int v = area[x][z];
                if (v < 100 || v > 500 || v - 100 >= ids.size()) continue;
                MaceBuilding b = ids.get(v - 100);
                insert(b, x, z, BLOCK_START, area, -1, b.sourceY, false);
                if (b.type.equals("mineshaft_entrance")) {
                    mine = new int[]{BLOCK_START + x + b.width / 2, BLOCK_START + z + b.length / 2};
                    List<MaceBuilding> sky = lib.ofType("sky_feature");   // only listed in the Medieval theme
                    if (hasSkyFeature && !sky.isEmpty()) {
                        MaceBuilding balloon = select(sky, BUILDING_FREQUENCY, "frequency");
                        insert(balloon, x + (b.width - balloon.width) / 2, z + (b.length - balloon.length) / 2,
                                BLOCK_START, area, -1, balloon.sourceY, false);
                    }
                }
                int cx = x + b.width - 2, cz = z + b.length - 2;
                if (cx < area.length && cz < area[0].length) area[cx][cz] = 0;
            }
        }
        if (hasPaths) joinPathsToRoad();
        if (hasMainStreets && hasStreetLights) makeStreetLights();
        return mine;
    }

    /**
     * SourceWorld.InsertBuilding: a building at area (x, z) + blockStart, facing the nearest
     * street ({@code rotation} -1), or turned 0-3 (none, quarter left, quarter right, half).
     * Its air goes in too, except on the path layer (y64) and over planks (street signs), unless
     * it's a gatehouse or a mineshaft section.
     */
    private void insert(MaceBuilding b, int x1, int z1, int blockStart, int @Nullable [][] area, int rotation,
                        int yDest, boolean always) {
        int rot = rotation;
        if (rot < 0) {
            if (b.type.equals("mineshaft_section")) {
                rot = 0;
            } else if (area != null) {
                for (int d = 0; rot == -1 && d < 10; d++) {
                    for (int c = 0; rot == -1 && c <= b.width; c++) {
                        if (check(area, x1 + c, z1 - d) == 1) rot = 0;
                        else if (check(area, x1 - d, z1 + c) == 1) rot = 1;
                        else if (check(area, x1 + b.width + d, z1 + c) == 1) rot = 2;
                        else if (check(area, x1 + c, z1 + b.length + d) == 1) rot = 3;
                    }
                }
            }
            if (rot == -1) rot = rng.nextInt(4);
        }
        StructureRotation sr = switch (rot) {
            case 1 -> StructureRotation.COUNTERCLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_90;
            case 3 -> StructureRotation.CLOCKWISE_180;
            default -> StructureRotation.NONE;
        };
        boolean section = b.type.equals("mineshaft_section");
        MaceBuilding.Fill f = section ? SECTION_FILL : fill;
        MaceBuilding.Filled filled = b.fill(f, new Random(rng.nextLong()), sr);
        int ox = blockStart + x1, oz = blockStart + z1;
        boolean exempt = always || section;
        filled.forEachBlock((x, y, z, block) -> {
            int wx = ox + x, wy = yDest + y, wz = oz + z;
            if (!exempt) {
                Material here = type(wx, wy, wz);
                if ((wy == 64 && !here.isAir()) || here == Material.OAK_PLANKS) return;
            }
            set(wx, wy, wz, block);
        });
        filled.signs().forEach((p, lines) -> {
            long k = key(ox + p[0], yDest + p[1], oz + p[2]);
            if (get(ox + p[0], yDest + p[1], oz + p[2]).getMaterial().name().endsWith("SIGN")) signs.put(k, lines);
        });
        filled.containers().forEach((p, items) -> {
            long k = key(ox + p[0], yDest + p[1], oz + p[2]);
            if (!get(ox + p[0], yDest + p[1], oz + p[2]).getMaterial().isAir()) containers.put(k, items);
        });
    }

    /** Mineshaft sections sit below the ground layer: their ore stays ore. */
    private static final MaceBuilding.Fill SECTION_FILL = new MaceBuilding.Fill(Material.REDSTONE_ORE.createBlockData(),
            Material.GOLD_ORE.createBlockData(), Material.STONE.createBlockData(), true, null, t -> null, true, null);

    private static int check(int[][] area, int x, int z) {
        return x >= 0 && z >= 0 && x < area.length && z < area[0].length ? area[x][z] : 0;
    }

    /** Where a building's path stops one short of the main street, carry it across the kerb. */
    private void joinPathsToRoad() {
        int bs = BLOCK_START;
        int halfPlot = ((pathExtends / 2 + mapLength) - 2 * bs) / 2;
        for (int a = 0; a <= 2 * halfPlot; a++) {
            for (int b = -(pathExtends + 1); b <= pathExtends + 1; b += (pathExtends + 1) * 2) {
                int s = Integer.signum(b);
                if (same(bs + a, 63, bs + halfPlot + b + s, pathBlock) && same(bs + a, 63, bs + halfPlot + (b - s), pathBlock)
                        && (same(bs + a, 63, bs + halfPlot + b, ground) || same(bs + a, 63, bs + halfPlot + b, pathBlock))
                        && type(bs + a, 64, bs + halfPlot + b).isAir()) {
                    set(bs + a, 63, bs + halfPlot + b, pathBlock);
                    set(bs + a, 64, bs + halfPlot + b - s, (BlockData) null);
                }
                if (same(bs + halfPlot + b + s, 63, bs + a, pathBlock) && same(bs + halfPlot + (b - s), 63, bs + a, pathBlock)
                        && (same(bs + halfPlot + b, 63, bs + a, ground) || same(bs + halfPlot + b, 63, bs + a, pathBlock))
                        && type(bs + halfPlot + b, 64, bs + a).isAir()) {
                    set(bs + halfPlot + b, 63, bs + a, pathBlock);
                    set(bs + halfPlot + b - s, 64, bs + a, (BlockData) null);
                }
            }
        }
    }

    private boolean same(int x, int y, int z, BlockData b) {
        return get(x, y, z).getMaterial() == b.getMaterial();
    }

    private void makeStreetLights() {
        int m = mapLength, p = pathExtends;
        boolean glow = streetLightType.equals("Glowstones");
        for (int a = p + 1; a <= m / 2 - (EDGE + 16); a += 8) {
            streetLight(glow, m / 2 - a, m / 2 - (p + 1), m / 2 - a, m / 2 - p);
            streetLight(glow, m / 2 - a, m / 2 + (p + 1), m / 2 - a, m / 2 + p);
            streetLight(glow, m / 2 + a, m / 2 - (p + 1), m / 2 + a, m / 2 - p);
            streetLight(glow, m / 2 + a, m / 2 + (p + 1), m / 2 + a, m / 2 + p);
            streetLight(glow, m / 2 - (p + 1), m / 2 - a, m / 2 - p, m / 2 - a);
            streetLight(glow, m / 2 + (p + 1), m / 2 - a, m / 2 + p, m / 2 - a);
            streetLight(glow, m / 2 - (p + 1), m / 2 + a, m / 2 - p, m / 2 + a);
            streetLight(glow, m / 2 + (p + 1), m / 2 + a, m / 2 + p, m / 2 + a);
        }
    }

    private void streetLight(boolean glow, int x1, int z1, int x2, int z2) {
        if (!same(x1, 63, z1, ground) || !type(x1, 64, z1).isAir() || !type(x1, 65, z1).isAir()) return;
        for (int y = 64; y <= 68; y++) set(x1, y, z1, Material.OAK_FENCE);
        set(x2, 68, z2, Material.OAK_FENCE);
        if (glow) {
            set(x2, 67, z2, Material.GLOWSTONE);
        } else {
            set(x2, 67, z2, Material.OAK_PLANKS);
            if (z1 == z2) {
                torch(x2, 67, z2 - 1, Material.OAK_PLANKS, 0);
                torch(x2, 67, z2 + 1, Material.OAK_PLANKS, 0);
            } else {
                torch(x2 - 1, 67, z2, Material.OAK_PLANKS, 0);
                torch(x2 + 1, 67, z2, Material.OAK_PLANKS, 0);
            }
        }
    }

    // ── Mineshaft: seven levels, joined by the entrance's ladder ──────────

    private enum Mb { NATURAL, AIR, PLACEHOLDER, ENTRANCE, RAIL_SUPPORT, SUPPORT, CHEST_TORCH, RAIL, CEILING_SUPPORT, UNUSED9, STRUCTURE }

    private static final int OV_AIR = 1, OV_PLACEHOLDER = 2, OV_STRUCTURE = 10;

    /** Mineshaft.xml: per level, the resources its chests hold (weights) and the torch chance. */
    private static final String[][] LEVEL_RESOURCES = {
        {"gravel:10", "sand:10", "dirt:10", "sandstone:10", "cobblestone:10", "wood:5", "torches:5", "coal:5"},
        {"gravel:10", "sand:10", "dirt:10", "sandstone:10", "cobblestone:10", "wood:10", "torches:10", "coal:10", "iron:5"},
        {"gravel:5", "sand:5", "dirt:5", "sandstone:5", "cobblestone:5", "wood:10", "torches:10", "coal:10", "iron:10", "lapis:5"},
        {"dirt:5", "sandstone:5", "cobblestone:5", "wood:5", "torches:10", "coal:10", "iron:10", "lapis:10", "redstone:5"},
        {"cobblestone:5", "wood:5", "torches:5", "coal:10", "iron:10", "lapis:10", "redstone:10", "gold:5"},
        {"torches:5", "coal:5", "iron:10", "lapis:10", "redstone:10", "gold:10", "diamond:5"},
        {"iron:5", "lapis:10", "redstone:10", "gold:10", "diamond:10", "obsidian:5"},
    };
    private static final int[] TORCH_CHANCE = {12, 10, 8, 6, 4, 2, 0};
    private static final Map<String, Object[]> RESOURCES = Map.ofEntries(   // item, min, max
        Map.entry("gravel", new Object[]{Material.GRAVEL, 64, 192}), Map.entry("sand", new Object[]{Material.SAND, 64, 192}),
        Map.entry("dirt", new Object[]{Material.DIRT, 64, 192}), Map.entry("sandstone", new Object[]{Material.SANDSTONE, 32, 64}),
        Map.entry("cobblestone", new Object[]{Material.COBBLESTONE, 32, 64}), Map.entry("wood", new Object[]{Material.OAK_LOG, 32, 64}),
        Map.entry("torches", new Object[]{Material.TORCH, 10, 20}), Map.entry("coal", new Object[]{Material.COAL, 4, 8}),
        Map.entry("iron", new Object[]{Material.IRON_ORE, 2, 6}), Map.entry("lapis", new Object[]{Material.LAPIS_ORE, 1, 5}),
        Map.entry("redstone", new Object[]{Material.REDSTONE, 4, 16}), Map.entry("gold", new Object[]{Material.GOLD_ORE, 1, 4}),
        Map.entry("diamond", new Object[]{Material.DIAMOND, 1, 3}), Map.entry("obsidian", new Object[]{Material.OBSIDIAN, 1, 3}));

    private int mineBlockStart;

    private void makeMineshaft(int[] entrance) {
        mineBlockStart = EDGE + 13;
        int size = (1 + mapLength) - mineBlockStart * 2;
        if (size % 5 > 0) size += 5 - size % 5;
        mineBlockStart -= 2;
        for (int depth = 1; depth <= 7; depth++) makeLevel(depth, size, entrance);
        int ex = entrance[0], ez = entrance[1];
        BlockData top = get(ex, 63, ez + 1);
        solidBox(ex, ex, 3, 63, ez + 1, ez + 1, Material.OAK_LOG, 0);
        ladder(ex, 4, 63, ez, 0, Material.OAK_LOG);
        solidBox(ex, ex, 3, 63, ez + 1, ez + 1, Material.STONE, 0);
        set(ex, 63, ez + 1, top);
    }

    private void makeLevel(int depth, int size, int[] entrance) {
        String[] resources = LEVEL_RESOURCES[depth - 1];
        Mb[][] full = new Mb[size + MULTIPLIER * 2][size + MULTIPLIER * 2];
        for (Mb[] row : full) java.util.Arrays.fill(row, Mb.NATURAL);
        int xOrig = entrance[0] - mineBlockStart, zOrig = entrance[1] - mineBlockStart;
        mineBlockStart -= 2;
        int[][] ov = new int[full.length / MULTIPLIER][full[0].length / MULTIPLIER];
        int xPos = Math.max(0, Math.min(ov.length - 1, xOrig / MULTIPLIER));
        int zPos = Math.max(0, Math.min(ov[0].length - 1, zOrig / MULTIPLIER));
        ov[xPos][zPos] = OV_AIR;
        routeXPlus(ov, xPos + 1, zPos, 0);
        routeZPlus(ov, xPos, zPos + 1, 1);
        routeXMinus(ov, xPos - 1, zPos, 2);
        routeZMinus(ov, xPos, zPos - 1, 3);
        int offX = (xOrig - xPos * MULTIPLIER) - 2, offZ = (zOrig - zPos * MULTIPLIER) - 2;

        record Section(int x, int z, MaceBuilding b) {}
        List<Section> sections = new ArrayList<>();
        ov[xPos][zPos] = OV_PLACEHOLDER;
        List<MaceBuilding> placed = addSections(ov, depth);
        ov[xPos][zPos] = OV_AIR;
        for (int x = 0; x < ov.length; x++) {
            for (int z = 0; z < ov[0].length; z++) {
                if (ov[x][z] >= 100) {
                    MaceBuilding b = placed.get(ov[x][z] - 100);
                    sections.add(new Section(x * MULTIPLIER + offX - 1, z * MULTIPLIER + offZ - 1, b));
                    for (int x2 = x; x2 <= x + (b.width - 1) / 7 && x2 < ov.length; x2++) {
                        for (int z2 = z; z2 <= z + (b.length - 1) / 7 && z2 < ov[0].length; z2++) {
                            if (ov[x2][z2] >= 100 && placed.get(ov[x2][z2] - 100) == b) ov[x2][z2] = OV_STRUCTURE;
                        }
                    }
                }
            }
        }
        for (int x = 4; x < full.length - 4; x++) {
            for (int z = 4; z < full[0].length - 4; z++) {
                if (x / MULTIPLIER >= ov.length || z / MULTIPLIER >= ov[0].length) continue;
                Mb here = at(full, x + offX, z + offZ);
                if (here == Mb.NATURAL || here == Mb.AIR || here == Mb.PLACEHOLDER || here == Mb.ENTRANCE) {
                    put(full, x + offX, z + offZ, ovBlock(ov[x / MULTIPLIER][z / MULTIPLIER]));
                }
                if ((x + 3) % 5 != 0 || (z + 3) % 5 != 0 || ov[x / MULTIPLIER][z / MULTIPLIER] != OV_AIR) continue;
                int east = ovAt(ov, x / MULTIPLIER + 1, z / MULTIPLIER), south = ovAt(ov, x / MULTIPLIER, z / MULTIPLIER + 1);
                if (east == OV_AIR) {
                    for (int i = 0; i < 5; i++) {
                        Mb s = i == 1 || i == 3 ? Mb.CEILING_SUPPORT : Mb.SUPPORT;
                        put(full, x + offX + 3, z + offZ + i - 2, s);
                        put(full, x + offX + 2, z + offZ + i - 2, s);
                    }
                    for (int i = 0; i <= 5; i++) {
                        put(full, x + offX + i, z + offZ, at(full, x + offX + i, z + offZ) == Mb.SUPPORT ? Mb.RAIL_SUPPORT : Mb.RAIL);
                    }
                }
                if (south == OV_AIR) {
                    for (int i = 0; i < 5; i++) {
                        Mb s = i == 1 || i == 3 ? Mb.CEILING_SUPPORT : Mb.SUPPORT;
                        put(full, x + offX + i - 2, z + offZ + 3, s);
                        put(full, x + offX + i - 2, z + offZ + 2, s);
                    }
                    for (int i = 0; i <= 5; i++) {
                        put(full, x + offX, z + offZ + i, at(full, x + offX, z + offZ + i) == Mb.SUPPORT ? Mb.RAIL_SUPPORT : Mb.RAIL);
                    }
                }
                chestOrTorch(ov, full, (x - 3) / MULTIPLIER, z / MULTIPLIER, x + offX - 2, z + offZ);
                chestOrTorch(ov, full, (x + 3) / MULTIPLIER, z / MULTIPLIER, x + offX + 2, z + offZ);
                chestOrTorch(ov, full, x / MULTIPLIER, (z - 3) / MULTIPLIER, x + offX, z + offZ - 2);
                chestOrTorch(ov, full, x / MULTIPLIER, (z + 3) / MULTIPLIER, x + offX, z + offZ + 2);
            }
        }
        put(full, xOrig, zOrig, Mb.ENTRANCE);

        Material support = List.of(Material.OAK_LOG, Material.OAK_PLANKS, Material.OAK_FENCE).get(rng.nextInt(3));
        int bs = mineBlockStart, y0 = 38 - 5 * depth;
        for (int x = 0; x < full.length; x++) {
            for (int z = 0; z < full[0].length; z++) {
                int wx = x + bs, wz = z + bs;
                if (depth <= 4 && type(wx, y0 + 4, wz) == Material.GRAVEL) set(wx, y0 + 4, wz, Material.STONE);
                if (depth <= 2 && (type(wx, y0 + 4, wz) == Material.SAND || type(wx, y0 + 4, wz) == Material.SANDSTONE)) {
                    set(wx, y0 + 4, wz, Material.DIRT);
                }
                switch (full[x][z]) {
                    case NATURAL, STRUCTURE -> { }
                    case AIR -> {
                        for (int y = y0 + 1; y <= y0 + 3; y++) set(wx, y, wz, (BlockData) null);
                    }
                    case ENTRANCE, RAIL -> {
                        set(wx, y0, wz, Material.GRAVEL);
                        set(wx, y0 + 1, wz, Material.RAIL);
                        set(wx, y0 + 2, wz, (BlockData) null);
                        set(wx, y0 + 3, wz, (BlockData) null);
                    }
                    case RAIL_SUPPORT -> {
                        set(wx, y0, wz, Material.GRAVEL);
                        set(wx, y0 + 1, wz, Material.RAIL);
                        set(wx, y0 + 2, wz, (BlockData) null);
                        set(wx, y0 + 3, wz, support);
                    }
                    case SUPPORT -> {
                        for (int y = y0 + 1; y <= y0 + 3; y++) set(wx, y, wz, support);
                    }
                    case CHEST_TORCH -> {
                        for (int y = y0 + 1; y <= y0 + 3; y++) {
                            if (y == y0 + 1 && rng.nextDouble() > 0.9) {
                                set(wx, y, wz, Material.CHEST);
                                containers.put(key(wx, y, wz), mineChest(resources));
                            } else if (y == y0 + 3 && rng.nextDouble() < TORCH_CHANCE[depth - 1] / 100.0) {
                                BlockFace f = at(full, x - 1, z) == Mb.NATURAL ? BlockFace.EAST
                                        : at(full, x + 1, z) == Mb.NATURAL ? BlockFace.WEST
                                        : at(full, x, z - 1) == Mb.NATURAL ? BlockFace.SOUTH : BlockFace.NORTH;
                                set(wx, y, wz, facing(Material.WALL_TORCH, f));
                            } else {
                                set(wx, y, wz, (BlockData) null);
                            }
                        }
                    }
                    case CEILING_SUPPORT -> {
                        set(wx, y0 + 1, wz, (BlockData) null);
                        set(wx, y0 + 2, wz, (BlockData) null);
                        set(wx, y0 + 3, wz, support);
                    }
                    case UNUSED9 -> {
                        for (int y = y0 + 1; y <= 41 - 5 * (depth - 1); y++) set(wx, y, wz, (BlockData) null);
                    }
                    default -> { }
                }
            }
        }
        for (Section s : sections) insert(s.b(), s.x(), s.z(), bs, null, -1, y0, true);
        mineBlockStart += 2;
    }

    private static Mb ovBlock(int v) {
        return switch (v) {
            case OV_AIR -> Mb.AIR;
            case OV_PLACEHOLDER -> Mb.PLACEHOLDER;
            case OV_STRUCTURE -> Mb.STRUCTURE;
            default -> v >= 100 ? Mb.STRUCTURE : Mb.NATURAL;
        };
    }

    private static Mb at(Mb[][] a, int x, int z) {
        return x >= 0 && z >= 0 && x < a.length && z < a[0].length ? a[x][z] : Mb.NATURAL;
    }

    private static void put(Mb[][] a, int x, int z, Mb v) {
        if (x >= 0 && z >= 0 && x < a.length && z < a[0].length) a[x][z] = v;
    }

    private static int ovAt(int[][] ov, int x, int z) {
        return x >= 0 && z >= 0 && x < ov.length && z < ov[0].length ? ov[x][z] : 0;
    }

    private void chestOrTorch(int[][] ov, Mb[][] full, int ax, int az, int mx, int mz) {
        if (ovAt(ov, ax, az) == 0) put(full, mx, mz, Mb.CHEST_TORCH);
    }

    private ItemStack[] mineChest(String[] resources) {
        int total = 0;
        for (String r : resources) total += Integer.parseInt(r.split(":")[1]);
        int roll = rng.nextInt(total);
        String name = resources[0].split(":")[0];
        for (String r : resources) {
            roll -= Integer.parseInt(r.split(":")[1]);
            if (roll < 0) {
                name = r.split(":")[0];
                break;
            }
        }
        Object[] res = RESOURCES.get(name);
        Material item = (Material) res[0];
        int amount = (int) res[1] + rng.nextInt((int) res[2] - (int) res[1] + 1);
        ItemStack[] out = new ItemStack[27];
        for (int slot = 0; amount > 0 && slot < 27; slot++, amount -= 64) out[slot] = new ItemStack(item, Math.min(64, amount));
        return out;
    }

    /** AddMineshaftSections: a fifteenth as many sections as route cells, where their patterns fit. */
    private List<MaceBuilding> addSections(int[][] ov, int depth) {
        List<MaceBuilding> placed = new ArrayList<>();
        List<MaceBuilding> pool = lib.of(theme.file, "mineshaft_section");
        int cells = 0;
        for (int[] col : ov) for (int v : col) cells += v;
        int fails = 0, count = 0;
        Set<MaceBuilding> used = new HashSet<>();
        while (fails < 10 && count < cells / 15 && !pool.isEmpty()) {
            MaceBuilding s = MaceLibrary.select(pool, BUILDING_FREQUENCY, "frequency" + depth, rng);
            if (s == null) break;
            if (MaceLibrary.unique(s) && used.contains(s)) continue;
            String pattern = s.meta("pattern");
            int[] at = pattern == null ? null : matchPattern(ov, pattern.split(" "));
            if (at == null) {
                fails++;
                continue;
            }
            int px = parse(s.meta("pattern_position_x")), pz = parse(s.meta("pattern_position_z"));
            int sx = Math.max(1, (s.width - 1) / 6), sz = Math.max(1, (s.length - 1) / 6);
            placed.add(s);
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    if (at[0] + px + x < ov.length && at[1] + pz + z < ov[0].length) ov[at[0] + px + x][at[1] + pz + z] = 100 + placed.size() - 1;
                }
            }
            count++;
            if (MaceLibrary.unique(s)) used.add(s);
        }
        return placed;
    }

    private static int parse(@Nullable String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int @Nullable [] matchPattern(int[][] a, String[] pattern) {
        int n = pattern[0].length();
        List<int[]> found = new ArrayList<>();
        for (int x = 0; x <= a.length - 1 - n; x++) {
            for (int z = 0; z <= a[0].length - 1 - n; z++) {
                boolean ok = true;
                for (int dx = 0; dx < n && ok; dx++) {
                    for (int dz = 0; dz < n && ok; dz++) {
                        int v = a[x + dx][z + dz];
                        char p = pattern[dz].charAt(n - 1 - dx);
                        ok = v <= 1 && (p == '?' || p - '0' == v);
                    }
                }
                if (ok) found.add(new int[]{x, z});
            }
        }
        return found.isEmpty() ? null : found.get(rng.nextInt(found.size()));
    }

    private static boolean zeros(int[][] a, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (x < 0 || z < 0 || x >= a.length || z >= a[0].length || a[x][z] > 0) return false;
            }
        }
        return true;
    }

    // Mace's CreateRoute{X,Z}{Plus,Minus}: run a corridor a random way, branching now and then.
    private void routeXPlus(int[][] a, int x0, int z, int invalid) {
        if (a.length - x0 <= 2) return;
        int len = next((a.length - x0) / 2, a.length - x0);
        for (int x = x0 + 1; x < x0 + len; x++) {
            if (!zeros(a, x, z - 1, x + 1, z + 1)) break;
            a[x0][z] = 1;
            a[x][z] = 1;
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 3) routeZPlus(a, x, z, invalid);
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 1) routeZMinus(a, x, z, invalid);
        }
    }

    private void routeXMinus(int[][] a, int x0, int z, int invalid) {
        if (x0 <= 2) return;
        int len = next(x0 / 2, x0 + 1);
        for (int x = x0 - 1; x > x0 - len; x--) {
            if (!zeros(a, x - 1, z - 1, x, z + 1)) break;
            a[x0][z] = 1;
            a[x][z] = 1;
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 3) routeZPlus(a, x, z, invalid);
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 1) routeZMinus(a, x, z, invalid);
        }
    }

    private void routeZPlus(int[][] a, int x, int z0, int invalid) {
        if (a[0].length - z0 <= 2) return;
        int len = next((a[0].length - z0) / 2, a[0].length - z0);
        for (int z = z0 + 1; z < z0 + len; z++) {
            if (!zeros(a, x - 1, z, x + 1, z + 1)) break;
            a[x][z0] = 1;
            a[x][z] = 1;
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 2) routeXPlus(a, x, z, invalid);
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 0) routeXMinus(a, x, z, invalid);
        }
    }

    private void routeZMinus(int[][] a, int x, int z0, int invalid) {
        if (z0 <= 2) return;
        int len = next(z0 / 2, z0 + 1);
        for (int z = z0 - 1; z > z0 - len; z--) {
            if (!zeros(a, x - 1, z - 1, x + 1, z)) break;
            a[x][z0] = 1;
            a[x][z] = 1;
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 2) routeXPlus(a, x, z, invalid);
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 0) routeXMinus(a, x, z, invalid);
        }
    }

    // ── Guard towers at the corners ───────────────────────────────────────

    private void makeGuardTowers() {
        int e = EDGE, m = mapLength;
        Material w = wall.getMaterial();
        solidBox(e + 5, e + 11, 64, 79, e + 5, e + 11, (BlockData) null, 1);
        hollowBox(e + 4, e + 12, 63, 80, e + 4, e + 12, wall, 1);
        solidBox(e + 4, e + 12, 2, 72, e + 4, e + 12, wall, 1);   // two rooms, on a solid base
        solidBox(e + 5, e + 11, 64, 67, e + 5, e + 11, (BlockData) null, 1);
        if (hasOutsideLights) {
            if (outsideLightType.equals("Fire")) {
                block(e + 5, 76, e + 3, Material.NETHERRACK, 2, 100);
                block(e + 5, 77, e + 3, Material.FIRE, 2, 100);
                block(e + 11, 76, e + 3, Material.NETHERRACK, 2, 100);
                block(e + 11, 77, e + 3, Material.FIRE, 2, 100);
            } else {
                for (int y = 73; y <= 80; y += 7) {
                    torch(e + 6, y, e + 3, w, 2);
                    torch(e + 10, y, e + 3, w, 2);
                }
            }
        }
        if (hasTorchesOnWalkways) {
            torch(e + 6, 79, e + 13, w, 2);
            torch(e + 10, 79, e + 13, w, 2);
            torch(e + 6, 77, e + 11, w, 2);
            torch(e + 10, 77, e + 11, w, 2);
            torch(e + 5, 77, e + 6, w, 2);
            torch(e + 5, 77, e + 10, w, 2);
        }
        block(e + 7, 73, e + 12, (BlockData) null, 2, 100);
        block(e + 9, 73, e + 12, (BlockData) null, 2, 100);
        block(e + 7, 74, e + 12, (BlockData) null, 2, 100);
        block(e + 9, 74, e + 12, (BlockData) null, 2, 100);
        hollowLayers(e + 4, e + 12, 81, 81, e + 4, e + 12, wall, 1);
        for (int x = e + 4; x <= e + 12; x += 2) {
            for (int z = e + 4; z <= e + 12; z += 2) {
                if (x == e + 4 || x == e + 12 || z == e + 4 || z == e + 12) block(x, 82, z, wall, 1, 100);
            }
        }
        solidBox(e + 8, e + 8, 73, 82, e + 8, e + 8, wall, 1);
        ladder(e + 7, 73, 82, e + 8, 2, w);
        ladder(e + 9, 73, 82, e + 8, 2, w);
        if (hasTorchesOnWalkways) {
            block(e + 6, 81, e + 6, Material.TORCH, 1, 100);
            block(e + 6, 81, e + 10, Material.TORCH, 2, 100);
            block(e + 10, 81, e + 10, Material.TORCH, 1, 100);
        }
        block(e + 5, 79, e + 5, Material.COBWEB, 1, 30);
        block(e + 5, 79, e + 11, Material.COBWEB, 1, 30);
        block(e + 11, 79, e + 5, Material.COBWEB, 1, 30);
        block(e + 11, 79, e + 11, Material.COBWEB, 1, 30);
        guardChest(e + 11, 73, e + 11, w);
        guardChest(m - (e + 11), 73, e + 11, w);
        guardChest(e + 11, 73, m - (e + 11), w);
        guardChest(m - (e + 11), 73, m - (e + 11), w);
        solidBox(e + 4, e + 4, 74, 77, e + 8, e + 8, (BlockData) null, 2);
        solidBox(e + 4, e + 4, 76, 76, e + 7, e + 9, (BlockData) null, 2);
        if (!hasWalls) ladder(e + 13, 64, 72, e + 8, 2, w);
        bed(e + 5, e + 6, 64, e + 8, e + 8, 2);
        bed(e + 5, e + 6, 64, e + 10, e + 10, 2);
        bed(e + 11, e + 10, 64, e + 8, e + 8, 2);
        // logs first, so the ladder and torches find something to face away from, then wall
        solidBox(e + 5, e + 5, 64, 73, e + 5, e + 5, Material.OAK_LOG, 2);
        solidBox(e + 11, e + 11, 64, 71, e + 5, e + 5, Material.OAK_LOG, 2);
        solidBox(e + 5, e + 5, 64, 71, e + 11, e + 11, Material.OAK_LOG, 2);
        solidBox(e + 11, e + 11, 64, 71, e + 11, e + 11, Material.OAK_LOG, 2);
        ladder(e + 5, 64, 73, e + 6, 2, Material.OAK_LOG);
        if (hasTorchesOnWalkways) {
            torch(e + 10, 66, e + 5, Material.OAK_LOG, 2);
            torch(e + 6, 66, e + 11, Material.OAK_LOG, 2);
            torch(e + 10, 66, e + 11, Material.OAK_LOG, 2);
        }
        solidBox(e + 5, e + 5, 64, 73, e + 5, e + 5, wall, 2);
        solidBox(e + 11, e + 11, 64, 71, e + 5, e + 5, wall, 2);
        solidBox(e + 5, e + 5, 64, 71, e + 11, e + 11, wall, 2);
        solidBox(e + 11, e + 11, 64, 71, e + 11, e + 11, wall, 2);
        block(e + 11, 67, e + 8, Material.COBWEB, 2, 75);
        block(e + 11, 65, e + 11, Material.GOLD_BLOCK, 1, 100);
        block(e + 11, 64, e + 11, Material.GOLD_BLOCK, 1, 100);
        door(e + 11, 64, e + 12, Material.GOLD_BLOCK, true, 2);
        block(e + 11, 65, e + 11, (BlockData) null, 1, 100);
        block(e + 11, 64, e + 11, (BlockData) null, 1, 100);
        block(e + 11, 64, e + 11, Material.STONE_PRESSURE_PLATE, 1, 100);
        block(e + 11, 64, e + 13, Material.STONE_PRESSURE_PLATE, 2, 100);
        sign(e + 12, 65, e + 13, "~Guard Tower~~", w, 1);
        sign(e + 8, 74, e + 13, "~Guard Tower~~", w, 2);
        if (hasTowerAddition) {
            if (towerAdditionType.equals("Fire beacon")) {
                solidBox(e + 8, e + 8, 83, 84, e + 8, e + 8, wall, 1);
                solidBox(e + 6, e + 10, 85, 85, e + 6, e + 10, wall, 1);
                solidBox(e + 6, e + 10, 86, 86, e + 6, e + 10, Material.NETHERRACK, 1);
                solidBox(e + 6, e + 10, 87, 87, e + 6, e + 10, Material.FIRE, 1);
            } else if (!lib.flags.isEmpty()) {
                solidBox(e + 4, e + 4, 83, 91, e + 12, e + 12, Material.OAK_FENCE, 2);
                block(e + 4, 84, e + 13, Material.OAK_FENCE, 2, 100);
                block(e + 4, 91, e + 13, Material.OAK_FENCE, 2, 100);
                List<Integer> colours = new ArrayList<>(java.util.stream.IntStream.range(0, 15).boxed().toList());
                Collections.shuffle(colours, rng);
                List<String> flag = lib.flags.get(rng.nextInt(lib.flags.size()));
                for (int x = 0; x < flag.get(0).length(); x++) {
                    for (int y = 0; y < flag.size(); y++) {
                        int digit = Character.digit(flag.get(y).charAt(Math.min(x, flag.get(y).length() - 1)), 10);
                        BlockData wool = WOOL[colours.get(Math.max(0, Math.min(14, digit)))];
                        solidBox(e + 4, e + 4, 90 - y, 90 - y, e + 13 + x, e + 13 + x, wool, 2);
                    }
                }
            }
        }
    }

    private static final BlockData[] WOOL = java.util.stream.Stream.of(
        Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
        Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
        Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
        Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL
    ).map(Material::createBlockData).toArray(BlockData[]::new);

    private void guardChest(int x, int y, int z, Material against) {
        ItemStack[] items = new ItemStack[27];
        for (int a = 0; a < 5; a++) {
            items[a] = new ItemStack(List.of(Material.IRON_SWORD, Material.WOODEN_SWORD, Material.STONE_SWORD).get(rng.nextInt(3)));
        }
        items[6] = new ItemStack(Material.BOW);
        items[7] = new ItemStack(Material.ARROW, 64);
        Material[][] sets = {
            {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS},
            {Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS},
            {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS},
        };
        Material[] armour = sets[rng.nextInt(3)];
        for (int a = 9; a < 18; a++) items[a] = new ItemStack(armour[rng.nextInt(4)]);
        chest(x, y, z, against, items);
    }

    // ── Farms outside the north wall ──────────────────────────────────────

    private void makeFarms() {
        int[][] area = fillFarms(mapLength - 12, farmLength - 4);
        List<MaceBuilding> pool = lib.of(theme.file, "farm");
        for (int x = 0; x < area.length; x++) {
            for (int z = 0; z < area[0].length; z++) {
                int v = area[x][z];
                if (v < 100 || v > 500 || v - 100 >= ids.size()) continue;
                MaceBuilding b = ids.get(v - 100);
                if (b.width >= 10 && rng.nextDouble() > 0.8) {
                    if (rng.nextDouble() > 0.5) pond(6 + x, b.width, (6 - farmLength) + z, b.length);
                    else hill(6 + x, b.width, (6 - farmLength) + z, b.length);
                } else {
                    insert(b, 6 + x, (6 - farmLength) + z, 0, null, -1, b.sourceY, false);
                }
                int cx = x + b.width - 2, cz = z + b.length - 2;
                if (cx < area.length && cz < area[0].length) area[cx][cz] = 0;
            }
        }
    }

    private int[][] fillFarms(int sx, int sz) {
        List<MaceBuilding> pool = lib.of(theme.file, "farm");
        int[][] district = new int[sx][sz];
        int[][] best = new int[sx][sz];
        if (pool.isEmpty() || sz <= 0) return best;
        int wasted = sx * sz, attempts = 15;
        do {
            List<MaceBuilding> here = new ArrayList<>();
            int bonus = 0, fails = 0;
            do {
                MaceBuilding b;
                int guard = 0;
                do {
                    b = select(pool, FARM_FREQUENCY, "frequency");
                } while (MaceLibrary.unique(b) && here.contains(b) && ++guard < 1000);
                if (rng.nextDouble() > 0.5) district = rotateArray(district, rng.nextInt(4));
                boolean found = false;
                int x, z = 0;
                for (x = 0; x < district.length - b.width && !found; x++) {
                    for (z = 0; z < district[0].length - b.length && !found; z++) {
                        found = allZeros(district, x, z, x + b.width, z + b.length);
                    }
                }
                x--;
                z--;
                if (found) {
                    for (int a = x + 1; a <= x + b.width - 1; a++) for (int c = z + 1; c <= z + b.length - 1; c++) district[a][c] = 2;
                    if (MaceLibrary.unique(b)) bonus += 15;
                    here.add(b);
                    district[x + 1][z + 1] = 100 + id(b);
                    district[x + b.width - 1][z + b.length - 1] = 100 + id(b);
                    fails = 0;
                } else {
                    fails++;
                }
            } while (fails < 10);
            int cur = zeros(district) - bonus;
            if (cur < wasted) {
                best = copy(district);
                wasted = cur;
                attempts = 10;
            }
            district = new int[district.length][district[0].length];
            attempts--;
        } while (attempts > 0);
        if (sx == best[0].length) best = rotateArray(best, 1);
        return best;
    }

    private void pond(int x, int xlen, int z, int zlen) {
        int[][] a = blob(xlen, zlen, 12, 2, 4);
        a = neighbours(a, 0, 0, 7, 0, next(3, 6), true);
        a = neighbours(a, 6, 0, 2, 2, 1, false);
        a = neighbours(a, 14, 0, 3, 3, 1, false);
        boolean lava = rng.nextDouble() < 0.15;
        for (int i = 1; i < a.length - 1; i++) {
            for (int j = 1; j < a[0].length - 1; j++) {
                if (a[i][j] == 1) {
                    if (!lava && rng.nextDouble() < 0.05) set(x + i, 64, z + j, Material.SUGAR_CANE);
                    else set(x + i, 64, z + j, rng.nextBoolean() ? Material.SHORT_GRASS : Material.FERN);
                } else {
                    for (int y = 2; y <= a[i][j]; y++) {
                        set(x + i, 65 - y, z + j, lava ? Material.LAVA : Material.WATER);
                        if (!lava && y == 2 && rng.nextDouble() < 0.1) set(x + i, 66 - y, z + j, Material.LILY_PAD);
                    }
                }
            }
        }
    }

    private void hill(int x, int xlen, int z, int zlen) {
        int[][] a = blob(xlen, zlen, 8, 3, 5);
        a = neighbours(a, 0, 0, 7, 0, next(7, 11), true);
        a = neighbours(a, 6, 0, 3, 2, 1, false);
        a = neighbours(a, 14, 0, 3, 3, 1, false);
        for (int i = 1; i < a.length - 1; i++) {
            for (int j = 1; j < a[0].length - 1; j++) {
                for (int y = 1; y <= a[i][j]; y++) {
                    if (y == a[i][j]) {
                        set(x + i, y + 63, z + j, Material.GRASS_BLOCK);
                        switch (rng.nextInt(10)) {
                            case 0 -> set(x + i, y + 64, z + j, Material.POPPY);
                            case 1 -> set(x + i, y + 64, z + j, Material.DANDELION);
                            case 2, 3 -> set(x + i, y + 64, z + j, rng.nextBoolean() ? Material.SHORT_GRASS : Material.FERN);
                            default -> { }
                        }
                    } else {
                        set(x + i, y + 63, z + j, Material.DIRT);
                    }
                }
            }
        }
    }

    /** A filled rectangle with, on bigger ones, a random hole (Farms2.MakePond/MakeHill's start). */
    private int[][] blob(int xlen, int zlen, int min, int lenLo, int lenHi) {
        int[][] a = new int[xlen][zlen];
        int c = 0, d = 0, clen = -1, dlen = -1;
        if (xlen >= min && zlen >= min) {
            c = next(1, xlen - 1 - 7);
            d = next(1, zlen - 1 - 7);
            clen = next(lenLo, lenHi);
            dlen = next(lenLo, lenHi);
        }
        for (int i = 1; i <= xlen - 2; i++) {
            for (int j = 1; j <= zlen - 2; j++) if (i < c || i > c + clen || j < d || j > d + dlen) a[i][j] = 1;
        }
        return a;
    }

    private int[][] neighbours(int[][] a, int min, int randMin, int randMax, int setTo, int times, boolean down) {
        for (int t = 1; t <= times; t++) {
            int[][] orig = copy(a);
            for (int i = 1; i <= a.length - 2; i++) {
                for (int j = 1; j <= a[0].length - 2; j++) {
                    int sum = 0;
                    for (int p = i - 1; p <= i + 1; p++) for (int q = j - 1; q <= j + 1; q++) if (p != i || q != j) sum += orig[p][q];
                    int threshold = min + next(randMin, randMax);
                    if (down ? sum < threshold : sum >= threshold) a[i][j] = setTo;
                }
            }
        }
        return a;
    }

    // ── Flowers, rails, fences ────────────────────────────────────────────

    private void makeFlowers() {
        for (int x = 0; x <= mapLength; x++) {
            for (int z = -farmLength; z <= mapLength; z++) {
                if (!free(x, z)) continue;
                int freeNeighbours = 0;
                for (int i = x - 1; i <= x + 1; i++) for (int j = z - 1; j <= z + 1; j++) if (free(i, j)) freeNeighbours++;
                if (freeNeighbours >= 3 && rng.nextDouble() * 100 <= flowerPercent) {
                    Material f = pick(theme.flowers);
                    if (f != Material.CACTUS || freeNeighbours == 9) set(x, 64, z, f);
                }
            }
        }
    }

    private boolean free(int x, int z) {
        return type(x, 63, z) == ground.getMaterial() && type(x, 64, z).isAir();
    }

    /** Chunks.PositionRails / BlockHelper.MakeRail: straights and corners from the neighbours. */
    private void positionRails() {
        Map<Long, Rail.Shape> shapes = new HashMap<>();
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = -farmLength; z <= mapLength; z++) {
                for (int x = 0; x < sizeX; x++) {
                    if (type(x, y, z) != Material.RAIL) continue;
                    boolean n = rail(x, y, z - 1), s = rail(x, y, z + 1), w = rail(x - 1, y, z), e = rail(x + 1, y, z);
                    Rail.Shape shape = n && w ? Rail.Shape.NORTH_WEST : n && e ? Rail.Shape.NORTH_EAST
                            : s && w ? Rail.Shape.SOUTH_WEST : s && e ? Rail.Shape.SOUTH_EAST
                            : n || s ? Rail.Shape.NORTH_SOUTH : Rail.Shape.EAST_WEST;
                    shapes.put(key(x, y, z), shape);
                }
            }
        }
        for (Map.Entry<Long, Rail.Shape> en : shapes.entrySet()) {
            Rail r = (Rail) Material.RAIL.createBlockData();
            r.setShape(en.getValue());
            set(keyX(en.getKey()), keyY(en.getKey()), keyZ(en.getKey()), r);
        }
    }

    private boolean rail(int x, int y, int z) {
        return type(x, y, z) == Material.RAIL;
    }

    /** Fences and panes that Mace placed (streets, moat, towers) join their neighbours, like the builds'. */
    private void connectFences() {
        boolean[] joins = new boolean[palette.size()];
        for (int i = 0; i < palette.size(); i++) joins[i] = palette.get(i) instanceof Fence;
        Map<Long, BlockData> updates = new HashMap<>();
        for (int y = 1; y < HEIGHT; y++) {
            for (int z = -farmLength; z <= mapLength; z++) {
                for (int x = 0; x < sizeX; x++) {
                    short i = cells[index(x, y, z)];
                    if (i >= joins.length || !joins[i]) continue;
                    MultipleFacing f = (MultipleFacing) palette.get(i).clone();
                    for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                        BlockData n = get(x + face.getModX(), y, z + face.getModZ());
                        f.setFace(face, n instanceof Fence || n.getMaterial().name().endsWith("FENCE_GATE") || n.getMaterial().isOccluding());
                    }
                    updates.put(key(x, y, z), f);
                }
            }
        }
        updates.forEach((k, b) -> set(keyX(k), keyY(k), keyZ(k), b));
    }

    // ── Reading the city out ──────────────────────────────────────────────

    /** The palette index array is private; chunks copy columns through this. */
    void column(int x, int z, BlockData[] out) {
        for (int y = 0; y < HEIGHT; y++) out[y] = palette.get(cells[index(x, y, z)]);
    }

    /** Where to stand on arrival: the middle of the main crossing. */
    int[] centre() {
        return new int[]{mapLength / 2, 64, mapLength / 2};
    }
}
