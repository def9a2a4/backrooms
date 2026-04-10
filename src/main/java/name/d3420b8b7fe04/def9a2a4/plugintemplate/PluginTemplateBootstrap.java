package name.d3420b8b7fe04.def9a2a4.plugintemplate;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

@SuppressWarnings("UnstableApiUsage")
public class PluginTemplateBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                URI uri = getClass().getResource("/backrooms_datapack").toURI();
                event.registrar().discoverPack(uri, "backrooms");
            } catch (Exception e) {
                context.getLogger().warn("Failed to register backrooms datapack: {}", e.getMessage());
            }
        });
    }
}
