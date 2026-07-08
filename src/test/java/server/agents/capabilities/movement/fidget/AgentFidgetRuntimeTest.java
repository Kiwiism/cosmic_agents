package server.agents.capabilities.movement.fidget;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.integration.AgentChatStatusRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class AgentFidgetRuntimeTest {
    @Test
    void fidgetIdleCheckDelegatesToAgentStatusRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentChatStatusRuntime> status = mockStatic(AgentChatStatusRuntime.class)) {
            status.when(() -> AgentChatStatusRuntime.isOwnerIdle(entry)).thenReturn(true);

            assertTrue(AgentFidgetRuntime.isLeaderIdleForFidget(entry));
            status.verify(() -> AgentChatStatusRuntime.isOwnerIdle(entry));
        }
    }

    @Test
    void adaptsActiveFidgetModeState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentFidgetRuntime.hasActiveFidgetMode(entry));

        entry.fidgetState().setMode(AgentFidgetMode.PRONE);

        assertTrue(AgentFidgetRuntime.hasActiveFidgetMode(entry));
    }
}
