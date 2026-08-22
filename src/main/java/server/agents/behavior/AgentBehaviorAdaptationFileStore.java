package server.agents.behavior;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Independent atomic checkpoint store for human-like energy continuity. */
public final class AgentBehaviorAdaptationFileStore {
    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentBehaviorAdaptationFileStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("behavior directory is required");
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static AgentBehaviorAdaptationFileStore runtimeDefault() {
        return new AgentBehaviorAdaptationFileStore(
                Path.of(".runtime", "agents", "behavior-energy"));
    }

    public synchronized Optional<AgentBehaviorAdaptationSnapshot> load(int agentId) {
        Path path = path(agentId);
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(path.toFile(),
                    AgentBehaviorAdaptationSnapshot.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not restore Agent energy checkpoint", failure);
        }
    }

    public synchronized void save(int agentId, AgentBehaviorAdaptationSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("behavior snapshot is required");
        Path target = path(agentId);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            mapper.writeValue(temporary.toFile(), snapshot);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("could not persist Agent energy checkpoint", failure);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    public synchronized void delete(int agentId) {
        try {
            Files.deleteIfExists(path(agentId));
        } catch (IOException failure) {
            throw new IllegalStateException("could not delete Agent energy checkpoint", failure);
        }
    }

    private Path path(int agentId) {
        if (agentId <= 0) throw new IllegalArgumentException("positive Agent id is required");
        return directory.resolve(agentId + ".json");
    }
}
