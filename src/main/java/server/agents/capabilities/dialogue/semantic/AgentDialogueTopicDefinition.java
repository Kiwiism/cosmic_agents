package server.agents.capabilities.dialogue.semantic;

public record AgentDialogueTopicDefinition(String topicId, String description) {
    public AgentDialogueTopicDefinition {
        if (topicId == null || topicId.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Dialogue topic id and description are required");
        }
        topicId = topicId.trim().toLowerCase();
        description = description.trim();
    }
}
