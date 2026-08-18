package server.agents.field;

import java.util.List;

/** Generated, explainable observation capacity profiles derived from map facts. */
public record AgentFieldCapacityCatalog(
        int schemaVersion,
        String modelId,
        String mapFactsRevision,
        Policy policy,
        List<MapCapacity> maps) {

    public AgentFieldCapacityCatalog {
        if (schemaVersion != 1 || modelId == null || modelId.isBlank()
                || mapFactsRevision == null || mapFactsRevision.isBlank()
                || policy == null || maps == null || maps.isEmpty()) {
            throw new IllegalArgumentException("valid field capacity catalog is required");
        }
        maps = List.copyOf(maps);
    }

    public record Policy(
            int agentSpacingPx,
            int spawnEntriesPerAgent,
            int fragmentationPenaltyPercent,
            int minimumActivePercent) {
        public Policy {
            if (agentSpacingPx < 1 || spawnEntriesPerAgent < 1
                    || fragmentationPenaltyPercent < 0 || fragmentationPenaltyPercent >= 100
                    || minimumActivePercent < 1 || minimumActivePercent > 100) {
                throw new IllegalArgumentException("valid capacity policy is required");
            }
        }
    }

    public record MapCapacity(
            int mapId,
            String mapName,
            int recommendedMinimum,
            int recommendedMaximum,
            int maximumAgents,
            List<Integer> activeCounts,
            List<Integer> partySizes,
            String source,
            String confidence,
            int totalSpawnEntries,
            int rawPlatformCapacity,
            int spawnBudget,
            int accessPenalty,
            List<PlatformCapacity> platforms,
            List<String> adjustments) {
        public MapCapacity {
            if (mapId <= 0 || mapName == null || mapName.isBlank()
                    || recommendedMinimum < 1 || recommendedMinimum > recommendedMaximum
                    || recommendedMaximum > maximumAgents || activeCounts == null
                    || activeCounts.isEmpty() || partySizes == null || partySizes.isEmpty()
                    || source == null || source.isBlank() || confidence == null || confidence.isBlank()
                    || totalSpawnEntries < 1 || rawPlatformCapacity < 1 || spawnBudget < 1
                    || accessPenalty < 0 || platforms == null || platforms.isEmpty()
                    || adjustments == null) {
                throw new IllegalArgumentException("valid map capacity evidence is required");
            }
            if (maximumAgents > totalSpawnEntries) {
                throw new IllegalArgumentException("capacity cannot exceed physical spawn entries");
            }
            activeCounts = List.copyOf(activeCounts);
            partySizes = List.copyOf(partySizes);
            platforms = List.copyOf(platforms);
            adjustments = List.copyOf(adjustments);
            if (activeCounts.stream().anyMatch(count -> count == null || count < 1 || count > maximumAgents)
                    || partySizes.stream().anyMatch(size -> size == null || size < 1 || size > 6)
                    || partySizes.stream().mapToInt(Integer::intValue).sum() != maximumAgents) {
                throw new IllegalArgumentException("invalid active count or party partition");
            }
        }
    }

    public record PlatformCapacity(
            String platformId,
            List<Integer> componentIds,
            int spawnEntries,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int populatedWidth,
            int widthCapacity,
            int spawnCapacity,
            int effectiveCapacity,
            boolean sparse,
            List<String> constraints) {
        public PlatformCapacity {
            if (platformId == null || platformId.isBlank() || componentIds == null
                    || componentIds.isEmpty() || spawnEntries < 1 || populatedWidth < 1
                    || widthCapacity < 1 || spawnCapacity < 1 || effectiveCapacity < 1
                    || constraints == null) {
                throw new IllegalArgumentException("valid platform capacity evidence is required");
            }
            componentIds = List.copyOf(componentIds);
            constraints = List.copyOf(constraints);
        }
    }
}
