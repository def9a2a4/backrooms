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

import java.util.*;

public class SuffocationEntry implements EntryTrigger {

    private boolean enabled = true;
    private double damageThreshold = 16.0;
    private String targetLevel = "level_0";
    private String entryMessage = "\u00a77\u00a7oInternal server error: java.lang.NullPointerException at WorldGenLayer.class";
    private int blindnessDuration = 40;
    private int nauseaDuration = 60;
    private int delayTicks = 40;
    private Set<String> enabledWorlds = new HashSet<>();
    private final JavaPlugin plugin;

    private final Map<UUID, Double> accumulatedDamage = new HashMap<>();

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

        UUID uuid = player.getUniqueId();
        double total = accumulatedDamage.getOrDefault(uuid, 0.0) + damageEvent.getDamage();

        if (total >= damageThreshold) {
            accumulatedDamage.remove(uuid);
            damageEvent.setCancelled(true);
            return targetLevel;
        }

        accumulatedDamage.put(uuid, total);
        // Decay tracker if player stops taking suffocation damage within 3 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Double current = accumulatedDamage.get(uuid);
            if (current != null && current == total) {
                accumulatedDamage.remove(uuid);
            }
        }, 60L);
        return null;
    }

    @Override
    public String getTargetLevel() {
        return targetLevel;
    }

    @Override
    public void playEntrySequence(Player player, Runnable onComplete) {
        if (blindnessDuration > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, 1, false, false));
        if (nauseaDuration > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDuration, 0, false, false));
        if (entryMessage != null && !entryMessage.isEmpty()) player.sendMessage(entryMessage);
        Bukkit.getScheduler().runTaskLater(plugin, onComplete, delayTicks);
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        enabled = config.getBoolean("enabled", true);
        damageThreshold = config.getDouble("damage_threshold", 16.0);
        targetLevel = config.getString("target_level", "level_0");
        entryMessage = config.getString("entry_message", entryMessage);
        blindnessDuration = config.getInt("blindness_duration", blindnessDuration);
        nauseaDuration = config.getInt("nausea_duration", nauseaDuration);
        delayTicks = config.getInt("delay_ticks", delayTicks);
    }

    @Override
    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public void setEnabledWorlds(Set<String> worlds) {
        this.enabledWorlds = worlds;
    }
}
