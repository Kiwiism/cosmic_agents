package server.bots;


import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotOfferRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotStatusRuntime;
import server.agents.integration.AgentBotSupplyReportRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentCharacterDialogueReporter;
import server.agents.capabilities.dialogue.AgentChatReportFlow;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;
import server.agents.capabilities.dialogue.AgentMovementDialogueReporter;
import server.agents.capabilities.dialogue.AgentSkillDialogueReporter;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;
import server.combat.CombatFormulaProvider;
import server.maps.FieldLimit;
import server.maps.MapleMap;

import java.util.List;
import java.util.Map;

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
        reportLine(entry, AgentCharacterDialogueReporter.statsReport(bot));
    }

    static void reportRange(BotEntry entry, Character bot) {
        reportLine(entry, buildRangeReport(bot));
    }

    static String buildRangeReport(Character bot) {
        BotEquipManager.MapDamageProfile dmgProfile = BotEquipManager.MapDamageProfile.snapshot(bot);
        BotEquipManager.MapDamageProfile hitProfile = BotEquipManager.MapDamageProfile.snapshotByAvoid(bot);
        return buildRangeReport(bot, dmgProfile, hitProfile);
    }

    static String buildRangeReport(Character bot, BotEquipManager.MapDamageProfile mobProfile) {
        return buildRangeReport(bot, mobProfile, mobProfile);
    }

    private static String buildRangeReport(Character bot, BotEquipManager.MapDamageProfile mobProfile,
                                           BotEquipManager.MapDamageProfile hitProfile) {
        AgentCombatDialogueReporter.MobHitProfile agentHitProfile = hitProfile == null
                ? null
                : new AgentCombatDialogueReporter.MobHitProfile(hitProfile.mobLevel(), hitProfile.mobAvoid());
        return AgentCombatDialogueReporter.rangeReport(
                bot, BotEquipManager.isMageJob(bot.getJob()), agentHitProfile);
    }

    static void reportMovementStats(BotEntry entry, Character bot) {
        reportLines(entry, buildMovementStatsReport(bot));
    }

    static List<String> buildMovementStatsReport(Character bot) {
        if (bot == null) {
            return AgentMovementDialogueReporter.movementStatsReport(null, 0, 0, false, 0, null);
        }

        BotMovementProfile profile = BotMovementProfile.fromCharacter(bot);
        MapleMap map = bot.getMap();
        int rawSpeedStat = bot.getTotalMoveSpeedStat();
        int rawJumpStat = bot.getTotalJumpStat();
        boolean movementSkillsForced = map != null && FieldLimit.MOVEMENTSKILLS.check(map.getFieldLimit());
        AgentMovementDialogueReporter.MovementProfile agentProfile =
                new AgentMovementDialogueReporter.MovementProfile(
                        profile.totalSpeedStat(),
                        profile.totalJumpStat(),
                        profile.walkVelocityPxs(),
                        profile.hForcePxs(),
                        BotPhysicsEngine.jumpForcePerTick(profile),
                        BotPhysicsEngine.ropeJumpForcePerTick(profile),
                        BotPhysicsEngine.calculateMaxJumpHeight(profile));
        AgentMovementDialogueReporter.MapMovementProfile mapProfile = map == null
                ? null
                : new AgentMovementDialogueReporter.MapMovementProfile(
                        BotMovementManager.walkStep(map, profile),
                        BotPhysicsEngine.climbStepPerTick(),
                        BotPhysicsEngine.maxJumpHorizontalTravel(map, profile),
                        BotPhysicsEngine.maxRopeJumpHorizontalTravel(map, profile));
        return AgentMovementDialogueReporter.movementStatsReport(
                agentProfile,
                rawSpeedStat,
                rawJumpStat,
                movementSkillsForced,
                BotPhysicsEngine.climbStepPerTick(),
                mapProfile);
    }

    static void reportBuild(BotEntry entry, Character bot) {
        reportLine(entry, AgentCharacterDialogueReporter.buildReport(bot));
    }

    static void reportSkills(BotEntry entry, Character bot) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        List<AgentSkillReportFlow.SkillLine> beginnerSkills =
                AgentSkillDialogueReporter.collectLearnedBeginnerSkills(bot);
        int beginnerSpLeft = AgentSkillDialogueReporter.remainingBeginnerSp(bot);
        AgentChatReportRuntime.reportSkills(
                bot.isBeginnerJob(),
                bot.getRemainingSp(),
                beginnerSkills,
                beginnerSpLeft,
                skillTrees,
                decision -> BotChatPendingActionRuntime.applySkillReportDecision(entry, decision));
    }

    static void reportInventory(BotEntry entry, Character bot) {
        reportLine(entry, AgentInventoryDialogueReporter.inventorySummary(bot));
    }

    static void reportMesos(BotEntry entry, Character bot) {
        reportLine(entry, AgentCharacterDialogueReporter.mesoReport(bot));
    }

    static void reportExp(BotEntry entry, Character bot) {
        reportLine(entry, AgentCharacterDialogueReporter.expReport(bot));
    }

    static void reportInventorySlots(BotEntry entry, Character bot) {
        reportLine(entry, AgentInventoryDialogueReporter.slotsReport(bot));
    }

    static void reportScrolls(BotEntry entry, Character bot) {
        reportLine(entry, AgentInventoryDialogueReporter.scrollReport(bot));
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
