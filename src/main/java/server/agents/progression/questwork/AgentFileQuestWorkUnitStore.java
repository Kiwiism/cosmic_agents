package server.agents.progression.questwork;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Atomic JSON store under the Agent runtime directory. */
public final class AgentFileQuestWorkUnitStore implements AgentQuestWorkUnitStore {
    private final Path directory;
    private final ObjectMapper mapper;

    public AgentFileQuestWorkUnitStore() {
        this(Path.of(".runtime", "agents", "quest-work-units"));
    }

    public AgentFileQuestWorkUnitStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("quest work directory is required");
        this.directory = directory;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public synchronized void save(AgentQuestWorkUnit workUnit) {
        if (workUnit == null) throw new IllegalArgumentException("quest work unit is required");
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            Path target = path(workUnit.workUnitId());
            temporary = Files.createTempFile(directory, "quest-work-", ".tmp");
            mapper.writeValue(temporary.toFile(), workUnit);
            move(temporary, target);
        } catch (IOException failure) {
            throw new IllegalStateException("could not persist quest work unit", failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best-effort cleanup; the durable target has already won or save failed.
                }
            }
        }
    }

    @Override
    public synchronized Optional<AgentQuestWorkUnit> load(String workUnitId) {
        String normalizedId = workUnitId == null ? "" : workUnitId.trim();
        Path path = path(workUnitId);
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            AgentQuestWorkUnit unit = mapper.readValue(path.toFile(), AgentQuestWorkUnit.class);
            if (!unit.workUnitId().equals(normalizedId)) {
                throw new IllegalStateException("quest work identity does not match file name");
            }
            return Optional.of(unit);
        } catch (IOException failure) {
            throw new IllegalStateException("could not restore quest work unit", failure);
        }
    }

    @Override
    public synchronized List<AgentQuestWorkUnit> loadAll() {
        if (!Files.isDirectory(directory)) return List.of();
        try (var files = Files.list(directory)) {
            List<AgentQuestWorkUnit> units = new ArrayList<>();
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toList()) {
                AgentQuestWorkUnit unit = mapper.readValue(file.toFile(), AgentQuestWorkUnit.class);
                if (!file.getFileName().toString().equals(unit.workUnitId() + ".json")) {
                    throw new IllegalStateException("quest work identity does not match file name");
                }
                units.add(unit);
            }
            units.sort(Comparator.comparing(AgentQuestWorkUnit::workUnitId));
            return List.copyOf(units);
        } catch (IOException failure) {
            throw new IllegalStateException("could not enumerate quest work units", failure);
        }
    }

    @Override
    public synchronized void delete(String workUnitId) {
        try {
            Files.deleteIfExists(path(workUnitId));
        } catch (IOException failure) {
            throw new IllegalStateException("could not delete quest work unit", failure);
        }
    }

    private Path path(String workUnitId) {
        String id = workUnitId == null ? "" : workUnitId.trim();
        if (id.isEmpty() || !id.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("safe quest work identity is required");
        }
        return directory.resolve(id + ".json");
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
