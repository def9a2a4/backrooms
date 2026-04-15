package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class EntityTypeRegistry {

    private final Map<String, BiFunction<String, ConfigurationSection, BackroomsEntity>> factories = new HashMap<>();

    public void register(String typeId, BiFunction<String, ConfigurationSection, BackroomsEntity> factory) {
        factories.put(typeId, factory);
    }

    public BackroomsEntity create(String typeId, String instanceId, ConfigurationSection config) {
        BiFunction<String, ConfigurationSection, BackroomsEntity> factory = factories.get(typeId);
        if (factory == null) return null;
        return factory.apply(instanceId, config);
    }
}
