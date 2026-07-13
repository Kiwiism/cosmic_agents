package server.agents.capabilities.quest;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

public final class MapleIslandSouthperryBaseline {
    private static final String RESOURCE_PATH = "/agents/fixtures/amherstrun-post-amherst-baseline.json";
    private static final Snapshot SNAPSHOT = load();

    private MapleIslandSouthperryBaseline() {
    }

    public static Snapshot snapshot() {
        return SNAPSHOT;
    }

    private static Snapshot load() {
        try (InputStream input = MapleIslandSouthperryBaseline.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("missing Southperry baseline resource " + RESOURCE_PATH);
            }
            Snapshot snapshot = new ObjectMapper().readValue(input, Snapshot.class);
            if (snapshot.schemaVersion() != 1 || snapshot.character() == null
                    || snapshot.items() == null || snapshot.completedQuestIds() == null
                    || snapshot.resetQuestIds() == null) {
                throw new IllegalStateException("invalid Southperry baseline resource " + RESOURCE_PATH);
            }
            return snapshot.normalized();
        } catch (IOException failure) {
            throw new IllegalStateException("failed to load Southperry baseline resource " + RESOURCE_PATH, failure);
        }
    }

    public record Snapshot(
            int schemaVersion,
            String sourceCharacterName,
            String capturedAt,
            CharacterState character,
            List<ItemState> items,
            Set<Integer> completedQuestIds,
            Set<Integer> resetQuestIds) {
        private Snapshot normalized() {
            return new Snapshot(schemaVersion, sourceCharacterName, capturedAt, character,
                    List.copyOf(items), Set.copyOf(completedQuestIds), Set.copyOf(resetQuestIds));
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
            int[] remainingSp,
            int skinColorId,
            int gender,
            int hair,
            int face) {
        public CharacterState {
            remainingSp = remainingSp == null ? new int[0] : remainingSp.clone();
        }

        @Override
        public int[] remainingSp() {
            return remainingSp.clone();
        }
    }

    public record ItemState(
            int itemId,
            String inventoryType,
            short position,
            short quantity) {
    }
}
