package server.agents.capabilities.movement;

import client.Character;
import server.maps.FieldLimit;
import server.maps.MapleMap;

/** Builds read-only movement and physics snapshots from the live Cosmic character. */
public final class AgentMovementKinematicsRuntime {
    private AgentMovementKinematicsRuntime() {
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
