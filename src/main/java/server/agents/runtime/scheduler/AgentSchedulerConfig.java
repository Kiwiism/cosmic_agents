package server.agents.runtime.scheduler;

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
    public AgentSchedulerConfig {
        if (mode == null) {
            throw new IllegalArgumentException("Agent scheduler mode is required");
        }
        if (baseTickMs < 10L) {
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
        if (shardCount < 1 || shardCount > 64) {
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
        if (maxContinuationsPerFrame < 1 || maxContinuationsPerFrame > 64) {
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
                Math.max(baseTickMs, 250L),
                Math.max(baseTickMs, 5_000L),
                32,
                false,
                2,
                8);
    }

    public static AgentSchedulerConfig fromSystemProperties() {
        long baseTickMs = longProperty("agents.scheduler.baseTickMs", 50L);
        long backgroundActiveTickMs = longProperty(
                "agents.scheduler.simulation.backgroundActiveTickMs",
                Math.max(baseTickMs, 250L));
        return new AgentSchedulerConfig(
                configuredMode(),
                baseTickMs,
                Boolean.parseBoolean(System.getProperty("agents.scheduler.logSlowTicks", "true")),
                longProperty("agents.scheduler.slowTickMs", 250L),
                intProperty("agents.scheduler.maxAgentsPerTick", 0),
                intProperty("agents.scheduler.ingressCapacityPerShard", 4096),
                longProperty("agents.scheduler.cycleBudgetMs", 10L),
                intProperty("agents.scheduler.maxWorkItemsPerCycle", 256),
                intProperty("agents.scheduler.visibleReservePercent", 40),
                intProperty("agents.scheduler.criticalReservePercent", 10),
                longProperty("agents.scheduler.starvationPromotionMs", 2_000L),
                intProperty("agents.scheduler.shardCount", defaultShardCount()),
                Boolean.parseBoolean(System.getProperty("agents.scheduler.simulation.enabled", "false")),
                Boolean.parseBoolean(System.getProperty(
                        "agents.scheduler.simulation.backgroundAbstract.enabled", "false")),
                backgroundActiveTickMs,
                longProperty(
                        "agents.scheduler.simulation.backgroundAbstractHeartbeatMs",
                        Math.max(backgroundActiveTickMs, 5_000L)),
                intProperty("agents.scheduler.simulation.backgroundMaxWorkPerMapPerCycle", 32),
                Boolean.parseBoolean(System.getProperty("agents.scheduler.tickSlicing.enabled", "false")),
                intProperty("agents.scheduler.tickSlicing.maxSlicesPerTurn", 2),
                intProperty("agents.scheduler.tickSlicing.maxContinuationsPerFrame", 8));
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
        return Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 4);
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
}
