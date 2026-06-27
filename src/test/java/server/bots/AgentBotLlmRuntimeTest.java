package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotLlmReplyRuntime;
import server.agents.integration.AgentBotLlmRuntime;
import server.agents.integration.AgentBotReplyRuntime;

import static org.mockito.Mockito.mockStatic;

class AgentBotLlmRuntimeTest {
    @Test
    void llmReplyDelegatesToLlmReplyAdapter() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotLlmReplyRuntime> replies = mockStatic(AgentBotLlmReplyRuntime.class)) {
            AgentBotLlmRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentBotLlmReplyRuntime.replyNow(entry, "reply"));
        }
    }

    @Test
    void llmReplyAdapterDelegatesToBroadReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotLlmReplyRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
        }
    }
}
