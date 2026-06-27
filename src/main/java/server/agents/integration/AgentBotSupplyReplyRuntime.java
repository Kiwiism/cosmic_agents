package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned supply reply adapter. Supply request outcomes should depend on
 * this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotSupplyReplyRuntime {
    private AgentBotSupplyReplyRuntime() {
    }

    public static void queueReply(BotEntry entry, String message) {
        AgentBotReplyRuntime.queueReply(entry, message);
    }
}
