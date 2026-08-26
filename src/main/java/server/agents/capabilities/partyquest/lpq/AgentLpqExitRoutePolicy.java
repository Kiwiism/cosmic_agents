package server.agents.capabilities.partyquest.lpq;

import java.awt.Point;
import java.util.List;
import java.util.Map;

/** Authored LPQ exits and the stable rope/ladder chain used to approach them. */
final class AgentLpqExitRoutePolicy {
    private record RouteKey(int sourceMapId, int destinationMapId) { }
    private record Route(int portalId, List<Point> ascentWaypoints) { }

    private static final Map<RouteKey, Route> ROUTES = Map.ofEntries(
            route(922_010_100, 922_010_200, 2),
            route(922_010_200, 922_010_300, 2,
                    p(-123, -736), p(-178, -1_106), p(-95, -1_428),
                    p(62, -1_659), p(-101, -1_858), p(109, -2_121), p(24, -2_352)),
            route(922_010_201, 922_010_200, 2,
                    p(-8, -2_489), p(13, -3_885)),
            route(922_010_300, 922_010_400, 2, p(-5, -1_507)),
            route(922_010_400, 922_010_500, 7,
                    p(-15, -820), p(4, -1_561), p(-28, -1_946), p(1, -2_169)),
            route(922_010_400, 922_010_401, 2),
            route(922_010_400, 922_010_402, 3),
            route(922_010_400, 922_010_403, 4),
            route(922_010_400, 922_010_404, 5),
            route(922_010_400, 922_010_405, 6),
            route(922_010_401, 922_010_400, 2),
            route(922_010_402, 922_010_400, 2),
            route(922_010_403, 922_010_400, 2),
            route(922_010_404, 922_010_400, 2),
            route(922_010_405, 922_010_400, 2),
            route(922_010_500, 922_010_600, 8),
            route(922_010_500, 922_010_501, 2),
            route(922_010_500, 922_010_502, 3),
            route(922_010_500, 922_010_503, 4),
            route(922_010_500, 922_010_504, 5),
            route(922_010_500, 922_010_505, 6),
            route(922_010_500, 922_010_506, 7),
            route(922_010_501, 922_010_500, 2),
            route(922_010_502, 922_010_500, 2, p(-8, -2_488), p(13, -3_533)),
            route(922_010_503, 922_010_500, 2, p(-8, -2_488), p(13, -3_533)),
            route(922_010_504, 922_010_500, 2, p(-8, -2_488), p(13, -3_533)),
            route(922_010_505, 922_010_500, 2, p(-8, -2_488), p(13, -3_533)),
            route(922_010_506, 922_010_500, 2),
            route(922_010_700, 922_010_800, 2),
            route(922_010_800, 922_010_900, 2));

    private AgentLpqExitRoutePolicy() { }

    static Integer portalId(int sourceMapId, int destinationMapId) {
        Route route = ROUTES.get(new RouteKey(sourceMapId, destinationMapId));
        return route == null ? null : route.portalId();
    }

    static List<Integer> portalIds(int sourceMapId, int destinationMapId) {
        Integer primary = portalId(sourceMapId, destinationMapId);
        if (primary == null) return List.of();
        if (destinationMapId == 922_010_500
                && (sourceMapId == 922_010_501 || sourceMapId == 922_010_506)) {
            return List.of(primary, 3);
        }
        return List.of(primary);
    }

    /**
     * Returns the next authored upward anchor between the character and the exit.
     * Navigation still executes every foothold/rope edge; this only prevents route rediscovery.
     */
    static Point nextWaypoint(int sourceMapId, int destinationMapId,
                              Point currentPosition, Point portalPosition) {
        Route route = ROUTES.get(new RouteKey(sourceMapId, destinationMapId));
        if (route == null || currentPosition == null || portalPosition == null
                || portalPosition.y >= currentPosition.y) return null;
        for (Point waypoint : route.ascentWaypoints()) {
            if (waypoint.y < currentPosition.y - 48 && waypoint.y >= portalPosition.y - 48) {
                return new Point(waypoint);
            }
        }
        return null;
    }

    private static Map.Entry<RouteKey, Route> route(
            int sourceMapId, int destinationMapId, int portalId, Point... waypoints) {
        return Map.entry(new RouteKey(sourceMapId, destinationMapId),
                new Route(portalId, List.of(waypoints)));
    }

    private static Point p(int x, int y) { return new Point(x, y); }
}
