package server.agents.social.projection;

import server.agents.runtime.AgentRuntimeEntry;

/** Read boundary for future LLM prompt construction and planner adapters. */
public final class AgentSocialContextProjectionRuntime {
    private AgentSocialContextProjectionRuntime() {
    }

    public static AgentSocialContextProjectionState.Snapshot snapshot(AgentRuntimeEntry entry) {
        if (entry == null) {
            return new AgentSocialContextProjectionState().snapshot();
        }
        return entry.capabilityStates().require(AgentSocialContextProjectionState.STATE_KEY).snapshot();
    }
}
