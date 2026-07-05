package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.movement.AgentAirborneSteeringState;
import server.agents.capabilities.movement.AgentGroundTravelState;
import server.agents.capabilities.movement.AgentMovementPhysicsState;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed movement physics flags.
 */
public final class AgentBotMovementPhysicsStateRuntime {
    private AgentBotMovementPhysicsStateRuntime() {
    }

    public static int jumpCooldownMs(AgentRuntimeEntry entry) {
        return state(entry).jumpCooldownMs();
    }

    public static void setJumpCooldownMs(AgentRuntimeEntry entry, int cooldownMs) {
        state(entry).setJumpCooldownMs(cooldownMs);
    }

    public static void clearJumpCooldown(AgentRuntimeEntry entry) {
        state(entry).clearJumpCooldown();
    }

    public static boolean fixedAirArc(AgentRuntimeEntry entry) {
        return airborneSteeringState(entry).fixedAirArc();
    }

    public static float verticalVelocity(AgentRuntimeEntry entry) {
        return state(entry).verticalVelocity();
    }

    public static void setVerticalVelocity(AgentRuntimeEntry entry, float verticalVelocity) {
        state(entry).setVerticalVelocity(verticalVelocity);
    }

    public static int airVelocityX(AgentRuntimeEntry entry) {
        return airborneSteeringState(entry).velocityX();
    }

    public static void setAirVelocityX(AgentRuntimeEntry entry, int airVelocityX) {
        airborneSteeringState(entry).setVelocityX(airVelocityX);
    }

    public static double airSteerVelocityX(AgentRuntimeEntry entry) {
        return airborneSteeringState(entry).steeringVelocityX();
    }

    public static void setAirSteerVelocityX(AgentRuntimeEntry entry, double airSteerVelocityX) {
        airborneSteeringState(entry).setSteeringVelocityX(airSteerVelocityX);
    }

    public static void addClampedAirSteerVelocityX(AgentRuntimeEntry entry, double delta, double maxAbs) {
        airborneSteeringState(entry).addClampedSteeringVelocityX(delta, maxAbs);
    }

    public static void setFixedAirArc(AgentRuntimeEntry entry, boolean fixed) {
        airborneSteeringState(entry).setFixedAirArc(fixed);
    }

    public static double fallPeakPhysicsY(AgentRuntimeEntry entry) {
        return state(entry).fallPeakPhysicsY();
    }

    public static boolean hasFallPeakPhysicsY(AgentRuntimeEntry entry) {
        return state(entry).hasFallPeakPhysicsY();
    }

    public static void setFallPeakPhysicsY(AgentRuntimeEntry entry, double fallPeakPhysicsY) {
        state(entry).setFallPeakPhysicsY(fallPeakPhysicsY);
    }

    public static void resetFallPeakPhysicsY(AgentRuntimeEntry entry) {
        state(entry).resetFallPeakPhysicsY();
    }

    public static void recordFallPeakPhysicsY(AgentRuntimeEntry entry, double physicsY) {
        state(entry).recordFallPeakPhysicsY(physicsY);
    }

    public static double horizontalSpeed(AgentRuntimeEntry entry) {
        return state(entry).horizontalSpeed();
    }

    public static void setHorizontalSpeed(AgentRuntimeEntry entry, double horizontalSpeed) {
        state(entry).setHorizontalSpeed(horizontalSpeed);
    }

    public static double physicsX(AgentRuntimeEntry entry) {
        return state(entry).physicsX();
    }

    public static double physicsY(AgentRuntimeEntry entry) {
        return state(entry).physicsY();
    }

    public static int roundedPhysicsX(AgentRuntimeEntry entry) {
        return (int) Math.round(state(entry).physicsX());
    }

    public static Point roundedPhysicsPosition(AgentRuntimeEntry entry) {
        return new Point((int) Math.round(state(entry).physicsX()), (int) Math.round(state(entry).physicsY()));
    }

    public static void setPhysicsX(AgentRuntimeEntry entry, double physicsX) {
        state(entry).setPhysicsX(physicsX);
    }

    public static void setPhysicsY(AgentRuntimeEntry entry, double physicsY) {
        state(entry).setPhysicsY(physicsY);
    }

    public static void setPhysicsPosition(AgentRuntimeEntry entry, double physicsX, double physicsY) {
        state(entry).setPhysicsPosition(physicsX, physicsY);
    }

    public static void setPhysicsPosition(AgentRuntimeEntry entry, Point position) {
        state(entry).setPhysicsPosition(position);
    }

    public static void addPhysicsPosition(AgentRuntimeEntry entry, double deltaX, double deltaY) {
        state(entry).addPhysicsPosition(deltaX, deltaY);
    }

    public static double groundPhysicsCarryMs(AgentRuntimeEntry entry) {
        return state(entry).groundCarryMs();
    }

    public static void setGroundPhysicsCarryMs(AgentRuntimeEntry entry, double groundPhysicsCarryMs) {
        state(entry).setGroundCarryMs(groundPhysicsCarryMs);
    }

    public static int lastGroundFhId(AgentRuntimeEntry entry) {
        return entry.movementPhysicsCacheState().lastGroundFootholdId();
    }

    public static void setLastGroundFhId(AgentRuntimeEntry entry, int footholdId) {
        entry.movementPhysicsCacheState().setLastGroundFootholdId(footholdId);
    }

    public static AgentGroundTravelState groundTravelState(AgentRuntimeEntry entry) {
        return state(entry).groundTravelState();
    }

    private static AgentAirborneSteeringState airborneSteeringState(AgentRuntimeEntry entry) {
        return entry.airborneSteeringState();
    }

    private static AgentMovementPhysicsState state(AgentRuntimeEntry entry) {
        return entry.movementPhysicsState();
    }
}
