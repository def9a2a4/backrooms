package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.DebugLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Level 56 — "The Henge". A floating island in the fog carrying Stonehenge at 16x scale,
 * voxelized chunk by chunk from a ~50 KB mesh (see {@link VoxelMesh}).
 *
 * <p>The monument center sits at (origin_x, origin_z), by default 285 blocks south of the world
 * origin, so the fixed spawn at (0, 0) lands on the island's north rim facing the stones. At the
 * center stands the altar: a stepped polished diorite pyramid (5×5, 3×3, 1×1) with a lodestone on
 * top, where the {@code item_offering} exit takes a porkchop.
 *
 * <p>Mesh: "Stonehenge England - VR" by Graphic solutions, CC BY 4.0 — see
 * structures/stonehenge.CREDITS.txt.
 */
public class Level56ChunkGenerator extends BackroomsChunkGenerator {

    private static final String MESH_RESOURCE = "/structures/stonehenge.mesh.gz";
    private static final Logger LOG = Logger.getLogger("Backrooms");

    private double scale = 16.0;
    private int groundY = 64;
    private int originX = 0;
    private int originZ = 285;

    private volatile Loaded loaded;

    /** Pyramid layers' half-widths, bottom up; the lodestone sits on the last. */
    private static final int[] ALTAR_STEPS = {2, 1, 0};

    private record Loaded(VoxelMesh mesh, BlockData[] blocks, int altarBaseY) {}

    public Level56ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    @Override
    public void configure(@Nullable ConfigurationSection config) {
        if (config != null) {
            scale = config.getDouble("mesh_scale", scale);
            groundY = config.getInt("ground_y", groundY);
            originX = config.getInt("origin_x", originX);
            originZ = config.getInt("origin_z", originZ);
        }
        loaded = load();   // on the main thread, before any chunk generates
    }

    private Loaded load() {
        long t0 = System.nanoTime();
        try (InputStream in = Level56ChunkGenerator.class.getResourceAsStream(MESH_RESOURCE)) {
            if (in == null) throw new IOException("missing resource " + MESH_RESOURCE);
            VoxelMesh mesh = VoxelMesh.load(in, scale, groundY, originX, originZ);
            VoxelMesh.PaletteEntry[] palette = mesh.palette();
            BlockData[] blocks = new BlockData[palette.length];
            for (int i = 0; i < palette.length; i++) {
                Material mat = Material.matchMaterial(palette[i].id());
                if (mat == null || !mat.isBlock()) {
                    LOG.warning("Level 56: unknown block in mesh palette: " + palette[i].id());
                    mat = palette[i].family() == 0 ? Material.DIRT : Material.STONE;
                }
                blocks[i] = Bukkit.createBlockData(mat);
            }
            DebugLog.log(LOG, String.format("Level 56 mesh loaded in %.1f ms", (System.nanoTime() - t0) / 1e6));
            // Altar base: one above the highest ground under its footprint, so every chunk agrees.
            int top = Integer.MIN_VALUE;
            for (int dx = -ALTAR_STEPS[0]; dx <= ALTAR_STEPS[0]; dx++) {
                for (int dz = -ALTAR_STEPS[0]; dz <= ALTAR_STEPS[0]; dz++) {
                    top = Math.max(top, mesh.surfaceY(originX + dx, originZ + dz));
                }
            }
            int altarBaseY = (top == Integer.MIN_VALUE ? groundY : top) + 1;
            return new Loaded(mesh, blocks, altarBaseY);
        } catch (IOException e) {
            throw new UncheckedIOException("Level 56: failed to load " + MESH_RESOURCE, e);
        }
    }

    private Loaded loaded() {
        Loaded l = loaded;
        if (l == null) {
            synchronized (this) {
                l = loaded;
                if (l == null) loaded = l = load();
            }
        }
        return l;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        Loaded l = loaded();
        BlockData[] blocks = l.blocks();
        l.mesh().generateChunk(chunkX, chunkZ, chunkData.getMinHeight(), chunkData.getMaxHeight(),
                (x, y, z, p) -> chunkData.setBlock(x, y, z, blocks[p]));
        placeAltar(chunkX, chunkZ, chunkData, l.altarBaseY());
    }

    private void placeAltar(int chunkX, int chunkZ, ChunkData chunkData, int baseY) {
        int r = ALTAR_STEPS[0];
        for (int x = originX - r; x <= originX + r; x++) {
            if (x >> 4 != chunkX) continue;
            for (int z = originZ - r; z <= originZ + r; z++) {
                if (z >> 4 != chunkZ) continue;
                int lx = x & 15, lz = z & 15;
                // foundation down to the ground, in case the footprint isn't level
                for (int y = baseY - 1; y >= chunkData.getMinHeight() && chunkData.getType(lx, y, lz).isAir(); y--) {
                    chunkData.setBlock(lx, y, lz, Material.POLISHED_DIORITE);
                }
                int dist = Math.max(Math.abs(x - originX), Math.abs(z - originZ));
                for (int step = 0; step < ALTAR_STEPS.length; step++) {
                    if (dist <= ALTAR_STEPS[step]) chunkData.setBlock(lx, baseY + step, lz, Material.POLISHED_DIORITE);
                }
                if (dist == 0) chunkData.setBlock(lx, baseY + ALTAR_STEPS.length, lz, Material.LODESTONE);
            }
        }
    }

    /** World X/Z of the monument center: the altar, and the giant pigs' pasture. */
    public int getCenterX() {
        return originX;
    }

    public int getCenterZ() {
        return originZ;
    }

    /** Scan start for the surface spawn search: below the rim's ground so it finds the top. */
    @Override
    public int getSpawnY() {
        return groundY - 8;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, groundY + 1, 0.5);
    }
}
