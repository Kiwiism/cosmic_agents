package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationDebugStateTest {
    @Test
    void defaultsPreserveLegacyAgentRuntimeEntryValues() {
        AgentNavigationDebugState state = new AgentNavigationDebugState();

        assertEquals("-", state.lastDecision());
        assertNull(state.lastEdgeBlockReason());
        assertFalse(state.graphWarmupFallback());
    }

    @Test
    void storesDecisionBlockReasonAndFallbackFlag() {
        AgentNavigationDebugState state = new AgentNavigationDebugState();

        state.setLastDecision("graph-warmup");
        state.setLastEdgeBlockReason("climb-pos");
        state.setGraphWarmupFallback(true);

        assertEquals("graph-warmup", state.lastDecision());
        assertEquals("climb-pos", state.lastEdgeBlockReason());
        assertTrue(state.graphWarmupFallback());
    }
}
