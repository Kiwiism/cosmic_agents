package server.agents.progression;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Versioned checkpoint-2 reset states keyed by career build bundle.
 *
 * <p>Captured entries are authoritative observations from completed runs. Predicted entries are
 * temporary test fixtures and must be replaced after that career's checkpoint-1 run is recorded.
 */
public final class VictoriaCheckpointBaseline {
    private static final String RESOURCE_PATH =
            "/agents/fixtures/victoria-checkpoint2-baselines.json";
    private static final Content CONTENT = load();

    private VictoriaCheckpointBaseline() {
    }

    public static Snapshot require(String bundleId) {
        Snapshot snapshot = CONTENT.baselines().get(bundleId);
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "checkpoint 2 has no captured or predicted baseline for " + bundleId);
        }
        return snapshot;
    }

    public static Set<String> bundleIds() {
        return CONTENT.baselines().keySet();
    }

    private static Content load() {
        try (InputStream input = VictoriaCheckpointBaseline.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("missing Victoria checkpoint fixture " + RESOURCE_PATH);
            }
            Content content = new ObjectMapper().readValue(input, Content.class);
            if (content.schemaVersion() != 1 || content.baselines() == null
                    || content.baselines().isEmpty()) {
                throw new IllegalStateException("invalid Victoria checkpoint fixture " + RESOURCE_PATH);
            }
            return content.normalized();
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "failed to load Victoria checkpoint fixture " + RESOURCE_PATH, failure);
        }
    }

    public record Content(int schemaVersion, Map<String, Snapshot> baselines) {
        private Content normalized() {
            return new Content(schemaVersion, baselines.entrySet().stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(
                            Map.Entry::getKey, entry -> entry.getValue().normalized())));
        }
    }

    public record Snapshot(
            String provenance,
            String sourceCharacterName,
            String recordedAt,
            String note,
            CharacterState character,
            List<ItemState> items,
            List<SkillState> skills,
            Set<Integer> completedQuestIds,
            Set<Integer> resetQuestIds) {
        public Snapshot {
            if (!"CAPTURED".equals(provenance) && !"PREDICTED".equals(provenance)) {
                throw new IllegalArgumentException("checkpoint provenance must be CAPTURED or PREDICTED");
            }
            if (character == null || items == null || skills == null
                    || completedQuestIds == null || resetQuestIds == null) {
                throw new IllegalArgumentException("complete Victoria checkpoint state is required");
            }
        }

        private Snapshot normalized() {
            return new Snapshot(provenance, sourceCharacterName, recordedAt, note, character,
                    List.copyOf(items), List.copyOf(skills),
                    Set.copyOf(completedQuestIds), Set.copyOf(resetQuestIds));
        }

        public boolean captured() {
            return "CAPTURED".equals(provenance);
        }
    }

    public record CharacterState(
            int mapId,
            int level,
            int exp,
            int jobId,
            int str,
            int dex,
            int intelligence,
            int luk,
            int hp,
            int mp,
            int maxHp,
            int maxMp,
            int mesos,
            int remainingAp,
            int[] remainingSp) {
        public CharacterState {
            remainingSp = remainingSp == null ? new int[0] : remainingSp.clone();
        }

        @Override
        public int[] remainingSp() {
            return remainingSp.clone();
        }
    }

    public record ItemState(int itemId, String inventoryType, short position, short quantity) {
    }

    public record SkillState(int skillId, int level, int masterLevel) {
    }
}
