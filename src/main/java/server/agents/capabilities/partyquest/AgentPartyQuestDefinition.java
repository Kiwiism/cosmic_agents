package server.agents.capabilities.partyquest;

/** Immutable server-content contract used by shared PQ routing and recovery. */
public record AgentPartyQuestDefinition(
        String questKey,
        String eventManagerName,
        int recruitMapId,
        int entryMapId,
        int clearMapId,
        int exitMapId,
        int recoveryMapId,
        int minimumLevel,
        int maximumLevel,
        int minimumPartySize,
        int maximumPartySize) {

    public AgentPartyQuestDefinition {
        questKey = normalize(questKey);
        if (eventManagerName == null || eventManagerName.isBlank()
                || recruitMapId <= 0 || entryMapId <= 0 || clearMapId <= 0
                || exitMapId <= 0 || recoveryMapId <= 0
                || minimumLevel < 1 || maximumLevel < minimumLevel
                || minimumPartySize < 2 || maximumPartySize < minimumPartySize) {
            throw new IllegalArgumentException("valid party-quest definition values are required");
        }
        eventManagerName = eventManagerName.trim();
    }

    public boolean acceptsPartySize(int partySize) {
        return partySize >= minimumPartySize && partySize <= maximumPartySize;
    }

    public boolean acceptsLevel(int level) {
        return level >= minimumLevel && level <= maximumLevel;
    }

    public static String normalize(String questKey) {
        String normalized = questKey == null ? "" : questKey.trim().toLowerCase();
        if (normalized.isEmpty()) throw new IllegalArgumentException("party-quest key is required");
        return normalized;
    }
}
