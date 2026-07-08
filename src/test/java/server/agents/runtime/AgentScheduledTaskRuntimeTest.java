package server.agents.runtime;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentScheduledTaskRuntimeTest {
    @Test
    void adaptsScheduledTaskCancellation() {
        ScheduledFuture<?> task = mock(ScheduledFuture.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, task);

        assertFalse(AgentScheduledTaskRuntime.hasScheduledTask(null));
        assertTrue(AgentScheduledTaskRuntime.hasScheduledTask(entry));

        AgentScheduledTaskRuntime.cancelScheduledTask(entry);
        AgentScheduledTaskRuntime.cancelScheduledTask(null);

        verify(task).cancel(false);
    }
}
