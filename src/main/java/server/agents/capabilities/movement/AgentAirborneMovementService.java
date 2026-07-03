package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
import server.agents.runtime.AgentPerformanceMonitor;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;
import server.maps.Rope;

import java.awt.Point;

public final class AgentAirborneMovementService {
    private AgentAirborneMovementService() {
    }

    public static void tickAirborne(BotEntry entry, Point targetPos) {
        long startedAt = System.nanoTime();
        try {
            AgentBotSwimStateRuntime.setSwimming(entry, false);
            BotPhysicsEngine.tickMotionTimers(entry);

            Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
            Point agentPosition = agent.getPosition();

            if (successfullyGrabbedRope(entry, agent, agentPosition)) {
                return;
            }

            if (!AgentBotMovementStateRuntime.hasMoveDirection(entry) && targetPos != null && shouldApplyAirSteering(entry)) {
                int dx = targetPos.x - agentPosition.x;
                AgentBotMovementStateRuntime.setMoveDirection(entry,
                        Math.abs(dx) > BotPhysicsEngine.configuredSwimArrivalRadiusPx()
                                ? Integer.signum(dx) : 0);
            }

            BotPhysicsEngine.AirborneStepResult result = BotPhysicsEngine.stepAirborne(entry, agent);
            if (result == BotPhysicsEngine.AirborneStepResult.WALL) {
                if (successfullyGrabbedRope(entry, agent, agent.getPosition())) {
                    return;
                }
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }
            if (result == BotPhysicsEngine.AirborneStepResult.CEILING) {
                AgentMovementBroadcastService.broadcastMovement(entry);
                return;
            }
            if (result == BotPhysicsEngine.AirborneStepResult.LANDED) {
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

    static boolean successfullyGrabbedRope(BotEntry entry, Character agent, Point agentPosition) {
        if (!AgentBotClimbStateRuntime.climbUpIntent(entry)) {
            return false;
        }

        for (Rope rope : agent.getMap().getRopes()) {
            if (AgentClimbMovementPolicy.sameRope(AgentBotClimbStateRuntime.blockedRopeGrab(entry), rope)) {
                continue;
            }
            if (Math.abs(rope.x() - agentPosition.x) > BotPhysicsEngine.configuredRopeGrabX()) {
                continue;
            }
            if (agentPosition.y < rope.topY() || agentPosition.y > rope.bottomY() + 2) {
                continue;
            }

            BotPhysicsEngine.attachToRope(entry, agent, rope, agentPosition.y);
            AgentMovementBroadcastService.broadcastMovement(entry);
            return true;
        }

        return false;
    }

    static boolean shouldApplyAirSteering(BotEntry entry) {
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
