package server.agents.capabilities.navigation;

/**
 * Run-scoped presentation controls for the scripted Maple Island cohort.
 * Disabled settings preserve the normal navigation and movement behavior.
 */
public record AgentMapleIslandTravelSettings(
        long seed,
        boolean routeVariationEnabled,
        double maxRouteStretch,
        boolean travelHopsEnabled,
        double travelHopProbability,
        long travelHopDecisionIntervalMs,
        long travelHopCooldownMs) {
    private static final AgentMapleIslandTravelSettings DISABLED =
            new AgentMapleIslandTravelSettings(0L, false, 1.0d, false, 0.0d, 1_000L, 0L);

    public AgentMapleIslandTravelSettings {
        if (!Double.isFinite(maxRouteStretch) || maxRouteStretch < 1.0d || maxRouteStretch > 2.0d) {
            throw new IllegalArgumentException("maxRouteStretch must be between 1.0 and 2.0");
        }
        if (!Double.isFinite(travelHopProbability)
                || travelHopProbability < 0.0d || travelHopProbability > 1.0d) {
            throw new IllegalArgumentException("travelHopProbability must be between 0.0 and 1.0");
        }
        if (travelHopDecisionIntervalMs <= 0L) {
            throw new IllegalArgumentException("travelHopDecisionIntervalMs must be positive");
        }
        if (travelHopCooldownMs < 0L) {
            throw new IllegalArgumentException("travelHopCooldownMs must be non-negative");
        }
    }

    public static AgentMapleIslandTravelSettings disabled() {
        return DISABLED;
    }
}
