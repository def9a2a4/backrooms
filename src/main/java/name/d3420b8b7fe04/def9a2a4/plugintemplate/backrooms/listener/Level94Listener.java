package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.advancement.AdvancementManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level94ChunkGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.BackroomsLevel;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.SpawnFinder;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.PlayerStateManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Directional;
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

    private static final String WORLD_NAME = "bkrms_94";
    private static final int VOID_THRESHOLD = 10;
    private static final int CASCADE_DURATION_TICKS = 400; // 20 seconds

    private static Level94Listener instance;

    private final JavaPlugin plugin;
    private final LevelRegistry levelRegistry;
    private final PlayerStateManager playerStateManager;
    private final AdvancementManager advancementManager;
    private final String transitionMessage;

    private final Set<UUID> fallCooldown = new HashSet<>();
    private final Set<UUID> animating = new HashSet<>();
    private boolean chestPopulated = false;

    public Level94Listener(JavaPlugin plugin, LevelRegistry levelRegistry,
                           PlayerStateManager playerStateManager,
                           AdvancementManager advancementManager,
                           String transitionMessage) {
        this.plugin = plugin;
        this.levelRegistry = levelRegistry;
        this.playerStateManager = playerStateManager;
        this.advancementManager = advancementManager;
        this.transitionMessage = transitionMessage;
        instance = this;
    }

    public static Level94Listener getInstance() {
        return instance;
    }

    /** Trigger the cascade animation from a command (no clicked block). */
    public void triggerCascade(Player player) {
        if (!player.getWorld().getName().equals(WORLD_NAME)) return;
        if (animating.contains(player.getUniqueId())) return;
        startAnimation(player, null);
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

    // ── Barrier Wall Cascade ──────────────────────────────────────────

    private static final int BMIN = Level94ChunkGenerator.BARRIER_MIN;
    private static final int BMAX = Level94ChunkGenerator.BARRIER_MAX;

    private void startAnimation(Player player, Location clickedBlock) {
        UUID uuid = player.getUniqueId();
        animating.add(uuid);

        World world = player.getWorld();
        Random rng = new Random();

        // Each origin: {position-along-wall, y, face}
        //   face 0 = north (Z=BMIN), 1 = south (Z=BMAX), 2 = west (X=BMIN), 3 = east (X=BMAX)
        List<int[]> origins = new ArrayList<>();

        // Add the clicked barrier block as an origin on its face
        if (clickedBlock != null) {
            int cx = clickedBlock.getBlockX(), cy = clickedBlock.getBlockY(), cz = clickedBlock.getBlockZ();
            if (cz == BMIN)      origins.add(new int[]{cx, cy, 0});
            else if (cz == BMAX) origins.add(new int[]{cx, cy, 1});
            else if (cx == BMIN) origins.add(new int[]{cz, cy, 2});
            else if (cx == BMAX) origins.add(new int[]{cz, cy, 3});
        }

        // 1-2 random origin points on each of the 4 barrier faces
        for (int face = 0; face < 4; face++) {
            int count = 1 + rng.nextInt(2);
            for (int i = 0; i < count; i++) {
                int pos = BMIN + rng.nextInt(BMAX - BMIN + 1);
                int y = 10 + rng.nextInt(80);
                origins.add(new int[]{pos, y, face});
            }
        }

        int[] radius = {0};

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                taskHolder[0].cancel();
                animating.remove(uuid);
                return;
            }

            for (int[] origin : origins) {
                int face = origin[2];

                for (int d1 = -radius[0]; d1 <= radius[0]; d1++) {
                    for (int dy = -radius[0]; dy <= radius[0]; dy++) {
                        if (Math.abs(d1) + Math.abs(dy) > radius[0]) continue; // manhattan

                        int by = origin[1] + dy;
                        if (by < 0 || by > 100) continue;

                        int bx, bz, behindX, behindZ;
                        switch (face) {
                            case 0: // North wall (Z = BMIN), expand along X
                                bx = origin[0] + d1; bz = BMIN;
                                behindX = bx; behindZ = bz - 1;
                                break;
                            case 1: // South wall (Z = BMAX), expand along X
                                bx = origin[0] + d1; bz = BMAX;
                                behindX = bx; behindZ = bz + 1;
                                break;
                            case 2: // West wall (X = BMIN), expand along Z
                                bx = BMIN; bz = origin[0] + d1;
                                behindX = bx - 1; behindZ = bz;
                                break;
                            case 3: // East wall (X = BMAX), expand along Z
                                bx = BMAX; bz = origin[0] + d1;
                                behindX = bx + 1; behindZ = bz;
                                break;
                            default: continue;
                        }

                        if (bx < BMIN || bx > BMAX || bz < BMIN || bz > BMAX) continue;
                        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;

                        // Barrier → glass
                        Block b = world.getBlockAt(bx, by, bz);
                        if (b.getType() == Material.BARRIER) {
                            b.setType(Material.LIGHT_BLUE_STAINED_GLASS, false);
                        }

                        // Fill behind with server-room blocks
                        if (!world.isChunkLoaded(behindX >> 4, behindZ >> 4)) continue;
                        Block behind = world.getBlockAt(behindX, by, behindZ);
                        if (behind.getType() == Material.AIR) {
                            double roll = rng.nextDouble();
                            if (roll < 0.15) {
                                // Observer facing inward (toward the island)
                                BlockFace inward = switch (face) {
                                    case 0 -> BlockFace.SOUTH;
                                    case 1 -> BlockFace.NORTH;
                                    case 2 -> BlockFace.EAST;
                                    default -> BlockFace.WEST;
                                };
                                Directional obs = (Directional) Material.OBSERVER.createBlockData();
                                obs.setFacing(inward);
                                behind.setBlockData(obs, false);
                            } else {
                                Material fill = switch (rng.nextInt(5)) {
                                    case 0 -> Material.COMMAND_BLOCK;
                                    case 1 -> Material.CHAIN_COMMAND_BLOCK;
                                    case 2 -> Material.REPEATING_COMMAND_BLOCK;
                                    default -> Material.WHITE_CONCRETE;
                                };
                                behind.setType(fill, false);
                            }
                        }
                    }
                }
            }

            radius[0]++;
        }, 0L, 10L);

        // Transition after 20 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            taskHolder[0].cancel();
            transitionToServerRoom(player);
        }, CASCADE_DURATION_TICKS);
    }

    // ── Transition to Level 3 ───────────────────────────────────────────

    private void transitionToServerRoom(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false));
        player.sendMessage(transitionMessage);

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
            SpawnFinder.clearFallDamage(player);
            state.setCurrentLevelId("level_3");

            if (targetLevel != null) {
                targetLevel.onPlayerEnter(player, state);
            }

            advancementManager.grantSkyblockHint(player);
            advancementManager.grantLevelDiscovery(player, "level_3");

            animating.remove(uuid);

            // Schedule regeneration after all players have left
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World w = levelRegistry.getWorld("level_94");
                if (w != null && w.getPlayers().isEmpty()) {
                    chestPopulated = false;
                    levelRegistry.regenerateLevel("level_94");
                }
            }, 20L);
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
                chest.getInventory().addItem(new ItemStack(Material.WATER_BUCKET, 1));
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
