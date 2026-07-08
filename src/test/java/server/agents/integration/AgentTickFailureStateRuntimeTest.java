package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentTickFailureStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTickFailureStateRuntimeTest {
    @Test
    void recordsFailuresInsideWindowAndResetsAfterWindow() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(1, AgentTickFailureStateRuntime.recordFailure(entry, 1_000L, 500L));
        assertEquals(1_000L, AgentTickFailureStateRuntime.windowStartedAtMs(entry));
        assertEquals(1, AgentTickFailureStateRuntime.failureCount(entry));

        assertEquals(2, AgentTickFailureStateRuntime.recordFailure(entry, 1_500L, 500L));
        assertEquals(1_000L, AgentTickFailureStateRuntime.windowStartedAtMs(entry));
        assertEquals(2, AgentTickFailureStateRuntime.failureCount(entry));

        assertEquals(1, AgentTickFailureStateRuntime.recordFailure(entry, 1_501L, 500L));
        assertEquals(1_501L, AgentTickFailureStateRuntime.windowStartedAtMs(entry));
        assertEquals(1, AgentTickFailureStateRuntime.failureCount(entry));
    }

    @Test
    void clearsFailures() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentTickFailureStateRuntime.recordFailure(entry, 1_000L, 500L);

        assertTrue(AgentTickFailureStateRuntime.hasFailures(entry));

        AgentTickFailureStateRuntime.clear(entry);

        assertFalse(AgentTickFailureStateRuntime.hasFailures(entry));
        assertEquals(0, AgentTickFailureStateRuntime.failureCount(entry));
        assertEquals(0L, AgentTickFailureStateRuntime.windowStartedAtMs(entry));
    }
}
