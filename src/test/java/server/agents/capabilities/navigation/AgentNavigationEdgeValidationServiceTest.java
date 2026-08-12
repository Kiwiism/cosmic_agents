package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.Rope;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentNavigationEdgeValidationServiceTest {
    @Test
    void rejectsUnexpectedSourceRegion() {
        AgentNavigationGraph graph = graph();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentNavigationEdgeValidationService.Result result =
                AgentNavigationEdgeValidationService.validate(
                        graph, entry, 100, 2, new Point(0, 100), jump(), 1_000);

        assertTrue(result.rejected());
        assertEquals("unexpected-source-region", result.reason());
    }

    @Test
    void rejectsGroundEdgeWhileClimbing() {
        AgentNavigationGraph graph = graph();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, mock(Rope.class));

        AgentNavigationEdgeValidationService.Result result =
                AgentNavigationEdgeValidationService.validate(
                        graph, entry, 100, 1, new Point(0, 100), jump(), 1_000);

        assertTrue(result.rejected());
        assertEquals("ground-edge-while-climbing", result.reason());
    }

    @Test
    void rejectsClimbExitWhenAgentIsNotClimbing() {
        AgentNavigationGraph graph = graphWithRope();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge exit = new AgentNavigationGraph.Edge(
                3, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(50, 50), new Point(100, 100),
                1, -1, 50, 50, 150, 100);

        AgentNavigationEdgeValidationService.Result result =
                AgentNavigationEdgeValidationService.validate(
                        graph, entry, 100, 3, new Point(50, 50), exit, 1_000);

        assertTrue(result.rejected());
        assertEquals("climb-exit-not-climbing", result.reason());
    }

    @Test
    void validFixedJumpRemainsExecutableAndOffGraphLandingIsRejected() {
        AgentNavigationGraph graph = graph();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationEdgeValidationService.Result valid =
                AgentNavigationEdgeValidationService.validate(
                        graph, entry, 100, 1, new Point(0, 100), jump(), 1_000);
        AgentNavigationGraph.Edge invalidLanding = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 100), new Point(100, 400),
                -10, 10, 0, -1, 0, 0, 0, 100);
        AgentNavigationEdgeValidationService.Result invalid =
                AgentNavigationEdgeValidationService.validate(
                        graph, entry, 100, 1, new Point(0, 100), invalidLanding, 1_000);

        assertFalse(valid.rejected());
        assertTrue(valid.ready());
        assertTrue(invalid.rejected());
        assertEquals("unreachable-edge-anchor", invalid.reason());
    }

    private static AgentNavigationGraph graph() {
        return graph(List.of(ground(1, -50, 50, 100), ground(2, 50, 150, 100)));
    }

    private static AgentNavigationGraph graphWithRope() {
        return graph(List.of(ground(1, -50, 50, 100),
                ground(2, 50, 150, 100), new AgentNavigationGraph.Region(3, 50, 50, 150, false)));
    }

    private static AgentNavigationGraph graph(List<AgentNavigationGraph.Region> regions) {
        Map<Integer, AgentNavigationGraph.Region> byId = new java.util.HashMap<>();
        for (AgentNavigationGraph.Region region : regions) {
            byId.put(region.id, region);
        }
        return new AgentNavigationGraph(100, 1, regions, byId, Map.of(), Map.of(), Set.of());
    }

    private static AgentNavigationGraph.Region ground(int id, int x1, int x2, int y) {
        return new AgentNavigationGraph.Region(id, List.of(new AgentNavigationGraph.Segment(
                new Foothold(new Point(x1, y), new Point(x2, y), id))));
    }

    private static AgentNavigationGraph.Edge jump() {
        return new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 100), new Point(100, 100),
                -10, 10, 0, -1, 0, 0, 0, 100);
    }
}
