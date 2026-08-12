package server.agents.capabilities.movement;

import client.Character;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationRopeEdgeService;
import server.agents.capabilities.navigation.AgentVerticalTraversalService;
import server.agents.capabilities.navigation.AgentVerticalTraversalStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.monitoring.AgentPerformanceMonitor;
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

            // A vertical transaction owns an exact authored exit, which can be partway along a
            // rope. Converge on that waypoint instead of blindly integrating the entry direction;
            // the latter can step across a mid-rope launch height and continue to the rope head.
            if (AgentVerticalTraversalStateRuntime.active(entry)) {
                Point committedExit = AgentVerticalTraversalService.committedClimbExitPosition(entry);
                if (committedExit != null) {
                    AgentRopeMovementService.advanceClimbToward(entry, agent, committedExit.y);
                } else {
                    AgentRopeMovementService.holdClimb(entry, agent);
                }
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }

            // The wider vertical transaction can be invalidated while an Agent is entering a
            // rope (for example, when an unrelated action state is reset). The committed graph
            // edge still owns an exact rope-exit launch height. Honor that structural edge before
            // falling back to the entry direction, otherwise a mid-rope exit such as Forest East
            // 81 -> 44 is climbed past repeatedly.
            Point committedEdgeExit = committedClimbExitPosition(entry, agent, climbRope);
            if (committedEdgeExit != null) {
                AgentRopeMovementService.advanceClimbToward(entry, agent, committedEdgeExit.y);
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }

            // Grinding remains a compatibility fallback for Agents attached before a transaction
            // could be formed (for example, restored runtime state).
            if (AgentModeStateRuntime.grinding(entry)
                    && AgentClimbStateRuntime.hasClimbVerticalDirection(entry)) {
                AgentRopeMovementService.advanceClimb(entry, agent);
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }

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

    private static Point committedClimbExitPosition(AgentRuntimeEntry entry,
                                                     Character agent,
                                                     Rope climbRope) {
        if (entry == null || agent == null || climbRope == null) {
            return null;
        }
        Object active = AgentNavigationDebugStateRuntime.activeNavigationEdge(entry);
        if (!(active instanceof AgentNavigationGraph.Edge edge)) {
            return null;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        if (!AgentNavigationRopeEdgeService.isRopeExitEdge(graph, edge)
                || edge.ropeX != climbRope.x()
                || edge.ropeTopY != climbRope.topY()
                || edge.ropeBottomY != climbRope.bottomY()) {
            return null;
        }
        return new Point(edge.startPoint);
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
