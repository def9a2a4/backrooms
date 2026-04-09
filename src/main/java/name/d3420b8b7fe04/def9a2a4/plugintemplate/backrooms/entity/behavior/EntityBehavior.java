package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.behavior;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity.EntityHandle;
import org.bukkit.entity.Player;

public interface EntityBehavior {

    void tick(EntityHandle handle, Player nearestPlayer);

    default void onSpawn(EntityHandle handle) {}

    default void onDespawn(EntityHandle handle) {}
}
