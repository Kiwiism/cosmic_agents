package server.agents.capabilities.movement;

import server.bots.BotPhysicsEngine;
import server.maps.Foothold;
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
        Foothold foothold = AgentGroundingService.findGroundFoothold(map, position);
        return foothold != null && !foothold.isForbidFallDown();
    }

    public static boolean isGroundFarBelow(MapleMap map, Point position) {
        if (map == null || position == null) {
            return true;
        }
        Point ground = AgentGroundingService.findGroundPoint(map, position);
        return ground == null || ground.y > position.y + AgentMovementPhysicsConfig.configuredMaxSnapDrop();
    }
}
