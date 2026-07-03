package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import client.Character;
import server.agents.capabilities.combat.AgentLocalOpportunityAttackService;
import server.bots.BotEntry;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;

import java.awt.Point;

public final class AgentAnchoredFarmRuntime {
    private AgentAnchoredFarmRuntime() {
    }

    public static void tickAnchoredFarm(BotEntry entry,
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

    public static void tickAnchoredFarm(BotEntry entry,
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

    private static AgentAnchoredFarmTickService.AnchoredFarmHooks hooks(boolean enableUnstuck, int stopDistance) {
        return new AgentAnchoredFarmTickService.AnchoredFarmHooks(
                (entry, agent, agentPosition, movementTargetPosition, moveWindowReferencePosition,
                 allowCombatMovement, allowJumpTowardTarget) -> {
                    AgentLocalOpportunityAttackService.Result result =
                            AgentLocalOpportunityAttackRuntime.tryLocalOpportunityAttack(
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
                AgentIdlePhysicsRuntime::tickIdleEntry,
                (entry, agent) -> {
                    BotPhysicsEngine.idleOnGround(entry, agent);
                    BotMovementManager.broadcastMovement(entry);
                },
                (entry, targetPosition, runAiTick) -> AgentMovementTickRuntime.stepMovementCore(
                        entry,
                        targetPosition,
                        runAiTick,
                        enableUnstuck,
                        stopDistance));
    }
}
