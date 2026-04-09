package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryTrigger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class SuffocationEntry implements EntryTrigger {

    private boolean enabled = true;
    private double chance = 0.02;
    private Set<String> enabledWorlds = new HashSet<>();
    private final JavaPlugin plugin;

    public SuffocationEntry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "suffocation_noclip";
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(EntityDamageEvent.class);
    }

    @Override
    @Nullable
    public String evaluate(Event event, Player player) {
        if (!enabled) return null;
        if (!(event instanceof EntityDamageEvent damageEvent)) return null;
        if (damageEvent.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) return null;
        if (ThreadLocalRandom.current().nextDouble() >= chance) return null;
        damageEvent.setCancelled(true);
        return "level_0";
    }

    @Override
    public void playEntrySequence(Player player, Runnable onComplete) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false));
        player.sendMessage("\u00a77\u00a7oInternal server error: java.lang.NullPointerException at WorldGenLayer.class");
        Bukkit.getScheduler().runTaskLater(plugin, onComplete, 40L);
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        enabled = config.getBoolean("enabled", true);
        chance = config.getDouble("chance", 0.02);
    }

    @Override
    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public void setEnabledWorlds(Set<String> worlds) {
        this.enabledWorlds = worlds;
    }
}
