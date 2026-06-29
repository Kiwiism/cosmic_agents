package server.agents.capabilities.combat;

public final class AgentAttackPlanTieBreakPolicy {
    private AgentAttackPlanTieBreakPolicy() {
    }

    public static boolean isBetter(int candidateCooldownMs, int candidateSkillId,
                                   int currentCooldownMs, int currentSkillId) {
        if (candidateCooldownMs != currentCooldownMs) {
            return candidateCooldownMs < currentCooldownMs;
        }
        return candidateSkillId < currentSkillId;
    }
}
