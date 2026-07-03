package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

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
}
