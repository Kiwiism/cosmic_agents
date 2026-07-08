package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentModeStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentModeStateRuntimeTest {
    @Test
    void adaptsModeFlagsAndFollowTarget() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentModeStateRuntime.following(entry));
        assertFalse(AgentModeStateRuntime.grinding(entry));
        assertEquals(0, AgentModeStateRuntime.followTargetId(entry));

        AgentModeStateRuntime.startFollowing(entry, 123);

        assertTrue(AgentModeStateRuntime.following(entry));
        assertFalse(AgentModeStateRuntime.grinding(entry));
        assertEquals(123, AgentModeStateRuntime.followTargetId(entry));

        AgentModeStateRuntime.startGrinding(entry);

        assertFalse(AgentModeStateRuntime.following(entry));
        assertTrue(AgentModeStateRuntime.grinding(entry));
        assertEquals(0, AgentModeStateRuntime.followTargetId(entry));

        AgentModeStateRuntime.stopMovementModes(entry);

        assertFalse(AgentModeStateRuntime.following(entry));
        assertFalse(AgentModeStateRuntime.grinding(entry));
        assertEquals(0, AgentModeStateRuntime.followTargetId(entry));
    }
}
