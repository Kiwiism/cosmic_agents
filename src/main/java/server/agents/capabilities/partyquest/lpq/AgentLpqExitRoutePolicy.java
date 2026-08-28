package server.agents.capabilities.partyquest.lpq;

import java.awt.Point;
import java.util.List;
import java.util.Map;

/** Authored LPQ exits and the stable rope/ladder chain used to approach them. */
final class AgentLpqExitRoutePolicy {
    private record RouteKey(int sourceMapId, int destinationMapId) { }
    private record Route(int portalId, List<Point> ascentWaypoints) { }

    private static final Map<RouteKey, Route> ROUTES = Map.ofEntries(
            route(922_010_100, 922_010_200, 2,
                    p(-117, 85), p(-117, -178)),
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
            route(922_010_401, 922_010_400, 2,
                    p(-1306, 213), p(-1306, -73)),
            route(922_010_402, 922_010_400, 2,
                    p(-1306, 213), p(-1306, -73)),
            route(922_010_403, 922_010_400, 2,
                    p(-1306, 213), p(-1306, -73)),
            route(922_010_404, 922_010_400, 2,
                    p(-1306, 213), p(-1306, -73)),
            route(922_010_405, 922_010_400, 2,
                    p(-1306, 213), p(-1306, -73)),
            route(922_010_500, 922_010_600, 8),
            route(922_010_500, 922_010_501, 2),
            route(922_010_500, 922_010_502, 3),
            route(922_010_500, 922_010_503, 4),
            route(922_010_500, 922_010_504, 5),
            route(922_010_500, 922_010_505, 6),
            route(922_010_500, 922_010_506, 7),
            // The Teleport room is cleared from top to bottom. Leave through the
            // authored bottom portal instead of retracing the entire room.
            route(922_010_501, 922_010_500, 3),
            // Ordinary rooms have two authored ropes. Include both ends so an
            // exit plan never asks navigation to rediscover a multi-screen
            // rope transition in one step.
            route(922_010_502, 922_010_500, 2,
                    p(-8, -286), p(-8, -2_488), p(13, -2_562), p(13, -3_533)),
            route(922_010_503, 922_010_500, 2,
                    p(-8, -286), p(-8, -2_488), p(13, -2_562), p(13, -3_533)),
            route(922_010_504, 922_010_500, 2,
                    p(-8, -286), p(-8, -2_488), p(13, -2_562), p(13, -3_533)),
            route(922_010_505, 922_010_500, 2,
                    p(-8, -286), p(-8, -2_488), p(13, -2_562), p(13, -3_533)),
            // The Dark Sight room exits through authored portal 2 at the top.
            // Its lower half is a zig-zag chain of seven short ropes before
            // joining the same two-rope upper shaft as rooms 502-505.
            route(922_010_506, 922_010_500, 2,
                    p(-123, -252), p(-123, -428),
                    p(118, -452), p(118, -628),
                    p(-71, -671), p(-71, -847),
                    p(-5, -886), p(-5, -1_062),
                    p(-61, -1_079), p(-61, -1_255),
                    p(-24, -1_281), p(-24, -1_457),
                    p(-8, -1_518), p(-8, -2_488),
                    p(13, -2_562), p(13, -3_533)),
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
        return List.of(primary);
    }

    /**
     * Returns the next authored upward anchor between the character and the exit.
     * Navigation still executes every foothold/rope edge; this only prevents route rediscovery.
     */
    static Point nextWaypoint(int sourceMapId, int destinationMapId,
                              Point currentPosition, Point portalPosition) {
        Route route = ROUTES.get(new RouteKey(sourceMapId, destinationMapId));
        if (route == null || currentPosition == null || portalPosition == null) return null;
        if (stageFourRoom(sourceMapId) && destinationMapId == 922_010_400) {
            return stageFourRoomExitWaypoint(currentPosition, portalPosition);
        }
        if (portalPosition.y >= currentPosition.y) return null;
        for (Point waypoint : route.ascentWaypoints()) {
            // Several Dark Sight room rope landings are only 17-43 px above
            // the prior rope top but hundreds of pixels sideways. Preserve
            // those authored transfer ledges instead of skipping them.
            if (waypoint.y < currentPosition.y - 12 && waypoint.y >= portalPosition.y - 48) {
                return new Point(waypoint);
            }
        }
        return null;
    }

    /**
     * Stage 4 rooms are long horizontal maps. Their exit platform is isolated from
     * the main lower walk region by the far-left rope, so a simple request for portal
     * 2 cannot produce a route. First cross to the rope foot, then climb to its top.
     */
    static Point stageFourRoomExitWaypoint(Point currentPosition, Point portalPosition) {
        if (currentPosition == null || portalPosition == null) return null;
        // Keep the long dark-room return visibly grounded. These are authored
        // foothold landings from right to left; issuing one short leg at a time
        // prevents a single cross-map route from looking like a position zip.
        if (currentPosition.x > 180) return p(180, 165);
        if (currentPosition.x > -180) return p(-180, 165);
        if (currentPosition.x > -450) return p(-450, 225);
        if (currentPosition.x > -720) return p(-720, 225);
        if (currentPosition.x > -1_023) return p(-1_023, 165);
        if (currentPosition.x > -1_260) return p(-1_260, 285);
        if (currentPosition.x > -1_300) return p(-1_306, 213);
        if (currentPosition.y > -60) return p(-1_306, -73);
        return null;
    }

    private static boolean stageFourRoom(int mapId) {
        return mapId >= 922_010_401 && mapId <= 922_010_405;
    }

    private static Map.Entry<RouteKey, Route> route(
            int sourceMapId, int destinationMapId, int portalId, Point... waypoints) {
        return Map.entry(new RouteKey(sourceMapId, destinationMapId),
                new Route(portalId, List.of(waypoints)));
    }

    private static Point p(int x, int y) { return new Point(x, y); }
}
