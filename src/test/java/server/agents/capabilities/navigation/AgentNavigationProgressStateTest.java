package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
