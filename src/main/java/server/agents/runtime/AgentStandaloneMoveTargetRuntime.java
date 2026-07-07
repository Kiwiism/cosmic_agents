package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementProfileService;

import client.Character;

public final class AgentStandaloneMoveTargetRuntime {
    private AgentStandaloneMoveTargetRuntime() {
    }

    public static void tickStandaloneMoveTarget(AgentRuntimeEntry entry,
                                                Character agent,
                                                boolean runAiTick) {
        tickStandaloneMoveTarget(
                entry,
                agent,
                runAiTick,
                AgentRuntimeConfig.cfg.ENABLE_UNSTUCK,
                AgentMovementPhysicsConfig.configuredStopDist());
    }

    public static void tickStandaloneMoveTarget(AgentRuntimeEntry entry,
                                                Character agent,
                                                boolean runAiTick,
                                                boolean enableUnstuck,
                                                int stopDistance) {
        AgentStandaloneMoveTargetTickService.tickStandaloneMoveTarget(
                entry,
                agent,
                runAiTick,
                new AgentStandaloneMoveTargetTickService.Hooks(
                        AgentMapTransitionRuntime::groundAfterMapChange,
                        AgentMovementProfileService::refreshMovementProfile,
                        (moveEntry, targetPosition, moveRunAiTick) -> AgentMovementTickRuntime.stepMovementCore(
                                moveEntry,
                                targetPosition,
                                moveRunAiTick,
                                enableUnstuck,
                                stopDistance)));
    }
}
