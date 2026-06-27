package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotChatStatusRuntime;
import server.agents.integration.AgentBotFidgetRuntime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class AgentBotFidgetRuntimeTest {
    @Test
    void fidgetIdleCheckDelegatesToAgentStatusRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotChatStatusRuntime> status = mockStatic(AgentBotChatStatusRuntime.class)) {
            status.when(() -> AgentBotChatStatusRuntime.isOwnerIdle(entry)).thenReturn(true);

            assertTrue(AgentBotFidgetRuntime.isLeaderIdleForFidget(entry));
            status.verify(() -> AgentBotChatStatusRuntime.isOwnerIdle(entry));
        }
    }
}
