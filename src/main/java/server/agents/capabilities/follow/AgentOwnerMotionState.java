package server.agents.capabilities.follow;

import java.awt.Point;

public final class AgentOwnerMotionState {
    private Point lastOwnerPosition = null;
    private int observedOwnerStepX = 0;
    private int observedOwnerStepY = 0;

    public Point lastOwnerPosition() {
        return lastOwnerPosition == null ? null : new Point(lastOwnerPosition);
    }

    public void setLastOwnerPosition(Point lastOwnerPosition) {
        this.lastOwnerPosition = lastOwnerPosition == null ? null : new Point(lastOwnerPosition);
    }

    public int observedOwnerStepX() {
        return observedOwnerStepX;
    }

    public int observedOwnerStepY() {
        return observedOwnerStepY;
    }

    public void setObservedOwnerStep(int stepX, int stepY) {
        this.observedOwnerStepX = stepX;
        this.observedOwnerStepY = stepY;
    }

    public boolean observedOwnerMoved() {
        return observedOwnerStepX != 0 || observedOwnerStepY != 0;
    }

    public int maxObservedOwnerStep() {
        return Math.max(Math.abs(observedOwnerStepX), Math.abs(observedOwnerStepY));
    }

    public boolean ownerMostlyIdle() {
        return Math.abs(observedOwnerStepX) <= 1 && Math.abs(observedOwnerStepY) <= 1;
    }

    public void clearObservedOwnerStep() {
        setObservedOwnerStep(0, 0);
    }

    public void updateObservedOwnerStep(Point ownerPosition) {
        Point previous = lastOwnerPosition();
        int stepX = previous == null ? 0 : ownerPosition.x - previous.x;
        int stepY = previous == null ? 0 : ownerPosition.y - previous.y;
        setObservedOwnerStep(stepX, stepY);
    }
}
