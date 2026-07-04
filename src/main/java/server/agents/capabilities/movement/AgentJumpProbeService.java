package server.agents.capabilities.movement;

import server.bots.BotPhysicsEngine;
import server.agents.capabilities.navigation.AgentNavigationPhysicsService;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;

public final class AgentJumpProbeService {
    private AgentJumpProbeService() {
    }

    public static BotPhysicsEngine.JumpLanding simulateJumpLanding(MapleMap map, Point from, int stepX) {
        return simulateJumpLanding(map, from, stepX, AgentMovementProfile.base());
    }

    public static BotPhysicsEngine.JumpLanding simulateJumpLanding(MapleMap map,
                                                                   Point from,
                                                                   int stepX,
                                                                   AgentMovementProfile profile) {
        return BotPhysicsEngine.simulateJumpLanding(map, from, stepX, profile);
    }

    public static BotPhysicsEngine.JumpLanding simulateRopeJumpLanding(MapleMap map,
                                                                       Point from,
                                                                       int stepX,
                                                                       AgentMovementProfile profile) {
        return BotPhysicsEngine.simulateRopeJumpLanding(map, from, stepX, profile);
    }

    public static boolean canReachRopeFromGround(MapleMap map, Point from, Rope rope) {
        return canReachRopeFromGround(map, from, rope, AgentMovementProfile.base());
    }

    public static boolean canReachRopeFromGround(MapleMap map, Point from, Rope rope, AgentMovementProfile profile) {
        int dx = Math.abs(rope.x() - from.x);
        if (dx <= AgentMovementPhysicsConfig.configuredRopeGrabX()
                && from.y >= AgentNavigationPhysicsService.firstClimbableY(rope)
                && from.y <= rope.bottomY()) {
            return true;
        }
        if (rope.topY() >= from.y) {
            return false;
        }

        int jumpReach = (int) Math.ceil(AgentMovementKinematicsService.calculateMaxJumpHeight(profile));
        return rope.bottomY() >= from.y - jumpReach
                && dx <= AgentMovementKinematicsService.maxJumpHorizontalTravel(map, profile);
    }
}
