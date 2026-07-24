package server.agents.runtime.decision;

import java.util.List;

/** Immutable explanation of one policy or arbitration choice. */
public record AgentDecisionRecord(
        long sequence,
        long decidedAtMs,
        String domain,
        String choice,
        String source,
        String behaviorVersion,
        String reason,
        String correlationId,
        List<String> candidates) {

    public AgentDecisionRecord {
        if (sequence <= 0 || decidedAtMs < 0 || domain == null || domain.isBlank()
                || choice == null || choice.isBlank() || source == null || source.isBlank()
                || behaviorVersion == null || behaviorVersion.isBlank()) {
            throw new IllegalArgumentException("Complete decision provenance is required");
        }
        reason = reason == null ? "" : reason;
        correlationId = correlationId == null ? "" : correlationId;
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
    }
}
