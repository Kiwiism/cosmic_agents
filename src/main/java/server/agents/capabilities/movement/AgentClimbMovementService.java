package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentClimbStateRuntime;
import server.agents.integration.AgentModeStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Rope;

import java.awt.Point;

public final class AgentClimbMovementService {
    private enum ClimbAction {
        IDLE,
        CLIMB_UP,
        CLIMB_DOWN
    }

    private AgentClimbMovementService() {
    }

    public static void tickClimbing(AgentRuntimeEntry entry, Point targetPos, boolean runAiTick) {
        long startedAt = System.nanoTime();
        try {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            AgentMotionTimerService.tickMotionTimers(entry);
            Point agentPosition = agent.getPosition();
            int dy = targetPos.y - agentPosition.y;
            Rope climbRope = AgentClimbStateRuntime.climbRope(entry);
            int dxOwner = targetPos.x - climbRope.x();

            if (runAiTick && !AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
                    && Math.abs(dxOwner) > AgentMovementPhysicsConfig.configuredFollowDist()
                    && climbRope.bottomY() < targetPos.y) {
                jumpOffRope(entry, agent, dxOwner);
                return;
            }

            if (shouldHoldClimbIdle(entry, dy, dxOwner)) {
                AgentRopeMovementService.holdClimb(entry, agent);
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }

            if (shouldSnapToClimbTarget(entry, targetPos, dy)) {
                AgentRopeMovementService.attachToRope(entry, agent, climbRope, targetPos.y);
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }

            if (!runAiTick && !AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
                if (!AgentClimbStateRuntime.hasClimbVerticalDirection(entry)) {
                    AgentRopeMovementService.holdClimb(entry, agent);
                } else {
                    AgentRopeMovementService.advanceClimb(entry, agent);
                }
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }

            ClimbAction action = dy < 0
                    ? ClimbAction.CLIMB_UP
                    : dy > 0 ? ClimbAction.CLIMB_DOWN : ClimbAction.IDLE;
            applyClimbAction(entry, agent, action);
        } finally {
            AgentPerformanceMonitor.record("move-climb", System.nanoTime() - startedAt);
        }
    }

    public static void jumpOffRope(AgentRuntimeEntry entry, Character agent, int dx) {
        int airVelX = AgentJumpActionService.resolveAirVelocityX(
                agent.getMap(), AgentMovementStateRuntime.movementProfile(entry), dx);
        AgentRopeMovementService.beginJumpOffRope(entry, agent, airVelX);
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    public static void jumpToRope(AgentRuntimeEntry entry, Character agent, int dx) {
        Rope sourceRope = AgentClimbStateRuntime.climbRope(entry);
        int airVelX = AgentJumpActionService.resolveAirVelocityX(
                agent.getMap(), AgentMovementStateRuntime.movementProfile(entry), dx);
        AgentRopeMovementService.beginRopeTransferJump(entry, agent, sourceRope, airVelX);
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    private static void applyClimbAction(AgentRuntimeEntry entry, Character agent, ClimbAction action) {
        AgentClimbStateRuntime.setClimbVerticalDirection(entry, switch (action) {
            case CLIMB_UP -> -1;
            case CLIMB_DOWN -> 1;
            default -> 0;
        });

        if (!AgentClimbStateRuntime.hasClimbVerticalDirection(entry)) {
            AgentRopeMovementService.holdClimb(entry, agent);
        } else {
            AgentRopeMovementService.advanceClimb(entry, agent);
        }
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    public static boolean shouldHoldClimbIdle(AgentRuntimeEntry entry, int dy, int dxOwner) {
        return AgentClimbMovementPolicy.shouldHoldClimbIdle(
                AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry),
                AgentModeStateRuntime.grinding(entry),
                dy,
                dxOwner,
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentMovementPhysicsConfig.configuredFollowDist());
    }

    public static boolean shouldSnapToClimbTarget(AgentRuntimeEntry entry, Point targetPos, int dy) {
        if (entry == null) {
            return false;
        }
        return AgentClimbMovementPolicy.shouldSnapToClimbTarget(
                AgentClimbStateRuntime.climbing(entry),
                AgentClimbStateRuntime.climbRope(entry),
                targetPos,
                dy,
                AgentNavigationDebugStateRuntime.navPreciseTarget(entry),
                AgentMovementKinematicsService.climbStepPerTick());
    }
}
