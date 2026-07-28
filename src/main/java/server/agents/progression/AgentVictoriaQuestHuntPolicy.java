package server.agents.progression;

import java.util.List;

record AgentVictoriaQuestHuntPolicy(
        int schemaVersion,
        String policyId,
        boolean shadowModeEnabled,
        boolean adaptiveFallbackEnabled,
        AgentQuestHuntSelectionMode mvpDefaultMode,
        AgentQuestHuntSelectionMode nonMvpDefaultMode,
        List<QuestPolicy> questPolicies) {

    AgentVictoriaQuestHuntPolicy {
        if (schemaVersion <= 0 || policyId == null || policyId.isBlank()
                || mvpDefaultMode == null || nonMvpDefaultMode == null) {
            throw new IllegalArgumentException("a complete quest hunt selection policy is required");
        }
        questPolicies = questPolicies == null ? List.of() : List.copyOf(questPolicies);
    }

    AgentQuestHuntSelectionMode modeFor(int questId, boolean mvpPlan) {
        return questPolicies.stream()
                .filter(policy -> policy.questId() == questId)
                .map(QuestPolicy::mode)
                .findFirst()
                .orElse(mvpPlan ? mvpDefaultMode : nonMvpDefaultMode);
    }

    record QuestPolicy(int questId, AgentQuestHuntSelectionMode mode) {
        QuestPolicy {
            if (questId <= 0 || mode == null) {
                throw new IllegalArgumentException("quest policy requires a quest and mode");
            }
        }
    }
}
