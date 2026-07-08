package server.agents.capabilities.llm;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentReplyRuntime;

import static org.mockito.Mockito.mockStatic;

class AgentLlmRuntimeTest {
    @Test
    void llmReplyDelegatesToAgentReplyRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            AgentLlmRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentReplyRuntime.replyNow(entry, "reply"));
        }
    }
}
