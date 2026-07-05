package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed formation spacing state.
 */
public final class AgentBotFormationStateRuntime {
    private AgentBotFormationStateRuntime() {
    }

    public static int followOffsetX(AgentRuntimeEntry entry) {
        return entry.formationOffsetState().followOffsetX();
    }

    public static void setFollowOffsetX(AgentRuntimeEntry entry, int followOffsetX) {
        entry.formationOffsetState().setFollowOffsetX(followOffsetX);
    }
}
