package server.agents.runtime.activity.world;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Atomic JSON persistence for non-owning preparation sessions. */
public final class AgentFileWorldDirectorSessionStore implements AgentWorldDirectorSessionStore {
    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentFileWorldDirectorSessionStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("session directory is required");
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static AgentFileWorldDirectorSessionStore runtimeDefault() {
        return new AgentFileWorldDirectorSessionStore(
                Path.of(".runtime", "agents", "world-director", "sessions"));
    }

    @Override
    public synchronized void save(AgentWorldDirectorSession session) {
        if (session == null) throw new IllegalArgumentException("Director session is required");
        Path target = path(session.agentId());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            mapper.writeValue(temporary.toFile(), session);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("could not persist World Director session", failure);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The completed target remains authoritative.
            }
        }
    }

    @Override
    public synchronized Optional<AgentWorldDirectorSession> load(int agentId) {
        Path source = path(agentId);
        if (!Files.isRegularFile(source)) return Optional.empty();
        try {
            AgentWorldDirectorSession session =
                    mapper.readValue(source.toFile(), AgentWorldDirectorSession.class);
            if (session.agentId() != agentId) {
                throw new IllegalStateException("Director session identity does not match file name");
            }
            return Optional.of(session);
        } catch (IOException failure) {
            throw new IllegalStateException("could not restore World Director session", failure);
        }
    }

    @Override
    public synchronized void delete(int agentId) {
        try {
            Files.deleteIfExists(path(agentId));
        } catch (IOException failure) {
            throw new IllegalStateException("could not delete World Director session", failure);
        }
    }

    private Path path(int agentId) {
        if (agentId <= 0) throw new IllegalArgumentException("positive Agent id is required");
        return directory.resolve(agentId + ".json");
    }
}
