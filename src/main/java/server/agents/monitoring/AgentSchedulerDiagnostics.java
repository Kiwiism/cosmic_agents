package server.agents.monitoring;

import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.scheduler.AgentLoadSheddingLevel;
import server.agents.runtime.scheduler.AgentLoadSheddingReason;
import server.agents.runtime.scheduler.AgentLoadSheddingState;
import server.agents.runtime.scheduler.AgentSchedulerConfig;
import server.agents.runtime.scheduler.AgentSchedulerMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/** Bounded read-only operator view of centralized Agent scheduler metrics. */
public final class AgentSchedulerDiagnostics {
    private static final int MAX_SHARD_LINES = 8;
    private static final int MAX_ASYNC_QUEUE_LINES = 12;

    public record Snapshot(
            AgentSchedulerMode mode,
            int activeAgents,
            AgentSchedulerMetrics.Snapshot scheduler,
            Map<Integer, AgentSchedulerMetrics.ShardSnapshot> shards,
            int shardRegistrationImbalance,
            AgentSchedulerMetrics.LoadSheddingSnapshot loadShedding,
            AgentSchedulerMetrics.QuiescenceSnapshot quiescence,
            Map<String, AgentAsyncQueueMetrics.Snapshot> asyncQueues) {
        public Snapshot {
            if (mode == null || scheduler == null || shards == null || loadShedding == null
                    || quiescence == null || asyncQueues == null) {
                throw new IllegalArgumentException("Agent scheduler diagnostic snapshot is incomplete");
            }
            activeAgents = Math.max(0, activeAgents);
            shards = immutableSortedMap(shards);
            shardRegistrationImbalance = Math.max(0, shardRegistrationImbalance);
            asyncQueues = immutableSortedMap(asyncQueues);
        }
    }

    private AgentSchedulerDiagnostics() {
    }

    public static Snapshot capture() {
        return new Snapshot(
                AgentSchedulerConfig.fromSystemProperties().mode(),
                AgentRuntimeRegistry.activeAgentCount(),
                AgentSchedulerMetrics.snapshot(),
                AgentSchedulerMetrics.shardSnapshots(),
                AgentSchedulerMetrics.shardRegistrationImbalance(),
                AgentSchedulerMetrics.loadSheddingSnapshot(),
                AgentSchedulerMetrics.quiescenceSnapshot(),
                AgentAsyncQueueMetrics.snapshots());
    }

    public static List<String> lines() {
        return format(capture());
    }

    static List<String> format(Snapshot snapshot) {
        AgentSchedulerMetrics.Snapshot scheduler = snapshot.scheduler();
        List<String> lines = new ArrayList<>();
        lines.add("Agent scheduler: mode=" + snapshot.mode() + " active=" + snapshot.activeAgents()
                + " cycles=" + scheduler.cycles() + " updated=" + scheduler.updatedAgents()
                + " skipped=" + scheduler.skippedAgents() + " failed=" + scheduler.failedAgents()
                + " slow=" + scheduler.slowAgents());
        lines.add("Scheduler timing: lagMs p50/p95/p99=" + scheduler.queueLagP50Ms() + "/"
                + scheduler.queueLagP95Ms() + "/" + scheduler.queueLagP99Ms()
                + " max=" + scheduler.maxQueueLagMs() + " workUs p50/p95/p99="
                + nanosToMicros(scheduler.workDurationP50Ns()) + "/"
                + nanosToMicros(scheduler.workDurationP95Ns()) + "/"
                + nanosToMicros(scheduler.workDurationP99Ns())
                + " cycleMaxMs=" + formatMillis(scheduler.maxCycleNs()));
        lines.add("Scheduler pressure: ingress=" + scheduler.ingressDepth() + " high="
                + scheduler.ingressHighWaterMark() + " due=" + scheduler.dueHeapDepth()
                + " ready=" + scheduler.readyDepth() + " budget=" + scheduler.budgetExhaustions()
                + " deferred=" + scheduler.deferredWork() + " starvation="
                + scheduler.starvationPromotions() + " mapDeferred=" + scheduler.mapBudgetDeferrals()
                + " continuations=" + scheduler.tickContinuations());

        appendShardLines(lines, snapshot);
        appendLoadSheddingLine(lines, snapshot.loadShedding());
        appendQuiescenceLine(lines, snapshot.quiescence());
        appendAsyncQueueLines(lines, snapshot.asyncQueues());
        return List.copyOf(lines);
    }

