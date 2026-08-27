package server.agents.progression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Atomic per-character persistence for bounded Mushroom Kingdom farm campaigns. */
public final class FileAgentMushroomKingdomFarmProgressStore {
    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public FileAgentMushroomKingdomFarmProgressStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static FileAgentMushroomKingdomFarmProgressStore runtimeDefault() {
        return new FileAgentMushroomKingdomFarmProgressStore(
                Path.of(".runtime", "agents", "mushroom-kingdom", "farming"));
    }

    public synchronized Optional<AgentMushroomKingdomFarmProgress> load(int characterId)
            throws IOException {
        Path path = path(characterId);
        if (!Files.exists(path)) return Optional.empty();
        AgentMushroomKingdomFarmProgress value = mapper.readValue(
                path.toFile(), AgentMushroomKingdomFarmProgress.class);
        if (value.characterId() != characterId) {
            throw new IOException("Mushroom Kingdom farm progress identity mismatch");
        }
        return Optional.of(value);
    }

    public synchronized void save(AgentMushroomKingdomFarmProgress value) throws IOException {
        Files.createDirectories(directory);
        Path path = path(value.characterId());
        Path temp = Files.createTempFile(directory, path.getFileName().toString(), ".tmp");
        try {
            mapper.writeValue(temp.toFile(), value);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | AccessDeniedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Path path(int characterId) {
        if (characterId <= 0) throw new IllegalArgumentException("positive character id is required");
        return directory.resolve(characterId + ".json");
    }
}
