package name.d3420b8b7fe04.def9a2a4.plugintemplate;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.BackroomsAmbianceManager;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.BackroomsCommand;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.BackroomsListener;
import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.BackroomsManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginTemplatePlugin extends JavaPlugin {

    // Replace with your bStats plugin ID from https://bstats.org
    private static final int BSTATS_ID = 00000;

    private BackroomsManager backroomsManager;
    private BackroomsAmbianceManager ambianceManager;

    @Override
    public void onEnable() {
        new Metrics(this, BSTATS_ID);
        saveDefaultConfig();

        backroomsManager = new BackroomsManager();
        backroomsManager.loadOrCreateWorld();

        ambianceManager = new BackroomsAmbianceManager(this, backroomsManager);
        ambianceManager.start();

        getServer().getPluginManager().registerEvents(new BackroomsListener(backroomsManager), this);

        BackroomsCommand backroomsCommand = new BackroomsCommand(backroomsManager);
        getCommand("backrooms").setExecutor(backroomsCommand);
        getCommand("backrooms").setTabCompleter(backroomsCommand);

        getLogger().info("PluginTemplate enabled.");
    }

    @Override
    public void onDisable() {
        ambianceManager.stop();
        backroomsManager.unloadWorld();
        getLogger().info("PluginTemplate disabled.");
    }
}
