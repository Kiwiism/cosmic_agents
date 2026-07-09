package server.agents.capabilities.dialogue;


import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.capabilities.equipment.AgentMapDamageProfile;

import client.Character;
import server.agents.capabilities.dialogue.AgentCharacterDialogueReporter;
import server.agents.capabilities.combat.AgentCombatReportRuntime;
import server.agents.capabilities.dialogue.AgentChatReportFlow;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;
import server.agents.capabilities.dialogue.AgentMovementDialogueReporter;
import server.agents.capabilities.dialogue.AgentPendingActionRuntime;
import server.agents.capabilities.dialogue.AgentRangeReportService;
import server.agents.capabilities.dialogue.AgentSkillReportDecisionService;
import server.agents.capabilities.dialogue.AgentSupplyDialogueReporter;
import server.agents.capabilities.movement.AgentMovementKinematicsRuntime;
import server.agents.capabilities.trade.AgentOfferRuntime;
import server.agents.capabilities.supplies.AgentSupplyRuntime;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentStatusStateRuntime;

import java.util.List;

/**
 * Agent-owned report operations facade. Live identity lookup and reply
 * delivery remain integration seams.
 */
public final class AgentChatReportOperationsRuntime {
    private AgentChatReportOperationsRuntime() {
    }

    public static AgentChatReportFlow.ReportCallbacks reportCallbacks(AgentRuntimeEntry entry) {
        return AgentChatReportRuntime.reportCallbacks(
                AgentSchedulerRuntime::afterRandomDelay,
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
                AgentSupplyRuntime.handleRequestUpgradeCommand(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void recommendedGear() {
                reportRecommendedGear(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void skills() {
                reportSkills(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void stats() {
                reportStats(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void movementStats() {
                reportMovementStats(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void range() {
                reportRange(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void build() {
                reportBuild(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void inventory() {
                reportInventory(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void mesos() {
                reportMesos(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void exp() {
                reportExp(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void inventorySlots() {
                reportInventorySlots(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void scrolls() {
                reportScrolls(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void potions() {
                reportPotions(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void debugStats() {
                reportDebugStats(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void critDebug() {
                reportCritDebug(entry, AgentRuntimeIdentityRuntime.bot(entry));
            }

            @Override
            public void potDebug() {
                reportPotDebug(entry, AgentRuntimeIdentityRuntime.bot(entry));
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
        return AgentMovementDialogueReporter.movementStatsReport(AgentMovementKinematicsRuntime.snapshot(bot));
    }

    public static void reportBuild(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentCharacterDialogueReporter.buildReport(bot));
    }

    public static void reportSkills(AgentRuntimeEntry entry, Character bot) {
        AgentPendingActionRuntime.applySkillReportDecision(
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
        reportLine(entry, AgentCombatReportRuntime.debugStatsReport(entry, bot));
    }

    public static void reportCritDebug(AgentRuntimeEntry entry, Character bot) {
        reportLine(entry, AgentCombatReportRuntime.critDebugReport(bot));
    }

    public static void reportBuffDebug(AgentRuntimeEntry entry, Character bot) {
        reportLines(entry, AgentCombatReportRuntime.buffDebugLines(entry, bot));
    }

    public static void reportSkillBuffDebug(AgentRuntimeEntry entry, Character bot) {
        reportLines(entry, AgentCombatReportRuntime.skillBuffDebugLines(entry, bot));
    }

    public static void reportHelp(AgentRuntimeEntry entry) {
        AgentChatReportRuntime.reportHelp(line -> AgentReplyRuntime.queueReply(entry, line));
    }

    public static void reportRecommendedGear(AgentRuntimeEntry entry, Character bot) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        AgentChatReportRuntime.reportRecommendedGear(
                AgentStatusStateRuntime.recommendedGearReportState(entry),
                AgentOfferRuntime.recommendedGearActions(entry, bot, owner),
                System.currentTimeMillis());
    }

    private static void reportLine(AgentRuntimeEntry entry, String line) {
        AgentChatReportRuntime.reportLine(line, replyLine -> AgentReplyRuntime.queueReply(entry, replyLine));
    }

    private static void reportLines(AgentRuntimeEntry entry, Iterable<String> lines) {
        AgentChatReportRuntime.reportLines(lines, line -> AgentReplyRuntime.queueReply(entry, line));
    }
}
