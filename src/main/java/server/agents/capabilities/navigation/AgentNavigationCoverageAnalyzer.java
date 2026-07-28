package server.agents.capabilities.navigation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure graph analysis used by catalog validation and route-readiness
 * diagnostics. It neither warms graphs nor mutates live Agent state.
 */
public final class AgentNavigationCoverageAnalyzer {
    private AgentNavigationCoverageAnalyzer() {
    }

    public static AgentNavigationCoverageReport analyze(
            AgentNavigationGraph graph,
            Set<Integer> requiredRegionIds) {
        if (graph == null || requiredRegionIds == null) {
            throw new IllegalArgumentException("Graph and required region IDs are required");
        }

        Set<Integer> required = new LinkedHashSet<>(requiredRegionIds);
        Set<Integer> missing = new LinkedHashSet<>(required);
        missing.removeAll(graph.regionsById.keySet());

        EnumMap<AgentNavigationGraph.EdgeType, Integer> edgesByType =
                new EnumMap<>(AgentNavigationGraph.EdgeType.class);
        Map<Integer, Set<Integer>> directed = new HashMap<>();
        Set<Integer> weakComponents = new HashSet<>();
        int edgeCount = 0;
        for (AgentNavigationGraph.Region region : graph.regions) {
            directed.put(region.id, new LinkedHashSet<>());
            weakComponents.add(graph.connectedComponentId(region.id));
            for (AgentNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                edgeCount++;
                edgesByType.merge(edge.type, 1, Integer::sum);
                directed.get(region.id).add(edge.toRegionId);
            }
        }

        List<AgentNavigationCoverageReport.UnreachableRoute> unreachable = new ArrayList<>();
        for (Integer source : required) {
            if (missing.contains(source)) {
                continue;
            }
            Set<Integer> reachable = reachableFrom(source, directed);
            for (Integer target : required) {
                if (!source.equals(target)
                        && !missing.contains(target)
                        && !reachable.contains(target)) {
                    unreachable.add(new AgentNavigationCoverageReport.UnreachableRoute(source, target));
                }
            }
        }

        return new AgentNavigationCoverageReport(
                graph.mapId,
                graph.version,
                graph.regions.size(),
                edgeCount,
                weakComponents.size(),
                edgesByType,
                required,
                missing,
                unreachable);
    }

    private static Set<Integer> reachableFrom(
            int source,
            Map<Integer, Set<Integer>> directed) {
        Set<Integer> visited = new HashSet<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        visited.add(source);
        pending.add(source);
        while (!pending.isEmpty()) {
            int current = pending.removeFirst();
            for (Integer next : directed.getOrDefault(current, Set.of())) {
                if (visited.add(next)) {
                    pending.addLast(next);
                }
            }
        }
        return visited;
    }
}
