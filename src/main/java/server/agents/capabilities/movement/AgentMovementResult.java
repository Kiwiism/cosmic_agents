package server.agents.capabilities.movement;

import server.agents.model.AgentPosition;

public record AgentMovementResult(
        String requestId,
        AgentMovementOutcome outcome,
        AgentPosition position,
        String reason,
        long occurredAtMs) {

    public AgentMovementResult {
        if (requestId == null || requestId.isBlank() || outcome == null || position == null
                || occurredAtMs < 0) {
            throw new IllegalArgumentException("Valid movement result identity, outcome, and position are required");
        }
        reason = reason == null ? "" : reason;
    }
}
