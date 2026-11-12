package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.scan.ScanMeshes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * The scan levels (1881 Hintze Hall, 1967/1973 the Southbank Undercroft, 9500 Göbekli Tepe): a
 * photogrammetry scan voxelized chunk by chunk ({@link VoxelMesh}), plus a few "props" placed on
 * its floor from {@code generator_config.props} (display cases, an offering pedestal, farm
 * plots, single blocks).
 *
 * <p>The mesh is downloaded and decoded by {@link ScanMeshes} before the level's world is created,
 * so {@link #configure} only picks it up by name.
 *
 * <pre>
 * generator: scan
 * generator_config:
 *   mesh: hintze_hall          # name in scans/meshes.json
 *   mode: shell                # shell (interiors) or column (terrain)
 *   scale: 16
 *   ground_y: -62              # world Y of the mesh's lowest point (shell) / ground reference (column)
 *   thickness: 1.5             # shell: wall half-thickness, blocks
 *   island_depth: 1.5          # column: underside depth at the center, blocks per unit scale
 *   spawn: {x: -40, z: -120}   # spawn column; Y is found by scanning up from below the floor,
 *                              # or from y if given (to pick an upper floor)
 *   props:
 *     - {type: case, x: -88, z: 40, block: lodestone}
 *     - {type: block, x: 184, y: 67, z: -40, block: "candle[lit=true]"}   # y: on the floor at/above y
 * </pre>
 */
public class ScanChunkGenerator extends BackroomsChunkGenerator {

    private static final Logger LOG = Logger.getLogger("Backrooms");

    /** A prop anchored on the floor at (x, z); its 3×3 footprint must stay inside the anchor's chunk. */
    private record Prop(String type, int x, int z, @Nullable Integer y, @Nullable BlockData block,
                        @Nullable String book) {}

    /** A lectern prop in a live world: where it is and which placard book it holds. */
    public record Placard(int x, int y, int z, String book) {}

    private String meshName;
    private VoxelMesh.Options options = VoxelMesh.Options.column(16, 64, 0, 0);
    private int spawnX, spawnZ;
    private Integer spawnY;            // optional: search for standing room from here up
    private @Nullable String placardFile;
    private final List<Prop> props = new ArrayList<>();
    private volatile VoxelMesh mesh;
    private BlockData[] blocks;

    public ScanChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    /** The mesh options a level's generator_config describes (also used to preload the mesh). */
    public static VoxelMesh.Options options(ConfigurationSection c) {
        return new VoxelMesh.Options(c.getDouble("scale", 16), c.getInt("ground_y", 64),
                c.getInt("origin_x", 0), c.getInt("origin_z", 0), "shell".equals(c.getString("mode", "column")),
                c.getDouble("thickness", 1.5), c.getDouble("island_depth", 6.25), c.getBoolean("surface_grid", true));
    }

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config == null) return;
        meshName = config.getString("mesh");
        options = options(config);
        ConfigurationSection spawn = config.getConfigurationSection("spawn");
        if (spawn != null) {
            spawnX = spawn.getInt("x");
            spawnZ = spawn.getInt("z");
            spawnY = spawn.contains("y") ? spawn.getInt("y") : null;
        }
        placardFile = config.getString("placards");
        props.clear();
        for (Map<?, ?> m : config.getMapList("props")) {
            String type = String.valueOf(m.get("type"));
            int x = ((Number) m.get("x")).intValue(), z = ((Number) m.get("z")).intValue();
            BlockData block = null;
            if (m.get("block") != null) {
                try {
                    block = Bukkit.createBlockData(String.valueOf(m.get("block")));   // states allowed: candle[lit=true]
                } catch (IllegalArgumentException e) {
                    LOG.warning("Scan prop " + type + ": bad block " + m.get("block"));
                }
            }
            int lx = x & 15, lz = z & 15;
            if (lx < 1 || lx > 14 || lz < 1 || lz > 14) {
                LOG.warning("Scan prop " + type + " at " + x + "," + z + " straddles a chunk border; skipped.");
                continue;
            }
            Integer py = m.get("y") instanceof Number n ? n.intValue() : null;
            props.add(new Prop(type, x, z, py, block, m.get("book") == null ? null : String.valueOf(m.get("book"))));
        }
        mesh = ScanMeshes.get(meshName);
        if (mesh == null) throw new IllegalStateException("scan mesh '" + meshName + "' isn't loaded");
        VoxelMesh.PaletteEntry[] palette = mesh.palette();
        blocks = new BlockData[palette.length];
        for (int i = 0; i < palette.length; i++) {
            Material mat = Material.matchMaterial(palette[i].id());
            if (mat == null || !mat.isBlock()) {
                LOG.warning("Scan mesh " + meshName + ": unknown block " + palette[i].id());
                mat = Material.STONE;
            }
            blocks[i] = Bukkit.createBlockData(mat);
        }
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        BlockData[] b = blocks;
        mesh.generateChunk(chunkX, chunkZ, chunkData.getMinHeight(), chunkData.getMaxHeight(),
                (x, y, z, p) -> chunkData.setBlock(x, y, z, b[p]));
        for (Prop prop : props) {
            if (prop.x >> 4 != chunkX || prop.z >> 4 != chunkZ) continue;
            int bx = chunkX << 4, bz = chunkZ << 4;
            place(prop, chunkData.getMinHeight(), chunkData.getMaxHeight(), new Blocks() {
                public Material get(int x, int y, int z) { return chunkData.getType(x - bx, y, z - bz); }
                public void set(int x, int y, int z, BlockData d) { chunkData.setBlock(x - bx, y, z - bz, d); }
            });
        }
    }

    // ── props ────────────────────────────────────────────────────────────────────────────────────
    // Exits hang off some props (break an exhibit, place a candle), so props must come back for the
    // next visitor: ScanPropListener rebuilds a breakable prop a few seconds after it's damaged. To
    // rebuild in the same place, a prop's floor height comes from the scan alone (generated into a
    // scratch column), never from what's in the world now.

    /** World-coordinate block access, over a ChunkData or a live world. */
    private interface Blocks {
        Material get(int x, int y, int z);
        void set(int x, int y, int z, BlockData d);
    }

    private final Map<Prop, Integer> floors = new java.util.concurrent.ConcurrentHashMap<>();

    /** First standable Y (solid below, 3 air above) of the scan alone at the prop's anchor. */
    private int floorY(Prop prop, int minY, int maxY) {
        return floors.computeIfAbsent(prop, p -> {
            int h = maxY - minY, lx = p.x & 15, lz = p.z & 15;
            boolean[] solid = new boolean[h];
            mesh.generateChunk(p.x >> 4, p.z >> 4, minY, maxY, (x, y, z, i) -> {
                if (x == lx && z == lz) solid[y - minY] = true;
            });
            // from just below the prop's y if given (an upper floor), else from below the lowest floor
            for (int y = Math.max(minY + 1, p.y != null ? p.y - 2 : options.groundY() - 8); y < maxY - 3; y++) {
                int k = y - minY;
                if (solid[k - 1] && !solid[k] && !solid[k + 1] && !solid[k + 2]) return y;
            }
            return Integer.MIN_VALUE;
        });
    }

    /** Props that rebuild themselves when broken (farm plots and grass are meant to change). */
    private static boolean restorable(Prop p) {
        return switch (p.type) {
            case "case", "pedestal", "block", "lectern" -> true;
            default -> false;
        };
    }

    /** The restorable prop whose blocks include (x, y, z), or null. */
    @Nullable
    public Object propAt(World world, int x, int y, int z) {
        for (Prop p : props) {
            if (!restorable(p) || Math.abs(x - p.x) > 1 || Math.abs(z - p.z) > 1) continue;
            int fy = floorY(p, world.getMinHeight(), world.getMaxHeight());
            if (fy != Integer.MIN_VALUE && y >= fy - 1 && y <= fy + 2) return p;
        }
        return null;
    }

    /** Rebuild a prop returned by {@link #propAt} in a live world (main thread). */
    public void restore(World world, Object prop) {
        if (!(prop instanceof Prop p)) return;
        place(p, world.getMinHeight(), world.getMaxHeight(), new Blocks() {
            public Material get(int x, int y, int z) { return world.getBlockAt(x, y, z).getType(); }
            public void set(int x, int y, int z, BlockData d) { world.getBlockAt(x, y, z).setBlockData(d, false); }
        });
    }

    private void place(Prop prop, int minY, int maxY, Blocks w) {
        int ax = prop.x, az = prop.z;
        int y = floorY(prop, minY, maxY);
        if (y == Integer.MIN_VALUE) return;
        BlockData andesite = Material.POLISHED_ANDESITE.createBlockData(), glass = Material.GLASS.createBlockData();
        switch (prop.type) {
            case "case" -> {
                // display case: 3×3 polished andesite plinth, glass walls and lid, the exhibit inside
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int k = y - 1; k >= minY && !w.get(ax + dx, k, az + dz).isSolid(); k--) {
                            w.set(ax + dx, k, az + dz, andesite);
                        }
                        w.set(ax + dx, y, az + dz, andesite);
                        boolean center = dx == 0 && dz == 0;
                        w.set(ax + dx, y + 1, az + dz, center && prop.block != null ? prop.block : glass);
                        w.set(ax + dx, y + 2, az + dz, glass);
                    }
                }
            }
            case "pedestal" -> {
                // offering stone: a 3×3 step with the offering block in the middle of its top
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        w.set(ax + dx, y, az + dz, Material.POLISHED_TUFF.createBlockData());
                    }
                }
                w.set(ax, y + 1, az, prop.block != null ? prop.block : Material.CHISELED_TUFF.createBlockData());
            }
            case "plot" -> {
                // farm plot: water in the middle, tilled soil around
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean center = dx == 0 && dz == 0;
                        w.set(ax + dx, y - 1, az + dz, (center ? Material.WATER : Material.FARMLAND).createBlockData());
                    }
                }
            }
            case "grass" -> {
                // wild grass on soil: breaking it drops wheat seeds (vanilla, 1 in 8)
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        w.set(ax + dx, y - 1, az + dz, Material.GRASS_BLOCK.createBlockData());
                        w.set(ax + dx, y, az + dz, Material.SHORT_GRASS.createBlockData());
                    }
                }
            }
            case "block" -> {
                if (prop.block != null) w.set(ax, y, az, prop.block);
            }
            case "lectern" -> {
                // a placard: the book goes in when the chunk loads (ScanPropListener)
                w.set(ax, y, az, prop.block != null ? prop.block : Material.LECTERN.createBlockData());
            }
            default -> LOG.warning("Unknown scan prop type: " + prop.type);
        }
    }

    /** File (in levels/) holding the placard books, or null. */
    @Nullable
    public String placardFile() {
        return placardFile;
    }

    /** The lectern props in a chunk, with their floor height. */
    public List<Placard> placards(World world, int chunkX, int chunkZ) {
        List<Placard> out = new ArrayList<>();
        for (Prop p : props) {
            if (!"lectern".equals(p.type) || p.book == null || p.x >> 4 != chunkX || p.z >> 4 != chunkZ) continue;
            int y = floorY(p, world.getMinHeight(), world.getMaxHeight());
            if (y != Integer.MIN_VALUE) out.add(new Placard(p.x, y, p.z, p.book));
        }
        return out;
    }

    @Override
    public int getSpawnY() {
        // arrivals scan up from here for the first spot with room to stand
        return spawnY != null ? spawnY - 1 : options.groundY() - 8;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, spawnX + 0.5, spawnY != null ? spawnY : options.groundY() + 1, spawnZ + 0.5);
    }
}
