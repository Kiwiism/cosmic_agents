package server.agents.plans;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScriptTaskQueueStateTest {
    @Test
    void ownsQueuedTasksActiveTaskAndActivityEpoch() {
        AgentScriptTaskQueueState state = new AgentScriptTaskQueueState();
        AgentTask first = AgentTask.moveTo(new Point(10, 20), true);
        AgentTask second = AgentTask.stop();

        int epoch = state.activityEpoch();
        state.addTask(first);
        state.addTask(second);

        assertTrue(state.hasTasks());
        assertSame(first, state.pollTask());
        state.setActiveTask(first);
        assertSame(first, state.activeTask());

        state.setActiveTask(null);
        assertSame(second, state.pollTask());
        assertFalse(state.hasTasks());

        assertEquals(epoch + 1, state.bumpActivityEpoch());
        state.addTask(first);
        state.setActiveTask(first);
        state.clearTasks();
        assertFalse(state.hasTasks());
    }

    @Test
    void rejectsAuthoritativeTaskExplicitlyAtCapacityWithoutReordering() {
        AgentScriptTaskQueueState state = new AgentScriptTaskQueueState(2);
        AgentTask first = AgentTask.stop();
        AgentTask second = AgentTask.grind();

        state.addTask(first);
        state.addTask(second);

        assertEquals(2, state.queuedTaskCount());
        assertThrows(java.util.concurrent.RejectedExecutionException.class,
                () -> state.addTask(AgentTask.stop()));
        assertSame(first, state.pollTask());
        assertSame(second, state.pollTask());
    }
}
