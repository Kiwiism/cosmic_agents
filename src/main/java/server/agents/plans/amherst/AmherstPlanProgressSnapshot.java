package server.agents.plans.amherst;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AmherstPlanProgressSnapshot(
        String planId,
        int characterId,
        long revision,
        long updatedAtMs,
        Map<String, AmherstObjectiveProgress> objectives,
        List<AmherstPlanJournalEvent> journal) {

    public AmherstPlanProgressSnapshot {
        objectives = objectives == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(objectives));
        journal = journal == null ? List.of() : List.copyOf(journal);
    }

    public static AmherstPlanProgressSnapshot empty(String planId, int characterId) {
        return new AmherstPlanProgressSnapshot(planId, characterId, 0L, 0L, Map.of(), List.of());
    }
}
