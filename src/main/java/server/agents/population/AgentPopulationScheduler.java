package server.agents.population;

import server.agents.runtime.AgentSchedulerRuntime;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;

/** Cancellable Agent-owned steady population sweep. */
public final class AgentPopulationScheduler implements AutoCloseable {
    public static final long DEFAULT_SWEEP_MS = 60_000L;
    static final long FAST_START_WINDOW_MS = 30_000L;
    static final long FAST_START_STEP_MS = 5_000L;

    private final AgentPopulationReconciler reconciler;
    private final long periodMs;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final List<ScheduledFuture<?>> fastStartTasks = new CopyOnWriteArrayList<>();
    private final AgentPopulationMetrics metrics;
    private final TaskScheduler taskScheduler;
    private volatile ScheduledFuture<?> task;

    public AgentPopulationScheduler(AgentPopulationReconciler reconciler) {
        this(reconciler, null, DEFAULT_SWEEP_MS, runtimeScheduler());
    }

    AgentPopulationScheduler(AgentPopulationReconciler reconciler, long periodMs) {
        this(reconciler, null, periodMs, runtimeScheduler());
    }

    AgentPopulationScheduler(AgentPopulationReconciler reconciler,
                             AgentPopulationMetrics metrics,
                             long periodMs) {
        this(reconciler, metrics, periodMs, runtimeScheduler());
    }

    AgentPopulationScheduler(AgentPopulationReconciler reconciler,
                             AgentPopulationMetrics metrics,
                             long periodMs,
                             TaskScheduler taskScheduler) {
        if (periodMs <= 0) {
            throw new IllegalArgumentException("periodMs must be positive");
        }
        this.reconciler = reconciler;
        this.metrics = metrics;
        this.taskScheduler = taskScheduler;
        this.periodMs = periodMs;
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            task = taskScheduler.register(this::sweepSafely, periodMs);
        }
    }

    public AgentPopulationReconciler.Result sweepNow() {
        return reconciler.reconcile();
    }

    public void scheduleFastStart() {
        cancelFastStart();
        long currentGeneration = generation.incrementAndGet();
        long now = System.currentTimeMillis();
        for (long delay = 0; delay <= FAST_START_WINDOW_MS; delay += FAST_START_STEP_MS) {
            long expected = now + delay;
            Object callbackLock = new Object();
            ScheduledFuture<?>[] taskReference = new ScheduledFuture<?>[1];
            boolean[] completed = {false};
            ScheduledFuture<?> task = taskScheduler.schedule(() -> {
                try {
                    if (generation.get() != currentGeneration) return;
                    if (metrics != null) metrics.recordExpectedSweep(expected, System.currentTimeMillis());
                    sweepSafely();
                } finally {
                    synchronized (callbackLock) {
                        completed[0] = true;
                        if (taskReference[0] != null) fastStartTasks.remove(taskReference[0]);
                        updateQueueMetric();
                    }
                }
            }, delay);
            synchronized (callbackLock) {
                taskReference[0] = task;
                if (!completed[0]) fastStartTasks.add(task);
                updateQueueMetric();
            }
        }
    }

    public void cancelFastStart() {
        generation.incrementAndGet();
        fastStartTasks.forEach(task -> task.cancel(false));
        fastStartTasks.clear();
        updateQueueMetric();
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
        cancelFastStart();
        started.set(false);
        ScheduledFuture<?> scheduled = task;
        task = null;
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }

    private void updateQueueMetric() {
        if (metrics != null) metrics.setQueuedCallbacks(fastStartTasks.size());
    }

    interface TaskScheduler {
        ScheduledFuture<?> schedule(Runnable action, long delayMs);
        ScheduledFuture<?> register(Runnable action, long periodMs);
    }

    private static TaskScheduler runtimeScheduler() {
        return new TaskScheduler() {
            @Override public ScheduledFuture<?> schedule(Runnable action, long delayMs) {
                return AgentSchedulerRuntime.schedule(action, delayMs);
            }
            @Override public ScheduledFuture<?> register(Runnable action, long periodMs) {
                return AgentSchedulerRuntime.register(action, periodMs);
            }
        };
    }
}
