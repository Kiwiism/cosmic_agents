package server.agents.progression.questwork;

import server.agents.progression.questcatalog.AgentQuestDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rebuilds the cursor from server quest, inventory, objective, and map facts. */
public final class AgentQuestWorkReconciler {
    public AgentQuestWorkReconciliation reconcile(
            AgentQuestWorkUnit unit,
            AgentQuestDefinition definition,
            String catalogRevision,
            AgentQuestLiveState live,
            long nowMs) {
        if (unit == null || definition == null || live == null || catalogRevision == null
                || catalogRevision.isBlank() || nowMs < unit.updatedAtMs()
                || unit.questId() != definition.questId()
                || unit.characterId() != live.characterId()) {
            throw new IllegalArgumentException("matching quest work, catalog, and live state are required");
        }
        if (unit.terminal()) {
            return new AgentQuestWorkReconciliation(unit,
                    unit.phase() == AgentQuestWorkPhase.COMPLETED
                            ? AgentQuestWorkAction.COMPLETE : AgentQuestWorkAction.MANUAL_REVIEW,
                    0, unit.lastReasonCode());
        }

        Map<String, AgentQuestObjectiveProgress> progress = progress(definition, live);
        boolean suspended = unit.phase() == AgentQuestWorkPhase.SUSPENDED
                || unit.phase() == AgentQuestWorkPhase.SUSPEND_REQUESTED;
        if (live.questState() >= 2) {
            AgentQuestWorkUnit completed = unit.withState(
                    AgentQuestWorkPhase.COMPLETED, AgentQuestWorkStage.COMPLETE, nowMs,
                    0, "QUEST_ALREADY_COMPLETE", "", progress, catalogRevision);
            return new AgentQuestWorkReconciliation(
                    completed, AgentQuestWorkAction.COMPLETE, 0,
                    "authoritative quest state is complete");
        }

        if (live.questState() == 0) {
            if (definition.start().mapIds().isEmpty()) {
                return manualReview(unit, catalogRevision, progress, nowMs,
                        "QUEST_START_ENDPOINT_MISSING",
                        "quest start endpoint requires catalog review");
            }
            boolean atStart = definition.start().mapIds().contains(live.mapId());
            AgentQuestWorkStage stage = atStart
                    ? AgentQuestWorkStage.ACCEPT_QUEST : AgentQuestWorkStage.TRAVEL_TO_START;
            AgentQuestWorkUnit reconciled = unit.withState(
                    unit.phase(), stage, nowMs, 0, "QUEST_NOT_STARTED",
                    unit.suspensionReason(), progress, catalogRevision);
            return new AgentQuestWorkReconciliation(reconciled,
                    suspended ? AgentQuestWorkAction.WAIT
                            : atStart ? AgentQuestWorkAction.ACCEPT_QUEST
                            : AgentQuestWorkAction.TRAVEL_TO_START,
                    atStart ? 0 : definition.start().mapIds().getFirst(),
                    "authoritative quest state is not started");
        }

        boolean objectivesComplete = progress.values().stream()
                .allMatch(AgentQuestObjectiveProgress::complete);
        if (objectivesComplete) {
            if (definition.completion().mapIds().isEmpty()) {
                return manualReview(unit, catalogRevision, progress, nowMs,
                        "QUEST_COMPLETION_ENDPOINT_MISSING",
                        "quest completion endpoint requires catalog review");
            }
            boolean atCompletion = definition.completion().mapIds().contains(live.mapId());
            AgentQuestWorkStage stage = atCompletion
                    ? AgentQuestWorkStage.TURN_IN_QUEST : AgentQuestWorkStage.RETURN_TO_TURN_IN;
            AgentQuestWorkUnit reconciled = unit.withState(
                    unit.phase(), stage, nowMs, 0, "OBJECTIVES_COMPLETE",
                    unit.suspensionReason(), progress, catalogRevision);
            return new AgentQuestWorkReconciliation(reconciled,
                    suspended ? AgentQuestWorkAction.WAIT
                            : atCompletion ? AgentQuestWorkAction.TURN_IN_QUEST
                            : AgentQuestWorkAction.RETURN_TO_TURN_IN,
                    atCompletion ? 0 : definition.completion().mapIds().getFirst(),
                    "all authoritative objective counts are complete");
        }

        int huntMapId = preferredHuntMap(definition, live.mapId());
        boolean atHuntMap = huntMapId == 0 || huntMapId == live.mapId();
        AgentQuestWorkUnit reconciled = unit.withState(
                unit.phase() == AgentQuestWorkPhase.SELECTED
                        ? AgentQuestWorkPhase.ACTIVE : unit.phase(),
                AgentQuestWorkStage.COMPLETE_OBJECTIVES, nowMs, huntMapId,
                "OBJECTIVES_REMAIN", unit.suspensionReason(), progress, catalogRevision);
        return new AgentQuestWorkReconciliation(reconciled,
                suspended ? AgentQuestWorkAction.WAIT
                        : atHuntMap ? AgentQuestWorkAction.COMPLETE_OBJECTIVES
                        : AgentQuestWorkAction.TRAVEL_TO_HUNT_MAP,
                atHuntMap ? 0 : huntMapId,
                "authoritative objective debt remains");
    }

    private static AgentQuestWorkReconciliation manualReview(
            AgentQuestWorkUnit unit,
            String catalogRevision,
            Map<String, AgentQuestObjectiveProgress> progress,
            long nowMs,
            String reasonCode,
            String reason) {
        AgentQuestWorkUnit suspended = unit.withState(
                AgentQuestWorkPhase.SUSPENDED, unit.stage(), nowMs,
                unit.selectedHuntMapId(), reasonCode, reason, progress, catalogRevision);
        return new AgentQuestWorkReconciliation(
                suspended, AgentQuestWorkAction.MANUAL_REVIEW, 0, reason);
    }

    private static Map<String, AgentQuestObjectiveProgress> progress(
            AgentQuestDefinition definition,
            AgentQuestLiveState live) {
        Map<String, AgentQuestObjectiveProgress> result = new LinkedHashMap<>();
        for (AgentQuestDefinition.Objective objective : definition.objectives()) {
            int observed = objective.type().equals("collect-item")
                    ? live.itemCounts().getOrDefault(objective.targetId(), 0)
                    : live.objectiveCounts().getOrDefault(objective.objectiveId(), 0);
            result.put(objective.objectiveId(), new AgentQuestObjectiveProgress(
                    objective.objectiveId(), observed, objective.requiredCount()));
        }
        return Map.copyOf(result);
    }

    private static int preferredHuntMap(AgentQuestDefinition definition, int currentMapId) {
        List<AgentQuestDefinition.HuntMap> candidates = definition.objectives().stream()
                .flatMap(objective -> objective.huntMaps().stream())
                .distinct().toList();
        if (candidates.stream().anyMatch(candidate -> candidate.mapId() == currentMapId)) {
            return currentMapId;
        }
        return candidates.stream()
                .min(java.util.Comparator.comparingInt(AgentQuestDefinition.HuntMap::rank)
                        .thenComparingInt(AgentQuestDefinition.HuntMap::mapId))
                .map(AgentQuestDefinition.HuntMap::mapId).orElse(0);
    }
}
