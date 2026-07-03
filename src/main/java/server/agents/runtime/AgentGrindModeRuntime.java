package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementPhaseDispatchService;

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
import server.agents.integration.AgentBotCombatAttackRuntime;
import server.agents.integration.AgentBotCombatTargetRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

import java.awt.Point;

public final class AgentGrindModeRuntime {
    private AgentGrindModeRuntime() {
    }

    public static AgentLiveModeTickRuntime.LocalAttackResult tickGrindMode(BotEntry entry,
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

    public static AgentLiveModeTickRuntime.LocalAttackResult tickGrindMode(BotEntry entry,
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
                (entry, agent) -> AgentBotCombatTargetRuntime.findPatrolTarget(entry, agent, AgentCombatConfig.cfg),
                (entry, agent) -> AgentBotCombatTargetRuntime.findGrindTarget(entry, agent, AgentCombatConfig.cfg),
                AgentCombatConfig.cfg.GRIND_RETARGET_INTERVAL_MS);
    }

    private static AgentGrindNoTargetFallbackService.Hooks grindNoTargetFallbackHooks(
            AgentLiveModeTickRuntime.MovementCoreStep movementCoreStep) {
        return new AgentGrindNoTargetFallbackService.Hooks(
                AgentMovementPhaseDispatchService::tickSwimming,
                AgentMovementPhaseDispatchService::tickAirborne,
                AgentGrindTargetRuntime::resolvePatrolWanderTarget,
                AgentGrindTargetRuntime::resolveNoGrindTargetPosition,
                movementCoreStep::step);
    }

    private static AgentGrindTargetCommitmentService.Hooks grindTargetCommitmentHooks() {
        return new AgentGrindTargetCommitmentService.Hooks(
                AgentGrindCombatRuntime::selectPriorityRangedAttackTarget,
                AgentAttackExecutionProvider::findCloserThreatMob);
    }

    private static AgentGrindRangedEngagementService.Hooks grindRangedEngagementHooks() {
        return new AgentGrindRangedEngagementService.Hooks(
                AgentAttackExecutionProvider::getEquippedWeaponType,
                AgentAttackExecutionProvider::shouldDegenerateRangedAttack,
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                AgentGrindNavigationRuntime::selectCrossRegionRetreatTarget,
                AgentCombatRangePolicy::isTargetInAttackRange,
                AgentGrindCombatRuntime::resolveAoeReposition,
                AgentCombatRangePolicy::canUseAttackPlanNow,
                AgentBotCombatAttackRuntime::attackMonster,
                AgentCombatAmmoCounter::isRangedAmmoWeapon,
                AgentCombatRangePolicy::isTargetJumpable,
                BotPhysicsEngine::calculateMaxJumpHeight,
                AgentJumpActionService::initiateJump,
                BotPhysicsEngine::idleOnGround,
                AgentMovementBroadcastService::broadcastMovement);
    }

    private static AgentGrindNavigationTailService.Hooks grindNavigationTailHooks() {
        return new AgentGrindNavigationTailService.Hooks(
                AgentGrindNavigationRuntime::selectGrindNavigationTarget,
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                AgentGrindTargetRuntime::convenientLootTarget);
    }
}
