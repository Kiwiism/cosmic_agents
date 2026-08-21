package server.agents.progression.questcatalog;

import java.util.List;
import java.util.Set;

/** One independently selectable quest assembled from generated facts and authored guidance. */
public record AgentQuestDefinition(
        int questId,
        String questName,
        Integer minimumLevel,
        Integer maximumLevel,
        int recommendedLevel,
        Set<Integer> allowedJobIds,
        List<Prerequisite> prerequisites,
        boolean autonomousStartAllowed,
        AgentQuestSelectionDisposition selectionDisposition,
        Endpoint start,
        Endpoint completion,
        List<Objective> objectives,
        AgentQuestAttemptRequirements attemptRequirements,
        String recommendationRationale,
        List<String> warnings) {

    public AgentQuestDefinition {
        questName = text(questName);
        recommendationRationale = text(recommendationRationale);
        allowedJobIds = Set.copyOf(allowedJobIds == null ? Set.of() : allowedJobIds);
        prerequisites = List.copyOf(prerequisites == null ? List.of() : prerequisites);
        objectives = List.copyOf(objectives == null ? List.of() : objectives);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        if (questId <= 0 || questName.isEmpty() || recommendedLevel <= 0
                || selectionDisposition == null || start == null || completion == null
                || attemptRequirements == null) {
            throw new IllegalArgumentException("complete universal quest identity and guidance are required");
        }
        if (minimumLevel != null && recommendedLevel < minimumLevel) {
            throw new IllegalArgumentException("recommended quest level cannot be below the server minimum");
        }
    }

    public record Prerequisite(int questId, int requiredState) {
        public Prerequisite {
            if (questId <= 0 || requiredState < 0) {
                throw new IllegalArgumentException("valid quest prerequisite is required");
            }
        }
    }

    public record Endpoint(int npcId, List<Integer> mapIds) {
        public Endpoint {
            mapIds = List.copyOf(mapIds == null ? List.of() : mapIds);
            if (npcId < 0 || mapIds.stream().anyMatch(id -> id == null || id <= 0)) {
                throw new IllegalArgumentException("quest endpoint facts cannot be negative or invalid");
            }
        }

        public boolean complete() {
            return npcId > 0 && !mapIds.isEmpty();
        }
    }

    public record Objective(
            String objectiveId,
            String type,
            int targetId,
            String targetName,
            int requiredCount,
            List<Integer> sourceMobIds,
            List<HuntMap> huntMaps) {
        public Objective {
            objectiveId = text(objectiveId);
            type = text(type);
            targetName = text(targetName);
            sourceMobIds = List.copyOf(sourceMobIds == null ? List.of() : sourceMobIds);
            huntMaps = List.copyOf(huntMaps == null ? List.of() : huntMaps);
            if (objectiveId.isEmpty() || type.isEmpty() || targetId <= 0 || requiredCount <= 0) {
                throw new IllegalArgumentException("complete quest objective is required");
            }
        }
    }

    public record HuntMap(
            int rank,
            int mapId,
            String mapName,
            List<Integer> targetMobIds,
            int maximumMobLevel,
            int recommendedAgents,
            int maximumAgents) {
        public HuntMap {
            mapName = text(mapName);
            targetMobIds = List.copyOf(targetMobIds == null ? List.of() : targetMobIds);
            if (rank <= 0 || mapId <= 0 || targetMobIds.isEmpty()
                    || maximumMobLevel < 0 || recommendedAgents <= 0
                    || maximumAgents < recommendedAgents) {
                throw new IllegalArgumentException("complete ranked quest hunt map is required");
            }
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
