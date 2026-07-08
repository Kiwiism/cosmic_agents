package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotShopRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotShopRuntimeTest {
    @Test
    void shopBridgeMethodsDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.randomDelayMs(2000, 4001)).thenReturn(2500L);

            AgentBotShopRuntime.replyNow(entry, "reply");
            AgentBotShopRuntime.sayMapNow(null, "shop");
            AgentBotShopRuntime.afterDelay(500L, action);
            long delay = AgentBotShopRuntime.randomDelayMs(2000, 4001);

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
            replies.verify(() -> AgentBotReplyRuntime.sayMapNow(null, "shop"));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.randomDelayMs(2000, 4001));
            assertEquals(2500L, delay);
        }
    }
}
