package server.agents.progression.questwork.shadow;

import server.agents.progression.questwork.AgentQuestWorkReconciliation;

/** Side-effect-free comparison with respect to the existing foreground quest plan. */
public record AgentQuestWorkShadowReport(
        AgentQuestPlanShadowObservation existingPlan,
        AgentQuestWorkReconciliation durableRecommendation,
        AgentQuestShadowComparison comparison,
        String explanation) {

    public AgentQuestWorkShadowReport {
        explanation = explanation == null ? "" : explanation.trim();
        if (existingPlan == null || durableRecommendation == null || comparison == null) {
            throw new IllegalArgumentException("complete quest shadow comparison is required");
        }
    }

    public boolean matches() {
        return comparison == AgentQuestShadowComparison.MATCH;
    }
}
