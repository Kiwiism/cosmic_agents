package server.agents.plans.mapleisland.cohort;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Atomic JSON persistence for reusable pool identities and leases. */
public final class FileMapleIslandCohortPoolStore implements MapleIslandCohortPoolStore {
    private final Path path;
    private final ObjectMapper mapper;

    public FileMapleIslandCohortPoolStore(Path path) {
        this(path, new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
    }

    FileMapleIslandCohortPoolStore(Path path, ObjectMapper mapper) {
        this.path = path.toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    @Override
    public MapleIslandCohortPoolSnapshot load() throws IOException {
        if (!Files.exists(path)) {
            return MapleIslandCohortPoolSnapshot.EMPTY;
        }
        return mapper.readValue(path.toFile(), MapleIslandCohortPoolSnapshot.class);
    }

    @Override
    public void save(MapleIslandCohortPoolSnapshot snapshot) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
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
}
