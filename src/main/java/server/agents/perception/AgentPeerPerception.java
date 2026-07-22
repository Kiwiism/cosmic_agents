package server.agents.perception;

import server.agents.model.AgentPosition;

/** Immutable map-wide Agent presence used for contention and crowd policy. */
public record AgentPeerPerception(int characterId,
                                  AgentPosition position,
                                  boolean grinding,
                                  int targetObjectId) {
    public AgentPeerPerception {
        if (characterId <= 0 || position == null) throw new IllegalArgumentException("valid peer is required");
    }
}
