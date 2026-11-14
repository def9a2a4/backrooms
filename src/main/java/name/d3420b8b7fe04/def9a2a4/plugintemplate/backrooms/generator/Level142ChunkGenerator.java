package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Level 142 — "The Endless City". Mace's walled medieval cities (see {@link MaceCity}), each in
 * a region of its own with countryside around it: built the way Mace builds a city, with its
 * moat, walls, gatehouses, corner towers, streets and districts, a mineshaft seven levels deep
 * under it, and farms outside its north wall.
 *
 * <p>The world is cut into 768-block squares; about a third stay whole and hold one large city,
 * the rest split into 384-block regions with a middling city, or again into 192-block regions
 * with a small town. Each city is sized to cover about 40% of its region (farms included), so
 * cities take up about 40% of the land whatever their size. A region's city is hashed from the
 * world seed and the region: its theme (Medieval, Novv's Medieval, Desert), size and place.
 * Building a city takes a second or more, so the last few are kept; a chunk copies its columns out
 * of the city's buffer, and sign text and chest contents are written when the chunk first loads
 * ({@link #applyBlockEntities}, from Level142Listener). Around the cities is flat ground in the
 * theme's soil, with Mace's trees and flowers.
 *
 * <p>The buildings, words, emblems and flags are in the Mace pack (GPLv3), downloaded by
 * MeshStore before the world is created.
 */
public class Level142ChunkGenerator extends BackroomsChunkGenerator {

    static final String PACK = MaceLibrary.PACK;

    private static final int LARGE = 768, MEDIUM = 384, SMALL = 192;   // region sides
    private static final int LARGE_PERCENT = 33, MEDIUM_PERCENT = 55;
    private static final double COVER = 0.45;        // target share of a region for its city and farms (~40% after rounding)
    private static final int MAX_CHUNKS = 29;        // a 29-chunk city's buffer is ~67 MB
    private static final int MARGIN = 12;            // countryside at least this wide around a city
    private static final int GROUND = 63;            // Mace's ground layer
    private static final long CACHE_CELLS = 80_000_000L;   // ~160 MB of city buffers
    private static final int TREE_CELL = 11;
    private static final long TREE_SALT = 0x7EE5L, FLOWER_SALT = 0xF10L, SITE_SALT = 0x5173L,
            LARGE_SALT = 0x1A26EL, MEDIUM_SALT = 0x3ED1L, CITY_SALT = 0xC17EL;

    /** A square of the world holding one city. */
    private record Region(int x, int z, int size) {}

    /** Where a region's city stands: (x0, z0) is Mace's (0, 0); the farms run north of it. */
    private record Site(Region region, MaceCity.Plan plan, int x0, int z0) {
        /** Buffer cells, for the cache's budget. */
        long cells() {
            return (long) (plan.mapLength() + 1) * (plan.mapLength() + plan.farmLength() + 1) * MaceCity.HEIGHT;
        }

        int minX() { return x0; }
        int maxX() { return x0 + plan.mapLength(); }
        int minZ() { return z0 - plan.farmLength(); }
        int maxZ() { return z0 + plan.mapLength(); }

        boolean contains(int x, int z) {
            return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
        }

        boolean overlaps(int x1, int z1, int x2, int z2) {
            return x1 <= maxX() && x2 >= minX() && z1 <= maxZ() && z2 >= minZ();
        }
    }

    /** Built cities, least recently used first, kept within CACHE_CELLS. */
    private final Map<Site, CompletableFuture<MaceCity>> cities = new LinkedHashMap<>(16, 0.75f, true);
    private final String pack;
    private final List<MaceCity.Theme> themes;
    /** Trees fill their ground layer with the theme's ground. */
    private final Map<MaceCity.Theme, MaceBuilding.Fill> treeFills = new EnumMap<>(MaceCity.Theme.class);
    private volatile @Nullable Map<MaceCity.Theme, List<MaceBuilding>> trees;

    public Level142ChunkGenerator(NamespacedKey biomeKey) {
        this(biomeKey, PACK, MaceCity.Theme.MACE);
    }

    /** Cities of {@code themes}, built from the buildings in {@code pack}. */
    protected Level142ChunkGenerator(NamespacedKey biomeKey, String pack, List<MaceCity.Theme> themes) {
        super(biomeKey);
        this.pack = pack;
        this.themes = themes;
        for (MaceCity.Theme t : themes) {
            BlockData ground = t.ground.createBlockData();
            treeFills.put(t, new MaceBuilding.Fill(ground, ground, Material.STONE.createBlockData(), true, null,
                    w -> null, false, null));
        }
        try {
            MaceLibrary.get(pack);   // the world is created once the pack is in: load it here, on the main thread
        } catch (RuntimeException e) {
            // not downloaded yet: the first chunk loads it
        }
    }

    // ── Regions ───────────────────────────────────────────────────────────

    private static long hash(long seed, long salt, int x, int z) {
        return mix(seed ^ salt ^ mix(x * 0x9E3779B97F4A7C15L ^ mix(z * 0xC2B2AE3D27D4EB4FL)));
    }

    /** The region holding (x, z): a whole 768 square, one of its 384 quarters, or a 192 sixteenth. */
    private static Region regionAt(long seed, int x, int z) {
        int lx = Math.floorDiv(x, LARGE), lz = Math.floorDiv(z, LARGE);
        if (Math.floorMod(hash(seed, LARGE_SALT, lx, lz), 100L) < LARGE_PERCENT) return new Region(lx * LARGE, lz * LARGE, LARGE);
        int mx = Math.floorDiv(x, MEDIUM), mz = Math.floorDiv(z, MEDIUM);
        if (Math.floorMod(hash(seed, MEDIUM_SALT, mx, mz), 100L) < MEDIUM_PERCENT) return new Region(mx * MEDIUM, mz * MEDIUM, MEDIUM);
        return new Region(Math.floorDiv(x, SMALL) * SMALL, Math.floorDiv(z, SMALL) * SMALL, SMALL);
    }

    /**
     * The region's city: the biggest Mace city (odd chunk counts, 5 and up) whose footprint,
     * farms included, is within about 45% of the region (give or take 15%, for variety; rounding
     * down to whole chunks brings it to about 40%), placed
     * anywhere in the region that leaves a margin of countryside.
     */
    private Site site(long seed, Region r) {
        long h = hash(seed, CITY_SALT, r.x(), r.z());
        MaceCity.Plan theme = MaceCity.Plan.of(h, 5, themes);
        Random rng = new Random(h ^ SITE_SALT);
        double target = COVER * r.size() * r.size() * (0.85 + 0.3 * rng.nextDouble());
        int chunks = 5;
        for (int c = MAX_CHUNKS; c > 5; c -= 2) {
            int w = c * 16 + 2 * MaceCity.EDGE + 1, l = w + theme.farmLength();
            if ((double) w * l <= target && l <= r.size() - 2 * MARGIN) {
                chunks = c;
                break;
            }
        }
        MaceCity.Plan plan = MaceCity.Plan.of(h, chunks, themes);
        int w = plan.mapLength() + 1, l = plan.mapLength() + plan.farmLength() + 1;
        int x = r.x() + MARGIN + rng.nextInt(Math.max(1, r.size() - 2 * MARGIN - w));
        int z = r.z() + MARGIN + rng.nextInt(Math.max(1, r.size() - 2 * MARGIN - l)) + plan.farmLength();
        return new Site(r, plan, x, z);
    }

    private Site siteAt(long seed, int x, int z) {
        return site(seed, regionAt(seed, x, z));
    }

    /** The region's city, built once and kept while it's among the last few used. */
    private MaceCity city(Site site) {
        Site key = site;
        CompletableFuture<MaceCity> f;
        boolean build = false;
        synchronized (cities) {
            f = cities.get(key);
            if (f == null) {
                f = new CompletableFuture<>();
                cities.put(key, f);
                build = true;
                long total = 0;
                for (Site s : cities.keySet()) total += s.cells();
                for (var it = cities.keySet().iterator(); total > CACHE_CELLS && it.hasNext(); ) {
                    Site old = it.next();
                    if (old.equals(key)) continue;
                    total -= old.cells();
                    it.remove();
                }
            }
        }
        if (build) {
            try {
                f.complete(MaceCity.build(site.plan(), MaceLibrary.get(pack)));
            } catch (RuntimeException | Error e) {
                f.completeExceptionally(e);
                synchronized (cities) {
                    cities.remove(key);
                }
                throw e;
            }
        }
        return f.join();
    }

    // ── Generation ────────────────────────────────────────────────────────

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        long seed = worldInfo.getSeed();
        int baseX = chunkX * 16, baseZ = chunkZ * 16, minY = data.getMinHeight();
        data.setRegion(0, minY, 0, 16, minY + 1, 16, Material.BEDROCK);
        data.setRegion(0, minY + 1, 0, 16, 0, 16, Material.STONE);

        // Up to four regions meet in a chunk; each column belongs to one.
        BlockData[] column = new BlockData[MaceCity.HEIGHT];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = baseX + x, wz = baseZ + z;
                Site site = siteAt(seed, wx, wz);
                if (site.contains(wx, wz)) {
                    city(site).column(wx - site.x0(), wz - site.z0(), column);
                    for (int y = 0; y < MaceCity.HEIGHT; y++) {
                        if (!column[y].getMaterial().isAir()) data.setBlock(x, y, z, column[y]);
                    }
                } else {
                    countryside(data, x, z, site.plan().theme());
                }
            }
        }
        decorate(data, seed, chunkX, chunkZ);
    }

    private static void countryside(ChunkData data, int x, int z, MaceCity.Theme theme) {
        data.setRegion(x, 0, z, x + 1, 2, z + 1, Material.BEDROCK);
        data.setRegion(x, 2, z, x + 1, GROUND - 3, z + 1, theme.rock);
        data.setRegion(x, GROUND - 3, z, x + 1, GROUND, z + 1, theme.soil);
        data.setBlock(x, GROUND, z, theme.ground);
    }

    /** The pack's trees, and the theme's flowers, on the countryside. */
    private void decorate(ChunkData data, long seed, int chunkX, int chunkZ) {
        int baseX = chunkX * 16, baseZ = chunkZ * 16;
        Map<MaceCity.Theme, List<MaceBuilding>> trees = trees(MaceLibrary.get(pack));
        for (int cx = Math.floorDiv(baseX - 8, TREE_CELL); cx <= Math.floorDiv(baseX + 15, TREE_CELL); cx++) {
            for (int cz = Math.floorDiv(baseZ - 8, TREE_CELL); cz <= Math.floorDiv(baseZ + 15, TREE_CELL); cz++) {
                long h = mix(seed ^ TREE_SALT ^ mix(cx * 0x9E3779B97F4A7C15L ^ mix(cz)));
                if ((h & 7) >= 2) continue;   // a quarter of the cells
                int tx = cx * TREE_CELL + (int) ((h >>> 8) & 3), tz = cz * TREE_CELL + (int) ((h >>> 12) & 3);
                Site site = siteAt(seed, tx, tz);
                MaceCity.Theme theme = site.plan().theme();
                List<MaceBuilding> kinds = trees.getOrDefault(theme, List.of());
                if (!theme.trees || kinds.isEmpty()) continue;
                MaceBuilding t = kinds.get((int) Long.remainderUnsigned(h >>> 16, kinds.size()));
                if (site.overlaps(tx - 3, tz - 3, tx + t.width + 3, tz + t.length + 3)
                        || !regionAt(seed, tx + t.width, tz + t.length).equals(site.region())) {
                    continue;
                }
                MaceBuilding.Filled f = t.fill(treeFills.get(theme), new Random(h),
                        StructureRotation.values()[(int) ((h >>> 24) & 3)]);
                f.place(data, chunkX, chunkZ, tx, GROUND + t.sourceY - 63, tz);
            }
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = baseX + x, wz = baseZ + z;
                long h = mix(seed ^ FLOWER_SALT ^ mix(wx * 0x9E3779B97F4A7C15L ^ mix(wz)));
                if ((h & 31) != 0 || !data.getType(x, GROUND + 1, z).isAir()) continue;
                Site site = siteAt(seed, wx, wz);
                if (site.contains(wx, wz) || data.getType(x, GROUND, z) != site.plan().theme().ground) continue;
                List<Material> flowers = site.plan().theme().flowers;
                Material f = flowers.get((int) Long.remainderUnsigned(h >>> 8, flowers.size()));
                if (f != Material.CACTUS) data.setBlock(x, GROUND + 1, z, f);
            }
        }
    }

    /**
     * Each theme's trees: the pack's buildings of type "tree", or named as trees (Mace's are city
     * buildings), listed in the theme's file; any of them if none are.
     */
    private Map<MaceCity.Theme, List<MaceBuilding>> trees(MaceLibrary lib) {
        Map<MaceCity.Theme, List<MaceBuilding>> t = trees;
        if (t == null) {
            List<MaceBuilding> all = new ArrayList<>();
            for (MaceBuilding b : lib.buildings) {
                if (b.type.equals("tree") || b.name.toLowerCase(Locale.ROOT).contains("tree")) all.add(b);
            }
            t = new EnumMap<>(MaceCity.Theme.class);
            for (MaceCity.Theme theme : themes) {
                List<MaceBuilding> mine = new ArrayList<>();
                for (MaceBuilding b : all) {
                    String files = b.meta("files");
                    if (files != null && List.of(files.split(",")).contains(theme.file)) mine.add(b);
                }
                t.put(theme, mine.isEmpty() ? all : mine);
            }
            trees = t;
        }
        return t;
    }

    // ── Block entities ────────────────────────────────────────────────────

    /** Sign text and chest contents for the city blocks in a newly generated chunk (main thread). */
    public void applyBlockEntities(Chunk chunk) {
        long seed = chunk.getWorld().getSeed();
        int baseX = chunk.getX() * 16, baseZ = chunk.getZ() * 16;
        List<Site> seen = new ArrayList<>();
        for (int[] corner : new int[][]{{baseX, baseZ}, {baseX + 15, baseZ}, {baseX, baseZ + 15}, {baseX + 15, baseZ + 15}}) {
            Site site = siteAt(seed, corner[0], corner[1]);
            if (seen.contains(site) || !site.overlaps(baseX, baseZ, baseX + 15, baseZ + 15)) continue;
            seen.add(site);
            MaceCity city = city(site);
            int lx = baseX - site.x0(), lz = baseZ - site.z0();
            for (Map.Entry<Long, String[]> e : city.signs.entrySet()) {
                Block b = blockIn(chunk, e.getKey(), lx, lz);
                if (b == null || !(b.getState() instanceof Sign sign)) continue;
                for (int i = 0; i < 4; i++) sign.getSide(Side.FRONT).line(i, Component.text(e.getValue()[i]));
                sign.setWaxed(true);
                sign.update(true, false);
            }
            for (Map.Entry<Long, ItemStack[]> e : city.containers.entrySet()) {
                Block b = blockIn(chunk, e.getKey(), lx, lz);
                if (b == null) continue;
                BlockState state = b.getState();
                if (!(state instanceof Container container)) continue;
                Inventory inv = container.getSnapshotInventory();
                inv.clear();
                ItemStack[] items = e.getValue();
                for (int i = 0; i < items.length && i < inv.getSize(); i++) if (items[i] != null) inv.setItem(i, items[i].clone());
                state.update(true, false);
            }
        }
    }

    /** The world block at a city position, if it's in this chunk ((lx, lz): the chunk's corner in city coordinates). */
    private static @Nullable Block blockIn(Chunk chunk, long key, int lx, int lz) {
        int x = MaceCity.keyX(key) - lx, z = MaceCity.keyZ(key) - lz;
        if (x < 0 || x > 15 || z < 0 || z > 15) return null;
        return chunk.getBlock(x, MaceCity.keyY(key), z);
    }

    // ── Hashing and spawn ─────────────────────────────────────────────────

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public int getSpawnY() {
        return GROUND + 1;
    }

    /** In the middle of the main crossing of the city in the origin's region. */
    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        Site site = siteAt(world.getSeed(), 0, 0);
        int half = site.plan().mapLength() / 2;
        return new Location(world, site.x0() + half + 0.5, GROUND + 1, site.z0() + half + 0.5);
    }
}
