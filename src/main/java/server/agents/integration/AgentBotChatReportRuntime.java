package server.agents.integration;

import server.agents.capabilities.equipment.AgentMapDamageProfile;

import client.Character;
import server.agents.capabilities.dialogue.AgentCharacterDialogueReporter;
import server.agents.capabilities.dialogue.AgentChatReportFlow;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;
import server.agents.capabilities.dialogue.AgentMovementDialogueReporter;
import server.agents.capabilities.dialogue.AgentRangeReportService;
import server.agents.capabilities.dialogue.AgentSkillReportDecisionService;
import server.agents.capabilities.dialogue.AgentSupplyDialogueReporter;
import server.agents.capabilities.supplies.AgentPotionService;
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
                AgentBotSchedulerRuntime::afterRandomDelay,
                reportOperations(entry));
    }

    public static AgentChatReportRuntime.ReportOperations reportOperations(BotEntry entry) {
        return new AgentChatReportRuntime.ReportOperations() {
            @Override
            public void help() {
                reportHelp(entry);
            }

            @Override
            public void requestUpgrade() {
                AgentBotSupplyRuntime.handleRequestUpgradeCommand(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void recommendedGear() {
                reportRecommendedGear(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void skills() {
                reportSkills(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void stats() {
                reportStats(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void movementStats() {
                reportMovementStats(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void range() {
                reportRange(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void build() {
                reportBuild(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void inventory() {
                reportInventory(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void mesos() {
                reportMesos(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void exp() {
                reportExp(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void inventorySlots() {
                reportInventorySlots(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void scrolls() {
                reportScrolls(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void potions() {
                reportPotions(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void debugStats() {
                reportDebugStats(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void critDebug() {
                reportCritDebug(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void potDebug() {
                reportPotDebug(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
            }
        };
    }

    public static void reportStats(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentCharacterDialogueReporter.statsReport(bot));
    }

    public static void reportRange(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, buildRangeReport(bot));
    }

    public static String buildRangeReport(Character bot) {
        return AgentRangeReportService.rangeReport(bot);
    }

    public static String buildRangeReport(Character bot, AgentMapDamageProfile mobProfile) {
        return AgentRangeReportService.rangeReport(bot, mobProfile);
    }

    public static void reportMovementStats(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLines(entry, buildMovementStatsReport(bot));
    }

    public static List<String> buildMovementStatsReport(Character bot) {
        return AgentMovementDialogueReporter.movementStatsReport(AgentBotMovementKinematicsRuntime.snapshot(bot));
    }

    public static void reportBuild(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentCharacterDialogueReporter.buildReport(bot));
    }

    public static void reportSkills(BotEntry entry, Character bot) {
        AgentBotPendingActionRuntime.applySkillReportDecision(
                entry,
                AgentSkillReportDecisionService.skillReportDecision(bot));
    }

    public static void reportInventory(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentInventoryDialogueReporter.inventorySummary(bot));
    }

    public static void reportMesos(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentCharacterDialogueReporter.mesoReport(bot));
    }

    public static void reportExp(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentCharacterDialogueReporter.expReport(bot));
    }

    public static void reportInventorySlots(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentInventoryDialogueReporter.slotsReport(bot));
    }

    public static void reportScrolls(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentInventoryDialogueReporter.scrollReport(bot));
    }

    public static void reportPotions(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(
                entry,
                AgentSupplyDialogueReporter.potionReport(AgentPotionService.countPotions(bot)));
    }

    public static void reportPotDebug(BotEntry entry, Character bot) {
        AgentBotReportDeliveryRuntime.reportLine(entry, AgentPotionService.autopotDebugReport(bot));
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
