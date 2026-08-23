package server.agents.runtime.journey;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Atomic one-event-per-file journal; duplicate event IDs are idempotent. */
public final class AgentFileJourneyJournalStore implements AgentJourneyJournalStore {
    private static final ConcurrentHashMap<Path, Object> JOURNAL_LOCKS =
            new ConcurrentHashMap<>();
    private final Path directory;
    private final ObjectMapper mapper;

    public AgentFileJourneyJournalStore() {
        this(Path.of(".runtime", "agents", "journeys"));
    }

    public AgentFileJourneyJournalStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("journey directory is required");
        this.directory = directory;
        mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public synchronized AgentJourneyEvent append(AgentJourneyEventDraft draft) {
        if (draft == null) throw new IllegalArgumentException("journey event draft is required");
        Path agentDirectory = agentDirectory(draft.agentId());
        synchronized (journalLock(agentDirectory)) {
            Path target = agentDirectory.resolve(encoded(draft.eventId()) + ".json");
            if (Files.isRegularFile(target)) {
                AgentJourneyEvent existing = load(target);
                if (!existing.draft().equals(draft)) {
                    throw new IllegalStateException("journey event id is already bound to different evidence");
                }
                return existing;
            }
            List<AgentJourneyEvent> current = read(draft.agentId());
            long nextSequence = current.stream().mapToLong(AgentJourneyEvent::sequence)
                    .max().orElse(0L) + 1L;
            AgentJourneyEvent event = AgentJourneyEvent.sequence(nextSequence, draft);
            Path temporary = null;
            try {
                Files.createDirectories(agentDirectory);
                temporary = Files.createTempFile(agentDirectory, "journey-", ".tmp");
                mapper.writeValue(temporary.toFile(), event);
                move(temporary, target);
                return event;
            } catch (IOException failure) {
                throw new IllegalStateException("could not persist Agent journey event", failure);
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ignored) {
                        // Best-effort cleanup after either an atomic win or failed append.
                    }
                }
            }
        }
    }

    @Override
    public synchronized List<AgentJourneyEvent> read(String agentId) {
        String normalizedAgentId = required(agentId, "Agent id");
        Path agentDirectory = agentDirectory(normalizedAgentId);
        synchronized (journalLock(agentDirectory)) {
            if (!Files.isDirectory(agentDirectory)) return List.of();
            try (var files = Files.list(agentDirectory)) {
                List<AgentJourneyEvent> events = files
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .map(this::load)
                        .sorted(Comparator.comparingLong(AgentJourneyEvent::sequence))
                        .toList();
                if (events.stream().anyMatch(event -> !event.agentId().equals(normalizedAgentId))) {
                    throw new IllegalStateException("journey event identity does not match directory");
                }
                long distinctSequences = events.stream().map(AgentJourneyEvent::sequence).distinct().count();
                if (distinctSequences != events.size()) {
                    throw new IllegalStateException("journey contains duplicate sequence numbers");
                }
                return List.copyOf(events);
            } catch (IOException failure) {
                throw new IllegalStateException("could not read Agent journey", failure);
            }
        }
    }

    private static Object journalLock(Path agentDirectory) {
        Path key = agentDirectory.toAbsolutePath().normalize();
        return JOURNAL_LOCKS.computeIfAbsent(key, ignored -> new Object());
    }

    private AgentJourneyEvent load(Path path) {
        try {
            return mapper.readValue(path.toFile(), AgentJourneyEvent.class);
        } catch (IOException failure) {
            throw new IllegalStateException("could not restore Agent journey event " + path, failure);
        }
    }

    private Path agentDirectory(String agentId) {
        return directory.resolve(encoded(required(agentId, "Agent id")));
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
