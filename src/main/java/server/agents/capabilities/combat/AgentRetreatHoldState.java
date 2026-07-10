package server.agents.capabilities.combat;

import java.awt.Point;

/**
 * Temporary local retreat target held to prevent ranged kiting oscillation.
 */
public final class AgentRetreatHoldState {
    private Point position;
    private long untilMs;

    public Point position() {
        return position == null ? null : new Point(position);
    }

    public long untilMs() {
        return untilMs;
    }

    public boolean hasHold() {
        return position != null;
    }

    public boolean active(long nowMs) {
        return hasHold() && nowMs < untilMs;
    }

    public void set(Point position, long untilMs) {
        this.position = position == null ? null : new Point(position);
        this.untilMs = position == null ? 0L : untilMs;
    }

    public void clear() {
        position = null;
        untilMs = 0L;
    }
}
