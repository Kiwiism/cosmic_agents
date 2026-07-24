package server.agents.perception;

import server.agents.model.AgentPosition;

/** Immutable character presence used by local crowd-pressure policies. */
public record AgentCharacterPerception(
        int characterId,
        AgentPosition position,
        boolean agent) {

    public AgentCharacterPerception {
        if (characterId <= 0 || position == null) {
            throw new IllegalArgumentException("valid perceived character is required");
        }
    }
}
