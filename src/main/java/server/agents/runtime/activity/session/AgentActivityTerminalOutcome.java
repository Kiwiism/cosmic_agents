package server.agents.runtime.activity.session;

import java.util.Map;

/** Stable result shape exposed by every high-level activity owner. */
public record AgentActivityTerminalOutcome(
        AgentActivityKind kind,
        AgentActivityPhase phase,
        String sessionId,
        String agentId,
        String reason,
        boolean retryable,
        long startedAtMs,
        long endedAtMs,
        Map<String, Object> evidence) {
    public AgentActivityTerminalOutcome {
        if (kind == null || phase == null || !phase.terminal()
                || startedAtMs < 0L || endedAtMs < startedAtMs) {
            throw new IllegalArgumentException("a terminal activity outcome with valid timing is required");
        }
        sessionId = normalize(sessionId);
        agentId = normalize(agentId);
        reason = normalize(reason);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
