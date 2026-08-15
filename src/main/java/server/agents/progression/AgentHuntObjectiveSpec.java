package server.agents.progression;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable, catalog-compiled hunt intent consumed by runtime selection. */
record AgentHuntObjectiveSpec(
        String selectionId,
        List<AgentHuntSelectionRequest.ObjectiveDemand> objectives,
        List<AgentVictoriaQuestRuntimeCatalog.HuntMap> preferredMaps,
        boolean mvpPlan) {

    AgentHuntObjectiveSpec {
        if (selectionId == null || selectionId.isBlank()
                || objectives == null || objectives.isEmpty()) {
            throw new IllegalArgumentException("a hunt objective requires an id and demands");
        }
        objectives = List.copyOf(objectives);
        preferredMaps = preferredMaps == null ? List.of() : List.copyOf(preferredMaps);
        Set<Integer> mapIds = new HashSet<>();
        for (AgentVictoriaQuestRuntimeCatalog.HuntMap map : preferredMaps) {
            if (map == null || map.mapId() <= 0 || !mapIds.add(map.mapId())) {
                throw new IllegalArgumentException(
                        "preferred hunt maps must be positive and unique");
            }
        }
    }
}
