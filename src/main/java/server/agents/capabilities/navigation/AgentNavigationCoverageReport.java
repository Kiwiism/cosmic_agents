package server.agents.capabilities.navigation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable evidence describing whether the regions required by a plan can
 * reach one another through a generated navigation graph.
 */
public record AgentNavigationCoverageReport(
        int mapId,
        int graphVersion,
        int regionCount,
        int edgeCount,
        int weakComponentCount,
        Map<AgentNavigationGraph.EdgeType, Integer> edgesByType,
        Set<Integer> requiredRegionIds,
        Set<Integer> missingRegionIds,
        List<UnreachableRoute> unreachableRoutes) {

    public record UnreachableRoute(int fromRegionId, int toRegionId) {
    }

    public AgentNavigationCoverageReport {
        if (mapId < 0 || graphVersion < 0 || regionCount < 0 || edgeCount < 0
                || weakComponentCount < 0 || edgesByType == null
                || requiredRegionIds == null || missingRegionIds == null
                || unreachableRoutes == null) {
            throw new IllegalArgumentException("Valid navigation coverage evidence is required");
        }
        edgesByType = Map.copyOf(edgesByType);
        requiredRegionIds = Set.copyOf(requiredRegionIds);
        missingRegionIds = Set.copyOf(missingRegionIds);
        unreachableRoutes = List.copyOf(unreachableRoutes);
    }

    public boolean covered() {
        return missingRegionIds.isEmpty() && unreachableRoutes.isEmpty();
    }
}
