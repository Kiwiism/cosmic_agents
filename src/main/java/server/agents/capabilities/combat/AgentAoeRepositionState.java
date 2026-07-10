package server.agents.capabilities.combat;

import java.awt.Point;

/**
 * Committed AoE sweet-spot target held while an Agent walks into firing position.
 */
public final class AgentAoeRepositionState {
    private Point anchor;
    private long deadlineMs;

    public Point anchor() {
        return anchor == null ? null : new Point(anchor);
    }

    public boolean hasAnchor() {
        return anchor != null;
    }

    public long deadlineMs() {
        return deadlineMs;
    }

    public void setAnchor(Point anchor, long deadlineMs) {
        this.anchor = anchor == null ? null : new Point(anchor);
        this.deadlineMs = anchor == null ? 0L : deadlineMs;
    }

    public void clear() {
        anchor = null;
        deadlineMs = 0L;
    }

    public boolean expiredOrArrived(Point position, long nowMs, int arrivalX) {
        return anchor == null
                || nowMs > deadlineMs
                || position == null
                || Math.abs(anchor.x - position.x) <= arrivalX;
    }
}
