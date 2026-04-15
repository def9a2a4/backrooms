package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class AmbientSoundEvent extends AbstractTimedEvent {

    private List<Sound> sounds = new ArrayList<>();
    private Logger logger;

    public AmbientSoundEvent() {
        this.chance = 0.3;
        this.checkIntervalTicks = 5 * 20;
    }

    @Override
    public String getId() {
        return "ambient_sound";
    }

    @Override
    public void init(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        sounds = new ArrayList<>();
        if (config != null && config.contains("sounds")) {
            for (String name : config.getStringList("sounds")) {
                String lower = name.toLowerCase(Locale.ROOT);
                // Try as direct minecraft key (e.g. "ambient.warped_forest.mood")
                Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(lower));
                if (sound == null) {
                    // Try enum-style name: normalize both sides to underscores and compare
                    String normalized = lower.replace('.', '_');
                    for (Sound s : Registry.SOUNDS) {
                        if (s.key().value().replace('.', '_').equals(normalized)) {
                            sound = s;
                            break;
                        }
                    }
                }
                if (sound != null) {
                    sounds.add(sound);
                } else if (logger != null) {
                    logger.warning("Unknown sound in config: " + name);
                }
            }
        }
        if (sounds.isEmpty()) {
            sounds.add(Sound.AMBIENT_CAVE);
        }
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Sound sound = sounds.get(rng.nextInt(sounds.size()));
        Location loc = player.getLocation().add(
                rng.nextDouble(-15, 15),
                rng.nextDouble(-3, 3),
                rng.nextDouble(-15, 15)
        );
        float pitch = 0.7f + rng.nextFloat() * 0.6f;
        player.playSound(loc, sound, SoundCategory.AMBIENT, 0.8f, pitch);
    }
}
