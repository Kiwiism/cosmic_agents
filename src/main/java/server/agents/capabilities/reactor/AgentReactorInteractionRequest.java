package server.agents.capabilities.reactor;

import java.awt.Point;

public record AgentReactorInteractionRequest(
        int mapId,
        int questId,
        AgentReactorInteractionMode mode,
        Integer reactorId,
        String reactorName,
        Integer objectId,
        Point agentPosition,
        int maxRangePx) {

    public AgentReactorInteractionRequest {
        if (mode == null) {
            mode = AgentReactorInteractionMode.HIT;
        }
        if (agentPosition != null) {
            agentPosition = new Point(agentPosition);
        }
    }

    public boolean hasObjectIdFilter() {
        return objectId != null && objectId > 0;
    }

    public boolean hasReactorIdFilter() {
        return reactorId != null && reactorId > 0;
    }

    public boolean hasNameFilter() {
        return reactorName != null && !reactorName.isBlank();
    }

    public boolean hasRangeFilter() {
        return agentPosition != null && maxRangePx >= 0;
    }
}
