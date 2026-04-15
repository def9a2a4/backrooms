package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level94ChunkGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.BackroomsLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Level 94 "Fake Skyblock" — handles void-fall loop, barrier-break animation,
 * chest population, and transition to Level 3.
 */
public class Level94Listener implements Listener {

    private static final String WORLD_NAME = "backrooms_level_94";
    private static final int VOID_THRESHOLD = 10;
    private static final int MAX_RADIUS = 60;

    private static Level94Listener instance;

    private final JavaPlugin plugin;
    private final LevelRegistry levelRegistry;
    private final PlayerStateManager playerStateManager;

    private final Set<UUID> fallCooldown = new HashSet<>();
    private final Set<UUID> animating = new HashSet<>();
    private boolean chestPopulated = false;

    public Level94Listener(JavaPlugin plugin, LevelRegistry levelRegistry,
                           PlayerStateManager playerStateManager) {
        this.plugin = plugin;
        this.levelRegistry = levelRegistry;
        this.playerStateManager = playerStateManager;
        instance = this;
    }

    public static Level94Listener getInstance() {
        return instance;
    }

    /** Trigger the cascade animation from a command — uses the player's position as the origin. */
    public void triggerCascade(Player player) {
        if (!player.getWorld().getName().equals(WORLD_NAME)) return;
        if (animating.contains(player.getUniqueId())) return;
        startAnimation(player, player.getLocation());
    }

