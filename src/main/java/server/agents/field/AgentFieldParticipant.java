package server.agents.field;

import java.awt.Point;
import java.util.Set;

/** Immutable planner input for one live field participant. */
public record AgentFieldParticipant(
        int agentId,
        int partyId,
        Point position,
        AgentFieldIntent intent,
        Set<String> previousCellIds,
        long previousLeaseExpiresAtMs,
        long joinedAtMs) {

    public AgentFieldParticipant {
        if (agentId <= 0 || position == null || intent == null || previousCellIds == null
                || previousLeaseExpiresAtMs < 0 || joinedAtMs < 0) {
            throw new IllegalArgumentException("Valid field participant identity, position, and intent are required");
        }
        position = new Point(position);
        previousCellIds = Set.copyOf(previousCellIds);
    }

    @Override
    public Point position() {
        return new Point(position);
    }
}
