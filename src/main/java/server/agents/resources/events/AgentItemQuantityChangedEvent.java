package server.agents.resources.events;

import server.agents.events.AgentEvent;

/** Inventory quantity transition after an Agent-owned inventory mutation. */
public record AgentItemQuantityChangedEvent(
        int agentId,
        long occurredAtMs,
        int itemId,
        int previousQuantity,
        int quantity,
        String inventoryType,
        String source,
        String objectiveId) implements AgentEvent {
    public static final String TYPE = "inventory.item-quantity-changed";

    public AgentItemQuantityChangedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || itemId <= 0 || previousQuantity < 0
                || quantity < 0 || previousQuantity == quantity || inventoryType == null
                || inventoryType.isBlank() || source == null || source.isBlank()) {
            throw new IllegalArgumentException("Valid item quantity transition is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
