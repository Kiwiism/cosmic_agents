package server.agents.resources.events;

import server.agents.events.AgentContextualEvent;

/** Inventory capacity threshold observed while considering or collecting loot. */
public record AgentInventoryThresholdChangedEvent(
        int agentId,
        long occurredAtMs,
        String inventoryType,
        int freeSlots,
        int slotLimit,
        String threshold,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "inventory.threshold-changed";

    public AgentInventoryThresholdChangedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || inventoryType == null || inventoryType.isBlank()
                || freeSlots < 0 || slotLimit < freeSlots || threshold == null || threshold.isBlank()) {
            throw new IllegalArgumentException("Valid inventory threshold context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return inventoryType + ":" + threshold;
    }
}
