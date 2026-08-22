package server.agents.social.projection;

/** Compact significant fact retained for future planner or dialogue prompts. */
public record AgentSocialContextFact(
        long occurredAtMs,
        String type,
        String summary,
        String objectiveId,
        int mapId) {
    public AgentSocialContextFact {
        if (occurredAtMs < 0 || type == null || type.isBlank() || summary == null || mapId < -1) {
            throw new IllegalArgumentException("Valid social context fact is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }
}
