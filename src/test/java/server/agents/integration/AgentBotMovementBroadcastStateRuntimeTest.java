package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementBroadcastStateRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotMovementBroadcastStateRuntimeTest {
    @Test
    void recordsMatchesAndInvalidatesMovementBroadcastSnapshot() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentBotMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 3, 4));

        AgentBotMovementBroadcastStateRuntime.record(entry, 10, 20, 1, 2, 3, 4);

        assertTrue(AgentBotMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 3, 4));
        assertFalse(AgentBotMovementBroadcastStateRuntime.matches(entry, 11, 20, 1, 2, 3, 4));
        assertFalse(AgentBotMovementBroadcastStateRuntime.matches(entry, 10, 20, 9, 2, 3, 4));
        assertFalse(AgentBotMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 9, 4));
        assertFalse(AgentBotMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 3, 9));

        AgentBotMovementBroadcastStateRuntime.invalidate(entry);

        assertFalse(AgentBotMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 3, 4));
    }
}
