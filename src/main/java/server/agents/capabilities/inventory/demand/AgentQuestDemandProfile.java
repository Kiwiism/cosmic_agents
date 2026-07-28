package server.agents.capabilities.inventory.demand;

import java.util.Map;
import java.util.Set;

/** Bounded character state used by deterministic policy and future LLM read models. */
public record AgentQuestDemandProfile(
        int level,
        int jobId,
        Set<Integer> plannedJobIds,
        Map<Integer, Integer> questStatuses,
        Set<Integer> committedQuestIds,
        Map<Integer, Integer> ownedItemCounts) {

    public AgentQuestDemandProfile {
        if (level <= 0 || jobId < 0) {
            throw new IllegalArgumentException("valid level and job are required");
        }
        plannedJobIds = plannedJobIds == null ? Set.of() : Set.copyOf(plannedJobIds);
        questStatuses = questStatuses == null ? Map.of() : Map.copyOf(questStatuses);
        committedQuestIds = committedQuestIds == null ? Set.of() : Set.copyOf(committedQuestIds);
        ownedItemCounts = ownedItemCounts == null ? Map.of() : Map.copyOf(ownedItemCounts);
    }

    public int questStatus(int questId) {
        return questStatuses.getOrDefault(questId, 0);
    }
}
