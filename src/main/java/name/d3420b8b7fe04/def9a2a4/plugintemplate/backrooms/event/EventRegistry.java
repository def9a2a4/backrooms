package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.event;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EventRegistry {

    private final Map<String, BackroomsEvent> events = new HashMap<>();

    public void register(BackroomsEvent event) {
        events.put(event.getId(), event);
    }

    @Nullable
    public BackroomsEvent get(String id) {
        return events.get(id);
    }

    public Collection<BackroomsEvent> getAll() {
        return Collections.unmodifiableCollection(events.values());
    }
}
