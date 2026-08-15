package server.agents.capabilities.townlife;

/** Caller-owned identity around a local TownLife visit request. */
public record AgentTownLifeEntryRequest(
        String requestId,
        String callerId,
        AgentTownLifeVisitRequest visit) {

    public AgentTownLifeEntryRequest {
        requestId = normalizeRequired(requestId, "TownLife request id");
        callerId = normalizeRequired(callerId, "TownLife caller id");
        if (visit == null) {
            throw new IllegalArgumentException("TownLife visit request is required");
        }
    }

    public static AgentTownLifeEntryRequest external(
            String requestId, String callerId, AgentTownLifeVisitRequest visit) {
        return new AgentTownLifeEntryRequest(requestId, callerId, visit);
    }

    static AgentTownLifeEntryRequest legacy(
            int agentId, long nowMs, AgentTownLifeVisitRequest visit) {
        return new AgentTownLifeEntryRequest(
                "legacy-" + Math.max(0, agentId) + '-' + Math.max(0L, nowMs),
                "legacy-runtime",
                visit);
    }

    private static String normalizeRequired(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }
}
