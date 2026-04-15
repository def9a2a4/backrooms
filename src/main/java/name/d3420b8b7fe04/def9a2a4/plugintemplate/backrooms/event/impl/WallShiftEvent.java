package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class WallShiftEvent extends AbstractTimedEvent {

    private int radiusMin = 8;
    private int radiusMax = 20;
    private int blocksPerTrigger = 2;

    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    public WallShiftEvent() {
        this.chance = 0.20;
        this.checkIntervalTicks = 45 * 20;
    }

    @Override
    public String getId() {
        return "wall_shift";
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        if (config != null) {
            radiusMin = config.getInt("radius_min", radiusMin);
            radiusMax = config.getInt("radius_max", radiusMax);
            blocksPerTrigger = config.getInt("blocks_per_trigger", blocksPerTrigger);
        }
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location playerLoc = player.getLocation();
        Vector facing = playerLoc.getDirection().setY(0).normalize();
        Vector behind = facing.multiply(-1);

        int placed = 0;
        for (int attempt = 0; attempt < blocksPerTrigger * 5 && placed < blocksPerTrigger; attempt++) {
            // Random angle within 150° arc behind player (±75°)
            double angleOffset = Math.toRadians(rng.nextDouble(-75, 75));
            double baseAngle = Math.atan2(behind.getZ(), behind.getX());
            double angle = baseAngle + angleOffset;
            double distance = radiusMin + rng.nextDouble() * (radiusMax - radiusMin);

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            // Scan Y range around player
            for (int dy = -1; dy <= 2; dy++) {
                Block candidate = playerLoc.getWorld().getBlockAt(
                        (int) Math.floor(x), (int) playerLoc.getY() + dy, (int) Math.floor(z));

                if (!candidate.getType().isAir()) continue;

                // Find an adjacent solid block to match material
                for (BlockFace face : HORIZONTAL_FACES) {
                    Block neighbor = candidate.getRelative(face);
                    if (!neighbor.getType().isSolid()) continue;
                    if (!neighbor.getType().isOccluding()) continue;

                    // Safety: don't place within 3 blocks of player
                    if (candidate.getLocation().distance(playerLoc) < 3.0) continue;

                    // Safety: don't seal player in — check player's block stays air
                    Block playerBlock = playerLoc.getBlock();
                    Block playerHead = playerBlock.getRelative(BlockFace.UP);
                    if (candidate.equals(playerBlock) || candidate.equals(playerHead)) continue;

                    candidate.setType(neighbor.getType(), false);
                    player.playSound(candidate.getLocation(), Sound.BLOCK_STONE_PLACE,
                            SoundCategory.BLOCKS, 0.12f, 1.0f);
                    placed++;
                    break;
                }
                if (placed >= blocksPerTrigger) break;
            }
        }
    }
}
