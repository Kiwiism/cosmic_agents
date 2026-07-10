package server.agents.runtime;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.TimerManager;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentSchedulerRuntimeTest {
    @Test
    void scheduleDelegatesToExistingTimerManagerAndReturnsCancellationHandle() {
        TimerManager timer = mock(TimerManager.class);
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        Runnable action = () -> { };

        try (MockedStatic<TimerManager> timers = mockStatic(TimerManager.class)) {
            timers.when(TimerManager::getInstance).thenReturn(timer);
            doReturn(scheduled).when(timer).schedule(action, 250L);

            assertSame(scheduled, AgentSchedulerRuntime.schedule(action, 250L));
        }
    }

    @Test
    void randomDelayUsesLegacyInclusiveExclusiveWindow() {
        for (int i = 0; i < 100; i++) {
            long delayMs = AgentSchedulerRuntime.randomDelayMs(500, 700);

            assertTrue(delayMs >= 500);
            assertTrue(delayMs < 700);
        }
    }
}
