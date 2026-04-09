package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryTrigger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class BedAnomalyEntry implements EntryTrigger {

    private boolean enabled = true;
    private double chance = 0.05;
    private boolean requireThunderstorm = true;
    private Set<String> enabledWorlds = new HashSet<>();
    private final JavaPlugin plugin;

    public BedAnomalyEntry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "bed_anomaly";
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(PlayerBedEnterEvent.class);
    }

    @Override
    @Nullable
    public String evaluate(Event event, Player player) {
        if (!enabled) return null;
        if (!(event instanceof PlayerBedEnterEvent bedEvent)) return null;
        if (bedEvent.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return null;
        if (requireThunderstorm && !player.getWorld().isThundering()) return null;
        if (ThreadLocalRandom.current().nextDouble() >= chance) return null;
        bedEvent.setCancelled(true);
        return "level_0";
    }

    @Override
    public void playEntrySequence(Player player, Runnable onComplete) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1, false, false));
        player.sendMessage("\u00a77You wake up. Something is wrong.");
        Bukkit.getScheduler().runTaskLater(plugin, onComplete, 60L);
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        enabled = config.getBoolean("enabled", true);
        chance = config.getDouble("chance", 0.05);
        requireThunderstorm = config.getBoolean("require_thunderstorm", true);
    }

    @Override
    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public void setEnabledWorlds(Set<String> worlds) {
        this.enabledWorlds = worlds;
    }
}
