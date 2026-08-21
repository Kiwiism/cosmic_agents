package server.agents.progression.questwork;

import java.util.Map;

/** Authoritative game facts captured immediately before work-unit reconciliation. */
public record AgentQuestLiveState(
        int characterId,
        int level,
        int mapId,
        int questState,
        Map<Integer, Integer> itemCounts,
        Map<String, Integer> objectiveCounts) {

    public AgentQuestLiveState {
        itemCounts = Map.copyOf(itemCounts == null ? Map.of() : itemCounts);
        objectiveCounts = Map.copyOf(objectiveCounts == null ? Map.of() : objectiveCounts);
        if (characterId <= 0 || level <= 0 || mapId <= 0 || questState < 0
                || itemCounts.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey() <= 0
                        || entry.getValue() == null || entry.getValue() < 0)
                || objectiveCounts.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey().isBlank()
                        || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("valid authoritative quest live state is required");
        }
    }
}
