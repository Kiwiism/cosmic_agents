package server.agents.progression;

public record AgentCareerProgressionCheckpoint(
        int schemaVersion,
        int characterId,
        String bundleId,
        int bundleVersion,
        AgentCareerProgressionState.RunMode runMode,
        String startVariantId,
        AgentCareerProgressionState.Stage stage,
        int trainingQuestIndex,
        long nextActionAtMs,
        String blockReason,
        long stateRevision,
        long updatedAtMs) {

    public AgentCareerProgressionCheckpoint {
        if (schemaVersion <= 0 || characterId <= 0 || bundleId == null || bundleId.isBlank()
                || bundleVersion <= 0 || runMode == null || startVariantId == null
                || startVariantId.isBlank() || stage == null || trainingQuestIndex < 0
                || nextActionAtMs < 0 || stateRevision < 0 || updatedAtMs < 0) {
            throw new IllegalArgumentException("complete career progression checkpoint is required");
        }
        bundleId = bundleId.trim();
        startVariantId = startVariantId.trim();
        blockReason = blockReason == null ? "" : blockReason;
    }
}
