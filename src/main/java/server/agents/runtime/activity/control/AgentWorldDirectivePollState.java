package server.agents.runtime.activity.control;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Bounded scheduler cadence and last failure for durable directive polling. */
public final class AgentWorldDirectivePollState {
    public static final AgentCapabilityStateKey<AgentWorldDirectivePollState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.world-director-poll",
                    AgentWorldDirectivePollState.class, AgentWorldDirectivePollState::new);

    private long lastPollAtMs;
    private String lastResult = "";

    public synchronized boolean claim(long nowMs, long intervalMs) {
        if (intervalMs <= 0L || nowMs < 0L
                || lastPollAtMs > 0L && nowMs - lastPollAtMs < intervalMs) return false;
        lastPollAtMs = nowMs;
        return true;
    }

    public synchronized void result(String value) {
        lastResult = value == null ? "" : value.trim();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(lastPollAtMs, lastResult);
    }

    public record Snapshot(long lastPollAtMs, String lastResult) { }
}
