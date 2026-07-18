package server.agents.catalog;

import server.agents.model.AgentPosition;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Curated overlay referencing generated geometry rather than duplicating it. */
public record AgentMapStrategy(
        int mapId,
        int schemaVersion,
        String geometryVersion,
        int recommendedAgents,
        int maximumAgents,
        Map<String, List<AgentPosition>> farmingAnchors,
        Map<Integer, String> mobRoles,
        Map<Integer, List<List<String>>> partyLayouts,
        Set<String> hazards) {

    public AgentMapStrategy {
        if (mapId < 0 || schemaVersion <= 0 || geometryVersion == null || geometryVersion.isBlank()
                || recommendedAgents <= 0 || maximumAgents < recommendedAgents
                || farmingAnchors == null || mobRoles == null || partyLayouts == null || hazards == null) {
            throw new IllegalArgumentException("Valid map strategy identity, capacity, and overlays are required");
        }
        farmingAnchors = farmingAnchors.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        mobRoles = Map.copyOf(mobRoles);
        partyLayouts = partyLayouts.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream().map(List::copyOf).toList()));
        hazards = Set.copyOf(hazards);
    }
}
