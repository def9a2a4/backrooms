package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.List;
import java.util.UUID;

public record EntityHandle(
        String entityId,
        UUID instanceId,
        List<Entity> bukkitEntities,
        UUID targetPlayerUuid,
        Location spawnLocation,
        long spawnTick
) {}
