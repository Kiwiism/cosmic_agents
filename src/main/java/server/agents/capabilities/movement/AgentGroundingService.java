package server.agents.capabilities.movement;

import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Agent-owned ground lookup seam while foothold physics internals migrate.
 */
public final class AgentGroundingService {
    private AgentGroundingService() {
    }

    public static Foothold findGroundFoothold(MapleMap map, Point position) {
        if (map == null || map.getFootholds() == null || position == null) {
            return null;
        }

        Foothold exact = map.getFootholds().findBelow(position);
        Foothold offset = map.getFootholds().findBelow(new Point(
                position.x,
                position.y - AgentMovementPhysicsConfig.configuredMaxSlopeUp()));
        if (exact == null) {
            return offset;
        }
        if (offset == null) {
            return exact;
        }

        Point exactGround = map.getPointBelow(position);
        Point offsetGround = map.getPointBelow(new Point(
                position.x,
                position.y - AgentMovementPhysicsConfig.configuredMaxSlopeUp()));
        if (exactGround == null) {
            return offset;
        }
        if (offsetGround == null) {
            return exact;
        }
        return Math.abs(offsetGround.y - position.y) < Math.abs(exactGround.y - position.y) ? offset : exact;
    }

    public static Point findGroundPoint(MapleMap map, Point position) {
        if (map == null || position == null) {
            return null;
        }

        Point exactGround = map.getPointBelow(position);
        Point offsetGround = map.getPointBelow(new Point(
                position.x,
                position.y - AgentMovementPhysicsConfig.configuredMaxSlopeUp()));
        if (exactGround == null) {
            return offsetGround;
        }
        if (offsetGround == null) {
            return exactGround;
        }

        int exactDistance = Math.abs(exactGround.y - position.y);
        int offsetDistance = Math.abs(offsetGround.y - position.y);
        return offsetDistance < exactDistance ? offsetGround : exactGround;
    }
}
