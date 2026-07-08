package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned seam for combat-driven knockback movement.
 */
public final class AgentKnockbackMovementService {
    private AgentKnockbackMovementService() {
    }

    public static void beginKnockback(AgentRuntimeEntry entry, Character agent, Point position, float initialVelocityY, int airVelocityX) {
        int preservedFacingDir = AgentMovementStateRuntime.facingDirection(entry);
        agent.setPosition(position);
        AgentClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentAirborneLaunchService.launchAirborne(entry, position, initialVelocityY, airVelocityX, true);
        AgentMovementStateRuntime.setFacingDirection(entry, preservedFacingDir);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    public static void applyAirKnockback(AgentRuntimeEntry entry, Character agent, int airVelocityX) {
        int preservedFacingDir = AgentMovementStateRuntime.facingDirection(entry);
        Point position = agent.getPosition();
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentMovementStateRuntime.setCrouching(entry, false);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentClimbStateRuntime.setClimbUpIntent(entry, true);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, airVelocityX);
        AgentMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentMovementStateRuntime.setDownJumpPending(entry, false);
        AgentClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentMovementStateRuntime.setMovementVelocity(entry,
                AgentMovementKinematicsService.velocityFromDeltaX(airVelocityX),
                AgentAirborneLaunchService.movementVelocityFromAirStep(
                        AgentMovementPhysicsStateRuntime.verticalVelocity(entry)));
        AgentMovementStateRuntime.setFacingDirection(entry, preservedFacingDir);
        AgentMovementPoseService.syncCharacterState(entry);
    }
}
