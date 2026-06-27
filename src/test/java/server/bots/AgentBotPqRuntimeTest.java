package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotPqReplyRuntime;
import server.agents.integration.AgentBotPqRuntime;
import server.agents.integration.AgentBotReplyRuntime;

import static org.mockito.Mockito.mockStatic;

class AgentBotPqRuntimeTest {
    @Test
    void pqDialogueDelegatesToPqReplyAdapter() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotPqReplyRuntime> replies = mockStatic(AgentBotPqReplyRuntime.class)) {
            AgentBotPqRuntime.queueSay(entry, "Here's your pass!");

            replies.verify(() -> AgentBotPqReplyRuntime.queueSay(entry, "Here's your pass!"));
        }
    }

    @Test
    void pqReplyAdapterDelegatesToBroadReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotPqReplyRuntime.queueSay(entry, "Here's your pass!");

            replies.verify(() -> AgentBotReplyRuntime.queueSay(entry, "Here's your pass!"));
        }
    }
}
