package server.agents.capabilities.movement;

import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.navigation.AgentNavigationTargetService;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Assembles the movement and navigation capability steps for one movement tick.
 */
public final class AgentMovementTickCoordinator {
    private AgentMovementTickCoordinator() {
    }

    public static void stepMovementCore(AgentRuntimeEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick) {
        stepMovementCore(entry, targetPosition, runAiTick,
                AgentMovementPhysicsConfig.configuredStopDist());
    }

    public static void stepMovementCore(AgentRuntimeEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick,
                                        int stopDistance) {
        AgentMovementTickService.stepMovementCore(
                entry,
                targetPosition,
                runAiTick,
                AgentMovementSubstepPolicy.substeps(entry),
                hooks(entry, stopDistance));
    }

    private static AgentMovementTickService.MovementTickHooks hooks(AgentRuntimeEntry entry,
                                                                     int stopDistance) {
        return new AgentMovementTickService.MovementTickHooks(
                (ignored, targetPosition, runAiTick) -> {
                    AgentNavigationTargetService.NavigationDirective directive =
                            AgentNavigationTargetService.resolveTarget(entry, targetPosition, runAiTick);
                    return new AgentMovementTickService.NavigationResult(directive.consumedTick(), directive.targetPos());
                },
                (ignored, targetPosition, runAiTick) -> AgentFidgetService.tryHandleTick(entry, targetPosition, runAiTick),
                (ignored, targetPosition) -> AgentFidgetService.tickMovementSubstep(entry, targetPosition),
                (ignored, targetPosition, runAiTick) -> AgentMovementPhaseService.tickMovementPhase(entry, targetPosition, runAiTick),
                (ignored, targetPosition) -> AgentNavigationTargetService.tryExecuteCommittedEdgeAfterGroundMovement(entry, targetPosition),
                ignored -> AgentStuckDetectionService.tickStuckDetection(entry),
                ignored -> AgentMovementTargetMaintenanceService.clearReachedMoveTarget(entry, stopDistance));
    }
}
