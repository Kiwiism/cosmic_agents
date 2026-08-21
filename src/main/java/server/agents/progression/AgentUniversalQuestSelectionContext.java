package server.agents.progression;

import server.agents.progression.questcatalog.AgentQuestEligibilityContext;

import java.util.Map;
import java.util.Set;

/** Live, read-only inputs for ranking independently selectable quests. */
public record AgentUniversalQuestSelectionContext(
        int characterId,
        int currentMapId,
        AgentQuestEligibilityContext eligibility,
        AgentProgressionProfile profile,
        Map<Integer, Integer> routeHopsByMapId,
        Set<Integer> suppressedQuestIds) {

    public AgentUniversalQuestSelectionContext {
        routeHopsByMapId = Map.copyOf(routeHopsByMapId == null ? Map.of() : routeHopsByMapId);
        suppressedQuestIds = Set.copyOf(suppressedQuestIds == null ? Set.of() : suppressedQuestIds);
        if (characterId <= 0 || currentMapId <= 0 || eligibility == null || profile == null
                || routeHopsByMapId.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey() <= 0
                        || entry.getValue() == null || entry.getValue() < 0)
                || suppressedQuestIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("valid live universal quest selection context is required");
        }
    }
}
