package server.agents.field;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Per-Agent projection of its current field intent and territory. */
public final class AgentFieldAssignmentState {
    public static final AgentCapabilityStateKey<AgentFieldAssignmentState> STATE_KEY =
            new AgentCapabilityStateKey<>("field.assignment", AgentFieldAssignmentState.class,
                    AgentFieldAssignmentState::new);

    private String sessionId = "";
    private AgentFieldIntent intent;
    private AgentFieldAssignment assignment;
    private long updatedAtMs;

    public synchronized void update(
            String currentSessionId,
            AgentFieldIntent currentIntent,
            AgentFieldAssignment currentAssignment,
            long nowMs) {
        sessionId = currentSessionId == null ? "" : currentSessionId;
        intent = currentIntent;
        assignment = currentAssignment;
        updatedAtMs = Math.max(0L, nowMs);
    }

    public synchronized void clear() {
        sessionId = "";
        intent = null;
        assignment = null;
        updatedAtMs = 0L;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(sessionId, intent, assignment, updatedAtMs);
    }

    public record Snapshot(
            String sessionId,
            AgentFieldIntent intent,
            AgentFieldAssignment assignment,
            long updatedAtMs) {
    }
}
