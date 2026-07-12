package server.agents.plans.amherst;

import java.util.List;
import java.util.Set;

public record AmherstPlanCard(
        int schemaVersion,
        String planId,
        String title,
        String category,
        String priority,
        String status,
        String objectiveMode,
        FocusPolicy focusPolicy,
        EntryCriteria entryCriteria,
        ExitCriteria exitCriteria,
        Set<Integer> requiredQuestIds,
        Set<Integer> excludedQuestIds,
        List<AmherstPlanObjective> objectives) {

    public AmherstPlanCard {
        requiredQuestIds = requiredQuestIds == null ? Set.of() : Set.copyOf(requiredQuestIds);
        excludedQuestIds = excludedQuestIds == null ? Set.of() : Set.copyOf(excludedQuestIds);
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
    }

    public record FocusPolicy(String focusLevel,
                              boolean allowSidetracks,
                              Set<String> allowedSidetrackTypes,
                              String returnToPlan) {
        public FocusPolicy {
            allowedSidetrackTypes = allowedSidetrackTypes == null
                    ? Set.of() : Set.copyOf(allowedSidetrackTypes);
        }
    }

    public record EntryCriteria(int requiredStartMapId,
                                String requiredRegion,
                                String requiredCharacterState) {
    }

    public record ExitCriteria(String completeWhen,
                               int finalMapId,
                               Set<Integer> blockedCompletedQuestIds,
                               Set<Integer> forbiddenMapIds,
                               Set<Integer> forbiddenNpcIds) {
        public ExitCriteria {
            blockedCompletedQuestIds = blockedCompletedQuestIds == null
                    ? Set.of() : Set.copyOf(blockedCompletedQuestIds);
            forbiddenMapIds = forbiddenMapIds == null ? Set.of() : Set.copyOf(forbiddenMapIds);
            forbiddenNpcIds = forbiddenNpcIds == null ? Set.of() : Set.copyOf(forbiddenNpcIds);
        }
    }
}
