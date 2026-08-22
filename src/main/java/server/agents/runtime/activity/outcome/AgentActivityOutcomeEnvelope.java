package server.agents.runtime.activity.outcome;

import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;

/** Durable exactly-once acknowledgement around a system terminal outcome. */
public record AgentActivityOutcomeEnvelope(
        int schemaVersion,
        String outcomeId,
        AgentActivityTerminalOutcome outcome,
        boolean acknowledged,
        long publishedAtMs,
        long acknowledgedAtMs,
        String acknowledgement) {

    public AgentActivityOutcomeEnvelope {
        outcomeId = outcomeId == null ? "" : outcomeId.trim();
        acknowledgement = acknowledgement == null ? "" : acknowledgement.trim();
        if (schemaVersion != 1 || outcomeId.isEmpty() || outcome == null
                || publishedAtMs < 0L || acknowledgedAtMs < 0L
                || (!acknowledged && acknowledgedAtMs > 0L)) {
            throw new IllegalArgumentException("valid activity outcome envelope is required");
        }
    }

    public static AgentActivityOutcomeEnvelope published(
            String outcomeId, AgentActivityTerminalOutcome outcome, long nowMs) {
        return new AgentActivityOutcomeEnvelope(
                1, outcomeId, outcome, false, nowMs, 0L, "");
    }

    public AgentActivityOutcomeEnvelope acknowledge(String reason, long nowMs) {
        if (acknowledged) return this;
        return new AgentActivityOutcomeEnvelope(schemaVersion, outcomeId, outcome,
                true, publishedAtMs, nowMs, reason);
    }
}
