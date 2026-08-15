package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;

public final class AgentJumpActionService {
    private AgentJumpActionService() {
    }

    public static void initiateJump(AgentRuntimeEntry entry, Character agent, int dx) {
        AgentRopeMovementService.beginGroundJump(entry, agent,
                resolveAirVelocityX(agent.getMap(), AgentMovementStateRuntime.movementProfile(entry), dx));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    public static void initiateFixedArcJump(AgentRuntimeEntry entry, Character agent, int dx) {
        initiateJump(entry, agent, dx);
        AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, true);
    }

    public static void initiateRopeJump(AgentRuntimeEntry entry, Character agent, int dx) {
        AgentRopeMovementService.beginClimbUpJump(entry, agent,
                resolveAirVelocityX(agent.getMap(), AgentMovementStateRuntime.movementProfile(entry), dx));
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    public static int resolveAirVelocityX(MapleMap map, AgentMovementProfile profile, int dx) {
        if (dx == 0) {
            return 0;
        }
        int walkStep = AgentMovementKinematicsService.walkStep(map, profile);
        return dx > 0 ? walkStep : -walkStep;
    }

    /** Physics-probes a horizontal jump and accepts only a nearby same-level landing. */
    public static Point probeSameLevelLanding(AgentRuntimeEntry entry,
                                              Character agent,
                                              Point origin,
                                              int direction,
                                              int minimumTravelX,
                                              int yTolerance) {
        if (entry == null || agent == null || agent.getMap() == null || origin == null
                || direction == 0 || !grounded(entry)) {
            return null;
        }
        MapleMap map = agent.getMap();
        AgentMovementProfile profile = AgentMovementStateRuntime.movementProfile(entry);
        int airVelocityX = resolveAirVelocityX(map, profile, direction);
        AgentJumpLanding landing = AgentJumpProbeService.simulateJumpLanding(
                map, origin, airVelocityX, profile);
        Foothold source = AgentGroundingService.findGroundFoothold(map, origin);
        if (source == null || landing == null || landing.point() == null
                || landing.foothold() == null
                || Math.abs(landing.point().x - origin.x) < Math.max(0, minimumTravelX)
                || Math.abs(landing.point().y - origin.y) > Math.max(0, yTolerance)) {
            return null;
        }
        return new Point(landing.point());
    }

    public static boolean grounded(AgentRuntimeEntry entry) {
        return entry != null && AgentMovementStateRuntime.grounded(entry);
    }
}
