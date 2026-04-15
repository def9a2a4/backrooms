package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.exit;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Factory registry mapping exit trigger type IDs to constructors.
 * Each factory takes a ConfigurationSection and produces an ExitTrigger.
 */
public class ExitTriggerRegistry {

    private final Map<String, Function<ConfigurationSection, ExitTrigger>> factories = new HashMap<>();

    public void register(String typeId, Function<ConfigurationSection, ExitTrigger> factory) {
        factories.put(typeId, factory);
    }

    @Nullable
    public ExitTrigger create(String typeId, ConfigurationSection config) {
        Function<ConfigurationSection, ExitTrigger> factory = factories.get(typeId);
        return factory != null ? factory.apply(config) : null;
    }

    public boolean has(String typeId) {
        return factories.containsKey(typeId);
    }

    public Set<String> getTypeIds() {
        return factories.keySet();
    }
}
