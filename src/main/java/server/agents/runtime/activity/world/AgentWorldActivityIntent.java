package server.agents.runtime.activity.world;

/** Proposal plus a durable request reference, intentionally without a mutation handle. */
public record AgentWorldActivityIntent(
        AgentWorldActivityProposal proposal,
        AgentWorldActivityRequestType requestType,
        String requestId) {

    public AgentWorldActivityIntent {
        if (proposal == null || requestType == null) {
            throw new IllegalArgumentException("proposal and request type are required");
        }
        requestId = requestId == null ? "" : requestId.trim();
        if (requestId.isEmpty()) {
            throw new IllegalArgumentException("durable activity request id is required");
        }
    }
}
