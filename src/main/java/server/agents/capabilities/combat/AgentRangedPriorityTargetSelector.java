package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import server.agents.integration.AgentBotAmmoStateRuntime;
import server.agents.integration.AgentBotCombatPlanRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.life.Monster;

import java.awt.Point;

/**
 * Agent-owned selector for replacing degenerate ranged targets with a valid ranged target.
 */
public final class AgentRangedPriorityTargetSelector {
    private AgentRangedPriorityTargetSelector() {
    }

    public static Monster selectPriorityRangedAttackTarget(BotEntry entry,
                                                          Character agent,
                                                          Point agentPosition,
                                                          Monster preferredTarget) {
        if (entry == null || AgentBotAmmoStateRuntime.noAmmo(entry) || agent == null || agentPosition == null) {
            return null;
        }

        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(agent);
        if (!AgentCombatAmmoCounter.isRangedAmmoWeapon(weaponType)) {
            return null;
        }
        if (isNonDegenerateRangedAttackTarget(entry, agent, agentPosition, weaponType, preferredTarget)) {
            return preferredTarget;
        }

        Monster best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (Monster candidate : agent.getMap().getAllMonsters()) {
            if (candidate == preferredTarget) {
                continue;
            }
            if (!isNonDegenerateRangedAttackTarget(entry, agent, agentPosition, weaponType, candidate)) {
                continue;
            }
            double distanceSq = candidate.getPosition().distanceSq(agentPosition);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isNonDegenerateRangedAttackTarget(BotEntry entry,
                                                            Character agent,
                                                            Point agentPosition,
                                                            WeaponType weaponType,
                                                            Monster target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        Point targetPos = target.getPosition();
        if (AgentAttackExecutionProvider.shouldDegenerateRangedAttack(weaponType, agentPosition, targetPos)) {
            return false;
        }
        AgentAttackPlan plan = AgentBotCombatPlanRuntime.planAttack(entry, agent, target, AgentCombatConfig.cfg);
        return plan != null
                && plan.route == AgentAttackRoute.RANGED
                && AgentCombatRangePolicy.isTargetInAttackRange(plan, agent, target)
                && AgentCombatRangePolicy.canUseAttackPlanNow(
                        AgentBotMovementStateRuntime.grounded(entry), weaponType, plan.route);
    }
}
