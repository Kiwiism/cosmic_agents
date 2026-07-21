package server.agents.coordination;

/** Result of accepting or rejecting a message at the bounded routing boundary. */
public record AgentCoordinationPublishResult(
        boolean accepted,
        AgentCoordinationEnvelope envelope,
        String reason) {

    public AgentCoordinationPublishResult {
        if (envelope == null) {
            throw new IllegalArgumentException("Coordination envelope is required");
        }
        reason = reason == null ? "" : reason;
    }
}
