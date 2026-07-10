package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AgentScheduledTaskScopeTest {
    @Test
    void removesCompletedTaskFromScope() {
        AgentScheduledTaskScope scope = new AgentScheduledTaskScope();
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();

        scope.schedule(action -> {
            scheduled.set(action);
            return future;
        }, calls::incrementAndGet);
        assertEquals(1, scope.pendingTaskCount());

        scheduled.get().run();

        assertEquals(1, calls.get());
        assertEquals(0, scope.pendingTaskCount());
        verify(future, never()).cancel(false);
    }

    @Test
    void cancellationPreventsPendingTaskFromRunning() {
        AgentScheduledTaskScope scope = new AgentScheduledTaskScope();
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        scope.schedule(action -> {
            scheduled.set(action);
            return future;
        }, calls::incrementAndGet);

        scope.cancelAll();
        scheduled.get().run();

        assertEquals(0, calls.get());
        assertEquals(0, scope.pendingTaskCount());
        verify(future).cancel(false);
    }

    @Test
    void immediateCompletionDoesNotLeaveTrackedFuture() {
        AgentScheduledTaskScope scope = new AgentScheduledTaskScope();
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicInteger calls = new AtomicInteger();

        scope.schedule(action -> {
            action.run();
            return future;
        }, calls::incrementAndGet);

        assertEquals(1, calls.get());
        assertEquals(0, scope.pendingTaskCount());
        verify(future).cancel(false);
    }
}
