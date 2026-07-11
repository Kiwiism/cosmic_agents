package server.agents.population;

import server.agents.runtime.AgentSchedulerRuntime;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cancellable Agent-owned steady population sweep. */
public final class AgentPopulationScheduler implements AutoCloseable {
    public static final long DEFAULT_SWEEP_MS = 60_000L;

    private final AgentPopulationReconciler reconciler;
    private final long periodMs;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile ScheduledFuture<?> task;

    public AgentPopulationScheduler(AgentPopulationReconciler reconciler) {
        this(reconciler, DEFAULT_SWEEP_MS);
    }

    AgentPopulationScheduler(AgentPopulationReconciler reconciler, long periodMs) {
        if (periodMs <= 0) {
            throw new IllegalArgumentException("periodMs must be positive");
        }
        this.reconciler = reconciler;
        this.periodMs = periodMs;
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            task = AgentSchedulerRuntime.register(this::sweepSafely, periodMs);
        }
    }

    public AgentPopulationReconciler.Result sweepNow() {
        return reconciler.reconcile();
    }

    private void sweepSafely() {
        try {
            reconciler.reconcile();
        } catch (RuntimeException ignored) {
            // The steady scheduler must survive a failed census or store read.
        }
    }

    @Override
    public void close() {
        started.set(false);
        ScheduledFuture<?> scheduled = task;
        task = null;
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }
}
