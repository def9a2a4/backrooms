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
 *   spawn: {x: -40, z: -120}   # spawn column; Y is found by scanning up from below the floor
 *   props:
 *     - {type: case, x: -88, z: 40, block: lodestone}
 * </pre>
 */
public class ScanChunkGenerator extends BackroomsChunkGenerator {

    private static final Logger LOG = Logger.getLogger("Backrooms");

    /** A prop anchored on the floor at (x, z); its 3×3 footprint must stay inside the anchor's chunk. */
    private record Prop(String type, int x, int z, @Nullable BlockData block) {}

    private String meshName;
    private VoxelMesh.Options options = VoxelMesh.Options.column(16, 64, 0, 0);
    private int spawnX, spawnZ;
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
        }
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
            props.add(new Prop(type, x, z, block));
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
            if (prop.x >> 4 == chunkX && prop.z >> 4 == chunkZ) place(prop, chunkData);
        }
    }

    /** First standable Y (solid below, 3 air above) scanning up from below the floor; MIN if none. */
    private int floor(ChunkData d, int lx, int lz) {
        int start = Math.max(d.getMinHeight() + 1, options.groundY() - 8);
        for (int y = start; y < d.getMaxHeight() - 3; y++) {
            if (d.getType(lx, y - 1, lz).isSolid() && d.getType(lx, y, lz).isAir()
                    && d.getType(lx, y + 1, lz).isAir() && d.getType(lx, y + 2, lz).isAir()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private void place(Prop prop, ChunkData d) {
        int lx = prop.x & 15, lz = prop.z & 15;
        int y = floor(d, lx, lz);
        if (y == Integer.MIN_VALUE) return;
        switch (prop.type) {
            case "case" -> {
                // display case: 3×3 polished andesite plinth, glass walls and lid, the exhibit inside
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int k = y - 1; k >= d.getMinHeight() && !d.getType(lx + dx, k, lz + dz).isSolid(); k--) {
                            d.setBlock(lx + dx, k, lz + dz, Material.POLISHED_ANDESITE);
                        }
                        d.setBlock(lx + dx, y, lz + dz, Material.POLISHED_ANDESITE);
                        boolean center = dx == 0 && dz == 0;
                        if (center && prop.block != null) d.setBlock(lx, y + 1, lz, prop.block);
                        else d.setBlock(lx + dx, y + 1, lz + dz, Material.GLASS);
                        d.setBlock(lx + dx, y + 2, lz + dz, Material.GLASS);
                    }
                }
            }
            case "pedestal" -> {
                // offering stone: a 3×3 step with the offering block in the middle of its top
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        d.setBlock(lx + dx, y, lz + dz, Material.POLISHED_TUFF);
                    }
                }
                if (prop.block != null) d.setBlock(lx, y + 1, lz, prop.block);
                else d.setBlock(lx, y + 1, lz, Material.CHISELED_TUFF);
            }
            case "plot" -> {
                // farm plot: water in the middle, tilled soil around
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean center = dx == 0 && dz == 0;
                        d.setBlock(lx + dx, y - 1, lz + dz, center ? Material.WATER : Material.FARMLAND);
                    }
                }
            }
            case "grass" -> {
                // wild grass on soil: breaking it drops wheat seeds (vanilla, 1 in 8)
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        d.setBlock(lx + dx, y - 1, lz + dz, Material.GRASS_BLOCK);
                        d.setBlock(lx + dx, y, lz + dz, Material.SHORT_GRASS);
                    }
                }
            }
            case "block" -> {
                if (prop.block != null) d.setBlock(lx, y, lz, prop.block);
            }
            default -> LOG.warning("Unknown scan prop type: " + prop.type);
        }
    }

    @Override
    public int getSpawnY() {
        return options.groundY() - 8;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, spawnX + 0.5, options.groundY() + 1, spawnZ + 0.5);
    }
}
