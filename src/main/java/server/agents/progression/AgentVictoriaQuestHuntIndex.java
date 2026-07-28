package server.agents.progression;

import java.util.List;

record AgentVictoriaQuestHuntIndex(
        int schemaVersion,
        String catalogId,
        String revision,
        List<Entry> entries) {

    AgentVictoriaQuestHuntIndex {
        if (schemaVersion <= 0 || blank(catalogId) || blank(revision) || entries == null) {
            throw new IllegalArgumentException("a complete quest hunt index is required");
        }
        entries = List.copyOf(entries);
    }

    record Entry(int questId, String questName, List<Objective> objectives) {
        Entry {
            objectives = objectives == null ? List.of() : List.copyOf(objectives);
        }
    }

    record Objective(
            String objectiveId,
            String type,
            int targetId,
            int requiredCount,
            List<Candidate> candidates) {
        Objective {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    record Candidate(
            int rank,
            int mapId,
            String mapName,
            long score,
            List<Integer> targetMobIds,
            int targetSpawnEntries,
            int totalSpawnEntries,
            int targetConcentrationBasisPoints,
            int coObjectiveCoverageCount,
            int targetComponentCount,
            int recommendedAgents,
            int maximumAgents,
            ScoreEvidence scoreEvidence) {
        Candidate {
            targetMobIds = targetMobIds == null ? List.of() : List.copyOf(targetMobIds);
        }

        AgentVictoriaQuestRuntimeCatalog.HuntMap asHuntMap() {
            return new AgentVictoriaQuestRuntimeCatalog.HuntMap(
                    Math.max(1, rank), mapId, Math.max(1, recommendedAgents),
                    Math.max(Math.max(1, recommendedAgents), maximumAgents), targetMobIds);
        }
    }

    record ScoreEvidence(
            long targetSpawnScore,
            long targetConcentrationScore,
            long coObjectiveCoverageScore,
            long otherRequiredSpawnScore,
            long expectedDropYieldScore,
            long irrelevantSpawnPenalty,
            long traversableWidthPenalty,
            long componentSpreadPenalty,
            long climbablePenalty,
            long topologyComplexityPenalty,
            long levelHazardPenalty) {

        String summary() {
            return "spawn=" + targetSpawnScore
                    + ",concentration=" + targetConcentrationScore
                    + ",coObjectives=" + coObjectiveCoverageScore
                    + ",drops=" + expectedDropYieldScore
                    + ",irrelevantPenalty=" + irrelevantSpawnPenalty
                    + ",topologyPenalty=" + (traversableWidthPenalty + componentSpreadPenalty
                    + climbablePenalty + topologyComplexityPenalty)
                    + ",hazardPenalty=" + levelHazardPenalty;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
