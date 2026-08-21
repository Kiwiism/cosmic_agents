package server.agents.runtime.activity.session;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Atomic JSON persistence for handoffs that must survive a server restart. */
public final class AgentFileActivityHandoffStore implements AgentActivityHandoffStore {
    private final Path directory;
    private final ObjectMapper mapper;

    public AgentFileActivityHandoffStore(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("handoff directory is required");
        }
        this.directory = directory.toAbsolutePath().normalize();
        this.mapper = new ObjectMapper();
    }

    @Override
    public void save(AgentActivityHandoffCoordinator.Handoff handoff) {
        if (handoff == null) {
            throw new IllegalArgumentException("handoff is required");
        }
        Path target = path(handoff.handoffId());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            mapper.writeValue(temporary.toFile(), handoff);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("could not persist activity handoff", failure);
        }
    }

    @Override
    public Optional<AgentActivityHandoffCoordinator.Handoff> load(String handoffId) {
        Path source = path(handoffId);
        if (!Files.isRegularFile(source)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(
                    source.toFile(), AgentActivityHandoffCoordinator.Handoff.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not restore activity handoff", failure);
        }
    }

    @Override
    public List<AgentActivityHandoffCoordinator.Handoff> list() {
        if (!Files.isDirectory(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .map(path -> {
                        try {
                            return mapper.readValue(path.toFile(),
                                    AgentActivityHandoffCoordinator.Handoff.class);
                        } catch (IOException failure) {
                            throw new IllegalStateException(
                                    "could not restore activity handoff " + path, failure);
                        }
                    }).toList();
        } catch (IOException failure) {
            throw new IllegalStateException("could not enumerate activity handoffs", failure);
        }
    }

    @Override
    public void delete(String handoffId) {
        try {
            Files.deleteIfExists(path(handoffId));
        } catch (IOException failure) {
            throw new IllegalStateException("could not delete activity handoff", failure);
        }
    }

    private Path path(String handoffId) {
        String normalized = handoffId == null ? "" : handoffId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("handoff id is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(digest) + ".json");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
