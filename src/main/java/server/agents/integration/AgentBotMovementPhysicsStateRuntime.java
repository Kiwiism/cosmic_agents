package server.agents.integration;

import server.bots.BotEntry;
import server.agents.capabilities.movement.AgentAirborneSteeringState;
import server.agents.capabilities.movement.AgentGroundTravelState;

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
        return airborneSteeringState(entry).fixedAirArc();
    }

    public static float verticalVelocity(BotEntry entry) {
        return entry.verticalVelocity();
    }

    public static void setVerticalVelocity(BotEntry entry, float verticalVelocity) {
        entry.setVerticalVelocity(verticalVelocity);
    }

    public static int airVelocityX(BotEntry entry) {
        return airborneSteeringState(entry).velocityX();
    }

    public static void setAirVelocityX(BotEntry entry, int airVelocityX) {
        airborneSteeringState(entry).setVelocityX(airVelocityX);
    }

    public static double airSteerVelocityX(BotEntry entry) {
        return airborneSteeringState(entry).steeringVelocityX();
    }

    public static void setAirSteerVelocityX(BotEntry entry, double airSteerVelocityX) {
        airborneSteeringState(entry).setSteeringVelocityX(airSteerVelocityX);
    }

    public static void addClampedAirSteerVelocityX(BotEntry entry, double delta, double maxAbs) {
        airborneSteeringState(entry).addClampedSteeringVelocityX(delta, maxAbs);
    }

    public static void setFixedAirArc(BotEntry entry, boolean fixed) {
        airborneSteeringState(entry).setFixedAirArc(fixed);
    }

    public static double fallPeakPhysicsY(BotEntry entry) {
        return entry.fallPeakPhysicsY();
    }

    public static boolean hasFallPeakPhysicsY(BotEntry entry) {
        return Double.isFinite(entry.fallPeakPhysicsY());
    }

    public static void setFallPeakPhysicsY(BotEntry entry, double fallPeakPhysicsY) {
        entry.setFallPeakPhysicsY(fallPeakPhysicsY);
    }

    public static void resetFallPeakPhysicsY(BotEntry entry) {
        entry.resetFallPeakPhysicsY();
    }

    public static void recordFallPeakPhysicsY(BotEntry entry, double physicsY) {
        if (physicsY < entry.fallPeakPhysicsY()) {
            entry.setFallPeakPhysicsY(physicsY);
        }
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

    public static void setPhysicsY(BotEntry entry, double physicsY) {
        entry.setPhysicsY(physicsY);
    }

    public static void setPhysicsPosition(BotEntry entry, double physicsX, double physicsY) {
        entry.setPhysicsPosition(physicsX, physicsY);
    }

    public static void setPhysicsPosition(BotEntry entry, Point position) {
        entry.setPhysicsPosition(position);
    }

    public static void addPhysicsPosition(BotEntry entry, double deltaX, double deltaY) {
        entry.addPhysicsPosition(deltaX, deltaY);
    }

    public static double groundPhysicsCarryMs(BotEntry entry) {
        return entry.groundPhysicsCarryMs();
    }

    public static void setGroundPhysicsCarryMs(BotEntry entry, double groundPhysicsCarryMs) {
        entry.setGroundPhysicsCarryMs(groundPhysicsCarryMs);
    }

    public static int lastGroundFhId(BotEntry entry) {
        return entry.movementPhysicsCacheState().lastGroundFootholdId();
    }

    public static void setLastGroundFhId(BotEntry entry, int footholdId) {
        entry.movementPhysicsCacheState().setLastGroundFootholdId(footholdId);
    }

    public static AgentGroundTravelState groundTravelState(BotEntry entry) {
        return entry.groundTravelState();
    }

    private static AgentAirborneSteeringState airborneSteeringState(BotEntry entry) {
        return entry.airborneSteeringState();
    }
}
