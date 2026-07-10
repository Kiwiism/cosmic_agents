package client;

import org.junit.jupiter.api.Test;
import server.quest.Quest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestStatusPersistenceDirtyTest {
    @Test
    void hydrationIsCleanAndLaterMutationsMarkDirty() {
        Quest quest = mock(Quest.class);
        when(quest.getId()).thenReturn((short) 1000);
        QuestStatus status = new QuestStatus(quest, QuestStatus.Status.NOT_STARTED);
        status.setProgress(1, "001");
        status.addMedalMap(100000000);
        AtomicInteger marks = new AtomicInteger();

        status.setPersistenceDirtyMarker(marks::incrementAndGet);
        status.setCustomData("state");
        status.setCompletionTime(status.getCompletionTime() + 1);
        status.setProgress(1, "002");

        assertEquals(3, marks.get());
        assertThrows(UnsupportedOperationException.class, () -> status.getMedalMaps().add(2));
    }

    @Test
    void assigningSameValueDoesNotCreateDirtyWork() {
        Quest quest = mock(Quest.class);
        when(quest.getId()).thenReturn((short) 1001);
        QuestStatus status = new QuestStatus(quest, QuestStatus.Status.NOT_STARTED);
        AtomicInteger marks = new AtomicInteger();
        status.setPersistenceDirtyMarker(marks::incrementAndGet);

        status.setStatus(QuestStatus.Status.NOT_STARTED);
        status.setProgress(1, "001");
        status.setProgress(1, "001");

        assertEquals(1, marks.get());
    }
}
