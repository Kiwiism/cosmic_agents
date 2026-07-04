package server.agents.capabilities.social.airshow;

/**
 * Mutable airshow session state for a live Agent.
 */
public final class AgentAirshowState {
    private volatile boolean active = false;
    private volatile long lastTrailAtMs = 0L;

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long lastTrailAtMs() {
        return lastTrailAtMs;
    }

    public void setLastTrailAtMs(long lastTrailAtMs) {
        this.lastTrailAtMs = lastTrailAtMs;
    }
}
