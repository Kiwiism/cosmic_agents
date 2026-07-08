package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.integration.AgentShopRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentShopRuntimeTest {
    @Test
    void shopBridgeMethodsDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.randomDelayMs(2000, 4001)).thenReturn(2500L);

            AgentShopRuntime.replyNow(entry, "reply");
            AgentShopRuntime.sayMapNow(null, "shop");
            AgentShopRuntime.afterDelay(500L, action);
            long delay = AgentShopRuntime.randomDelayMs(2000, 4001);

            replies.verify(() -> AgentReplyRuntime.replyNow(entry, "reply"));
            replies.verify(() -> AgentReplyRuntime.sayMapNow(null, "shop"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentSchedulerRuntime.randomDelayMs(2000, 4001));
            assertEquals(2500L, delay);
        }
    }
}
