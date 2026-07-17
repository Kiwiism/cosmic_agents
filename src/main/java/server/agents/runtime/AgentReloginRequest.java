package server.agents.runtime;

import java.awt.Point;

/** Immutable session placement and relationship context used for owner-free relogin. */
public record AgentReloginRequest(int agentCharacterId,
                                  int cohortId,
                                  long formationId,
                                  int followTargetCharacterId,
                                  int interactionTargetCharacterId,
                                  int world,
                                  int channel,
                                  int mapId,
                                  Point position) {
    public AgentReloginRequest {
        position = position == null ? new Point() : new Point(position);
    }

    @Override
    public Point position() {
        return new Point(position);
    }
}
