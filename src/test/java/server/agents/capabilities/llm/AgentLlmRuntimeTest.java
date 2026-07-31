package server.agents.capabilities.llm;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentDialogueTransportRuntime;

import static org.mockito.Mockito.mockStatic;

class AgentLlmRuntimeTest {
    @Test
    void llmReplyDelegatesToAgentDialogueTransportRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentDialogueTransportRuntime> replies = mockStatic(AgentDialogueTransportRuntime.class)) {
            AgentLlmRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentDialogueTransportRuntime.replyNow(entry, "reply"));
        }
    }
}
