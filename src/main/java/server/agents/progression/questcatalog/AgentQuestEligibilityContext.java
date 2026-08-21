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
        Map<Integer, Integer> questStates) {

    public AgentQuestEligibilityContext {
        questStates = Map.copyOf(questStates == null ? Map.of() : questStates);
        if (level <= 0 || jobId < 0 || estimatedHitChanceBasisPoints < 0
                || estimatedHitChanceBasisPoints > 10_000 || freeInventorySlots < 0
                || hpPotionCount < 0 || mpPotionCount < 0) {
            throw new IllegalArgumentException("valid live quest eligibility facts are required");
        }
    }
}
