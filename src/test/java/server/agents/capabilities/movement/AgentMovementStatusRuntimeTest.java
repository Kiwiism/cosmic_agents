package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentChatStatusRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentMovementStatusRuntimeTest {
    @Test
    void movementStatusCallsDelegateToChatStatusRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character bot = mock(Character.class);

        try (MockedStatic<AgentChatStatusRuntime> status = mockStatic(AgentChatStatusRuntime.class)) {
            AgentMovementStatusRuntime.prepareMovementActiveMode(entry);
            AgentMovementStatusRuntime.checkMovementStatus(entry, bot);
            AgentMovementStatusRuntime.randomFidgetExpression();

            status.verify(() -> AgentChatStatusRuntime.prepareActiveModeEntry(entry));
            status.verify(() -> AgentChatStatusRuntime.checkBotStatus(entry, bot));
            status.verify(AgentChatStatusRuntime::randomFidgetExpression);
        }
    }
}
