package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationPhysicsService;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
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
        BotPhysicsEngine.beginGroundJump(entry, agent, airVelocityX);
    }

    public static void beginClimbUpJump(BotEntry entry, Character agent, int airVelocityX) {
        BotPhysicsEngine.beginClimbUpJump(entry, agent, airVelocityX);
    }

    public static void beginJumpOffRope(BotEntry entry, Character agent, int airVelocityX) {
        BotPhysicsEngine.beginJumpOffRope(entry, agent, airVelocityX);
    }

    public static void beginRopeTransferJump(BotEntry entry, Character agent, Rope sourceRope, int airVelocityX) {
        BotPhysicsEngine.beginRopeTransferJump(entry, agent, sourceRope, airVelocityX);
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
}
