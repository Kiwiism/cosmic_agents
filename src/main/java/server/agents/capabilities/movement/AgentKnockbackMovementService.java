package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned seam for combat-driven knockback movement.
 */
public final class AgentKnockbackMovementService {
    private AgentKnockbackMovementService() {
    }

    public static void beginKnockback(BotEntry entry, Character agent, Point position, float initialVelocityY, int airVelocityX) {
        int preservedFacingDir = AgentBotMovementStateRuntime.facingDirection(entry);
        agent.setPosition(position);
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentAirborneLaunchService.launchAirborne(entry, position, initialVelocityY, airVelocityX, true);
        AgentBotMovementStateRuntime.setFacingDirection(entry, preservedFacingDir);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    public static void applyAirKnockback(BotEntry entry, Character agent, int airVelocityX) {
        int preservedFacingDir = AgentBotMovementStateRuntime.facingDirection(entry);
        Point position = agent.getPosition();
        AgentBotMovementStateRuntime.setInAir(entry, true);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, true);
        AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, airVelocityX);
        AgentBotMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentBotMovementStateRuntime.setMovementVelocity(entry,
                AgentMovementKinematicsService.velocityFromDeltaX(airVelocityX),
                AgentAirborneLaunchService.movementVelocityFromAirStep(
                        AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry)));
        AgentBotMovementStateRuntime.setFacingDirection(entry, preservedFacingDir);
        AgentMovementPoseService.syncCharacterState(entry);
    }
}
