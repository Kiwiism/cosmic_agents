package server.agents.monitoring;

import server.agents.runtime.simulation.AgentSimulationMode;
import server.agents.runtime.scheduler.AgentWorkClass;

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

    public record ShardSnapshot(int registrations,
                                int ingressDepth,
                                int dueHeapDepth,
                                int readyDepth) {
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
    private static final AtomicLong INGRESS_DEPTH = new AtomicLong();
    private static final AtomicLong INGRESS_HIGH_WATER_MARK = new AtomicLong();
    private static final AtomicLong DUE_HEAP_DEPTH = new AtomicLong();
    private static final AtomicLong READY_DEPTH = new AtomicLong();
    private static final AgentSchedulerRollingWindow QUEUE_LAG_WINDOW =
            new AgentSchedulerRollingWindow(ROLLING_WINDOW_CAPACITY);
    private static final AgentSchedulerRollingWindow WORK_DURATION_WINDOW =
            new AgentSchedulerRollingWindow(ROLLING_WINDOW_CAPACITY);
    private static final Map<AgentWorkClass, AgentSchedulerRollingWindow> WORK_CLASS_WINDOWS =
            new EnumMap<>(AgentWorkClass.class);
    private static final Map<AgentSimulationMode, AgentSchedulerRollingWindow> SIMULATION_MODE_WINDOWS =
            new EnumMap<>(AgentSimulationMode.class);
    private static final Map<Integer, ShardSnapshot> SHARD_SNAPSHOTS = new ConcurrentHashMap<>();

    static {
        for (AgentWorkClass workClass : AgentWorkClass.values()) {
            WORK_CLASS_WINDOWS.put(workClass, new AgentSchedulerRollingWindow(ROLLING_WINDOW_CAPACITY));
        }
        for (AgentSimulationMode mode : AgentSimulationMode.values()) {
            SIMULATION_MODE_WINDOWS.put(mode, new AgentSchedulerRollingWindow(ROLLING_WINDOW_CAPACITY));
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

    public static void recordDepths(int ingressDepth,
                                    int ingressHighWaterMark,
                                    int dueHeapDepth,
                                    int readyDepth) {
        INGRESS_DEPTH.set(Math.max(0, ingressDepth));
        INGRESS_HIGH_WATER_MARK.accumulateAndGet(Math.max(0, ingressHighWaterMark), Math::max);
        DUE_HEAP_DEPTH.set(Math.max(0, dueHeapDepth));
        READY_DEPTH.set(Math.max(0, readyDepth));
    }

    public static void recordShardDepths(int shardId,
                                         int registrations,
                                         int ingressDepth,
                                         int dueHeapDepth,
                                         int readyDepth) {
        SHARD_SNAPSHOTS.put(
                Math.max(0, shardId),
                new ShardSnapshot(
                        Math.max(0, registrations),
                        Math.max(0, ingressDepth),
                        Math.max(0, dueHeapDepth),
                        Math.max(0, readyDepth)));
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
                MAP_BUDGET_DEFERRALS.sum(),
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
        INGRESS_DEPTH.set(0L);
        INGRESS_HIGH_WATER_MARK.set(0L);
        DUE_HEAP_DEPTH.set(0L);
        READY_DEPTH.set(0L);
        QUEUE_LAG_WINDOW.reset();
        WORK_DURATION_WINDOW.reset();
        WORK_CLASS_WINDOWS.values().forEach(AgentSchedulerRollingWindow::reset);
        SIMULATION_MODE_WINDOWS.values().forEach(AgentSchedulerRollingWindow::reset);
        SHARD_SNAPSHOTS.clear();
    }
}
