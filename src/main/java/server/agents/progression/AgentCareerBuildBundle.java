package server.agents.progression;

import java.util.List;

/** Versioned career policy selected once for an Agent and restored on every session. */
public record AgentCareerBuildBundle(
        String bundleId,
        int bundleVersion,
        String career,
        int firstJobId,
        int advancementLevel,
        String apProfileId,
        String spProfileId,
        int instructorNpcId,
        int instructorMapId,
        List<Integer> instructorTrainingQuestIds,
        int milestoneLevel) {

    public AgentCareerBuildBundle {
        if (bundleId == null || bundleId.isBlank() || bundleVersion <= 0
                || career == null || career.isBlank() || firstJobId <= 0
                || advancementLevel < 8 || apProfileId == null || apProfileId.isBlank()
                || spProfileId == null || spProfileId.isBlank() || instructorNpcId <= 0
                || instructorMapId <= 0 || instructorTrainingQuestIds == null
                || instructorTrainingQuestIds.isEmpty() || milestoneLevel < advancementLevel) {
            throw new IllegalArgumentException("valid career build bundle fields are required");
        }
        instructorTrainingQuestIds = List.copyOf(instructorTrainingQuestIds);
    }
}
