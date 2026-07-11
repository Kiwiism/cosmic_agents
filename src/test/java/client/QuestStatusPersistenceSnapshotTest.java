package client;

import org.junit.jupiter.api.Test;
import server.quest.Quest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class QuestStatusPersistenceSnapshotTest {
    @Test
    void skipsOnlyEmptyNotStartedPlaceholder() {
        assertFalse(snapshot(QuestStatus.Status.NOT_STARTED, 0, Map.of(), List.of()).shouldPersist());
    }

    @Test
    void preservesNotStartedQuestWithForfeitHistory() {
        assertTrue(snapshot(QuestStatus.Status.NOT_STARTED, 1, Map.of(), List.of()).shouldPersist());
    }

    @Test
    void preservesNotStartedQuestWithProgress() {
        assertTrue(snapshot(QuestStatus.Status.NOT_STARTED, 0, Map.of(100100, "3"), List.of()).shouldPersist());
    }

    @Test
    void preservesNotStartedQuestWithMedalMaps() {
        assertTrue(snapshot(QuestStatus.Status.NOT_STARTED, 0, Map.of(), List.of(100000000)).shouldPersist());
    }

    @Test
    void preservesStartedAndCompletedQuests() {
        assertTrue(snapshot(QuestStatus.Status.STARTED, 0, Map.of(), List.of()).shouldPersist());
        assertTrue(snapshot(QuestStatus.Status.COMPLETED, 0, Map.of(), List.of()).shouldPersist());
    }

    @Test
    void meaningfulNotStartedSnapshotRoundTripsWithoutLosingState() {
        Quest quest = mock(Quest.class);
        QuestStatus.PersistenceSnapshot snapshot = snapshot(
                QuestStatus.Status.NOT_STARTED, 2, Map.of(100100, "7"), List.of(100000000));

        QuestStatus restored = QuestStatus.fromPersistenceSnapshot(quest, snapshot);
        QuestStatus.PersistenceSnapshot roundTrip = restored.persistenceSnapshot();

        assertTrue(roundTrip.shouldPersist());
        assertEquals(snapshot.status(), roundTrip.status());
        assertEquals(snapshot.forfeited(), roundTrip.forfeited());
        assertEquals(snapshot.progress(), roundTrip.progress());
        assertEquals(snapshot.medalMaps(), roundTrip.medalMaps());
    }

    private static QuestStatus.PersistenceSnapshot snapshot(
            QuestStatus.Status status,
            int forfeited,
            Map<Integer, String> progress,
            List<Integer> medalMaps) {
        return new QuestStatus.PersistenceSnapshot(
                (short) 1000,
                status.getId(),
                0L,
                0L,
                forfeited,
                0,
                progress,
                medalMaps);
    }
}
