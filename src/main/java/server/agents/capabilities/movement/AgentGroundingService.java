package server.agents.capabilities.movement;

import server.bots.BotPhysicsEngine;
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
        return BotPhysicsEngine.findGroundFoothold(map, position);
    }

    public static Point findGroundPoint(MapleMap map, Point position) {
        return BotPhysicsEngine.findGroundPoint(map, position);
    }
}
