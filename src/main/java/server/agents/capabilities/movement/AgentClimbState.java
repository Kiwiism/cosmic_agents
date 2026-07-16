package server.agents.capabilities.movement;

import server.maps.Rope;

/**
 * Mutable rope and ladder climb state for one live Agent runtime.
 */
public final class AgentClimbState {
    private boolean climbing;
    private Rope climbRope;
    private int verticalDirection;
    private boolean climbUpIntent;
    private Rope blockedRopeGrab;
    private int ropeGrabCooldownMs;
    private boolean ropeEntryPending;
    private Rope ropeEntryRope;
    private int ropeEntryY;
    private int ropeEntryDirection;

    public boolean climbing() {
        return climbing;
    }

    public Rope climbRope() {
        return climbRope;
    }

    public boolean hasClimbRope() {
        return climbRope != null;
    }

    public void setClimbingFlag(boolean climbing) {
        this.climbing = climbing;
    }

    public void setClimbingOnRope(Rope rope) {
        climbing = rope != null;
        climbRope = rope;
    }

    public int verticalDirection() {
        return verticalDirection;
    }

    public boolean hasVerticalDirection() {
        return verticalDirection != 0;
    }

    public void setVerticalDirection(int direction) {
        verticalDirection = Integer.compare(direction, 0);
    }

    public boolean climbUpIntent() {
        return climbUpIntent;
    }

    public void setClimbUpIntent(boolean climbUpIntent) {
        this.climbUpIntent = climbUpIntent;
    }

    public Rope blockedRopeGrab() {
        return blockedRopeGrab;
    }

    public void setBlockedRopeGrab(Rope rope) {
        blockedRopeGrab = rope;
    }

    public void clearBlockedRopeGrab() {
        blockedRopeGrab = null;
    }

    public int ropeGrabCooldownMs() {
        return ropeGrabCooldownMs;
    }

    public void setRopeGrabCooldownMs(int ropeGrabCooldownMs) {
        this.ropeGrabCooldownMs = ropeGrabCooldownMs;
    }

    public boolean ropeEntryPending() {
        return ropeEntryPending;
    }

    public Rope ropeEntryRope() {
        return ropeEntryRope;
    }

    public int ropeEntryY() {
        return ropeEntryY;
    }

    public int ropeEntryDirection() {
        return ropeEntryDirection;
    }

    public void queueRopeEntry(Rope rope, int y, int direction) {
        ropeEntryPending = true;
        ropeEntryRope = rope;
        ropeEntryY = y;
        ropeEntryDirection = Integer.compare(direction, 0);
    }

    public void clearRopeEntry() {
        ropeEntryPending = false;
        ropeEntryRope = null;
        ropeEntryY = 0;
        ropeEntryDirection = 0;
    }
}
