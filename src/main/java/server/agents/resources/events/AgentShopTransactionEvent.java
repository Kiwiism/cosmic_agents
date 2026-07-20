package server.agents.resources.events;

import server.agents.events.AgentEvent;

/** Committed or rejected NPC-shop operation. */
public record AgentShopTransactionEvent(
        int agentId,
        long occurredAtMs,
        int npcId,
        String operation,
        int itemId,
        int quantity,
        int mesoDelta,
        String result,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "shopping.transaction-resolved";

    public AgentShopTransactionEvent {
        if (agentId <= 0 || occurredAtMs < 0 || npcId <= 0 || operation == null
                || operation.isBlank() || itemId <= 0 || quantity < 0 || result == null
                || result.isBlank()) {
            throw new IllegalArgumentException("Valid shop transaction context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
