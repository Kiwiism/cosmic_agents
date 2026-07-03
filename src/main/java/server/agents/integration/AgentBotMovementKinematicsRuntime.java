package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementKinematicsService;

import client.Character;
import server.agents.capabilities.movement.AgentMovementKinematicsSnapshot;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.maps.FieldLimit;
import server.maps.MapleMap;

/**
 * Temporary integration adapter for read-only movement/physics metrics while
 * movement calculation still lives in the legacy bot runtime.
 */
public final class AgentBotMovementKinematicsRuntime {
    private AgentBotMovementKinematicsRuntime() {
    }

    public static AgentMovementKinematicsSnapshot snapshot(Character bot) {
        if (bot == null) {
            return null;
        }

        AgentMovementProfile profile = AgentMovementProfile.fromCharacter(bot);
        MapleMap map = bot.getMap();
        AgentMovementKinematicsSnapshot.MovementProfile movementProfile =
                new AgentMovementKinematicsSnapshot.MovementProfile(
                        profile.totalSpeedStat(),
                        profile.totalJumpStat(),
                        profile.walkVelocityPxs(),
                        profile.hForcePxs(),
                        AgentMovementKinematicsService.jumpForcePerTick(profile),
                        AgentMovementKinematicsService.ropeJumpForcePerTick(profile),
                        AgentMovementKinematicsService.calculateMaxJumpHeight(profile));
        AgentMovementKinematicsSnapshot.MapMovementProfile mapMovementProfile = map == null
                ? null
                : new AgentMovementKinematicsSnapshot.MapMovementProfile(
                        AgentMovementKinematicsService.walkStep(map, profile),
                        AgentMovementKinematicsService.climbStepPerTick(),
                        AgentMovementKinematicsService.maxJumpHorizontalTravel(map, profile),
                        AgentMovementKinematicsService.maxRopeJumpHorizontalTravel(map, profile));

        return new AgentMovementKinematicsSnapshot(
                movementProfile,
                bot.getTotalMoveSpeedStat(),
                bot.getTotalJumpStat(),
                map != null && FieldLimit.MOVEMENTSKILLS.check(map.getFieldLimit()),
                AgentMovementKinematicsService.climbStepPerTick(),
                mapMovementProfile);
    }
}
