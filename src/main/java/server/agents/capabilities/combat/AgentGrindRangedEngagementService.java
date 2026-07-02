package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.life.Monster;

import java.awt.Point;

public final class AgentGrindRangedEngagementService {
    private AgentGrindRangedEngagementService() {
    }

    public record Result(boolean consumedTick,
                         Point targetPos,
                         Point crossRegionRetreatPos,
                         Point aoeRepositionPos,
                         boolean shouldRetreatForRangedSpacing,
                         boolean attackAttemptedInRange,
                         WeaponType weaponType) {
    }

    public record Hooks(WeaponTypeResolver weaponTypeResolver,
                        DegenerateAttackPolicy degenerateAttackPolicy,
                        RetreatPolicy retreatPolicy,
                        CrossRegionRetreatSelector crossRegionRetreatSelector,
                        TargetRangePolicy targetRangePolicy,
                        AoeRepositionResolver aoeRepositionResolver,
                        AttackPlanUsabilityPolicy attackPlanUsabilityPolicy,
                        AttackExecutor attackExecutor,
                        RangedAmmoWeaponPolicy rangedAmmoWeaponPolicy,
                        TargetJumpablePolicy targetJumpablePolicy,
                        JumpHeightCalculator jumpHeightCalculator,
                        JumpInitiator jumpInitiator,
                        IdleOnGround idleOnGround,
                        MovementBroadcaster movementBroadcaster) {
    }

    @FunctionalInterface
    public interface WeaponTypeResolver {
        WeaponType resolve(Character agent);
    }

    @FunctionalInterface
    public interface DegenerateAttackPolicy {
        boolean shouldDegenerate(WeaponType weaponType, Point agentPosition, Point targetPosition);
    }

    @FunctionalInterface
    public interface RetreatPolicy {
        boolean shouldRetreat(WeaponType weaponType, Point agentPosition, Point targetPosition);
    }

    @FunctionalInterface
    public interface CrossRegionRetreatSelector {
        Point select(BotEntry entry, Point agentPosition, Point targetPosition);
    }

    @FunctionalInterface
    public interface TargetRangePolicy {
        boolean isInRange(AgentAttackPlan attackPlan, Character agent, Monster target);
    }

    @FunctionalInterface
    public interface AoeRepositionResolver {
        Point resolve(BotEntry entry, Character agent, Monster target, AgentAttackPlan attackPlan, Point agentPosition);
    }

    @FunctionalInterface
    public interface AttackPlanUsabilityPolicy {
        boolean canUse(boolean grounded, WeaponType weaponType, AgentAttackRoute route);
    }

    @FunctionalInterface
    public interface AttackExecutor {
        void attack(BotEntry entry, Character agent, AgentAttackPlan attackPlan);
    }

    @FunctionalInterface
    public interface RangedAmmoWeaponPolicy {
        boolean isRangedAmmoWeapon(WeaponType weaponType);
    }

    @FunctionalInterface
    public interface TargetJumpablePolicy {
        boolean isJumpable(AgentMovementProfile movementProfile,
                           boolean closeRangeRoute,
                           Point agentPosition,
                           Point targetPosition,
                           float maxJumpHeight);
    }

    @FunctionalInterface
    public interface JumpHeightCalculator {
        float calculate(AgentMovementProfile movementProfile);
    }

    @FunctionalInterface
    public interface JumpInitiator {
        void initiate(BotEntry entry, Character agent, int dx);
    }

