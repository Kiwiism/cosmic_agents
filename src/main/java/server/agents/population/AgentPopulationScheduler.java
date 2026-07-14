package server.agents.population;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.monitoring.AgentAsyncQueueMetrics;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.async.AgentAsyncExecutorRegistry;
import server.agents.runtime.async.AgentAsyncWorkKind;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Cancellable Agent-owned steady population sweep. */
public final class AgentPopulationScheduler implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AgentPopulationScheduler.class);
    public static final long DEFAULT_SWEEP_MS = 60_000L;
    static final long FAST_START_WINDOW_MS = 30_000L;
    static final long FAST_START_STEP_MS = 5_000L;

    private final AgentPopulationReconciler reconciler;
    private final long periodMs;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean sweepPending = new AtomicBoolean();
    private final AtomicBoolean sweepAgain = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final List<ScheduledFuture<?>> fastStartTasks = new CopyOnWriteArrayList<>();
    private final AgentPopulationMetrics metrics;
    private final TaskScheduler taskScheduler;
    private final SweepDispatcher sweepDispatcher;
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
        this(reconciler, metrics, periodMs, taskScheduler, runtimeSweepDispatcher());
    }

    AgentPopulationScheduler(AgentPopulationReconciler reconciler,
                             AgentPopulationMetrics metrics,
                             long periodMs,
                             TaskScheduler taskScheduler,
                             SweepDispatcher sweepDispatcher) {
        if (periodMs <= 0) {
            throw new IllegalArgumentException("periodMs must be positive");
        }
        this.reconciler = reconciler;
        this.metrics = metrics;
        this.taskScheduler = taskScheduler;
        this.sweepDispatcher = sweepDispatcher;
        this.periodMs = periodMs;
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            task = taskScheduler.register(this::requestSweep, periodMs);
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
                    requestSweep();
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

    private boolean sweepSafely() {
        try {
            reconciler.reconcile();
            return true;
        } catch (RuntimeException ignored) {
            // The steady scheduler must survive a failed census or store read.
            if (metrics != null) {
                metrics.recordFailure();
            }
            return false;
        }
    }

    private void requestSweep() {
        if (closed.get()) {
            return;
        }
        if (!sweepPending.compareAndSet(false, true)) {
            sweepAgain.set(true);
            AgentAsyncQueueMetrics.recordCoalesced(AgentAsyncWorkKind.POPULATION_LIFECYCLE.metricName(), 1);
            return;
        }
        try {
            sweepDispatcher.dispatch(this::runCoalescedSweeps);
        } catch (RejectedExecutionException failure) {
            sweepPending.set(false);
            if (!closed.get() && metrics != null) {
                metrics.recordFailure();
            }
        }
    }

    private void runCoalescedSweeps() {
        long startedNs = System.nanoTime();
        boolean succeeded = true;
        try {
            boolean repeat;
            do {
                repeat = sweepAgain.getAndSet(false);
                if (!closed.get()) {
                    succeeded &= sweepSafely();
                }
            } while (!closed.get() && (repeat || sweepAgain.get()));
        } finally {
            if (succeeded) {
                AgentAsyncQueueMetrics.recordCompleted(
                        AgentAsyncWorkKind.POPULATION_LIFECYCLE.metricName(),
                        System.nanoTime() - startedNs);
            } else {
                AgentAsyncQueueMetrics.recordFailed(
                        AgentAsyncWorkKind.POPULATION_LIFECYCLE.metricName(),
                        System.nanoTime() - startedNs);
            }
            sweepPending.set(false);
            if (!closed.get() && sweepAgain.getAndSet(false)) {
                requestSweep();
            }
        }
    }

    @Override
    public void close() {
        closed.set(true);
        cancelFastStart();
        started.set(false);
        sweepAgain.set(false);
        ScheduledFuture<?> scheduled = task;
        task = null;
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        long shutdownTimeoutMs = Math.max(1L,
                Long.getLong("agents.population.shutdownTimeoutMs", 10_000L));
        if (!sweepDispatcher.shutdownAndAwait(shutdownTimeoutMs)) {
            if (metrics != null) {
                metrics.recordFailure();
            }
            log.warn("Agent population lifecycle lane did not stop within {} ms", shutdownTimeoutMs);
        }
    }

    private void updateQueueMetric() {
        if (metrics != null) metrics.setQueuedCallbacks(fastStartTasks.size());
    }

    interface TaskScheduler {
        ScheduledFuture<?> schedule(Runnable action, long delayMs);
        ScheduledFuture<?> register(Runnable action, long periodMs);
    }

    interface SweepDispatcher {
        void dispatch(Runnable action);

        default boolean shutdownAndAwait(long timeoutMs) {
            return true;
        }
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

    private static SweepDispatcher runtimeSweepDispatcher() {
        return new SweepDispatcher() {
            @Override
            public void dispatch(Runnable action) {
                AgentAsyncExecutorRegistry.runtime().execute(
                        AgentAsyncWorkKind.POPULATION_LIFECYCLE,
                        action);
            }

            @Override
            public boolean shutdownAndAwait(long timeoutMs) {
                try {
                    return AgentAsyncExecutorRegistry.runtime().shutdownAndAwait(
                            AgentAsyncWorkKind.POPULATION_LIFECYCLE,
                            timeoutMs,
                            TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        };
    }
}
