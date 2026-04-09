package name.d3420b8b7fe04.def9a2a4.plugintemplate;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.BackroomsPlugin;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginTemplatePlugin extends JavaPlugin {

    // Replace with your bStats plugin ID from https://bstats.org
    private static final int BSTATS_ID = 00000;

    private BackroomsPlugin backrooms;

    @Override
    public void onEnable() {
        new Metrics(this, BSTATS_ID);
        saveDefaultConfig();

        backrooms = new BackroomsPlugin(this);
        backrooms.enable();

        getLogger().info("PluginTemplate enabled.");
    }

    @Override
    public void onDisable() {
        if (backrooms != null) {
            backrooms.disable();
        }
        getLogger().info("PluginTemplate disabled.");
    }
}
