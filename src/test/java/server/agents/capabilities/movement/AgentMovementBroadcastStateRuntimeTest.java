package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMovementBroadcastStateRuntimeTest {
    @Test
    void recordsMatchesAndInvalidatesMovementBroadcastSnapshot() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 3, 4));

        AgentMovementBroadcastStateRuntime.record(entry, 10, 20, 1, 2, 3, 4);

        assertTrue(AgentMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 3, 4));
        assertFalse(AgentMovementBroadcastStateRuntime.matches(entry, 11, 20, 1, 2, 3, 4));
        assertFalse(AgentMovementBroadcastStateRuntime.matches(entry, 10, 20, 9, 2, 3, 4));
        assertFalse(AgentMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 9, 4));
        assertFalse(AgentMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 3, 9));

        AgentMovementBroadcastStateRuntime.invalidate(entry);

        assertFalse(AgentMovementBroadcastStateRuntime.matches(entry, 10, 20, 1, 2, 3, 4));
    }
}
