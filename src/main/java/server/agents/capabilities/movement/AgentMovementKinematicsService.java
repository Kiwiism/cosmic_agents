package server.agents.capabilities.movement;

import server.bots.BotPhysicsEngine;
import server.maps.MapleMap;

public final class AgentMovementKinematicsService {
    private AgentMovementKinematicsService() {
    }

    public static int walkStep(MapleMap map) {
        return walkStep(map, AgentMovementProfile.base());
    }

    public static int walkStep(MapleMap map, AgentMovementProfile profile) {
        return BotPhysicsEngine.walkStep(map, profile);
    }

    public static int climbStepPerTick() {
        return Math.max(1, Math.round(AgentMovementPhysicsConfig.configuredClimbSpeedPxs()
                * AgentMovementPhysicsConfig.configuredMovementTickMs() / 1000.0f));
    }

    public static float jumpForcePerTick(AgentMovementProfile profile) {
        return BotPhysicsEngine.jumpForcePerTick(profile);
    }

    public static float ropeJumpForcePerTick(AgentMovementProfile profile) {
        return BotPhysicsEngine.ropeJumpForcePerTick(profile);
    }

    public static float gravityPerTick() {
        return BotPhysicsEngine.gravityPerTick();
    }

    public static float calculateMaxJumpHeight(AgentMovementProfile profile) {
        return BotPhysicsEngine.calculateMaxJumpHeight(profile);
    }

    public static int maxJumpHorizontalTravel(MapleMap map, AgentMovementProfile profile) {
        return BotPhysicsEngine.maxJumpHorizontalTravel(map, profile);
    }

    public static int maxRopeJumpHorizontalTravel(MapleMap map, AgentMovementProfile profile) {
        return BotPhysicsEngine.maxRopeJumpHorizontalTravel(map, profile);
    }

    public static int maxRopeGrabSimulationHorizontalTravel(MapleMap map, AgentMovementProfile profile) {
        return BotPhysicsEngine.maxRopeGrabSimulationHorizontalTravel(map, profile);
    }
}
