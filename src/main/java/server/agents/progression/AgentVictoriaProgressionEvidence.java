package server.agents.progression;

import java.util.List;

public record AgentVictoriaProgressionEvidence(
        int schemaVersion,
        long capturedAtMs,
        int characterId,
        String characterName,
        int level,
        int experience,
        int jobId,
        int mapId,
        int mesos,
        Stats stats,
        String careerBundleId,
        String apProfileId,
        String spProfileId,
        String progressionProfileId,
        int progressionProfileVersion,
        String careerStage,
        String activeObjectiveId,
        String activeObjectiveType,
        List<String> suspendedObjectiveIds,
        int trainingTargetLevel,
        int selectedTrainingMapId,
        String trainingSelectionReason,
        String questStage,
        int scheduledQuestId,
        String supplyPhase,
        String supplyCategory) {

    public AgentVictoriaProgressionEvidence {
        characterName = characterName == null ? "" : characterName;
        careerBundleId = text(careerBundleId);
        apProfileId = text(apProfileId);
        spProfileId = text(spProfileId);
        progressionProfileId = text(progressionProfileId);
        careerStage = text(careerStage);
        activeObjectiveId = text(activeObjectiveId);
        activeObjectiveType = text(activeObjectiveType);
        suspendedObjectiveIds = suspendedObjectiveIds == null ? List.of() : List.copyOf(suspendedObjectiveIds);
        trainingSelectionReason = text(trainingSelectionReason);
        questStage = text(questStage);
        supplyPhase = text(supplyPhase);
        supplyCategory = text(supplyCategory);
    }

    public record Stats(int str, int dex, int intStat, int luk, int remainingAp,
                        List<Integer> remainingSpByBook) {
        public Stats {
            remainingSpByBook = remainingSpByBook == null ? List.of() : List.copyOf(remainingSpByBook);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
