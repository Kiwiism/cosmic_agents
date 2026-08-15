package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

/**
 * Single owner for ephemeral tactical decision frames. Mechanics state such as cooldowns,
 * buffs, ammo, damage, and death deliberately remains outside this aggregate.
 */
public final class AgentCombatDecisionState {
    public static final AgentCapabilityStateKey<AgentCombatDecisionState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.decision",
                    AgentCombatDecisionState.class, AgentCombatDecisionState::new);

    private final AgentCombatTacticalState tactical = new AgentCombatTacticalState();
    private final AgentCombatLocalTargetLeaseState localTargetLease =
            new AgentCombatLocalTargetLeaseState();
    private final AgentCombatTargetSearchModeState targetSearch =
            new AgentCombatTargetSearchModeState();
    private final AgentCombatPlatformBatchState platformBatch =
            new AgentCombatPlatformBatchState();
    private final AgentRouteBlockerState routeBlocker = new AgentRouteBlockerState();

    public AgentCombatTacticalState tactical() {
        return tactical;
    }

    public AgentCombatLocalTargetLeaseState localTargetLease() {
        return localTargetLease;
    }

    public AgentCombatTargetSearchModeState targetSearch() {
        return targetSearch;
    }

    public AgentCombatPlatformBatchState platformBatch() {
        return platformBatch;
    }

    public AgentRouteBlockerState routeBlocker() {
        return routeBlocker;
    }

    public void clear() {
        tactical.clear();
        localTargetLease.clear();
        targetSearch.clear();
        platformBatch.clear();
        routeBlocker.clear();
    }
}
