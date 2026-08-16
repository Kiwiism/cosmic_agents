package server.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTimeTest {
    @Test
    void scopesLogicalTimeAndRestoresWallClock() {
        assertEquals(123_456L, QuestTime.withTimeSource(() -> 123_456L, QuestTime::now));
        assertTrue(Math.abs(System.currentTimeMillis() - QuestTime.now()) < 1_000L);
    }
}
