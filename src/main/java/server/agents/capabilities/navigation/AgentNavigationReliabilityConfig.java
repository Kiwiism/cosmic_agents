package server.agents.capabilities.navigation;

/** Configuration for per-Agent edge observation and reliability-aware routing. */
public final class AgentNavigationReliabilityConfig {
    private static final String TUNING_PREFIX =
            "server.agents.capabilities.navigation.AgentNavigationReliabilityConfig.";
    private static final AgentNavigationReliabilityMode ROUTING_MODE =
            AgentNavigationReliabilityMode.parse(
                    config.AgentTuning.stringValue(
                            "server.agents.capabilities.navigation.AgentNavigationReliabilityConfig.ROUTING_MODE"));

    private AgentNavigationReliabilityConfig() {
    }

    public static AgentNavigationReliabilityMode routingMode() { return ROUTING_MODE; }
    public static int failureThreshold() { return tuningInt("FAILURE_THRESHOLD"); }
    public static long suppressionMs() { return tuningLong("SUPPRESSION_MS"); }
    public static long failureRetentionMs() { return tuningLong("FAILURE_RETENTION_MS"); }
    public static int failurePenaltyMs() { return tuningInt("FAILURE_PENALTY_MS"); }
    public static int maxEdgePenaltyMs() { return tuningInt("MAX_EDGE_PENALTY_MS"); }
    public static int maxTrackedEdges() { return tuningInt("MAX_TRACKED_EDGES"); }
    public static long attemptTimeoutMs() { return tuningLong("ATTEMPT_TIMEOUT_MS"); }
    public static int progressTolerancePx() { return tuningInt("PROGRESS_TOLERANCE_PX"); }
    public static int launchTolerancePx() { return tuningInt("LAUNCH_TOLERANCE_PX"); }
    public static int landingTolerancePx() { return tuningInt("LANDING_TOLERANCE_PX"); }
    public static int attachmentTolerancePx() { return tuningInt("ATTACHMENT_TOLERANCE_PX"); }

    public static boolean tracksReliability() {
        return true;
    }

    public static boolean influencesRouting() {
        return ROUTING_MODE == AgentNavigationReliabilityMode.ACTIVE;
    }

    private static int tuningInt(String key) {
        return config.AgentTuning.intValue(TUNING_PREFIX + key);
    }

    private static long tuningLong(String key) {
        return config.AgentTuning.longValue(TUNING_PREFIX + key);
    }
}
