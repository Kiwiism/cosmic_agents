package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import client.Skill;
import constants.skills.BlazeWizard;
import constants.skills.Evan;
import constants.skills.Magician;
import server.StatEffect;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.integration.SkillGateway;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentPartyGatherRegistry;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

public final class AgentCombatBuffRuntime {
    private static final java.util.Set<Integer> CRITICAL_SURVIVAL_BUFFS = java.util.Set.of(
            Magician.MAGIC_GUARD, BlazeWizard.MAGIC_GUARD, Evan.MAGIC_GUARD);

    private AgentCombatBuffRuntime() {
    }

    public static void tickBuffs(AgentRuntimeEntry entry, Character bot, AgentCombatConfig.Config config) {
        tickBuffs(entry, bot, config, AgentSkillGatewayRuntime.skills());
    }

    public static void tickBuffs(AgentRuntimeEntry entry, Character bot, AgentCombatConfig.Config config,
                                 SkillGateway skills) {
        long now = System.currentTimeMillis();
        // A party gathered for buffs: the Agent buffs while it stands and waits, even in town.
        AgentPartyGatherRegistry.Gathering gathering = gathering(entry, bot, now);
        AgentCombatSupportPolicy.SkillBuffTickDecision tickDecision =
                AgentCombatSupportPolicy.skillBuffTickDecision(
                        AgentCombatCooldownStateRuntime.hasAttackCooldown(entry),
                        AgentCombatBuffStateRuntime.skillBuffsEnabled(entry),
                        AgentModeStateRuntime.following(entry) || gathering != null,
                        AgentModeStateRuntime.grinding(entry),
                        AgentCombatSkillCacheStateRuntime.hasBuffSkillIds(entry));
        if (tickDecision != AgentCombatSupportPolicy.SkillBuffTickDecision.READY) {
            if (tickDecision.debugSummary() != null) {
                AgentSkillBuffDebugStateRuntime.rememberAction(
                        entry, System.currentTimeMillis(), tickDecision.debugSummary());
            }
            return;
        }
        boolean hasLivingMobs = server.agents.perception.AgentMapPerception.monsters(bot.getMap()).stream().anyMatch(Monster::isAlive);
        if (gathering == null && AgentCombatSupportPolicy.shouldSkipSkillBuffsWithoutLivingMobs(hasLivingMobs)) return;

        if (gathering != null && gathering.stillWalking(bot.getId(), bot.getPosition(), now)) {
            return;
        }
        if (trySupportBuff(entry, bot, config, now, skills, gathering)) {
            return;
        }
        boolean heldForParty = false;

        for (int skillId : AgentCombatSkillCacheStateRuntime.buffSkillIds(entry)) {
            if (now < AgentCombatBuffStateRuntime.nextBuffAt(entry, skillId)) continue;
            if (bot.skillIsCooling(skillId)) continue;

            Skill skill = skills.getSkill(skillId);
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            if (!AgentCombatSkillClassifier.isActiveSupportSkill(skill, fx)
                    || AgentCombatSkillClassifier.isBuffBlacklisted(skill.getId())) {
                continue;
            }
            // While the party waits, only party buffs go up; the Agent's own buffs follow once it is released.
            if (gathering != null && !AgentCombatSupportPolicy.isPartyBuff(skillId, fx)) {
                continue;
            }
            if (holdForParty(gathering, skillId, fx, bot, now)) {
                heldForParty = true;
                continue;
            }
            if (castSupportSkill(entry, bot, skill, fx, now)) {
                return;
            }
        }
        if (gathering != null && !heldForParty) {
            gathering.bufferFinished(bot.getId());
        }
        AgentSkillBuffDebugStateRuntime.rememberAction(
                entry, System.currentTimeMillis(), AgentCombatSupportPolicy.allSkillBuffsActiveOrOnCooldownSummary());
    }

    private static AgentPartyGatherRegistry.Gathering gathering(AgentRuntimeEntry entry, Character bot, long now) {
        Character owner = entry.owner();
        return owner == null ? null : AgentPartyGatherRegistry.active(owner.getId(), bot.getMapId(), now);
    }

    /** During a gathering a party buff waits for the whole party to stand in its box, until the wait runs out. */
    static boolean holdForParty(AgentPartyGatherRegistry.Gathering gathering, int skillId, StatEffect effect,
                                Character bot, long now) {
        return gathering != null
                && gathering.waitingForParty(now)
                && AgentCombatSupportPolicy.isPartyBuff(skillId, effect)
                && !AgentCombatSupportPolicy.wholePartyInBox(bot, effect);
    }

