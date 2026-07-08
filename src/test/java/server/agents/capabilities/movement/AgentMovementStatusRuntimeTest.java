package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatStatusOrchestrator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentMovementStatusRuntimeTest {
    @Test
    void movementStatusCallsDelegateToChatStatusRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character bot = mock(Character.class);

        try (MockedStatic<AgentChatStatusOrchestrator> status = mockStatic(AgentChatStatusOrchestrator.class)) {
            AgentMovementStatusRuntime.prepareMovementActiveMode(entry);
            AgentMovementStatusRuntime.checkMovementStatus(entry, bot);
            AgentMovementStatusRuntime.randomFidgetExpression();

            status.verify(() -> AgentChatStatusOrchestrator.prepareActiveModeEntry(entry));
            status.verify(() -> AgentChatStatusOrchestrator.checkBotStatus(entry, bot));
            status.verify(AgentChatStatusOrchestrator::randomFidgetExpression);
        }
    }
}
