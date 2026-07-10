package server.agents.integration.cosmic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.TimerManager;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class CosmicSchedulerGatewayTest {
    @Test
    void delegatesOneShotAndRepeatingTasksToTimerManager() {
        TimerManager timer = mock(TimerManager.class);
        ScheduledFuture<?> oneShot = mock(ScheduledFuture.class);
        ScheduledFuture<?> repeating = mock(ScheduledFuture.class);
        Runnable action = () -> { };

        try (MockedStatic<TimerManager> timers = mockStatic(TimerManager.class)) {
            timers.when(TimerManager::getInstance).thenReturn(timer);
            doReturn(oneShot).when(timer).schedule(action, 250L);
            doReturn(repeating).when(timer).register(action, 100L);

            assertSame(oneShot, CosmicSchedulerGateway.INSTANCE.schedule(action, 250L));
            assertSame(repeating, CosmicSchedulerGateway.INSTANCE.register(action, 100L));
        }
    }
}
