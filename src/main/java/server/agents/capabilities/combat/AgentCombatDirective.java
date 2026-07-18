package server.agents.capabilities.combat;

import server.agents.catalog.AgentMapRegionAssignment;

import java.util.Map;
import java.util.Set;

/** Objective/map-policy input to combat; packet execution remains unchanged. */
public record AgentCombatDirective(
        String directiveId,
        String objectiveId,
        Set<Integer> requiredMobIds,
        Map<Integer, Integer> requiredKills,
        AgentIncidentalMobPolicy incidentalPolicy,
        AgentMapRegionAssignment regionAssignment,
        long deadlineMs) {

    public AgentCombatDirective {
        if (directiveId == null || directiveId.isBlank() || requiredMobIds == null
                || requiredKills == null || incidentalPolicy == null || deadlineMs < 0) {
            throw new IllegalArgumentException("Valid combat directive identity, targets, and policy are required");
        }
        objectiveId = objectiveId == null ? "" : objectiveId;
        requiredMobIds = Set.copyOf(requiredMobIds);
        requiredKills = Map.copyOf(requiredKills);
    }
}
