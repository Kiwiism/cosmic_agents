package server.agents.capabilities.contracts;

public record AgentSupplyNeed(
        AgentResourceCategory category,
        int currentQuantity,
        int targetQuantity,
        AgentSupplyUrgency urgency,
        String objectiveId,
        long observedAtMs) {

    public AgentSupplyNeed {
        if (category == null || currentQuantity < 0 || targetQuantity < currentQuantity
                || urgency == null || observedAtMs < 0) {
            throw new IllegalArgumentException("Valid supply category, quantities, urgency, and timestamp are required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }

    public int shortfall() {
        return targetQuantity - currentQuantity;
    }
}
