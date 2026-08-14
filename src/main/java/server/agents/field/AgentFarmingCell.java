package server.agents.field;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable runtime farming unit derived from navigation regions and live spawn evidence. */
public record AgentFarmingCell(
        String cellId,
        int mapId,
        Set<Integer> regionIds,
        Map<Integer, Integer> mobCounts,
        Map<Integer, Integer> expectedMobCounts,
        List<AgentFarmingAnchor> anchors,
        Set<String> adjacentCellIds,
        int capacity,
        boolean deadEnd,
        boolean transitOnly) {

    public AgentFarmingCell {
        if (cellId == null || cellId.isBlank() || mapId < 0 || regionIds == null
                || regionIds.isEmpty() || mobCounts == null || expectedMobCounts == null
                || anchors == null || anchors.isEmpty()
                || adjacentCellIds == null || capacity <= 0) {
            throw new IllegalArgumentException("Valid farming cell geometry, spawns, anchors, and capacity are required");
        }
        regionIds = Set.copyOf(regionIds);
        mobCounts = Map.copyOf(mobCounts);
        expectedMobCounts = Map.copyOf(expectedMobCounts);
        anchors = List.copyOf(anchors);
        adjacentCellIds = Set.copyOf(adjacentCellIds);
    }

    public int relevantPopulation(Set<Integer> requiredMobIds) {
        if (requiredMobIds == null || requiredMobIds.isEmpty()) {
            return mobCounts.values().stream().mapToInt(Integer::intValue).sum();
        }
        return requiredMobIds.stream().mapToInt(mobId -> mobCounts.getOrDefault(mobId, 0)).sum();
    }

    public int objectiveCoverage(Set<Integer> requiredMobIds) {
        if (requiredMobIds == null || requiredMobIds.isEmpty()) {
            return mobCounts.isEmpty() ? 0 : 1;
        }
        Map<Integer, Integer> coverageCounts = expectedMobCounts.isEmpty()
                ? mobCounts : expectedMobCounts;
        return (int) requiredMobIds.stream().filter(coverageCounts::containsKey).count();
    }
}
