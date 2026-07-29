package server.agents.capabilities.movement;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Session-local cast and airborne state for Agent movement skills. */
public final class AgentMovementSkillState {
    public static final AgentCapabilityStateKey<AgentMovementSkillState> STATE_KEY =
            new AgentCapabilityStateKey<>("movement.skills", AgentMovementSkillState.class,
                    AgentMovementSkillState::new);

    private long nextCastAtMs;
    private boolean flashJumpPending;
    private boolean flashJumpFired;
    private long lastShadowLogAtMs;

    public long nextCastAtMs() {
        return nextCastAtMs;
    }

    public void setNextCastAtMs(long nextCastAtMs) {
        this.nextCastAtMs = Math.max(0L, nextCastAtMs);
    }

    public boolean flashJumpPending() {
        return flashJumpPending;
    }

    public void setFlashJumpPending(boolean flashJumpPending) {
        this.flashJumpPending = flashJumpPending;
    }

    public boolean flashJumpFired() {
        return flashJumpFired;
    }

    public void setFlashJumpFired(boolean flashJumpFired) {
        this.flashJumpFired = flashJumpFired;
    }

    public long lastShadowLogAtMs() {
        return lastShadowLogAtMs;
    }

    public void setLastShadowLogAtMs(long lastShadowLogAtMs) {
        this.lastShadowLogAtMs = Math.max(0L, lastShadowLogAtMs);
    }

    public void clearAirborneCast() {
        flashJumpPending = false;
        flashJumpFired = false;
    }
}
