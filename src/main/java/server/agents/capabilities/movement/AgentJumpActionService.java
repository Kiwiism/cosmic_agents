package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;
import server.maps.MapleMap;

public final class AgentJumpActionService {
    private AgentJumpActionService() {
    }

    public static void initiateJump(BotEntry entry, Character agent, int dx) {
        BotPhysicsEngine.beginGroundJump(entry, agent,
                resolveAirVelocityX(agent.getMap(), AgentBotMovementStateRuntime.movementProfile(entry), dx));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    public static void initiateFixedArcJump(BotEntry entry, Character agent, int dx) {
        initiateJump(entry, agent, dx);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, true);
    }

    public static void initiateRopeJump(BotEntry entry, Character agent, int dx) {
        BotPhysicsEngine.beginClimbUpJump(entry, agent,
                resolveAirVelocityX(agent.getMap(), AgentBotMovementStateRuntime.movementProfile(entry), dx));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    public static int resolveAirVelocityX(MapleMap map, AgentMovementProfile profile, int dx) {
        if (dx == 0) {
            return 0;
        }
        int walkStep = AgentMovementKinematicsService.walkStep(map, profile);
        return dx > 0 ? walkStep : -walkStep;
    }
}