    // ── Void Fall Loop ──────────────────────────────────────────────────

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        if (!to.getWorld().getName().equals(WORLD_NAME)) return;
        if (to.getY() >= VOID_THRESHOLD) return;
        if (fallCooldown.contains(event.getPlayer().getUniqueId())) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        fallCooldown.add(uuid);

        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));

        Location spawn = new Location(to.getWorld(),
                Level94ChunkGenerator.SPAWN_X + 0.5,
                256,
                Level94ChunkGenerator.SPAWN_Z + 0.5,
                player.getLocation().getYaw(), player.getLocation().getPitch());
        player.teleport(spawn);

        Bukkit.getScheduler().runTaskLater(plugin, () -> fallCooldown.remove(uuid), 20L);
    }

    // ── Barrier Break Handler ───────────────────────────────────────────

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(WORLD_NAME)) return;

        Block block;
        if (action == Action.LEFT_CLICK_BLOCK) {
            block = event.getClickedBlock();
            if (block == null || block.getType() != Material.BARRIER) return;
        } else {
            // Barriers are invisible in survival — client fires LEFT_CLICK_AIR instead of
            // LEFT_CLICK_BLOCK because it doesn't send START_DESTROY_BLOCK for invisible blocks.
            // getTargetBlockExact skips barriers (noOcclude), so iterate getLineOfSight instead.
            block = null;
            for (Block b : player.getLineOfSight(null, 5)) {
                if (b.getType() == Material.BARRIER) {
                    block = b;
                    break;
                }
            }
            if (block == null) return;
        }

        if (animating.contains(player.getUniqueId())) return;

        startAnimation(player, block.getLocation());
    }

    // ── Floor Fill Animation ────────────────────────────────────────────

    private void startAnimation(Player player, Location breakPoint) {
        UUID uuid = player.getUniqueId();
        animating.add(uuid);

        World world = breakPoint.getWorld();
        Random rng = new Random();

        // Origin points: the break point + 2-3 random points
        List<int[]> origins = new ArrayList<>();
        origins.add(new int[]{breakPoint.getBlockX(), breakPoint.getBlockZ()});
        int extraPoints = 2 + rng.nextInt(2);
        for (int i = 0; i < extraPoints; i++) {
            int rx = Level94ChunkGenerator.BARRIER_MIN + rng.nextInt(
                    Level94ChunkGenerator.BARRIER_MAX - Level94ChunkGenerator.BARRIER_MIN);
            int rz = Level94ChunkGenerator.BARRIER_MIN + rng.nextInt(
                    Level94ChunkGenerator.BARRIER_MAX - Level94ChunkGenerator.BARRIER_MIN);
            origins.add(new int[]{rx, rz});
        }

        int fillY = player.getLocation().getBlockY() - 1; // floor level
        int[] radius = {0};

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                taskHolder[0].cancel();
                animating.remove(uuid);
                return;
            }

            for (int[] origin : origins) {
                for (int dx = -radius[0]; dx <= radius[0]; dx++) {
                    for (int dz = -radius[0]; dz <= radius[0]; dz++) {
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if ((int) dist != radius[0]) continue;

                        int bx = origin[0] + dx;
                        int bz = origin[1] + dz;
                        if (bx < Level94ChunkGenerator.BARRIER_MIN || bx > Level94ChunkGenerator.BARRIER_MAX) continue;
                        if (bz < Level94ChunkGenerator.BARRIER_MIN || bz > Level94ChunkGenerator.BARRIER_MAX) continue;

                        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;

                        // Wavefront: blue glass
                        Block b = world.getBlockAt(bx, fillY, bz);
                        if (b.getType() == Material.BARRIER) continue; // don't replace barrier walls
                        b.setType(Material.LIGHT_BLUE_STAINED_GLASS, false);

                        // Also fill 2 blocks up for walls
                        for (int wy = 1; wy <= 3; wy++) {
                            Block wall = world.getBlockAt(bx, fillY + wy, bz);
                            if (wall.getType() != Material.AIR && wall.getType() != Material.BARRIER) continue;
                            if (wall.getType() == Material.BARRIER) continue;
                            wall.setType(Material.LIGHT_BLUE_STAINED_GLASS, false);
                        }
                    }
                }

                // Fill behind wavefront with concrete/command blocks
                if (radius[0] >= 3) {
                    int fillRadius = radius[0] - 3;
                    for (int dx = -fillRadius; dx <= fillRadius; dx++) {
                        for (int dz = -fillRadius; dz <= fillRadius; dz++) {
                            double dist = Math.sqrt(dx * dx + dz * dz);
                            if ((int) dist != fillRadius) continue;

                            int bx = origin[0] + dx;
                            int bz = origin[1] + dz;
                            if (bx < Level94ChunkGenerator.BARRIER_MIN || bx > Level94ChunkGenerator.BARRIER_MAX) continue;
                            if (bz < Level94ChunkGenerator.BARRIER_MIN || bz > Level94ChunkGenerator.BARRIER_MAX) continue;
                            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;

                            Block b = world.getBlockAt(bx, fillY, bz);
                            if (b.getType() == Material.BARRIER) continue;

                            Material fill = rng.nextDouble() < 0.3 ? Material.COMMAND_BLOCK : Material.WHITE_CONCRETE;
                            b.setType(fill, false);

                            // Walls behind wavefront
                            for (int wy = 1; wy <= 3; wy++) {
                                Block wall = world.getBlockAt(bx, fillY + wy, bz);
                                if (wall.getType() == Material.BARRIER) continue;
                                if (wall.getType() == Material.LIGHT_BLUE_STAINED_GLASS
                                        || wall.getType() == Material.AIR) {
                                    wall.setType(fill, false);
                                }
                            }
                        }
                    }
                }
            }

            // Sound
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_PLACE, 0.5f, 1.2f);

            radius[0]++;
            if (radius[0] > MAX_RADIUS) {
                taskHolder[0].cancel();
                transitionToServerRoom(player);
            }
        }, 0L, 3L);
    }

    // ── Transition to Level 3 ───────────────────────────────────────────

    private void transitionToServerRoom(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false));
        player.sendMessage("§c[ERR] §7Reality breach detected. Rerouting...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            UUID uuid = player.getUniqueId();
            if (!player.isOnline()) {
                animating.remove(uuid);
                return;
            }

            World targetWorld = levelRegistry.getWorld("level_3");
            if (targetWorld == null) {
                plugin.getLogger().warning("Level 3 world not loaded for Level 94 transition");
                animating.remove(uuid);
                return;
            }

            BackroomsLevel currentLevel = levelRegistry.get("level_94");
            BackroomsLevel targetLevel = levelRegistry.get("level_3");
            BackroomsPlayerState state = playerStateManager.getOrCreate(player);

            if (currentLevel != null) {
                currentLevel.onPlayerLeave(player, state);
            }

            player.teleport(targetWorld.getSpawnLocation());
            state.setCurrentLevelId("level_3");

            if (targetLevel != null) {
                targetLevel.onPlayerEnter(player, state);
            }

            animating.remove(uuid);
        }, 40L);
    }

    // ── Chest Population + Bonus-Tree Cleanup ─────────────────────────

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.getWorld().getName().equals(WORLD_NAME)) return;

        Chunk chunk = event.getChunk();
        if (chunk.getX() != 0 || chunk.getZ() != 0) return;
        if (chestPopulated) return;
        chestPopulated = true;

        // Defer to next tick so tile entities are ready
        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = event.getWorld();

            // Populate chest if empty
            Block chestBlock = world.getBlockAt(
                    Level94ChunkGenerator.SPAWN_X,
                    Level94ChunkGenerator.SPAWN_Y,
                    Level94ChunkGenerator.SPAWN_Z + 1);
            if (chestBlock.getType() == Material.CHEST
                    && chestBlock.getState() instanceof Chest chest
                    && chest.getInventory().isEmpty()) {
                chest.getInventory().addItem(new ItemStack(Material.LAVA_BUCKET, 1));
            }

            // Remove any bonus tree / stray blocks Paper may have placed at spawn.
            // Scan the island chunk and clear OAK_LOG / OAK_LEAVES / DIRT that are
            // outside the expected island + tree geometry.
            cleanBonusTree(world);
        });
    }

    private void cleanBonusTree(World world) {
        int treeX = Level94ChunkGenerator.SPAWN_X; // 6
        int treeZ = 7; // TREE_Z
        int treeBaseY = Level94ChunkGenerator.SPAWN_Y; // 65

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 100; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    Material type = b.getType();
                    if (type != Material.OAK_LOG && type != Material.OAK_LEAVES
                            && type != Material.DIRT) continue;

                    // Is this block part of our intended tree?
                    int dx = x - treeX;
                    int dz = z - treeZ;
                    boolean partOfTree =
                            // Trunk column
                            (x == treeX && z == treeZ && y >= treeBaseY && y < treeBaseY + 4
                                    && type == Material.OAK_LOG)
                            // 3×3 leaf canopy at y+2, y+3
                            || (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1
                                    && (y == treeBaseY + 2 || y == treeBaseY + 3)
                                    && type == Material.OAK_LEAVES)
                            // Leaf cap
                            || (x == treeX && z == treeZ && y == treeBaseY + 4
                                    && type == Material.OAK_LEAVES);

                    // Is this block part of the island platform?
                    boolean partOfIsland =
                            x >= 5 && x <= 7 && z >= 5 && z <= 10
                            && y >= 61 && y <= 64
                            && type == Material.DIRT;

                    if (!partOfTree && !partOfIsland) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
    }
}
