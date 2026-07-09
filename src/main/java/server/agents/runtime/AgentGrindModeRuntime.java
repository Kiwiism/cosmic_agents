package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementPhaseDispatchService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatRangePolicy;
import server.agents.capabilities.combat.AgentGrindModeTickService;
import server.agents.capabilities.combat.AgentGrindNavigationTailService;
import server.agents.capabilities.combat.AgentGrindRangedEngagementService;
import server.agents.capabilities.combat.AgentGrindTargetCommitmentService;
import server.agents.capabilities.combat.AgentGrindTargetSearchService;
import server.agents.capabilities.combat.AgentCombatAttackRuntime;
import server.agents.capabilities.combat.AgentCombatTargetRuntime;

import java.awt.Point;

public final class AgentGrindModeRuntime {
    private AgentGrindModeRuntime() {
    }

    public static AgentLiveModeTickRuntime.LocalAttackResult tickGrindMode(AgentRuntimeEntry entry,
                                                                           Character agent,
                                                                           Point agentPosition,
                                                                           Point targetPosition,
                                                                           boolean runAiTick,
                                                                           AgentLiveModeTickRuntime.MovementCoreStep movementCoreStep) {
        return tickGrindMode(
                entry,
                agent,
                agentPosition,
                targetPosition,
                runAiTick,
                movementCoreStep,
                AgentRuntimeConfig.cfg.LOOT_RADIUS);
    }

    public static AgentLiveModeTickRuntime.LocalAttackResult tickGrindMode(AgentRuntimeEntry entry,
                                                                           Character agent,
                                                                           Point agentPosition,
                                                                           Point targetPosition,
                                                                           boolean runAiTick,
                                                                           AgentLiveModeTickRuntime.MovementCoreStep movementCoreStep,
                                                                           int lootRadius) {
        AgentGrindModeTickService.Result result = AgentGrindModeTickService.tickGrindMode(
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
        return new AgentLiveModeTickRuntime.LocalAttackResult(result.consumedTick(), result.targetPos());
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
            AgentLiveModeTickRuntime.MovementCoreStep movementCoreStep) {
        return new AgentGrindNoTargetFallbackService.Hooks(
                AgentMovementPhaseDispatchService::tickSwimming,
                AgentMovementPhaseDispatchService::tickAirborne,
                (entry, agentPosition, map) -> AgentGrindTargetRuntime.resolvePatrolWanderTarget(
                        entry, agentPosition, map),
                (entry, agentPosition, map) -> AgentGrindTargetRuntime.resolveNoGrindTargetPosition(
                        entry, agentPosition, map),
                (entry, targetPos, runAiTick) -> movementCoreStep.step(entry, targetPos, runAiTick));
    }

    private static AgentGrindTargetCommitmentService.Hooks grindTargetCommitmentHooks() {
        return new AgentGrindTargetCommitmentService.Hooks(
                (entry, agent, agentPosition, preferredTarget) ->
                        AgentGrindCombatRuntime.selectPriorityRangedAttackTarget(
                                entry, agent, agentPosition, preferredTarget),
                AgentAttackExecutionProvider::findCloserThreatMob);
    }

    private static AgentGrindRangedEngagementService.Hooks grindRangedEngagementHooks() {
        return new AgentGrindRangedEngagementService.Hooks(
                AgentAttackExecutionProvider::getEquippedWeaponType,
                AgentAttackExecutionProvider::shouldDegenerateRangedAttack,
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                (entry, agentPosition, targetPosition) ->
                        AgentGrindNavigationRuntime.selectCrossRegionRetreatTarget(
                                entry, agentPosition, targetPosition),
                AgentCombatRangePolicy::isTargetInAttackRange,
                (entry, agent, target, attackPlan, agentPosition) ->
                        AgentGrindCombatRuntime.resolveAoeReposition(
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
                        AgentGrindNavigationRuntime.selectGrindNavigationTarget(
                                entry, agentPosition, combatTargetPosition, retreatChecked),
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                (entry, agentPosition, mobPosition) ->
                        AgentGrindTargetRuntime.convenientLootTarget(entry, agentPosition, mobPosition));
    }
}
