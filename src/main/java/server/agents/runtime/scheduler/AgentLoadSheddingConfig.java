package server.agents.runtime.scheduler;

import config.AgentTuning;

public record AgentLoadSheddingConfig(
        boolean enabled,
        int pressureCycles,
        int recoveryCycles,
        long sampleIntervalMs,
        long queueLagLevel1Ms,
        int readyDepthLevel1,
        int ingressLevel2Percent,
        double cpuLevel3Percent,
        double heapLevel3Percent,
        long gcPauseLevel3Ms,
        int backgroundCadenceMultiplier,
        int maxActiveAgents) {
    private static final String TUNING_PREFIX =
            "server.agents.runtime.scheduler.AgentLoadSheddingConfig.";

    public AgentLoadSheddingConfig {
        if (pressureCycles < 1 || recoveryCycles < 1) {
            throw new IllegalArgumentException("Agent load-shedding hysteresis cycles must be positive");
        }
        if (sampleIntervalMs < 1 || queueLagLevel1Ms < 1 || readyDepthLevel1 < 1) {
            throw new IllegalArgumentException("Agent load-shedding queue thresholds must be positive");
        }
        if (ingressLevel2Percent < 1
                || ingressLevel2Percent > tuningInt("MAX_INGRESS_LEVEL2_PERCENT")) {
            throw new IllegalArgumentException("Agent ingress pressure threshold must be between 1 and 95");
        }
        if (cpuLevel3Percent <= 0 || cpuLevel3Percent > 100
                || heapLevel3Percent <= 0
                || heapLevel3Percent > tuningDouble("MAX_JVM_PERCENT")) {
            throw new IllegalArgumentException("Agent JVM pressure thresholds must be within 0-100");
        }
        if (gcPauseLevel3Ms < 1 || backgroundCadenceMultiplier < 1 || maxActiveAgents < 0) {
            throw new IllegalArgumentException("Agent load-shedding limits are invalid");
        }
    }

    public static AgentLoadSheddingConfig fromSystemProperties() {
        return new AgentLoadSheddingConfig(
                Boolean.parseBoolean(System.getProperty(
                        "agents.scheduler.loadShedding.enabled",
                        Boolean.toString(tuningBoolean("DEFAULT_ENABLED")))),
                positiveInteger(
                        "agents.scheduler.loadShedding.pressureCycles",
                        tuningInt("DEFAULT_PRESSURE_CYCLES")),
                positiveInteger(
                        "agents.scheduler.loadShedding.recoveryCycles",
                        tuningInt("DEFAULT_RECOVERY_CYCLES")),
                positiveLong(
                        "agents.scheduler.loadShedding.sampleIntervalMs",
                        tuningLong("DEFAULT_SAMPLE_INTERVAL_MS")),
                positiveLong(
                        "agents.scheduler.loadShedding.queueLagLevel1Ms",
                        tuningLong("DEFAULT_QUEUE_LAG_LEVEL1_MS")),
                positiveInteger(
                        "agents.scheduler.loadShedding.readyDepthLevel1",
                        tuningInt("DEFAULT_READY_DEPTH_LEVEL1")),
                positiveInteger(
                        "agents.scheduler.loadShedding.ingressLevel2Percent",
                        tuningInt("DEFAULT_INGRESS_LEVEL2_PERCENT")),
                positiveDouble(
                        "agents.scheduler.loadShedding.cpuLevel3Percent",
                        tuningDouble("DEFAULT_CPU_LEVEL3_PERCENT")),
                positiveDouble(
                        "agents.scheduler.loadShedding.heapLevel3Percent",
                        tuningDouble("DEFAULT_HEAP_LEVEL3_PERCENT")),
                positiveLong(
                        "agents.scheduler.loadShedding.gcPauseLevel3Ms",
                        tuningLong("DEFAULT_GC_PAUSE_LEVEL3_MS")),
                positiveInteger(
                        "agents.scheduler.loadShedding.backgroundCadenceMultiplier",
                        tuningInt("DEFAULT_BACKGROUND_CADENCE_MULTIPLIER")),
                nonNegativeInteger(
                        "agents.scheduler.loadShedding.maxActiveAgents",
                        tuningInt("DEFAULT_MAX_ACTIVE_AGENTS")));
    }

    private static int positiveInteger(String property, int fallback) {
        return Math.max(1, Integer.getInteger(property, fallback));
    }

    private static int nonNegativeInteger(String property, int fallback) {
        return Math.max(0, Integer.getInteger(property, fallback));
    }

    private static long positiveLong(String property, long fallback) {
        return Math.max(1L, Long.getLong(property, fallback));
    }

    private static double positiveDouble(String property, double fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(tuningDouble("MIN_POSITIVE_DOUBLE"), Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int tuningInt(String name) {
        return AgentTuning.intValue(TUNING_PREFIX + name);
    }

    private static long tuningLong(String name) {
        return AgentTuning.longValue(TUNING_PREFIX + name);
    }

    private static double tuningDouble(String name) {
        return AgentTuning.doubleValue(TUNING_PREFIX + name);
    }

    private static boolean tuningBoolean(String name) {
        return AgentTuning.booleanValue(TUNING_PREFIX + name);
    }
}
