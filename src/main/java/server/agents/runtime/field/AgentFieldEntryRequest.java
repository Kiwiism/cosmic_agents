package server.agents.runtime.field;

/** External identity and policy for one exact field visit. */
public record AgentFieldEntryRequest(
        String requestId,
        String callerId,
        AgentFieldVisitRequest visit) {
    public AgentFieldEntryRequest {
        requestId = required(requestId, "field request id");
        callerId = required(callerId, "field caller id");
        if (visit == null) {
            throw new IllegalArgumentException("field visit request is required");
        }
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }
}
