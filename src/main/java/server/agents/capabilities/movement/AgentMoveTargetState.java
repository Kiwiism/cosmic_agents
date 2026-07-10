package server.agents.capabilities.movement;

import java.awt.Point;

/**
 * Explicit one-off movement target requested by command/script control.
 */
public final class AgentMoveTargetState {
    private Point target;
    private boolean precise;

    public Point target() {
        return target == null ? null : new Point(target);
    }

    public boolean hasTarget() {
        return target != null;
    }

    public boolean precise() {
        return precise;
    }

    public void setTarget(Point target, boolean precise) {
        this.target = target == null ? null : new Point(target);
        this.precise = target != null && precise;
    }

    public void clear() {
        target = null;
        precise = false;
    }

    public boolean targetEquals(Point point) {
        return target != null && target.equals(point);
    }
}
