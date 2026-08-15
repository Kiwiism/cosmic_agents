package server.agents.progression;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Set;

record AgentHuntSelectionRequest(
        AgentRuntimeEntry entry,
        Character agent,
        AgentHuntObjectiveSpec objective,
        Set<Integer> excludedMapIds,
        Reason reason,
        long nowMs) {

    AgentHuntSelectionRequest {
        if (entry == null || agent == null || objective == null) {
            throw new IllegalArgumentException("a hunt request requires an Agent and objectives");
        }
        excludedMapIds = excludedMapIds == null ? Set.of() : Set.copyOf(excludedMapIds);
        reason = reason == null ? Reason.NORMAL : reason;
    }

    AgentHuntSelectionRequest(
            AgentRuntimeEntry entry,
            Character agent,
            String selectionId,
            List<ObjectiveDemand> objectives,
            List<AgentVictoriaQuestRuntimeCatalog.HuntMap> preferredMaps,
            Set<Integer> excludedMapIds,
            boolean mvpPlan,
            Reason reason,
            long nowMs) {
        this(entry, agent,
                new AgentHuntObjectiveSpec(selectionId, objectives, preferredMaps, mvpPlan),
                excludedMapIds, reason, nowMs);
    }

    String selectionId() {
        return objective.selectionId();
    }

    List<ObjectiveDemand> objectives() {
        return objective.objectives();
    }

    List<AgentVictoriaQuestRuntimeCatalog.HuntMap> preferredMaps() {
        return objective.preferredMaps();
    }

    boolean mvpPlan() {
        return objective.mvpPlan();
    }

    record ObjectiveDemand(
            int questId,
            String objectiveId,
            String type,
            int targetId,
            int requiredCount,
            int currentCount,
            Set<Integer> sourceMobIds) {
        ObjectiveDemand {
            if (questId <= 0 || objectiveId == null || objectiveId.isBlank()
                    || type == null || type.isBlank() || targetId <= 0 || requiredCount <= 0
                    || sourceMobIds == null || sourceMobIds.isEmpty()) {
                throw new IllegalArgumentException("a hunt demand requires objective truth");
            }
            currentCount = Math.max(0, currentCount);
            sourceMobIds = Set.copyOf(sourceMobIds);
        }

        int remainingCount() {
            return Math.max(0, requiredCount - currentCount);
        }

        boolean collectObjective() {
            return type.toLowerCase(java.util.Locale.ROOT).contains("collect");
        }
    }

    enum Reason {
        NORMAL,
        EXHAUSTION_FALLBACK,
        NAVIGATION_FALLBACK
    }
}
