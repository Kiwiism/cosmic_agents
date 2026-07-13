package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.agents.integration.AgentSchedulerGatewayRuntime;
import server.agents.integration.SchedulerGateway;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void scopedScheduleRunsForCurrentSession() {
        SchedulerGateway scheduler = mock(SchedulerGateway.class);
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        AtomicInteger calls = new AtomicInteger();
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        AgentRuntimeRegistry.clear();
        AgentRuntimeRegistry.registerEntry(1, entry);

        try (MockedStatic<AgentSchedulerGatewayRuntime> runtime = mockStatic(AgentSchedulerGatewayRuntime.class)) {
            runtime.when(AgentSchedulerGatewayRuntime::scheduler).thenReturn(scheduler);
            doReturn(scheduled).when(scheduler).schedule(callback.capture(), eq(250L));

            AgentSchedulerRuntime.schedule(entry, calls::incrementAndGet, 250L);
            callback.getValue().run();

            assertEquals(1, calls.get());
        } finally {
            AgentRuntimeRegistry.clear();
        }
    }

    @Test
    void scopedScheduleSkipsReplacedSessionWithSameCharacter() {
        SchedulerGateway scheduler = mock(SchedulerGateway.class);
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        AtomicInteger calls = new AtomicInteger();
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        AgentRuntimeEntry stale = new AgentRuntimeEntry(agent, leader, null);
        AgentRuntimeEntry replacement = new AgentRuntimeEntry(agent, leader, null);
        AgentRuntimeRegistry.clear();
        AgentRuntimeRegistry.registerEntry(1, stale);

        try (MockedStatic<AgentSchedulerGatewayRuntime> runtime = mockStatic(AgentSchedulerGatewayRuntime.class)) {
            runtime.when(AgentSchedulerGatewayRuntime::scheduler).thenReturn(scheduler);
            doReturn(scheduled).when(scheduler).schedule(callback.capture(), eq(250L));

            AgentSchedulerRuntime.schedule(stale, calls::incrementAndGet, 250L);
            AgentRuntimeRegistry.unregisterEntry(1, stale);
            AgentRuntimeRegistry.registerEntry(1, replacement);
            callback.getValue().run();

            assertEquals(0, calls.get());
        } finally {
            AgentRuntimeRegistry.clear();
        }
    }
}
