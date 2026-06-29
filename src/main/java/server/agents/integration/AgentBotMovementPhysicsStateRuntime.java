package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed movement physics flags.
 */
public final class AgentBotMovementPhysicsStateRuntime {
    private AgentBotMovementPhysicsStateRuntime() {
    }

    public static int jumpCooldownMs(BotEntry entry) {
        return entry.jumpCooldownMs();
    }

    public static void setJumpCooldownMs(BotEntry entry, int cooldownMs) {
        entry.setJumpCooldownMs(cooldownMs);
    }

    public static void clearJumpCooldown(BotEntry entry) {
        entry.setJumpCooldownMs(0);
    }

    public static boolean fixedAirArc(BotEntry entry) {
        return entry.fixedAirArc();
    }

    public static float verticalVelocity(BotEntry entry) {
        return entry.verticalVelocity();
    }

    public static void setVerticalVelocity(BotEntry entry, float verticalVelocity) {
        entry.setVerticalVelocity(verticalVelocity);
    }

    public static int airVelocityX(BotEntry entry) {
        return entry.airVelocityX();
    }

    public static void setFixedAirArc(BotEntry entry, boolean fixed) {
        entry.setFixedAirArc(fixed);
    }

    public static double horizontalSpeed(BotEntry entry) {
        return entry.horizontalSpeed();
    }

    public static void setHorizontalSpeed(BotEntry entry, double horizontalSpeed) {
        entry.setHorizontalSpeed(horizontalSpeed);
    }

    public static double physicsX(BotEntry entry) {
        return entry.physicsX();
    }

    public static double physicsY(BotEntry entry) {
        return entry.physicsY();
    }

    public static int roundedPhysicsX(BotEntry entry) {
        return (int) Math.round(entry.physicsX());
    }

    public static Point roundedPhysicsPosition(BotEntry entry) {
        return new Point((int) Math.round(entry.physicsX()), (int) Math.round(entry.physicsY()));
    }

    public static void setPhysicsX(BotEntry entry, double physicsX) {
        entry.setPhysicsX(physicsX);
    }

    public static void setPhysicsPosition(BotEntry entry, Point position) {
        entry.setPhysicsPosition(position);
    }

    public static int lastGroundFhId(BotEntry entry) {
        return entry.lastGroundFhId();
    }

    public static void setLastGroundFhId(BotEntry entry, int footholdId) {
        entry.setLastGroundFhId(footholdId);
    }

    public static Object groundTravelState(BotEntry entry) {
        return entry.groundTravelState();
    }
}
