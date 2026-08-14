package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementProfile;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationRouteOverlayPolicyTest {
    @Test
    void nautilusExitUsesAuthoredUpperRopeRoute() {
        AgentNavigationGraph graph = new AgentNavigationGraph(
                120000000,
                59,
                AgentMovementProfile.base(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of());

        assertTrue(AgentNavigationRouteOverlayPolicy.applies(graph, 191));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 191, edge(216, 254)));
        assertFalse(AgentNavigationRouteOverlayPolicy.allows(graph, 191, edge(216, 250)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 191, edge(254, 212)));
        assertFalse(AgentNavigationRouteOverlayPolicy.allows(graph, 191, edge(254, 200)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 191, edge(212, 200)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 191, edge(200, 193)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 191, edge(193, 253)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 191, edge(253, 252)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 191, edge(252, 191)));
        assertFalse(AgentNavigationRouteOverlayPolicy.allows(graph, 191, edge(200, 179)));
        assertFalse(AgentNavigationRouteOverlayPolicy.allows(graph, 191, edge(191, 1)));
    }

    @Test
    void nautilusOverlayDoesNotAffectOtherDestinationsOrGraphVersions() {
        AgentNavigationGraph currentGraph = graph(59);
        AgentNavigationGraph otherVersion = graph(58);

        assertFalse(AgentNavigationRouteOverlayPolicy.applies(currentGraph, 200));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(currentGraph, 200, edge(213, 214)));
        assertFalse(AgentNavigationRouteOverlayPolicy.applies(otherVersion, 191));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(otherVersion, 191, edge(213, 214)));
    }

    @Test
    void forestEastUpperLeftHabitatAvoidsIrrelevantDropFrontiers() {
        AgentNavigationGraph graph = new AgentNavigationGraph(
                100030000,
                59,
                AgentMovementProfile.base(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of());

        assertTrue(AgentNavigationRouteOverlayPolicy.applies(graph, 3));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 3, edge(62, 79)));
        assertFalse(AgentNavigationRouteOverlayPolicy.allows(graph, 3, edge(62, 17)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 3, edge(4, 3)));
    }

    @Test
    void forestSouthExitUsesCentralLadderInsteadOfUnreliableJump() {
        AgentNavigationGraph graph = new AgentNavigationGraph(
                100040000,
                59,
                AgentMovementProfile.base(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of());

        assertTrue(AgentNavigationRouteOverlayPolicy.applies(graph, 1));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(44, 45)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(41, 51)));
        assertFalse(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(41, 46)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(13, 12)));
        assertFalse(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(13, 11)));
        assertFalse(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(13, 50)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(12, 10)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(10, 9)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(9, 5)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(5, 4)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(4, 2)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(2, 3)));
        assertTrue(AgentNavigationRouteOverlayPolicy.allows(graph, 1, edge(3, 1)));
    }

    private static AgentNavigationGraph graph(int version) {
        return new AgentNavigationGraph(
                120000000,
                version,
                AgentMovementProfile.base(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Set.of());
    }

    private static AgentNavigationGraph.Edge edge(int fromRegionId, int toRegionId) {
        return new AgentNavigationGraph.Edge(
                fromRegionId,
                toRegionId,
                AgentNavigationGraph.EdgeType.WALK,
                new Point(),
                new Point(),
                0,
                0,
                0,
                0,
                0,
                1);
    }
}
