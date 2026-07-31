package server.agents.capabilities.inventory;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentDialogueTransportRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentInventoryRuntimeTest {
    @Test
    void inventoryReplyAndSchedulerDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentDialogueTransportRuntime> replies = mockStatic(AgentDialogueTransportRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            AgentInventoryRuntime.replyNow(entry, "hello");
            AgentInventoryRuntime.visibleSayNow(entry, "visible");
            AgentInventoryRuntime.afterDelay(entry, 123L, action);

            replies.verify(() -> AgentDialogueTransportRuntime.replyNow(entry, "hello"));
            replies.verify(() -> AgentDialogueTransportRuntime.visibleSayNow(entry, "visible"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(entry, 123L, action));
        }
    }
}
