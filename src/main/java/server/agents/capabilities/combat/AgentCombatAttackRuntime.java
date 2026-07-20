package server.agents.capabilities.combat;

import server.agents.capabilities.supplies.AgentAmmoStateRuntime;

import client.Character;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.combat.CombatFormulaProvider;
import server.life.Monster;

import java.util.HashMap;

public final class AgentCombatAttackRuntime {
    private AgentCombatAttackRuntime() {
    }

    public static void attackMonster(AgentRuntimeEntry entry, Character bot, AgentAttackPlan attackPlan) {
        if (entry == null || bot == null || attackPlan == null || attackPlan.targets.isEmpty()) {
            return;
        }
        Monster primaryTarget = attackPlan.targets.get(0);
        if (primaryTarget == null || !primaryTarget.isAlive()) {
            return;
        }
        AgentCombatAttackExecutionPolicy.AttackExecutionReadiness readiness =
                AgentCombatAttackExecutionPolicy.attackExecutionReadiness(
                        AgentCombatCooldownStateRuntime.hasAttackCooldown(entry),
                        AgentAmmoStateRuntime.noAmmo(entry) && attackPlan.route == AgentAttackRoute.RANGED,
                        attackPlan.skillId,
                        () -> AgentCombatSkillUsePolicy.canPaySkillCost(
                                bot, attackPlan.skillId, attackPlan.skillLevel),
                        () -> entry != null && attackPlan != null && AgentCombatRangePolicy.canUseAttackPlanNow(
                                AgentMovementStateRuntime.grounded(entry),
                                AgentAttackExecutionProvider.getEquippedWeaponType(bot),
                                attackPlan.route));
        if (readiness != AgentCombatAttackExecutionPolicy.AttackExecutionReadiness.READY) {
            return;
        }

        int numAttacked = AgentAttackPacketPolicy.targetCount(attackPlan.targets.size());
        int numDamage = AgentAttackPacketPolicy.damageLineCount(attackPlan.numDamage);
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill = attackPlan.skillId;
        attack.skilllevel = attackPlan.skillLevel;
        attack.numDamage = numDamage;
        attack.numAttacked = numAttacked;
        attack.numAttackedAndDamage = AgentAttackPacketPolicy.packCounts(numAttacked, numDamage);
        attack.speed = attackPlan.speed;
        attack.stance = attackPlan.stance;
        attack.display = attackPlan.display;
        attack.direction = attackPlan.direction;
        attack.rangedirection = attackPlan.rangedDirection;
        attack.ranged = attackPlan.route == AgentAttackRoute.RANGED;
        CombatFormulaProvider.DamageProfile damageProfile = AgentAttackDamageProfileService.resolve(bot, attackPlan);
        attack.magic = damageProfile.magicAttack();
        attack.targets = new HashMap<>();

        for (Monster target : attackPlan.targets.subList(0, numAttacked)) {
            attack.targets.put(target.getObjectId(),
                    CombatFormulaProvider.getInstance().makeTarget(bot, target, numDamage,
                            attackPlan.skillId, damageProfile, attackPlan.hitDelayMs));
        }

        if (!AgentAttackExecutionProvider.applyAttackRoute(attackPlan.route, attack, bot)) {
            return;
        }
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, attackPlan.cooldownMs);
        AgentCombatFacingRuntime.rememberAttackFacing(entry, attackPlan.stance);
        AgentCombatAlertRuntime.markAlerted(entry);
    }

}
