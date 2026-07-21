package server.agents.personality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class FileAgentPersonalityAssignmentStore implements AgentPersonalityAssignmentStore {
    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public FileAgentPersonalityAssignmentStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static FileAgentPersonalityAssignmentStore runtimeDefault() {
        return new FileAgentPersonalityAssignmentStore(
                Path.of(".runtime", "agents", "personality", "assignments"));
    }

    @Override
    public synchronized Optional<AgentPersonalityAssignment> load(int characterId) throws IOException {
        Path path = path(characterId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        AgentPersonalityAssignment assignment = mapper.readValue(
                path.toFile(), AgentPersonalityAssignment.class);
        if (assignment.characterId() != characterId) {
            throw new IOException("personality assignment identity does not match its file name");
        }
        return Optional.of(assignment);
    }

    @Override
    public synchronized void save(AgentPersonalityAssignment assignment) throws IOException {
        Files.createDirectories(directory);
        Path path = path(assignment.characterId());
        Path temp = Files.createTempFile(directory, path.getFileName().toString(), ".tmp");
        try {
            mapper.writeValue(temp.toFile(), assignment);
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

    private Path path(int characterId) {
        if (characterId <= 0) {
            throw new IllegalArgumentException("positive character id is required");
        }
        return directory.resolve(characterId + ".json");
    }
}
