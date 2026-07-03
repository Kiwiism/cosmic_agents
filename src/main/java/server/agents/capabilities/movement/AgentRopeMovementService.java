package server.agents.capabilities.movement;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;
import server.maps.Rope;

/**
 * Agent-owned seam for rope and ladder movement actions while physics internals migrate.
 */
public final class AgentRopeMovementService {
    private AgentRopeMovementService() {
    }

    public static void attachToRope(BotEntry entry, Character agent, Rope rope, int y) {
        BotPhysicsEngine.attachToRope(entry, agent, rope, y);
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
}
