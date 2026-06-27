package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotPqRuntime;
import server.agents.integration.AgentBotReplyRuntime;

import static org.mockito.Mockito.mockStatic;

class AgentBotPqRuntimeTest {
    @Test
    void pqDialogueDelegatesToAgentReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotPqRuntime.queueSay(entry, "Here's your pass!");

            replies.verify(() -> AgentBotReplyRuntime.queueSay(entry, "Here's your pass!"));
        }
    }
}
