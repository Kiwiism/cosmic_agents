package server.agents.capabilities.movement;

import client.Character;
import java.awt.Point;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
import server.agents.runtime.AgentPerformanceMonitor;
import server.bots.BotEntry;
import server.maps.Foothold;

public final class AgentGroundMovementRuntimeService {
    private AgentGroundMovementRuntimeService() {
    }

    public static void tickGrounded(BotEntry entry, Point targetPos) {
        long startedAt = System.nanoTime();
        try {
            AgentBotSwimStateRuntime.setSwimming(entry, false);
            Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);

            AgentMotionTimerService.tickMotionTimers(entry);

            Foothold currentFoothold = AgentGroundPhysicsService.syncAndDetectGround(entry, bot);
            if (currentFoothold == null) {
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }

            Point botPos = bot.getPosition();
            if (AgentBotClimbStateRuntime.ropeEntryPending(entry)) {
                performTopRopeEntry(entry);
                return;
            }
            if (AgentBotMovementStateRuntime.hasDownJumpPending(entry)) {
                performDownJump(entry);
                return;
            }

            targetPos = AgentGroundTargetService.adjustGrindingTargetPosition(entry, currentFoothold, targetPos);
            if (AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry) && targetPos != null) {
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

    private static void performDownJump(BotEntry entry) {
        AgentQueuedMovementActionService.beginDownJump(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    private static void performTopRopeEntry(BotEntry entry) {
        AgentQueuedMovementActionService.beginTopRopeEntry(entry, AgentBotRuntimeIdentityRuntime.bot(entry));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }
}
