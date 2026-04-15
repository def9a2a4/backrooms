package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit.AbstractExitTrigger;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Multi-step trigger for Level 2 → Poolrooms transition.
 *
 * Step 1: Player finds and flips a rare lever in a machinery room → lever drops as item,
 *         tracked via customData ("lever_pipe_has_lever" = "true").
 * Step 2: Player finds a rare copper trapdoor in a pipe wall. Right-clicking the
 *         trapdoor while holding a lever places it, opens the trapdoor, and triggers transition.
 *
 * Config:
 *   type: lever_pipe
 *   target_level: level_37
 *   trapdoor_material: COPPER_TRAPDOOR
 */
public class LeverPipeTrigger extends AbstractExitTrigger {

    private static final String STATE_KEY = "lever_pipe_has_lever";
    private final Material trapdoorMaterial;

    public LeverPipeTrigger(ConfigurationSection config) {
        super(config);
        String matStr = config.getString("trapdoor_material", "COPPER_TRAPDOOR");
        Material parsed;
        try {
            parsed = Material.valueOf(matStr);
        } catch (IllegalArgumentException e) {
            parsed = Material.COPPER_TRAPDOOR;
        }
        this.trapdoorMaterial = parsed;
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(PlayerInteractEvent.class);
    }

    @Override
    public boolean checkEvent(Event event, Player player, BackroomsPlayerState state) {
        if (!(event instanceof PlayerInteractEvent interact)) return false;
        if (interact.getClickedBlock() == null) return false;

        Block clicked = interact.getClickedBlock();

        // Step 1: Flip a lever → track it and give lever item
        if (clicked.getType() == Material.LEVER) {
            if (!"true".equals(state.getCustomData(STATE_KEY))) {
                state.setCustomData(STATE_KEY, "true");
                player.getInventory().addItem(new ItemStack(Material.LEVER, 1));
                clicked.setType(Material.AIR);
                player.sendMessage("The lever snaps off in your hand...");
                interact.setCancelled(true);
            }
            return false; // Don't trigger transition on step 1
        }

        // Step 2: Right-click copper trapdoor while holding lever
        if (clicked.getType() == trapdoorMaterial) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.LEVER && "true".equals(state.getCustomData(STATE_KEY))) {
                hand.setAmount(hand.getAmount() - 1);
                openTrapdoor(clicked);
                interact.setCancelled(true);
                return true; // Trigger transition
            }
        }

        return false;
    }

    @Override
    public boolean check(Player player, BackroomsPlayerState state) {
        // Event-driven only
        return false;
    }

    private void openTrapdoor(Block block) {
        org.bukkit.block.data.BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.TrapDoor trapDoor) {
            trapDoor.setOpen(true);
            block.setBlockData(trapDoor);
        }
    }
}
