package server.bots;


import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotCharacterReportRuntime;
import server.agents.integration.AgentBotInventoryReportRuntime;
import server.agents.integration.AgentBotMovementReportRuntime;
import server.agents.integration.AgentBotOfferRuntime;
import server.agents.integration.AgentBotRangeReportRuntime;
import server.agents.integration.AgentBotReportOperationsRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotSkillReportRuntime;
import server.agents.integration.AgentBotStatusRuntime;
import server.agents.integration.AgentBotSupplyReportRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentChatReportFlow;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.combat.CombatFormulaProvider;

import java.util.List;

/**
 * Temporary bot-side report adapter while report data sources still live in bot
 * runtime managers.
 */
public final class BotChatReportRuntime {
    private BotChatReportRuntime() {
    }

    static AgentChatReportFlow.ReportCallbacks reportCallbacks(BotEntry entry) {
        return AgentChatReportRuntime.reportCallbacks(
                AgentBotSchedulerRuntime.reportScheduler(),
                AgentBotReportOperationsRuntime.reportOperations(entry));
    }

    public static void reportStats(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotCharacterReportRuntime.statsReport(bot));
    }

    public static void reportRange(BotEntry entry, Character bot) {
        reportLine(entry, buildRangeReport(bot));
    }

    public static String buildRangeReport(Character bot) {
        return AgentBotRangeReportRuntime.rangeReport(bot);
    }

    public static String buildRangeReport(Character bot, BotEquipManager.MapDamageProfile mobProfile) {
        return AgentBotRangeReportRuntime.rangeReport(bot, mobProfile);
    }

    public static void reportMovementStats(BotEntry entry, Character bot) {
        reportLines(entry, buildMovementStatsReport(bot));
    }

    public static List<String> buildMovementStatsReport(Character bot) {
        return AgentBotMovementReportRuntime.movementStatsReport(bot);
    }

    public static void reportBuild(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotCharacterReportRuntime.buildReport(bot));
    }

    public static void reportSkills(BotEntry entry, Character bot) {
        AgentBotSkillReportRuntime.reportSkills(entry, bot);
    }

    public static void reportInventory(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotInventoryReportRuntime.inventorySummary(bot));
    }

    public static void reportMesos(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotCharacterReportRuntime.mesoReport(bot));
    }

    public static void reportExp(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotCharacterReportRuntime.expReport(bot));
    }

    public static void reportInventorySlots(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotInventoryReportRuntime.slotsReport(bot));
    }

    public static void reportScrolls(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotInventoryReportRuntime.scrollReport(bot));
    }

    public static void reportPotions(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotSupplyReportRuntime.potionReport(bot));
    }

    public static void reportPotDebug(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotSupplyReportRuntime.autopotDebugReport(bot));
    }

    public static void reportDebugStats(BotEntry entry, Character bot) {
        reportLine(entry, BotCombatManager.describeDebugStats(entry, bot));
    }

    public static void reportCritDebug(BotEntry entry, Character bot) {
        CombatFormulaProvider formula = CombatFormulaProvider.getInstance();
        CombatFormulaProvider.CritProfile crit = formula.resolveCritProfile(bot);
        CombatFormulaProvider.DamageProfile dmg = formula.resolveDamageProfile(bot, 0, 0, false);
        reportLine(entry, AgentCombatDialogueReporter.critReport(crit, dmg));
    }

    public static void reportBuffDebug(BotEntry entry, Character bot) {
        reportLines(entry, BotBuffManager.getDebugLines(entry, bot));
    }

    public static void reportSkillBuffDebug(BotEntry entry, Character bot) {
        reportLines(entry, BotCombatManager.getSkillBuffDebugLines(entry, bot));
    }

    public static void reportHelp(BotEntry entry) {
        AgentChatReportRuntime.reportHelp(line -> AgentBotReplyRuntime.queueReply(entry, line));
    }

    public static void reportRecommendedGear(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        AgentChatReportRuntime.reportRecommendedGear(
                AgentBotStatusRuntime.recommendedGearReportState(entry),
                AgentBotOfferRuntime.recommendedGearActions(entry, bot, owner),
                System.currentTimeMillis());
    }

    private static void reportLine(BotEntry entry, String line) {
        AgentChatReportRuntime.reportLine(line, replyLine -> AgentBotReplyRuntime.queueReply(entry, replyLine));
    }

    private static void reportLines(BotEntry entry, Iterable<String> lines) {
        AgentChatReportRuntime.reportLines(lines, line -> AgentBotReplyRuntime.queueReply(entry, line));
    }

}
