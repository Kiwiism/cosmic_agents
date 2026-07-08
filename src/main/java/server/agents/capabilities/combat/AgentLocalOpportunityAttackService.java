package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.integration.AgentAmmoStateRuntime;
import server.agents.integration.AgentCombatAttackRuntime;
import server.agents.integration.AgentCombatCooldownStateRuntime;
import server.agents.integration.AgentCombatPlanRuntime;
import server.agents.integration.AgentCombatTargetRuntime;
import server.agents.integration.AgentDegenerateAttackStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;

public final class AgentLocalOpportunityAttackService {
    private AgentLocalOpportunityAttackService() {
    }

    public record Result(boolean consumedTick, Point targetPos) {
    }

    public record Hooks(GrindNavigationTargetSelector grindNavigationTargetSelector,
                        JumpHeightCalculator jumpHeightCalculator,
                        JumpInitiator jumpInitiator,
                        LocalAttackMoveWindowSetter localAttackMoveWindowSetter) {
    }

    @FunctionalInterface
    public interface GrindNavigationTargetSelector {
        Point select(AgentRuntimeEntry entry, Point botPos, Point combatTargetPos);
    }

    @FunctionalInterface
    public interface JumpHeightCalculator {
        float calculate(AgentMovementProfile movementProfile);
    }

    @FunctionalInterface
    public interface JumpInitiator {
        void initiate(AgentRuntimeEntry entry, Character agent, int dx);
    }

    @FunctionalInterface
    public interface LocalAttackMoveWindowSetter {
        void set(AgentRuntimeEntry entry, Point agentPos, Point referencePos);
    }

    public static Result tryLocalOpportunityAttack(AgentRuntimeEntry entry,
                                                   Character agent,
                                                   Point agentPos,
                                                   Point movementTargetPos,
                                                   Point moveWindowReferencePos,
                                                   boolean allowCombatMovement,
                                                   boolean allowJumpTowardTarget,
                                                   Hooks hooks) {
        Point targetPos = movementTargetPos;
        if (AgentAmmoStateRuntime.noAmmo(entry) || agent == null || agentPos == null) {
            return new Result(false, targetPos);
        }

        Monster localTarget = AgentCombatTargetRuntime.findFollowAttackTarget(entry, agent, AgentCombatConfig.cfg);
        if (localTarget == null) {
            return new Result(false, targetPos);
        }

        Point localTargetPos = localTarget.getPosition();
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(agent);
        boolean shouldRetreat = allowCombatMovement
                && (AgentDegenerateAttackStateRuntime.degenAttackDone(entry)
                || AgentAttackExecutionProvider.shouldRetreatFromNearbyTarget(weaponType, agentPos, localTargetPos)
                || AgentAttackExecutionProvider.isAnyMobNearerThanTarget(agent, agentPos, localTargetPos));
        if (shouldRetreat) {
            AgentDegenerateAttackStateRuntime.clear(entry);
            return new Result(
                    false, hooks.grindNavigationTargetSelector().select(entry, agentPos, localTargetPos));
        }

        AgentAttackPlan attackPlan = AgentCombatPlanRuntime.planAttack(entry, agent, localTarget, AgentCombatConfig.cfg);
        if (attackPlan == null) {
            return new Result(false, targetPos);
        }
        if (AgentMovementStateRuntime.inAir(entry)) {
            if (AgentCombatRangePolicy.canUseAttackPlanNow(
                    AgentMovementStateRuntime.grounded(entry), weaponType, attackPlan.route)
                    && AgentCombatRangePolicy.isTargetInAttackRange(attackPlan, agent, localTarget)) {
                AgentCombatAttackRuntime.attackMonster(entry, agent, attackPlan);
                if (allowCombatMovement && attackPlan.isCloseRangeRoute()
                        && AgentCombatAmmoCounter.isRangedAmmoWeapon(weaponType)) {
                    AgentDegenerateAttackStateRuntime.markDegenAttackDone(entry);
                }
            }
            return new Result(false, targetPos);
        }

        if (allowJumpTowardTarget
                && weaponType != WeaponType.BOW && weaponType != WeaponType.CROSSBOW
                && weaponType != WeaponType.WAND && weaponType != WeaponType.STAFF
                && AgentCombatRangePolicy.isTargetJumpable(
                        movementProfile(entry),
                        true,
                        agentPos,
                        localTargetPos,
                        hooks.jumpHeightCalculator().calculate(movementProfile(entry)))) {
            hooks.jumpInitiator().initiate(entry, agent, localTargetPos.x - agentPos.x);
            return new Result(true, targetPos);
        }

        if (!AgentCombatCooldownStateRuntime.hasMoveWindow(entry)
                && AgentCombatRangePolicy.isTargetInAttackRange(attackPlan, agent, localTarget)) {
            AgentCombatAttackRuntime.attackMonster(entry, agent, attackPlan);
            hooks.localAttackMoveWindowSetter().set(entry, agentPos, moveWindowReferencePos);
            if (allowCombatMovement && attackPlan.isCloseRangeRoute()
                    && AgentCombatAmmoCounter.isRangedAmmoWeapon(weaponType)) {
                AgentDegenerateAttackStateRuntime.markDegenAttackDone(entry);
            }
            return new Result(!AgentMovementStateRuntime.inAir(entry), targetPos);
        }

        return new Result(false, targetPos);
    }

    private static AgentMovementProfile movementProfile(AgentRuntimeEntry entry) {
        return AgentMovementStateRuntime.movementProfile(entry);
    }
}
