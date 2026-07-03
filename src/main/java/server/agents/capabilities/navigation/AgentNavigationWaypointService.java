package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;
import server.maps.Foothold;
import server.maps.Rope;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent-owned waypoint selection for navigation edges that do not require live
 * runtime state.
 */
public final class AgentNavigationWaypointService {
    private AgentNavigationWaypointService() {
    }

    public static Point selectJumpWaypoint(AgentNavigationGraph graph,
                                           Point botPos,
                                           AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return new Point(edge.startPoint);
        }
        int targetX = edge.containsLaunchX(botPos.x)
                ? botPos.x
                : botPos.x < edge.launchMinX ? edge.launchMinX : edge.launchMaxX;
        return fromRegion.pointAt(targetX);
    }

    public static Point selectStraightDropWaypoint(AgentNavigationGraph graph,
                                                   Point botPos,
                                                   AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph.Region fromRegion = graph != null ? graph.getRegion(edge.fromRegionId) : null;
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return new Point(edge.startPoint);
        }
        int targetX = edge.containsLaunchX(botPos.x)
                ? botPos.x
                : botPos.x < edge.launchMinX ? edge.launchMinX : edge.launchMaxX;
        return fromRegion.pointAt(targetX);
    }

    public static Point selectDropWaypoint(BotEntry entry,
                                           AgentNavigationGraph graph,
                                           Point botPos,
                                           AgentNavigationGraph.Edge edge) {
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            return new Point(edge.endPoint);
        }
        if (edge.launchStepX == 0) {
            return selectStraightDropWaypoint(graph, botPos, edge);
        }

        if (AgentNavigationLaunchWindowService.hasReachedDirectionalDropRunway(botPos, edge)) {
            return new Point(edge.endPoint);
        }

        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return new Point(edge.endPoint);
        }

        BotPhysicsEngine.WalkOffLanding liveOutcome = BotPhysicsEngine.simulateWalkOffLanding(
                AgentBotRuntimeIdentityRuntime.botMap(entry), botPos, Integer.signum(edge.launchStepX),
                AgentBotMovementPhysicsStateRuntime.groundTravelState(entry),
                AgentBotMovementStateRuntime.movementProfile(entry));
        if (matchesDirectionalDrop(edge, graph, liveOutcome)) {
            // Like rope top step-offs, once the continuous-control exit is naturally executable
            // we stop targeting an intermediate anchor and just keep feeding the authored
            // direction until physics performs the dismount.
            return new Point(edge.endPoint);
        }
        return new Point(edge.startPoint);
    }

    private static boolean matchesDirectionalDrop(AgentNavigationGraph.Edge edge,
                                                  AgentNavigationGraph graph,
                                                  BotPhysicsEngine.WalkOffLanding outcome) {
        if (outcome == null || outcome.landing() == null) {
            return false;
        }
        Foothold landingFoothold = outcome.landing().foothold();
        if (landingFoothold == null) {
            return false;
        }
        if (graph.regionIdByFootholdId.getOrDefault(landingFoothold.getId(), -1) != edge.toRegionId) {
            return false;
        }
        int xTolerance = Math.max(6, Math.abs(edge.launchStepX) + 2);
        int yTolerance = AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2;
        return Math.abs(outcome.landing().point().x - edge.endPoint.x) <= xTolerance
                && Math.abs(outcome.landing().point().y - edge.endPoint.y) <= yTolerance;
    }

    public static int selectJumpLaunchX(BotEntry entry,
                                        AgentNavigationGraph graph,
                                        AgentNavigationGraph.Edge edge) {
        if (entry == null || graph == null || edge == null || edge.type != AgentNavigationGraph.EdgeType.JUMP) {
            return edge != null ? edge.startPoint.x : 0;
        }
        AgentNavigationGraph.Region fromRegion = graph.getRegion(edge.fromRegionId);
        if (fromRegion == null || fromRegion.isRopeRegion) {
            return edge.startPoint.x;
        }
        int cachedLaunchX = AgentBotNavigationDebugStateRuntime.navJumpLaunchX(entry);
        if (AgentBotNavigationDebugStateRuntime.matchesNavJumpLaunchEdge(entry, edge)
                && cachedLaunchX >= edge.launchMinX
                && cachedLaunchX <= edge.launchMaxX) {
            return cachedLaunchX;
        }

        int minX = Math.max(edge.launchMinX, fromRegion.minX);
        int maxX = Math.min(edge.launchMaxX, fromRegion.maxX);
        if (minX > maxX) {
            minX = edge.launchMinX;
            maxX = edge.launchMaxX;
        }

        int width = Math.max(0, maxX - minX);
        int margin = Math.min(width / 2, Math.max(1,
                AgentMovementKinematicsService.walkStep(AgentBotRuntimeIdentityRuntime.botMap(entry),
                        AgentBotMovementStateRuntime.movementProfile(entry)) * 2));
        int randomMinX = minX + margin;
        int randomMaxX = maxX - margin;
        if (randomMinX > randomMaxX) {
            randomMinX = minX;
            randomMaxX = maxX;
        }

        int selectedX = randomMinX >= randomMaxX
                ? randomMinX
                : ThreadLocalRandom.current().nextInt(randomMinX, randomMaxX + 1);
        AgentBotNavigationDebugStateRuntime.rememberNavJumpLaunch(entry, edge, selectedX);
        return selectedX;
    }

    public static Point selectClimbWaypoint(AgentNavigationGraph graph,
                                            BotEntry entry,
                                            Point botPos,
                                            AgentNavigationGraph.Edge edge,
                                            ClimbExitReadiness climbExitReadiness) {
        if (AgentBotMovementStateRuntime.inAir(entry)) {
            return new Point(edge.endPoint);
        }
        if (AgentBotClimbStateRuntime.climbing(entry) && edge.launchStepX != 0) {
            if (graph != null && climbExitReadiness.canExecute(graph, entry, botPos, edge)) {
                return new Point(botPos);
            }
            return new Point(edge.startPoint);
        }
        if (AgentBotClimbStateRuntime.climbing(entry)) {
            Rope climbRope = AgentBotClimbStateRuntime.climbRope(entry);
            int ropeX = climbRope != null ? climbRope.x() : edge.startPoint.x;
            return new Point(ropeX, edge.endPoint.y);
        }
        return new Point(edge.startPoint);
    }

    public interface ClimbExitReadiness {
        boolean canExecute(AgentNavigationGraph graph, BotEntry entry, Point botPos, AgentNavigationGraph.Edge edge);
    }
}
