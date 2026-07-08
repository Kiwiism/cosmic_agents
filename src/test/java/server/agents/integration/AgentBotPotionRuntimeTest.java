package server.agents.integration;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentBotPotionRuntime;
import server.agents.integration.AgentSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotPotionRuntimeTest {
    @Test
    void potionBridgeMethodsDelegateToBroadAgentRuntimes() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.randomDelayMs(900, 1400)).thenReturn(999L);

            AgentBotPotionRuntime.sayMapNow(null, "pots");
            AgentBotPotionRuntime.afterDelay(500L, action);
            AgentBotPotionRuntime.afterRandomDelay(900, 1100, action);
            long delay = AgentBotPotionRuntime.randomDelayMs(900, 1400);

            replies.verify(() -> AgentReplyRuntime.sayMapNow(null, "pots"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(900, 1100, action));
            scheduler.verify(() -> AgentSchedulerRuntime.randomDelayMs(900, 1400));
            assertEquals(999L, delay);
        }
    }
}
