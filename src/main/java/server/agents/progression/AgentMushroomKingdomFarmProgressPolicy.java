package server.agents.progression;

/** Pure transition policy for durable Mushroom Kingdom farming history. */
final class AgentMushroomKingdomFarmProgressPolicy {
    private AgentMushroomKingdomFarmProgressPolicy() { }

    static AgentMushroomKingdomFarmProgress beginYetiCampaign(
            AgentMushroomKingdomFarmProgress current, long nowMs) {
        if (current.yetiRuns() < AgentMushroomKingdomFarmProgressRuntime.MAX_YETI_RUNS
                || current.yetiCooldownUntilMs() > nowMs) return current;
        return new AgentMushroomKingdomFarmProgress(
                1, current.characterId(), 0, 0, nowMs, 0L,
                current.acquiredWeaponItemId(), current.scrollAttempts(),
                current.scrollCompletions(), nowMs,
                "starting a new Yeti campaign after cooldown");
    }

    static AgentMushroomKingdomFarmProgress recordYetiRun(
            AgentMushroomKingdomFarmProgress current,
            boolean relevantBoxOpportunity,
            int acquiredWeaponItemId,
            long nowMs) {
        int runs = current.yetiRuns() + 1;
        boolean capped = runs >= AgentMushroomKingdomFarmProgressRuntime.MAX_YETI_RUNS
                && acquiredWeaponItemId <= 0;
        String reason = acquiredWeaponItemId > 0 ? "desired Pepe weapon acquired"
                : capped ? "ten Yeti runs completed without the desired weapon"
                : "Yeti boss run completed";
        return new AgentMushroomKingdomFarmProgress(
                1, current.characterId(), runs,
                current.relevantBoxOpportunities() + (relevantBoxOpportunity ? 1 : 0),
                current.yetiCampaignStartedAtMs() == 0L
                        ? nowMs : current.yetiCampaignStartedAtMs(),
                capped ? nowMs + AgentMushroomKingdomFarmProgressRuntime.YETI_COOLDOWN_MS : 0L,
                Math.max(current.acquiredWeaponItemId(), acquiredWeaponItemId),
                current.scrollAttempts(), current.scrollCompletions(), nowMs, reason);
    }

    static AgentMushroomKingdomFarmProgress recordScrollAttempt(
            AgentMushroomKingdomFarmProgress current,
            boolean applied,
            String outcome,
            long nowMs) {
        return new AgentMushroomKingdomFarmProgress(
                1, current.characterId(), current.yetiRuns(),
                current.relevantBoxOpportunities(), current.yetiCampaignStartedAtMs(),
                current.yetiCooldownUntilMs(), current.acquiredWeaponItemId(),
                current.scrollAttempts() + 1,
                current.scrollCompletions() + (applied ? 1 : 0), nowMs,
                outcome == null ? "Pepe scroll attempt completed" : outcome);
    }

    static AgentMushroomKingdomFarmProgress recordStopReason(
            AgentMushroomKingdomFarmProgress current, String reason, long nowMs) {
        return new AgentMushroomKingdomFarmProgress(
                1, current.characterId(), current.yetiRuns(),
                current.relevantBoxOpportunities(), current.yetiCampaignStartedAtMs(),
                current.yetiCooldownUntilMs(), current.acquiredWeaponItemId(),
                current.scrollAttempts(), current.scrollCompletions(), nowMs, reason);
    }
}
