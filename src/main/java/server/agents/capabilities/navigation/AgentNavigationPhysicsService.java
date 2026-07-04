package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.bots.BotPhysicsEngine;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;
import java.util.Map;

/**
 * Agent-owned seam for navigation-facing physics helpers while internals migrate.
 */
public final class AgentNavigationPhysicsService {
    private static final int WALK_GAP_PX = 12;

    private AgentNavigationPhysicsService() {
    }

    public static int firstClimbableY(Rope rope) {
        return Math.min(rope.bottomY(), rope.topY() + 1);
    }

    public static boolean isWalkableEndpointStep(int dx, int dy) {
        return dx <= WALK_GAP_PX
                && dy <= AgentMovementPhysicsConfig.configuredMaxSnapDrop()
                && dy >= -AgentMovementPhysicsConfig.configuredMaxSlopeUp();
    }

    public static boolean canWalkAcrossFootholds(Foothold first, Foothold second) {
        if (first == null || second == null || first.isWall() || second.isWall()) {
            return false;
        }

        EndpointConnection connection = sharedEndpointConnection(first, second);
        if (connection == null) {
            connection = closestEndpointConnection(first, second);
            if (connection == null
                    || (Math.abs(connection.to().x - connection.from().x)
                    + Math.abs(connection.to().y - connection.from().y)) > 2) {
                return false;
            }
        }

        int dx = Math.abs(connection.to().x - connection.from().x);
        int dy = connection.to().y - connection.from().y;
        return isWalkableEndpointStep(dx, dy);
    }

    public static void setBuildWalkRegionLookup(MapleMap map,
                                                Map<Integer, AgentNavigationGraph.Region> regionsById,
                                                Map<Integer, Integer> regionIdByFootholdId,
                                                Map<Integer, Foothold> footholdsById) {
        BotPhysicsEngine.setBuildWalkRegionLookup(map, regionsById, regionIdByFootholdId, footholdsById);
    }

    public static void clearBuildWalkRegionLookup() {
        BotPhysicsEngine.clearBuildWalkRegionLookup();
    }

    private record EndpointConnection(Point from, Point to) {
    }

    private static Point[] endpoints(Foothold foothold) {
        return new Point[]{new Point(foothold.getX1(), foothold.getY1()), new Point(foothold.getX2(), foothold.getY2())};
    }

    private static EndpointConnection closestEndpointConnection(Foothold first, Foothold second) {
        EndpointConnection best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Point from : endpoints(first)) {
            for (Point to : endpoints(second)) {
                int distance = Math.abs(to.x - from.x) + Math.abs(to.y - from.y);
                if (distance < bestDistance) {
                    best = new EndpointConnection(from, to);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static EndpointConnection sharedEndpointConnection(Foothold first, Foothold second) {
        for (Point from : endpoints(first)) {
            for (Point to : endpoints(second)) {
                if (from.equals(to)) {
                    return new EndpointConnection(from, to);
                }
            }
        }
        return null;
    }
}
