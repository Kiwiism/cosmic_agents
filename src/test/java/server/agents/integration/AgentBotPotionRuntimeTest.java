package server.agents.integration;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotPotionRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotPotionRuntimeTest {
    @Test
    void potionBridgeMethodsDelegateToBroadAgentRuntimes() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.randomDelayMs(900, 1400)).thenReturn(999L);

            AgentBotPotionRuntime.sayMapNow(null, "pots");
            AgentBotPotionRuntime.afterDelay(500L, action);
            AgentBotPotionRuntime.afterRandomDelay(900, 1100, action);
            long delay = AgentBotPotionRuntime.randomDelayMs(900, 1400);

            replies.verify(() -> AgentBotReplyRuntime.sayMapNow(null, "pots"));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(900, 1100, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.randomDelayMs(900, 1400));
            assertEquals(999L, delay);
        }
    }
}
