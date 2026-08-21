package server.agents.progression.questwork.shadow;

import server.agents.progression.questwork.AgentQuestLiveState;
import server.agents.progression.questwork.AgentQuestWorkReconciliation;
import server.agents.progression.questwork.AgentQuestWorkUnit;
import server.agents.progression.questwork.AgentQuestWorkUnitService;

/** Compares new durable advice with an existing plan observation; it owns no plan execution port. */
public final class AgentQuestWorkShadowAdapter {
    private final AgentQuestWorkUnitService workUnits;

    public AgentQuestWorkShadowAdapter(AgentQuestWorkUnitService workUnits) {
        if (workUnits == null) throw new IllegalArgumentException("quest work service is required");
        this.workUnits = workUnits;
    }

    public AgentQuestWorkShadowReport compare(
            String workUnitId,
            AgentQuestLiveState live,
            AgentQuestPlanShadowObservation existingPlan,
            long nowMs) {
        if (existingPlan == null || live == null) {
            throw new IllegalArgumentException("live state and existing plan observation are required");
        }
        AgentQuestWorkUnit durable = workUnits.find(normalize(workUnitId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown quest work unit " + workUnitId));
        if (durable.questId() != existingPlan.questId()) {
            AgentQuestWorkReconciliation recommendation = workUnits.reconcile(
                    workUnitId, live, nowMs);
            return new AgentQuestWorkShadowReport(existingPlan, recommendation,
                    AgentQuestShadowComparison.QUEST_MISMATCH,
                    "existing plan and durable work unit refer to different quests");
        }
        AgentQuestWorkReconciliation recommendation = workUnits.reconcile(
                workUnitId, live, nowMs);
        if (existingPlan.action() != recommendation.nextAction()) {
            return new AgentQuestWorkShadowReport(existingPlan, recommendation,
                    AgentQuestShadowComparison.ACTION_MISMATCH,
                    "existing action=" + existingPlan.action()
                            + "; durable action=" + recommendation.nextAction());
        }
        if (existingPlan.destinationMapId() != recommendation.destinationMapId()) {
            return new AgentQuestWorkShadowReport(existingPlan, recommendation,
                    AgentQuestShadowComparison.DESTINATION_MISMATCH,
                    "existing destination=" + existingPlan.destinationMapId()
                            + "; durable destination=" + recommendation.destinationMapId());
        }
        return new AgentQuestWorkShadowReport(existingPlan, recommendation,
                AgentQuestShadowComparison.MATCH,
                "existing plan and durable quest recommendation agree exactly");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
