package server.agents.runtime.activity.world;

import com.fasterxml.jackson.databind.ObjectMapper;

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

/** Atomic, one-file-per-directive inbox. */
public final class AgentFileWorldDirectiveInbox implements AgentWorldDirectiveInbox {
    private static final Comparator<AgentWorldDirectiveEnvelope> PENDING_ORDER =
            Comparator.<AgentWorldDirectiveEnvelope>comparingInt(value -> value.directive().priority())
                    .reversed()
                    .thenComparingLong(value -> value.directive().createdAtMs())
                    .thenComparing(value -> value.directive().directiveId());

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentFileWorldDirectiveInbox(Path directory) {
        if (directory == null) throw new IllegalArgumentException("directive directory is required");
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static AgentFileWorldDirectiveInbox runtimeDefault() {
        return new AgentFileWorldDirectiveInbox(
                Path.of(".runtime", "agents", "world-director", "directives"));
    }

    @Override
    public synchronized AgentWorldDirectiveEnvelope submit(AgentWorldDirective directive, long nowMs) {
        if (directive == null || nowMs < 0L) {
            throw new IllegalArgumentException("directive and current time are required");
        }
        Optional<AgentWorldDirectiveEnvelope> existing = load(
                directive.agentId(), directive.directiveId());
        if (existing.isPresent()) {
            if (!existing.orElseThrow().directive().equals(directive)) {
                throw new IllegalStateException("directive id is already bound to different content");
            }
            return existing.orElseThrow();
        }
        AgentWorldDirectiveEnvelope created = AgentWorldDirectiveEnvelope.pending(directive);
        if (directive.expiredAt(nowMs)) {
            created = created.resolve(AgentWorldDirectiveStatus.EXPIRED,
                    "directive expired before admission", nowMs);
        }
        save(created);
        return created;
    }

    @Override
    public synchronized Optional<AgentWorldDirectiveEnvelope> nextPending(int agentId, long nowMs) {
        List<AgentWorldDirectiveEnvelope> directives = list(agentId);
        for (AgentWorldDirectiveEnvelope envelope : directives) {
            if (envelope.status() == AgentWorldDirectiveStatus.PENDING
                    && envelope.directive().expiredAt(nowMs)) {
                save(envelope.resolve(AgentWorldDirectiveStatus.EXPIRED,
                        "directive expired while pending", nowMs));
            }
        }
        return list(agentId).stream()
                .filter(value -> value.status() == AgentWorldDirectiveStatus.PENDING)
                .sorted(PENDING_ORDER)
                .findFirst();
    }

    @Override
    public synchronized AgentWorldDirectiveEnvelope claim(
            int agentId, String directiveId, long nowMs) {
        AgentWorldDirectiveEnvelope current = required(agentId, directiveId);
        if (current.status() == AgentWorldDirectiveStatus.CLAIMED) return current;
        AgentWorldDirectiveEnvelope claimed = current.claim(nowMs);
        save(claimed);
        return claimed;
    }

    @Override
    public synchronized AgentWorldDirectiveEnvelope resolve(
            int agentId,
            String directiveId,
            AgentWorldDirectiveStatus terminalStatus,
            String reason,
            long nowMs) {
        AgentWorldDirectiveEnvelope current = required(agentId, directiveId);
        if (current.status() == terminalStatus && terminalStatus.terminal()) return current;
        AgentWorldDirectiveEnvelope resolved = current.resolve(terminalStatus, reason, nowMs);
        save(resolved);
        return resolved;
    }

    @Override
    public synchronized Optional<AgentWorldDirectiveEnvelope> load(int agentId, String directiveId) {
        Path source = path(agentId, directiveId);
        if (!Files.isRegularFile(source)) return Optional.empty();
        try {
            AgentWorldDirectiveEnvelope envelope =
                    mapper.readValue(source.toFile(), AgentWorldDirectiveEnvelope.class);
            if (envelope.directive().agentId() != agentId
                    || !envelope.directive().directiveId().equals(directiveId)) {
                throw new IllegalStateException("directive identity does not match its file");
            }
            return Optional.of(envelope);
        } catch (IOException failure) {
            throw new IllegalStateException("could not restore World Director directive", failure);
        }
    }

    @Override
    public synchronized List<AgentWorldDirectiveEnvelope> list(int agentId) {
        Path agentDirectory = agentDirectory(agentId);
        if (!Files.isDirectory(agentDirectory)) return List.of();
        try (var files = Files.list(agentDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::read)
                    .sorted(Comparator.comparingLong(value -> value.directive().createdAtMs()))
                    .toList();
        } catch (IOException failure) {
            throw new IllegalStateException("could not list World Director directives", failure);
        }
    }

    public synchronized void deleteAgent(int agentId) {
        Path target = agentDirectory(agentId);
        if (!Files.exists(target)) return;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("could not delete Agent Director directives", failure);
        }
    }

    private AgentWorldDirectiveEnvelope required(int agentId, String directiveId) {
        return load(agentId, directiveId)
                .orElseThrow(() -> new IllegalStateException("unknown World Director directive"));
    }

    private AgentWorldDirectiveEnvelope read(Path source) {
        try {
            return mapper.readValue(source.toFile(), AgentWorldDirectiveEnvelope.class);
        } catch (IOException failure) {
            throw new IllegalStateException("could not restore World Director directive", failure);
        }
    }

    private void save(AgentWorldDirectiveEnvelope envelope) {
        Path target = path(envelope.directive().agentId(), envelope.directive().directiveId());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            mapper.writeValue(temporary.toFile(), envelope);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("could not persist World Director directive", failure);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The completed target remains authoritative.
            }
        }
    }

    private Path path(int agentId, String directiveId) {
        if (directiveId == null || directiveId.isBlank()) {
            throw new IllegalArgumentException("directive id is required");
        }
        return agentDirectory(agentId).resolve(digest(directiveId) + ".json");
    }

    private Path agentDirectory(int agentId) {
        if (agentId <= 0) throw new IllegalArgumentException("positive Agent id is required");
        return directory.resolve(Integer.toString(agentId));
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
