package server.agents.economy.scenario;

/** Immutable, journalable heterogeneity; holdings are deliberately not sampled here. */
public record EconomyAgentProfile(String agentId, String jobFamily, double dailyActivityFraction,
                                  double riskTolerance, double liquidityPreference,
                                  double upgradeAggressiveness, double shoppingPatience,
                                  double stallWillingness, int priceMemoryHours,
                                  double negotiationAggressiveness, double chairInterest) {
    public EconomyAgentProfile {
        if (agentId == null || agentId.isBlank() || jobFamily == null || jobFamily.isBlank()
                || priceMemoryHours <= 0) throw new IllegalArgumentException("invalid agent profile");
        double[] values = {dailyActivityFraction, riskTolerance, liquidityPreference,
                upgradeAggressiveness, shoppingPatience, stallWillingness,
                negotiationAggressiveness, chairInterest};
        for (double value : values) if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("profile fractions must be between zero and one");
    }
}
