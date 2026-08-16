package server.agents.runtime.commerce;

import server.agents.runtime.activity.session.AgentActivityPhase;

/** Restart-safe state for one independently owned Commerce visit. */
public record AgentCommerceSessionCheckpoint(
        int schemaVersion,
        AgentCommerceVisitRequest request,
        String sessionId,
        AgentActivityPhase phase,
        long startedAtMs,
        long updatedAtMs,
        long revisitAtMs,
        String reason) {
    public static final int SCHEMA_VERSION = 1;

    public AgentCommerceSessionCheckpoint {
        if (schemaVersion != SCHEMA_VERSION || request == null || phase == null
                || startedAtMs < 0L || updatedAtMs < startedAtMs || revisitAtMs < 0L) {
            throw new IllegalArgumentException("invalid Commerce session checkpoint");
        }
        sessionId = sessionId == null ? "" : sessionId.trim();
        reason = reason == null ? "" : reason.trim();
        if (phase.retainsSession() && sessionId.isEmpty()) {
            throw new IllegalArgumentException("owning Commerce checkpoint requires a session id");
        }
    }

    public boolean terminal() {
        return phase.terminal();
    }
}
