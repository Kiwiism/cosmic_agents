package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotInventoryReplyRuntime;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.integration.AgentBotInventorySchedulerRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotInventoryRuntimeTest {
    @Test
    void inventoryReplyAndSchedulerDelegateToInventoryAdapters() {
        BotEntry entry = new BotEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotInventoryReplyRuntime> replies =
                     mockStatic(AgentBotInventoryReplyRuntime.class);
             MockedStatic<AgentBotInventorySchedulerRuntime> scheduler =
                     mockStatic(AgentBotInventorySchedulerRuntime.class)) {
            AgentBotInventoryRuntime.replyNow(entry, "hello");
            AgentBotInventoryRuntime.visibleSayNow(entry, "visible");
            AgentBotInventoryRuntime.afterDelay(123L, action);

            replies.verify(() -> AgentBotInventoryReplyRuntime.replyNow(entry, "hello"));
            replies.verify(() -> AgentBotInventoryReplyRuntime.visibleSayNow(entry, "visible"));
            scheduler.verify(() -> AgentBotInventorySchedulerRuntime.afterDelay(123L, action));
        }
    }

    @Test
    void inventoryReplyAdapterDelegatesToBroadReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotInventoryReplyRuntime.replyNow(entry, "hello");
            AgentBotInventoryReplyRuntime.visibleSayNow(entry, "visible");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "hello"));
            replies.verify(() -> AgentBotReplyRuntime.visibleSayNow(entry, "visible"));
        }
    }

    @Test
    void inventorySchedulerAdapterDelegatesToBroadSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotInventorySchedulerRuntime.afterDelay(123L, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(123L, action));
        }
    }
}
