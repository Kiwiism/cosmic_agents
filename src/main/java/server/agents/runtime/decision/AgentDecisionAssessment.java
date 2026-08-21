package server.agents.runtime.decision;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.List;

/** Complete immutable input for one advisory evaluation. */
public record AgentDecisionAssessment(
        String agentId,
        AgentActivityKind currentActivity,
        long evaluatedAtMs,
        String correlationId,
        List<AgentDecisionSignal> signals) {

    public AgentDecisionAssessment {
        agentId = text(agentId);
        correlationId = text(correlationId);
        signals = List.copyOf(signals == null ? List.of() : signals);
        if (agentId.isEmpty() || correlationId.isEmpty() || evaluatedAtMs < 0L
                || signals.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("complete advisory assessment identity and evidence are required");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
