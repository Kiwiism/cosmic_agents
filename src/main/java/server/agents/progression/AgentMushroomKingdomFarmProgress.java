package server.agents.progression;

/** Durable, bounded post-story farming history for one character. */
public record AgentMushroomKingdomFarmProgress(
        int schemaVersion,
        int characterId,
        int yetiRuns,
        int relevantBoxOpportunities,
        long yetiCampaignStartedAtMs,
        long yetiCooldownUntilMs,
        int acquiredWeaponItemId,
        int scrollAttempts,
        int scrollCompletions,
        long updatedAtMs,
        String lastStopReason) {

    public AgentMushroomKingdomFarmProgress {
        if (schemaVersion <= 0 || characterId <= 0 || yetiRuns < 0
                || relevantBoxOpportunities < 0 || yetiCampaignStartedAtMs < 0L
                || yetiCooldownUntilMs < 0L || acquiredWeaponItemId < 0
                || scrollAttempts < 0 || scrollCompletions < 0 || updatedAtMs < 0L) {
            throw new IllegalArgumentException("valid Mushroom Kingdom farm progress is required");
        }
        lastStopReason = lastStopReason == null ? "" : lastStopReason.trim();
    }

    public static AgentMushroomKingdomFarmProgress empty(int characterId, long nowMs) {
        return new AgentMushroomKingdomFarmProgress(
                1, characterId, 0, 0, 0L, 0L, 0, 0, 0, nowMs, "");
    }

    public AgentMushroomKingdomFarmSnapshot snapshot() {
        return new AgentMushroomKingdomFarmSnapshot(
                yetiRuns, relevantBoxOpportunities, yetiCooldownUntilMs,
                scrollAttempts, scrollCompletions, lastStopReason);
    }
}
