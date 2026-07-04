package server.agents.runtime;

import java.util.concurrent.ScheduledFuture;

/**
 * Live scheduler handle for one Agent runtime session.
 */
public final class AgentScheduledTaskState {
    private final ScheduledFuture<?> task;

    public AgentScheduledTaskState(ScheduledFuture<?> task) {
        this.task = task;
    }

    public ScheduledFuture<?> task() {
        return task;
    }

    public boolean hasScheduledTask() {
        return task != null;
    }

    public void cancelScheduledTask() {
        if (task != null) {
            task.cancel(false);
        }
    }
}
