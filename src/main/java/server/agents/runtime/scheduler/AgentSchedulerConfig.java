package server.agents.runtime.scheduler;

import config.AgentTuning;
import server.agents.runtime.AgentTickSliceKind;

import java.util.Locale;

public record AgentSchedulerConfig(
        AgentSchedulerMode mode,
        long baseTickMs,
        boolean logSlowTicks,
        long slowTickMs,
        int maxAgentsPerTick,
        int ingressCapacityPerShard,
        long cycleBudgetMs,
        int maxWorkItemsPerCycle,
        int visibleReservePercent,
        int criticalReservePercent,
        long starvationPromotionMs,
        int shardCount,
        boolean simulationEnabled,
        boolean backgroundAbstractEnabled,
        long backgroundActiveTickMs,
        long backgroundAbstractHeartbeatMs,
        int backgroundMaxWorkPerMapPerCycle,
        boolean tickSlicingEnabled,
        int maxSlicesPerTurn,
        int maxContinuationsPerFrame) {
    private static final String TUNING_PREFIX =
            "server.agents.runtime.scheduler.AgentSchedulerConfig.";

    public AgentSchedulerConfig {
        if (mode == null) {
            throw new IllegalArgumentException("Agent scheduler mode is required");
        }
        if (baseTickMs < tuningLong("MIN_BASE_TICK_MS")) {
            throw new IllegalArgumentException("Agent scheduler baseTickMs must be at least 10");
        }
        if (slowTickMs < 1L) {
            throw new IllegalArgumentException("Agent scheduler slowTickMs must be positive");
        }
        if (maxAgentsPerTick < 0) {
            throw new IllegalArgumentException("Agent scheduler maxAgentsPerTick must not be negative");
        }
        if (ingressCapacityPerShard < 1) {
            throw new IllegalArgumentException("Agent scheduler ingressCapacityPerShard must be positive");
        }
        if (cycleBudgetMs < 1L) {
            throw new IllegalArgumentException("Agent scheduler cycleBudgetMs must be positive");
        }
        if (maxWorkItemsPerCycle < 1) {
            throw new IllegalArgumentException("Agent scheduler maxWorkItemsPerCycle must be positive");
        }
        if (visibleReservePercent < 0 || criticalReservePercent < 0
                || visibleReservePercent + criticalReservePercent > 100) {
            throw new IllegalArgumentException("Agent scheduler reserve percentages must total at most 100");
        }
        if (starvationPromotionMs < 1L) {
            throw new IllegalArgumentException("Agent scheduler starvationPromotionMs must be positive");
        }
        if (shardCount < 1 || shardCount > tuningInt("MAX_SHARD_COUNT")) {
            throw new IllegalArgumentException("Agent scheduler shardCount must be between 1 and 64");
        }
        if (backgroundActiveTickMs < baseTickMs) {
            throw new IllegalArgumentException("Agent background-active cadence must not be faster than baseTickMs");
        }
        if (backgroundAbstractHeartbeatMs < backgroundActiveTickMs) {
            throw new IllegalArgumentException(
                    "Agent background-abstract heartbeat must not be faster than background-active cadence");
        }
        if (backgroundMaxWorkPerMapPerCycle < 0) {
            throw new IllegalArgumentException("Agent background map work limit must not be negative");
        }
        if (maxSlicesPerTurn < 1 || maxSlicesPerTurn > AgentTickSliceKind.values().length) {
            throw new IllegalArgumentException("Agent maxSlicesPerTurn is outside the bounded tick frame");
        }
        if (maxContinuationsPerFrame < 1
                || maxContinuationsPerFrame > tuningInt("MAX_CONTINUATIONS_PER_FRAME")) {
            throw new IllegalArgumentException("Agent maxContinuationsPerFrame must be between 1 and 64");
        }
    }

    public AgentSchedulerConfig(AgentSchedulerMode mode,
                                long baseTickMs,
                                boolean logSlowTicks,
                                long slowTickMs,
                                int maxAgentsPerTick,
                                int ingressCapacityPerShard,
                                long cycleBudgetMs,
                                int maxWorkItemsPerCycle,
                                int visibleReservePercent,
                                int criticalReservePercent,
                                long starvationPromotionMs,
                                int shardCount) {
        this(
                mode,
                baseTickMs,
                logSlowTicks,
                slowTickMs,
                maxAgentsPerTick,
                ingressCapacityPerShard,
                cycleBudgetMs,
                maxWorkItemsPerCycle,
                visibleReservePercent,
                criticalReservePercent,
                starvationPromotionMs,
                shardCount,
                false,
                false,
                Math.max(baseTickMs, tuningLong("DEFAULT_BACKGROUND_ACTIVE_TICK_MS")),
                Math.max(baseTickMs, tuningLong("DEFAULT_BACKGROUND_ABSTRACT_HEARTBEAT_MS")),
                tuningInt("DEFAULT_BACKGROUND_MAX_WORK_PER_MAP"),
                false,
                tuningInt("DEFAULT_MAX_SLICES_PER_TURN"),
                tuningInt("DEFAULT_MAX_CONTINUATIONS_PER_FRAME"));
    }

    public static AgentSchedulerConfig fromSystemProperties() {
        long baseTickMs = longProperty(
                "agents.scheduler.baseTickMs", tuningLong("DEFAULT_BASE_TICK_MS"));
        long backgroundActiveTickMs = longProperty(
                "agents.scheduler.simulation.backgroundActiveTickMs",
                Math.max(baseTickMs, tuningLong("DEFAULT_BACKGROUND_ACTIVE_TICK_MS")));
        return new AgentSchedulerConfig(
                configuredMode(),
                baseTickMs,
                Boolean.parseBoolean(System.getProperty(
                        "agents.scheduler.logSlowTicks",
                        Boolean.toString(tuningBoolean("DEFAULT_LOG_SLOW_TICKS")))),
                longProperty("agents.scheduler.slowTickMs", tuningLong("DEFAULT_SLOW_TICK_MS")),
                intProperty("agents.scheduler.maxAgentsPerTick", 0),
                intProperty(
                        "agents.scheduler.ingressCapacityPerShard",
                        tuningInt("DEFAULT_INGRESS_CAPACITY_PER_SHARD")),
                longProperty(
                        "agents.scheduler.cycleBudgetMs",
                        tuningLong("DEFAULT_CYCLE_BUDGET_MS")),
                intProperty(
                        "agents.scheduler.maxWorkItemsPerCycle",
                        tuningInt("DEFAULT_MAX_WORK_ITEMS_PER_CYCLE")),
                intProperty(
                        "agents.scheduler.visibleReservePercent",
                        tuningInt("DEFAULT_VISIBLE_RESERVE_PERCENT")),
                intProperty(
                        "agents.scheduler.criticalReservePercent",
                        tuningInt("DEFAULT_CRITICAL_RESERVE_PERCENT")),
                longProperty(
                        "agents.scheduler.starvationPromotionMs",
                        tuningLong("DEFAULT_STARVATION_PROMOTION_MS")),
                intProperty("agents.scheduler.shardCount", defaultShardCount()),
                Boolean.parseBoolean(System.getProperty(
                        "agents.scheduler.simulation.enabled",
                        Boolean.toString(tuningBoolean("DEFAULT_SIMULATION_ENABLED")))),
                Boolean.parseBoolean(System.getProperty(
                        "agents.scheduler.simulation.backgroundAbstract.enabled",
                        Boolean.toString(tuningBoolean("DEFAULT_BACKGROUND_ABSTRACT_ENABLED")))),
                backgroundActiveTickMs,
                longProperty(
                        "agents.scheduler.simulation.backgroundAbstractHeartbeatMs",
                        Math.max(
                                backgroundActiveTickMs,
                                tuningLong("DEFAULT_BACKGROUND_ABSTRACT_HEARTBEAT_MS"))),
                intProperty(
                        "agents.scheduler.simulation.backgroundMaxWorkPerMapPerCycle",
                        tuningInt("DEFAULT_BACKGROUND_MAX_WORK_PER_MAP")),
                Boolean.parseBoolean(System.getProperty(
                        "agents.scheduler.tickSlicing.enabled",
                        Boolean.toString(tuningBoolean("DEFAULT_TICK_SLICING_ENABLED")))),
                intProperty(
                        "agents.scheduler.tickSlicing.maxSlicesPerTurn",
                        tuningInt("DEFAULT_MAX_SLICES_PER_TURN")),
                intProperty(
                        "agents.scheduler.tickSlicing.maxContinuationsPerFrame",
                        tuningInt("DEFAULT_MAX_CONTINUATIONS_PER_FRAME")));
    }

    int effectiveMaxWorkItemsPerCycle() {
        return maxAgentsPerTick == 0
                ? maxWorkItemsPerCycle
                : Math.min(maxAgentsPerTick, maxWorkItemsPerCycle);
    }

    private static AgentSchedulerMode configuredMode() {
        String explicitMode = System.getProperty("agents.scheduler.mode");
        if (explicitMode != null && !explicitMode.isBlank()) {
            return parseMode(explicitMode);
        }
        return Boolean.getBoolean("agents.scheduler.central.enabled")
                ? AgentSchedulerMode.CENTRAL_SEQUENTIAL
                : AgentSchedulerMode.LEGACY_PER_AGENT;
    }

    static AgentSchedulerMode parseMode(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "legacy", "legacy-per-agent" -> AgentSchedulerMode.LEGACY_PER_AGENT;
            case "central", "central-sequential" -> AgentSchedulerMode.CENTRAL_SEQUENTIAL;
            case "central-sharded", "sharded" -> AgentSchedulerMode.CENTRAL_SHARDED;
            default -> throw new IllegalArgumentException("Unknown Agent scheduler mode: " + value);
        };
    }

    private static int defaultShardCount() {
        return Math.clamp(
                Runtime.getRuntime().availableProcessors()
                        / tuningInt("DEFAULT_SHARD_PROCESSOR_DIVISOR"),
                1,
                tuningInt("DEFAULT_SHARD_COUNT_MAX"));
    }

    private static long longProperty(String name, long defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid long Agent scheduler property " + name + ": " + value, failure);
        }
    }

    private static int intProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid integer Agent scheduler property " + name + ": " + value, failure);
        }
    }

    private static int tuningInt(String name) {
        return AgentTuning.intValue(TUNING_PREFIX + name);
    }

    private static long tuningLong(String name) {
        return AgentTuning.longValue(TUNING_PREFIX + name);
    }

    private static boolean tuningBoolean(String name) {
        return AgentTuning.booleanValue(TUNING_PREFIX + name);
    }
}
