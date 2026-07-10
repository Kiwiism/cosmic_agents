package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementPhaseDispatchService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Coordinates combat-owned grind target, engagement, fallback, and navigation
 * services for one grind-mode tick.
 */
public final class AgentGrindModeCoordinator {
    @FunctionalInterface
    public interface MovementCoreStep {
        void step(AgentRuntimeEntry entry, Point targetPosition, boolean runAiTick);
    }

    private AgentGrindModeCoordinator() {
    }

    public static AgentGrindModeTickService.Result tickGrindMode(AgentRuntimeEntry entry,
                                                                 Character agent,
                                                                 Point agentPosition,
                                                                 Point targetPosition,
                                                                 boolean runAiTick,
                                                                 MovementCoreStep movementCoreStep) {
        return tickGrindMode(
                entry,
                agent,
                agentPosition,
                targetPosition,
                runAiTick,
                movementCoreStep,
                AgentRuntimeConfig.cfg.LOOT_RADIUS);
    }

    public static AgentGrindModeTickService.Result tickGrindMode(AgentRuntimeEntry entry,
                                                                 Character agent,
                                                                 Point agentPosition,
                                                                 Point targetPosition,
                                                                 boolean runAiTick,
                                                                 MovementCoreStep movementCoreStep,
                                                                 int lootRadius) {
        return AgentGrindModeTickService.tickGrindMode(
                entry,
                agent,
                agentPosition,
                targetPosition,
                runAiTick,
                new AgentGrindModeTickService.Hooks(
                        grindTargetSearchHooks(),
                        grindNoTargetFallbackHooks(movementCoreStep),
                        grindTargetCommitmentHooks(),
                        grindRangedEngagementHooks(),
                        grindNavigationTailHooks(),
                        AgentCombatConfig.cfg.GRIND_SEEK_RANGE,
                        lootRadius));
    }

    private static AgentGrindTargetSearchService.SearchHooks grindTargetSearchHooks() {
        return new AgentGrindTargetSearchService.SearchHooks(
                (entry, agent) -> AgentCombatTargetRuntime.findPatrolTarget(
                        entry, agent, AgentCombatConfig.cfg),
                (entry, agent) -> AgentCombatTargetRuntime.findGrindTarget(
                        entry, agent, AgentCombatConfig.cfg),
                AgentCombatConfig.cfg.GRIND_RETARGET_INTERVAL_MS);
    }

    private static AgentGrindNoTargetFallbackService.Hooks grindNoTargetFallbackHooks(
            MovementCoreStep movementCoreStep) {
        return new AgentGrindNoTargetFallbackService.Hooks(
                AgentMovementPhaseDispatchService::tickSwimming,
                AgentMovementPhaseDispatchService::tickAirborne,
                (entry, agentPosition, map) -> AgentGrindTargetPositionService.resolvePatrolWanderTarget(
                        entry, agentPosition, map),
                (entry, agentPosition, map) -> AgentGrindTargetPositionService.resolveNoGrindTargetPosition(
                        entry, agentPosition, map),
                movementCoreStep::step);
    }

    private static AgentGrindTargetCommitmentService.Hooks grindTargetCommitmentHooks() {
        return new AgentGrindTargetCommitmentService.Hooks(
                (entry, agent, agentPosition, preferredTarget) ->
                        AgentRangedPriorityTargetSelector.selectPriorityRangedAttackTarget(
                                entry, agent, agentPosition, preferredTarget),
                AgentAttackExecutionProvider::findCloserThreatMob);
    }

    private static AgentGrindRangedEngagementService.Hooks grindRangedEngagementHooks() {
        return new AgentGrindRangedEngagementService.Hooks(
                AgentAttackExecutionProvider::getEquippedWeaponType,
                AgentAttackExecutionProvider::shouldDegenerateRangedAttack,
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                (entry, agentPosition, targetPosition) ->
                        AgentGrindNavigationTargetSelector.selectCrossRegionRetreatTarget(
                                entry, agentPosition, targetPosition),
                AgentCombatRangePolicy::isTargetInAttackRange,
                (entry, agent, target, attackPlan, agentPosition) ->
                        AgentAoeRepositionService.resolveAoeReposition(
                                entry, agent, target, attackPlan, agentPosition),
                AgentCombatRangePolicy::canUseAttackPlanNow,
                (entry, agent, attackPlan) ->
                        AgentCombatAttackRuntime.attackMonster(entry, agent, attackPlan),
                AgentCombatAmmoCounter::isRangedAmmoWeapon,
                AgentCombatRangePolicy::isTargetJumpable,
                AgentMovementKinematicsService::calculateMaxJumpHeight,
                AgentJumpActionService::initiateJump,
                AgentMovementPoseService::idleOnGround,
                AgentMovementBroadcastService::broadcastMovement);
    }

    private static AgentGrindNavigationTailService.Hooks grindNavigationTailHooks() {
        return new AgentGrindNavigationTailService.Hooks(
                (entry, agentPosition, combatTargetPosition, retreatChecked) ->
                        AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(
                                entry, agentPosition, combatTargetPosition, retreatChecked),
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                (entry, agentPosition, mobPosition) ->
                        AgentGrindTargetPositionService.convenientLootTarget(entry, agentPosition, mobPosition));
    }
}
