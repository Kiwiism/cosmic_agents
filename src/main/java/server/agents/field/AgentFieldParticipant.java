package server.agents.field;

import java.awt.Point;
import java.util.Set;

/** Immutable planner input for one live field participant. */
public record AgentFieldParticipant(
        int agentId,
        int partyId,
        Point position,
        AgentFieldIntent intent,
        AgentFieldCombatProfile combatProfile,
        Set<String> previousCellIds,
        long previousLeaseExpiresAtMs,
        long joinedAtMs) {

    public AgentFieldParticipant {
        if (agentId <= 0 || position == null || intent == null || combatProfile == null || previousCellIds == null
                || previousLeaseExpiresAtMs < 0 || joinedAtMs < 0) {
            throw new IllegalArgumentException("Valid field participant identity, position, and intent are required");
        }
        position = new Point(position);
        previousCellIds = Set.copyOf(previousCellIds);
    }

    public AgentFieldParticipant(
            int agentId,
            int partyId,
            Point position,
            AgentFieldIntent intent,
            Set<String> previousCellIds,
            long previousLeaseExpiresAtMs,
            long joinedAtMs) {
        this(agentId, partyId, position, intent, AgentFieldCombatProfile.roamer(),
                previousCellIds, previousLeaseExpiresAtMs, joinedAtMs);
    }

    @Override
    public Point position() {
        return new Point(position);
    }
}
