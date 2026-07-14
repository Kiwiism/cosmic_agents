package server.agents.runtime.scheduler;

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
    public AgentLoadSheddingConfig {
        if (pressureCycles < 1 || recoveryCycles < 1) {
            throw new IllegalArgumentException("Agent load-shedding hysteresis cycles must be positive");
        }
        if (sampleIntervalMs < 1 || queueLagLevel1Ms < 1 || readyDepthLevel1 < 1) {
            throw new IllegalArgumentException("Agent load-shedding queue thresholds must be positive");
        }
        if (ingressLevel2Percent < 1 || ingressLevel2Percent > 95) {
            throw new IllegalArgumentException("Agent ingress pressure threshold must be between 1 and 95");
        }
        if (cpuLevel3Percent <= 0 || cpuLevel3Percent > 100
                || heapLevel3Percent <= 0 || heapLevel3Percent > 100) {
            throw new IllegalArgumentException("Agent JVM pressure thresholds must be within 0-100");
        }
        if (gcPauseLevel3Ms < 1 || backgroundCadenceMultiplier < 1 || maxActiveAgents < 0) {
            throw new IllegalArgumentException("Agent load-shedding limits are invalid");
        }
    }

    public static AgentLoadSheddingConfig fromSystemProperties() {
        return new AgentLoadSheddingConfig(
                Boolean.parseBoolean(System.getProperty("agents.scheduler.loadShedding.enabled", "false")),
                positiveInteger("agents.scheduler.loadShedding.pressureCycles", 3),
                positiveInteger("agents.scheduler.loadShedding.recoveryCycles", 20),
                positiveLong("agents.scheduler.loadShedding.sampleIntervalMs", 1_000L),
                positiveLong("agents.scheduler.loadShedding.queueLagLevel1Ms", 100L),
                positiveInteger("agents.scheduler.loadShedding.readyDepthLevel1", 256),
                positiveInteger("agents.scheduler.loadShedding.ingressLevel2Percent", 75),
                positiveDouble("agents.scheduler.loadShedding.cpuLevel3Percent", 85.0d),
                positiveDouble("agents.scheduler.loadShedding.heapLevel3Percent", 85.0d),
                positiveLong("agents.scheduler.loadShedding.gcPauseLevel3Ms", 250L),
                positiveInteger("agents.scheduler.loadShedding.backgroundCadenceMultiplier", 2),
                nonNegativeInteger("agents.scheduler.loadShedding.maxActiveAgents", 2_000));
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
            return Math.max(0.1d, Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
