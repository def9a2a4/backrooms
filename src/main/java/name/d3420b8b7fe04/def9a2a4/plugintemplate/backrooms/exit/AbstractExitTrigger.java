package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.player.BackroomsPlayerState;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Base class for data-driven exit triggers.
 * Parses shared config: target_level, transition effects (blindness, nausea, darkness, delay, message, sound).
 */
public abstract class AbstractExitTrigger implements ExitTrigger {

    protected final String id;
    protected final String targetLevelId;
    protected final ConfigurationSection config;

    // Transition effect config
    protected final int blindnessTicks;
    protected final int nauseaTicks;
    protected final int darknessTicks;
    protected final int delayTicks;
    protected final String message;
    protected final String sound;

    protected AbstractExitTrigger(ConfigurationSection config) {
        this.config = config;
        this.id = config.getString("id", config.getString("type", "unknown"));
        this.targetLevelId = config.getString("target_level", "overworld");

        ConfigurationSection transition = config.getConfigurationSection("transition");
        if (transition != null) {
            this.blindnessTicks = transition.getInt("blindness_ticks", 60);
            this.nauseaTicks = transition.getInt("nausea_ticks", 0);
            this.darknessTicks = transition.getInt("darkness_ticks", 0);
            this.delayTicks = transition.getInt("delay_ticks", 20);
            this.message = transition.getString("message", null);
            this.sound = transition.getString("sound", null);
        } else {
            this.blindnessTicks = 60;
            this.nauseaTicks = 0;
            this.darknessTicks = 0;
            this.delayTicks = 20;
            this.message = null;
            this.sound = null;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTargetLevelId() {
        return targetLevelId;
    }

    @Override
    public void playTransitionSequence(Player player, Runnable onComplete) {
        if (message != null) {
            player.sendMessage(message);
        }

        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }

        if (blindnessTicks > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, false, false));
        }
        if (nauseaTicks > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaTicks, 0, false, false));
        }
        if (darknessTicks > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, darknessTicks, 0, false, false));
        }

        if (delayTicks > 0) {
            JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
            Bukkit.getScheduler().runTaskLater(plugin, onComplete, delayTicks);
        } else {
            onComplete.run();
        }
    }
}
