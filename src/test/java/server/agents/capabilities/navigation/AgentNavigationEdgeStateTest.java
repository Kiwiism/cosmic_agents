package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationEdgeStateTest {
    @Test
    void defaultsPreserveLegacyAgentRuntimeEntryValues() {
        AgentNavigationEdgeState state = new AgentNavigationEdgeState();

        assertFalse(state.hasActiveEdge());
        assertNull(state.activeEdge());
        assertFalse(state.hasJumpLaunchEdge());
        assertEquals(Integer.MIN_VALUE, state.jumpLaunchX());
    }

    @Test
    void storesAndClearsActiveEdge() {
        AgentNavigationEdgeState state = new AgentNavigationEdgeState();
        AgentNavigationGraph.Edge edge = edge(AgentNavigationGraph.EdgeType.WALK, 1, 2);

        state.setActiveEdge(edge);

        assertTrue(state.hasActiveEdge());
        assertSame(edge, state.activeEdge());

        state.clearActiveEdge();

        assertFalse(state.hasActiveEdge());
        assertNull(state.activeEdge());
    }

    @Test
    void ignoresNonNavigationEdges() {
        AgentNavigationEdgeState state = new AgentNavigationEdgeState();

        state.setActiveEdge("not-edge");
        state.setJumpLaunch("not-edge", 12);

        assertFalse(state.hasActiveEdge());
        assertNull(state.activeEdge());
        assertFalse(state.hasJumpLaunchEdge());
        assertEquals(12, state.jumpLaunchX());
    }

    @Test
    void matchesEquivalentJumpLaunchEdgesByLegacyIdentityFields() {
        AgentNavigationEdgeState state = new AgentNavigationEdgeState();
        AgentNavigationGraph.Edge edge = edge(AgentNavigationGraph.EdgeType.JUMP, 1, 2);
        AgentNavigationGraph.Edge same = edge(AgentNavigationGraph.EdgeType.JUMP, 1, 2);
        AgentNavigationGraph.Edge different = edge(AgentNavigationGraph.EdgeType.DROP, 1, 2);

        state.setJumpLaunch(edge, 12);

        assertTrue(state.hasJumpLaunchEdge());
        assertEquals(12, state.jumpLaunchX());
        assertTrue(state.matchesJumpLaunchEdge(edge));
        assertTrue(state.matchesJumpLaunchEdge(same));
        assertFalse(state.matchesJumpLaunchEdge(different));

        state.setJumpLaunch(null, Integer.MIN_VALUE);

        assertFalse(state.hasJumpLaunchEdge());
        assertEquals(Integer.MIN_VALUE, state.jumpLaunchX());
    }

    private static AgentNavigationGraph.Edge edge(AgentNavigationGraph.EdgeType type, int from, int to) {
        return new AgentNavigationGraph.Edge(
                from,
                to,
                type,
                new Point(10, 20),
                new Point(30, 40),
                0,
                0,
                5,
                15,
                0,
                100);
    }
}
