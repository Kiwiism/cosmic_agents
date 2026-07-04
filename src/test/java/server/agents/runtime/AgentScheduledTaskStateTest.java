package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentScheduledTaskStateTest {
    @Test
    void emptyStateHasNoScheduledTaskAndCancelIsNoop() {
        AgentScheduledTaskState state = new AgentScheduledTaskState(null);

        assertFalse(state.hasScheduledTask());

        state.cancelScheduledTask();
    }

    @Test
    void exposesAndCancelsScheduledTaskWithoutInterruptingRunningTick() {
        ScheduledFuture<?> task = mock(ScheduledFuture.class);
        AgentScheduledTaskState state = new AgentScheduledTaskState(task);

        assertTrue(state.hasScheduledTask());
        assertSame(task, state.task());

        state.cancelScheduledTask();

        verify(task).cancel(false);
    }
}
