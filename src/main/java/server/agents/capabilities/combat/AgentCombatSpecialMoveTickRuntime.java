package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import constants.skills.Brawler;
import constants.skills.ChiefBandit;
import server.StatEffect;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.perception.AgentMapPerception;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;
import client.status.MonsterStatus;

import java.awt.Rectangle;
import java.util.List;

/**
 * Automatic use of the special-move skills the combat loop used to ignore: keeps one summon up
 * while fighting and opens on a fresh grind target with a mob debuff (Slow, Seal, Threaten, Doom,
 * Ninja Ambush, the Crashes) it does not carry yet. Gated like the skill buffs: grinding or
 * following, buffs enabled, no attack cooldown, living mobs around.
 */
public final class AgentCombatSpecialMoveTickRuntime {
    /** Minimum gap between debuff casts so a mob-status roll that misses does not spam. */
    private static final long DEBUFF_INTERVAL_MS = config.AgentTuning.longValue("server.agents.capabilities.combat.AgentCombatSpecialMoveTickRuntime.DEBUFF_INTERVAL_MS");
    private static final int DEBUFF_REACH_X = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatSpecialMoveTickRuntime.DEBUFF_REACH_X");
    private static final int DEBUFF_REACH_Y = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatSpecialMoveTickRuntime.DEBUFF_REACH_Y");
    private static final int ATTACKS_BETWEEN_SPECIAL_MOVES = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatSpecialMoveTickRuntime.ATTACKS_BETWEEN_SPECIAL_MOVES");

    /** Diagnostic kill switch (the skill probe flips it for A/B runs); on by default. */
    private static volatile boolean enabled = true;

    private AgentCombatSpecialMoveTickRuntime() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void tick(AgentRuntimeEntry entry, Character bot) {
        if (!enabled || entry == null || bot == null || !bot.isAlive()
                || AgentCombatCooldownStateRuntime.hasAttackCooldown(entry)
                || !AgentCombatBuffStateRuntime.skillBuffsEnabled(entry)
                || !(AgentModeStateRuntime.following(entry) || AgentModeStateRuntime.grinding(entry))) {
            return;
        }
        if (tickSurvival(entry, bot)) {
            return;
        }
        List<Monster> monsters = AgentMapPerception.monsters(bot.getMap());
        if (monsters.stream().noneMatch(Monster::isAlive)) {
            return;
        }
        // This hook runs before the attack decision and would take every cooldown window; attacks keep
        // priority, a special move only spends a window once a couple of attacks have landed since the last.
        AgentCombatSpecialMoveState state = entry.capabilityStates().require(AgentCombatSpecialMoveState.STATE_KEY);
        if (state.specialMovesCast() > 0 && state.attacksSinceLastSpecialMove() < ATTACKS_BETWEEN_SPECIAL_MOVES) {
            return;
        }
        if (AgentCombatSummonRuntime.tickSummons(entry, bot)) {
            afterCast(entry, bot, AgentCombatSkillCacheStateRuntime.summonSkillIds(entry).get(0));
            return;
        }
        tickDebuffs(entry, bot);
    }

    /** Chakra below half HP, MP Recovery when MP is low and HP can spare it; survival outranks attack priority. */
    private static boolean tickSurvival(AgentRuntimeEntry entry, Character bot) {
        int maxHp = bot.getCurrentMaxHp();
        int maxMp = bot.getCurrentMaxMp();
        if (maxHp > 0 && bot.getHp() * 2 < maxHp && bot.getSkillLevel(ChiefBandit.CHAKRA) > 0
                && AgentCombatDebuffRuntime.tryDebuff(entry, bot, ChiefBandit.CHAKRA) == AgentCombatDebuffRuntime.DebuffResult.CAST) {
            afterCast(entry, bot, ChiefBandit.CHAKRA);
            return true;
        }
        if (maxMp > 0 && bot.getMp() * 10 < maxMp * 3 && maxHp > 0 && bot.getHp() * 10 > maxHp * 7
                && bot.getSkillLevel(Brawler.MP_RECOVERY) > 0
                && AgentCombatDebuffRuntime.tryDebuff(entry, bot, Brawler.MP_RECOVERY) == AgentCombatDebuffRuntime.DebuffResult.CAST) {
            afterCast(entry, bot, Brawler.MP_RECOVERY);
            return true;
        }
        return false;
    }

