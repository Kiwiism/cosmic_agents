package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.runtime.AgentPerformanceMonitor;
import server.bots.BotEntry;
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

    public static void tickClimbing(BotEntry entry, Point targetPos, boolean runAiTick) {
        long startedAt = System.nanoTime();
        try {
            Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
            AgentMotionTimerService.tickMotionTimers(entry);
            Point agentPosition = agent.getPosition();
            int dy = targetPos.y - agentPosition.y;
            Rope climbRope = AgentBotClimbStateRuntime.climbRope(entry);
            int dxOwner = targetPos.x - climbRope.x();

            if (runAiTick && !AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)
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

            if (!runAiTick && !AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry)) {
                if (!AgentBotClimbStateRuntime.hasClimbVerticalDirection(entry)) {
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

    public static void jumpOffRope(BotEntry entry, Character agent, int dx) {
        int airVelX = AgentJumpActionService.resolveAirVelocityX(
                agent.getMap(), AgentBotMovementStateRuntime.movementProfile(entry), dx);
        AgentRopeMovementService.beginJumpOffRope(entry, agent, airVelX);
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    public static void jumpToRope(BotEntry entry, Character agent, int dx) {
        Rope sourceRope = AgentBotClimbStateRuntime.climbRope(entry);
        int airVelX = AgentJumpActionService.resolveAirVelocityX(
                agent.getMap(), AgentBotMovementStateRuntime.movementProfile(entry), dx);
        AgentRopeMovementService.beginRopeTransferJump(entry, agent, sourceRope, airVelX);
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    private static void applyClimbAction(BotEntry entry, Character agent, ClimbAction action) {
        AgentBotClimbStateRuntime.setClimbVerticalDirection(entry, switch (action) {
            case CLIMB_UP -> -1;
            case CLIMB_DOWN -> 1;
            default -> 0;
        });

        if (!AgentBotClimbStateRuntime.hasClimbVerticalDirection(entry)) {
            AgentRopeMovementService.holdClimb(entry, agent);
        } else {
            AgentRopeMovementService.advanceClimb(entry, agent);
        }
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    public static boolean shouldHoldClimbIdle(BotEntry entry, int dy, int dxOwner) {
        return AgentClimbMovementPolicy.shouldHoldClimbIdle(
                AgentBotNavigationDebugStateRuntime.hasActiveNavigationEdge(entry),
                AgentBotModeStateRuntime.grinding(entry),
                dy,
                dxOwner,
                AgentMovementPhysicsConfig.configuredStopDist(),
                AgentMovementPhysicsConfig.configuredFollowDist());
    }

    public static boolean shouldSnapToClimbTarget(BotEntry entry, Point targetPos, int dy) {
        if (entry == null) {
            return false;
        }
        return AgentClimbMovementPolicy.shouldSnapToClimbTarget(
                AgentBotClimbStateRuntime.climbing(entry),
                AgentBotClimbStateRuntime.climbRope(entry),
                targetPos,
                dy,
                AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry),
                AgentMovementKinematicsService.climbStepPerTick());
    }
}
