package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.operations.events.AgentCombatPostureChangedEvent;
import server.agents.operations.events.AgentCombatPostureRuntime;
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
                        CrowdJumpTargetSelector crowdJumpTargetSelector,
                        TargetJumpablePolicy targetJumpablePolicy,
                        JumpHeightCalculator jumpHeightCalculator,
                        JumpInitiator jumpInitiator,
                        JumpInitiator fixedArcJumpInitiator,
                        FiringPositionSelector firingPositionSelector,
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
        boolean shouldRetreat(WeaponType weaponType,
                              AgentAttackRoute route,
                              Point agentPosition,
                              Point targetPosition);
    }

    @FunctionalInterface
    public interface CrossRegionRetreatSelector {
        Point select(AgentRuntimeEntry entry, Point agentPosition, Point targetPosition);
    }

    @FunctionalInterface
    public interface TargetRangePolicy {
        boolean isInRange(AgentAttackPlan attackPlan, Character agent, Monster target);
    }

    @FunctionalInterface
    public interface AoeRepositionResolver {
        Point resolve(AgentRuntimeEntry entry, Character agent, Monster target, AgentAttackPlan attackPlan, Point agentPosition);
    }

    @FunctionalInterface
    public interface AttackPlanUsabilityPolicy {
        boolean canUse(boolean grounded, WeaponType weaponType, AgentAttackRoute route);
    }

    @FunctionalInterface
    public interface AttackExecutor {
        AgentAttackTransactionResult attack(AgentRuntimeEntry entry,
                                            Character agent,
                                            AgentAttackPlan attackPlan);
    }

    @FunctionalInterface
    public interface RangedAmmoWeaponPolicy {
        boolean isRangedAmmoWeapon(WeaponType weaponType);
    }

    @FunctionalInterface
    public interface CrowdJumpTargetSelector {
        Point select(AgentRuntimeEntry entry,
                     Character agent,
                     WeaponType weaponType,
                     Point agentPosition,
                     Point targetPosition);
    }

    @FunctionalInterface
    public interface TargetJumpablePolicy {
        boolean isJumpable(AgentMovementProfile movementProfile,
                           WeaponType weaponType,
                           AgentAttackRoute route,
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
        void initiate(AgentRuntimeEntry entry, Character agent, int dx);
    }

    @FunctionalInterface
    public interface FiringPositionSelector {
        Point select(AgentRuntimeEntry entry, Point agentPosition, Point targetPosition);
    }

    @FunctionalInterface
    public interface IdleOnGround {
        void idle(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface MovementBroadcaster {
        void broadcast(AgentRuntimeEntry entry);
    }

    public static Result engage(AgentRuntimeEntry entry,
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
        boolean degenAttackDone = AgentDegenerateAttackStateRuntime.degenAttackDone(entry);
        AgentAttackRoute attackRoute = attackPlan != null ? attackPlan.route : AgentAttackRoute.CLOSE;
        boolean canFireWithoutDegen = weaponType == null
                || !hooks.degenerateAttackPolicy().shouldDegenerate(weaponType, agentPosition, targetPosition);
        AgentRangedEngagementPolicy.Decision spacingDecision = AgentRangedEngagementPolicy.decide(
                new AgentRangedEngagementPolicy.Input(
                        targetInDegenerateBand,
                        degenAttackDone,
                        rangedPriorityTarget != null,
                        hooks.retreatPolicy().shouldRetreat(
                                weaponType, attackRoute, agentPosition, targetPosition),
                        canFireWithoutDegen));
        boolean shouldRetreatForRangedSpacing = spacingDecision.retreat();
        boolean attackGateOpen = spacingDecision.attackGateOpen();
        Point crossRegionRetreatPos = shouldRetreatForRangedSpacing
                ? hooks.crossRegionRetreatSelector().select(entry, agentPosition, targetPosition)
                : null;
        Point aoeRepositionPos = (!shouldRetreatForRangedSpacing && crossRegionRetreatPos == null
                && attackGateOpen && hooks.targetRangePolicy().isInRange(attackPlan, agent, target))
                ? hooks.aoeRepositionResolver().resolve(entry, agent, target, attackPlan, agentPosition)
                : null;

        if (!AgentMovementStateRuntime.climbing(entry)
                && !AgentMovementStateRuntime.inAir(entry)
                && attackGateOpen
                && hooks.targetRangePolicy().isInRange(attackPlan, agent, target)) {
            Point crowdJumpTarget = hooks.crowdJumpTargetSelector().select(
                    entry, agent, weaponType, agentPosition, targetPosition);
            if (crowdJumpTarget != null) {
                AgentCombatPostureRuntime.observe(entry, agent,
                        AgentCombatPostureChangedEvent.Posture.JUMP_ATTACK,
                        target.getId(), targetPosition, "crowd jump attack", System.currentTimeMillis());
                hooks.fixedArcJumpInitiator().initiate(
                        entry, agent, crowdJumpTarget.x - agentPosition.x);
                return new Result(true, crowdJumpTarget, null, null,
                        shouldRetreatForRangedSpacing, false, weaponType);
            }
        }

        boolean attackAttemptedInRange = false;
        if (!AgentMovementStateRuntime.climbing(entry)) {
            boolean airborneDegenerateRangedWeapon = AgentMovementStateRuntime.inAir(entry)
                    && attackPlan != null
                    && attackPlan.isCloseRangeRoute()
                    && hooks.rangedAmmoWeaponPolicy().isRangedAmmoWeapon(weaponType);
            if (!airborneDegenerateRangedWeapon
                    && aoeRepositionPos == null
                    && attackGateOpen && hooks.targetRangePolicy().isInRange(attackPlan, agent, target)
                    && hooks.attackPlanUsabilityPolicy().canUse(
                    AgentMovementStateRuntime.grounded(entry), weaponType, attackPlan.route)) {
                attackAttemptedInRange = true;
                AgentAttackTransactionResult attackResult = hooks.attackExecutor().attack(entry, agent, attackPlan);
                boolean attacked = attackResult != null && attackResult.committed();
                if (attacked && attackPlan.isCloseRangeRoute()
                        && hooks.rangedAmmoWeaponPolicy().isRangedAmmoWeapon(weaponType)) {
                    AgentDegenerateAttackStateRuntime.markDegenAttackDone(entry);
                }
                if (attacked && !AgentMovementStateRuntime.inAir(entry) && crossRegionRetreatPos == null) {
                    if (attackPlan.route == AgentAttackRoute.RANGED) {
                        Point firingPosition = hooks.firingPositionSelector().select(
                                entry, agentPosition, targetPosition);
                        if (firingPosition != null) {
                            AgentCombatPostureRuntime.observe(entry, agent,
                                    AgentCombatPostureChangedEvent.Posture.SAFE_SHOT,
                                    target.getId(), targetPosition,
                                    "holding a reachable firing anchor", System.currentTimeMillis());
                            return new Result(false, firingPosition, firingPosition, aoeRepositionPos,
                                    shouldRetreatForRangedSpacing, false, weaponType);
                        }
                    }
                    AgentCombatPostureRuntime.observe(entry, agent,
                            attackPlan.route == AgentAttackRoute.RANGED
                                    || attackPlan.route == AgentAttackRoute.MAGIC
                                    ? AgentCombatPostureChangedEvent.Posture.RANGED
                                    : AgentCombatPostureChangedEvent.Posture.MELEE,
                            target.getId(), targetPosition,
                            AgentMovementStateRuntime.inAir(entry) ? "airborne committed attack"
                                    : "committed in-range attack", System.currentTimeMillis());
                    return new Result(true, currentMovementTarget, crossRegionRetreatPos, aoeRepositionPos,
                            shouldRetreatForRangedSpacing, attackAttemptedInRange, weaponType);
                }
            } else if (!AgentMovementStateRuntime.inAir(entry)
                    && attackPlan != null
                    && hooks.targetJumpablePolicy().isJumpable(
                    AgentMovementStateRuntime.movementProfile(entry),
                    weaponType,
                    attackPlan.route,
                    agentPosition,
                    targetPosition,
                    hooks.jumpHeightCalculator().calculate(AgentMovementStateRuntime.movementProfile(entry)))) {
                hooks.jumpInitiator().initiate(entry, agent, targetPosition.x - agentPosition.x);
                AgentCombatPostureRuntime.observe(entry, agent,
                        AgentCombatPostureChangedEvent.Posture.JUMP_ATTACK,
                        target.getId(), targetPosition, "jumping into legal attack range",
                        System.currentTimeMillis());
                return new Result(true, currentMovementTarget, crossRegionRetreatPos, aoeRepositionPos,
                        shouldRetreatForRangedSpacing, attackAttemptedInRange, weaponType);
            }
        }

        if (target != null && !AgentMovementStateRuntime.inAir(entry) && !AgentMovementStateRuntime.climbing(entry)
                && !shouldRetreatForRangedSpacing && crossRegionRetreatPos == null
                && aoeRepositionPos == null
                && !attackAttemptedInRange
                && hooks.targetRangePolicy().isInRange(attackPlan, agent, target)) {
            hooks.idleOnGround().idle(entry, agent);
            hooks.movementBroadcaster().broadcast(entry);
            return new Result(true, currentMovementTarget, crossRegionRetreatPos, aoeRepositionPos,
                    shouldRetreatForRangedSpacing, attackAttemptedInRange, weaponType);
        }

        if (aoeRepositionPos != null) {
            AgentCombatPostureRuntime.observe(entry, agent,
                    AgentCombatPostureChangedEvent.Posture.AOE_REPOSITION,
                    target.getId(), targetPosition, "seeking better multi-target coverage",
                    System.currentTimeMillis());
        } else if (shouldRetreatForRangedSpacing || crossRegionRetreatPos != null) {
            AgentCombatPostureRuntime.observe(entry, agent,
                    AgentCombatPostureChangedEvent.Posture.KITING,
                    target.getId(), targetPosition, "restoring ranged spacing",
                    System.currentTimeMillis());
        }

        return new Result(false, currentMovementTarget, crossRegionRetreatPos, aoeRepositionPos,
                shouldRetreatForRangedSpacing, attackAttemptedInRange, weaponType);
    }
}
