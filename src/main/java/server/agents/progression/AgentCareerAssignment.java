package server.agents.progression;

public record AgentCareerAssignment(
        int schemaVersion,
        int characterId,
        String characterName,
        String bundleId,
        int bundleVersion,
        long assignedAtMs) {

    public AgentCareerAssignment {
        if (schemaVersion <= 0 || characterId <= 0 || characterName == null
                || bundleId == null || bundleId.isBlank() || bundleVersion <= 0 || assignedAtMs < 0) {
            throw new IllegalArgumentException("valid durable career assignment fields are required");
        }
        characterName = characterName.trim();
    }
}
