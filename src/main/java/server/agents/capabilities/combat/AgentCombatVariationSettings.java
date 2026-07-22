package server.agents.capabilities.combat;

/** Plan-neutral, seeded variation for otherwise equivalent combat choices. */
public record AgentCombatVariationSettings(long seed,
                                           boolean targetSelectionVariationEnabled,
                                           double middleTargetProbability,
                                           int targetShortlistLimit,
                                           boolean platformAnchorEnabled,
                                           double platformAnchorProbability) {
    private static final AgentCombatVariationSettings DISABLED =
            new AgentCombatVariationSettings(0L, false, 0.0d, 10, false, 0.0d);

    public AgentCombatVariationSettings {
        if (!Double.isFinite(middleTargetProbability)
                || middleTargetProbability < 0.0d || middleTargetProbability > 1.0d) {
            throw new IllegalArgumentException("middleTargetProbability must be between 0.0 and 1.0");
        }
        if (targetShortlistLimit < 1 || targetShortlistLimit > 64) {
            throw new IllegalArgumentException("targetShortlistLimit must be between 1 and 64");
        }
        if (!Double.isFinite(platformAnchorProbability)
                || platformAnchorProbability < 0.0d || platformAnchorProbability > 1.0d) {
            throw new IllegalArgumentException("platformAnchorProbability must be between 0.0 and 1.0");
        }
    }

    public static AgentCombatVariationSettings disabled() {
        return DISABLED;
    }
}
