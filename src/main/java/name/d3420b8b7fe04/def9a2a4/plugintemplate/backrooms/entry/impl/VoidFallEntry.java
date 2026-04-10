package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.impl;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry.EntryTrigger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VoidFallEntry implements EntryTrigger {

    private boolean enabled = true;
    private double yThreshold = -64;
    private String targetLevel = "level_0";
    private String entryMessage = "\u00a77[Server] Warning: Entity position out of bounds. Relocating...";
    private int blindnessDuration = 40;
    private int delayTicks = 30;
    private Set<String> enabledWorlds = new HashSet<>();
    private final JavaPlugin plugin;

    public VoidFallEntry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "void_fall";
    }

    @Override
    public List<Class<? extends Event>> getListenedEvents() {
        return List.of(PlayerMoveEvent.class);
    }

    @Override
    @Nullable
    public String evaluate(Event event, Player player) {
        if (!enabled) return null;
        if (!(event instanceof PlayerMoveEvent moveEvent)) return null;
        if (moveEvent.getTo().getY() > yThreshold) return null;
        return targetLevel;
    }

    @Override
    public void playEntrySequence(Player player, Runnable onComplete) {
        if (blindnessDuration > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, 1, false, false));
        if (entryMessage != null && !entryMessage.isEmpty()) player.sendMessage(entryMessage);
        Bukkit.getScheduler().runTaskLater(plugin, onComplete, delayTicks);
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        enabled = config.getBoolean("enabled", true);
        targetLevel = config.getString("target_level", "level_0");
        entryMessage = config.getString("entry_message", entryMessage);
        blindnessDuration = config.getInt("blindness_duration", blindnessDuration);
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
