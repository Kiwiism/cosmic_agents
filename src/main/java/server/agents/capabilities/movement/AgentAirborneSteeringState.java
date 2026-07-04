package server.agents.capabilities.movement;

/**
 * Mutable airborne horizontal steering state for one live Agent runtime.
 */
public final class AgentAirborneSteeringState {
    private int velocityX;
    private double steeringVelocityX;
    private boolean fixedAirArc;

    public int velocityX() {
        return velocityX;
    }

    public void setVelocityX(int velocityX) {
        this.velocityX = velocityX;
    }

    public double steeringVelocityX() {
        return steeringVelocityX;
    }

    public void setSteeringVelocityX(double steeringVelocityX) {
        this.steeringVelocityX = steeringVelocityX;
    }

    public void addClampedSteeringVelocityX(double delta, double maxAbs) {
        steeringVelocityX = Math.clamp(steeringVelocityX + delta, -maxAbs, maxAbs);
    }

    public boolean fixedAirArc() {
        return fixedAirArc;
    }

    public void setFixedAirArc(boolean fixedAirArc) {
        this.fixedAirArc = fixedAirArc;
    }
}
