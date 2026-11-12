package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Level 9500 "Göbekli Tepe": the first farm. Seeds shaken out of the wild grass, the wheat grown
 * from them and the bread baked from it carry names and lore ({@code einkorn:} in level_9500.yml),
 * as the giant pig's porkchop does in Level 56. Any bread works as the offering.
 */
public class Level9500Listener implements Listener {

    private static final String LEVEL_ID = "level_9500";

    /** A name and lore lines, legacy § formatted. */
    public record Label(String name, List<String> lore) {
        static @Nullable Label from(@Nullable ConfigurationSection s) {
            return s == null ? null : new Label(s.getString("name", ""), s.getStringList("lore"));
        }
    }

    private final LevelRegistry levelRegistry;
    private final @Nullable Label seeds, wheat, bread;

    public Level9500Listener(LevelRegistry levelRegistry, @Nullable ConfigurationSection einkorn) {
        this.levelRegistry = levelRegistry;
        this.seeds = Label.from(einkorn == null ? null : einkorn.getConfigurationSection("seeds"));
        this.wheat = Label.from(einkorn == null ? null : einkorn.getConfigurationSection("wheat"));
        this.bread = Label.from(einkorn == null ? null : einkorn.getConfigurationSection("bread"));
    }

    private boolean here(World world) {
        World w = levelRegistry.getWorld(LEVEL_ID);
        return w != null && w.equals(world);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        if (!here(event.getBlock().getWorld())) return;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            Label label = stack.getType() == Material.WHEAT_SEEDS ? seeds
                    : stack.getType() == Material.WHEAT ? wheat : null;
            if (label == null) continue;
            apply(stack, label);
            item.setItemStack(stack);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() != Material.BREAD || bread == null) return;
        if (event.getView().getPlayer() == null || !here(event.getView().getPlayer().getWorld())) return;
        apply(result, bread);
        event.getInventory().setResult(result);
    }

    private static void apply(ItemStack stack, Label label) {
        ItemMeta meta = stack.getItemMeta();
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        if (!label.name().isEmpty()) {
            meta.displayName(legacy.deserialize(label.name()).decoration(TextDecoration.ITALIC, false));
        }
        List<Component> lore = new ArrayList<>();
        for (String line : label.lore()) lore.add(legacy.deserialize(line).decoration(TextDecoration.ITALIC, true));
        if (!lore.isEmpty()) meta.lore(lore);
        stack.setItemMeta(meta);
    }
}
