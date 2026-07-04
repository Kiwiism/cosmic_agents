package server.agents.runtime;

public final class AgentTickState {
    private boolean lastTickWasAi = false;
    private long lastTickAtMs = 0L;
    private long lastHeartbeatAtMs = 0L;
    private long nextFollowIdleMovementCheckAtMs = 0L;

    public boolean lastTickWasAi() {
        return lastTickWasAi;
    }

    public long lastTickAtMs() {
        return lastTickAtMs;
    }

    public void recordTick(boolean aiTick, long tickAtMs) {
        this.lastTickWasAi = aiTick;
        this.lastTickAtMs = tickAtMs;
    }

    public long lastHeartbeatAtMs() {
        return lastHeartbeatAtMs;
    }

    public void setLastHeartbeatAtMs(long lastHeartbeatAtMs) {
        this.lastHeartbeatAtMs = lastHeartbeatAtMs;
    }

    public boolean heartbeatDue(long nowMs, long intervalMs) {
        return nowMs - lastHeartbeatAtMs >= intervalMs;
    }

    public long nextFollowIdleMovementCheckAtMs() {
        return nextFollowIdleMovementCheckAtMs;
    }

    public void setNextFollowIdleMovementCheckAtMs(long nextFollowIdleMovementCheckAtMs) {
        this.nextFollowIdleMovementCheckAtMs = nextFollowIdleMovementCheckAtMs;
    }
}
