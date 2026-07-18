package server.agents.capabilities.navigation;

import server.agents.model.AgentPosition;

public record AgentNavigationRequest(
        String requestId,
        int destinationMapId,
        AgentPosition destination,
        String objectiveId,
        long deadlineMs) {

    public AgentNavigationRequest {
        if (requestId == null || requestId.isBlank() || destinationMapId < 0
                || destination == null || deadlineMs < 0) {
            throw new IllegalArgumentException("Valid navigation request identity and destination are required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
    }
}
