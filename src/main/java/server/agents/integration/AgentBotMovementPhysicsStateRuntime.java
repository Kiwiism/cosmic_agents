package server.agents.integration;

import server.bots.BotEntry;
import server.agents.capabilities.movement.AgentAirborneSteeringState;
import server.agents.capabilities.movement.AgentGroundTravelState;
import server.agents.capabilities.movement.AgentMovementPhysicsState;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed movement physics flags.
 */
public final class AgentBotMovementPhysicsStateRuntime {
    private AgentBotMovementPhysicsStateRuntime() {
    }

    public static int jumpCooldownMs(BotEntry entry) {
        return state(entry).jumpCooldownMs();
    }

    public static void setJumpCooldownMs(BotEntry entry, int cooldownMs) {
        state(entry).setJumpCooldownMs(cooldownMs);
    }

    public static void clearJumpCooldown(BotEntry entry) {
        state(entry).clearJumpCooldown();
    }

    public static boolean fixedAirArc(BotEntry entry) {
        return airborneSteeringState(entry).fixedAirArc();
    }

    public static float verticalVelocity(BotEntry entry) {
        return state(entry).verticalVelocity();
    }

    public static void setVerticalVelocity(BotEntry entry, float verticalVelocity) {
        state(entry).setVerticalVelocity(verticalVelocity);
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
        return state(entry).fallPeakPhysicsY();
    }

    public static boolean hasFallPeakPhysicsY(BotEntry entry) {
        return state(entry).hasFallPeakPhysicsY();
    }

    public static void setFallPeakPhysicsY(BotEntry entry, double fallPeakPhysicsY) {
        state(entry).setFallPeakPhysicsY(fallPeakPhysicsY);
    }

    public static void resetFallPeakPhysicsY(BotEntry entry) {
        state(entry).resetFallPeakPhysicsY();
    }

    public static void recordFallPeakPhysicsY(BotEntry entry, double physicsY) {
        state(entry).recordFallPeakPhysicsY(physicsY);
    }

    public static double horizontalSpeed(BotEntry entry) {
        return state(entry).horizontalSpeed();
    }

    public static void setHorizontalSpeed(BotEntry entry, double horizontalSpeed) {
        state(entry).setHorizontalSpeed(horizontalSpeed);
    }

    public static double physicsX(BotEntry entry) {
        return state(entry).physicsX();
    }

    public static double physicsY(BotEntry entry) {
        return state(entry).physicsY();
    }

    public static int roundedPhysicsX(BotEntry entry) {
        return (int) Math.round(state(entry).physicsX());
    }

    public static Point roundedPhysicsPosition(BotEntry entry) {
        return new Point((int) Math.round(state(entry).physicsX()), (int) Math.round(state(entry).physicsY()));
    }

    public static void setPhysicsX(BotEntry entry, double physicsX) {
        state(entry).setPhysicsX(physicsX);
    }

    public static void setPhysicsY(BotEntry entry, double physicsY) {
        state(entry).setPhysicsY(physicsY);
    }

    public static void setPhysicsPosition(BotEntry entry, double physicsX, double physicsY) {
        state(entry).setPhysicsPosition(physicsX, physicsY);
    }

    public static void setPhysicsPosition(BotEntry entry, Point position) {
        state(entry).setPhysicsPosition(position);
    }

    public static void addPhysicsPosition(BotEntry entry, double deltaX, double deltaY) {
        state(entry).addPhysicsPosition(deltaX, deltaY);
    }

    public static double groundPhysicsCarryMs(BotEntry entry) {
        return state(entry).groundCarryMs();
    }

    public static void setGroundPhysicsCarryMs(BotEntry entry, double groundPhysicsCarryMs) {
        state(entry).setGroundCarryMs(groundPhysicsCarryMs);
    }

    public static int lastGroundFhId(BotEntry entry) {
        return entry.movementPhysicsCacheState().lastGroundFootholdId();
    }

    public static void setLastGroundFhId(BotEntry entry, int footholdId) {
        entry.movementPhysicsCacheState().setLastGroundFootholdId(footholdId);
    }

    public static AgentGroundTravelState groundTravelState(BotEntry entry) {
        return state(entry).groundTravelState();
    }

    private static AgentAirborneSteeringState airborneSteeringState(BotEntry entry) {
        return entry.airborneSteeringState();
    }

    private static AgentMovementPhysicsState state(BotEntry entry) {
        return entry.movementPhysicsState();
    }
}
