package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import client.SkillFactory;
import server.StatEffect;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

public final class AgentCombatBuffRuntime {
    private AgentCombatBuffRuntime() {
    }

    public static void tickBuffs(AgentRuntimeEntry entry, Character bot, AgentCombatConfig.Config config) {
        AgentCombatSupportPolicy.SkillBuffTickDecision tickDecision =
                AgentCombatSupportPolicy.skillBuffTickDecision(
                        AgentCombatCooldownStateRuntime.hasAttackCooldown(entry),
                        AgentCombatBuffStateRuntime.skillBuffsEnabled(entry),
                        AgentModeStateRuntime.following(entry),
                        AgentModeStateRuntime.grinding(entry),
                        AgentCombatSkillCacheStateRuntime.hasBuffSkillIds(entry));
        if (tickDecision != AgentCombatSupportPolicy.SkillBuffTickDecision.READY) {
            if (tickDecision.legacyDebugSummary() != null) {
                AgentSkillBuffDebugStateRuntime.rememberAction(
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

        for (int skillId : AgentCombatSkillCacheStateRuntime.buffSkillIds(entry)) {
            if (now < AgentCombatBuffStateRuntime.nextBuffAt(entry, skillId)) continue;
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
        AgentSkillBuffDebugStateRuntime.rememberAction(
                entry, System.currentTimeMillis(), AgentCombatSupportPolicy.allSkillBuffsActiveOrOnCooldownSummary());
    }

    private static boolean trySupportBuff(AgentRuntimeEntry entry, Character bot, AgentCombatConfig.Config config, long now) {
        for (int skillId : AgentCombatSkillCacheStateRuntime.buffSkillIds(entry)) {
            if (!AgentCombatSupportPolicy.shouldConsiderSupportBuff(
                    AgentCombatSkillClassifier.isPartySupportSkill(skillId),
                    bot.skillIsCooling(skillId),
                    AgentCombatBuffStateRuntime.supportBuffOnCooldown(entry, skillId, now))) {
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
                AgentCombatBuffStateRuntime.setNextSupportBuffAt(
                        entry, skillId, now + config.SUPPORT_REBUFF_CD_MS);
                return true;
            }
        }

        return false;
    }

    private static boolean castSupportSkill(AgentRuntimeEntry entry, Character bot, Skill skill, StatEffect fx, long now) {
        int skillLevel = bot.getSkillLevel(skill);
        AgentCombatSupportPolicy.SupportCastReadiness readiness =
                AgentCombatSupportPolicy.supportCastReadiness(skillLevel, bot.isAlive(), () -> fx.canPaySkillCost(bot));
        String readinessSummary = readiness.legacyDebugSummary(
                AgentCombatDialogueReporter.combatSkillLabel(skill.getId()));
        if (readinessSummary != null) {
            AgentSkillBuffDebugStateRuntime.rememberAction(entry, System.currentTimeMillis(), readinessSummary);
            return false;
        }
        if (!AgentSupportSpecialMoveExecutor.dispatch(bot, skill, skillLevel)) {
            AgentSkillBuffDebugStateRuntime.rememberAction(
                    entry,
                    System.currentTimeMillis(),
                    AgentCombatSupportPolicy.supportSpecialMoveFailedSummary(
                            AgentCombatDialogueReporter.combatSkillLabel(skill.getId())));
            return false;
        }

        long dur = fx.getDuration();
        if (dur > 0) {
            AgentCombatBuffStateRuntime.setNextBuffAt(
                    entry, skill.getId(), AgentCombatSupportPolicy.nextSupportBuffRefreshAt(now, dur));
        }
        AgentAttackExecutionProvider.BasicAttackData fallbackAttackData =
                AgentAttackExecutionProvider.buildBasicAttackData(bot, bot.getPosition());
        String action = AgentAttackExecutionProvider.resolveSkillAttackAction(bot, skill, skillLevel,
                AgentAttackExecutionProvider.getEquippedWeaponType(bot));
        AgentAttackExecutionProvider.SkillAttackTiming skillTiming =
                AgentAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallbackAttackData);
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry,
                AgentCombatSupportPolicy.supportCastCooldownMs(skillTiming.cooldownMs(), skill.getAnimationTime()));
        AgentCombatAlertRuntime.markAlerted(entry);
        AgentSkillBuffDebugStateRuntime.rememberAction(
                entry,
                System.currentTimeMillis(),
                AgentCombatSupportPolicy.supportCastSummary(
                        AgentCombatDialogueReporter.combatSkillLabel(skill.getId())));
        return true;
    }
}
