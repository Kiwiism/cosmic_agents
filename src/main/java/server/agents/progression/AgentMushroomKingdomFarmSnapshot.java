package server.agents.progression;

/** Immutable durable outcome summary used by World Director proposal scoring. */
public record AgentMushroomKingdomFarmSnapshot(
        int yetiRuns,
        int relevantBoxOpportunities,
        long yetiCooldownUntilMs,
        int scrollAttempts,
        int scrollCompletions,
        String lastStopReason) {

    public static final AgentMushroomKingdomFarmSnapshot EMPTY =
            new AgentMushroomKingdomFarmSnapshot(0, 0, 0L, 0, 0, "");

    public AgentMushroomKingdomFarmSnapshot {
        if (yetiRuns < 0 || relevantBoxOpportunities < 0 || yetiCooldownUntilMs < 0L
                || scrollAttempts < 0 || scrollCompletions < 0) {
            throw new IllegalArgumentException("valid Mushroom Kingdom farm facts are required");
        }
        lastStopReason = lastStopReason == null ? "" : lastStopReason.trim();
    }

    public boolean yetiCooldownActive(long nowMs) {
        return yetiCooldownUntilMs > Math.max(0L, nowMs);
    }
}
