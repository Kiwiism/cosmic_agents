package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentInventoryRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentInventoryRuntimeTest {
    @Test
    void inventoryReplyAndSchedulerDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            AgentInventoryRuntime.replyNow(entry, "hello");
            AgentInventoryRuntime.visibleSayNow(entry, "visible");
            AgentInventoryRuntime.afterDelay(123L, action);

            replies.verify(() -> AgentReplyRuntime.replyNow(entry, "hello"));
            replies.verify(() -> AgentReplyRuntime.visibleSayNow(entry, "visible"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(123L, action));
        }
    }
}
