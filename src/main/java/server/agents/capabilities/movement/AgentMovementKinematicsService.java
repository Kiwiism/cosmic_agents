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
}
