package server.agents.capabilities.objective;

import server.agents.profiles.AgentBehaviorProfile;

/** Plan-neutral objective presentation and timing variation. */
public record AgentObjectiveVariationSettings(
        boolean enabled,
        long seed,
        AgentBehaviorProfile.DelayRange beforeNpcInteractionMs,
        AgentBehaviorProfile.DelayRange betweenObjectivesMs,
        boolean npcAnchorVariationEnabled,
        boolean restSpotVariationEnabled) {
    private static final AgentObjectiveVariationSettings DISABLED =
            new AgentObjectiveVariationSettings(false, 0L, null, null, false, false);

    public static AgentObjectiveVariationSettings disabled() {
        return DISABLED;
    }
}
