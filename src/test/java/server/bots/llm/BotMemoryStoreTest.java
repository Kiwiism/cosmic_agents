package server.bots.llm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotMemoryStoreTest {
    private Path tmpDir;
    private String origMemoryDir;

    @BeforeEach
    void setUp() throws IOException {
        tmpDir = Files.createTempDirectory("bot-llm-memory-test");
        origMemoryDir = BotLlmConfig.memoryDir;
        BotLlmConfig.memoryDir = tmpDir.toString();
    }

    @AfterEach
    void tearDown() throws IOException {
        BotLlmConfig.memoryDir = origMemoryDir;
        if (Files.exists(tmpDir)) {
            try (var s = Files.walk(tmpDir)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    void appendAndLoadRoundTrip() {
        BotMemoryStore.appendTurn("Jason",
                new BotMemoryStore.Turn(1L, "owner", "Player1", "hi", "hey"));
        BotMemoryStore.appendTurn("Jason",
                new BotMemoryStore.Turn(2L, "stranger", "Player2", "yo bot", "hi stranger"));

        List<BotMemoryStore.Turn> recent = BotMemoryStore.loadRecent("Jason", 5);
        assertEquals(2, recent.size());
        assertEquals("Player1", recent.get(0).sender());
        assertEquals("hi", recent.get(0).msg());
        assertEquals("hi stranger", recent.get(1).reply());
    }

    @Test
    void loadRecentReturnsLastN() {
        for (int i = 0; i < 10; i++) {
            BotMemoryStore.appendTurn("B", new BotMemoryStore.Turn(i, "owner", "p", "m" + i, "r" + i));
        }
        List<BotMemoryStore.Turn> recent = BotMemoryStore.loadRecent("B", 3);
        assertEquals(3, recent.size());
        assertEquals("m7", recent.get(0).msg());
        assertEquals("r9", recent.get(2).reply());
    }

    @Test
    void compactKeepsRecentLines() throws IOException {
        BotLlmConfig.compactKeepRecentTurns = 3;
        for (int i = 0; i < 10; i++) {
            BotMemoryStore.appendTurn("Z", new BotMemoryStore.Turn(i, "owner", "p", "m" + i, "r" + i));
        }
        BotMemoryStore.compact("Z");
        assertEquals(3, BotMemoryStore.countTurns("Z"));
        List<BotMemoryStore.Turn> recent = BotMemoryStore.loadRecent("Z", 5);
        assertEquals("m7", recent.get(0).msg());
    }

    @Test
    void specialCharactersInMessagesSurviveRoundTrip() {
        String tricky = "hello \"world\" \\ \n new line";
        BotMemoryStore.appendTurn("C",
                new BotMemoryStore.Turn(1L, "party", "Friend", tricky, "ok"));
        List<BotMemoryStore.Turn> recent = BotMemoryStore.loadRecent("C", 5);
        assertEquals(1, recent.size());
        assertEquals(tricky, recent.get(0).msg());
    }

    @Test
    void ensureDirCreatesGitignore() throws IOException {
        BotMemoryStore.ensureDir();
        assertTrue(Files.exists(tmpDir.resolve(".gitignore")));
    }
}
