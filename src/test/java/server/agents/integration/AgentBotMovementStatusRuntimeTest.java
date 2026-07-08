package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotChatStatusRuntime;
import server.agents.integration.AgentBotMovementStatusRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotMovementStatusRuntimeTest {
    @Test
    void movementStatusCallsDelegateToChatStatusRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character bot = mock(Character.class);

        try (MockedStatic<AgentBotChatStatusRuntime> status = mockStatic(AgentBotChatStatusRuntime.class)) {
            AgentBotMovementStatusRuntime.prepareMovementActiveMode(entry);
            AgentBotMovementStatusRuntime.checkMovementStatus(entry, bot);
            AgentBotMovementStatusRuntime.randomFidgetExpression();

            status.verify(() -> AgentBotChatStatusRuntime.prepareActiveModeEntry(entry));
            status.verify(() -> AgentBotChatStatusRuntime.checkBotStatus(entry, bot));
            status.verify(AgentBotChatStatusRuntime::randomFidgetExpression);
        }
    }
}
