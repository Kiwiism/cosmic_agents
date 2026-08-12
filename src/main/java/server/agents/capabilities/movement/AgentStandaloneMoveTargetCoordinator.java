package server.agents.capabilities.movement;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Coordinates movement toward an explicit target outside follow or grind mode.
 */
public final class AgentStandaloneMoveTargetCoordinator {
    private AgentStandaloneMoveTargetCoordinator() {
    }

    public static void tickStandaloneMoveTarget(AgentRuntimeEntry entry,
                                                Character agent,
                                                boolean runAiTick) {
        tickStandaloneMoveTarget(entry, agent, runAiTick,
                AgentMovementPhysicsConfig.configuredStopDist());
    }

    public static void tickStandaloneMoveTarget(AgentRuntimeEntry entry,
                                                Character agent,
                                                boolean runAiTick,
                                                int stopDistance) {
        AgentStandaloneMoveTargetTickService.tickStandaloneMoveTarget(
                entry,
                agent,
                runAiTick,
                new AgentStandaloneMoveTargetTickService.Hooks(
                        AgentMapGroundingCoordinator::groundAfterMapChange,
                        AgentMovementProfileService::refreshMovementProfile,
                        (moveEntry, targetPosition, moveRunAiTick) -> AgentMovementTickCoordinator.stepMovementCore(
                                moveEntry,
                                targetPosition,
                                moveRunAiTick,
                                stopDistance)));
    }
}
