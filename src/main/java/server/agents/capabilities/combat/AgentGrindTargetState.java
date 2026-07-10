package server.agents.capabilities.combat;

import server.life.Monster;

public final class AgentGrindTargetState {
    private Monster target = null;
    private long nextSearchAtMs = 0L;

    public Monster target() {
        return target;
    }

    public void setTarget(Monster target) {
        this.target = target;
    }

    public void clearTarget() {
        target = null;
    }

    public long nextSearchAtMs() {
        return nextSearchAtMs;
    }

    public void setNextSearchAtMs(long nextSearchAtMs) {
        this.nextSearchAtMs = nextSearchAtMs;
    }

    public void clearNextSearchAtMs() {
        nextSearchAtMs = 0L;
    }
}
