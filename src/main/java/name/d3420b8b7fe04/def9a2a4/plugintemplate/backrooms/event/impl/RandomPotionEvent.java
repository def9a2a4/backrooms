package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event.AbstractTimedEvent;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomPotionEvent extends AbstractTimedEvent {

    private int durationTicks = 60;
    private int maxAmplifier = 1;

    private static final PotionEffectType[] DEFAULT_EFFECTS = {
            PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS,
            PotionEffectType.LEVITATION,
            PotionEffectType.SLOW_FALLING,
            PotionEffectType.SPEED,
            PotionEffectType.SLOWNESS,
            PotionEffectType.NIGHT_VISION,
            PotionEffectType.GLOWING
    };

    private List<PotionEffectType> effects = new ArrayList<>(List.of(DEFAULT_EFFECTS));

    public RandomPotionEvent() {
        this.chance = 0.2;
        this.checkIntervalTicks = 15 * 20;
    }

    @Override
    public String getId() {
        return "random_potion";
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        super.loadConfig(config);
        if (config != null) {
            durationTicks = config.getInt("duration_ticks", durationTicks);
            maxAmplifier = config.getInt("max_amplifier", maxAmplifier);
        }
    }

    @Override
    public void trigger(Player player, BackroomsPlayerState state) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        PotionEffectType type = effects.get(rng.nextInt(effects.size()));
        int amplifier = rng.nextInt(maxAmplifier + 1);
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, false, false));
    }
}
