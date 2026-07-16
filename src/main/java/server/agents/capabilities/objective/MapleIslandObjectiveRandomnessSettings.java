package server.agents.capabilities.objective;

import server.agents.profiles.AgentBehaviorProfile;

/**
 * Run-scoped presentation variation for a Maple Island objective plan.
 * Null delay ranges retain the assigned behavior profile's ranges.
 */
public record MapleIslandObjectiveRandomnessSettings(
        boolean enabled,
        long seed,
        AgentBehaviorProfile.DelayRange beforeNpcInteractionMs,
        AgentBehaviorProfile.DelayRange betweenObjectivesMs,
        boolean npcAnchorVariationEnabled,
        boolean restSpotVariationEnabled) {
    public static final AgentBehaviorProfile.DelayRange COHORT_NPC_DELAY_MS =
            new AgentBehaviorProfile.DelayRange(600, 2_200);
    public static final AgentBehaviorProfile.DelayRange COHORT_OBJECTIVE_DELAY_MS =
            new AgentBehaviorProfile.DelayRange(900, 3_000);

    public static MapleIslandObjectiveRandomnessSettings disabled() {
        return new MapleIslandObjectiveRandomnessSettings(false, 0L, null, null, false, false);
    }

    public static MapleIslandObjectiveRandomnessSettings cohort(long seed) {
        return new MapleIslandObjectiveRandomnessSettings(
                true, seed, COHORT_NPC_DELAY_MS, COHORT_OBJECTIVE_DELAY_MS, true, true);
    }
}
