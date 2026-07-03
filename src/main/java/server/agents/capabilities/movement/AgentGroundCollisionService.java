package server.agents.capabilities.movement;

import server.bots.BotPhysicsEngine;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Agent-owned seam for grounded collision and ledge queries.
 */
public final class AgentGroundCollisionService {
    private AgentGroundCollisionService() {
    }

    public static boolean canWalkGroundStep(MapleMap map, Point from, int stepX) {
        return BotPhysicsEngine.canWalkGroundStep(map, from, stepX);
    }

    public static boolean isGroundStepBlockedByWall(MapleMap map, Point from, int stepX) {
        return BotPhysicsEngine.isGroundStepBlockedByWall(map, from, stepX);
    }

    public static boolean canStartDownJump(MapleMap map, Point position) {
        return BotPhysicsEngine.canStartDownJump(map, position);
    }

    public static boolean isGroundFarBelow(MapleMap map, Point position) {
        return BotPhysicsEngine.isGroundFarBelow(map, position);
    }
}
