package server.agents.capabilities.dialogue.llm;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.llm.AgentLlmRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentLlmReplyCoordinatorTest {
    @Test
    void composesRequestAndDelegatesToLlmService() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        Character sender = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);

        try (MockedStatic<AgentLlmReplyService> service = mockStatic(AgentLlmReplyService.class);
             MockedStatic<AgentLlmRuntime> replies = mockStatic(AgentLlmRuntime.class)) {
            AgentLlmReplyCoordinator.maybeRespond(entry, sender, "hello");

            service.verify(() -> AgentLlmReplyService.maybeRespond(
                    any(AgentLlmReplyRequest.class),
                    eq(sender),
                    eq("hello"),
                    any(AgentLlmReplyService.ReplyEmitter.class)));
        }
    }

    @Test
    void ignoresMissingRuntimeInputs() {
        try (MockedStatic<AgentLlmReplyService> service = mockStatic(AgentLlmReplyService.class)) {
            AgentLlmReplyCoordinator.maybeRespond(null, mock(Character.class), "hello");
            AgentLlmReplyCoordinator.maybeRespond(new AgentRuntimeEntry(null, null, null), null, "hello");

            service.verifyNoInteractions();
        }
    }
}