    private static void tickDebuffs(AgentRuntimeEntry entry, Character bot) {
        AgentCombatSpecialMoveState state = entry.capabilityStates().require(AgentCombatSpecialMoveState.STATE_KEY);
        long now = System.currentTimeMillis();
        if (now < state.nextDebuffAtMs()) {
            return;
        }
        Monster target = AgentGrindTargetStateRuntime.activeTargetInMap(entry, bot.getMap());
        if (target == null) {
            // The grind-objective planner sets that state; ad-hoc combat does not. Use the nearest hostile in reach.
            target = nearestHostile(bot);
        }
        if (target == null || target.isBoss() || target.getPosition() == null || bot.getPosition() == null
                || Math.abs(target.getPosition().x - bot.getPosition().x) > DEBUFF_REACH_X
                || Math.abs(target.getPosition().y - bot.getPosition().y) > DEBUFF_REACH_Y) {
            return;
        }
        for (int skillId : state.debuffSkillIds()) {
            Skill skill = AgentSkillGatewayRuntime.skills().getSkill(skillId);
            int level = skill == null ? 0 : bot.getSkillLevel(skill);
            if (level <= 0 || AgentCombatSkillClassifier.isMonsterMagnet(skillId)
                    || skillId == constants.skills.Priest.DISPEL) {
                continue;  // pulls and party dispel are not opening moves
            }
            StatEffect effect = skill.getEffect(level);
            if (alreadyCarries(target, effect) || !facesTarget(bot, target, effect)) {
                continue;
            }
            if (AgentCombatDebuffRuntime.tryDebuff(entry, bot, skillId) == AgentCombatDebuffRuntime.DebuffResult.CAST) {
                state.setNextDebuffAtMs(now + DEBUFF_INTERVAL_MS);
                afterCast(entry, bot, skillId);
                return;
            }
        }
    }

    private static Monster nearestHostile(Character bot) {
        Monster nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        if (bot.getPosition() == null || bot.getMap() == null) {
            return null;
        }
        for (Monster monster : AgentMapPerception.monsters(bot.getMap())) {
            if (!AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(monster) || monster.getPosition() == null) {
                continue;
            }
            double distSq = monster.getPosition().distanceSq(bot.getPosition());
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = monster;
            }
        }
        return nearest;
    }

    private static boolean alreadyCarries(Monster target, StatEffect effect) {
        for (MonsterStatus status : effect.getMonsterStati().keySet()) {
            if (target.isBuffed(status)) {
                return true;
            }
        }
        return false;
    }

    /** The server applies the skill box relative to the caster's facing; only cast when the target is inside it. */
    private static boolean facesTarget(Character bot, Monster target, StatEffect effect) {
        if (!effect.hasBoundingBox()) {
            return true;
        }
        Rectangle box = effect.calculateBoundingBox(bot.getPosition(), bot.isFacingLeft());
        return AgentCombatHitboxIntersection.intersectsMonster(box, target);
    }

    private static void afterCast(AgentRuntimeEntry entry, Character bot, int skillId) {
        entry.capabilityStates().require(AgentCombatSpecialMoveState.STATE_KEY).countSpecialMove();
        Skill skill = AgentSkillGatewayRuntime.skills().getSkill(skillId);
        if (skill == null) {
            return;
        }
        AgentAttackExecutionProvider.BasicAttackData fallback =
                AgentAttackExecutionProvider.buildBasicAttackData(bot, bot.getPosition());
        String action = AgentAttackExecutionProvider.resolveSkillAttackAction(bot, skill, bot.getSkillLevel(skill),
                AgentAttackExecutionProvider.getEquippedWeaponType(bot));
        AgentAttackExecutionProvider.SkillAttackTiming timing =
                AgentAttackExecutionProvider.resolveSkillAttackTiming(skill, action, bot, fallback);
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry,
                AgentCombatSupportPolicy.supportCastCooldownMs(timing.cooldownMs(), skill.getAnimationTime()));
        AgentCombatAlertRuntime.markAlerted(entry);
    }
}
