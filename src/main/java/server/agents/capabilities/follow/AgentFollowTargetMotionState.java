package server.agents.capabilities.follow;

import java.awt.Point;

/** Motion observation for the configured follow target, independent of ownership. */
public final class AgentFollowTargetMotionState {
    private Point lastPosition;
    private int observedStepX;
    private int observedStepY;

    public Point lastPosition() { return lastPosition == null ? null : new Point(lastPosition); }
    public void remember(Point position) { lastPosition = position == null ? null : new Point(position); }
    public int observedStepX() { return observedStepX; }
    public int observedStepY() { return observedStepY; }
    public boolean observedMovement() { return observedStepX != 0 || observedStepY != 0; }
    public int maximumObservedStep() { return Math.max(Math.abs(observedStepX), Math.abs(observedStepY)); }
    public boolean mostlyIdle() { return Math.abs(observedStepX) <= 1 && Math.abs(observedStepY) <= 1; }
    public void clearObservedStep() { observedStepX = 0; observedStepY = 0; }

    public void updateObservedStep(Point position) {
        Point previous = lastPosition();
        observedStepX = previous == null ? 0 : position.x - previous.x;
        observedStepY = previous == null ? 0 : position.y - previous.y;
    }
}
