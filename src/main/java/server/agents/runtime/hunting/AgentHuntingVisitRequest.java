package server.agents.runtime.hunting;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.LinkedHashSet;
import java.util.Set;

/** Typed child request from another activity into Hunting's combat capability. */
public record AgentHuntingVisitRequest(
        String visitId,
        AgentActivityKind callerKind,
        Purpose purpose,
        int mapId,
        Set<Integer> preferredMobIds,
        Set<Integer> incidentalMobIds) {
    public AgentHuntingVisitRequest {
        visitId = visitId == null ? "" : visitId.trim();
        preferredMobIds = preferredMobIds == null ? Set.of() : Set.copyOf(preferredMobIds);
        incidentalMobIds = incidentalMobIds == null ? Set.of() : Set.copyOf(incidentalMobIds);
        if (visitId.isEmpty() || callerKind == null || purpose == null || mapId <= 0
                || preferredMobIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "a Hunting visit requires caller, map, and preferred targets");
        }
        LinkedHashSet<Integer> overlap = new LinkedHashSet<>(preferredMobIds);
        overlap.retainAll(incidentalMobIds);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException(
                    "preferred and incidental Hunting targets must be distinct");
        }
    }

    public enum Purpose {
        QUEST_OBJECTIVE,
        LEVEL_TRAINING,
        ROUTE_HARVEST
    }
}
