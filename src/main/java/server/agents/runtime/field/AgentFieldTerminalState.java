package server.agents.runtime.field;

import server.agents.field.events.AgentFieldLifecycleEvent;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Last terminal field outcome for plan reattachment and diagnostics. */
public final class AgentFieldTerminalState {
    public static final AgentCapabilityStateKey<AgentFieldTerminalState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.field-terminal",
                    AgentFieldTerminalState.class, AgentFieldTerminalState::new);
    private String sessionId = "";
    private AgentFieldLifecycleEvent.Phase phase;
    private String reason = "";
    private long occurredAtMs;

    public synchronized void record(String id, AgentFieldLifecycleEvent.Phase next,
                                    String detail, long nowMs) {
        sessionId = id == null ? "" : id;
        phase = next;
        reason = detail == null ? "" : detail;
        occurredAtMs = Math.max(0L, nowMs);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(sessionId, phase, reason, occurredAtMs);
    }

    public record Snapshot(String sessionId, AgentFieldLifecycleEvent.Phase phase,
                           String reason, long occurredAtMs) {
        public boolean matches(String id) { return sessionId.equals(id); }
    }
}
