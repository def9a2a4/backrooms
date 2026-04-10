package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BlockCorruptionEvent extends AbstractTimedEvent {

    private int radius = 20;
    private int cubeSize = 4;

    private static final Material[] DEFAULT_CORRUPT_MATERIALS = {
            Material.NETHERRACK, Material.GRASS_BLOCK, Material.END_STONE,
            Material.SAND, Material.GRAVEL, Material.ICE, Material.MAGMA_BLOCK,
            Material.COBBLESTONE, Material.OAK_PLANKS, Material.OBSIDIAN,
            Material.PRISMARINE, Material.SOUL_SAND, Material.CRIMSON_NYLIUM,
            Material.WARPED_NYLIUM, Material.MYCELIUM, Material.PODZOL,
            Material.TERRACOTTA, Material.DEEPSLATE, Material.TUFF,
            Material.CALCITE, Material.DRIPSTONE_BLOCK
    };

    private Material[] corruptMaterials = DEFAULT_CORRUPT_MATERIALS;

    public BlockCorruptionEvent() {
        this.chance = 0.15;
        this.checkIntervalTicks = 30 * 20;
    }

    @Override
    public String getId() {
        return "block_corruption";
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        if (config != null) {
            radius = config.getInt("radius", radius);
            cubeSize = config.getInt("cube_size", cubeSize);
            List<String> materialNames = config.getStringList("materials");
            if (!materialNames.isEmpty()) {
                Material[] parsed = materialNames.stream()
                        .map(Material::matchMaterial)
                        .filter(m -> m != null)
                        .toArray(Material[]::new);
                if (parsed.length > 0) corruptMaterials = parsed;
            }
        }
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location center = player.getLocation().add(
                rng.nextInt(-radius, radius),
                rng.nextInt(-5, 5),
                rng.nextInt(-radius, radius)
        );

        for (int dx = 0; dx < cubeSize; dx++) {
            for (int dy = 0; dy < cubeSize; dy++) {
                for (int dz = 0; dz < cubeSize; dz++) {
                    Block block = center.clone().add(dx, dy, dz).getBlock();
                    if (!block.getType().isAir()) {
                        block.setType(corruptMaterials[rng.nextInt(corruptMaterials.length)], false);
                    }
                }
            }
        }
    }
}
