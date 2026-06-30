package server.agents.capabilities.dialogue.llm;

import server.agents.capabilities.dialogue.llm.AgentLlmConfig;

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

class AgentMemoryStoreTest {
    private Path tmpDir;
    private String origMemoryDir;

    @BeforeEach
    void setUp() throws IOException {
        tmpDir = Files.createTempDirectory("bot-llm-memory-test");
        origMemoryDir = AgentLlmConfig.memoryDir;
        AgentLlmConfig.memoryDir = tmpDir.toString();
    }

    @AfterEach
    void tearDown() throws IOException {
        AgentLlmConfig.memoryDir = origMemoryDir;
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
        AgentMemoryStore.appendTurn("Jason",
                new AgentMemoryStore.Turn(1L, "owner", "Player1", "hi", "hey"));
        AgentMemoryStore.appendTurn("Jason",
                new AgentMemoryStore.Turn(2L, "stranger", "Player2", "yo bot", "hi stranger"));

        List<AgentMemoryStore.Turn> recent = AgentMemoryStore.loadUncompacted("Jason");
        assertEquals(2, recent.size());
        assertEquals("Player1", recent.get(0).sender());
        assertEquals("hi", recent.get(0).msg());
        assertEquals("hi stranger", recent.get(1).reply());
    }

    @Test
    void loadUncompactedReturnsEverythingWhenNoCompactionRan() {
        for (int i = 0; i < 10; i++) {
            AgentMemoryStore.appendTurn("B", new AgentMemoryStore.Turn(i, "owner", "p", "m" + i, "r" + i));
        }
        List<AgentMemoryStore.Turn> recent = AgentMemoryStore.loadUncompacted("B");
        assertEquals(10, recent.size());
        assertEquals("m0", recent.get(0).msg());
        assertEquals("r9", recent.get(9).reply());
    }

    @Test
    void compactPreservesJsonlHistory() throws IOException {
        // Compact without Ollama available: cursor must NOT advance, jsonl untouched.
        for (int i = 0; i < 20; i++) {
            AgentMemoryStore.appendTurn("Z", new AgentMemoryStore.Turn(i, "owner", "p", "m" + i, "r" + i));
        }
        AgentMemoryStore.compact("Z");
        // Whether Ollama is up or not, the full jsonl history must survive.
        assertEquals(20, AgentMemoryStore.countTurns("Z"));
    }

    @Test
    void specialCharactersInMessagesSurviveRoundTrip() {
        String tricky = "hello \"world\" \\ \n new line";
        AgentMemoryStore.appendTurn("C",
                new AgentMemoryStore.Turn(1L, "party", "Friend", tricky, "ok"));
        List<AgentMemoryStore.Turn> recent = AgentMemoryStore.loadUncompacted("C");
        assertEquals(1, recent.size());
        assertEquals(tricky, recent.get(0).msg());
    }

    @Test
    void ensureDirCreatesGitignore() throws IOException {
        AgentMemoryStore.ensureDir();
        assertTrue(Files.exists(tmpDir.resolve(".gitignore")));
    }
}
