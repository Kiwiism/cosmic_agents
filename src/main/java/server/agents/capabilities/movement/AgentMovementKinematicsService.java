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
}
