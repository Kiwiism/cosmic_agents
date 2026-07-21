package server.agents.coordination;

/** Routing and lifetime metadata kept separate from the technical message payload. */
public record AgentCoordinationEnvelope(
        long messageId,
        String correlationId,
        AgentCoordinationScope scope,
        long routeId,
        int targetAgentCharacterId,
        long createdAtMillis,
        long expiresAtMillis,
        boolean acknowledgementRequired,
        AgentCoordinationMessage message) {

    public AgentCoordinationEnvelope {
        if (messageId <= 0 || correlationId == null || correlationId.isBlank()
                || scope == null || routeId <= 0 || targetAgentCharacterId < 0
                || createdAtMillis < 0 || expiresAtMillis <= createdAtMillis || message == null) {
            throw new IllegalArgumentException("Valid coordination envelope context is required");
        }
        if (scope == AgentCoordinationScope.AGENT && targetAgentCharacterId <= 0) {
            throw new IllegalArgumentException("Agent-routed messages require a target Agent");
        }
    }

    public boolean expired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }
}
