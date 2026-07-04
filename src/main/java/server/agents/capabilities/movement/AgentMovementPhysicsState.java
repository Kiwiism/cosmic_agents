package server.agents.capabilities.movement;

import java.awt.Point;

/**
 * Mutable physics scalars for one live Agent runtime.
 */
public final class AgentMovementPhysicsState {
    private float verticalVelocity;
    private double horizontalSpeed;
    private double physicsX;
    private double physicsY;
    private double groundCarryMs;
    private double fallPeakPhysicsY = Double.POSITIVE_INFINITY;
    private boolean inAir;
    private int jumpCooldownMs;

    public boolean inAir() {
        return inAir;
    }

    public void setInAir(boolean inAir) {
        this.inAir = inAir;
    }

    public float verticalVelocity() {
        return verticalVelocity;
    }

    public void setVerticalVelocity(float verticalVelocity) {
        this.verticalVelocity = verticalVelocity;
    }

    public double horizontalSpeed() {
        return horizontalSpeed;
    }

    public void setHorizontalSpeed(double horizontalSpeed) {
        this.horizontalSpeed = horizontalSpeed;
    }

    public double physicsX() {
        return physicsX;
    }

    public void setPhysicsX(double physicsX) {
        this.physicsX = physicsX;
    }

    public double physicsY() {
        return physicsY;
    }

    public void setPhysicsY(double physicsY) {
        this.physicsY = physicsY;
    }

    public void setPhysicsPosition(double physicsX, double physicsY) {
        this.physicsX = physicsX;
        this.physicsY = physicsY;
    }

    public void setPhysicsPosition(Point position) {
        if (position != null) {
            setPhysicsPosition(position.x, position.y);
        }
    }

    public void addPhysicsPosition(double deltaX, double deltaY) {
        physicsX += deltaX;
        physicsY += deltaY;
    }

    public double groundCarryMs() {
        return groundCarryMs;
    }

    public void setGroundCarryMs(double groundCarryMs) {
        this.groundCarryMs = groundCarryMs;
    }

    public double fallPeakPhysicsY() {
        return fallPeakPhysicsY;
    }

    public boolean hasFallPeakPhysicsY() {
        return Double.isFinite(fallPeakPhysicsY);
    }

    public void setFallPeakPhysicsY(double fallPeakPhysicsY) {
        this.fallPeakPhysicsY = fallPeakPhysicsY;
    }

    public void resetFallPeakPhysicsY() {
        fallPeakPhysicsY = Double.POSITIVE_INFINITY;
    }

    public void recordFallPeakPhysicsY(double physicsY) {
        if (physicsY < fallPeakPhysicsY) {
            fallPeakPhysicsY = physicsY;
        }
    }

    public int jumpCooldownMs() {
        return jumpCooldownMs;
    }

    public void setJumpCooldownMs(int jumpCooldownMs) {
        this.jumpCooldownMs = jumpCooldownMs;
    }

    public void clearJumpCooldown() {
        jumpCooldownMs = 0;
    }

    public AgentGroundTravelState groundTravelState() {
        return new AgentGroundTravelState(physicsX, horizontalSpeed, groundCarryMs);
    }
}
