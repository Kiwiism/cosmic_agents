package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned report reply adapter. Report delivery should depend on this
 * narrow boundary instead of the broad bot reply runtime.
 */
public final class AgentBotReportReplyRuntime {
    private AgentBotReportReplyRuntime() {
    }

    public static void queueReply(BotEntry entry, String message) {
        AgentBotReplyRuntime.queueReply(entry, message);
    }
}
