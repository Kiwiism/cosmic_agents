package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotLlmRuntime;
import server.agents.integration.AgentBotReplyRuntime;

import static org.mockito.Mockito.mockStatic;

class AgentBotLlmRuntimeTest {
    @Test
    void llmReplyDelegatesToAgentReplyRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotLlmRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
        }
    }
}
