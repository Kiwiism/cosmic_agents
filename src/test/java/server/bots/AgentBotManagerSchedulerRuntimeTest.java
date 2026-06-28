package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotManagerSchedulerRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class AgentBotManagerSchedulerRuntimeTest {
    @Test
    void afterDelayDelegatesToAgentSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotManagerSchedulerRuntime.afterDelay(321L, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(321L, action));
        }
    }

    @Test
    void adaptsScheduledTaskCancellation() {
        ScheduledFuture<?> task = mock(ScheduledFuture.class);
        BotEntry entry = new BotEntry(null, null, task);

        assertFalse(AgentBotManagerSchedulerRuntime.hasScheduledTask(null));
        assertTrue(AgentBotManagerSchedulerRuntime.hasScheduledTask(entry));

        AgentBotManagerSchedulerRuntime.cancelScheduledTask(entry);
        AgentBotManagerSchedulerRuntime.cancelScheduledTask(null);

        verify(task).cancel(false);
    }
}
