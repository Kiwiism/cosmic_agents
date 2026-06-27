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

    static AgentChatReportFlow.ReportCallbacks reportCallbacks(BotEntry entry) {
        return new AgentChatReportFlow.ReportCallbacks() {
            @Override
            public void help() {
                BotManager.after(BotManager.randMs(500, 700), () -> reportHelp(entry));
            }

            @Override
            public void requestUpgrade() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotChatSupplyRuntime.handleRequestUpgradeCommand(entry, entry.bot));
            }

            @Override
            public void recommendedGear() {
                BotManager.after(BotManager.randMs(500, 700), () -> reportRecommendedGear(entry, entry.bot));
            }

            @Override
            public void skills() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportSkills(entry, entry.bot));
            }

            @Override
            public void stats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportStats(entry, entry.bot));
            }

            @Override
            public void movementStats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportMovementStats(entry, entry.bot));
            }

            @Override
            public void range() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportRange(entry, entry.bot));
            }

            @Override
            public void build() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportBuild(entry, entry.bot));
            }

            @Override
            public void inventory() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportInventory(entry, entry.bot));
            }

            @Override
            public void mesos() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportMesos(entry, entry.bot));
            }

            @Override
            public void exp() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportExp(entry, entry.bot));
            }

            @Override
            public void inventorySlots() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportInventorySlots(entry, entry.bot));
            }

            @Override
            public void scrolls() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportScrolls(entry, entry.bot));
            }

            @Override
            public void potions() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportPotions(entry, entry.bot));
            }

            @Override
            public void debugStats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportDebugStats(entry, entry.bot));
            }

            @Override
            public void critDebug() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportCritDebug(entry, entry.bot));
            }

            @Override
            public void potDebug() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportPotDebug(entry, entry.bot));
            }
        };
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
        BotChatPendingActionRuntime.applySkillReportDecision(entry, AgentSkillReportFlow.reportSkills(
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
