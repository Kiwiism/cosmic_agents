package server.agents.runtime.scheduler;

import server.agents.integration.AgentGatewayAffinityCatalog;
import server.agents.monitoring.AgentSchedulerMetrics;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentRuntimeShutdownCoordinator;
import server.agents.runtime.async.AgentAsyncTaskGateway;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Stable lifecycle-facing facade over all Agent scheduler modes. */
public final class AgentScheduler {
    private AgentScheduler() {
    }

    public static AgentScheduleHandle register(
            AgentRuntimeEntry entry,
            Runnable tick,
            long periodMs,
            AgentLifecycleService.AgentTickScheduler legacyScheduler) {
        if (entry == null || tick == null || legacyScheduler == null) {
            throw new IllegalArgumentException("Agent entry, tick, and legacy scheduler are required");
        }
        if (!AgentRuntimeShutdownCoordinator.acceptingRegistrations()) {
            throw new RejectedExecutionException("Agent runtime is stopping");
        }
        AgentSchedulerConfig config = AgentSchedulerConfig.fromSystemProperties();
        entry.tickSliceState().configure(
                config.mode() != AgentSchedulerMode.LEGACY_PER_AGENT && config.tickSlicingEnabled(),
                config.maxSlicesPerTurn(),
                config.maxContinuationsPerFrame());
        AgentScheduleHandle handle = switch (config.mode()) {
            case LEGACY_PER_AGENT -> registerLegacy(entry, tick, periodMs, legacyScheduler);
            case CENTRAL_SEQUENTIAL -> AgentTickScheduler.instance().register(entry, tick, periodMs);
            case CENTRAL_SHARDED -> {
                if (!AgentGatewayAffinityCatalog.multiShardReady()) {
                    throw new IllegalStateException(
                            "CENTRAL_SHARDED is blocked by an unsafe Agent gateway affinity");
                }
                yield AgentShardedTickScheduler.instance().register(entry, tick, periodMs);
            }
        };
        AgentSchedulerMetrics.recordLifecycleRegistered();
        return handle;
    }

    public static boolean wake(AgentRuntimeEntry entry) {
        if (entry == null || !(entry.scheduledTaskState().task() instanceof AgentScheduleHandle handle)) {
            return false;
        }
        return handle.sessionId().matches(entry) && handle.wake();
    }

    public static CompletionStage<AgentQuiescenceToken> quiesce(
            AgentRuntimeEntry entry,
            AgentQuiescenceReason reason) {
        return quiesce(entry, reason, Duration.ofMillis(defaultQuiescenceTimeoutMs()));
    }

    public static CompletionStage<AgentQuiescenceToken> quiesce(
            AgentRuntimeEntry entry,
            AgentQuiescenceReason reason,
            Duration timeout) {
        AgentScheduleHandle handle = handle(entry);
        if (handle == null) {
            return CompletableFuture.failedFuture(new AgentQuiescenceException(
                    AgentQuiescenceException.Reason.CLOSED,
                    "Agent session has no active schedule handle"));
        }
        return handle.quiesce(reason, timeout);
    }

    public static boolean resume(AgentRuntimeEntry entry, AgentQuiescenceToken token) {
        AgentScheduleHandle handle = handle(entry);
        return handle != null && handle.resume(token);
    }

    public static boolean validatesQuiescence(AgentRuntimeEntry entry, AgentQuiescenceToken token) {
        AgentScheduleHandle handle = handle(entry);
        return handle != null && handle.validatesQuiescence(token);
    }

    private static AgentScheduleHandle registerLegacy(
            AgentRuntimeEntry entry,
            Runnable tick,
            long periodMs,
            AgentLifecycleService.AgentTickScheduler legacyScheduler) {
        AgentSessionId sessionId = AgentSessionId.from(entry);
        AgentQuiescenceController controller = controller(entry, sessionId, System::currentTimeMillis);
        LegacyTickGuard guard = new LegacyTickGuard(tick, controller);
        return new LegacyScheduleHandle(
                sessionId,
                legacyScheduler.schedule(guard::run, periodMs),
                controller);
    }

    static AgentQuiescenceController controller(
            AgentRuntimeEntry entry,
            AgentSessionId sessionId,
            java.util.function.LongSupplier nowMs) {
        return new AgentQuiescenceController(
                entry,
                sessionId,
                nowMs,
                () -> AgentAsyncTaskGateway.runtime().pendingCount(sessionId),
                () -> AgentRuntimeRegistry.isActiveSession(entry, sessionId.generation()));
    }

    private static AgentScheduleHandle handle(AgentRuntimeEntry entry) {
        if (entry == null || !(entry.scheduledTaskState().task() instanceof AgentScheduleHandle handle)) {
            return null;
        }
        return handle.sessionId().matches(entry) ? handle : null;
    }

    private static long defaultQuiescenceTimeoutMs() {
        return Math.max(1L, Long.getLong("agents.scheduler.quiescenceTimeoutMs", 5_000L));
    }

    private static final class LegacyTickGuard {
        private final Runnable tick;
        private final AgentQuiescenceController controller;

        private LegacyTickGuard(Runnable tick, AgentQuiescenceController controller) {
            this.tick = tick;
            this.controller = controller;
        }

        private void run() {
            AgentQuiescenceController.ExecutionMode executionMode = controller.beforeExecution();
            if (executionMode == AgentQuiescenceController.ExecutionMode.SKIP) {
                return;
            }
            try {
                if (executionMode == AgentQuiescenceController.ExecutionMode.QUIESCENCE_MAINTENANCE) {
                    controller.runMaintenance();
                } else {
                    tick.run();
                }
            } finally {
                controller.afterExecution();
            }
        }
    }

    private static final class LegacyScheduleHandle implements AgentScheduleHandle {
        private final AgentSessionId sessionId;
        private final ScheduledFuture<?> delegate;
        private final AgentQuiescenceController quiescence;

        private LegacyScheduleHandle(AgentSessionId sessionId,
                                     ScheduledFuture<?> delegate,
                                     AgentQuiescenceController quiescence) {
            if (delegate == null) {
                throw new IllegalArgumentException("Legacy scheduler returned no handle");
            }
            this.sessionId = sessionId;
            this.delegate = delegate;
            this.quiescence = quiescence;
        }

        @Override
        public AgentSessionId sessionId() {
            return sessionId;
        }

        @Override
        public AgentSchedulerMode mode() {
            return AgentSchedulerMode.LEGACY_PER_AGENT;
        }

        @Override
        public boolean wake() {
            return false;
        }

        @Override
        public CompletionStage<AgentQuiescenceToken> quiesce(
                AgentQuiescenceReason reason,
                Duration timeout) {
            return quiescence.request(reason, timeout);
        }

        @Override
        public boolean resume(AgentQuiescenceToken token) {
            return quiescence.resume(token);
        }

        @Override
        public boolean validatesQuiescence(AgentQuiescenceToken token) {
            return quiescence.validates(token);
        }

        @Override
        public boolean isQuiescent() {
            return quiescence.quiescent();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return delegate.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed other) {
            return delegate.compareTo(other);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            quiescence.close();
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public Void get() throws InterruptedException, ExecutionException {
            delegate.get();
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            delegate.get(timeout, unit);
            return null;
        }
    }
}
