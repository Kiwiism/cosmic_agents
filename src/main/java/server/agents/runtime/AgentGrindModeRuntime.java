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
import server.agents.integration.AgentBotCombatAttackRuntime;
import server.agents.integration.AgentBotCombatTargetRuntime;
import server.bots.BotEntry;

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
                (entry, agent) -> AgentBotCombatTargetRuntime.findPatrolTarget(
                        entry, agent, AgentCombatConfig.cfg),
                (entry, agent) -> AgentBotCombatTargetRuntime.findGrindTarget(
                        entry, agent, AgentCombatConfig.cfg),
                AgentCombatConfig.cfg.GRIND_RETARGET_INTERVAL_MS);
    }

    private static AgentGrindNoTargetFallbackService.Hooks grindNoTargetFallbackHooks(
            AgentLiveModeTickRuntime.MovementCoreStep movementCoreStep) {
        return new AgentGrindNoTargetFallbackService.Hooks(
                (entry, targetPos) -> AgentMovementPhaseDispatchService.tickSwimming(asBotEntry(entry), targetPos),
                (entry, targetPos) -> AgentMovementPhaseDispatchService.tickAirborne(asBotEntry(entry), targetPos),
                (entry, agentPosition, map) -> AgentGrindTargetRuntime.resolvePatrolWanderTarget(
                        asBotEntry(entry), agentPosition, map),
                (entry, agentPosition, map) -> AgentGrindTargetRuntime.resolveNoGrindTargetPosition(
                        asBotEntry(entry), agentPosition, map),
                (entry, targetPos, runAiTick) -> movementCoreStep.step(asBotEntry(entry), targetPos, runAiTick));
    }

    private static AgentGrindTargetCommitmentService.Hooks grindTargetCommitmentHooks() {
        return new AgentGrindTargetCommitmentService.Hooks(
                (entry, agent, agentPosition, preferredTarget) ->
                        AgentGrindCombatRuntime.selectPriorityRangedAttackTarget(
                                asBotEntry(entry), agent, agentPosition, preferredTarget),
                AgentAttackExecutionProvider::findCloserThreatMob);
    }

    private static AgentGrindRangedEngagementService.Hooks grindRangedEngagementHooks() {
        return new AgentGrindRangedEngagementService.Hooks(
                AgentAttackExecutionProvider::getEquippedWeaponType,
                AgentAttackExecutionProvider::shouldDegenerateRangedAttack,
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                (entry, agentPosition, targetPosition) ->
                        AgentGrindNavigationRuntime.selectCrossRegionRetreatTarget(
                                asBotEntry(entry), agentPosition, targetPosition),
                AgentCombatRangePolicy::isTargetInAttackRange,
                (entry, agent, target, attackPlan, agentPosition) ->
                        AgentGrindCombatRuntime.resolveAoeReposition(
                                asBotEntry(entry), agent, target, attackPlan, agentPosition),
                AgentCombatRangePolicy::canUseAttackPlanNow,
                (entry, agent, attackPlan) ->
                        AgentBotCombatAttackRuntime.attackMonster(asBotEntry(entry), agent, attackPlan),
                AgentCombatAmmoCounter::isRangedAmmoWeapon,
                AgentCombatRangePolicy::isTargetJumpable,
                AgentMovementKinematicsService::calculateMaxJumpHeight,
                (entry, agent, dx) -> AgentJumpActionService.initiateJump(asBotEntry(entry), agent, dx),
                (entry, agent) -> AgentMovementPoseService.idleOnGround(asBotEntry(entry), agent),
                entry -> AgentMovementBroadcastService.broadcastMovement(asBotEntry(entry)));
    }

    private static AgentGrindNavigationTailService.Hooks grindNavigationTailHooks() {
        return new AgentGrindNavigationTailService.Hooks(
                (entry, agentPosition, combatTargetPosition, retreatChecked) ->
                        AgentGrindNavigationRuntime.selectGrindNavigationTarget(
                                asBotEntry(entry), agentPosition, combatTargetPosition, retreatChecked),
                AgentAttackExecutionProvider::shouldRetreatFromNearbyTarget,
                (entry, agentPosition, mobPosition) ->
                        AgentGrindTargetRuntime.convenientLootTarget(asBotEntry(entry), agentPosition, mobPosition));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        if (entry instanceof BotEntry botEntry) {
            return botEntry;
        }
        throw new IllegalArgumentException("Legacy grind runtime requires BotEntry compatibility shell");
    }
}
