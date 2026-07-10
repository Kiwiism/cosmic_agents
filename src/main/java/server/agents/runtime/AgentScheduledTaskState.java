package server.agents.runtime;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live scheduler handle for one Agent runtime session.
 */
public final class AgentScheduledTaskState {
    private final AtomicReference<ScheduledFuture<?>> task;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();

    public AgentScheduledTaskState(ScheduledFuture<?> task) {
        this.task = new AtomicReference<>(task);
    }

    public ScheduledFuture<?> task() {
        return task.get();
    }

    public void attachScheduledTask(ScheduledFuture<?> scheduledTask) {
        if (scheduledTask == null) {
            throw new IllegalArgumentException("scheduledTask must not be null");
        }
        if (!task.compareAndSet(null, scheduledTask)) {
            throw new IllegalStateException("scheduled task is already attached");
        }
        if (cancellationRequested.get()) {
            scheduledTask.cancel(false);
        }
    }

    public boolean hasScheduledTask() {
        return task.get() != null;
    }

    public void cancelScheduledTask() {
        cancellationRequested.set(true);
        ScheduledFuture<?> scheduledTask = task.get();
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
    }
}
