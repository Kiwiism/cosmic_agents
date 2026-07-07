package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

public final class AgentJumpActionService {
    private AgentJumpActionService() {
    }

    public static void initiateJump(AgentRuntimeEntry entry, Character agent, int dx) {
        AgentRopeMovementService.beginGroundJump(entry, agent,
                resolveAirVelocityX(agent.getMap(), AgentBotMovementStateRuntime.movementProfile(entry), dx));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    public static void initiateFixedArcJump(AgentRuntimeEntry entry, Character agent, int dx) {
        initiateJump(entry, agent, dx);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, true);
    }

    public static void initiateRopeJump(AgentRuntimeEntry entry, Character agent, int dx) {
        AgentRopeMovementService.beginClimbUpJump(entry, agent,
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
