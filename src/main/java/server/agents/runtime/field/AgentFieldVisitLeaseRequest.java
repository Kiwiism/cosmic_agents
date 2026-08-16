package server.agents.runtime.field;

/** External schedule around one exact field visit. */
public record AgentFieldVisitLeaseRequest(
        AgentFieldEntryRequest entryRequest,
        AgentFieldAdmissionMode admissionMode,
        long exitAtMs,
        long gracefulTimeoutMs,
        String exitReason) {
    public AgentFieldVisitLeaseRequest {
        exitReason = exitReason == null ? "" : exitReason.trim();
        if (entryRequest == null || admissionMode == null || exitAtMs <= 0L
                || gracefulTimeoutMs <= 0L) {
            throw new IllegalArgumentException("valid field visit lease is required");
        }
    }
}
