package server.agents.runtime.activity.world;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Append-only JSONL shadow journal; it is written only by explicit diagnostics. */
public final class AgentFileWorldDirectorJournalStore implements AgentWorldDirectorJournalStore {
    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentFileWorldDirectorJournalStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("journal directory is required");
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static AgentFileWorldDirectorJournalStore runtimeDefault() {
        return new AgentFileWorldDirectorJournalStore(
                Path.of(".runtime", "agents", "world-director", "journal"));
    }

    @Override
    public synchronized void append(AgentWorldDirectorJournalEntry entry) {
        if (entry == null) throw new IllegalArgumentException("journal entry is required");
        try {
            Files.createDirectories(directory);
            Files.writeString(path(entry.agentId()), mapper.writeValueAsString(entry)
                            + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new IllegalStateException("could not append World Director journal", failure);
        }
    }

    @Override
    public synchronized List<AgentWorldDirectorJournalEntry> recent(int agentId, int limit) {
        if (limit <= 0) return List.of();
        Path source = path(agentId);
        if (!Files.isRegularFile(source)) return List.of();
        try {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - limit);
            List<AgentWorldDirectorJournalEntry> result = new ArrayList<>();
            for (int index = start; index < lines.size(); index++) {
                if (!lines.get(index).isBlank()) {
                    result.add(mapper.readValue(
                            lines.get(index), AgentWorldDirectorJournalEntry.class));
                }
            }
            return List.copyOf(result);
        } catch (IOException failure) {
            throw new IllegalStateException("could not read World Director journal", failure);
        }
    }

    private Path path(int agentId) {
        if (agentId <= 0) throw new IllegalArgumentException("positive Agent id is required");
        return directory.resolve(agentId + ".jsonl");
    }
}
