package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotManagerSchedulerRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotManagerSchedulerRuntimeTest {
    @Test
    void afterDelayDelegatesToAgentSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotManagerSchedulerRuntime.afterDelay(321L, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(321L, action));
        }
    }
}
