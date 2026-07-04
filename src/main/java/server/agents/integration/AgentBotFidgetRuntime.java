package server.agents.integration;

import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for fidget status decisions while fidget
 * movement execution still lives in the legacy bot runtime.
 */
public final class AgentBotFidgetRuntime {
    private AgentBotFidgetRuntime() {
    }

    public static boolean isLeaderIdleForFidget(BotEntry entry) {
        return AgentBotChatStatusRuntime.isOwnerIdle(entry);
    }

    public static boolean hasActiveFidgetMode(BotEntry entry) {
        return AgentBotFidgetStateRuntime.active(entry);
    }
}
