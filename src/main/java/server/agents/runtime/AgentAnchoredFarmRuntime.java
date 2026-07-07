package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.agents.capabilities.combat.AgentLocalOpportunityAttackService;

import java.awt.Point;

public final class AgentAnchoredFarmRuntime {
    private AgentAnchoredFarmRuntime() {
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

    private static AgentAnchoredFarmTickService.AnchoredFarmHooks hooks(boolean enableUnstuck, int stopDistance) {
        return new AgentAnchoredFarmTickService.AnchoredFarmHooks(
                (entry, agent, agentPosition, movementTargetPosition, moveWindowReferencePosition,
                 allowCombatMovement, allowJumpTowardTarget) -> {
                    AgentLocalOpportunityAttackService.Result result =
                            AgentLocalOpportunityAttackRuntime.tryLocalOpportunityAttack(
                                    asBotEntry(entry),
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
                    AgentMovementPoseService.idleOnGround(asBotEntry(entry), agent);
                    AgentMovementBroadcastService.broadcastMovement(asBotEntry(entry));
                },
                (entry, targetPosition, runAiTick) -> AgentMovementTickRuntime.stepMovementCore(
                        asBotEntry(entry),
                        targetPosition,
                        runAiTick,
                        enableUnstuck,
                        stopDistance));
    }

    private static server.bots.BotEntry asBotEntry(AgentRuntimeEntry entry) {
        if (entry instanceof server.bots.BotEntry botEntry) {
            return botEntry;
        }
        throw new IllegalArgumentException("Legacy anchored farm runtime requires BotEntry compatibility shell");
    }
}
