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
        return "level_0";
    }

    @Override
    public void playEntrySequence(Player player, Runnable onComplete) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1, false, false));
        player.sendMessage("\u00a77[Server] Warning: Entity position out of bounds. Relocating...");
        Bukkit.getScheduler().runTaskLater(plugin, onComplete, 30L);
    }

    @Override
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;
        enabled = config.getBoolean("enabled", true);
    }

    @Override
    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public void setEnabledWorlds(Set<String> worlds) {
        this.enabledWorlds = worlds;
    }
}
