package server.agents.capabilities.supplies;

import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.events.AgentContextualEvent;

/** Immutable supply fact captured when an Agent crosses a configured threshold. */
public record AgentSupplyThresholdChangedEvent(
        int agentId,
        long occurredAtMs,
        long cohortId,
        int mapId,
        AgentResourceCategory category,
        int currentQuantity,
        int targetQuantity,
        AgentSupplyUrgency previousUrgency,
        AgentSupplyUrgency urgency,
        String objectiveId) implements AgentContextualEvent {
    public static final String TYPE = "supply.threshold-changed";

    public AgentSupplyThresholdChangedEvent {
        if (agentId <= 0 || occurredAtMs < 0 || cohortId < 0 || mapId < -1
                || category == null || currentQuantity < 0 || targetQuantity < currentQuantity
                || urgency == null) {
            throw new IllegalArgumentException("Valid supply threshold context is required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String dedupeKey() {
        return category + ":" + urgency;
    }
}
