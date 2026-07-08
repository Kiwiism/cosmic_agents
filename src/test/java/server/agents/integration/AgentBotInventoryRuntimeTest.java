package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotInventoryRuntimeTest {
    @Test
    void inventoryReplyAndSchedulerDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotInventoryRuntime.replyNow(entry, "hello");
            AgentBotInventoryRuntime.visibleSayNow(entry, "visible");
            AgentBotInventoryRuntime.afterDelay(123L, action);

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "hello"));
            replies.verify(() -> AgentBotReplyRuntime.visibleSayNow(entry, "visible"));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(123L, action));
        }
    }
}
