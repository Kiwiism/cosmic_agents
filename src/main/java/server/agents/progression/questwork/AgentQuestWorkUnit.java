package server.agents.progression.questwork;

import java.util.Map;

/** Serializable quest intent and resume cursor; live game state is never stored as authority. */
public record AgentQuestWorkUnit(
        int schemaVersion,
        String workUnitId,
        String agentId,
        int characterId,
        int questId,
        String catalogRevision,
        AgentQuestWorkPhase phase,
        AgentQuestWorkStage stage,
        long createdAtMs,
        long updatedAtMs,
        int selectedHuntMapId,
        int retryCount,
        String suspensionReason,
        String lastReasonCode,
        Map<String, AgentQuestObjectiveProgress> objectiveProgress) {

    public AgentQuestWorkUnit {
        workUnitId = text(workUnitId);
        agentId = text(agentId);
        catalogRevision = text(catalogRevision);
        suspensionReason = text(suspensionReason);
        lastReasonCode = text(lastReasonCode);
        objectiveProgress = Map.copyOf(objectiveProgress == null ? Map.of() : objectiveProgress);
        if (schemaVersion <= 0 || workUnitId.isEmpty() || agentId.isEmpty()
                || characterId <= 0 || questId <= 0 || catalogRevision.isEmpty()
                || phase == null || stage == null || createdAtMs < 0L
                || updatedAtMs < createdAtMs || selectedHuntMapId < 0 || retryCount < 0
                || objectiveProgress.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
                        || !entry.getKey().equals(entry.getValue().objectiveId()))) {
            throw new IllegalArgumentException("complete durable quest work-unit state is required");
        }
    }

    public boolean terminal() {
        return phase.terminal();
    }

    public boolean suspended() {
        return phase == AgentQuestWorkPhase.SUSPENDED;
    }

    public AgentQuestWorkUnit withState(
            AgentQuestWorkPhase nextPhase,
            AgentQuestWorkStage nextStage,
            long nowMs,
            int nextHuntMapId,
            String nextReason,
            String nextSuspensionReason,
            Map<String, AgentQuestObjectiveProgress> nextProgress,
            String nextCatalogRevision) {
        return new AgentQuestWorkUnit(schemaVersion, workUnitId, agentId, characterId, questId,
                nextCatalogRevision, nextPhase, nextStage, createdAtMs, nowMs,
                nextHuntMapId, retryCount, nextSuspensionReason, nextReason, nextProgress);
    }

    public AgentQuestWorkUnit withPhase(
            AgentQuestWorkPhase nextPhase,
            long nowMs,
            String reason,
            String nextSuspensionReason) {
        return withState(nextPhase, stage, nowMs, selectedHuntMapId, reason,
                nextSuspensionReason, objectiveProgress, catalogRevision);
    }

    public AgentQuestWorkUnit withRetry(long nowMs, String reason) {
        return new AgentQuestWorkUnit(schemaVersion, workUnitId, agentId, characterId, questId,
                catalogRevision, phase, stage, createdAtMs, nowMs, selectedHuntMapId,
                retryCount + 1, suspensionReason, reason, objectiveProgress);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
