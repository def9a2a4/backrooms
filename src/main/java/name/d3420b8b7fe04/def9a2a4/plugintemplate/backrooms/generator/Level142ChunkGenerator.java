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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Level 142 — "The Endless City". Mace's walled medieval cities (see {@link MaceCity}), one in
 * every 384-block region, with countryside between: each built the way Mace builds a city, with
 * its moat, walls, gatehouses, corner towers, streets and districts, a mineshaft seven levels
 * deep under it, and farms outside its north wall.
 *
 * <p>A region's city is hashed from the world seed and the region's position: its theme
 * (Medieval, Novv's Medieval, Desert), its size (7 to 13 chunks) and its place in the region.
 * Building a city takes about a second, so the last few are kept; a chunk copies its columns out
 * of the city's buffer, and sign text and chest contents are written when the chunk first loads
 * ({@link #applyBlockEntities}, from Level142Listener). Around the cities is flat ground in the
 * theme's soil, with Mace's trees and flowers.
 *
 * <p>The buildings, words, emblems and flags are in the Mace pack (GPLv3), downloaded by
 * MeshStore before the world is created.
 */
public class Level142ChunkGenerator extends BackroomsChunkGenerator {

    static final String PACK = MaceLibrary.PACK;

    private static final int REGION = 384;
    private static final int MARGIN = 24;            // countryside at least this wide around a city
    private static final int GROUND = 63;            // Mace's ground layer
    private static final int CACHED_CITIES = 4;      // up to ~17 MB each
    private static final int TREE_CELL = 11;
    private static final long TREE_SALT = 0x7EE5L, FLOWER_SALT = 0xF10L, SITE_SALT = 0x5173L;

    /** Where a region's city stands: (x0, z0) is Mace's (0, 0); the farms run north of it. */
    private record Site(MaceCity.Plan plan, int x0, int z0) {
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

    private final Map<Long, CompletableFuture<MaceCity>> cities = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CompletableFuture<MaceCity>> eldest) {
            return size() > CACHED_CITIES;
        }
    };

    public Level142ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
        try {
            MaceLibrary.get();   // the world is created once the pack is in: load it here, on the main thread
        } catch (RuntimeException e) {
            // not downloaded yet: the first chunk loads it
        }
    }

    // ── Regions ───────────────────────────────────────────────────────────

    private static Site site(long seed, int rx, int rz) {
        MaceCity.Plan plan = MaceCity.Plan.of(mix(seed ^ mix(rx * 0x9E3779B97F4A7C15L ^ mix(rz * 0xC2B2AE3D27D4EB4FL))));
        Random rng = new Random(plan.seed() ^ SITE_SALT);
        int w = plan.mapLength() + 1, l = plan.mapLength() + plan.farmLength() + 1;
        int x = rx * REGION + MARGIN + rng.nextInt(Math.max(1, REGION - 2 * MARGIN - w));
        int z = rz * REGION + MARGIN + rng.nextInt(Math.max(1, REGION - 2 * MARGIN - l)) + plan.farmLength();
        return new Site(plan, x, z);
    }

    private static Site siteAt(long seed, int x, int z) {
        return site(seed, Math.floorDiv(x, REGION), Math.floorDiv(z, REGION));
    }

    /** The region's city, built once and kept while it's among the last few used. */
    private MaceCity city(Site site) {
        long key = ((long) site.x0() << 32) ^ (site.z0() & 0xFFFFFFFFL);
        CompletableFuture<MaceCity> f;
        boolean build = false;
        synchronized (cities) {
            f = cities.get(key);
            if (f == null) {
                f = new CompletableFuture<>();
                cities.put(key, f);
                build = true;
            }
        }
        if (build) {
            try {
                f.complete(MaceCity.build(site.plan(), MaceLibrary.get()));
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
        boolean desert = theme == MaceCity.Theme.DESERT;
        data.setRegion(x, 0, z, x + 1, 2, z + 1, Material.BEDROCK);
        data.setRegion(x, 2, z, x + 1, GROUND - 3, z + 1, desert ? Material.SANDSTONE : Material.STONE);
        data.setRegion(x, GROUND - 3, z, x + 1, GROUND, z + 1, desert ? Material.SAND : Material.DIRT);
        data.setBlock(x, GROUND, z, theme.ground);
    }

    /** Mace's trees, and the theme's flowers, on the countryside. */
    private static void decorate(ChunkData data, long seed, int chunkX, int chunkZ) {
        int baseX = chunkX * 16, baseZ = chunkZ * 16;
        List<MaceBuilding> trees = trees(MaceLibrary.get());
        for (int cx = Math.floorDiv(baseX - 8, TREE_CELL); cx <= Math.floorDiv(baseX + 15, TREE_CELL); cx++) {
            for (int cz = Math.floorDiv(baseZ - 8, TREE_CELL); cz <= Math.floorDiv(baseZ + 15, TREE_CELL); cz++) {
                long h = mix(seed ^ TREE_SALT ^ mix(cx * 0x9E3779B97F4A7C15L ^ mix(cz)));
                if ((h & 7) >= 2 || trees.isEmpty()) continue;   // a quarter of the cells
                int tx = cx * TREE_CELL + (int) ((h >>> 8) & 3), tz = cz * TREE_CELL + (int) ((h >>> 12) & 3);
                Site site = siteAt(seed, tx, tz);
                if (site.plan().theme() == MaceCity.Theme.DESERT || site.overlaps(tx - 3, tz - 3, tx + 8, tz + 8)
                        || !sameRegion(tx, tz, tx + 5, tz + 5)) {
                    continue;
                }
                MaceBuilding t = trees.get((int) Long.remainderUnsigned(h >>> 16, trees.size()));
                MaceBuilding.Filled f = t.fill(TREE_FILL, new Random(h), StructureRotation.values()[(int) ((h >>> 24) & 3)]);
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

    private static boolean sameRegion(int x1, int z1, int x2, int z2) {
        return Math.floorDiv(x1, REGION) == Math.floorDiv(x2, REGION) && Math.floorDiv(z1, REGION) == Math.floorDiv(z2, REGION);
    }

    private static volatile @Nullable List<MaceBuilding> trees;

    private static List<MaceBuilding> trees(MaceLibrary lib) {
        List<MaceBuilding> t = trees;
        if (t == null) {
            t = new ArrayList<>();
            for (MaceBuilding b : lib.buildings) if (b.name.toLowerCase(Locale.ROOT).contains("tree")) t.add(b);
            trees = t;
        }
        return t;
    }

    private static final MaceBuilding.Fill TREE_FILL = new MaceBuilding.Fill(Material.GRASS_BLOCK.createBlockData(),
            Material.GRASS_BLOCK.createBlockData(), Material.STONE.createBlockData(), true, null, s -> null, false, null);

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
        Site site = site(world.getSeed(), 0, 0);
        int half = site.plan().mapLength() / 2;
        return new Location(world, site.x0() + half + 0.5, GROUND + 1, site.z0() + half + 0.5);
    }
}
