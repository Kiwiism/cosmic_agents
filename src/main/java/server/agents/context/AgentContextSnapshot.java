package server.agents.context;

/** Immutable cross-system context; it never carries mutable Cosmic objects. */
public record AgentContextSnapshot(
        int characterId,
        String characterName,
        String personalityProfileId,
        int personalityProfileVersion,
        long behaviorSeed,
        boolean personalityPresentationEnabled,
        int interactionTargetCharacterId,
        long cohortId,
        long formationId) {
    public AgentContextSnapshot {
        characterName = characterName == null ? "" : characterName;
        personalityProfileId = personalityProfileId == null ? "" : personalityProfileId;
    }

    public static AgentContextSnapshot empty() {
        return new AgentContextSnapshot(0, "", "", 0, 0L, false, 0, 0L, 0L);
    }
}