    public static boolean tryCastCriticalSurvivalBuff(AgentRuntimeEntry entry, Character bot) {
        return tryCastCriticalSurvivalBuff(entry, bot, AgentSkillGatewayRuntime.skills());
    }

    /**
     * Casts an explicitly requested utility buff through the same validated
     * animation, resource, packet, and cooldown path as ordinary combat buffs.
     * Scenario capabilities own the intent; this method deliberately bypasses
     * automatic-buff selection and its blacklist without bypassing execution.
     */
    public static boolean tryCastExplicitUtilityBuff(
            AgentRuntimeEntry entry, Character bot, int skillId) {
        if (entry == null
                || bot == null
                || !bot.isAlive()
                || AgentCombatCooldownStateRuntime.hasAttackCooldown(entry)
                || AgentMovementStateRuntime.inAir(entry)
                || AgentMovementStateRuntime.climbing(entry)
                || bot.skillIsCooling(skillId)) {
            return false;
        }

        Skill skill = AgentSkillGatewayRuntime.skills().getSkill(skillId);
        int skillLevel = skill == null ? 0 : bot.getSkillLevel(skill);
        if (skillLevel <= 0) {
            return false;
        }
        StatEffect effect = skill.getEffect(skillLevel);
        if (!AgentCombatSkillClassifier.isActiveSupportSkill(skill, effect)) {
            return false;
        }
        return castSupportSkill(entry, bot, skill, effect, System.currentTimeMillis());
    }

    static boolean tryCastCriticalSurvivalBuff(AgentRuntimeEntry entry, Character bot, SkillGateway skills) {
        if (AgentCombatCooldownStateRuntime.hasAttackCooldown(entry)
                || AgentMovementStateRuntime.inAir(entry)
                || AgentMovementStateRuntime.climbing(entry)
                || !AgentCombatBuffStateRuntime.skillBuffsEnabled(entry)
                || bot == null
                || !bot.isAlive()
                || bot.getBuffedValue(BuffStat.MAGIC_GUARD) != null) {
            return false;
        }
        for (int skillId : CRITICAL_SURVIVAL_BUFFS) {
            if (!AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(skillId)) {
                continue;
            }
            if (bot.skillIsCooling(skillId)) {
                return false;
            }
            Skill skill = skills.getSkill(skillId);
            int level = skill == null ? 0 : bot.getSkillLevel(skill);
            if (level <= 0) {
                continue;
            }
            StatEffect effect = skill.getEffect(level);
            if (!AgentCombatSkillClassifier.isActiveSupportSkill(skill, effect)) {
                return false;
            }
            return castSupportSkill(entry, bot, skill, effect, System.currentTimeMillis());
        }
        return false;
    }

    private static boolean trySupportBuff(AgentRuntimeEntry entry, Character bot, AgentCombatConfig.Config config,
                                          long now, SkillGateway skills,
                                          AgentPartyGatherRegistry.Gathering gathering) {
        for (int skillId : AgentCombatSkillCacheStateRuntime.buffSkillIds(entry)) {
            if (!AgentCombatSupportPolicy.shouldConsiderSupportBuff(
                    AgentCombatSkillClassifier.isPartySupportSkill(skillId),
                    bot.skillIsCooling(skillId),
                    AgentCombatBuffStateRuntime.supportBuffOnCooldown(entry, skillId, now))) {
                continue;
            }

            Skill skill = skills.getSkill(skillId);
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) {
                continue;
            }

            StatEffect fx = skill.getEffect(lvl);
            // The server hands a party buff to members inside its box, not a radius; a member just
            // outside it never gets the buff, so counting them kept the Agent recasting every few seconds.
            boolean someoneNeedsIt = fx.hasBoundingBox()
                    ? AgentCombatSupportPolicy.hasPartyMemberInBoxMissingBuff(bot, fx)
                    : AgentCombatSupportPolicy.hasNearbyPartyMemberMissingBuff(
                            bot, fx, config.SUPPORT_RANGE, config.SUPPORT_VERTICAL_RANGE);
            if (!someoneNeedsIt || holdForParty(gathering, skillId, fx, bot, now)) {
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
                AgentCombatSupportPolicy.supportCastReadiness(
                        skillLevel,
                        bot.isAlive(),
                        () -> AgentCombatWeaponPolicy.canUseSkillWithWeapon(
                                skill.getId(), AgentAttackExecutionProvider.getEquippedWeaponType(bot))
                                && fx.canPaySkillCost(bot));
        String readinessSummary = readiness.debugSummary(
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
