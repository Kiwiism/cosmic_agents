package server.agents.integration;

import client.Character;
import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for control-triggered report delivery.
 */
public final class AgentBotControlReportRuntime {
    private AgentBotControlReportRuntime() {
    }

    public static void reportBuffDebug(BotEntry entry) {
        AgentBotChatReportRuntime.reportBuffDebug(entry, bot(entry));
    }

    public static void reportSkillBuffDebug(BotEntry entry) {
        AgentBotChatReportRuntime.reportSkillBuffDebug(entry, bot(entry));
    }

    private static Character bot(BotEntry entry) {
        return AgentBotRuntimeIdentityRuntime.bot(entry);
    }
}
