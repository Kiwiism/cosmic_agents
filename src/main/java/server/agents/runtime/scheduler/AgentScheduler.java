package server.agents.runtime.scheduler;

import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.integration.AgentGatewayAffinityCatalog;

import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
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
        AgentSchedulerConfig config = AgentSchedulerConfig.fromSystemProperties();
        return switch (config.mode()) {
            case LEGACY_PER_AGENT -> new LegacyScheduleHandle(
                    AgentSessionId.from(entry),
                    legacyScheduler.schedule(tick, periodMs));
            case CENTRAL_SEQUENTIAL -> AgentTickScheduler.instance().register(entry, tick, periodMs);
            case CENTRAL_SHARDED -> {
                if (!AgentGatewayAffinityCatalog.multiShardReady()) {
                    throw new IllegalStateException(
                            "CENTRAL_SHARDED is blocked by an unsafe Agent gateway affinity");
                }
                yield AgentShardedTickScheduler.instance().register(entry, tick, periodMs);
            }
        };
    }

    public static boolean wake(AgentRuntimeEntry entry) {
        if (entry == null || !(entry.scheduledTaskState().task() instanceof AgentScheduleHandle handle)) {
            return false;
        }
        return handle.sessionId().matches(entry) && handle.wake();
    }

    private static final class LegacyScheduleHandle implements AgentScheduleHandle {
        private final AgentSessionId sessionId;
        private final ScheduledFuture<?> delegate;

        private LegacyScheduleHandle(AgentSessionId sessionId, ScheduledFuture<?> delegate) {
            if (delegate == null) {
                throw new IllegalArgumentException("Legacy scheduler returned no handle");
            }
            this.sessionId = sessionId;
            this.delegate = delegate;
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
        public long getDelay(TimeUnit unit) {
            return delegate.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed other) {
            return delegate.compareTo(other);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
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
