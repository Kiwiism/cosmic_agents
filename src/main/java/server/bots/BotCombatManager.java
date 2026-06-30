package server.bots;

import server.agents.capabilities.combat.AgentAttackRoute;

import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentCombatConfig;

import client.Character;
import client.inventory.WeaponType;
import server.agents.integration.AgentBotCombatAoeRepositionRuntime;
import server.agents.integration.AgentBotCombatAttackRuntime;
import server.agents.integration.AgentBotCombatBuffRuntime;
import server.agents.integration.AgentBotCombatReportRuntime;
import server.agents.integration.AgentBotCombatSkillCacheRuntime;
import server.agents.integration.AgentBotCombatHealRuntime;
import server.agents.integration.AgentBotCombatDeathRuntime;
import server.agents.integration.AgentBotCombatDamageRuntime;
import server.agents.integration.AgentBotCombatPlanRuntime;
import server.agents.integration.AgentBotCombatTargetRuntime;
import server.life.Monster;

import java.awt.*;
import java.util.List;

public class BotCombatManager {
    public static final class AttackPlan extends AgentAttackPlan {
        AttackPlan(int skillId, int skillLevel, int numDamage, Rectangle hitBox, List<Monster> targets,
                   AgentAttackRoute route, int display, int direction, int rangedDirection, int stance, int speed,
                   int hitDelayMs, int cooldownMs, WeaponType damageWeaponType) {
            super(skillId, skillLevel, numDamage, hitBox, targets, route, display, direction, rangedDirection,
                    stance, speed, hitDelayMs, cooldownMs, damageWeaponType);
        }
    }

    public static AgentCombatConfig.Config cfg = AgentCombatConfig.cfg;

    static void tickMobDamage(BotEntry entry, Character bot) {
        AgentBotCombatDamageRuntime.tickMobDamage(entry, bot, cfg, BotMovementManager::tickDown);
    }

    /**
     * Apply one physical hit from {@code mob} to the bot.
     * Uses the bot's shared character WDEF cache instead of ignoring defense entirely.
     */
    static void applyMobHit(BotEntry entry, Character bot, Monster mob) {
        AgentBotCombatDamageRuntime.applyMobHit(entry, bot, mob, cfg);
    }

    /**
     * Apply fall damage on landing. Distance is peak-to-landing descent in pixels
     * (BotPhysicsEngine tracks fall peak physics state through Agent movement
     * adapters each airborne tick and passes the delta here). Distance-based
     * rather than velocity-based because
     * terminal velocity is reached after only ~112px of fall, so velocity saturates
     * immediately while real-client damage keeps scaling with drop height.
     *
     * No packet is broadcast below threshold — matches real-client behaviour for
     * small jumps (no DAMAGE_PLAYER observed in monitored-packets logs).
     *
     * Broadcast direction is hardcoded to 0 because every captured real-client fall
     * sample used direction=0. Physics knockback still derives from bot facing so
     * the recoil arc points backward along the bot's movement direction.
     */
    static void applyFallDamage(BotEntry entry, Character bot, float fallDistancePx) {
        AgentBotCombatDamageRuntime.applyFallDamage(entry, bot, fallDistancePx, cfg);
    }

    static void enterDeadState(BotEntry entry, Character bot, boolean announceDeath) {
        AgentBotCombatDeathRuntime.enterDeadState(entry, bot, announceDeath, cfg);
    }

    static void rebuildSkillCacheIfNeeded(BotEntry entry, Character bot) {
        AgentBotCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
    }

    static void tickBuffs(BotEntry entry, Character bot) {
        AgentBotCombatBuffRuntime.tickBuffs(entry, bot, cfg);
    }

    static boolean tickSupportHealing(BotEntry entry, Character bot) {
        return AgentBotCombatHealRuntime.tickSupportHealing(entry, bot, cfg);
    }

    public static Monster findGrindTarget(BotEntry entry, Character bot) {
        return AgentBotCombatTargetRuntime.findGrindTarget(entry, bot, cfg);
    }

    static Monster findPatrolTarget(BotEntry entry, Character bot) {
        return AgentBotCombatTargetRuntime.findPatrolTarget(entry, bot, cfg);
    }

    static Monster findFollowAttackTarget(BotEntry entry, Character bot) {
        return AgentBotCombatTargetRuntime.findFollowAttackTarget(entry, bot, cfg);
    }

    static boolean isReachableGrindTarget(BotEntry entry, Character bot, Monster target) {
        return AgentBotCombatTargetRuntime.isReachableGrindTarget(entry, bot, target);
    }
    public static AttackPlan planAttack(BotEntry entry, Character bot, Monster target) {
        return toBotAttackPlan(AgentBotCombatPlanRuntime.planAttack(entry, bot, target, cfg));
    }

    private static AttackPlan toBotAttackPlan(AgentAttackPlan plan) {
        if (plan == null) {
            return null;
        }
        return new AttackPlan(plan.skillId, plan.skillLevel, plan.numDamage, plan.hitBox, plan.targets,
                plan.route, plan.display, plan.direction, plan.rangedDirection, plan.stance, plan.speed,
                plan.hitDelayMs, plan.cooldownMs, plan.damageWeaponType);
    }

    static void attackMonster(BotEntry entry, Character bot, AttackPlan attackPlan) {
        AgentBotCombatAttackRuntime.attackMonster(entry, bot, attackPlan);
    }

    static Point aoeRepositionTarget(BotEntry entry, Character bot, Monster primaryTarget, AttackPlan fireNowBest) {
        return AgentBotCombatAoeRepositionRuntime.aoeRepositionTarget(entry, bot, primaryTarget, fireNowBest, cfg);
    }
    public static String describeDebugStats(BotEntry entry, Character bot) {
        return AgentBotCombatReportRuntime.debugStatsReport(entry, bot);
    }

}



