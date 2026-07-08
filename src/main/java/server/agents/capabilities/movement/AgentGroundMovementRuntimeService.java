package server.agents.capabilities.movement;

import client.Character;
import java.awt.Point;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentSwimStateRuntime;
import server.agents.runtime.AgentPerformanceMonitor;
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
            if (AgentNavigationDebugStateRuntime.graphWarmupFallback(entry) && targetPos != null) {
                if (AgentFallbackMovementService.tryImmediateAction(entry, botPos, targetPos)) {
                    return;
                }
                targetPos = AgentFallbackMovementService.resolveSteeringTarget(entry, botPos, targetPos);
            }
            AgentGroundAction action = AgentGroundActionPlanner.planGroundAction(entry, currentFoothold, botPos, targetPos);
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
