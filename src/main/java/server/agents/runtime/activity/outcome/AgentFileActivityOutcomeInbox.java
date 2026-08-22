package server.agents.runtime.activity.outcome;

import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Atomic terminal-outcome inbox shared by all primary activity systems. */
public final class AgentFileActivityOutcomeInbox implements AgentActivityOutcomeInbox {
    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentFileActivityOutcomeInbox(Path directory) {
        if (directory == null) throw new IllegalArgumentException("outcome directory is required");
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static AgentFileActivityOutcomeInbox runtimeDefault() {
        return new AgentFileActivityOutcomeInbox(
                Path.of(".runtime", "agents", "activity-outcomes"));
    }

    @Override
    public synchronized AgentActivityOutcomeEnvelope publish(
            String outcomeId, AgentActivityTerminalOutcome outcome, long nowMs) {
        if (outcome == null || nowMs < 0L) {
            throw new IllegalArgumentException("terminal outcome and current time are required");
        }
        Optional<AgentActivityOutcomeEnvelope> existing = load(outcomeId);
        if (existing.isPresent()) {
            if (!existing.orElseThrow().outcome().equals(outcome)) {
                throw new IllegalStateException("outcome id is already bound to different content");
            }
            return existing.orElseThrow();
        }
        AgentActivityOutcomeEnvelope envelope =
                AgentActivityOutcomeEnvelope.published(outcomeId, outcome, nowMs);
        save(envelope);
        return envelope;
    }

    @Override
    public synchronized Optional<AgentActivityOutcomeEnvelope> load(String outcomeId) {
        Path source = path(outcomeId);
        if (!Files.isRegularFile(source)) return Optional.empty();
        AgentActivityOutcomeEnvelope envelope = read(source);
        if (!envelope.outcomeId().equals(outcomeId.trim())) {
            throw new IllegalStateException("activity outcome identity does not match its file");
        }
        return Optional.of(envelope);
    }

    @Override
    public synchronized List<AgentActivityOutcomeEnvelope> pending(String agentId) {
        String normalized = agentId == null ? "" : agentId.trim();
        if (normalized.isEmpty() || !Files.isDirectory(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::read)
                    .filter(envelope -> !envelope.acknowledged())
                    .filter(envelope -> envelope.outcome().agentId().equals(normalized))
                    .sorted(Comparator.comparingLong(AgentActivityOutcomeEnvelope::publishedAtMs))
                    .toList();
        } catch (IOException failure) {
            throw new IllegalStateException("could not list activity outcomes", failure);
        }
    }

    @Override
    public synchronized AgentActivityOutcomeEnvelope acknowledge(
            String outcomeId, String reason, long nowMs) {
        AgentActivityOutcomeEnvelope current = load(outcomeId)
                .orElseThrow(() -> new IllegalStateException("unknown activity outcome"));
        AgentActivityOutcomeEnvelope acknowledged = current.acknowledge(reason, nowMs);
        if (acknowledged != current) save(acknowledged);
        return acknowledged;
    }

    public synchronized void deleteAgent(int agentId) {
        String expected = Integer.toString(agentId);
        if (agentId <= 0 || !Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path source : files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList()) {
                if (read(source).outcome().agentId().equals(expected)) {
                    Files.deleteIfExists(source);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("could not delete Agent activity outcomes", failure);
        }
    }

    private AgentActivityOutcomeEnvelope read(Path source) {
        try {
            return mapper.readValue(source.toFile(), AgentActivityOutcomeEnvelope.class);
        } catch (IOException failure) {
            throw new IllegalStateException("could not restore activity outcome", failure);
        }
    }

    private void save(AgentActivityOutcomeEnvelope envelope) {
        Path target = path(envelope.outcomeId());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            mapper.writeValue(temporary.toFile(), envelope);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("could not persist activity outcome", failure);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The completed target remains authoritative.
            }
        }
    }

    private Path path(String outcomeId) {
        String normalized = outcomeId == null ? "" : outcomeId.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("outcome id is required");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(digest) + ".json");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
