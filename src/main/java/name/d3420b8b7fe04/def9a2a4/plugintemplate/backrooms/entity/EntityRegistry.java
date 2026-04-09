package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EntityRegistry {

    private final Map<String, BackroomsEntity> entityTypes = new HashMap<>();

    public void register(BackroomsEntity entity) {
        entityTypes.put(entity.getId(), entity);
    }

    @Nullable
    public BackroomsEntity get(String id) {
        return entityTypes.get(id);
    }

    public Collection<BackroomsEntity> getAll() {
        return Collections.unmodifiableCollection(entityTypes.values());
    }
}
