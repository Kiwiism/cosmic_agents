package server.agents.runtime;

import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.monitoring.AgentSchedulerMetrics;
import server.agents.runtime.async.AgentAsyncExecutorRegistry;
import server.agents.runtime.async.AgentAsyncTaskGateway;
import server.agents.runtime.async.AgentAsyncWorkKind;
import server.agents.runtime.scheduler.AgentShardedTickScheduler;
import server.agents.runtime.scheduler.AgentTickScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/** Process-level start/stop boundary for Agent scheduler-owned runtime work. */
public final class AgentRuntimeShutdownCoordinator {
    public record Report(int sessionsObserved,
                         int scheduleCancellationsRequested,
                         List<Integer> failedSessionIds,
                         int pendingAsyncRequestsCleared,
                         int schedulerRegistrationsRemaining,
                         int asyncExecutorsStopped,
                         int queuedAsyncTasksCancelled,
                         Set<AgentAsyncWorkKind> unterminatedAsyncExecutors,
                         boolean interrupted,
                         boolean timedOut,
                         long elapsedMs,
                         AgentSchedulerMetrics.Snapshot finalSchedulerSnapshot) {
        public Report {
            failedSessionIds = List.copyOf(failedSessionIds);
            unterminatedAsyncExecutors = Set.copyOf(unterminatedAsyncExecutors);
        }
    }

    private enum State {
        RUNNING,
        STOPPING,
        STOPPED
    }

    private static volatile State state = State.RUNNING;
    private static volatile Report lastReport;

    private AgentRuntimeShutdownCoordinator() {
    }

    public static synchronized void start() {
        if (state == State.STOPPED && lastReport != null && lastReport.timedOut()) {
            throw new IllegalStateException("Timed-out Agent runtime shutdown cannot be restarted safely");
        }
        AgentTickScheduler.instance().start();
        AgentShardedTickScheduler.instance().start();
        AgentAsyncExecutorRegistry.runtime().startAccepting();
        state = State.RUNNING;
        lastReport = null;
    }

    public static synchronized void beginShutdown() {
        if (state == State.RUNNING) {
            state = State.STOPPING;
            AgentAsyncExecutorRegistry.runtime().stopAccepting();
        }
    }

    public static boolean acceptingRegistrations() {
        return state == State.RUNNING;
    }

    public static Report shutdown() {
        long timeoutMs = Math.max(1L, Long.getLong("agents.scheduler.shutdownTimeoutMs", 10_000L));
        return shutdown(Duration.ofMillis(timeoutMs));
    }

    public static synchronized Report shutdown(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Agent runtime shutdown timeout must not be negative");
        }
        if (state == State.STOPPED && lastReport != null) {
            return lastReport;
        }
        beginShutdown();
        long startedNs = System.nanoTime();
        long deadlineNs = startedNs + Math.max(0L, timeout.toNanos());

        List<AgentRuntimeEntry> entries = AgentRuntimeRegistry.activeEntriesSnapshot();
        int cancellationRequests = 0;
        for (AgentRuntimeEntry entry : entries) {
            if (entry.scheduledTaskState().hasScheduledTask()) {
                cancellationRequests++;
            }
            AgentLifecycleService.cancelScheduledTickIfPresent(entry);
        }

        AgentTickScheduler.ShutdownResult sequential =
                AgentTickScheduler.instance().shutdownAndDrain(remaining(deadlineNs));
        AgentShardedTickScheduler.ShutdownResult sharded =
                AgentShardedTickScheduler.instance().shutdownAndDrain(remaining(deadlineNs));
        int pendingCleared = AgentAsyncTaskGateway.runtime().clearAll();
        AgentAsyncExecutorRegistry.ShutdownResult async =
                AgentAsyncExecutorRegistry.runtime().shutdownAllAndAwait(
                        Math.max(0L, remaining(deadlineNs).toNanos()),
                        java.util.concurrent.TimeUnit.NANOSECONDS);

        List<Integer> failedSessionIds = new ArrayList<>();
        for (AgentRuntimeEntry entry : entries) {
            ScheduledFuture<?> task = entry.scheduledTaskState().task();
            if (task != null && !task.isCancelled()) {
                failedSessionIds.add(AgentRuntimeIdentityRuntime.botId(entry));
            }
        }
        int remainingRegistrations = sequential.remaining() + sharded.remaining();
        boolean timedOut = sequential.timedOut() || sharded.timedOut() || async.timedOut()
                || !failedSessionIds.isEmpty() || System.nanoTime() > deadlineNs;
        lastReport = new Report(
                entries.size(),
                cancellationRequests,
                failedSessionIds,
                pendingCleared,
                remainingRegistrations,
                async.executors(),
                async.queuedTasksCancelled(),
                async.unterminatedExecutors(),
                async.interrupted(),
                timedOut,
                Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNs)).toMillis(),
                AgentSchedulerMetrics.snapshot());
        state = State.STOPPED;
        return lastReport;
    }

    private static Duration remaining(long deadlineNs) {
        return Duration.ofNanos(Math.max(0L, deadlineNs - System.nanoTime()));
    }
}
