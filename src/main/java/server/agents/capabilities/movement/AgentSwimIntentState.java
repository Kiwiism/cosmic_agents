package server.agents.capabilities.movement;

/**
 * Mutable swim-mode input and cooldown state for one live Agent runtime.
 */
public final class AgentSwimIntentState {
    private boolean swimming;
    private int moveDirection;
    private int verticalHold;
    private boolean jumpRequested;
    private long nextJumpAtMs;
    private boolean wallBlocked;

    public boolean swimming() {
        return swimming;
    }

    public void setSwimming(boolean swimming) {
        this.swimming = swimming;
    }

    public int moveDirection() {
        return moveDirection;
    }

    public void setMoveDirection(int direction) {
        moveDirection = Integer.compare(direction, 0);
    }

    public int verticalHold() {
        return verticalHold;
    }

    public void setVerticalHold(int verticalHold) {
        this.verticalHold = Integer.compare(verticalHold, 0);
    }

    public boolean jumpRequested() {
        return jumpRequested;
    }

    public void setJumpRequested(boolean jumpRequested) {
        this.jumpRequested = jumpRequested;
    }

    public long nextJumpAtMs() {
        return nextJumpAtMs;
    }

    public void setNextJumpAtMs(long nextJumpAtMs) {
        this.nextJumpAtMs = nextJumpAtMs;
    }

    public boolean wallBlocked() {
        return wallBlocked;
    }

    public void setWallBlocked(boolean wallBlocked) {
        this.wallBlocked = wallBlocked;
    }

    public void clearInput() {
        setMoveDirection(0);
        setVerticalHold(0);
        setJumpRequested(false);
    }
}
