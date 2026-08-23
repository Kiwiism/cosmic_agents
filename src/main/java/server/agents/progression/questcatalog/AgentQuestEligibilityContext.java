package server.agents.progression.questcatalog;

import java.util.Map;

/** Live readiness facts used to filter catalog entries without starting a quest. */
public record AgentQuestEligibilityContext(
        int level,
        int jobId,
        int estimatedHitChanceBasisPoints,
        int freeInventorySlots,
        int hpPotionCount,
        int mpPotionCount,
        Map<Integer, Integer> questStates,
        Map<Integer, Integer> itemCounts) {

    public AgentQuestEligibilityContext {
        questStates = Map.copyOf(questStates == null ? Map.of() : questStates);
        itemCounts = Map.copyOf(itemCounts == null ? Map.of() : itemCounts);
        if (level <= 0 || jobId < 0 || estimatedHitChanceBasisPoints < 0
                || estimatedHitChanceBasisPoints > 10_000 || freeInventorySlots < 0
                || hpPotionCount < 0 || mpPotionCount < 0
                || itemCounts.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey() <= 0
                        || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException("valid live quest eligibility facts are required");
        }
    }

    public AgentQuestEligibilityContext(
            int level,
            int jobId,
            int estimatedHitChanceBasisPoints,
            int freeInventorySlots,
            int hpPotionCount,
            int mpPotionCount,
            Map<Integer, Integer> questStates) {
        this(level, jobId, estimatedHitChanceBasisPoints, freeInventorySlots,
                hpPotionCount, mpPotionCount, questStates, Map.of());
    }
}
