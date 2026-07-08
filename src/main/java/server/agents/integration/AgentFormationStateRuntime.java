package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed formation spacing state.
 */
public final class AgentFormationStateRuntime {
    private AgentFormationStateRuntime() {
    }

    public static int followOffsetX(AgentRuntimeEntry entry) {
        return entry.formationOffsetState().followOffsetX();
    }

    public static void setFollowOffsetX(AgentRuntimeEntry entry, int followOffsetX) {
        entry.formationOffsetState().setFollowOffsetX(followOffsetX);
    }
}
