package server.agents.perception;

import server.agents.model.AgentPosition;

public record AgentMobPerception(
        int objectId,
        int mobId,
        AgentPosition position,
        long hp,
        boolean alive) {
}
