package server.agents.plans.amherst;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FileAmherstPlanProgressStore implements AmherstPlanProgressStore {
    private final Path directory;
    private final ObjectMapper mapper;

    public FileAmherstPlanProgressStore(Path directory) {
        this(directory, new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
    }

    FileAmherstPlanProgressStore(Path directory, ObjectMapper mapper) {
        this.directory = directory.toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    public static FileAmherstPlanProgressStore runtimeDefault() {
        return new FileAmherstPlanProgressStore(Path.of(".runtime", "agents", "plans"));
    }

    @Override
    public synchronized AmherstPlanProgressSnapshot load(String planId, int characterId) throws IOException {
        Path path = path(planId, characterId);
        if (!Files.exists(path)) {
            return AmherstPlanProgressSnapshot.empty(planId, characterId);
        }
        AmherstPlanProgressSnapshot snapshot = mapper.readValue(path.toFile(), AmherstPlanProgressSnapshot.class);
        if (!planId.equals(snapshot.planId()) || characterId != snapshot.characterId()) {
            throw new IOException("plan progress identity does not match its file name");
        }
        return snapshot;
    }

    @Override
    public synchronized void save(AmherstPlanProgressSnapshot snapshot) throws IOException {
        Files.createDirectories(directory);
        Path path = path(snapshot.planId(), snapshot.characterId());
        Path temp = Files.createTempFile(directory, path.getFileName().toString(), ".tmp");
        try {
            mapper.writeValue(temp.toFile(), snapshot);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public synchronized void delete(String planId, int characterId) throws IOException {
        Files.deleteIfExists(path(planId, characterId));
    }

    private Path path(String planId, int characterId) {
        if (planId == null || !planId.matches("[A-Za-z0-9._-]+") || characterId <= 0) {
            throw new IllegalArgumentException("safe plan id and positive character id are required");
        }
        return directory.resolve(planId + "-" + characterId + ".json");
    }
}
