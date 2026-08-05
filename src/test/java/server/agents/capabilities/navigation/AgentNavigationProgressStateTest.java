package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationProgressStateTest {
    @Test
    void suppressesThirdEdgeOfAlternatingRegionCycleForSameTarget() {
        AgentNavigationProgressState state = new AgentNavigationProgressState();
        Point target = new Point(-1012, 298);
        AgentNavigationGraph.Edge jump86To83 = edge(86, 83, new Point(253, 292), new Point(297, 223));

        state.observe(101000000, target, 86, 0L);
        state.observe(101000000, target, 83, 1_000L);
        state.observe(101000000, target, 86, 2_000L);

        assertTrue(state.suppressIfAlternatingCycle(jump86To83, 2_000L));
        assertFalse(state.allows(jump86To83, 2_001L));
        assertTrue(state.allows(jump86To83, 20_000L));
    }

    @Test
    void targetChangeStartsFreshTransitionHistory() {
        AgentNavigationProgressState state = new AgentNavigationProgressState();
        AgentNavigationGraph.Edge jump86To83 = edge(86, 83, new Point(253, 292), new Point(297, 223));

        state.observe(101000000, new Point(-1012, 298), 86, 0L);
        state.observe(101000000, new Point(-1012, 298), 83, 1_000L);
        state.observe(101000000, new Point(500, 298), 86, 2_000L);

        assertFalse(state.suppressIfAlternatingCycle(jump86To83, 2_000L));
        assertTrue(state.allows(jump86To83, 2_000L));
    }

    @Test
    void ordinaryForwardProgressDoesNotLookLikeOscillation() {
        AgentNavigationProgressState state = new AgentNavigationProgressState();
        Point target = new Point(500, 100);

        state.observe(1, target, 1, 0L);
        state.observe(1, target, 2, 1_000L);
        state.observe(1, target, 3, 2_000L);

        assertFalse(state.suppressIfAlternatingCycle(
                edge(3, 4, new Point(300, 100), new Point(400, 100)), 2_000L));
    }

    @Test
    void suppressesNextEdgeAfterThreeRegionCycleCloses() {
        AgentNavigationProgressState state = new AgentNavigationProgressState();
        Point target = new Point(500, 100);

        state.observe(1, target, 1, 0L);
        state.observe(1, target, 2, 1_000L);
        state.observe(1, target, 3, 2_000L);
        state.observe(1, target, 1, 3_000L);

        assertTrue(state.suppressIfRepeatedCycle(
                edge(1, 2, new Point(100, 100), new Point(200, 100)), 3_000L));
    }

    @Test
    void suppressesNextEdgeAfterFourRegionCycleCloses() {
        AgentNavigationProgressState state = new AgentNavigationProgressState();
        Point target = new Point(500, 100);

        state.observe(1, target, 1, 0L);
        state.observe(1, target, 2, 1_000L);
        state.observe(1, target, 3, 2_000L);
        state.observe(1, target, 4, 3_000L);
        state.observe(1, target, 1, 4_000L);

        assertTrue(state.suppressIfRepeatedCycle(
                edge(1, 2, new Point(100, 100), new Point(200, 100)), 4_000L));
    }

    @Test
    void structuralTraversalEdgesAreNeverSuppressedByGroundCycleRecovery() {
        AgentNavigationProgressState state = new AgentNavigationProgressState();
        Point target = new Point(500, 100);
        AgentNavigationGraph.Edge climb =
                edge(97, 19, AgentNavigationGraph.EdgeType.CLIMB,
                        new Point(300, 150), new Point(300, 50));
        AgentNavigationGraph.Edge portal =
                edge(7, 78, AgentNavigationGraph.EdgeType.PORTAL,
                        new Point(100, 100), new Point(500, 500));

        state.observe(1, target, 97, 0L);
        state.observe(1, target, 19, 1_000L);
        state.observe(1, target, 97, 2_000L);

        assertFalse(state.suppressIfRepeatedCycle(climb, 2_000L));
        state.suppress(climb, 2_000L);
        state.suppress(portal, 2_000L);
        assertTrue(state.allows(climb, 2_001L));
        assertTrue(state.allows(portal, 2_001L));
    }

    @Test
    void permitsOnePartialRouteReuseThenSuppressesItsFrontierEdge() {
        AgentNavigationProgressState state = new AgentNavigationProgressState();
        AgentNavigationGraph.Edge edge = edge(1, 2, new Point(100, 100), new Point(200, 100));

        assertTrue(state.allowsPartialReuse(edge, 1_000L));
        assertTrue(state.allowsPartialReuse(edge, 2_000L));
        assertFalse(state.allowsPartialReuse(edge, 3_000L));
        assertFalse(state.allows(edge, 3_001L));
    }

    private static AgentNavigationGraph.Edge edge(int from, int to, Point start, Point end) {
        return edge(from, to, AgentNavigationGraph.EdgeType.JUMP, start, end);
    }

    private static AgentNavigationGraph.Edge edge(int from,
                                                  int to,
                                                  AgentNavigationGraph.EdgeType type,
                                                  Point start,
                                                  Point end) {
        return new AgentNavigationGraph.Edge(
                from, to, type,
                start, end, 0, 0, 0, 0, 0, 100);
    }
}
