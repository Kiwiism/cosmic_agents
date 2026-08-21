package server.agents.progression.questwork.shadow;

import server.agents.progression.questwork.AgentQuestWorkAction;

/** Typed projection of what the existing quest plan is currently trying to do. */
public record AgentQuestPlanShadowObservation(
        String planId,
        String stepId,
        int questId,
        AgentQuestWorkAction action,
        int destinationMapId,
        long capturedAtMs) {

    public AgentQuestPlanShadowObservation {
        planId = text(planId);
        stepId = text(stepId);
        if (planId.isEmpty() || stepId.isEmpty() || questId <= 0 || action == null
                || destinationMapId < 0 || capturedAtMs < 0L) {
            throw new IllegalArgumentException("complete existing quest-plan observation is required");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
