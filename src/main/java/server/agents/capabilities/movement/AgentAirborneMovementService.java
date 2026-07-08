package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
import server.agents.runtime.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Rope;

import java.awt.Point;

public final class AgentAirborneMovementService {
    private AgentAirborneMovementService() {
    }

    public static void tickAirborne(AgentRuntimeEntry entry, Point targetPos) {
        long startedAt = System.nanoTime();
        try {
            AgentBotSwimStateRuntime.setSwimming(entry, false);
            AgentMotionTimerService.tickMotionTimers(entry);

            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            Point agentPosition = agent.getPosition();

            if (successfullyGrabbedRope(entry, agent, agentPosition)) {
                return;
            }

            if (!AgentBotMovementStateRuntime.hasMoveDirection(entry) && targetPos != null && shouldApplyAirSteering(entry)) {
                int dx = targetPos.x - agentPosition.x;
                AgentBotMovementStateRuntime.setMoveDirection(entry,
                        Math.abs(dx) > AgentMovementPhysicsConfig.configuredSwimArrivalRadiusPx()
                                ? Integer.signum(dx) : 0);
            }

            AgentAirborneStepResult result = AgentAirbornePhysicsService.stepAirborne(entry, agent);
            if (result == AgentAirborneStepResult.WALL) {
                if (successfullyGrabbedRope(entry, agent, agent.getPosition())) {
                    return;
                }
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }
            if (result == AgentAirborneStepResult.CEILING) {
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }
            if (result == AgentAirborneStepResult.LANDED) {
                AgentBotMovementPhysicsStateRuntime.clearJumpCooldown(entry);
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }

            if (successfullyGrabbedRope(entry, agent, agent.getPosition())) {
                return;
            }
            AgentMovementBroadcastService.broadcastMovement(entry);
        } finally {
            AgentPerformanceMonitor.record("move-air", System.nanoTime() - startedAt);
        }
    }

    static boolean successfullyGrabbedRope(AgentRuntimeEntry entry, Character agent, Point agentPosition) {
        if (!AgentBotClimbStateRuntime.climbUpIntent(entry)) {
            return false;
        }

        for (Rope rope : agent.getMap().getRopes()) {
            if (AgentClimbMovementPolicy.sameRope(AgentBotClimbStateRuntime.blockedRopeGrab(entry), rope)) {
                continue;
            }
            if (Math.abs(rope.x() - agentPosition.x) > AgentMovementPhysicsConfig.configuredRopeGrabX()) {
                continue;
            }
            if (agentPosition.y < rope.topY() || agentPosition.y > rope.bottomY() + 2) {
                continue;
            }

            AgentRopeMovementService.attachToRope(entry, agent, rope, agentPosition.y);
            AgentMovementBroadcastService.broadcastMovement(entry);
            return true;
        }

        return false;
    }

    static boolean shouldApplyAirSteering(AgentRuntimeEntry entry) {
        if (AgentBotMovementPhysicsStateRuntime.fixedAirArc(entry)) {
            return false;
        }
        if (AgentBotMovementStateRuntime.hasDownJumpGracePeriod(entry)) {
            return false;
        }
        AgentNavigationGraph.Edge navEdge = (AgentNavigationGraph.Edge) AgentBotNavigationDebugStateRuntime.activeNavigationEdge(entry);
        if (navEdge == null) {
            return true;
        }
        return navEdge.type != AgentNavigationGraph.EdgeType.JUMP
                && navEdge.type != AgentNavigationGraph.EdgeType.DROP
                && !(navEdge.type == AgentNavigationGraph.EdgeType.CLIMB
                && navEdge.launchStepX != 0);
    }
}
