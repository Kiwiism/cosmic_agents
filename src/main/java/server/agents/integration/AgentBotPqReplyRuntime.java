package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned PQ reply adapter. Party-quest dialogue flows should depend on
 * this narrow boundary instead of the broad reply runtime.
 */
public final class AgentBotPqReplyRuntime {
    private AgentBotPqReplyRuntime() {
    }

    public static void queueSay(BotEntry entry, String message) {
        AgentBotReplyRuntime.queueSay(entry, message);
    }
}
