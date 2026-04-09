package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EntryTriggerRegistry {

    private final Map<String, EntryTrigger> triggers = new HashMap<>();

    public void register(EntryTrigger trigger) {
        triggers.put(trigger.getId(), trigger);
    }

    public EntryTrigger get(String id) {
        return triggers.get(id);
    }

    public Collection<EntryTrigger> getAll() {
        return Collections.unmodifiableCollection(triggers.values());
    }
}
