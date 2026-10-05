package server.agents.integration.typesafe.state;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JevStateTest {
    @Test
    void keepsInsertionOrderAndSkipsEmptyBlocks() {
        JevState.Built built = JevState.create()
                .put("message", "grind here", 100)
                .put("speaker", Map.of(), 90)
                .put("bot", Map.of("name", "Jason"), 80)
                .put("history", List.of(), 50)
                .put("blank", "   ", 40)
                .put("missing", null, 30)
                .build(10_000);

        assertEquals(List.of("message", "bot"), List.copyOf(built.state().keySet()));
        assertTrue(built.droppedKeys().isEmpty());
    }

    @Test
    void dropsLowestPriorityBlocksFirstUntilUnderBudget() {
        String big = "x".repeat(400); // ~100 tokens at 4 chars per token
        JevState.Built built = JevState.create()
                .put("message", "stop", 100)
                .put("evidence", big, 20)
                .put("history", big, 40)
                .put("bot", big, 80)
                .build(150);

        assertEquals(List.of("evidence", "history"), built.droppedKeys());
        assertEquals(List.of("message", "bot"), List.copyOf(built.state().keySet()));
        assertTrue(built.estimatedTokens() <= 150);
    }

    @Test
    void neverDropsTheLastRemainingBlock() {
        JevState.Built built = JevState.create()
                .put("message", "y".repeat(4_000), 100)
                .build(10);

        assertEquals(List.of("message"), List.copyOf(built.state().keySet()));
        assertFalse(built.droppedKeys().contains("message"));
    }

    @Test
    void bandsTurnNumbersIntoWords() {
        assertEquals("full", JevStateBands.percentBand(500, 500));
        assertEquals("about half", JevStateBands.percentBand(250, 500));
        assertEquals("low (below 30%)", JevStateBands.percentBand(100, 500));
        assertEquals("critical (below 15%)", JevStateBands.percentBand(10, 500));
        assertEquals("empty", JevStateBands.percentBand(0, 500));
        assertEquals("unknown", JevStateBands.percentBand(5, 0));

        assertEquals("comfortable", JevStateBands.mesoBand(250_000L));
        assertEquals("well above", JevStateBands.levelGap(28, 45));
        assertEquals("about the same", JevStateBands.levelGap(30, 33));
        assertEquals("just now", JevStateBands.relativeAge(1_200L));
        assertEquals("about 40 seconds ago", JevStateBands.relativeAge(41_000L));
        assertEquals("about 3 minutes ago", JevStateBands.relativeAge(190_000L));
        assertEquals("a few", JevStateBands.countBand(3));
    }
}
