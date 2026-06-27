package server.bots;


import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotCharacterReportRuntime;
import server.agents.integration.AgentBotInventoryReportRuntime;
import server.agents.integration.AgentBotMovementReportRuntime;
import server.agents.integration.AgentBotOfferRuntime;
import server.agents.integration.AgentBotRangeReportRuntime;
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
final class BotChatReportRuntime {
    private BotChatReportRuntime() {
    }

    static AgentChatReportFlow.ReportCallbacks reportCallbacks(BotEntry entry) {
        return AgentChatReportRuntime.reportCallbacks(AgentBotSchedulerRuntime.reportScheduler(), reportOperations(entry));
    }

    private static AgentChatReportRuntime.ReportOperations reportOperations(BotEntry entry) {
        return new AgentChatReportRuntime.ReportOperations() {
            @Override
            public void help() {
                reportHelp(entry);
            }

            @Override
            public void requestUpgrade() {
                BotChatSupplyRuntime.handleRequestUpgradeCommand(entry, entry.bot);
            }

            @Override
            public void recommendedGear() {
                reportRecommendedGear(entry, entry.bot);
            }

            @Override
            public void skills() {
                reportSkills(entry, entry.bot);
            }

            @Override
            public void stats() {
                reportStats(entry, entry.bot);
            }

            @Override
            public void movementStats() {
                reportMovementStats(entry, entry.bot);
            }

            @Override
            public void range() {
                reportRange(entry, entry.bot);
            }

            @Override
            public void build() {
                reportBuild(entry, entry.bot);
            }

            @Override
            public void inventory() {
                reportInventory(entry, entry.bot);
            }

            @Override
            public void mesos() {
                reportMesos(entry, entry.bot);
            }

            @Override
            public void exp() {
                reportExp(entry, entry.bot);
            }

            @Override
            public void inventorySlots() {
                reportInventorySlots(entry, entry.bot);
            }

            @Override
            public void scrolls() {
                reportScrolls(entry, entry.bot);
            }

            @Override
            public void potions() {
                reportPotions(entry, entry.bot);
            }

            @Override
            public void debugStats() {
                reportDebugStats(entry, entry.bot);
            }

            @Override
            public void critDebug() {
                reportCritDebug(entry, entry.bot);
            }

            @Override
            public void potDebug() {
                reportPotDebug(entry, entry.bot);
            }
        };
    }

    static void reportStats(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotCharacterReportRuntime.statsReport(bot));
    }

    static void reportRange(BotEntry entry, Character bot) {
        reportLine(entry, buildRangeReport(bot));
    }

    static String buildRangeReport(Character bot) {
        return AgentBotRangeReportRuntime.rangeReport(bot);
    }

    static String buildRangeReport(Character bot, BotEquipManager.MapDamageProfile mobProfile) {
        return AgentBotRangeReportRuntime.rangeReport(bot, mobProfile);
    }

    static void reportMovementStats(BotEntry entry, Character bot) {
        reportLines(entry, buildMovementStatsReport(bot));
    }

    static List<String> buildMovementStatsReport(Character bot) {
        return AgentBotMovementReportRuntime.movementStatsReport(bot);
    }

    static void reportBuild(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotCharacterReportRuntime.buildReport(bot));
    }

    static void reportSkills(BotEntry entry, Character bot) {
        AgentBotSkillReportRuntime.reportSkills(entry, bot);
    }

    static void reportInventory(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotInventoryReportRuntime.inventorySummary(bot));
    }

    static void reportMesos(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotCharacterReportRuntime.mesoReport(bot));
    }

    static void reportExp(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotCharacterReportRuntime.expReport(bot));
    }

    static void reportInventorySlots(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotInventoryReportRuntime.slotsReport(bot));
    }

    static void reportScrolls(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotInventoryReportRuntime.scrollReport(bot));
    }

    static void reportPotions(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotSupplyReportRuntime.potionReport(bot));
    }

    static void reportPotDebug(BotEntry entry, Character bot) {
        reportLine(entry, AgentBotSupplyReportRuntime.autopotDebugReport(bot));
    }

    static void reportDebugStats(BotEntry entry, Character bot) {
        reportLine(entry, BotCombatManager.describeDebugStats(entry, bot));
    }

    static void reportCritDebug(BotEntry entry, Character bot) {
        CombatFormulaProvider formula = CombatFormulaProvider.getInstance();
        CombatFormulaProvider.CritProfile crit = formula.resolveCritProfile(bot);
        CombatFormulaProvider.DamageProfile dmg = formula.resolveDamageProfile(bot, 0, 0, false);
        reportLine(entry, AgentCombatDialogueReporter.critReport(crit, dmg));
    }

    static void reportBuffDebug(BotEntry entry, Character bot) {
        reportLines(entry, BotBuffManager.getDebugLines(entry, bot));
    }

    static void reportSkillBuffDebug(BotEntry entry, Character bot) {
        reportLines(entry, BotCombatManager.getSkillBuffDebugLines(entry, bot));
    }

    static void reportHelp(BotEntry entry) {
        AgentChatReportRuntime.reportHelp(line -> AgentBotReplyRuntime.queueReply(entry, line));
    }

    static void reportRecommendedGear(BotEntry entry, Character bot) {
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
