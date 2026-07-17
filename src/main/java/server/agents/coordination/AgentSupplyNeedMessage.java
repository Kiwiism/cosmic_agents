package server.agents.coordination;

/** Structured supply context avoids parsing Maple chat between Agents. */
public record AgentSupplyNeedMessage(int sourceAgentCharacterId,
                                     long cohortId,
                                     int mapId,
                                     SupplyKind kind,
                                     int currentCount,
                                     String equipmentContext,
                                     long createdAtMillis) implements AgentCoordinationMessage {
    public enum SupplyKind {
        HP_POTION,
        MP_POTION,
        AMMUNITION
    }
}
