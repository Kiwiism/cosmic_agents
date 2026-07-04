package server.agents.runtime;

import java.util.concurrent.ThreadLocalRandom;

public final class AgentTickState {
    private boolean lastTickWasAi = false;
    private long lastTickAtMs = 0L;
    private long lastHeartbeatAtMs = 0L;
    private long nextFollowIdleMovementCheckAtMs = 0L;
    private int skipDelayMs = ThreadLocalRandom.current().nextInt(0, 501);
    private int aiTickAccumulatorMs = 0;

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

    public int skipDelayMs() {
        return skipDelayMs;
    }

    public void setSkipDelayMs(int skipDelayMs) {
        this.skipDelayMs = skipDelayMs;
    }

    public int aiTickAccumulatorMs() {
        return aiTickAccumulatorMs;
    }

    public void setAiTickAccumulatorMs(int aiTickAccumulatorMs) {
        this.aiTickAccumulatorMs = aiTickAccumulatorMs;
    }

    public void resetCadence() {
        skipDelayMs = 0;
        aiTickAccumulatorMs = 0;
    }
}
