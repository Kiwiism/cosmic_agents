package server.agents.runtime;

/**
 * Mutable high-level movement mode state for one live Agent runtime.
 */
public final class AgentModeState {
    private boolean following;
    private boolean grinding;
    private int followTargetId;

    public boolean following() {
        return following;
    }

    public void setFollowing(boolean following) {
        this.following = following;
    }

    public boolean grinding() {
        return grinding;
    }

    public void setGrinding(boolean grinding) {
        this.grinding = grinding;
    }

    public int followTargetId() {
        return followTargetId;
    }

    public void setFollowTargetId(int followTargetId) {
        this.followTargetId = followTargetId;
    }

    public void startFollowing(int followTargetId) {
        this.followTargetId = followTargetId;
        grinding = false;
        following = true;
    }

    public void startGrinding() {
        followTargetId = 0;
        following = false;
        grinding = true;
    }

    public void stopFollowing() {
        followTargetId = 0;
        following = false;
    }

    public void stopMovementModes() {
        followTargetId = 0;
        following = false;
        grinding = false;
    }
}
