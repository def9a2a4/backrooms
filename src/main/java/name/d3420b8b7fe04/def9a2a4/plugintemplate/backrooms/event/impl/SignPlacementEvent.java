package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SignPlacementEvent extends AbstractTimedEvent {

    private int radiusMin = 10;
    private int radiusMax = 25;

    private List<String> messages = List.of(
            "STOP", "I am watching", "Not real", "Wake up", "Level -1",
            "behind you", "DON'T TURN AROUND", "you've been here before",
            "exit: none", ""
    );

    private static final BlockFace[] CARDINAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    public SignPlacementEvent() {
        this.chance = 0.15;
        this.checkIntervalTicks = 60 * 20;
    }

    @Override
    public String getId() {
        return "sign_placement";
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        if (config != null) {
            radiusMin = config.getInt("radius_min", radiusMin);
            radiusMax = config.getInt("radius_max", radiusMax);
            List<String> cfgMessages = config.getStringList("messages");
            if (!cfgMessages.isEmpty()) {
                messages = cfgMessages;
            }
        }
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location playerLoc = player.getLocation();
        Vector facing = playerLoc.getDirection().setY(0).normalize();
        Vector behind = facing.multiply(-1);

        for (int attempt = 0; attempt < 20; attempt++) {
            double angleOffset = Math.toRadians(rng.nextDouble(-75, 75));
            double baseAngle = Math.atan2(behind.getZ(), behind.getX());
            double angle = baseAngle + angleOffset;
            double distance = radiusMin + rng.nextDouble() * (radiusMax - radiusMin);

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            for (int dy = -1; dy <= 2; dy++) {
                int bx = (int) Math.floor(x);
                int by = (int) playerLoc.getY() + dy;
                int bz = (int) Math.floor(z);
                Block candidate = playerLoc.getWorld().getBlockAt(bx, by, bz);

                if (!candidate.getType().isAir()) continue;

                // Find a solid neighbor to attach the sign to
                for (BlockFace face : CARDINAL_FACES) {
                    Block neighbor = candidate.getRelative(face);
                    if (!neighbor.getType().isSolid()) continue;
                    if (!neighbor.getType().isOccluding()) continue;

                    // The sign faces AWAY from the solid block (toward the air side)
                    BlockFace signFacing = face.getOppositeFace();

                    candidate.setType(Material.OAK_WALL_SIGN, false);
                    var blockData = candidate.getBlockData();
                    if (blockData instanceof org.bukkit.block.data.type.WallSign wallSign) {
                        wallSign.setFacing(signFacing);
                        candidate.setBlockData(wallSign, false);
                    }

                    var blockState = candidate.getState();
                    if (blockState instanceof Sign sign) {
                        String msg = messages.get(rng.nextInt(messages.size()));
                        sign.getSide(Side.FRONT).line(1, Component.text(msg));
                        sign.update();
                    }
                    return; // placed one sign, done
                }
            }
        }
    }
}
