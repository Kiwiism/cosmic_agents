package server.agents.capabilities.movement;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

import java.awt.Point;

/**
 * Agent-owned pose and stance side-effect seam while physics internals migrate.
 */
public final class AgentMovementPoseService {
    private AgentMovementPoseService() {
    }

    public static void resetMotion(BotEntry entry, Point position) {
        BotPhysicsEngine.resetMotion(entry, position);
    }

    public static void teleportTo(BotEntry entry, Character agent, Point position) {
        BotPhysicsEngine.teleportTo(entry, agent, position);
    }

    public static void markDead(BotEntry entry, Character agent) {
        BotPhysicsEngine.markDead(entry, agent);
    }

    public static void idleOnGround(BotEntry entry, Character agent) {
        BotPhysicsEngine.idleOnGround(entry, agent);
    }

    public static void proneOnGround(BotEntry entry, Character agent) {
        BotPhysicsEngine.proneOnGround(entry, agent);
    }

    public static int resolveIdleGroundStance(BotEntry entry) {
        return BotPhysicsEngine.resolveIdleGroundStance(entry);
    }

    public static int resolveStance(BotEntry entry) {
        return BotPhysicsEngine.resolveStance(entry);
    }

    public static boolean isStandingResolvedStance(BotEntry entry) {
        return BotPhysicsEngine.isStandingStance(resolveStance(entry));
    }

    public static void syncCharacterState(BotEntry entry) {
        BotPhysicsEngine.syncCharacterState(entry);
    }
}
