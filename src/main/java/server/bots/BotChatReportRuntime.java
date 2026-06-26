package server.bots;

import client.Character;
import server.agents.capabilities.dialogue.AgentCharacterDialogueReporter;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.capabilities.dialogue.AgentChatReportFlow;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;
import server.agents.capabilities.dialogue.AgentMovementDialogueReporter;
import server.agents.capabilities.dialogue.AgentSkillDialogueReporter;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;
import server.agents.capabilities.dialogue.AgentSupplyDialogueReporter;
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

    static void reportStats(BotEntry entry, Character bot) {
        BotChatManager.queueBotReply(entry, AgentCharacterDialogueReporter.statsReport(bot));
    }

    static void reportRange(BotEntry entry, Character bot) {
        BotChatManager.queueBotReply(entry, buildRangeReport(bot));
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
        for (String line : buildMovementStatsReport(bot)) {
            BotChatManager.queueBotReply(entry, line);
        }
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
        BotChatManager.queueBotReply(entry, AgentCharacterDialogueReporter.buildReport(bot));
    }

    static void reportSkills(BotEntry entry, Character bot) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        List<AgentSkillReportFlow.SkillLine> beginnerSkills =
                AgentSkillDialogueReporter.collectLearnedBeginnerSkills(bot);
        int beginnerSpLeft = AgentSkillDialogueReporter.remainingBeginnerSp(bot);
        BotChatManager.applySkillReportDecision(entry, AgentSkillReportFlow.reportSkills(
                bot.isBeginnerJob(),
                bot.getRemainingSp(),
                beginnerSkills,
                beginnerSpLeft,
                skillTrees));
    }

    static void reportInventory(BotEntry entry, Character bot) {
        BotChatManager.queueBotReply(entry, AgentInventoryDialogueReporter.inventorySummary(bot));
    }

    static void reportMesos(BotEntry entry, Character bot) {
        BotChatManager.queueBotReply(entry, AgentCharacterDialogueReporter.mesoReport(bot));
    }

    static void reportExp(BotEntry entry, Character bot) {
        BotChatManager.queueBotReply(entry, AgentCharacterDialogueReporter.expReport(bot));
    }

    static void reportInventorySlots(BotEntry entry, Character bot) {
        BotChatManager.queueBotReply(entry, AgentInventoryDialogueReporter.slotsReport(bot));
    }

    static void reportScrolls(BotEntry entry, Character bot) {
        BotChatManager.queueBotReply(entry, AgentInventoryDialogueReporter.scrollReport(bot));
    }

    static void reportPotions(BotEntry entry, Character bot) {
        int[] counts = BotPotionManager.countPotions(bot);
        BotChatManager.queueBotReply(entry, AgentSupplyDialogueReporter.potionReport(counts));
    }

    static void reportPotDebug(BotEntry entry, Character bot) {
        BotChatManager.queueBotReply(entry, BotPotionManager.autopotDebugReport(bot));
    }

    static void reportDebugStats(BotEntry entry, Character bot) {
        BotChatManager.queueBotReply(entry, BotCombatManager.describeDebugStats(entry, bot));
    }

    static void reportCritDebug(BotEntry entry, Character bot) {
        CombatFormulaProvider formula = CombatFormulaProvider.getInstance();
        CombatFormulaProvider.CritProfile crit = formula.resolveCritProfile(bot);
        CombatFormulaProvider.DamageProfile dmg = formula.resolveDamageProfile(bot, 0, 0, false);
        BotChatManager.queueBotReply(entry, AgentCombatDialogueReporter.critReport(crit, dmg));
    }

    static void reportBuffDebug(BotEntry entry, Character bot) {
        for (String line : BotBuffManager.getDebugLines(entry, bot)) {
            BotChatManager.queueBotReply(entry, line);
        }
    }

    static void reportSkillBuffDebug(BotEntry entry, Character bot) {
        for (String line : BotCombatManager.getSkillBuffDebugLines(entry, bot)) {
            BotChatManager.queueBotReply(entry, line);
        }
    }

    static void reportHelp(BotEntry entry) {
        for (String line : AgentChatReportFlow.helpLines()) {
            BotChatManager.queueBotReply(entry, line);
        }
    }

    static void reportRecommendedGear(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        if (owner == null) {
            BotChatManager.queueBotReply(entry, AgentChatEquipmentFlow.gearCheckUnavailableReply());
            return;
        }
        if (!BotOfferManager.offerBestRecommendedGear(entry, bot, owner)) {
            BotChatManager.queueBotReply(entry, AgentChatEquipmentFlow.noBetterGearReply());
        }
        entry.nextGearSuggestionAt = System.currentTimeMillis() + 60_000L;
    }
}
