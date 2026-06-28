package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotTickFailureStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotTickFailureStateRuntimeTest {
    @Test
    void recordsFailuresInsideWindowAndResetsAfterWindow() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(1, AgentBotTickFailureStateRuntime.recordFailure(entry, 1_000L, 500L));
        assertEquals(1_000L, AgentBotTickFailureStateRuntime.windowStartedAtMs(entry));
        assertEquals(1, AgentBotTickFailureStateRuntime.failureCount(entry));

        assertEquals(2, AgentBotTickFailureStateRuntime.recordFailure(entry, 1_500L, 500L));
        assertEquals(1_000L, AgentBotTickFailureStateRuntime.windowStartedAtMs(entry));
        assertEquals(2, AgentBotTickFailureStateRuntime.failureCount(entry));

        assertEquals(1, AgentBotTickFailureStateRuntime.recordFailure(entry, 1_501L, 500L));
        assertEquals(1_501L, AgentBotTickFailureStateRuntime.windowStartedAtMs(entry));
        assertEquals(1, AgentBotTickFailureStateRuntime.failureCount(entry));
    }

    @Test
    void clearsFailures() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotTickFailureStateRuntime.recordFailure(entry, 1_000L, 500L);

        assertTrue(AgentBotTickFailureStateRuntime.hasFailures(entry));

        AgentBotTickFailureStateRuntime.clear(entry);

        assertFalse(AgentBotTickFailureStateRuntime.hasFailures(entry));
        assertEquals(0, AgentBotTickFailureStateRuntime.failureCount(entry));
        assertEquals(0L, AgentBotTickFailureStateRuntime.windowStartedAtMs(entry));
    }
}
