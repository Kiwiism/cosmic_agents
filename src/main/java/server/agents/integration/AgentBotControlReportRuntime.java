package server.agents.integration;

import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for control-triggered report delivery.
 */
public final class AgentBotControlReportRuntime {
    private AgentBotControlReportRuntime() {
    }

    public static void reportBuffDebug(BotEntry entry) {
        AgentBotChatReportRuntime.reportBuffDebug(entry, entry.bot());
    }

    public static void reportSkillBuffDebug(BotEntry entry) {
        AgentBotChatReportRuntime.reportSkillBuffDebug(entry, entry.bot());
    }
}
