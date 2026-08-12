package server.agents.capabilities.navigation;

import config.AgentTuning;

/** Configuration for optional per-Agent edge validation, suppression, and route costs. */
public final class AgentNavigationReliabilityConfig {
    private static final String PREFIX =
            "server.agents.capabilities.navigation.AgentNavigationReliabilityConfig.";

    private AgentNavigationReliabilityConfig() {
    }

    public static boolean edgeValidationEnabled() { return bool("EDGE_VALIDATION_ENABLED"); }
    public static boolean edgeSuppressionEnabled() { return bool("EDGE_SUPPRESSION_ENABLED"); }
    public static boolean routePenaltiesEnabled() { return bool("ROUTE_PENALTIES_ENABLED"); }
    public static int failureThreshold() { return integer("FAILURE_THRESHOLD"); }
    public static long suppressionMs() { return longValue("SUPPRESSION_MS"); }
    public static long failureRetentionMs() { return longValue("FAILURE_RETENTION_MS"); }
    public static int failurePenaltyMs() { return integer("FAILURE_PENALTY_MS"); }
    public static int maxEdgePenaltyMs() { return integer("MAX_EDGE_PENALTY_MS"); }
    public static int maxTrackedEdges() { return integer("MAX_TRACKED_EDGES"); }
    public static long attemptTimeoutMs() { return longValue("ATTEMPT_TIMEOUT_MS"); }
    public static int progressTolerancePx() { return integer("PROGRESS_TOLERANCE_PX"); }
    public static int launchTolerancePx() { return integer("LAUNCH_TOLERANCE_PX"); }
    public static int landingTolerancePx() { return integer("LANDING_TOLERANCE_PX"); }
    public static int attachmentTolerancePx() { return integer("ATTACHMENT_TOLERANCE_PX"); }

    public static boolean tracksReliability() {
        return edgeSuppressionEnabled() || routePenaltiesEnabled();
    }

    private static boolean bool(String key) { return AgentTuning.booleanValue(PREFIX + key); }
    private static int integer(String key) { return AgentTuning.intValue(PREFIX + key); }
    private static long longValue(String key) { return AgentTuning.longValue(PREFIX + key); }
}
