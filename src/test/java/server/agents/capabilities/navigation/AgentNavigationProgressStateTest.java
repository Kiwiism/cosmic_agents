package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationProgressStateTest {
    @Test
    void observesAlternatingRegionCycleWithoutSuppressingAnEdge() {
        AgentNavigationProgressState state = new AgentNavigationProgressState();
        Point target = new Point(-1012, 298);

        state.observe(101000000, target, 86, 0L);
        state.observe(101000000, target, 83, 1_000L);
        state.observe(101000000, target, 86, 2_000L);

        AgentNavigationProgressState.Snapshot snapshot = state.snapshot(2_000L);
        assertEquals("A/B oscillation", snapshot.loopKind());
        assertEquals(2, snapshot.transitions().size());
        assertNull(snapshot.suppressedEdge());
        assertEquals(0L, snapshot.suppressedUntilMs());
    }

    @Test
    void targetChangeStartsFreshTransitionHistory() {
        AgentNavigationProgressState state = new AgentNavigationProgressState();
        state.observe(1, new Point(0, 0), 1, 0L);
        state.observe(1, new Point(0, 0), 2, 1_000L);
        state.observe(1, new Point(500, 0), 3, 2_000L);

        AgentNavigationProgressState.Snapshot snapshot = state.snapshot(2_000L);
        assertTrue(snapshot.transitions().isEmpty());
        assertEquals("", snapshot.loopKind());
        assertEquals(3, snapshot.currentRegionId());
    }

    @Test
    void observesThreeRegionCycle() {
        AgentNavigationProgressState state = new AgentNavigationProgressState();
        Point target = new Point(500, 100);
        state.observe(1, target, 1, 0L);
        state.observe(1, target, 2, 1_000L);
        state.observe(1, target, 3, 2_000L);
        state.observe(1, target, 1, 3_000L);

        assertEquals("3-region cycle", state.snapshot(3_000L).loopKind());
    }

    @Test
    void repeatedAlternatingCycleTemporarilySuppressesTheInverseEdge() {
        AgentNavigationProgressState state = new AgentNavigationProgressState();
        Point target = new Point(0, -4_000);
        state.observe(101020000, target, 51, 0L);
        state.observe(101020000, target, 52, 1_000L);
        state.observe(101020000, target, 51, 2_000L);
        state.observe(101020000, target, 52, 3_000L);
        state.observe(101020000, target, 51, 4_000L);

        AgentNavigationGraph.Edge inverse = edge(51, 52);
        assertTrue(state.blocks(inverse, 4_000L));
        assertFalse(state.blocks(edge(51, 55), 4_000L));
        assertTrue(state.snapshot(4_000L).suppressedUntilMs() > 4_000L);
        assertFalse(state.blocks(inverse, Long.MAX_VALUE));
    }

    private static AgentNavigationGraph.Edge edge(int from, int to) {
        return new AgentNavigationGraph.Edge(
                from, to, AgentNavigationGraph.EdgeType.WALK,
                new Point(), new Point(), 0, 0, 0, 0, 0, 1);
    }
}
