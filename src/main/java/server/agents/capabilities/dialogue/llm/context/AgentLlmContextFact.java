package server.agents.capabilities.dialogue.llm.context;

/** Compact significant fact retained for future planner or dialogue prompts. */
public record AgentLlmContextFact(
        long occurredAtMs,
        String type,
        String summary,
        String objectiveId,
        int mapId) {
    public AgentLlmContextFact {
        if (occurredAtMs < 0 || type == null || type.isBlank() || summary == null || mapId < -1) {
            throw new IllegalArgumentException("Valid LLM context fact is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }
}
