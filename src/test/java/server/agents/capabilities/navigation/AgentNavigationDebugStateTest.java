package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.monitoring.AgentPathLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationDebugStateTest {
    @Test
    void defaultsPreserveLegacyBotEntryValues() {
        AgentNavigationDebugState state = new AgentNavigationDebugState();

        assertNull(state.pathLogger());
        assertEquals("-", state.lastDecision());
        assertNull(state.lastEdgeBlockReason());
        assertFalse(state.graphWarmupFallback());
    }

    @Test
    void storesPathLoggerAndClearsIt() {
        AgentNavigationDebugState state = new AgentNavigationDebugState();
        AgentPathLogger logger = new AgentPathLogger("agent123", 100000000);

        state.setPathLogger(logger);

        assertSame(logger, state.pathLogger());

        state.clearPathLogger();

        assertNull(state.pathLogger());
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
