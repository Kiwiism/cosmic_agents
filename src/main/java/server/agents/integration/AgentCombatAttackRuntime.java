package server.agents.integration;

import server.agents.capabilities.combat.AgentCombatAlertRuntime;
import server.agents.capabilities.supplies.AgentAmmoStateRuntime;

import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;

import client.Character;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentAttackRoute;
import server.agents.capabilities.combat.AgentCombatAttackExecutionPolicy;
import server.agents.capabilities.combat.AgentCombatFacingRuntime;
import server.agents.capabilities.combat.AgentCombatRangePolicy;
import server.agents.capabilities.combat.AgentCombatSkillUsePolicy;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.combat.CombatFormulaProvider;
import server.life.Monster;

import java.util.HashMap;

public final class AgentCombatAttackRuntime {
    private AgentCombatAttackRuntime() {
    }

    public static void attackMonster(AgentRuntimeEntry entry, Character bot, AgentAttackPlan attackPlan) {
        AgentCombatAttackExecutionPolicy.AttackExecutionReadiness readiness =
                AgentCombatAttackExecutionPolicy.attackExecutionReadiness(
                        AgentCombatCooldownStateRuntime.hasAttackCooldown(entry),
                        AgentAmmoStateRuntime.noAmmo(entry),
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

        int numAttacked = attackPlan.targets.size();
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill = attackPlan.skillId;
        attack.skilllevel = attackPlan.skillLevel;
        attack.numDamage = attackPlan.numDamage;
        attack.numAttacked = numAttacked;
        attack.numAttackedAndDamage = (numAttacked << 4) | attackPlan.numDamage;
        attack.speed = attackPlan.speed;
        attack.stance = attackPlan.stance;
        attack.display = attackPlan.display;
        attack.direction = attackPlan.direction;
        attack.rangedirection = attackPlan.rangedDirection;
        attack.ranged = attackPlan.route == AgentAttackRoute.RANGED;
        CombatFormulaProvider.DamageProfile damageProfile = CombatFormulaProvider.getInstance().resolveDamageProfile(
                bot, attackPlan.skillId, attackPlan.skillLevel,
                attackPlan.route == AgentAttackRoute.MAGIC, attackPlan.damageWeaponType);
        attack.magic = damageProfile.magicAttack();
        attack.targets = new HashMap<>();

        for (Monster target : attackPlan.targets) {
            attack.targets.put(target.getObjectId(),
                    CombatFormulaProvider.getInstance().makeTarget(bot, target, attackPlan.numDamage,
                            attackPlan.skillId, damageProfile, attackPlan.hitDelayMs));
        }

        AgentAttackExecutionProvider.applyAttackRoute(attackPlan.route, attack, bot);
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, attackPlan.cooldownMs);
        AgentCombatFacingRuntime.rememberAttackFacing(entry, attackPlan.stance);
        AgentCombatAlertRuntime.markAlerted(entry);
    }
}
