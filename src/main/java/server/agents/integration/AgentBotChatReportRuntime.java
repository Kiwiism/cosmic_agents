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
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/**
 * Agent-owned report facade over temporary bot-side data sources and reply
 * delivery. This keeps report orchestration in Agent modules while the data
 * providers still live in the legacy bot runtime.
 */
public final class AgentBotChatReportRuntime {
    private AgentBotChatReportRuntime() {
    }

    public static AgentChatReportFlow.ReportCallbacks reportCallbacks(AgentRuntimeEntry entry) {
        return AgentChatReportRuntime.reportCallbacks(
                AgentBotSchedulerRuntime::afterRandomDelay,
                reportOperations(entry));
    }

    public static AgentChatReportRuntime.ReportOperations reportOperations(AgentRuntimeEntry entry) {
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

    public static void reportStats(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentCharacterDialogueReporter.statsReport(bot));
    }

    public static void reportRange(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, buildRangeReport(bot));
    }

    public static String buildRangeReport(Character bot) {
        return AgentRangeReportService.rangeReport(bot);
    }

    public static String buildRangeReport(Character bot, AgentMapDamageProfile mobProfile) {
        return AgentRangeReportService.rangeReport(bot, mobProfile);
    }

    public static void reportMovementStats(AgentRuntimeEntry entry, Character bot) {
        reportLines(entry, buildMovementStatsReport(bot));
    }

    public static List<String> buildMovementStatsReport(Character bot) {
        return AgentMovementDialogueReporter.movementStatsReport(AgentBotMovementKinematicsRuntime.snapshot(bot));
    }

    public static void reportBuild(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentCharacterDialogueReporter.buildReport(bot));
    }

    public static void reportSkills(AgentRuntimeEntry entry, Character bot) {
        AgentBotPendingActionRuntime.applySkillReportDecision(
                entry,
                AgentSkillReportDecisionService.skillReportDecision(bot));
    }

    public static void reportInventory(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentInventoryDialogueReporter.inventorySummary(bot));
    }

    public static void reportMesos(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentCharacterDialogueReporter.mesoReport(bot));
    }

    public static void reportExp(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentCharacterDialogueReporter.expReport(bot));
    }

    public static void reportInventorySlots(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentInventoryDialogueReporter.slotsReport(bot));
    }

    public static void reportScrolls(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentInventoryDialogueReporter.scrollReport(bot));
    }

    public static void reportPotions(AgentRuntimeEntry entry, Character bot) {
        reportLine(
                entry,
                AgentSupplyDialogueReporter.potionReport(AgentPotionService.countPotions(bot)));
    }

    public static void reportPotDebug(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentPotionService.autopotDebugReport(bot));
    }

    public static void reportDebugStats(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentBotCombatReportRuntime.debugStatsReport(entry, bot));
    }

    public static void reportCritDebug(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentBotCombatReportRuntime.critDebugReport(bot));
    }

    public static void reportBuffDebug(AgentRuntimeEntry entry, Character bot) {
        reportLines(entry, AgentBotCombatReportRuntime.buffDebugLines(entry, bot));
    }

    public static void reportSkillBuffDebug(AgentRuntimeEntry entry, Character bot) {
        reportLines(entry, AgentBotCombatReportRuntime.skillBuffDebugLines(entry, bot));
    }

    public static void reportHelp(AgentRuntimeEntry entry) {
        AgentChatReportRuntime.reportHelp(line -> AgentBotReplyRuntime.queueReply(entry, line));
    }

    public static void reportRecommendedGear(AgentRuntimeEntry entry, Character bot) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        AgentChatReportRuntime.reportRecommendedGear(
                AgentBotStatusRuntime.recommendedGearReportState(entry),
                AgentBotOfferRuntime.recommendedGearActions(entry, bot, owner),
                System.currentTimeMillis());
    }

    private static void reportLine(AgentRuntimeEntry entry, String line) {
        AgentChatReportRuntime.reportLine(line, replyLine -> AgentBotReplyRuntime.queueReply(entry, replyLine));
    }

    private static void reportLines(AgentRuntimeEntry entry, Iterable<String> lines) {
        AgentChatReportRuntime.reportLines(lines, line -> AgentBotReplyRuntime.queueReply(entry, line));
    }
}
