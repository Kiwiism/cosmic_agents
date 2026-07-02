package server.agents.runtime;

import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.bots.BotEntry;
import server.bots.BotNavigationManager;

import java.awt.Point;

public final class AgentMovementTickRuntime {
    private AgentMovementTickRuntime() {
    }

    public static void stepMovementCore(BotEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick,
                                        boolean enableUnstuck,
                                        int stopDistance) {
        AgentMovementTickService.stepMovementCore(
                entry,
                targetPosition,
                runAiTick,
                hooks(enableUnstuck, stopDistance));
    }

    private static AgentMovementTickService.MovementTickHooks hooks(boolean enableUnstuck, int stopDistance) {
        return new AgentMovementTickService.MovementTickHooks(
                (entry, targetPosition, runAiTick) -> {
                    BotNavigationManager.NavigationDirective directive =
                            BotNavigationManager.resolveTarget(entry, targetPosition, runAiTick);
                    return new AgentMovementTickService.NavigationResult(directive.consumedTick, directive.targetPos);
                },
                AgentFidgetService::tryHandleTick,
                AgentMovementPhaseRuntime::tickMovementPhase,
                BotNavigationManager::tryExecuteCommittedEdgeAfterGroundMovement,
                entry -> AgentStuckDetectionRuntime.tickStuckDetection(entry, enableUnstuck),
                entry -> AgentTickStateMaintenanceService.clearReachedMoveTarget(entry, stopDistance));
    }
}
