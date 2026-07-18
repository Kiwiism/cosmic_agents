package server.agents.capabilities.contracts;

import java.util.List;

public record AgentProcurementRequest(
        String requestId,
        AgentResourceCategory category,
        int quantity,
        long maximumBudget,
        List<AgentProcurementMethod> permittedMethods,
        AgentSupplyUrgency urgency,
        String objectiveId,
        long expiresAtMs) {

    public AgentProcurementRequest {
        if (requestId == null || requestId.isBlank() || category == null || quantity <= 0
                || maximumBudget < 0 || permittedMethods == null || permittedMethods.isEmpty()
                || urgency == null || expiresAtMs < 0) {
            throw new IllegalArgumentException("Valid procurement identity, bounds, and methods are required");
        }
        permittedMethods = List.copyOf(permittedMethods);
        objectiveId = objectiveId == null ? "" : objectiveId;
    }
}
