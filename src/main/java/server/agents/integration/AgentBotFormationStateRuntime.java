package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed formation spacing state.
 */
public final class AgentBotFormationStateRuntime {
    private AgentBotFormationStateRuntime() {
    }

    public static int followOffsetX(BotEntry entry) {
        return entry.followOffsetX();
    }

    public static void setFollowOffsetX(BotEntry entry, int followOffsetX) {
        entry.setFollowOffsetX(followOffsetX);
    }
}
