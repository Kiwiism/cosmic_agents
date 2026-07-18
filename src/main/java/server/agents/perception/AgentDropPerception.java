package server.agents.perception;

import server.agents.model.AgentPosition;

public record AgentDropPerception(
        int objectId,
        int itemId,
        int meso,
        int ownerId,
        AgentPosition position) {
}
