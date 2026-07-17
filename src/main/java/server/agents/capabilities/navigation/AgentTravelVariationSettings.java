package server.agents.capabilities.navigation;

/** Plan-neutral route and incidental-hop variation. */
public record AgentTravelVariationSettings(long seed, boolean routeVariationEnabled,
        double maxRouteStretch, boolean travelHopsEnabled, double travelHopProbability,
        long travelHopDecisionIntervalMs, long travelHopCooldownMs) {
    private static final AgentTravelVariationSettings DISABLED =
            new AgentTravelVariationSettings(0L, false, 1.0d, false, 0.0d, 1_000L, 0L);
    public AgentTravelVariationSettings {
        if (!Double.isFinite(maxRouteStretch) || maxRouteStretch < 1.0d || maxRouteStretch > 2.0d)
            throw new IllegalArgumentException("maxRouteStretch must be between 1.0 and 2.0");
        if (!Double.isFinite(travelHopProbability) || travelHopProbability < 0.0d || travelHopProbability > 1.0d)
            throw new IllegalArgumentException("travelHopProbability must be between 0.0 and 1.0");
        if (travelHopDecisionIntervalMs <= 0L || travelHopCooldownMs < 0L)
            throw new IllegalArgumentException("travel timing values are invalid");
    }
    public static AgentTravelVariationSettings disabled() { return DISABLED; }
}
