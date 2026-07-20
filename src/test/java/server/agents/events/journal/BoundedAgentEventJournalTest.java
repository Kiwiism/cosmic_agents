package server.agents.events.journal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.events.AgentDomainEvent;
import server.agents.progression.events.AgentJobAdvancedEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedAgentEventJournalTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesSelectedEventsOffThePublisherThread() throws Exception {
        Path journalPath = temporaryDirectory.resolve("events.jsonl");
        BoundedAgentEventJournal journal = new BoundedAgentEventJournal(
                new AgentEventJournalConfig(true, journalPath, 4, 1024 * 1024));

        assertFalse(journal.offer(new AgentDomainEvent(
                1, 10L, "navigation.route-selected", "route", Map.of())));
        assertTrue(journal.offer(new AgentJobAdvancedEvent(
                1, 20L, 0, 100, 10, 100000000, "advance-warrior")));
        journal.close();

        List<String> lines = Files.readAllLines(journalPath);
        assertEquals(1, lines.size());
        JsonNode record = new ObjectMapper().readTree(lines.getFirst());
        assertEquals("progression.job-advanced", record.path("type").asText());
        assertEquals("advance-warrior", record.path("context").path("objectiveId").asText());
        assertEquals(1, journal.snapshot().written());
        assertEquals(0, journal.snapshot().rejected());
    }
}
