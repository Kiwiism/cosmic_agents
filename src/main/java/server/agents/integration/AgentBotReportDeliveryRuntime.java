package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for report delivery while replies and offer
 * side effects still target the bot runtime.
 */
public final class AgentBotReportDeliveryRuntime {
    private AgentBotReportDeliveryRuntime() {
    }

    public static void reportHelp(BotEntry entry) {
        AgentChatReportRuntime.reportHelp(line -> AgentBotReplyRuntime.queueReply(entry, line));
    }

    public static void reportLine(BotEntry entry, String line) {
        AgentChatReportRuntime.reportLine(line, replyLine -> AgentBotReplyRuntime.queueReply(entry, replyLine));
    }

    public static void reportLines(BotEntry entry, Iterable<String> lines) {
        AgentChatReportRuntime.reportLines(lines, line -> AgentBotReplyRuntime.queueReply(entry, line));
    }

    public static void reportRecommendedGear(BotEntry entry, Character bot) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        AgentChatReportRuntime.reportRecommendedGear(
                AgentBotStatusRuntime.recommendedGearReportState(entry),
                AgentBotOfferRuntime.recommendedGearActions(entry, bot, owner),
                System.currentTimeMillis());
    }
}
