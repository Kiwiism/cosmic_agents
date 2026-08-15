package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

/** Registry access boundary for ranged tactical commitments. */
final class AgentRangedTacticalStateRuntime {
    private AgentRangedTacticalStateRuntime() {
    }

    static AgentRangedTacticalState state(AgentRuntimeEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("an Agent runtime entry is required");
        }
        return entry.capabilityStates().require(AgentRangedTacticalState.STATE_KEY);
    }
}
