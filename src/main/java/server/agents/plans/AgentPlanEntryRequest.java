package server.agents.plans;

import java.util.Map;

/** Caller-owned request for one top-level quest/plan run. */
public record AgentPlanEntryRequest(
        String requestId,
        String callerId,
        String planId,
        Map<String, Object> inputs,
        Object transientAttachment) {
    public AgentPlanEntryRequest {
        requestId = required(requestId, "plan request id");
        callerId = required(callerId, "plan caller id");
        planId = required(planId, "plan id");
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }

    public AgentPlanStartRequest startRequest() {
        return new AgentPlanStartRequest(inputs, transientAttachment);
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }
}
