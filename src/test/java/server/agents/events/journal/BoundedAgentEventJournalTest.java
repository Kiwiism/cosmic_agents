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
import java.util.Set;

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

    @Test
    void replaysUsingBoundedAgentObjectiveTypeAndTimeFilters() {
        Path journalPath = temporaryDirectory.resolve("replay.jsonl");
        BoundedAgentEventJournal journal = new BoundedAgentEventJournal(
                new AgentEventJournalConfig(true, journalPath, 4, 1024 * 1024));
        journal.offer(new AgentJobAdvancedEvent(
                1, 20L, 0, 100, 10, 100000000, "warrior"));
        journal.offer(new AgentJobAdvancedEvent(
                2, 30L, 0, 200, 10, 101000000, "magician"));
        journal.close();

        List<AgentEventJournalRecord> records = new AgentEventJournalReplayReader(journalPath).query(
                new AgentEventReplayQuery(2, "magician", "magician",
                        Set.of(AgentJobAdvancedEvent.TYPE), 25L, 35L, 1));

        assertEquals(1, records.size());
        assertEquals(2, records.getFirst().agentId());
        assertEquals("magician", records.getFirst().context().objectiveId());
    }

    @Test
    void rotatesAtTheConfiguredDiskBoundAndReplaysBothSegments() {
        Path journalPath = temporaryDirectory.resolve("rotating.jsonl");
        BoundedAgentEventJournal journal = new BoundedAgentEventJournal(
                new AgentEventJournalConfig(true, journalPath, 4, 1));
        journal.offer(new AgentJobAdvancedEvent(
                1, 20L, 0, 100, 10, 100000000, "warrior"));
        journal.offer(new AgentJobAdvancedEvent(
                2, 30L, 0, 200, 10, 101000000, "magician"));
        journal.close();

        assertTrue(Files.isRegularFile(journalPath));
        assertTrue(Files.isRegularFile(journalPath.resolveSibling("rotating.jsonl.1")));
        assertEquals(2, new AgentEventJournalReplayReader(journalPath)
                .query(AgentEventReplayQuery.all(2)).size());
    }
}
