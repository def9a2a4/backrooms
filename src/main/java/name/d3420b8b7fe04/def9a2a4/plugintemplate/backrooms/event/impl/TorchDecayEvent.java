package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class TorchDecayEvent extends AbstractTimedEvent {

    private int radius = 20;
    private double extinguishChance = 0.3;

    public TorchDecayEvent() {
        this.chance = 0.4;
        this.checkIntervalTicks = 60 * 20;
    }

    @Override
    public String getId() {
        return "torch_decay";
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        if (config != null) {
            radius = config.getInt("radius", radius);
            extinguishChance = config.getDouble("extinguish_chance", extinguishChance);
        }
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        Location center = player.getLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        boolean anyExtinguished = false;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = center.clone().add(dx, dy, dz).getBlock();
                    Material type = block.getType();
                    if (isTorch(type) && rng.nextDouble() < extinguishChance) {
                        block.setType(Material.AIR, false);
                        anyExtinguished = true;
                    }
                }
            }
        }

        if (anyExtinguished) {
            player.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 0.8f);
        }
    }

    private boolean isTorch(Material mat) {
        return mat == Material.TORCH || mat == Material.WALL_TORCH
                || mat == Material.SOUL_TORCH || mat == Material.SOUL_WALL_TORCH
                || mat == Material.REDSTONE_TORCH || mat == Material.REDSTONE_WALL_TORCH;
    }
}
