package server.agents.plans.mapleisland;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Deferred handoff from Maple Island progression into Lith Harbor TownLife. */
public final class AgentMapleIslandLithHandoffState {
    public static final AgentCapabilityStateKey<AgentMapleIslandLithHandoffState> STATE_KEY =
            new AgentCapabilityStateKey<>("plans.maple-island-lith-handoff",
                    AgentMapleIslandLithHandoffState.class,
                    AgentMapleIslandLithHandoffState::new);

    public enum Stage {
        IDLE,
        WAITING_FOR_SOUTHPERRY,
        TRANSFERRING,
        COMPLETE,
        FAILED
    }

    private Stage stage = Stage.IDLE;
    private long requestedAtMs;
    private String reason = "";

    public synchronized void request(long nowMs) {
        stage = Stage.WAITING_FOR_SOUTHPERRY;
        requestedAtMs = Math.max(0L, nowMs);
        reason = "";
    }

    public synchronized void transferring() {
        stage = Stage.TRANSFERRING;
        reason = "";
    }

    public synchronized void complete() {
        stage = Stage.COMPLETE;
        reason = "";
    }

    public synchronized void fail(String reason) {
        stage = Stage.FAILED;
        this.reason = reason == null ? "" : reason;
    }

    public synchronized boolean requested() {
        return stage == Stage.WAITING_FOR_SOUTHPERRY || stage == Stage.TRANSFERRING;
    }

    public synchronized Stage stage() {
        return stage;
    }

    public synchronized long requestedAtMs() {
        return requestedAtMs;
    }

    public synchronized String reason() {
        return reason;
    }
}
