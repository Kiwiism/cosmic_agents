package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementTickService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.navigation.AgentNavigationTargetService;

import java.awt.Point;

public final class AgentMovementTickRuntime {
    private AgentMovementTickRuntime() {
    }

    public static void stepMovementCore(AgentRuntimeEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick) {
        stepMovementCore(
                entry,
                targetPosition,
                runAiTick,
                AgentRuntimeConfig.cfg.ENABLE_UNSTUCK,
                AgentMovementPhysicsConfig.configuredStopDist());
    }

    public static void stepMovementCore(AgentRuntimeEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick,
                                        boolean enableUnstuck,
                                        int stopDistance) {
        AgentMovementTickService.stepMovementCore(
                entry,
                targetPosition,
                runAiTick,
                hooks(entry, enableUnstuck, stopDistance));
    }

    private static AgentMovementTickService.MovementTickHooks hooks(AgentRuntimeEntry entry, boolean enableUnstuck, int stopDistance) {
        return new AgentMovementTickService.MovementTickHooks(
                (ignored, targetPosition, runAiTick) -> {
                    AgentNavigationTargetService.NavigationDirective directive =
                            AgentNavigationTargetService.resolveTarget(entry, targetPosition, runAiTick);
                    return new AgentMovementTickService.NavigationResult(directive.consumedTick(), directive.targetPos());
                },
                (ignored, targetPosition, runAiTick) -> AgentFidgetService.tryHandleTick(entry, targetPosition, runAiTick),
                (ignored, targetPosition, runAiTick) -> AgentMovementPhaseRuntime.tickMovementPhase(entry, targetPosition, runAiTick),
                (ignored, targetPosition) -> AgentNavigationTargetService.tryExecuteCommittedEdgeAfterGroundMovement(entry, targetPosition),
                ignored -> AgentStuckDetectionRuntime.tickStuckDetection(entry, enableUnstuck),
                ignored -> AgentTickStateMaintenanceService.clearReachedMoveTarget(entry, stopDistance));
    }

}
