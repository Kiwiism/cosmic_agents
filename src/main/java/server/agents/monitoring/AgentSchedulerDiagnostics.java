package server.agents.monitoring;

import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.agents.events.AgentEventBusSnapshot;
import server.agents.coordination.AgentCoordinationRuntime;
import server.agents.coordination.AgentCoordinationRuntimeSnapshot;
import server.agents.runtime.scheduler.AgentLoadSheddingLevel;
import server.agents.runtime.scheduler.AgentLoadSheddingReason;
import server.agents.runtime.scheduler.AgentLoadSheddingState;
import server.agents.runtime.scheduler.AgentPriorityClass;
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

    public record EventRuntimeSnapshot(
            int sessions,
            long capacity,
            long queued,
            int maxHighWaterMark,
            long subscriptions,
            long published,
            long delivered,
            long dropped,
            long deduplicated,
            long listenerInvocations,
            long listenerFailures,
            long listenerTotalDurationNs,
            long listenerMaxDurationNs,
            long queueLatencyTotalNs,
            long queueLatencyMaxNs) {
    }

    public record Snapshot(
            AgentSchedulerMode mode,
            int activeAgents,
            AgentSchedulerMetrics.Snapshot scheduler,
            Map<Integer, AgentSchedulerMetrics.ShardSnapshot> shards,
            int shardRegistrationImbalance,
            Map<AgentPriorityClass, AgentSchedulerMetrics.PrioritySnapshot> readyPriorities,
            AgentSchedulerMetrics.LoadSheddingSnapshot loadShedding,
            AgentSchedulerMetrics.QuiescenceSnapshot quiescence,
            Map<String, AgentAsyncQueueMetrics.Snapshot> asyncQueues,
            Map<String, AgentEventReactionMetrics.Snapshot> eventReactions,
            EventRuntimeSnapshot eventRuntime,
            AgentCoordinationRuntimeSnapshot coordination) {
        public Snapshot {
            if (mode == null || scheduler == null || shards == null || readyPriorities == null || loadShedding == null
                    || quiescence == null || asyncQueues == null || eventReactions == null
                    || eventRuntime == null || coordination == null) {
                throw new IllegalArgumentException("Agent scheduler diagnostic snapshot is incomplete");
            }
            activeAgents = Math.max(0, activeAgents);
            shards = immutableSortedMap(shards);
            shardRegistrationImbalance = Math.max(0, shardRegistrationImbalance);
            readyPriorities = Map.copyOf(readyPriorities);
            asyncQueues = immutableSortedMap(asyncQueues);
            eventReactions = immutableSortedMap(eventReactions);
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
                AgentSchedulerMetrics.readyPrioritySnapshots(),
                AgentSchedulerMetrics.loadSheddingSnapshot(),
                AgentSchedulerMetrics.quiescenceSnapshot(),
                AgentAsyncQueueMetrics.snapshots(),
                AgentEventReactionMetrics.snapshots(),
                captureEventRuntime(),
                AgentCoordinationRuntime.snapshot());
    }

    public static List<String> lines() {
        Snapshot snapshot = capture();
        List<String> lines = new ArrayList<>(format(snapshot));
        lines.addAll(AgentSchedulerDetailDiagnostics.stateLines(
                snapshot.mode(), snapshot.activeAgents(), System.currentTimeMillis()));
        AgentSchedulerConfig config = AgentSchedulerConfig.fromSystemProperties();
        long budgetNs = TimeUnit.MILLISECONDS.toNanos(config.cycleBudgetMs());
        long averageCycleNs = snapshot.scheduler().cycles() == 0L
                ? 0L
                : snapshot.scheduler().totalCycleNs() / snapshot.scheduler().cycles();
        lines.add("Scheduler cycle budget: configuredMs=" + config.cycleBudgetMs()
                + " avgUtil=" + formatPercent(averageCycleNs, budgetNs)
                + "% maxUtil=" + formatPercent(snapshot.scheduler().maxCycleNs(), budgetNs) + "%");
        AgentSchedulerMetrics.LifecycleSnapshot lifecycle = AgentSchedulerMetrics.lifecycleSnapshot();
        lines.add("Scheduler lifecycle: registered=" + lifecycle.registered()
                + " replaced=" + lifecycle.replaced() + " cancel=" + lifecycle.cancellationRequests()
                + " cleanup=" + lifecycle.cleanedUp());
        return List.copyOf(lines);
    }

    public static List<String> lines(String[] params) {
        if (params == null || params.length == 0 || params[0].equalsIgnoreCase("status")) {
            return lines();
        }
        if (params[0].equalsIgnoreCase("shards")) {
            return format(capture()).stream()
                    .filter(line -> line.startsWith("Scheduler shards:") || line.startsWith("  shard="))
                    .toList();
        }
        if (params[0].equalsIgnoreCase("costs")) {
            return AgentSchedulerDetailDiagnostics.costLines();
        }
        return AgentSchedulerDetailDiagnostics.lines(params);
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
        lines.add("Scheduler ready queues: " + formatPriorityDepths(snapshot.readyPriorities()));

        appendShardLines(lines, snapshot);
        appendLoadSheddingLine(lines, snapshot.loadShedding());
        appendQuiescenceLine(lines, snapshot.quiescence());
        appendAsyncQueueLines(lines, snapshot.asyncQueues());
        appendEventRuntimeLines(lines, snapshot);
        return List.copyOf(lines);
    }

    private static void appendEventRuntimeLines(List<String> lines, Snapshot snapshot) {
        EventRuntimeSnapshot events = snapshot.eventRuntime();
        lines.add("Agent events: sessions=" + events.sessions() + " queued=" + events.queued()
                + "/" + events.capacity() + " high=" + events.maxHighWaterMark()
                + " published=" + events.published() + " delivered=" + events.delivered()
                + " dropped=" + events.dropped() + " deduped=" + events.deduplicated()
                + " listenerFailures=" + events.listenerFailures()
                + " listenerMaxUs=" + nanosToMicros(events.listenerMaxDurationNs())
                + " queueMaxUs=" + nanosToMicros(events.queueLatencyMaxNs()));
        long reactionAccepted = snapshot.eventReactions().values().stream()
                .mapToLong(value -> value.accepted() + value.coalesced()).sum();
        long reactionRejected = snapshot.eventReactions().values().stream()
                .mapToLong(AgentEventReactionMetrics.Snapshot::rejected).sum();
        lines.add("Agent event reactions: types=" + snapshot.eventReactions().size()
                + " accepted=" + reactionAccepted + " rejected=" + reactionRejected);
        AgentCoordinationRuntimeSnapshot coordination = snapshot.coordination();
        lines.add("Agent coordination: routes=" + coordination.routes()
                + " queued=" + coordination.queued() + " accepted=" + coordination.accepted()
                + " rejected=" + coordination.rejectedCapacity()
                + " expired=" + coordination.expired()
                + " listenerFailures=" + coordination.listenerFailures());
    }

    private static EventRuntimeSnapshot captureEventRuntime() {
        int sessions = 0;
        long capacity = 0L;
        long queued = 0L;
        int maxHighWaterMark = 0;
        long subscriptions = 0L;
        long published = 0L;
        long delivered = 0L;
        long dropped = 0L;
        long deduplicated = 0L;
        long listenerInvocations = 0L;
        long listenerFailures = 0L;
        long listenerTotalDurationNs = 0L;
        long listenerMaxDurationNs = 0L;
        long queueLatencyTotalNs = 0L;
        long queueLatencyMaxNs = 0L;
        for (var entry : AgentRuntimeRegistry.activeEntriesSnapshot()) {
            AgentEventBusSnapshot event = AgentSessionEventRuntime.snapshot(entry);
            if (event == null) {
                continue;
            }
            sessions++;
            capacity += event.capacity();
            queued += event.queued();
            maxHighWaterMark = Math.max(maxHighWaterMark, event.highWaterMark());
            subscriptions += event.subscriptions();
            published += event.published();
            delivered += event.delivered();
            dropped += event.dropped();
            deduplicated += event.deduplicated();
            listenerInvocations += event.listenerInvocations();
            listenerFailures += event.listenerFailures();
            listenerTotalDurationNs += event.listenerTotalDurationNs();
            listenerMaxDurationNs = Math.max(listenerMaxDurationNs, event.listenerMaxDurationNs());
            queueLatencyTotalNs += event.queueLatencyTotalNs();
            queueLatencyMaxNs = Math.max(queueLatencyMaxNs, event.queueLatencyMaxNs());
        }
        return new EventRuntimeSnapshot(sessions, capacity, queued, maxHighWaterMark,
                subscriptions, published, delivered, dropped, deduplicated,
                listenerInvocations, listenerFailures, listenerTotalDurationNs,
                listenerMaxDurationNs, queueLatencyTotalNs, queueLatencyMaxNs);
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
                    + " ready=" + shard.readyDepth() + " priorities="
                    + formatPriorityDepths(shard.readyPriorities()));
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
                    + " timeout=" + queue.timedOut() + " stale=" + queue.stale()
                    + " expired=" + queue.expired() + " drained=" + queue.drained());
        }
    }

    private static long nanosToMicros(long nanoseconds) {
        return TimeUnit.NANOSECONDS.toMicros(Math.max(0L, nanoseconds));
    }

    private static String formatMillis(long nanoseconds) {
        return String.format(Locale.ROOT, "%.3f", Math.max(0L, nanoseconds) / 1_000_000.0);
    }

    private static String formatPercent(long value, long total) {
        double percent = Math.max(0L, value) * 100.0 / Math.max(1L, total);
        return String.format(Locale.ROOT, "%.1f", percent);
    }

    private static String formatPriorityDepths(
            Map<AgentPriorityClass, AgentSchedulerMetrics.PrioritySnapshot> priorities) {
        List<String> values = new ArrayList<>();
        for (AgentPriorityClass priority : AgentPriorityClass.values()) {
            AgentSchedulerMetrics.PrioritySnapshot snapshot = priorities.get(priority);
            if (snapshot != null && (snapshot.readyDepth() > 0L || snapshot.readyHighWaterMark() > 0L)) {
                values.add(priority + "=" + snapshot.readyDepth() + "/" + snapshot.readyHighWaterMark());
            }
        }
        return values.isEmpty() ? "none" : String.join(",", values);
    }

    private static <K extends Comparable<? super K>, V> Map<K, V> immutableSortedMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }
}
