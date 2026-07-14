package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AgentScheduledTaskStateTest {
    @Test
    void attachesTaskOnce() {
        AgentScheduledTaskState state = new AgentScheduledTaskState(null);
        ScheduledFuture<?> task = mock(ScheduledFuture.class);

        state.attachScheduledTask(task);

        assertSame(task, state.task());
        assertThrows(IllegalStateException.class,
                () -> state.attachScheduledTask(mock(ScheduledFuture.class)));
    }

    @Test
    void cancellationBeforeAttachmentCancelsAttachedTask() {
        AgentScheduledTaskState state = new AgentScheduledTaskState(null);
        ScheduledFuture<?> task = mock(ScheduledFuture.class);

        state.cancelScheduledTask();
        state.attachScheduledTask(task);

        verify(task).cancel(false);
    }

    @Test
    void cancellationIsIdempotent() {
        AgentScheduledTaskState state = new AgentScheduledTaskState(null);
        ScheduledFuture<?> task = mock(ScheduledFuture.class);
        state.attachScheduledTask(task);

        assertTrue(state.cancelScheduledTask());
        assertFalse(state.cancelScheduledTask());

        verify(task, times(1)).cancel(false);
    }
}
