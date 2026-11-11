package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.listener;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.Level56ChunkGenerator;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level.LevelRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Pig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Level 56 "The Henge" — the giant pigs.
 *
 * <p>Each time a player arrives, one giant pig is born inside the stone circle (up to
 * {@code max_pigs} alive at once). Killed, a pig drops a single named porkchop; laid on the
 * lodestone altar at the center, a porkchop is the way out (the {@code item_offering} exit in
 * level_56.yml — any porkchop is accepted). While players are here the center chunks stay loaded
 * so the pigs keep grazing, and a pig that wanders too far is led back.
 */
public class Level56Listener implements Listener {

    public static final NamespacedKey PIG_KEY = new NamespacedKey("backrooms", "giant_pig");
    private static final String LEVEL_ID = "level_56";
    private static final int ALTAR_CLEARANCE = 6;   // pigs are born at least this far from the altar

    /** Tunables from level_56.yml {@code giant_pig:}. */
    public record PigConfig(double scale, double health, int maxPigs, double leashRadius,
                            String dropName, List<String> dropLore) {}

    private final JavaPlugin plugin;
    private final LevelRegistry levelRegistry;
    private final PigConfig config;

    private BukkitTask task;
    private World ticketWorld;
    private int ticketX, ticketZ;
    private int pendingSpawns;

    public Level56Listener(JavaPlugin plugin, LevelRegistry levelRegistry, PigConfig config) {
        this.plugin = plugin;
        this.levelRegistry = levelRegistry;
        this.config = config;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        releaseTickets();
    }

    @EventHandler
    public void onArrive(PlayerChangedWorldEvent event) {
        World world = levelRegistry.getWorld(LEVEL_ID);
        if (world != null && event.getPlayer().getWorld().equals(world)) pendingSpawns++;
    }

    private void tick() {
        World world = levelRegistry.getWorld(LEVEL_ID);
        if (world == null || !(world.getGenerator() instanceof Level56ChunkGenerator gen)) return;
        int cx = gen.getCenterX(), cz = gen.getCenterZ();

        // Only hold the pasture loaded while someone is here; the pigs are saved with their chunks.
        if (world.getPlayers().isEmpty()) {
            releaseTickets();
            pendingSpawns = 0;
            return;
        }
        holdTickets(world, cx >> 4, cz >> 4);
        if (!world.isChunkLoaded(cx >> 4, cz >> 4) || !world.getChunkAt(cx >> 4, cz >> 4).isEntitiesLoaded()) {
            return;   // saved pigs may not be back yet; count them before spawning more
        }

        List<Pig> pigs = new ArrayList<>();
        for (Pig p : world.getEntitiesByClass(Pig.class)) {
            if (!p.isDead() && p.getPersistentDataContainer().has(PIG_KEY, PersistentDataType.BYTE)) pigs.add(p);
        }
        double leash2 = config.leashRadius() * config.leashRadius();
        for (Pig p : pigs) {
            double dx = p.getLocation().getX() - (cx + 0.5), dz = p.getLocation().getZ() - (cz + 0.5);
            if (dx * dx + dz * dz > leash2) p.teleport(pasture(world, cx, cz));
        }
        int alive = pigs.size();
        while (pendingSpawns > 0 && alive < config.maxPigs()) {
            spawnPig(pasture(world, cx, cz));
            alive++;
            pendingSpawns--;
        }
        pendingSpawns = 0;   // arrivals while the circle is full don't queue up
    }

    /** A random spot inside the leash radius, clear of the altar. */
    private Location pasture(World world, int cx, int cz) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double angle = rng.nextDouble(Math.PI * 2);
        double r = ALTAR_CLEARANCE + rng.nextDouble(Math.max(1, config.leashRadius() - ALTAR_CLEARANCE));
        int x = cx + (int) Math.round(Math.cos(angle) * r), z = cz + (int) Math.round(Math.sin(angle) * r);
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5,
                (float) rng.nextDouble(360), 0);
    }

    private void spawnPig(Location at) {
        at.getWorld().spawn(at, Pig.class, pig -> {
            pig.getPersistentDataContainer().set(PIG_KEY, PersistentDataType.BYTE, (byte) 1);
            pig.setPersistent(true);
            pig.setRemoveWhenFarAway(false);
            AttributeInstance scale = pig.getAttribute(Attribute.SCALE);
            if (scale != null) scale.setBaseValue(config.scale());
            AttributeInstance health = pig.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(config.health());
                pig.setHealth(config.health());
            }
        });
    }

    @EventHandler
    public void onPigDeath(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(PIG_KEY, PersistentDataType.BYTE)) return;
        event.getDrops().clear();
        event.getDrops().add(porkchop());
        event.setDroppedExp(10);
    }

    private ItemStack porkchop() {
        ItemStack item = new ItemStack(Material.PORKCHOP);
        ItemMeta meta = item.getItemMeta();
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        if (config.dropName() != null && !config.dropName().isEmpty()) {
            meta.displayName(legacy.deserialize(config.dropName()).decoration(TextDecoration.ITALIC, false));
        }
        List<Component> lore = new ArrayList<>();
        for (String line : config.dropLore()) {
            lore.add(legacy.deserialize(line).decoration(TextDecoration.ITALIC, true));
        }
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void holdTickets(World world, int chunkX, int chunkZ) {
        if (ticketWorld != null) return;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.addPluginChunkTicket(chunkX + x, chunkZ + z, plugin);
            }
        }
        ticketWorld = world;
        ticketX = chunkX;
        ticketZ = chunkZ;
    }

    private void releaseTickets() {
        if (ticketWorld == null) return;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                ticketWorld.removePluginChunkTicket(ticketX + x, ticketZ + z, plugin);
            }
        }
        ticketWorld = null;
    }
}
