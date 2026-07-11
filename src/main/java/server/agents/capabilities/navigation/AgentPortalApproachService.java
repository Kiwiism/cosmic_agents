package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentGroundingService;
import server.maps.MapleMap;
import server.maps.Portal;
import server.maps.Rope;

import java.awt.Point;

/** Resolves a standable or climbable point inside a collision portal hitbox. */
public final class AgentPortalApproachService {
    public static final int COLLISION_PORTAL_TYPE = 3;
    public static final int COLLISION_ENTER_X = 30;
    public static final int COLLISION_ENTER_Y = 60;

    private AgentPortalApproachService() {
    }

    public static Point target(MapleMap map, Portal portal) {
        if (portal == null) {
            return null;
        }
        Point center = portal.getPosition();
        if (map == null || portal.getType() != COLLISION_PORTAL_TYPE) {
            return center;
        }
        Point reachable = reachableApproachInBox(map, center, COLLISION_ENTER_X, COLLISION_ENTER_Y);
        return reachable != null ? reachable : center;
    }

    static Point reachableApproachInBox(MapleMap map, Point center, int radiusX, int radiusY) {
        if (map == null || center == null) {
            return null;
        }
        for (Rope rope : map.getRopes()) {
            int top = AgentNavigationPhysicsService.firstClimbableY(rope);
            if (Math.abs(rope.x() - center.x) <= radiusX
                    && top <= center.y + radiusY
                    && rope.bottomY() >= center.y - radiusY) {
                return new Point(rope.x(), Math.clamp(center.y, top, rope.bottomY()));
            }
        }

        Point best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int offsetX = -radiusX; offsetX <= radiusX; offsetX += 10) {
            Point ground = AgentGroundingService.findGroundPoint(
                    map, new Point(center.x + offsetX, center.y - radiusY));
            if (ground == null || Math.abs(ground.y - center.y) > radiusY) {
                continue;
            }
            long distance = (long) offsetX * offsetX;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Point(center.x + offsetX, ground.y);
            }
        }
        return best;
    }
}
