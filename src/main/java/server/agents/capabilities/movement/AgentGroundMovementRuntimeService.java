package server.agents.capabilities.movement;

import client.Character;
import java.awt.Point;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.monitoring.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;

public final class AgentGroundMovementRuntimeService {
    private AgentGroundMovementRuntimeService() {
    }

    public static void tickGrounded(AgentRuntimeEntry entry, Point targetPos) {
        long startedAt = System.nanoTime();
        try {
            AgentSwimStateRuntime.setSwimming(entry, false);
            Character bot = AgentRuntimeIdentityRuntime.bot(entry);

            AgentMotionTimerService.tickMotionTimers(entry);

            Foothold currentFoothold = AgentGroundPhysicsService.syncAndDetectGround(entry, bot);
            if (currentFoothold == null) {
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }

            Point botPos = bot.getPosition();
            if (AgentClimbStateRuntime.ropeEntryPending(entry)) {
                performTopRopeEntry(entry);
                return;
            }
            if (AgentMovementStateRuntime.hasDownJumpPending(entry)) {
                performDownJump(entry);
                return;
            }

            targetPos = AgentGroundTargetService.adjustGrindingTargetPosition(entry, currentFoothold, targetPos);
            boolean walkOffWaypoint = false;
            if (AgentMovementNavigationStateRuntime.graphWarmupFallback(entry) && targetPos != null) {
                if (AgentFallbackMovementService.tryImmediateAction(entry, botPos, targetPos)) {
                    return;
                }
                AgentFallbackMovementService.Steering steering =
                        AgentFallbackMovementService.resolveSteeringTarget(entry, botPos, targetPos);
                targetPos = steering.target();
                walkOffWaypoint = steering.walkOffLedge();
            }
            AgentGroundAction action = AgentGroundActionPlanner.planGroundAction(
                    entry, currentFoothold, botPos, targetPos, walkOffWaypoint);
            AgentGroundActionExecutor.applyGroundAction(entry, currentFoothold, action);
        } finally {
            AgentPerformanceMonitor.record("move-ground", System.nanoTime() - startedAt);
        }
    }

    private static void performDownJump(AgentRuntimeEntry entry) {
        AgentQueuedMovementActionService.beginDownJump(entry, AgentRuntimeIdentityRuntime.bot(entry));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    private static void performTopRopeEntry(AgentRuntimeEntry entry) {
        AgentQueuedMovementActionService.beginTopRopeEntry(entry, AgentRuntimeIdentityRuntime.bot(entry));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }
}
