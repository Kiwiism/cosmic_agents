package server.agents.integration;

import server.agents.capabilities.equipment.AgentMapDamageProfile;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatReportFlow;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.bots.BotEntry;
import server.agents.capabilities.equipment.AgentEquipmentService;

import java.util.List;

/**
 * Agent-owned report facade over temporary bot-side data sources and reply
 * delivery. This keeps report orchestration in Agent modules while the data
 * providers still live in the legacy bot runtime.
 */
public final class AgentBotChatReportRuntime {
    private AgentBotChatReportRuntime() {
    }

    public static AgentChatReportFlow.ReportCallbacks reportCallbacks(BotEntry entry) {
        return AgentChatReportRuntime.reportCallbacks(
                AgentBotReportSchedulerRuntime.reportScheduler(),
                AgentBotReportOperationsRuntime.reportOperations(entry));
    }

    public static void reportStats(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentBotCharacterReportRuntime.statsReport(bot));
    }

    public static void reportRange(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, buildRangeReport(bot));
    }

    public static String buildRangeReport(Character bot) {
        return AgentBotRangeReportRuntime.rangeReport(bot);
    }

    public static String buildRangeReport(Character bot, AgentMapDamageProfile mobProfile) {
        return AgentBotRangeReportRuntime.rangeReport(bot, mobProfile);
    }

    public static void reportMovementStats(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLines(entry, buildMovementStatsReport(bot));
    }

    public static List<String> buildMovementStatsReport(Character bot) {
        return AgentBotMovementReportRuntime.movementStatsReport(bot);
    }

    public static void reportBuild(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentBotCharacterReportRuntime.buildReport(bot));
    }

    public static void reportSkills(BotEntry entry, Character bot) {
        AgentBotSkillReportRuntime.reportSkills(entry, bot);
    }

    public static void reportInventory(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentBotInventoryReportRuntime.inventorySummary(bot));
    }

    public static void reportMesos(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentBotCharacterReportRuntime.mesoReport(bot));
    }

    public static void reportExp(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentBotCharacterReportRuntime.expReport(bot));
    }

    public static void reportInventorySlots(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentBotInventoryReportRuntime.slotsReport(bot));
    }

    public static void reportScrolls(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentBotInventoryReportRuntime.scrollReport(bot));
    }

    public static void reportPotions(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentBotSupplyReportRuntime.potionReport(bot));
    }

    public static void reportPotDebug(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentBotSupplyReportRuntime.autopotDebugReport(bot));
    }

    public static void reportDebugStats(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentBotCombatReportRuntime.debugStatsReport(entry, bot));
    }

    public static void reportCritDebug(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentBotCombatReportRuntime.critDebugReport(bot));
    }

    public static void reportBuffDebug(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLines(entry, AgentBotCombatReportRuntime.buffDebugLines(entry, bot));
    }

    public static void reportSkillBuffDebug(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLines(entry, AgentBotCombatReportRuntime.skillBuffDebugLines(entry, bot));
    }

    public static void reportHelp(BotEntry entry) {
        AgentBotReportDeliveryRuntime.reportHelp(entry);
    }

    public static void reportRecommendedGear(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportRecommendedGear(entry, bot);
    }
}
