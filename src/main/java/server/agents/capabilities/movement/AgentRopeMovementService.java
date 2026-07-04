package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationPhysicsService;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;
import server.maps.Rope;

import java.awt.Point;

/**
 * Agent-owned seam for rope and ladder movement actions while physics internals migrate.
 */
public final class AgentRopeMovementService {
    private AgentRopeMovementService() {
    }

    public static void attachToRope(BotEntry entry, Character agent, Rope rope, int y) {
        int ropeY = Math.clamp(y, AgentNavigationPhysicsService.firstClimbableY(rope), rope.bottomY());
        AgentBotClimbStateRuntime.setClimbVerticalDirection(entry, 0);
        setClimbPosition(entry, agent, rope, ropeY);
    }

    public static void holdClimb(BotEntry entry, Character agent) {
        BotPhysicsEngine.holdClimb(entry, agent);
    }

    public static void advanceClimb(BotEntry entry, Character agent) {
        BotPhysicsEngine.advanceClimb(entry, agent);
    }

    public static void beginGroundJump(BotEntry entry, Character agent, int airVelocityX) {
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        if (agent.getMap() != null && agent.getMap().isSwim()) {
            beginSwimGroundJump(entry, agent);
            return;
        }
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.jumpForcePerTick(AgentBotMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                false);
    }

    public static void beginClimbUpJump(BotEntry entry, Character agent, int airVelocityX) {
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.jumpForcePerTick(AgentBotMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                true);
    }

    public static void beginJumpOffRope(BotEntry entry, Character agent, int airVelocityX) {
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.ropeJumpForcePerTick(AgentBotMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                false);
    }

    public static void beginRopeTransferJump(BotEntry entry, Character agent, Rope sourceRope, int airVelocityX) {
        AgentBotClimbStateRuntime.setBlockedRopeGrab(entry, sourceRope);
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.ropeJumpForcePerTick(AgentBotMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                true);
    }

    private static void setClimbPosition(BotEntry entry, Character agent, Rope rope, int y) {
        Point position = new Point(rope.x(), y);
        agent.setPosition(position);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, rope);
        AgentBotMovementStateRuntime.setInAir(entry, false);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentBotMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentBotClimbStateRuntime.clearRopeEntry(entry);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentBotMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    private static void beginSwimGroundJump(BotEntry entry, Character agent) {
        Point position = agent.getPosition();
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        AgentBotSwimStateRuntime.setSwimming(entry, true);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry,
                -profileOrBase(AgentBotMovementStateRuntime.movementProfile(entry)).jumpSpeedPxs());
        AgentGroundPhysicsService.stopGroundMotion(entry);
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentBotMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        AgentBotSwimStateRuntime.setSwimJumpRequested(entry, false);
        AgentBotSwimStateRuntime.setSwimNextJumpAtMs(entry,
                System.currentTimeMillis() + AgentMovementPhysicsConfig.configuredSwimJumpCooldownMs());
        AgentBotMovementStateRuntime.setMovementVelocity(entry, 0,
                Math.round(AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry)));
        AgentMovementPoseService.syncCharacterState(entry);
    }

    private static AgentMovementProfile profileOrBase(AgentMovementProfile profile) {
        return profile != null ? profile : AgentMovementProfile.base();
    }
}
