package server.agents.capabilities.movement;

import server.bots.BotPhysicsEngine;
import server.agents.capabilities.navigation.AgentNavigationPhysicsService;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;

public final class AgentJumpProbeService {
    private AgentJumpProbeService() {
    }

    public static AgentJumpLanding simulateJumpLanding(MapleMap map, Point from, int stepX) {
        return simulateJumpLanding(map, from, stepX, AgentMovementProfile.base());
    }

    public static AgentJumpLanding simulateJumpLanding(MapleMap map,
                                                       Point from,
                                                       int stepX,
                                                       AgentMovementProfile profile) {
        return fromBotLanding(BotPhysicsEngine.simulateJumpLanding(map, from, stepX, profile));
    }

    public static AgentJumpLanding simulateRopeJumpLanding(MapleMap map,
                                                           Point from,
                                                           int stepX,
                                                           AgentMovementProfile profile) {
        return fromBotLanding(BotPhysicsEngine.simulateRopeJumpLanding(map, from, stepX, profile));
    }

    public static AgentPostLandingJump simulateJumpLandingWithPostLandingTicks(MapleMap map,
                                                                               Point from,
                                                                               int stepX,
                                                                               AgentMovementProfile profile,
                                                                               int postLandingTicks) {
        return fromBotPostLanding(BotPhysicsEngine.simulateJumpLandingWithPostLandingTicks(
                map, from, stepX, profile, postLandingTicks));
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

    private static AgentJumpLanding fromBotLanding(BotPhysicsEngine.JumpLanding landing) {
        if (landing == null) {
            return null;
        }
        return new AgentJumpLanding(landing.point(), landing.foothold(), landing.incomingDeltaX(),
                landing.incomingDeltaY(), landing.timeMs());
    }

    private static AgentPostLandingJump fromBotPostLanding(BotPhysicsEngine.PostLandingJump landing) {
        if (landing == null) {
            return null;
        }
        return new AgentPostLandingJump(fromBotLanding(landing.landing()), landing.finalPoint(),
                landing.finalFoothold(), landing.lostGround());
    }
}