    @FunctionalInterface
    public interface IdleOnGround {
        void idle(BotEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface MovementBroadcaster {
        void broadcast(BotEntry entry);
    }

    public static Result engage(BotEntry entry,
                                Character agent,
                                Point agentPosition,
                                Point currentMovementTarget,
                                Monster target,
                                Point targetPosition,
                                AgentAttackPlan attackPlan,
                                Monster rangedPriorityTarget,
                                Hooks hooks) {
        WeaponType weaponType = hooks.weaponTypeResolver().resolve(agent);
        boolean targetInDegenerateBand = hooks.degenerateAttackPolicy().shouldDegenerate(
                weaponType, agentPosition, targetPosition);
        boolean degenAttackDone = AgentBotDegenerateAttackStateRuntime.degenAttackDone(entry);
        boolean allowOneDegenerateAttack = targetInDegenerateBand && !degenAttackDone && rangedPriorityTarget == null;
        boolean shouldRetreatForRangedSpacing = degenAttackDone
                || (hooks.retreatPolicy().shouldRetreat(weaponType, agentPosition, targetPosition)
                && !allowOneDegenerateAttack);
        boolean canFireWithoutDegen = weaponType == null
                || !hooks.degenerateAttackPolicy().shouldDegenerate(weaponType, agentPosition, targetPosition);
        boolean attackGateOpen = !shouldRetreatForRangedSpacing || canFireWithoutDegen || allowOneDegenerateAttack;
        Point crossRegionRetreatPos = shouldRetreatForRangedSpacing
                ? hooks.crossRegionRetreatSelector().select(entry, agentPosition, targetPosition)
                : null;
        Point aoeRepositionPos = (!shouldRetreatForRangedSpacing && crossRegionRetreatPos == null
                && attackGateOpen && hooks.targetRangePolicy().isInRange(attackPlan, agent, target))
                ? hooks.aoeRepositionResolver().resolve(entry, agent, target, attackPlan, agentPosition)
                : null;

        boolean attackAttemptedInRange = false;
        if (!AgentBotMovementStateRuntime.climbing(entry)) {
            if (aoeRepositionPos == null
                    && attackGateOpen && hooks.targetRangePolicy().isInRange(attackPlan, agent, target)
                    && hooks.attackPlanUsabilityPolicy().canUse(
                    AgentBotMovementStateRuntime.grounded(entry), weaponType, attackPlan.route)) {
                attackAttemptedInRange = true;
                int prevCooldown = AgentBotCombatCooldownStateRuntime.attackCooldownMs(entry);
                hooks.attackExecutor().attack(entry, agent, attackPlan);
                boolean attacked = AgentBotCombatCooldownStateRuntime.attackCooldownMs(entry) != prevCooldown;
                if (attacked && attackPlan.isCloseRangeRoute()
                        && hooks.rangedAmmoWeaponPolicy().isRangedAmmoWeapon(weaponType)) {
                    AgentBotDegenerateAttackStateRuntime.markDegenAttackDone(entry);
                }
                if (attacked && !AgentBotMovementStateRuntime.inAir(entry) && crossRegionRetreatPos == null) {
                    return new Result(true, currentMovementTarget, crossRegionRetreatPos, aoeRepositionPos,
                            shouldRetreatForRangedSpacing, attackAttemptedInRange, weaponType);
                }
            } else if (!AgentBotMovementStateRuntime.inAir(entry)
                    && attackPlan != null
                    && hooks.targetJumpablePolicy().isJumpable(
                    AgentBotMovementStateRuntime.movementProfile(entry),
                    attackPlan.isCloseRangeRoute(),
                    agentPosition,
                    targetPosition,
                    hooks.jumpHeightCalculator().calculate(AgentBotMovementStateRuntime.movementProfile(entry)))
                    && weaponType != WeaponType.BOW && weaponType != WeaponType.CROSSBOW
                    && weaponType != WeaponType.WAND && weaponType != WeaponType.STAFF) {
                hooks.jumpInitiator().initiate(entry, agent, targetPosition.x - agentPosition.x);
                return new Result(true, currentMovementTarget, crossRegionRetreatPos, aoeRepositionPos,
                        shouldRetreatForRangedSpacing, attackAttemptedInRange, weaponType);
            }
        }

        if (target != null && !AgentBotMovementStateRuntime.inAir(entry) && !AgentBotMovementStateRuntime.climbing(entry)
                && !shouldRetreatForRangedSpacing && crossRegionRetreatPos == null
                && aoeRepositionPos == null
                && !attackAttemptedInRange
                && hooks.targetRangePolicy().isInRange(attackPlan, agent, target)) {
            hooks.idleOnGround().idle(entry, agent);
            hooks.movementBroadcaster().broadcast(entry);
            return new Result(true, currentMovementTarget, crossRegionRetreatPos, aoeRepositionPos,
                    shouldRetreatForRangedSpacing, attackAttemptedInRange, weaponType);
        }

        return new Result(false, currentMovementTarget, crossRegionRetreatPos, aoeRepositionPos,
                shouldRetreatForRangedSpacing, attackAttemptedInRange, weaponType);
    }
}
