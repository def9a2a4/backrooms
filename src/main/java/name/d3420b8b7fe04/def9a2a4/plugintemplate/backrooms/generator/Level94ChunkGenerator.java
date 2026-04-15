package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Fake Skyblock — classic 3×6 dirt island with oak tree and chest,
 * surrounded by an invisible barrier square ~30 blocks out.
 */
public class Level94ChunkGenerator extends BackroomsChunkGenerator {

    public Level94ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    // Island platform (world coords, chunk 0,0)
    private static final int ISLAND_X_MIN = 5;
    private static final int ISLAND_X_MAX = 7;   // 3 wide (5,6,7)
    private static final int ISLAND_Z_MIN = 5;
    private static final int ISLAND_Z_MAX = 10;  // 6 long (5–10)
    private static final int ISLAND_Y_TOP = 64;
    private static final int ISLAND_Y_BOT = 61;  // 4 tall (61,62,63,64)

    // Tree (world coords)
    private static final int TREE_X = 6;
    private static final int TREE_Z = 7;
    private static final int TREE_BASE_Y = 65;

    // Chest (world coords)
    private static final int CHEST_X = 6;
    private static final int CHEST_Z = 9;
    private static final int CHEST_Y = 65;

    // Barrier ring (world coords) — ~30 blocks from island center
    public static final int BARRIER_MIN = -24;
    public static final int BARRIER_MAX = 31;
    private static final int BARRIER_Y_MIN = 0;
    private static final int BARRIER_Y_MAX = 100;

    // Spawn
    public static final int SPAWN_X = 6;
    public static final int SPAWN_Y = 65;
    public static final int SPAWN_Z = 8;

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Everything defaults to air. Place island, tree, chest, and barriers.

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // --- Barrier ring ---
                boolean onBarrierX = (worldX == BARRIER_MIN || worldX == BARRIER_MAX);
                boolean onBarrierZ = (worldZ == BARRIER_MIN || worldZ == BARRIER_MAX);
                boolean inBarrierRange = worldX >= BARRIER_MIN && worldX <= BARRIER_MAX
                        && worldZ >= BARRIER_MIN && worldZ <= BARRIER_MAX;

                if (inBarrierRange && (onBarrierX || onBarrierZ)) {
                    for (int y = BARRIER_Y_MIN; y <= BARRIER_Y_MAX; y++) {
                        chunkData.setBlock(x, y, z, Material.BARRIER);
                    }
                }

                // --- Island platform ---
                if (worldX >= ISLAND_X_MIN && worldX <= ISLAND_X_MAX
                        && worldZ >= ISLAND_Z_MIN && worldZ <= ISLAND_Z_MAX) {
                    chunkData.setBlock(x, ISLAND_Y_TOP, z, Material.GRASS_BLOCK);
                    for (int y = ISLAND_Y_BOT; y < ISLAND_Y_TOP; y++) {
                        chunkData.setBlock(x, y, z, Material.DIRT);
                    }
                }

                // --- Oak tree ---
                if (worldX == TREE_X && worldZ == TREE_Z) {
                    // Trunk: 4 blocks tall
                    for (int y = TREE_BASE_Y; y < TREE_BASE_Y + 4; y++) {
                        chunkData.setBlock(x, y, z, Material.OAK_LOG);
                    }
                }

                // Leaf canopy (3x3 at y=67-68, 1x1 cap at y=69)
                int dx = worldX - TREE_X;
                int dz = worldZ - TREE_Z;
                if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) {
                    chunkData.setBlock(x, TREE_BASE_Y + 2, z, Material.OAK_LEAVES);
                    chunkData.setBlock(x, TREE_BASE_Y + 3, z, Material.OAK_LEAVES);
                }
                if (worldX == TREE_X && worldZ == TREE_Z) {
                    chunkData.setBlock(x, TREE_BASE_Y + 4, z, Material.OAK_LEAVES);
                    // Re-place trunk over leaves
                    chunkData.setBlock(x, TREE_BASE_Y + 2, z, Material.OAK_LOG);
                    chunkData.setBlock(x, TREE_BASE_Y + 3, z, Material.OAK_LOG);
                }

                // --- Chest ---
                if (worldX == CHEST_X && worldZ == CHEST_Z) {
                    chunkData.setBlock(x, CHEST_Y, z, Material.CHEST);
                }
            }
        }
    }

    @Override
    public int getSpawnY() { return SPAWN_Y; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5);
    }
}
