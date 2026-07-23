package server.agents.plans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class FileAgentPlanCheckpointStore implements AgentPlanCheckpointStore {
    private final Path directory;
    private final ObjectMapper mapper;

    public FileAgentPlanCheckpointStore(Path directory) {
        this(directory, new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
    }

    FileAgentPlanCheckpointStore(Path directory, ObjectMapper mapper) {
        this.directory = directory.toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    public static FileAgentPlanCheckpointStore runtimeDefault() {
        return new FileAgentPlanCheckpointStore(
                Path.of(".runtime", "agents", "plans", "checkpoints"));
    }

    @Override
    public synchronized Optional<AgentPlanCheckpoint> load(int characterId) throws IOException {
        Path path = path(characterId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        AgentPlanCheckpoint checkpoint = mapper.readValue(path.toFile(), AgentPlanCheckpoint.class);
        if (checkpoint.characterId() != characterId) {
            throw new IOException("plan checkpoint identity does not match its file name");
        }
        return Optional.of(checkpoint);
    }

    @Override
    public synchronized void save(AgentPlanCheckpoint checkpoint) throws IOException {
        Files.createDirectories(directory);
        Path path = path(checkpoint.characterId());
        Path temp = Files.createTempFile(directory, path.getFileName().toString(), ".tmp");
        try {
            mapper.writeValue(temp.toFile(), checkpoint);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public synchronized void delete(int characterId) throws IOException {
        Files.deleteIfExists(path(characterId));
    }

    private Path path(int characterId) {
        if (characterId <= 0) {
            throw new IllegalArgumentException("positive character id is required");
        }
        return directory.resolve(characterId + ".json");
    }
}
