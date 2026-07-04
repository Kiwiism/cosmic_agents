package server.agents.capabilities.movement;

import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

public final class AgentAirborneLaunchService {
    private AgentAirborneLaunchService() {
    }

    public static void launchAirborne(BotEntry entry,
                                      Point position,
                                      float initialVelocityY,
                                      int airVelocityX,
                                      boolean climbUpIntent) {
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry, initialVelocityY);
        AgentGroundPhysicsService.stopGroundMotion(entry);
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, climbUpIntent);
        AgentBotClimbStateRuntime.clearRopeEntry(entry);
        AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, airVelocityX);
        AgentBotMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        AgentBotMovementStateRuntime.clearMoveDirection(entry);
        AgentBotMovementStateRuntime.setMovementVelocity(entry,
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
