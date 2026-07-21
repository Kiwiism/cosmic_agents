package server.agents.events.journal;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Blocking bounded reader intended for diagnostics and offline consumers only. */
final class AgentEventJournalReplayReader {
    private final Path path;
    private final ObjectMapper mapper;

    AgentEventJournalReplayReader(Path path) {
        this(path, new ObjectMapper());
    }

    AgentEventJournalReplayReader(Path path, ObjectMapper mapper) {
        this.path = path;
        this.mapper = mapper;
    }

    List<AgentEventJournalRecord> query(AgentEventReplayQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Replay query is required");
        }
        List<AgentEventJournalRecord> matches = new ArrayList<>(query.limit());
        Path rotated = path.resolveSibling(path.getFileName() + ".1");
        read(rotated, query, matches);
        if (matches.size() < query.limit()) {
            read(path, query, matches);
        }
        return List.copyOf(matches);
    }

    private void read(Path source, AgentEventReplayQuery query, List<AgentEventJournalRecord> matches) {
        if (!Files.isRegularFile(source)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String line;
            while (matches.size() < query.limit() && (line = reader.readLine()) != null) {
                AgentEventJournalRecord record = parse(line);
                if (record != null && matches(record, query)) {
                    matches.add(record);
                }
            }
        } catch (IOException ignored) {
            // Replay is diagnostic; a missing/unreadable file must not affect Agent gameplay.
        }
    }

    private AgentEventJournalRecord parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(line, AgentEventJournalRecord.class);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean matches(AgentEventJournalRecord record, AgentEventReplayQuery query) {
        return (query.agentId() == null || query.agentId() == record.agentId())
                && (query.objectiveId().isEmpty()
                || query.objectiveId().equals(record.context().objectiveId()))
                && (query.correlationId().isEmpty()
                || query.correlationId().equals(record.context().correlationId()))
                && (query.eventTypes().isEmpty() || query.eventTypes().contains(record.type()))
                && record.occurredAtMs() >= query.fromMs()
                && record.occurredAtMs() <= query.toMs();
    }
}
