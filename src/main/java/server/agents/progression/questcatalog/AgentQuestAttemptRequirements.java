package server.agents.progression.questcatalog;

/** Authored readiness guidance layered over generated quest facts. */
public record AgentQuestAttemptRequirements(
        int minimumHitChanceBasisPoints,
        int minimumFreeInventorySlots,
        int minimumHpPotionReserve,
        int minimumMpPotionReserve) {

    public AgentQuestAttemptRequirements {
        if (minimumHitChanceBasisPoints < 0 || minimumHitChanceBasisPoints > 10_000
                || minimumFreeInventorySlots < 0
                || minimumHpPotionReserve < 0 || minimumMpPotionReserve < 0) {
            throw new IllegalArgumentException("quest attempt requirements cannot be negative or exceed 100% hit chance");
        }
    }
}