    private static void appendShardLines(List<String> lines, Snapshot snapshot) {
        if (snapshot.shards().isEmpty()) {
            lines.add("Scheduler shards: none; imbalance=0");
            return;
        }
        lines.add("Scheduler shards: count=" + snapshot.shards().size()
                + " imbalance=" + snapshot.shardRegistrationImbalance());
        int shown = 0;
        for (Map.Entry<Integer, AgentSchedulerMetrics.ShardSnapshot> entry : snapshot.shards().entrySet()) {
            if (shown++ == MAX_SHARD_LINES) {
                lines.add("  ... " + (snapshot.shards().size() - MAX_SHARD_LINES) + " more shard(s)");
                break;
            }
            AgentSchedulerMetrics.ShardSnapshot shard = entry.getValue();
            lines.add("  shard=" + entry.getKey() + " agents=" + shard.registrations()
                    + " ingress=" + shard.ingressDepth() + " due=" + shard.dueHeapDepth()
                    + " ready=" + shard.readyDepth());
        }
    }

    private static void appendLoadSheddingLine(
            List<String> lines,
            AgentSchedulerMetrics.LoadSheddingSnapshot loadShedding) {
        AgentLoadSheddingLevel highest = AgentLoadSheddingLevel.NORMAL;
        Set<AgentLoadSheddingReason> reasons = EnumSet.noneOf(AgentLoadSheddingReason.class);
        for (AgentLoadSheddingState state : loadShedding.shardStates().values()) {
            if (state.level().ordinal() > highest.ordinal()) {
                highest = state.level();
            }
            reasons.addAll(state.reasons());
        }
        lines.add("Load shedding: level=" + highest + " reasons=" + reasons
                + " transitions=" + loadShedding.transitions()
                + " suppressed=" + loadShedding.suppressedWork()
                + " rejected=" + loadShedding.rejectedAdmissions());
    }

    private static void appendQuiescenceLine(
            List<String> lines,
            AgentSchedulerMetrics.QuiescenceSnapshot quiescence) {
        lines.add("Quiescence: requested=" + quiescence.requested()
                + " completed=" + quiescence.completed() + " timedOut=" + quiescence.timedOut()
                + " cancelled=" + quiescence.cancelled() + " resumed=" + quiescence.resumed()
                + " durationMs p50/p95/p99=" + quiescence.durationP50Ms() + "/"
                + quiescence.durationP95Ms() + "/" + quiescence.durationP99Ms());
    }

    private static void appendAsyncQueueLines(
            List<String> lines,
            Map<String, AgentAsyncQueueMetrics.Snapshot> queues) {
        if (queues.isEmpty()) {
            lines.add("Agent async queues: none initialized");
            return;
        }
        lines.add("Agent async queues: count=" + queues.size());
        int shown = 0;
        for (Map.Entry<String, AgentAsyncQueueMetrics.Snapshot> entry : queues.entrySet()) {
            if (shown++ == MAX_ASYNC_QUEUE_LINES) {
                lines.add("  ... " + (queues.size() - MAX_ASYNC_QUEUE_LINES) + " more queue(s)");
                break;
            }
            AgentAsyncQueueMetrics.Snapshot queue = entry.getValue();
            lines.add("  queue=" + entry.getKey() + " depth=" + queue.currentDepth() + "/"
                    + queue.capacity() + " high=" + queue.maxDepth() + " active=" + queue.active()
                    + " submitted=" + queue.submitted() + " coalesced=" + queue.coalesced()
                    + " rejected=" + queue.rejected() + " failed=" + queue.failed()
                    + " timeout=" + queue.timedOut() + " stale=" + queue.stale());
        }
    }

    private static long nanosToMicros(long nanoseconds) {
        return TimeUnit.NANOSECONDS.toMicros(Math.max(0L, nanoseconds));
    }

    private static String formatMillis(long nanoseconds) {
        return String.format(Locale.ROOT, "%.3f", Math.max(0L, nanoseconds) / 1_000_000.0);
    }

    private static <K extends Comparable<? super K>, V> Map<K, V> immutableSortedMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }
}
