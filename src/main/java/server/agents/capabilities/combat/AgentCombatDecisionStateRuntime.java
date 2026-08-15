package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

/** Access boundary for the single per-Agent combat decision aggregate. */
public final class AgentCombatDecisionStateRuntime {
    private AgentCombatDecisionStateRuntime() {
    }

    public static AgentCombatDecisionState state(AgentRuntimeEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("an Agent runtime entry is required");
        }
        return entry.capabilityStates().require(AgentCombatDecisionState.STATE_KEY);
    }
}
