package server.bots;


import server.agents.integration.AgentBotChatReportRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentChatReportFlow;

import java.util.List;

/**
 * Temporary bot-side report adapter while report data sources still live in bot
 * runtime managers.
 */
public final class BotChatReportRuntime {
    private BotChatReportRuntime() {
    }

    static AgentChatReportFlow.ReportCallbacks reportCallbacks(BotEntry entry) {
        return AgentBotChatReportRuntime.reportCallbacks(entry);
    }

    public static void reportStats(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportStats(entry, bot);
    }

    public static void reportRange(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportRange(entry, bot);
    }

    public static String buildRangeReport(Character bot) {
        return AgentBotChatReportRuntime.buildRangeReport(bot);
    }

    public static String buildRangeReport(Character bot, BotEquipManager.MapDamageProfile mobProfile) {
        return AgentBotChatReportRuntime.buildRangeReport(bot, mobProfile);
    }

    public static void reportMovementStats(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportMovementStats(entry, bot);
    }

    public static List<String> buildMovementStatsReport(Character bot) {
        return AgentBotChatReportRuntime.buildMovementStatsReport(bot);
    }

    public static void reportBuild(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportBuild(entry, bot);
    }

    public static void reportSkills(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportSkills(entry, bot);
    }

    public static void reportInventory(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportInventory(entry, bot);
    }

    public static void reportMesos(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportMesos(entry, bot);
    }

    public static void reportExp(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportExp(entry, bot);
    }

    public static void reportInventorySlots(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportInventorySlots(entry, bot);
    }

    public static void reportScrolls(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportScrolls(entry, bot);
    }

    public static void reportPotions(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportPotions(entry, bot);
    }

    public static void reportPotDebug(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportPotDebug(entry, bot);
    }

    public static void reportDebugStats(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportDebugStats(entry, bot);
    }

    public static void reportCritDebug(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportCritDebug(entry, bot);
    }

    public static void reportBuffDebug(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportBuffDebug(entry, bot);
    }

    public static void reportSkillBuffDebug(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportSkillBuffDebug(entry, bot);
    }

    public static void reportHelp(BotEntry entry) {
        AgentBotChatReportRuntime.reportHelp(entry);
    }

    public static void reportRecommendedGear(BotEntry entry, Character bot) {
        AgentBotChatReportRuntime.reportRecommendedGear(entry, bot);
    }

}
