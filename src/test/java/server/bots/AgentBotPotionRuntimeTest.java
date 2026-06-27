package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotPotionRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotPotionRuntimeTest {
    @Test
    void potionSchedulerMethodsDelegateToAgentSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.randomDelayMs(900, 1400)).thenReturn(999L);

            AgentBotPotionRuntime.afterDelay(500L, action);
            AgentBotPotionRuntime.afterRandomDelay(900, 1100, action);
            long delay = AgentBotPotionRuntime.randomDelayMs(900, 1400);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(500L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(900, 1100, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.randomDelayMs(900, 1400));
            assertEquals(999L, delay);
        }
    }
}
