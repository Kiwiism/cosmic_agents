package server.agents.progression;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/** Captured mid-pack reset states used to resume a validated Victoria MVP test boundary. */
final class VictoriaResumeCheckpointBaseline {
    private static final String RESOURCE_PATH =
            "/agents/fixtures/victoria-resume-checkpoints.json";
    private static final Content CONTENT = load();

    private VictoriaResumeCheckpointBaseline() {
    }

    static ResumeCheckpoint require(String bundleId, String checkpointId) {
        ResumeCheckpoint checkpoint = CONTENT.checkpoints().get(bundleId + ":" + checkpointId);
        if (checkpoint == null) {
            throw new IllegalArgumentException(
                    checkpointId + " has no captured baseline for " + bundleId);
        }
        return checkpoint;
    }

    private static Content load() {
        try (InputStream input =
                     VictoriaResumeCheckpointBaseline.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException(
                        "missing Victoria resume checkpoint fixture " + RESOURCE_PATH);
            }
            Content content = new ObjectMapper().readValue(input, Content.class);
            if (content.schemaVersion() != 1 || content.checkpoints() == null
                    || content.checkpoints().isEmpty()) {
                throw new IllegalStateException(
                        "invalid Victoria resume checkpoint fixture " + RESOURCE_PATH);
            }
            return content.normalized();
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "failed to load Victoria resume checkpoint fixture " + RESOURCE_PATH, failure);
        }
    }

    record Content(int schemaVersion, Map<String, ResumeCheckpoint> checkpoints) {
        private Content normalized() {
            return new Content(schemaVersion, Map.copyOf(checkpoints));
        }
    }

    record ResumeCheckpoint(
            String questPackId,
            int questPackIndex,
            Position position,
            VictoriaCheckpointBaseline.Snapshot snapshot,
            List<ActiveQuest> activeQuests) {
        ResumeCheckpoint {
            if (questPackId == null || questPackId.isBlank() || questPackIndex < 0
                    || position == null || snapshot == null) {
                throw new IllegalArgumentException(
                        "complete Victoria resume checkpoint state is required");
            }
            activeQuests = activeQuests == null ? List.of() : List.copyOf(activeQuests);
        }
    }

    record Position(int x, int y) {
    }

    record ActiveQuest(int questId, int npcId, Map<Integer, String> progress) {
        ActiveQuest {
            progress = progress == null ? Map.of() : Map.copyOf(progress);
        }
    }
}
