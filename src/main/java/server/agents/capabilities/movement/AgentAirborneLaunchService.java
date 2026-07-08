package server.agents.capabilities.movement;

import server.agents.integration.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentAirborneLaunchService {
    private AgentAirborneLaunchService() {
    }

    public static void launchAirborne(AgentRuntimeEntry entry,
                                      Point position,
                                      float initialVelocityY,
                                      int airVelocityX,
                                      boolean climbUpIntent) {
        AgentClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementStateRuntime.setCrouching(entry, false);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, initialVelocityY);
        AgentGroundPhysicsService.stopGroundMotion(entry);
        AgentClimbStateRuntime.setClimbUpIntent(entry, climbUpIntent);
        AgentClimbStateRuntime.clearRopeEntry(entry);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, airVelocityX);
        AgentMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentMovementStateRuntime.setDownJumpPending(entry, false);
        AgentMovementStateRuntime.clearMoveDirection(entry);
        AgentMovementStateRuntime.setMovementVelocity(entry,
                AgentMovementKinematicsService.velocityFromDeltaX(airVelocityX),
                movementVelocityFromAirStep(initialVelocityY));
        AgentMovementPoseService.syncCharacterState(entry);
    }

    public static float downJumpForcePerTick() {
        return AgentMovementPhysicsConfig.configuredDownJumpSpeedPxs()
                * AgentMovementPhysicsConfig.configuredMovementTickMs() / 1000f;
    }

    public static int movementVelocityFromAirStep(float airVelocityPerTick) {
        return Math.round(airVelocityPerTick
                * (1000f / AgentMovementPhysicsConfig.configuredMovementTickMs()));
    }
}
