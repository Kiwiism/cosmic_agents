package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationCoverageAnalyzerTest {
    @Test
    void reportsDirectedCoverageAndEdgeEvidence() {
        AgentNavigationGraph graph = graph(Map.of(
                1, List.of(edge(1, 2)),
                2, List.of(edge(2, 1), edge(2, 3)),
                3, List.of(edge(3, 2))));

        AgentNavigationCoverageReport report =
                AgentNavigationCoverageAnalyzer.analyze(graph, Set.of(1, 3));

        assertTrue(report.covered());
        assertEquals(3, report.regionCount());
        assertEquals(4, report.edgeCount());
        assertEquals(1, report.weakComponentCount());
        assertEquals(4, report.edgesByType().get(AgentNavigationGraph.EdgeType.WALK));
    }

    @Test
    void identifiesMissingAndOneWayRequiredRoutes() {
        AgentNavigationGraph graph = graph(Map.of(
                1, List.of(edge(1, 2)),
                2, List.of()));

        AgentNavigationCoverageReport report =
                AgentNavigationCoverageAnalyzer.analyze(graph, Set.of(1, 2, 99));

        assertFalse(report.covered());
        assertEquals(Set.of(99), report.missingRegionIds());
        assertEquals(List.of(new AgentNavigationCoverageReport.UnreachableRoute(2, 1)),
                report.unreachableRoutes());
    }

    private static AgentNavigationGraph graph(
            Map<Integer, List<AgentNavigationGraph.Edge>> outgoing) {
        List<AgentNavigationGraph.Region> regions = List.of(
                new AgentNavigationGraph.Region(1, 10, 0, 100, false),
                new AgentNavigationGraph.Region(2, 20, 0, 100, false),
                new AgentNavigationGraph.Region(3, 30, 0, 100, false));
        return new AgentNavigationGraph(
                100000000,
                1,
                regions,
                Map.of(1, regions.get(0), 2, regions.get(1), 3, regions.get(2)),
                Map.of(),
                outgoing,
                Set.of());
    }

    private static AgentNavigationGraph.Edge edge(int from, int to) {
        return new AgentNavigationGraph.Edge(
                from,
                to,
                AgentNavigationGraph.EdgeType.WALK,
                new Point(from * 10, 0),
                new Point(to * 10, 0),
                0,
                -1,
                0,
                0,
                0,
                1);
    }
}
