package server.agents.personality;

/** Durable personality identity independent from career and active plans. */
public record AgentPersonalityAssignment(
        int schemaVersion,
        int characterId,
        String characterName,
        String personalityProfileId,
        int personalityProfileVersion,
        long behaviorSeed,
        long assignedAtMs) {
    public AgentPersonalityAssignment {
        if (schemaVersion <= 0 || characterId <= 0 || characterName == null
                || personalityProfileId == null || personalityProfileId.isBlank()
                || personalityProfileVersion <= 0 || assignedAtMs < 0) {
            throw new IllegalArgumentException("valid durable personality assignment fields are required");
        }
        characterName = characterName.trim();
        personalityProfileId = personalityProfileId.trim();
    }
}
