package server.agents.field;

import java.util.List;
import java.util.Set;

/** Authored map eligibility; generated capacity is deliberately stored separately. */
record AgentFieldObservationManifest(
        int schemaVersion,
        String harnessId,
        long rotationWindowMs,
        long supplyDurationMs,
        List<MapDefinition> maps) {
    AgentFieldObservationManifest {
        if (schemaVersion != 2 || harnessId == null || harnessId.isBlank()
                || rotationWindowMs < 1L || supplyDurationMs < rotationWindowMs
                || maps == null || maps.isEmpty()) {
            throw new IllegalArgumentException("valid field observation manifest is required");
        }
        maps = List.copyOf(maps);
    }

    record MapDefinition(
            int mapId,
            String mapName,
            String group,
            int level,
            Set<Integer> allowedMobIds,
            Set<Integer> excludedMobIds) {
        MapDefinition {
            group = group == null || group.isBlank() ? "recommended" : group.trim().toLowerCase();
            if (mapId <= 0 || mapName == null || mapName.isBlank() || level < 1 || level > 25
                    || allowedMobIds == null || allowedMobIds.isEmpty()
                    || (!"recommended".equals(group) && !"exploratory".equals(group))) {
                throw new IllegalArgumentException("valid observation map definition is required");
            }
            allowedMobIds = Set.copyOf(allowedMobIds);
            excludedMobIds = excludedMobIds == null ? Set.of() : Set.copyOf(excludedMobIds);
        }
    }
}
