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

    private static AgentNavigationGraph.Edge edge(int from, int to, Point start, Point end) {
        return new AgentNavigationGraph.Edge(
                from, to, AgentNavigationGraph.EdgeType.JUMP,
                start, end, 0, 0, 0, 0, 0, 100);
    }
}
