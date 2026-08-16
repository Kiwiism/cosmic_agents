package server.agents.economy.session;

/**
 * Immutable participant preferences consumed by Commerce.
 *
 * <p>Identity and preferences belong to the caller-facing Commerce contract. Holdings, live
 * character state, population schedules, and observation clocks deliberately do not.</p>
 */
public record CommerceParticipant(
        String agentId,
        String jobFamily,
        double dailyActivityFraction,
        double riskTolerance,
        double liquidityPreference,
        double upgradeAggressiveness,
        double shoppingPatience,
        double stallWillingness,
        int priceMemoryHours,
        double negotiationAggressiveness,
        double chairInterest) {
    public CommerceParticipant {
        if (agentId == null || agentId.isBlank() || jobFamily == null || jobFamily.isBlank()
                || priceMemoryHours <= 0) {
            throw new IllegalArgumentException("invalid Commerce participant");
        }
        double[] values = {dailyActivityFraction, riskTolerance, liquidityPreference,
                upgradeAggressiveness, shoppingPatience, stallWillingness,
                negotiationAggressiveness, chairInterest};
        for (double value : values) {
            if (!Double.isFinite(value) || value < 0 || value > 1) {
                throw new IllegalArgumentException(
                        "Commerce participant fractions must be between zero and one");
            }
        }
    }
}
