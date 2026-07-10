package server.agents.runtime;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentSchedulerGatewayRuntime;
import server.agents.integration.SchedulerGateway;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentSchedulerRuntimeTest {
    @Test
    void scheduleDelegatesToExistingTimerManagerAndReturnsCancellationHandle() {
        SchedulerGateway scheduler = mock(SchedulerGateway.class);
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        Runnable action = () -> { };

        try (MockedStatic<AgentSchedulerGatewayRuntime> runtime = mockStatic(AgentSchedulerGatewayRuntime.class)) {
            runtime.when(AgentSchedulerGatewayRuntime::scheduler).thenReturn(scheduler);
            doReturn(scheduled).when(scheduler).schedule(action, 250L);

            assertSame(scheduled, AgentSchedulerRuntime.schedule(action, 250L));
        }
    }

    @Test
    void registerDelegatesRepeatingTicksToExistingTimerManager() {
        SchedulerGateway scheduler = mock(SchedulerGateway.class);
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        Runnable action = () -> { };

        try (MockedStatic<AgentSchedulerGatewayRuntime> runtime = mockStatic(AgentSchedulerGatewayRuntime.class)) {
            runtime.when(AgentSchedulerGatewayRuntime::scheduler).thenReturn(scheduler);
            doReturn(scheduled).when(scheduler).register(action, 100L);

            assertSame(scheduled, AgentSchedulerRuntime.register(action, 100L));
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
