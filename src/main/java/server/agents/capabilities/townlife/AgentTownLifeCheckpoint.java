package server.agents.capabilities.townlife;

/** Durable local-session intent. Transient destinations and live map objects are never stored. */
public record AgentTownLifeCheckpoint(int schemaVersion,
                                      int characterId,
                                      int townMapId,
                                      AgentTownLifeVisitRequest.Purpose purpose,
                                      String reason,
                                      long remainingFreeTimeMs,
                                      long updatedAtMs) {
    public AgentTownLifeCheckpoint {
        if (schemaVersion != 1 || characterId <= 0 || townMapId <= 0 || purpose == null
                || remainingFreeTimeMs < 0L || updatedAtMs < 0L) {
            throw new IllegalArgumentException("valid TownLife checkpoint identity is required");
        }
        reason = reason == null ? "" : reason;
    }
}
