package server.agents.monitoring;

import server.agents.runtime.AgentTickSliceKind;
import server.agents.runtime.scheduler.AgentLoadSheddingLevel;
import server.agents.runtime.scheduler.AgentLoadSheddingReason;
import server.agents.runtime.scheduler.AgentLoadSheddingState;
import server.agents.runtime.scheduler.AgentPriorityClass;
import server.agents.runtime.scheduler.AgentWorkClass;
import server.agents.runtime.simulation.AgentSimulationMode;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class AgentSchedulerMetrics {
    public record Snapshot(long cycles,
                           long updatedAgents,
                           long skippedAgents,
                           long failedAgents,
                           long slowAgents,
                           long totalCycleNs,
                           long maxCycleNs,
                           long totalQueueLagMs,
                           long maxQueueLagMs,
                           long queueLagP50Ms,
                           long queueLagP95Ms,
                           long queueLagP99Ms,
                           long workDurationP50Ns,
                           long workDurationP95Ns,
                           long workDurationP99Ns,
                           long budgetExhaustions,
                           long deferredWork,
                           long starvationPromotions,
                           long mapBudgetDeferrals,
                           long tickContinuations,
                           long ingressDepth,
                           long ingressHighWaterMark,
                           long dueHeapDepth,
                           long readyDepth) {
    }

    public record WorkClassSnapshot(long durationP50Ns,
                                    long durationP95Ns,
                                    long durationP99Ns,
                                    int sampleCount) {
    }

    public record SimulationModeSnapshot(long durationP50Ns,
                                         long durationP95Ns,
                                         long durationP99Ns,
                                         int sampleCount) {
    }

    public record TickSliceSnapshot(long durationP50Ns,
                                    long durationP95Ns,
                                    long durationP99Ns,
                                    int sampleCount) {
    }

    public record ShardSnapshot(int registrations,
                                int ingressDepth,
                                int dueHeapDepth,
                                int readyDepth,
                                Map<AgentPriorityClass, PrioritySnapshot> readyPriorities) {
        public ShardSnapshot {
            readyPriorities = Map.copyOf(readyPriorities);
        }
    }

    public record PrioritySnapshot(long readyDepth,
                                   long readyHighWaterMark) {
    }

    public record LoadSheddingSnapshot(Map<Integer, AgentLoadSheddingState> shardStates,
                                       long transitions,
                                       long suppressedWork,
                                       long rejectedAdmissions,
                                       Map<AgentLoadSheddingReason, Long> suppressedByReason,
                                       Map<AgentLoadSheddingReason, Long> rejectedAdmissionsByReason) {
    }

    public record QuiescenceSnapshot(long requested,
                                     long completed,
                                     long timedOut,
                                     long cancelled,
                                     long resumed,
                                     long durationP50Ms,
                                     long durationP95Ms,
                                     long durationP99Ms,
                                     int sampleCount) {
    }

    public record LifecycleSnapshot(long registered,
                                    long replaced,
                                    long cancellationRequests,
                                    long cleanedUp) {
    }

    private static final int ROLLING_WINDOW_CAPACITY = 2_048;
    private static final LongAdder CYCLES = new LongAdder();
    private static final LongAdder UPDATED = new LongAdder();
    private static final LongAdder SKIPPED = new LongAdder();
    private static final LongAdder FAILED = new LongAdder();
    private static final LongAdder SLOW = new LongAdder();
    private static final LongAdder TOTAL_CYCLE_NS = new LongAdder();
    private static final AtomicLong MAX_CYCLE_NS = new AtomicLong();
    private static final LongAdder TOTAL_QUEUE_LAG_MS = new LongAdder();
    private static final AtomicLong MAX_QUEUE_LAG_MS = new AtomicLong();
    private static final LongAdder BUDGET_EXHAUSTIONS = new LongAdder();
    private static final LongAdder DEFERRED_WORK = new LongAdder();
    private static final LongAdder STARVATION_PROMOTIONS = new LongAdder();
    private static final LongAdder MAP_BUDGET_DEFERRALS = new LongAdder();
    private static final LongAdder TICK_CONTINUATIONS = new LongAdder();
    private static final LongAdder LOAD_SHEDDING_TRANSITIONS = new LongAdder();
    private static final LongAdder LOAD_SHEDDING_SUPPRESSED = new LongAdder();
    private static final LongAdder AGENT_ADMISSIONS_REJECTED = new LongAdder();
    private static final LongAdder QUIESCENCE_REQUESTED = new LongAdder();
    private static final LongAdder QUIESCENCE_COMPLETED = new LongAdder();
    private static final LongAdder QUIESCENCE_TIMED_OUT = new LongAdder();
    private static final LongAdder QUIESCENCE_CANCELLED = new LongAdder();
    private static final LongAdder QUIESCENCE_RESUMED = new LongAdder();
    private static final LongAdder LIFECYCLE_REGISTERED = new LongAdder();
    private static final LongAdder LIFECYCLE_REPLACED = new LongAdder();
    private static final LongAdder LIFECYCLE_CANCELLATION_REQUESTS = new LongAdder();
    private static final LongAdder LIFECYCLE_CLEANED_UP = new LongAdder();
    private static final AtomicLong INGRESS_DEPTH = new AtomicLong();
    private static final AtomicLong INGRESS_HIGH_WATER_MARK = new AtomicLong();
    private static final AtomicLong DUE_HEAP_DEPTH = new AtomicLong();
    private static final AtomicLong READY_DEPTH = new AtomicLong();
    private static final Map<AgentPriorityClass, AtomicLong> READY_PRIORITY_DEPTHS =
            new EnumMap<>(AgentPriorityClass.class);
    private static final Map<AgentPriorityClass, AtomicLong> READY_PRIORITY_HIGH_WATER_MARKS =
            new EnumMap<>(AgentPriorityClass.class);
    private static final AgentSchedulerRollingWindow QUEUE_LAG_WINDOW =
            new AgentSchedulerRollingWindow(ROLLING_WINDOW_CAPACITY);
    private static final AgentSchedulerRollingWindow WORK_DURATION_WINDOW =
            new AgentSchedulerRollingWindow(ROLLING_WINDOW_CAPACITY);
    private static final AgentSchedulerRollingWindow QUIESCENCE_DURATION_MS_WINDOW =
            new AgentSchedulerRollingWindow(ROLLING_WINDOW_CAPACITY);
    private static final Map<AgentWorkClass, AgentSchedulerRollingWindow> WORK_CLASS_WINDOWS =
            new EnumMap<>(AgentWorkClass.class);
    private static final Map<AgentSimulationMode, AgentSchedulerRollingWindow> SIMULATION_MODE_WINDOWS =
            new EnumMap<>(AgentSimulationMode.class);
    private static final Map<AgentTickSliceKind, AgentSchedulerRollingWindow> TICK_SLICE_WINDOWS =
            new EnumMap<>(AgentTickSliceKind.class);
    private static final Map<Integer, ShardSnapshot> SHARD_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<Integer, AgentLoadSheddingState> LOAD_SHEDDING_STATES = new ConcurrentHashMap<>();
    private static final Map<AgentLoadSheddingReason, LongAdder> SUPPRESSED_BY_REASON =
            new EnumMap<>(AgentLoadSheddingReason.class);
    private static final Map<AgentLoadSheddingReason, LongAdder> ADMISSION_REJECTIONS_BY_REASON =
            new EnumMap<>(AgentLoadSheddingReason.class);

    static {
        for (AgentWorkClass workClass : AgentWorkClass.values()) {
            WORK_CLASS_WINDOWS.put(workClass, new AgentSchedulerRollingWindow(ROLLING_WINDOW_CAPACITY));
        }
        for (AgentPriorityClass priority : AgentPriorityClass.values()) {
            READY_PRIORITY_DEPTHS.put(priority, new AtomicLong());
            READY_PRIORITY_HIGH_WATER_MARKS.put(priority, new AtomicLong());
        }
        for (AgentSimulationMode mode : AgentSimulationMode.values()) {
            SIMULATION_MODE_WINDOWS.put(mode, new AgentSchedulerRollingWindow(ROLLING_WINDOW_CAPACITY));
        }
        for (AgentTickSliceKind slice : AgentTickSliceKind.values()) {
            TICK_SLICE_WINDOWS.put(slice, new AgentSchedulerRollingWindow(ROLLING_WINDOW_CAPACITY));
        }
        for (AgentLoadSheddingReason reason : AgentLoadSheddingReason.values()) {
            SUPPRESSED_BY_REASON.put(reason, new LongAdder());
            ADMISSION_REJECTIONS_BY_REASON.put(reason, new LongAdder());
        }
    }

    private AgentSchedulerMetrics() {
    }

    public static void recordCycle(long elapsedNs) {
        CYCLES.increment();
        TOTAL_CYCLE_NS.add(Math.max(0L, elapsedNs));
        MAX_CYCLE_NS.accumulateAndGet(Math.max(0L, elapsedNs), Math::max);
    }

    public static void recordUpdated(long queueLagMs, boolean slow) {
        recordUpdated(queueLagMs, 0L, AgentWorkClass.MAINTENANCE, slow);
    }

    public static void recordUpdated(long queueLagMs,
                                     long elapsedNs,
                                     AgentWorkClass workClass,
                                     boolean slow) {
        recordUpdated(queueLagMs, elapsedNs, workClass, AgentSimulationMode.PRESENTATION, slow);
    }

    public static void recordUpdated(long queueLagMs,
                                     long elapsedNs,
                                     AgentWorkClass workClass,
                                     AgentSimulationMode simulationMode,
                                     boolean slow) {
        UPDATED.increment();
        long lag = Math.max(0L, queueLagMs);
        TOTAL_QUEUE_LAG_MS.add(lag);
        MAX_QUEUE_LAG_MS.accumulateAndGet(lag, Math::max);
        QUEUE_LAG_WINDOW.add(lag);
        long duration = Math.max(0L, elapsedNs);
        WORK_DURATION_WINDOW.add(duration);
        WORK_CLASS_WINDOWS.get(workClass).add(duration);
        SIMULATION_MODE_WINDOWS.get(simulationMode).add(duration);
        if (slow) {
            SLOW.increment();
        }
    }

    public static void recordSkipped(long count) {
        if (count > 0) {
            SKIPPED.add(count);
        }
    }

    public static void recordFailure() {
        FAILED.increment();
    }

    public static void recordBudgetExhausted() {
        BUDGET_EXHAUSTIONS.increment();
    }

    public static void recordDeferred(long count) {
        if (count > 0L) {
            DEFERRED_WORK.add(count);
        }
    }

    public static void recordStarvationPromotion() {
        recordStarvationPromotions(1L);
    }

    public static void recordStarvationPromotions(long count) {
        if (count > 0L) {
            STARVATION_PROMOTIONS.add(count);
        }
    }

    public static void recordMapBudgetDeferral() {
        MAP_BUDGET_DEFERRALS.increment();
    }

    public static void recordTickSlice(AgentTickSliceKind slice, long elapsedNs) {
        TICK_SLICE_WINDOWS.get(slice).add(Math.max(0L, elapsedNs));
    }

    public static void recordTickContinuation() {
        TICK_CONTINUATIONS.increment();
    }

    public static void recordLoadSheddingTransition(int shardId,
                                                    AgentLoadSheddingLevel previous,
                                                    AgentLoadSheddingState state) {
        if (previous == null || state == null) {
            throw new IllegalArgumentException("Agent load-shedding transition is incomplete");
        }
        recordLoadSheddingState(shardId, state);
        if (previous != state.level()) {
            LOAD_SHEDDING_TRANSITIONS.increment();
        }
    }

    public static void recordLoadSheddingState(int shardId, AgentLoadSheddingState state) {
        if (state == null) {
            throw new IllegalArgumentException("Agent load-shedding state is required");
        }
        LOAD_SHEDDING_STATES.put(Math.max(0, shardId), state);
    }

    public static void clearLoadSheddingShard(int shardId) {
        LOAD_SHEDDING_STATES.remove(Math.max(0, shardId));
    }

    public static void recordLoadSheddingSuppressed(AgentLoadSheddingReason reason) {
        LOAD_SHEDDING_SUPPRESSED.increment();
        SUPPRESSED_BY_REASON.get(reason).increment();
    }

    public static void recordAgentAdmissionRejected(AgentLoadSheddingReason reason) {
        AGENT_ADMISSIONS_REJECTED.increment();
        ADMISSION_REJECTIONS_BY_REASON.get(reason).increment();
    }

    public static void recordQuiescenceRequested() {
        QUIESCENCE_REQUESTED.increment();
    }

    public static void recordQuiescenceCompleted(long durationMs) {
        QUIESCENCE_COMPLETED.increment();
        QUIESCENCE_DURATION_MS_WINDOW.add(Math.max(0L, durationMs));
    }

    public static void recordQuiescenceTimedOut() {
        QUIESCENCE_TIMED_OUT.increment();
    }

    public static void recordQuiescenceCancelled() {
        QUIESCENCE_CANCELLED.increment();
    }

    public static void recordQuiescenceResumed() {
        QUIESCENCE_RESUMED.increment();
    }

    public static void recordLifecycleRegistered() {
        LIFECYCLE_REGISTERED.increment();
    }

    public static void recordLifecycleReplaced(long count) {
        if (count > 0L) {
            LIFECYCLE_REPLACED.add(count);
        }
    }

    public static void recordLifecycleCancellationRequested() {
        LIFECYCLE_CANCELLATION_REQUESTS.increment();
    }

    public static void recordLifecycleCleanedUp() {
        LIFECYCLE_CLEANED_UP.increment();
    }

    public static void recordDepths(int ingressDepth,
                                    int ingressHighWaterMark,
                                    int dueHeapDepth,
                                    int readyDepth) {
        recordDepths(ingressDepth, ingressHighWaterMark, dueHeapDepth, readyDepth, Map.of());
    }

    public static void recordDepths(int ingressDepth,
                                    int ingressHighWaterMark,
                                    int dueHeapDepth,
                                    int readyDepth,
                                    Map<AgentPriorityClass, Integer> readyPriorityDepths) {
        INGRESS_DEPTH.set(Math.max(0, ingressDepth));
        INGRESS_HIGH_WATER_MARK.accumulateAndGet(Math.max(0, ingressHighWaterMark), Math::max);
        DUE_HEAP_DEPTH.set(Math.max(0, dueHeapDepth));
        READY_DEPTH.set(Math.max(0, readyDepth));
        recordReadyPriorityDepths(readyPriorityDepths);
    }

    public static void recordShardDepths(int shardId,
                                         int registrations,
                                         int ingressDepth,
                                         int dueHeapDepth,
                                         int readyDepth) {
        recordShardDepths(shardId, registrations, ingressDepth, dueHeapDepth, readyDepth, Map.of());
    }

    public static void recordShardDepths(int shardId,
                                         int registrations,
                                         int ingressDepth,
                                         int dueHeapDepth,
                                         int readyDepth,
                                         Map<AgentPriorityClass, Integer> readyPriorityDepths) {
        int normalizedShardId = Math.max(0, shardId);
        ShardSnapshot previous = SHARD_SNAPSHOTS.get(normalizedShardId);
        Map<AgentPriorityClass, PrioritySnapshot> priorities = new EnumMap<>(AgentPriorityClass.class);
        for (AgentPriorityClass priority : AgentPriorityClass.values()) {
            long depth = Math.max(0, readyPriorityDepths.getOrDefault(priority, 0));
            PrioritySnapshot previousPriority = previous == null
                    ? null
                    : previous.readyPriorities().get(priority);
            long previousHigh = previousPriority == null ? 0L : previousPriority.readyHighWaterMark();
            priorities.put(priority, new PrioritySnapshot(depth, Math.max(depth, previousHigh)));
        }
        SHARD_SNAPSHOTS.put(
                normalizedShardId,
                new ShardSnapshot(
                        Math.max(0, registrations),
                        Math.max(0, ingressDepth),
                        Math.max(0, dueHeapDepth),
                        Math.max(0, readyDepth),
                        priorities));
        long aggregateIngressDepth = SHARD_SNAPSHOTS.values().stream()
                .mapToLong(ShardSnapshot::ingressDepth)
                .sum();
        INGRESS_DEPTH.set(aggregateIngressDepth);
        INGRESS_HIGH_WATER_MARK.accumulateAndGet(aggregateIngressDepth, Math::max);
        DUE_HEAP_DEPTH.set(SHARD_SNAPSHOTS.values().stream()
                .mapToLong(ShardSnapshot::dueHeapDepth)
                .sum());
        READY_DEPTH.set(SHARD_SNAPSHOTS.values().stream()
                .mapToLong(ShardSnapshot::readyDepth)
                .sum());
        Map<AgentPriorityClass, Integer> aggregatePriorities = new EnumMap<>(AgentPriorityClass.class);
        for (AgentPriorityClass priority : AgentPriorityClass.values()) {
            int depth = SHARD_SNAPSHOTS.values().stream()
                    .mapToInt(snapshot -> (int) snapshot.readyPriorities()
                            .getOrDefault(priority, new PrioritySnapshot(0L, 0L)).readyDepth())
                    .sum();
            aggregatePriorities.put(priority, depth);
        }
        recordReadyPriorityDepths(aggregatePriorities);
    }

    public static Map<Integer, ShardSnapshot> shardSnapshots() {
        return Map.copyOf(SHARD_SNAPSHOTS);
    }

    public static int shardRegistrationImbalance() {
        if (SHARD_SNAPSHOTS.isEmpty()) {
            return 0;
        }
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (ShardSnapshot snapshot : SHARD_SNAPSHOTS.values()) {
            minimum = Math.min(minimum, snapshot.registrations());
            maximum = Math.max(maximum, snapshot.registrations());
        }
        return maximum - minimum;
    }

    public static Map<AgentPriorityClass, PrioritySnapshot> readyPrioritySnapshots() {
        Map<AgentPriorityClass, PrioritySnapshot> snapshots = new EnumMap<>(AgentPriorityClass.class);
        for (AgentPriorityClass priority : AgentPriorityClass.values()) {
            snapshots.put(priority, new PrioritySnapshot(
                    READY_PRIORITY_DEPTHS.get(priority).get(),
                    READY_PRIORITY_HIGH_WATER_MARKS.get(priority).get()));
        }
        return Map.copyOf(snapshots);
    }

    public static Snapshot snapshot() {
        AgentSchedulerRollingWindow.Percentiles queueLag = QUEUE_LAG_WINDOW.percentiles();
        AgentSchedulerRollingWindow.Percentiles workDuration = WORK_DURATION_WINDOW.percentiles();
        return new Snapshot(
                CYCLES.sum(), UPDATED.sum(), SKIPPED.sum(), FAILED.sum(), SLOW.sum(),
                TOTAL_CYCLE_NS.sum(), MAX_CYCLE_NS.get(),
                TOTAL_QUEUE_LAG_MS.sum(), MAX_QUEUE_LAG_MS.get(),
                queueLag.p50(), queueLag.p95(), queueLag.p99(),
                workDuration.p50(), workDuration.p95(), workDuration.p99(),
                BUDGET_EXHAUSTIONS.sum(), DEFERRED_WORK.sum(), STARVATION_PROMOTIONS.sum(),
                MAP_BUDGET_DEFERRALS.sum(), TICK_CONTINUATIONS.sum(),
                INGRESS_DEPTH.get(), INGRESS_HIGH_WATER_MARK.get(),
                DUE_HEAP_DEPTH.get(), READY_DEPTH.get());
    }

    public static WorkClassSnapshot workClassSnapshot(AgentWorkClass workClass) {
        AgentSchedulerRollingWindow.Percentiles duration = WORK_CLASS_WINDOWS.get(workClass).percentiles();
        return new WorkClassSnapshot(duration.p50(), duration.p95(), duration.p99(), duration.sampleCount());
    }

    public static SimulationModeSnapshot simulationModeSnapshot(AgentSimulationMode mode) {
        AgentSchedulerRollingWindow.Percentiles duration = SIMULATION_MODE_WINDOWS.get(mode).percentiles();
        return new SimulationModeSnapshot(duration.p50(), duration.p95(), duration.p99(), duration.sampleCount());
    }

    public static TickSliceSnapshot tickSliceSnapshot(AgentTickSliceKind slice) {
        AgentSchedulerRollingWindow.Percentiles duration = TICK_SLICE_WINDOWS.get(slice).percentiles();
        return new TickSliceSnapshot(duration.p50(), duration.p95(), duration.p99(), duration.sampleCount());
    }

    public static LoadSheddingSnapshot loadSheddingSnapshot() {
        return new LoadSheddingSnapshot(
                Map.copyOf(LOAD_SHEDDING_STATES),
                LOAD_SHEDDING_TRANSITIONS.sum(),
                LOAD_SHEDDING_SUPPRESSED.sum(),
                AGENT_ADMISSIONS_REJECTED.sum(),
                reasonCounts(SUPPRESSED_BY_REASON),
                reasonCounts(ADMISSION_REJECTIONS_BY_REASON));
    }

    public static QuiescenceSnapshot quiescenceSnapshot() {
        AgentSchedulerRollingWindow.Percentiles duration = QUIESCENCE_DURATION_MS_WINDOW.percentiles();
        return new QuiescenceSnapshot(
                QUIESCENCE_REQUESTED.sum(),
                QUIESCENCE_COMPLETED.sum(),
                QUIESCENCE_TIMED_OUT.sum(),
                QUIESCENCE_CANCELLED.sum(),
                QUIESCENCE_RESUMED.sum(),
                duration.p50(),
                duration.p95(),
                duration.p99(),
                duration.sampleCount());
    }

    public static LifecycleSnapshot lifecycleSnapshot() {
        return new LifecycleSnapshot(
                LIFECYCLE_REGISTERED.sum(),
                LIFECYCLE_REPLACED.sum(),
                LIFECYCLE_CANCELLATION_REQUESTS.sum(),
                LIFECYCLE_CLEANED_UP.sum());
    }

    static void reset() {
        CYCLES.reset();
        UPDATED.reset();
        SKIPPED.reset();
        FAILED.reset();
        SLOW.reset();
        TOTAL_CYCLE_NS.reset();
        MAX_CYCLE_NS.set(0L);
        TOTAL_QUEUE_LAG_MS.reset();
        MAX_QUEUE_LAG_MS.set(0L);
        BUDGET_EXHAUSTIONS.reset();
        DEFERRED_WORK.reset();
        STARVATION_PROMOTIONS.reset();
        MAP_BUDGET_DEFERRALS.reset();
        TICK_CONTINUATIONS.reset();
        LOAD_SHEDDING_TRANSITIONS.reset();
        LOAD_SHEDDING_SUPPRESSED.reset();
        AGENT_ADMISSIONS_REJECTED.reset();
        QUIESCENCE_REQUESTED.reset();
        QUIESCENCE_COMPLETED.reset();
        QUIESCENCE_TIMED_OUT.reset();
        QUIESCENCE_CANCELLED.reset();
        QUIESCENCE_RESUMED.reset();
        LIFECYCLE_REGISTERED.reset();
        LIFECYCLE_REPLACED.reset();
        LIFECYCLE_CANCELLATION_REQUESTS.reset();
        LIFECYCLE_CLEANED_UP.reset();
        INGRESS_DEPTH.set(0L);
        INGRESS_HIGH_WATER_MARK.set(0L);
        DUE_HEAP_DEPTH.set(0L);
        READY_DEPTH.set(0L);
        READY_PRIORITY_DEPTHS.values().forEach(depth -> depth.set(0L));
        READY_PRIORITY_HIGH_WATER_MARKS.values().forEach(depth -> depth.set(0L));
        QUEUE_LAG_WINDOW.reset();
        WORK_DURATION_WINDOW.reset();
        QUIESCENCE_DURATION_MS_WINDOW.reset();
        WORK_CLASS_WINDOWS.values().forEach(AgentSchedulerRollingWindow::reset);
        SIMULATION_MODE_WINDOWS.values().forEach(AgentSchedulerRollingWindow::reset);
        TICK_SLICE_WINDOWS.values().forEach(AgentSchedulerRollingWindow::reset);
        SUPPRESSED_BY_REASON.values().forEach(LongAdder::reset);
        ADMISSION_REJECTIONS_BY_REASON.values().forEach(LongAdder::reset);
        SHARD_SNAPSHOTS.clear();
        LOAD_SHEDDING_STATES.clear();
    }

    private static Map<AgentLoadSheddingReason, Long> reasonCounts(
            Map<AgentLoadSheddingReason, LongAdder> counters) {
        Map<AgentLoadSheddingReason, Long> counts = new EnumMap<>(AgentLoadSheddingReason.class);
        counters.forEach((reason, counter) -> counts.put(reason, counter.sum()));
        return Map.copyOf(counts);
    }

    private static void recordReadyPriorityDepths(Map<AgentPriorityClass, Integer> readyPriorityDepths) {
        for (AgentPriorityClass priority : AgentPriorityClass.values()) {
            long depth = Math.max(0, readyPriorityDepths.getOrDefault(priority, 0));
            READY_PRIORITY_DEPTHS.get(priority).set(depth);
            READY_PRIORITY_HIGH_WATER_MARKS.get(priority).accumulateAndGet(depth, Math::max);
        }
    }
}
