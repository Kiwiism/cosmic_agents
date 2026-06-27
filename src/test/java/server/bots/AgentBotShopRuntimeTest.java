package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotShopReplyRuntime;
import server.agents.integration.AgentBotShopRuntime;
import server.agents.integration.AgentBotShopSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotShopRuntimeTest {
    @Test
    void shopBridgeMethodsDelegateToShopAdapters() {
        BotEntry entry = new BotEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotShopReplyRuntime> replies = mockStatic(AgentBotShopReplyRuntime.class);
             MockedStatic<AgentBotShopSchedulerRuntime> scheduler =
                     mockStatic(AgentBotShopSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotShopSchedulerRuntime.randomDelayMs(2000, 4001)).thenReturn(2500L);

            AgentBotShopRuntime.replyNow(entry, "reply");
            AgentBotShopRuntime.sayMapNow(null, "shop");
            AgentBotShopRuntime.afterDelay(500L, action);
            long delay = AgentBotShopRuntime.randomDelayMs(2000, 4001);

            replies.verify(() -> AgentBotShopReplyRuntime.replyNow(entry, "reply"));
            replies.verify(() -> AgentBotShopReplyRuntime.sayMapNow(null, "shop"));
            scheduler.verify(() -> AgentBotShopSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentBotShopSchedulerRuntime.randomDelayMs(2000, 4001));
            assertEquals(2500L, delay);
        }
    }

    @Test
    void shopReplyAdapterDelegatesToBroadReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotShopReplyRuntime.replyNow(entry, "reply");
            AgentBotShopReplyRuntime.sayMapNow(null, "shop");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
            replies.verify(() -> AgentBotReplyRuntime.sayMapNow(null, "shop"));
        }
    }

    @Test
    void shopSchedulerAdapterDelegatesToBroadSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.randomDelayMs(2000, 4001)).thenReturn(2500L);

            AgentBotShopSchedulerRuntime.afterDelay(500L, action);
            long delay = AgentBotShopSchedulerRuntime.randomDelayMs(2000, 4001);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.randomDelayMs(2000, 4001));
            assertEquals(2500L, delay);
        }
    }
}
