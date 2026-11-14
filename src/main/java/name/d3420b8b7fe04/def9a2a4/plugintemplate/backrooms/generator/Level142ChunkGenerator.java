package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.mesh.MeshStore;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Level 142 — "The Endless City". Mace's medieval cities (see {@link MaceBuilding}) with no edge:
 * a grid of 59-block city blocks between 5-block streets, forever.
 *
 * <p>Each city block is its own Mace city, hashed from the world seed and its grid position: a
 * theme (Medieval, Novv's Medieval or Desert: ground, walls, paths, the buildings to draw from),
 * a name ("City of Ashbluff", on its gatehouse signs), and lots packed guillotine-style with
 * buildings of the theme picked by Mace's frequencies, in all four rotations. Mace's other
 * building kinds come along: farms, city entrances (the gatehouses, as lots), sky features (a
 * balloon or an airship over some Medieval blocks), and mineshaft entrances, whose ladder leads
 * down to one level of Mace's mineshaft under the block: corridors grown from the entrance like
 * Mace's Mineshaft.MakeLevel, with its sections (pools, dungeons, a resting station) placed where
 * their patterns fit.
 *
 * <p>The buildings come from the Mace pack (a "mesh" in meshes.json, downloaded by MeshStore
 * before the world is created; GPLv3, so not in the jar). Blocks are written during generation;
 * sign text and chest loot need the chunk to exist, so {@link #applyBlockEntities} fills them
 * in on its first load (Level142Listener). A block's layout is cached, and recomputed the same
 * from the seed when it isn't.
 */
public class Level142ChunkGenerator extends BackroomsChunkGenerator {

    static final String PACK = "mace";

    private static final int SURFACE = 64;             // world y of Mace's ground layer (source y63)
    private static final int PERIOD = 64;
    private static final int STREET = 5;
    private static final int BLOCK = PERIOD - STREET;  // 59: fits Mace's largest building (40x40)
    private static final int GAP = 1;                  // ground between neighbouring lots

    private static final int MINE_Y = 30;              // mineshaft floor; Mace's deepest basements stop at y41
    private static final int CELL = 5;                 // Mace's mineshaft MULTIPLIER
    private static final int SPLIT_CHANCE = 7;
    private static final int SOLID = 0, ROUTE = 1, START = 2, SECTION = 100;

    private static final int SKY_MIN_Y = 115, SKY_RANGE = 30;
    private static final int SKY_CHANCE = 8;           // 1 in N Medieval blocks has a sky feature
    private static final int ENTRANCE_CHANCE = 4;      // 1 in N blocks may get a gatehouse lot
    private static final int MINE_CHANCE = 2;          // 1 in N blocks may get a mineshaft entrance

    private static final long THEME_SALT = 0x7E3A1L, PATH_SALT = 0x9A7L, WORDS_SALT = 0x5167L, LOT_SALT = 0x1075L, MINE_SALT = 0x3135L;

    /** One of Mace's themes (Resources/Themes/*.xml), by the file its buildings are listed in. */
    private enum Theme {
        MEDIEVAL("Medieval Buildings", "adjectives", "nouns", Material.GRASS_BLOCK, Material.DIRT,
                new Material[]{Material.STONE, Material.OAK_PLANKS, Material.BRICKS, Material.STONE_BRICKS},
                new Material[]{Material.STONE, Material.OAK_PLANKS, Material.BRICKS, Material.STONE_BRICKS, Material.GRAVEL},
                true, true),
        NOVV("Novv's Medieval Buildings", "adjectives", "novv", Material.GRASS_BLOCK, Material.DIRT,
                new Material[]{Material.STONE, Material.OAK_PLANKS, Material.STONE_BRICKS},
                new Material[]{Material.STONE, Material.OAK_PLANKS, Material.BRICKS, Material.STONE_BRICKS, Material.GRAVEL},
                true, false),
        DESERT("Desert Buildings", "desert_adjectives", "desert_nouns", Material.SAND, Material.SANDSTONE,
                new Material[]{Material.SANDSTONE},
                new Material[]{Material.SANDSTONE, Material.STONE, Material.STONE_BRICKS},
                false, false);

        final String file, prefixList, suffixList;
        final Material ground, subsoil;
        final Material[] walls, paths;
        final boolean farms, sky;

        Theme(String file, String prefixList, String suffixList, Material ground, Material subsoil,
              Material[] walls, Material[] paths, boolean farms, boolean sky) {
            this.file = file;
            this.prefixList = prefixList;
            this.suffixList = suffixList;
            this.ground = ground;
            this.subsoil = subsoil;
            this.walls = walls;
            this.paths = paths;
            this.farms = farms;
            this.sky = sky;
        }
    }

    /** The buildings and word lists in the Mace pack, sorted into each theme's pools. */
    private static final class Library {
        final MaceWords words;
        final Map<Theme, List<MaceBuilding>> lots = new EnumMap<>(Theme.class);
        final Map<Theme, List<MaceBuilding>> gates = new EnumMap<>(Theme.class);
        final Map<Theme, List<MaceBuilding>> mineEntrances = new EnumMap<>(Theme.class);
        final Map<Theme, List<MaceBuilding>> sky = new EnumMap<>(Theme.class);
        final List<MaceBuilding> sections = new ArrayList<>();

        Library(ZipFile zip) throws IOException {
            words = MaceWords.load(zip);
            List<MaceBuilding> all = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
                String n = en.nextElement().getName();
                if (n.startsWith("schem/") && n.endsWith(".schem")) names.add(n);
            }
            Collections.sort(names);   // pools in a fixed order, so a seed always gives the same city
            for (String n : names) {
                try (InputStream in = zip.getInputStream(zip.getEntry(n))) {
                    all.add(MaceBuilding.load(in));
                }
            }
            for (Theme t : Theme.values()) {
                lots.put(t, new ArrayList<>());
                gates.put(t, new ArrayList<>());
                mineEntrances.put(t, new ArrayList<>());
                sky.put(t, new ArrayList<>());
            }
            for (MaceBuilding b : all) {
                if (b.type.equals("mineshaft_section")) {
                    sections.add(b);
                    continue;
                }
                List<String> files = List.of(String.valueOf(b.meta("files")).split(","));
                boolean themeless = files.stream().noneMatch(f -> f.endsWith("Buildings"));  // the empty plots
                for (Theme t : Theme.values()) {
                    if (!themeless && !files.contains(t.file)) continue;
                    switch (b.type) {
                        case "city" -> lots.get(t).add(b);
                        case "farm" -> { if (t.farms) lots.get(t).add(b); }
                        case "city_entrance" -> gates.get(t).add(b);
                        case "mineshaft_entrance" -> mineEntrances.get(t).add(b);
                        case "sky_feature" -> { if (t.sky) sky.get(t).add(b); }
                        default -> { }
                    }
                }
            }
        }
    }

    private static volatile @Nullable Library library;

    /** The library, loaded from the downloaded pack on first use. */
    private static Library library() {
        Library lib = library;
        if (lib != null) return lib;
        synchronized (Level142ChunkGenerator.class) {
            if (library == null) {
                Path file = MeshStore.file(PACK);
                if (file == null) throw new IllegalStateException("the Mace pack isn't downloaded");
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    library = new Library(zip);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return library;
        }
    }

    /** A filled building at its world origin (the minimum corner). */
    private record Placed(MaceBuilding.Filled filled, int x, int y, int z) {
        boolean overlaps(int minX, int minZ, int maxX, int maxZ) {
            return x <= maxX && x + filled.sizeX() > minX && z <= maxZ && z + filled.sizeZ() > minZ;
        }
    }

    /**
     * One mineshaft level: a grid of 5-block cells (Mace's overview) whose cell (0, 0) starts at
     * world (x0, z0). The START cell is under the entrance, its ladder at the cell's center.
     */
    private record Mine(int x0, int z0, int[][] cells, int ladderX, int ladderZ) {
        boolean open(int i, int j) {
            return i >= 0 && j >= 0 && i < cells.length && j < cells[0].length && cells[i][j] != SOLID;
        }
    }

    private record CityBlock(List<Placed> placed, @Nullable Mine mine) {}

    private final Map<Long, CityBlock> cache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CityBlock> eldest) {
            return size() > 128;
        }
    });

    public Level142ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
        try {
            library();   // the world is created once the pack is in: load it here, on the main thread
        } catch (RuntimeException e) {
            // not downloaded yet: the first chunk loads it
        }
    }

    // ── Generation ────────────────────────────────────────────────────────

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        long seed = worldInfo.getSeed();
        int baseX = chunkX * 16, baseZ = chunkZ * 16;
        int minY = data.getMinHeight();

        // Ground, and streets in the path of the block they lead into.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = baseX + x, wz = baseZ + z;
                int bx = Math.floorDiv(wx, PERIOD), bz = Math.floorDiv(wz, PERIOD);
                Theme theme = theme(seed, bx, bz);
                boolean street = wx - bx * PERIOD < STREET || wz - bz * PERIOD < STREET;
                data.setBlock(x, minY, z, Material.BEDROCK);
                data.setRegion(x, minY + 1, z, x + 1, SURFACE - 3, z + 1, Material.STONE);
                data.setRegion(x, SURFACE - 3, z, x + 1, SURFACE, z + 1, theme.subsoil);
                data.setBlock(x, SURFACE, z, street ? path(seed, bx, bz) : theme.ground);
            }
        }
        // A lamp post in the middle of each crossing.
        int lx = Math.floorDiv(baseX, PERIOD) * PERIOD + STREET / 2, lz = Math.floorDiv(baseZ, PERIOD) * PERIOD + STREET / 2;
        if (lx >= baseX && lx < baseX + 16 && lz >= baseZ && lz < baseZ + 16) {
            data.setRegion(lx - baseX, SURFACE + 1, lz - baseZ, lx - baseX + 1, SURFACE + 3, lz - baseZ + 1, Material.OAK_FENCE);
            data.setBlock(lx - baseX, SURFACE + 3, lz - baseZ, Material.LANTERN);
        }

        for (int bx = Math.floorDiv(baseX, PERIOD); bx <= Math.floorDiv(baseX + 15, PERIOD); bx++) {
            for (int bz = Math.floorDiv(baseZ, PERIOD); bz <= Math.floorDiv(baseZ + 15, PERIOD); bz++) {
                CityBlock block = cityBlock(seed, bx, bz);
                if (block.mine() != null) carveMine(block.mine(), data, baseX, baseZ, seed);
                for (Placed p : block.placed()) {
                    if (p.overlaps(baseX, baseZ, baseX + 15, baseZ + 15)) {
                        p.filled().place(data, chunkX, chunkZ, p.x(), p.y(), p.z());
                    }
                }
                if (block.mine() != null) ladder(block.mine(), data, baseX, baseZ);
            }
        }
    }

    /** Sign text and chest loot for the buildings in a newly generated chunk (main thread). */
    public void applyBlockEntities(Chunk chunk) {
        long seed = chunk.getWorld().getSeed();
        int baseX = chunk.getX() * 16, baseZ = chunk.getZ() * 16;
        for (int bx = Math.floorDiv(baseX, PERIOD); bx <= Math.floorDiv(baseX + 15, PERIOD); bx++) {
            for (int bz = Math.floorDiv(baseZ, PERIOD); bz <= Math.floorDiv(baseZ + 15, PERIOD); bz++) {
                for (Placed p : cityBlock(seed, bx, bz).placed()) {
                    if (p.overlaps(baseX, baseZ, baseX + 15, baseZ + 15)) {
                        p.filled().applyBlockEntities(chunk, p.x(), p.y(), p.z());
                    }
                }
            }
        }
    }

    private static Theme theme(long seed, int bx, int bz) {
        int roll = (int) Math.floorMod(blockHash(seed ^ THEME_SALT, bx, bz), 100L);
        return roll < 15 ? Theme.DESERT : roll < 50 ? Theme.NOVV : Theme.MEDIEVAL;
    }

    /** The block's path material: its streets, and Mace's path markers in its buildings. */
    private static Material path(long seed, int bx, int bz) {
        Material[] paths = theme(seed, bx, bz).paths;
        return paths[(int) Math.floorMod(blockHash(seed ^ PATH_SALT, bx, bz), (long) paths.length)];
    }

    // ── City blocks ───────────────────────────────────────────────────────

    private CityBlock cityBlock(long seed, int bx, int bz) {
        long key = ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
        CityBlock block = cache.get(key);
        if (block == null) {
            block = makeCityBlock(seed, bx, bz);
            cache.put(key, block);
        }
        return block;
    }

    /** Lays out and fills one city block, all from hash(seed, bx, bz). */
    private static CityBlock makeCityBlock(long seed, int bx, int bz) {
        Library lib = library();
        long hash = blockHash(seed, bx, bz);
        Random rng = new Random(hash);
        Theme theme = theme(seed, bx, bz);
        BlockData path = path(seed, bx, bz).createBlockData();
        BlockData wall = theme.walls[rng.nextInt(theme.walls.length)].createBlockData();
        String cityName = lib.words.cityName(theme.prefixList, theme.suffixList, rng);
        MaceBuilding.Fill fill = new MaceBuilding.Fill(theme.ground.createBlockData(), path, wall, true, cityName,
                lib.words.forCity(new Random(hash ^ WORDS_SALT)), true, new HashSet<>());

        int ox = bx * PERIOD + STREET, oz = bz * PERIOD + STREET;   // the block's interior corner
        Packer packer = new Packer(lib, theme, rng, rng.nextInt(ENTRANCE_CHANCE) == 0, rng.nextInt(MINE_CHANCE) == 0);
        packer.pack(0, 0, BLOCK, BLOCK);

        List<Placed> placed = new ArrayList<>();
        Mine mine = null;
        for (int i = 0; i < packer.lots.size(); i++) {
            Packer.Lot lot = packer.lots.get(i);
            MaceBuilding.Filled f = lot.building().fill(fill, new Random(mix(hash ^ LOT_SALT + i)), lot.rotation());
            placed.add(new Placed(f, ox + lot.x(), SURFACE + lot.building().sourceY - 63, oz + lot.z()));
            if (lot.building().type.equals("mineshaft_entrance") && mine == null) {
                mine = mine(lib, new Random(hash ^ MINE_SALT), ox, oz,
                        lot.x() + f.sizeX() / 2, lot.z() + f.sizeZ() / 2, mix(hash ^ MINE_SALT), placed);
            }
        }

        List<MaceBuilding> sky = lib.sky.get(theme);
        if (!sky.isEmpty() && rng.nextInt(SKY_CHANCE) == 0) {
            MaceBuilding b = pick(sky, rng, "frequency");
            if (b != null) {
                StructureRotation rot = StructureRotation.values()[rng.nextInt(4)];
                MaceBuilding.Filled f = b.fill(fill, new Random(mix(hash ^ LOT_SALT - 1)), rot);
                placed.add(new Placed(f, ox + rng.nextInt(BLOCK - f.sizeX() + 1), SKY_MIN_Y + rng.nextInt(SKY_RANGE),
                        oz + rng.nextInt(BLOCK - f.sizeZ() + 1)));
            }
        }
        return new CityBlock(placed, mine);
    }

    /**
     * Guillotine packing of a block's interior: a building picked by Mace's frequency for the
     * free rectangle, put in one of its corners, the rest of the rectangle cut in two and packed
     * the same way. At most one gatehouse and one mineshaft entrance per block; unique buildings
     * once.
     */
    private static final class Packer {
        record Lot(MaceBuilding building, StructureRotation rotation, int x, int z) {}

        final List<Lot> lots = new ArrayList<>();
        private final Library lib;
        private final Theme theme;
        private final Random rng;
        private boolean gate, mine;
        private final Set<MaceBuilding> used = new HashSet<>();

        Packer(Library lib, Theme theme, Random rng, boolean gate, boolean mine) {
            this.lib = lib;
            this.theme = theme;
            this.rng = rng;
            this.gate = gate;
            this.mine = mine;
        }

        void pack(int x, int z, int w, int l) {
            if (w < 3 || l < 3) return;
            List<MaceBuilding> pool = new ArrayList<>();
            for (MaceBuilding b : lib.lots.get(theme)) if (fitsIn(b, w, l)) pool.add(b);
            // The extras get a fair chance at any rectangle they fit, not just the first.
            if (gate) for (MaceBuilding b : lib.gates.get(theme)) if (fitsIn(b, w, l) && rng.nextInt(3) == 0) pool.add(b);
            if (mine) for (MaceBuilding b : lib.mineEntrances.get(theme)) if (fitsIn(b, w, l) && rng.nextInt(3) == 0) pool.add(b);
            MaceBuilding b = pick(pool, rng, "frequency");
            if (b == null) return;
            if (unique(b)) used.add(b);
            if (b.type.equals("city_entrance")) gate = false;
            if (b.type.equals("mineshaft_entrance")) mine = false;

            boolean straight = b.width <= w && b.length <= l, turned = b.length <= w && b.width <= l;
            boolean quarter = turned && (!straight || rng.nextBoolean());
            StructureRotation rot = quarter
                    ? (rng.nextBoolean() ? StructureRotation.CLOCKWISE_90 : StructureRotation.COUNTERCLOCKWISE_90)
                    : (rng.nextBoolean() ? StructureRotation.NONE : StructureRotation.CLOCKWISE_180);
            int sx = quarter ? b.length : b.width, sz = quarter ? b.width : b.length;
            boolean left = rng.nextBoolean(), top = rng.nextBoolean();
            int bxPos = left ? x : x + w - sx, bzPos = top ? z : z + l - sz;
            lots.add(new Lot(b, rot, bxPos, bzPos));

            // The free strip beside the building (in x) and in front of it (in z), gap included.
            int restW = w - sx - GAP, restL = l - sz - GAP;
            int stripX = left ? x + sx + GAP : x, stripZ = top ? z + sz + GAP : z;
            if (rng.nextBoolean()) {     // cut along z: the x strip runs the full length
                pack(stripX, z, restW, l);
                pack(bxPos, stripZ, sx, restL);
            } else {                     // cut along x: the z strip runs the full width
                pack(x, stripZ, w, restL);
                pack(stripX, bzPos, restW, sz);
            }
        }

        private boolean fitsIn(MaceBuilding b, int w, int l) {
            return ((b.width <= w && b.length <= l) || (b.length <= w && b.width <= l)) && !used.contains(b);
        }

        private static boolean unique(MaceBuilding b) {
            String u = b.meta("unique");
            return u != null && (u.equalsIgnoreCase("yes") || u.equalsIgnoreCase("true") || u.equals("1"));
        }
    }

    /** Mace's frequency words as weights. */
    private static int weight(@Nullable String frequency) {
        if (frequency == null) return 3;
        return switch (frequency.trim().toLowerCase(Locale.ROOT)) {
            case "very common" -> 5;
            case "common" -> 4;
            case "rare" -> 2;
            case "very rare" -> 1;
            case "exclude" -> 0;
            default -> 3;
        };
    }

    @Nullable
    private static MaceBuilding pick(List<MaceBuilding> pool, Random rng, String frequencyKey) {
        int total = 0;
        for (MaceBuilding b : pool) total += weight(b.meta(frequencyKey));
        if (total <= 0) return null;
        int roll = rng.nextInt(total);
        for (MaceBuilding b : pool) {
            roll -= weight(b.meta(frequencyKey));
            if (roll < 0) return b;
        }
        return null;
    }

    // ── Mineshaft ─────────────────────────────────────────────────────────

    /**
     * One level of Mace's mineshaft (Mineshaft.MakeLevel at depth 1) under a block, laid out on 5-block
     * cells around the entrance at block-local (cx, cz); its sections are filled and added to
     * {@code placed}. Null if the entrance is too near the block's edge for a cell of its own.
     */
    private static @Nullable Mine mine(Library lib, Random rng, int ox, int oz, int cx, int cz, long hash, List<Placed> placed) {
        int ci = (cx - CELL / 2) / CELL, cj = (cz - CELL / 2) / CELL;
        int gx = cx - CELL / 2 - ci * CELL, gz = cz - CELL / 2 - cj * CELL;   // grid offset in the block, 0..4
        int[][] cells = new int[(BLOCK - gx) / CELL][(BLOCK - gz) / CELL];
        if (ci >= cells.length || cj >= cells[0].length) return null;
        cells[ci][cj] = ROUTE;
        routeXPlus(cells, rng, ci + 1, cj, 0);
        routeZPlus(cells, rng, ci, cj + 1, 1);
        routeXMinus(cells, rng, ci - 1, cj, 2);
        routeZMinus(cells, rng, ci, cj - 1, 3);
        cells[ci][cj] = START;   // never under a section

        // AddMineshaftSections: a fifteenth of the route cells, until ten picks find no place.
        int routes = 0;
        for (int[] col : cells) for (int c : col) if (c == ROUTE) routes++;
        Set<MaceBuilding> used = new HashSet<>();
        int fails = 0, sections = 0;
        MaceBuilding.Fill fill = new MaceBuilding.Fill(Material.REDSTONE_ORE.createBlockData(),
                Material.GOLD_ORE.createBlockData(), Material.STONE.createBlockData(), true, null, t -> null, true, null);
        while (fails < 10 && sections < routes / 15 + 1) {
            MaceBuilding s = pick(lib.sections, rng, "frequency1");
            if (s == null) break;
            String pattern = s.meta("pattern");
            if (pattern == null || used.contains(s)) {
                fails++;
                continue;
            }
            int[] at = matchPattern(cells, pattern.split(" "), rng);
            if (at == null) {
                fails++;
                continue;
            }
            int si = at[0] + parse(s.meta("pattern_position_x")), sj = at[1] + parse(s.meta("pattern_position_z"));
            cells[si][sj] = SECTION + sections;
            MaceBuilding.Filled f = s.fill(fill, new Random(mix(hash + sections)), StructureRotation.NONE);
            // A 7-wide section on a 5-wide cell: centered, its doors meet the corridors.
            placed.add(new Placed(f, ox + gx + si * CELL - (f.sizeX() - CELL) / 2, MINE_Y,
                    oz + gz + sj * CELL - (f.sizeZ() - CELL) / 2));
            sections++;
        }
        return new Mine(ox + gx, oz + gz, cells, ox + cx, oz + cz);
    }

    private static int parse(@Nullable String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Mace's MatchPatternInArray: a random place where the 3x3 pattern (rows in z, x reversed) fits. */
    private static int @Nullable [] matchPattern(int[][] cells, String[] pattern, Random rng) {
        int n = pattern[0].length();
        List<int[]> found = new ArrayList<>();
        for (int x = 0; x + n <= cells.length; x++) {
            for (int z = 0; z + n <= cells[0].length; z++) {
                boolean ok = true;
                for (int dx = 0; dx < n && ok; dx++) {
                    for (int dz = 0; dz < n && ok; dz++) {
                        int v = cells[x + dx][z + dz];
                        char p = pattern[dz].charAt(n - 1 - dx);
                        ok = v <= ROUTE && (p == '?' || p - '0' == v);
                    }
                }
                if (ok) found.add(new int[]{x, z});
            }
        }
        return found.isEmpty() ? null : found.get(rng.nextInt(found.size()));
    }

    /** Mace's Utils.IsZeros, with cells off the grid counting as taken. */
    private static boolean zeros(int[][] a, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (x < 0 || z < 0 || x >= a.length || z >= a[0].length || a[x][z] > 0) return false;
            }
        }
        return true;
    }

    private static int between(Random rng, int lo, int hi) {   // C#'s Random.Next(lo, hi)
        return hi > lo ? lo + rng.nextInt(hi - lo) : lo;
    }

    // Mace's CreateRoute{X,Z}{Plus,Minus}: run a corridor a random way, branching now and then.
    private static void routeXPlus(int[][] a, Random rng, int x0, int z, int invalid) {
        if (a.length - x0 <= 2) return;
        int len = between(rng, (a.length - x0) / 2, a.length - x0);
        for (int x = x0 + 1; x < x0 + len; x++) {
            if (!zeros(a, x, z - 1, x + 1, z + 1)) break;
            a[x0][z] = ROUTE;
            a[x][z] = ROUTE;
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 3) routeZPlus(a, rng, x, z, invalid);
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 1) routeZMinus(a, rng, x, z, invalid);
        }
    }

    private static void routeXMinus(int[][] a, Random rng, int x0, int z, int invalid) {
        if (x0 <= 2) return;
        int len = between(rng, x0 / 2, x0 + 1);
        for (int x = x0 - 1; x > x0 - len; x--) {
            if (!zeros(a, x - 1, z - 1, x, z + 1)) break;
            a[x0][z] = ROUTE;
            a[x][z] = ROUTE;
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 3) routeZPlus(a, rng, x, z, invalid);
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 1) routeZMinus(a, rng, x, z, invalid);
        }
    }

    private static void routeZPlus(int[][] a, Random rng, int x, int z0, int invalid) {
        if (a[0].length - z0 <= 2) return;
        int len = between(rng, (a[0].length - z0) / 2, a[0].length - z0);
        for (int z = z0 + 1; z < z0 + len; z++) {
            if (!zeros(a, x - 1, z, x + 1, z + 1)) break;
            a[x][z0] = ROUTE;
            a[x][z] = ROUTE;
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 2) routeXPlus(a, rng, x, z, invalid);
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 0) routeXMinus(a, rng, x, z, invalid);
        }
    }

    private static void routeZMinus(int[][] a, Random rng, int x, int z0, int invalid) {
        if (z0 <= 2) return;
        int len = between(rng, z0 / 2, z0 + 1);
        for (int z = z0 - 1; z > z0 - len; z--) {
            if (!zeros(a, x - 1, z - 1, x + 1, z)) break;
            a[x][z0] = ROUTE;
            a[x][z] = ROUTE;
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 2) routeXPlus(a, rng, x, z, invalid);
            if (rng.nextInt(SPLIT_CHANCE) == 0 && invalid != 0) routeXMinus(a, rng, x, z, invalid);
        }
    }

    /**
     * Corridors: 3 wide and 3 high down the middle of each route cell, reaching out to open
     * neighbours, with a fence-and-plank support across straight runs and a torch now and then.
     * The start cell is a 5x5 room. Sections are placed over this afterwards.
     */
    private static void carveMine(Mine m, ChunkData data, int baseX, int baseZ, long seed) {
        for (int i = 0; i < m.cells().length; i++) {
            for (int j = 0; j < m.cells()[0].length; j++) {
                int v = m.cells()[i][j];
                if (v != ROUTE && v != START) continue;
                int cx = m.x0() + i * CELL, cz = m.z0() + j * CELL;
                if (cx > baseX + 15 || cx + CELL <= baseX || cz > baseZ + 15 || cz + CELL <= baseZ) continue;
                if (v == START) {
                    air(data, baseX, baseZ, cx, cz, cx + 4, cz + 4);
                    continue;
                }
                boolean east = m.open(i + 1, j), west = m.open(i - 1, j), south = m.open(i, j + 1), north = m.open(i, j - 1);
                air(data, baseX, baseZ, cx + (west ? 0 : 1), cz + 1, cx + (east ? 4 : 3), cz + 3);
                air(data, baseX, baseZ, cx + 1, cz + (north ? 0 : 1), cx + 3, cz + (south ? 4 : 3));
                long h = blockHash(seed ^ MINE_SALT, cx, cz);
                if ((east || west) && !north && !south) {
                    support(data, baseX, baseZ, cx + 2, cz + 1, cx + 2, cz + 3, h);
                } else if ((north || south) && !east && !west) {
                    support(data, baseX, baseZ, cx + 1, cz + 2, cx + 3, cz + 2, h);
                }
            }
        }
    }

    private static void air(ChunkData data, int baseX, int baseZ, int x1, int z1, int x2, int z2) {
        for (int x = Math.max(x1, baseX); x <= Math.min(x2, baseX + 15); x++) {
            for (int z = Math.max(z1, baseZ); z <= Math.min(z2, baseZ + 15); z++) {
                data.setRegion(x - baseX, MINE_Y + 1, z - baseZ, x - baseX + 1, MINE_Y + 4, z - baseZ + 1, Material.AIR);
            }
        }
    }

    /** Fence posts at both ends of a crossbeam from (x1, z1) to (x2, z2), and maybe a torch. */
    private static void support(ChunkData data, int baseX, int baseZ, int x1, int z1, int x2, int z2, long hash) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (x < baseX || x > baseX + 15 || z < baseZ || z > baseZ + 15) continue;
                boolean end = (x == x1 && z == z1) || (x == x2 && z == z2);
                if (end) data.setRegion(x - baseX, MINE_Y + 1, z - baseZ, x - baseX + 1, MINE_Y + 3, z - baseZ + 1, Material.OAK_FENCE);
                data.setBlock(x - baseX, MINE_Y + 3, z - baseZ, Material.OAK_PLANKS);
            }
        }
        int tx = x1 == x2 ? x1 - 1 : x1, tz = x1 == x2 ? z1 : z1 - 1;   // beside the first post
        if ((hash & 3) == 0 && tx >= baseX && tx <= baseX + 15 && tz >= baseZ && tz <= baseZ + 15) {
            data.setBlock(tx - baseX, MINE_Y + 1, tz - baseZ, Material.TORCH);
        }
    }

    /** The ladder from the start room up through the entrance's hole, on a plank post in the room. */
    private static void ladder(Mine m, ChunkData data, int baseX, int baseZ) {
        int x = m.ladderX() - baseX, z = m.ladderZ() - baseZ;
        if (x >= 0 && x < 16 && z + 1 >= 0 && z + 1 < 16) {
            data.setRegion(x, MINE_Y + 1, z + 1, x + 1, MINE_Y + 4, z + 2, Material.OAK_PLANKS);
        }
        if (x < 0 || x >= 16 || z < 0 || z >= 16) return;
        Ladder ladder = (Ladder) Material.LADDER.createBlockData();
        ladder.setFacing(BlockFace.NORTH);   // hangs on the block to its south
        data.setRegion(x, MINE_Y + 1, z, x + 1, SURFACE + 1, z + 1, ladder);
    }

    // ── Hashing ───────────────────────────────────────────────────────────

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long blockHash(long seed, int x, int z) {
        return mix(seed ^ mix(x * 0x9E3779B97F4A7C15L ^ mix(z * 0xC2B2AE3D27D4EB4FL)));
    }

    // ── Spawn ─────────────────────────────────────────────────────────────

    @Override
    public int getSpawnY() {
        return SURFACE + 1;
    }

    /** On the street crossing at the origin, beside its lamp post. */
    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, SURFACE + 1, 0.5);
    }
}
