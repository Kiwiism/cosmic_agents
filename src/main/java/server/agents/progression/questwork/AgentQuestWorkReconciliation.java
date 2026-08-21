package server.agents.progression.questwork;

/** Pure reconciliation result. Execution is delegated to the existing questing capabilities. */
public record AgentQuestWorkReconciliation(
        AgentQuestWorkUnit workUnit,
        AgentQuestWorkAction nextAction,
        int destinationMapId,
        String reason) {

    public AgentQuestWorkReconciliation {
        reason = reason == null ? "" : reason.trim();
        if (workUnit == null || nextAction == null || destinationMapId < 0) {
            throw new IllegalArgumentException("complete quest work reconciliation is required");
        }
    }
}
