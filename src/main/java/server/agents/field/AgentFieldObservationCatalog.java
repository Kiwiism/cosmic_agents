package server.agents.field;

import java.util.List;
import java.util.Set;

/** Composed Victoria field-observation policy: authored eligibility plus generated capacity. */
public record AgentFieldObservationCatalog(
        int schemaVersion,
        String harnessId,
        long rotationWindowMs,
        long supplyDurationMs,
        List<MapPreset> maps) {

    public AgentFieldObservationCatalog {
        if (schemaVersion != 2 || harnessId == null || harnessId.isBlank()
                || rotationWindowMs < 1L || supplyDurationMs < rotationWindowMs
                || maps == null || maps.isEmpty()) {
            throw new IllegalArgumentException("valid field-observation catalog fields are required");
        }
        maps = List.copyOf(maps);
    }

    public record MapPreset(
            int mapId,
            String mapName,
            String group,
            int level,
            int recommendedMinimum,
            int recommendedMaximum,
            int maximumAgents,
            List<Integer> activeCounts,
            List<Integer> partySizes,
            String capacitySource,
            String capacityConfidence,
            Set<Integer> allowedMobIds,
            Set<Integer> excludedMobIds) {

        public MapPreset {
            group = group == null || group.isBlank() ? "recommended" : group.trim().toLowerCase();
            if (mapId <= 0 || mapName == null || mapName.isBlank() || level < 1 || level > 25
                    || recommendedMinimum < 1 || recommendedMinimum > recommendedMaximum
                    || recommendedMaximum > maximumAgents || activeCounts == null
                    || activeCounts.isEmpty() || partySizes == null || partySizes.isEmpty()
                    || capacitySource == null || capacitySource.isBlank()
                    || capacityConfidence == null || capacityConfidence.isBlank()
                    || allowedMobIds == null || allowedMobIds.isEmpty()
                    || (!"recommended".equals(group) && !"exploratory".equals(group))) {
                throw new IllegalArgumentException("valid field-observation map fields are required");
            }
            activeCounts = List.copyOf(activeCounts);
            partySizes = List.copyOf(partySizes);
            allowedMobIds = Set.copyOf(allowedMobIds);
            excludedMobIds = excludedMobIds == null ? Set.of() : Set.copyOf(excludedMobIds);
            if (activeCounts.stream().anyMatch(count -> count == null || count < 1 || count > maximumAgents)
                    || partySizes.stream().anyMatch(size -> size == null || size < 1 || size > 6)
                    || partySizes.stream().mapToInt(Integer::intValue).sum() != maximumAgents
                    || allowedMobIds.stream().anyMatch(id -> id == null || id <= 0)
                    || excludedMobIds.stream().anyMatch(id -> id == null || id <= 0)
                    || !java.util.Collections.disjoint(allowedMobIds, excludedMobIds)) {
                throw new IllegalArgumentException("invalid field-observation capacity or mob policy");
            }
        }
    }
}
