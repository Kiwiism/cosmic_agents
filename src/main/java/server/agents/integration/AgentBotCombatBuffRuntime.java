package server.agents.integration;

import client.Character;
import client.Skill;
import client.SkillFactory;
import server.StatEffect;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatSkillClassifier;
import server.agents.capabilities.combat.AgentCombatSupportPolicy;
import server.agents.capabilities.combat.AgentSupportSpecialMoveExecutor;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.bots.BotEntry;
import server.life.Monster;

public final class AgentBotCombatBuffRuntime {
    private AgentBotCombatBuffRuntime() {
    }

    public static void tickBuffs(BotEntry entry, Character bot, AgentCombatConfig.Config config) {
        AgentCombatSupportPolicy.SkillBuffTickDecision tickDecision =
                AgentCombatSupportPolicy.skillBuffTickDecision(
                        AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry),
                        AgentBotCombatBuffStateRuntime.skillBuffsEnabled(entry),
                        AgentBotModeStateRuntime.following(entry),
                        AgentBotModeStateRuntime.grinding(entry),
                        AgentBotCombatSkillCacheStateRuntime.hasBuffSkillIds(entry));
        if (tickDecision != AgentCombatSupportPolicy.SkillBuffTickDecision.READY) {
            if (tickDecision.legacyDebugSummary() != null) {
                AgentBotSkillBuffDebugStateRuntime.rememberAction(
                        entry, System.currentTimeMillis(), tickDecision.legacyDebugSummary());
            }
            return;
        }
        boolean hasLivingMobs = bot.getMap().getAllMonsters().stream().anyMatch(Monster::isAlive);
        if (AgentCombatSupportPolicy.shouldSkipSkillBuffsWithoutLivingMobs(hasLivingMobs)) return;

        long now = System.currentTimeMillis();
        if (trySupportBuff(entry, bot, config, now)) {
            return;
        }

        for (int skillId : AgentBotCombatSkillCacheStateRuntime.buffSkillIds(entry)) {
            if (now < AgentBotCombatBuffStateRuntime.nextBuffAt(entry, skillId)) continue;
            if (bot.skillIsCooling(skillId)) continue;

            Skill skill = SkillFactory.getSkill(skillId);
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            if (!AgentCombatSkillClassifier.isActiveSupportSkill(skill, fx)
                    || AgentCombatSkillClassifier.isBuffBlacklisted(skill.getId())) {
                continue;
            }
            if (castSupportSkill(entry, bot, skill, fx, now)) {
                return;
            }
        }
        AgentBotSkillBuffDebugStateRuntime.rememberAction(
                entry, System.currentTimeMillis(), AgentCombatSupportPolicy.allSkillBuffsActiveOrOnCooldownSummary());
    }

    private static boolean trySupportBuff(BotEntry entry, Character bot, AgentCombatConfig.Config config, long now) {
        for (int skillId : AgentBotCombatSkillCacheStateRuntime.buffSkillIds(entry)) {
            if (!AgentCombatSupportPolicy.shouldConsiderSupportBuff(
                    AgentCombatSkillClassifier.isPartySupportSkill(skillId),
                    bot.skillIsCooling(skillId),
                    AgentBotCombatBuffStateRuntime.supportBuffOnCooldown(entry, skillId, now))) {
                continue;
            }

            Skill skill = SkillFactory.getSkill(skillId);
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) {
                continue;
            }

            StatEffect fx = skill.getEffect(lvl);
            if (!AgentCombatSupportPolicy.hasNearbyPartyMemberMissingBuff(
                    bot, fx, config.SUPPORT_RANGE, config.SUPPORT_VERTICAL_RANGE)) {
                continue;
            }

            if (castSupportSkill(entry, bot, skill, fx, now)) {
                AgentBotCombatBuffStateRuntime.setNextSupportBuffAt(
                        entry, skillId, now + config.SUPPORT_REBUFF_CD_MS);
                return true;
            }
        }

        return false;
    }

    private static boolean castSupportSkill(BotEntry entry, Character bot, Skill skill, StatEffect fx, long now) {
        int skillLevel = bot.getSkillLevel(skill);
        AgentCombatSupportPolicy.SupportCastReadiness readiness =
                AgentCombatSupportPolicy.supportCastReadiness(skillLevel, bot.isAlive(), () -> fx.canPaySkillCost(bot));
        String readinessSummary = readiness.legacyDebugSummary(
                AgentCombatDialogueReporter.combatSkillLabel(skill.getId()));
        if (readinessSummary != null) {
            AgentBotSkillBuffDebugStateRuntime.rememberAction(entry, System.currentTimeMillis(), readinessSummary);
            return false;
        }
        if (!AgentSupportSpecialMoveExecutor.dispatch(bot, skill, skillLevel)) {
            AgentBotSkillBuffDebugStateRuntime.rememberAction(
                    entry,
                    System.currentTimeMillis(),
                    AgentCombatSupportPolicy.supportSpecialMoveFailedSummary(
                            AgentCombatDialogueReporter.combatSkillLabel(skill.getId())));
            return false;
        }

        long dur = fx.getDuration();
        if (dur > 0) {
            AgentBotCombatBuffStateRuntime.setNextBuffAt(
                    entry, skill.getId(), AgentCombatSupportPolicy.nextSupportBuffRefreshAt(now, dur));
        }
        AgentAttackExecutionProvider.BasicAttackData fallbackAttackData =
                AgentAttackExecutionProvider.buildBasicAttackData(bot, bot.getPosition());
        String action = AgentAttackExecutionProvider.resolveSkillAttackAction(bot, skill, skillLevel,
                AgentAttackExecutionProvider.getEquippedWeaponType(bot));
        AgentAttackExecutionProvider.SkillAttackTiming skillTiming =
                AgentAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        AgentBotCombatCooldownStateRuntime.maxAttackCooldown(entry,
                AgentCombatSupportPolicy.supportCastCooldownMs(skillTiming.cooldownMs(), skill.getAnimationTime()));
        AgentBotCombatAlertRuntime.markAlerted(entry);
        AgentBotSkillBuffDebugStateRuntime.rememberAction(
                entry,
                System.currentTimeMillis(),
                AgentCombatSupportPolicy.supportCastSummary(
                        AgentCombatDialogueReporter.combatSkillLabel(skill.getId())));
        return true;
    }
}
