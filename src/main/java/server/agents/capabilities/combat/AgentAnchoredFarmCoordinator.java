package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.movement.AgentIdlePhysicsService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementTickCoordinator;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Coordinates combat and movement capability operations for anchored farming.
 */
public final class AgentAnchoredFarmCoordinator {
    private AgentAnchoredFarmCoordinator() {
    }

    public static void tickAnchoredFarm(AgentRuntimeEntry entry,
                                        Character agent,
                                        Point agentPosition,
                                        boolean runAiTick) {
        tickAnchoredFarm(
                entry,
                agent,
                agentPosition,
                runAiTick,
                AgentRuntimeConfig.cfg.ENABLE_UNSTUCK,
                AgentMovementPhysicsConfig.configuredStopDist());
    }

    public static void tickAnchoredFarm(AgentRuntimeEntry entry,
                                        Character agent,
                                        Point agentPosition,
                                        boolean runAiTick,
                                        boolean enableUnstuck,
                                        int stopDistance) {
        AgentAnchoredFarmTickService.tickAnchoredFarm(
                entry,
                agent,
                agentPosition,
                runAiTick,
                hooks(enableUnstuck, stopDistance));
    }

    private static AgentAnchoredFarmTickService.AnchoredFarmHooks hooks(boolean enableUnstuck,
                                                                        int stopDistance) {
        return new AgentAnchoredFarmTickService.AnchoredFarmHooks(
                (entry, agent, agentPosition, movementTargetPosition, moveWindowReferencePosition,
                 allowCombatMovement, allowJumpTowardTarget) -> {
                    AgentLocalOpportunityAttackService.Result result =
                            AgentLocalOpportunityAttackCoordinator.tryLocalOpportunityAttack(
                                    entry,
                                    agent,
                                    agentPosition,
                                    movementTargetPosition,
                                    moveWindowReferencePosition,
                                    allowCombatMovement,
                                    allowJumpTowardTarget);
                    return new AgentAnchoredFarmTickService.LocalOpportunityResult(
                            result.consumedTick(), result.targetPos());
                },
                AgentIdlePhysicsService::tickIdleEntry,
                (entry, agent) -> {
                    AgentMovementPoseService.idleOnGround(entry, agent);
                    AgentMovementBroadcastService.broadcastMovement(entry);
                },
                (entry, targetPosition, runAiTick) -> AgentMovementTickCoordinator.stepMovementCore(
                        entry,
                        targetPosition,
                        runAiTick,
                        enableUnstuck,
                        stopDistance));
    }
}
