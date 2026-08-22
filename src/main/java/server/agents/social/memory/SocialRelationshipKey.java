package server.agents.social.memory;

/** Directional relationship identity using stable server-side character ids. */
public record SocialRelationshipKey(
        int agentId,
        SocialCounterpartyType targetType,
        int targetId) {
    public SocialRelationshipKey {
        if (agentId <= 0 || targetType == null || targetId <= 0 || agentId == targetId) {
            throw new IllegalArgumentException("Valid directional social relationship key is required");
        }
    }
}
