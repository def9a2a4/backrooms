package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class EntityBehaviorRegistry {

    private final Map<String, Supplier<EntityBehavior>> factories = new HashMap<>();

    public void register(String name, Supplier<EntityBehavior> factory) {
        factories.put(name, factory);
    }

    public EntityBehavior create(String name) {
        Supplier<EntityBehavior> factory = factories.get(name);
        if (factory == null) return null;
        return factory.get();
    }
}
