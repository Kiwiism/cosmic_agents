package server.agents.runtime;

import java.awt.Point;

public final class AgentMovementStuckState {
    private int stuckMs = 0;
    private int unstuckCooldownMs = 0;
    private int stuckCheckX = Integer.MIN_VALUE;
    private int stuckCheckY = Integer.MIN_VALUE;

    public int stuckMs() {
        return stuckMs;
    }

    public void setStuckMs(int stuckMs) {
        this.stuckMs = stuckMs;
    }

    public void addStuckMs(int deltaMs) {
        stuckMs += deltaMs;
    }

    public int unstuckCooldownMs() {
        return unstuckCooldownMs;
    }

    public void setUnstuckCooldownMs(int unstuckCooldownMs) {
        this.unstuckCooldownMs = unstuckCooldownMs;
    }

    public int stuckCheckX() {
        return stuckCheckX;
    }

    public int stuckCheckY() {
        return stuckCheckY;
    }

    public boolean hasStuckCheckPosition() {
        return stuckCheckX != Integer.MIN_VALUE;
    }

    public void setStuckCheckPosition(Point position) {
        stuckCheckX = position.x;
        stuckCheckY = position.y;
    }

    public void clearStuckCheckPosition() {
        stuckCheckX = Integer.MIN_VALUE;
    }
}
