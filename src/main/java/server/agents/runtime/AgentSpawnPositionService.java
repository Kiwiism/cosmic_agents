package server.agents.runtime;

import server.bots.BotPhysicsEngine;
import server.maps.MapleMap;

import java.awt.Point;

public final class AgentSpawnPositionService {
    private AgentSpawnPositionService() {
    }

    public static Point resolveSpawnPosition(MapleMap map, Point desiredPosition) {
        return resolveSpawnPosition(map, desiredPosition, BotPhysicsEngine::findGroundPoint);
    }

    static Point resolveSpawnPosition(MapleMap map, Point desiredPosition, GroundPointResolver groundPointResolver) {
        if (map == null || desiredPosition == null) {
            return desiredPosition;
        }

        Point groundPoint = groundPointResolver.findGroundPoint(map, new Point(desiredPosition.x, desiredPosition.y - 1));
        return groundPoint != null ? groundPoint : desiredPosition;
    }

    @FunctionalInterface
    interface GroundPointResolver {
        Point findGroundPoint(MapleMap map, Point point);
    }
}
