package server.agents.capabilities.combat;

import server.life.Monster;

public final class AgentGrindTargetState {
    private Monster target = null;
    private long targetCommittedUntilMs = 0L;
    private int targetSwitchCount = 0;
    private long nextSearchAtMs = 0L;

    public Monster target() {
        return target;
    }

    public void setTarget(Monster target) {
        if (this.target != target) {
            targetCommittedUntilMs = 0L;
        }
        this.target = target;
    }

    public void commitTarget(Monster target, long committedUntilMs) {
        if (this.target != target) {
            if (this.target != null && target != null) {
                targetSwitchCount = this.target.isAlive() ? targetSwitchCount + 1 : 0;
            }
            this.target = target;
            this.targetCommittedUntilMs = committedUntilMs;
        }
    }

    public int targetSwitchCount() {
        return targetSwitchCount;
    }

    public boolean committedTo(Monster target, long nowMs) {
        return this.target == target && target != null && nowMs < targetCommittedUntilMs;
    }

    public void clearTarget() {
        target = null;
        targetCommittedUntilMs = 0L;
        targetSwitchCount = 0;
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
